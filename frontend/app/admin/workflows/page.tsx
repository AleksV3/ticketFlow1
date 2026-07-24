"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { WorkflowConfigurationPanels } from "@/components/WorkflowConfigurationPanels";
import { api, get, patch, post } from "@/lib/api";
import type { CurrentUser } from "@/lib/auth";
import { Background, BaseEdge, ConnectionLineType, Controls, Handle, MarkerType, MiniMap, Position, ReactFlow, useEdgesState, useNodesState, type Connection, type Edge, type EdgeProps, type Node } from "@xyflow/react";

type State = { id: number; key: string; name?: string; isInitial: boolean; isTerminal: boolean; sortOrder: number };
type Transition = { id: number; fromStateId: number; toStateId: number; requiredPermission: string; requiredParty: string | null; responsibilityAfter: string | null; operationKind: string };
type Workflow = { id: number; name: string; organizationId: number | null; version: number; canvasLayout?: string | null; states: State[]; transitions: Transition[] };
type TicketType = { id: number; key: string; name: string; workflowId: number; organizationId: number | null; requiresProposal: boolean; active?: boolean; sortOrder?: number; capability?: string; version?: number };
type Organization = { id: number; name: string };

export default function WorkflowsPage() {
  return <AppShell require="WORKFLOW_MANAGE">{user => <Editor user={user} />}</AppShell>;
}

function Editor({ user }: { user: CurrentUser }) {
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [organizationId, setOrganizationId] = useState(user.organizationId?.toString() ?? "internal");
  const [workflows, setWorkflows] = useState<Workflow[]>([]);
  const [types, setTypes] = useState<TicketType[]>([]);
  const [selected, setSelected] = useState<Workflow | null>(null);
  const [workflowName, setWorkflowName] = useState("");
  const [initialKey, setInitialKey] = useState("OPEN");
  const [terminalKey, setTerminalKey] = useState("DONE");
  const [typeKey, setTypeKey] = useState("");
  const [typeName, setTypeName] = useState("");
  const [message, setMessage] = useState("");
  const internalOrganization = useMemo(() => organizations.find(org => org.name.toLowerCase() === "ticketflow1 internal"), [organizations]);
  const selectedOrganizationId = organizationId === "internal" ? internalOrganization?.id ?? null : Number(organizationId);
  const selectedScopeReady = user.party !== "TICKETFLOW1" || organizationId !== "internal" || !!selectedOrganizationId;

  useEffect(() => {
    if (user.party === "TICKETFLOW1") {
      get<Organization[]>("/admin/organizations").then(setOrganizations)
        .catch(error => setMessage(error instanceof Error ? error.message : "Could not load organizations."));
    }
  }, [user.party]);

  const load = useCallback(async () => {
    if (!selectedScopeReady) return;
    try {
      const query = selectedOrganizationId ? `?organizationId=${selectedOrganizationId}` : "";
      const [workflowRows, typeRows] = await Promise.all([
        get<Workflow[]>(`/admin/workflows${query}`),
        get<TicketType[]>(`/admin/ticket-types${query}`),
      ]);
      setWorkflows(workflowRows); setTypes(typeRows);
      setSelected(previous => workflowRows.find(row => row.id === previous?.id) ?? null);
      setMessage("");
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Could not load workflow configuration.");
    }
  }, [selectedOrganizationId, selectedScopeReady]);
  useEffect(() => { void load(); }, [load]);

  async function createWorkflow(event: FormEvent) {
    event.preventDefault();
    const first = normalize(initialKey), terminal = normalize(terminalKey);
    if (first === terminal) { setMessage("Use two different state keys."); return; }
    try {
      await post("/admin/workflows", {
        name: workflowName, organizationId: selectedOrganizationId,
        states: [
          { key: first, isInitial: true, isTerminal: false, sortOrder: 0 },
          { key: terminal, isInitial: false, isTerminal: true, sortOrder: 1 },
        ],
        transitions: [{ fromState: first, toState: terminal, requiredPermission: "TICKET_TRANSITION", requiredParty: null, responsibilityAfter: null, operationKind: "STANDARD" }],
      });
      setWorkflowName(""); setMessage("Custom workflow created with a safe STANDARD transition."); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not create workflow."); }
  }

  async function addGraphState(name: string, isTerminal: boolean) {
    if (!selected) return;
    const key = normalize(name);
    if (!key) { setMessage("Enter a state name."); return; }
    if (selected.states.some(state => state.key === key)) { setMessage("A state with that name already exists."); return; }
    const states = [...selected.states.map(({ key, isInitial, isTerminal, sortOrder }) => ({ key, isInitial, isTerminal, sortOrder })),
      { key, isInitial: false, isTerminal, sortOrder: selected.states.length }];
    try {
      await patch(`/admin/workflows/${selected.id}`, { version: selected.version, states });
      setMessage(`${key.replaceAll("_", " ")} added to the canvas. Drag it where you want, then connect it.`); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not update workflow."); }
  }
  async function renameGraphState(state: State) {
    const next = window.prompt("Rename workflow state", state.key);
    if (next == null || !next.trim() || normalize(next) === state.key || !selected) return;
    try { await patch(`/admin/workflows/${selected.id}/states/${state.id}`, { version: selected.version, name: next.trim() }); setMessage("Workflow state renamed."); await load(); }
    catch (error) { setMessage(error instanceof Error ? error.message : "Could not rename workflow state."); }
  }

  async function addType(event: FormEvent) {
    event.preventDefault(); if (!selected) return;
    try {
      await post("/admin/ticket-types", { key: normalize(typeKey), name: typeName, workflowId: selected.id,
        organizationId: selectedOrganizationId, requiresProposal: false });
      setTypeKey(""); setTypeName(""); setMessage("Custom ticket type created."); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not create ticket type."); }
  }

  async function applyWorkflowToType(type: TicketType, workflowId: number) {
    if (type.workflowId === workflowId) return;
    try { await patch(`/admin/ticket-types/${type.id}`, { version: type.version, workflowId }); setMessage("Ticket type workflow updated."); await load(); }
    catch (error) { setMessage(error instanceof Error ? error.message : "Could not update ticket type workflow."); }
  }

  const editableTransitions = (workflow: Workflow) => workflow.transitions.filter(edge => edge.operationKind === "STANDARD");
  const transitionRequest = (workflow: Workflow, edges: Transition[]) => edges.map(edge => ({
    fromState: workflow.states.find(state => state.id === edge.fromStateId)?.key,
    toState: workflow.states.find(state => state.id === edge.toStateId)?.key,
    requiredPermission: edge.requiredPermission,
    requiredParty: edge.requiredParty,
    responsibilityAfter: edge.responsibilityAfter,
    operationKind: "STANDARD",
  }));

  async function addGraphTransition(from: string, to: string, party: string | null = null, responsibility: string | null = null) {
    if (!selected || from === to) return;
    const existing = transitionRequest(selected, editableTransitions(selected));
    if (selected.transitions.some(edge => selected.states.find(state => state.id === edge.fromStateId)?.key === from && selected.states.find(state => state.id === edge.toStateId)?.key === to)) { setMessage("That connection already exists."); return; }
    try {
      await patch(`/admin/workflows/${selected.id}`, { version: selected.version, transitions: [...existing, {
        fromState: from, toState: to, requiredPermission: "TICKET_TRANSITION",
        requiredParty: party, responsibilityAfter: responsibility, operationKind: "STANDARD",
      }] });
      setMessage(`Branch added: ${from} → ${to}.`); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not add workflow branch."); }
  }

  async function removeTransition(edge: Transition) {
    if (!selected || edge.operationKind !== "STANDARD") return;
    try {
      const remaining = editableTransitions(selected).filter(item => item.id !== edge.id);
      await patch(`/admin/workflows/${selected.id}`, { version: selected.version, transitions: transitionRequest(selected, remaining) });
      setMessage("Workflow branch removed."); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not remove workflow branch."); }
  }
  async function removeState(state: State) {
    if (!selected || !window.confirm(`Remove ${state.key.replaceAll("_", " ")}?`)) return;
    try { await api(`/admin/workflows/${selected.id}/states/${state.id}`, { method: "DELETE" }); setMessage("Workflow state removed."); await load(); }
    catch (error) { setMessage(error instanceof Error ? error.message : "Could not remove workflow state."); }
  }
  async function updateTransition(edge: Transition, patchBody: Partial<Transition>) {
    if (!selected || edge.operationKind !== "STANDARD") return;
    try {
      const updated = editableTransitions(selected).map(item => item.id === edge.id ? { ...item, ...patchBody } : item);
      await patch(`/admin/workflows/${selected.id}`, { version: selected.version, transitions: transitionRequest(selected, updated) });
      setMessage("Workflow connection updated."); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not update workflow connection."); }
  }
  async function saveCanvasLayout(workflow: Workflow, canvasLayout: string) {
    try {
      await patch(`/admin/workflows/${workflow.id}`, { version: workflow.version, canvasLayout });
      setMessage("Workflow canvas layout saved for this organization."); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not save workflow canvas layout."); }
  }

  return <div className="space-y-6">
    <div><p className="eyebrow">Ticket configuration</p><h1 className="mt-1 text-3xl font-bold">Ticket types apply workflows</h1><p className="mt-2 max-w-3xl text-slate-600">A <strong>ticket type</strong> defines what kind of ticket is created. Each ticket type applies exactly one <strong>workflow</strong>: a branching map of states and allowed choices. Admins can add states and connect one state to multiple next states.</p></div>
    {user.party === "TICKETFLOW1" ? <label className="card block">Workflow owner<select className="field mt-1" value={organizationId} onChange={event => setOrganizationId(event.target.value)}><option value="internal">TicketFlow1 · Internal workflows</option>{organizations.map(org => <option key={org.id} value={org.id}>{org.name} · Client workflows</option>)}</select></label> : null}
    {!selectedScopeReady ? <div className="card">Loading TicketFlow1 Internal workflow scope…</div> : null}
    <div className="space-y-6">
      <section className="card space-y-5"><div><h2 className="font-bold">1. Choose a workflow map</h2><div className="mt-2 grid gap-2 sm:grid-cols-3">{workflows.map(workflow => <button className="block w-full rounded border p-3 text-left hover:bg-slate-50" key={workflow.id} onClick={() => setSelected(workflow)}><strong>{workflow.name}</strong><span className="mt-1 block text-xs text-slate-500">{organizationId==="internal"?"Internal TicketFlow1 workflow":`Applied by: ${types.filter(type => type.workflowId === workflow.id).map(type => type.name).join(", ") || "No ticket type"}`}</span><span className="mt-1 block text-xs text-blue-400">{workflow.states.length} states · {workflow.transitions.length} choices</span></button>)}</div></div>{organizationId!=="internal"?<div><h2 className="font-bold">2. Select workflow for each ticket type</h2><div className="mt-2 grid gap-2">{types.map(type => <label className="block rounded-xl border p-3" key={type.id}><span className="flex flex-wrap items-center justify-between gap-3"><span><strong>{type.name}</strong><span className="block text-xs text-slate-500">{type.key}</span></span><select aria-label={`Workflow for ${type.name}`} className="field min-w-64" value={type.workflowId} onChange={event => void applyWorkflowToType(type, Number(event.target.value))}>{workflows.map(workflow => <option value={workflow.id} key={workflow.id}>{workflow.name}</option>)}</select></span></label>)}</div><p className="mt-2 text-xs text-slate-500">For safety, a ticket type can only switch workflows before its first ticket is created.</p></div>:null}</section>
      <section className="space-y-6">
        {selected ? <WorkflowMap workflow={selected} types={types.filter(type => type.workflowId === selected.id)} addState={addGraphState} renameState={renameGraphState} addTransition={addGraphTransition} updateTransition={updateTransition} removeTransition={removeTransition} removeState={removeState} saveCanvasLayout={saveCanvasLayout} /> : <div className="card">Select a workflow to view its branching map.</div>}
        {selected ? <form className="card space-y-3" onSubmit={addType}><div><p className="eyebrow">Selected workflow</p><h2 className="font-bold">Create ticket type for {selected.name}</h2></div><p className="text-sm text-slate-500">{organizationId === "internal" ? "Creates a TicketFlow1 Internal ticket type used by internal ticket creation." : "Creates a client-organization ticket type."}</p><label className="block">Key<input required className="field mt-1" value={typeKey} onChange={event => setTypeKey(event.target.value)} /></label><label className="block">Display name<input required className="field mt-1" value={typeName} onChange={event => setTypeName(event.target.value)} /></label><button className="btn-primary">Create ticket type</button></form> : null}
        {selectedScopeReady ? <WorkflowConfigurationPanels organizationId={selectedOrganizationId ? String(selectedOrganizationId) : "internal"} workflows={workflows} types={types} reload={load} report={setMessage} /> : null}
        <form className="card grid gap-3 sm:grid-cols-2" onSubmit={createWorkflow}><div className="sm:col-span-2"><p className="eyebrow">New map</p><h2 className="font-bold">Create custom workflow</h2><p className="text-sm text-slate-500">Start with two states, then arrange and connect everything directly in the canvas.</p></div><label className="sm:col-span-2">Workflow name<input className="field mt-1" required value={workflowName} onChange={event => setWorkflowName(event.target.value)} /></label><label>Starting state<input className="field mt-1" required value={initialKey} onChange={event => setInitialKey(event.target.value)} /></label><label>Ending state<input className="field mt-1" required value={terminalKey} onChange={event => setTerminalKey(event.target.value)} /></label><button className="btn-primary sm:col-span-2">Create workflow</button></form>
      </section>
    </div>
    {message ? <p className="card" role="status">{message}</p> : null}
  </div>;
}

function WorkflowMap({ workflow, types, addState, renameState, addTransition, updateTransition, removeTransition, removeState, saveCanvasLayout }: {
  workflow: Workflow;
  types: TicketType[];
  addState: (name: string, isTerminal: boolean) => Promise<void>;
  renameState: (state: State) => Promise<void>;
  addTransition: (from: string, to: string, party?: string | null, responsibility?: string | null) => Promise<void>;
  updateTransition: (edge: Transition, patchBody: Partial<Transition>) => Promise<void>;
  removeTransition: (edge: Transition) => Promise<void>;
  removeState: (state: State) => Promise<void>;
  saveCanvasLayout: (workflow: Workflow, canvasLayout: string) => Promise<void>;
}) {
  const [addingState, setAddingState] = useState(false);
  const [newStateName, setNewStateName] = useState("");
  const [newStateTerminal, setNewStateTerminal] = useState(false);
  const [savingState, setSavingState] = useState(false);
  const [pendingConnection, setPendingConnection] = useState<{ from: string; to: string } | null>(null);
  const [connectionParty, setConnectionParty] = useState("");
  const [connectionResponsibility, setConnectionResponsibility] = useState("");
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [routeVersion, setRouteVersion] = useState(0);
  const savedCanvasLayout = useMemo(() => parseCanvasLayout(workflow.canvasLayout), [workflow.canvasLayout]);
  const [draftCanvasLayout, setDraftCanvasLayout] = useState(savedCanvasLayout);
  const [layoutDirty, setLayoutDirty] = useState(false);
  const [savingLayout, setSavingLayout] = useState(false);
  const selectedTransition = workflow.transitions.find(edge => String(edge.id) === selectedEdgeId && edge.operationKind === "STANDARD") ?? null;
  const selectedRoute = selectedTransition ? draftCanvasLayout.edgeRoutes[String(selectedTransition.id)] ?? defaultRoute : defaultRoute;
  const initial = useMemo(() => flowElements(workflow, draftCanvasLayout, routeVersion), [draftCanvasLayout, routeVersion, workflow]);
  const [nodes, setNodes, onNodesChange] = useNodesState(initial.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initial.edges);
  useEffect(() => { setNodes(initial.nodes); setEdges(initial.edges); }, [initial, setEdges, setNodes]);
  useEffect(() => { setDraftCanvasLayout(savedCanvasLayout); setLayoutDirty(false); setRouteVersion(value => value + 1); }, [savedCanvasLayout]);
  const changeCanvasLayout = useCallback((updater: (layout: CanvasLayout) => CanvasLayout) => {
    setDraftCanvasLayout(current => cleanCanvasLayout(updater(current), workflow));
    setLayoutDirty(true);
  }, [workflow]);
  const updateRoutePoint = useCallback((node: Node, persist: boolean) => {
    const edgeId = edgeIdFromRouteNode(node.id);
    if (!edgeId) return false;
    const point = { x: node.position.x + ROUTE_HANDLE_SIZE / 2, y: node.position.y + ROUTE_HANDLE_SIZE / 2 };
    setEdges(current => current.map(edge => edge.id === String(edgeId) ? { ...edge, data: { ...(edge.data ?? {}), routeX: point.x, routeY: point.y } } : edge));
    if (persist) {
      changeCanvasLayout(layout => ({ ...layout, edgePoints: { ...layout.edgePoints, [String(edgeId)]: point } }));
      setSelectedEdgeId(String(edgeId));
    }
    return true;
  }, [changeCanvasLayout, setEdges]);
  const moveRoutePoint = useCallback((_: unknown, node: Node) => { updateRoutePoint(node, false); }, [updateRoutePoint]);
  const connect = useCallback((connection: Connection) => {
    const from = workflow.states.find(state => String(state.id) === connection.source)?.key;
    const to = workflow.states.find(state => String(state.id) === connection.target)?.key;
    if (!from || !to || from === to) return;
    setPendingConnection({ from, to });
    setEdges(current => [...current.filter(edge => edge.id !== "draft-connection"), {
      id: "draft-connection", source: connection.source!, target: connection.target!,
      sourceHandle: connection.sourceHandle ?? "source-right",
      targetHandle: connection.targetHandle ?? "target-left",
      type: "manualStep", label: "Configure…", animated: true, deletable: false,
      markerEnd: edgeMarker(), className: "flow-edge-draft",
    }]);
  }, [setEdges, workflow.states]);
  const deleteEdges = useCallback(async (removed: Edge[]) => {
    for (const edge of removed) {
      const transition = workflow.transitions.find(item => String(item.id) === edge.id);
      if (transition?.operationKind === "STANDARD") await removeTransition(transition);
    }
  }, [removeTransition, workflow.transitions]);
  const rememberPosition = useCallback((_: unknown, node: Node) => {
    if (updateRoutePoint(node, true)) {
      setRouteVersion(value => value + 1);
      return;
    }
    changeCanvasLayout(layout => ({ ...layout, nodes: { ...layout.nodes, [node.id]: node.position } }));
  }, [changeCanvasLayout, updateRoutePoint]);
  async function submitState(event: FormEvent) {
    event.preventDefault(); if (!newStateName.trim()) return;
    setSavingState(true);
    try { await addState(newStateName, newStateTerminal); setNewStateName(""); setNewStateTerminal(false); setAddingState(false); }
    finally { setSavingState(false); }
  }
  async function saveConnection(event: FormEvent) {
    event.preventDefault(); if (!pendingConnection) return;
    await addTransition(pendingConnection.from, pendingConnection.to, connectionParty || null, connectionResponsibility || null);
    setPendingConnection(null); setConnectionParty(""); setConnectionResponsibility("");
  }
  async function saveSelectedConnection(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedTransition) return;
    const form = new FormData(event.currentTarget);
    await updateTransition(selectedTransition, {
      requiredParty: stringOrNull(form.get("requiredParty")),
      responsibilityAfter: stringOrNull(form.get("responsibilityAfter")),
    });
  }
  function setSelectedRouteSide(kind: "source" | "target", side: Side) {
    if (!selectedTransition) return;
    changeCanvasLayout(layout => ({ ...layout, edgeRoutes: { ...layout.edgeRoutes, [String(selectedTransition.id)]: { ...selectedRoute, [kind]: side } } }));
    setRouteVersion(value => value + 1);
  }
  async function persistCanvasLayout() {
    setSavingLayout(true);
    try {
      await saveCanvasLayout(workflow, JSON.stringify(cleanCanvasLayout(draftCanvasLayout, workflow)));
      setLayoutDirty(false);
    } finally {
      setSavingLayout(false);
    }
  }
  function cancelConnection() { setPendingConnection(null); setEdges(current => current.filter(edge => edge.id !== "draft-connection")); }
  return <section className="card overflow-hidden">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="eyebrow">Interactive workflow canvas</p><h2 className="mt-1 text-xl font-bold">{workflow.name}</h2><p className="mt-1 text-sm text-slate-500">Applied to: <strong className="text-slate-700">{types.map(type => type.name).join(", ") || "No ticket type"}</strong></p></div><div className="flex items-center gap-2"><span className={`badge ${layoutDirty ? "bg-yellow-900 text-yellow-100" : "bg-blue-900 text-blue-100"}`}>{layoutDirty ? "Unsaved layout changes" : "Layout saved"}</span><button type="button" className="btn-secondary" disabled={!layoutDirty || savingLayout} onClick={() => void persistCanvasLayout()}>{savingLayout ? "Saving…" : "Save layout"}</button><button type="button" className="btn-primary" onClick={() => setAddingState(value => !value)}>+ Add state</button></div></div>
    <div className="mt-3 flex flex-wrap gap-2">{workflow.states.filter(state => !state.isInitial).map(state => <span className="inline-flex gap-1" key={state.id}><button type="button" className="btn-secondary px-2 py-1 text-xs" onClick={() => void renameState(state)}>Rename {(state.name ?? state.key).replaceAll("_", " ")}</button><button type="button" className="btn-secondary px-2 py-1 text-xs text-red-300" onClick={() => void removeState(state)}>Remove</button></span>)}</div>
    {addingState ? <form className="graph-add-state mt-4" onSubmit={event => void submitState(event)}><label className="flex-1"><span className="sr-only">State name</span><input autoFocus required className="field" placeholder="State name, e.g. REQUEST CHANGES" value={newStateName} onChange={event => setNewStateName(event.target.value)} /></label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={newStateTerminal} onChange={event => setNewStateTerminal(event.target.checked)} /> End state</label><button className="btn-primary" disabled={savingState}>{savingState ? "Adding…" : "Add to canvas"}</button><button type="button" className="btn-secondary" onClick={() => setAddingState(false)}>Cancel</button></form> : null}
    {pendingConnection ? <form className="graph-connection-editor mt-4" onSubmit={event => void saveConnection(event)}><div className="min-w-48"><span className="eyebrow">New connection</span><strong className="block">{pendingConnection.from.replaceAll("_", " ")} → {pendingConnection.to.replaceAll("_", " ")}</strong></div><label className="flex-1">Who can choose?<select className="field mt-1" value={connectionParty} onChange={event => setConnectionParty(event.target.value)}><option value="">Any permitted party</option><option value="CLIENT">Client</option><option value="TICKETFLOW1">TicketFlow1</option></select></label><label className="flex-1">Responsibility after<select className="field mt-1" value={connectionResponsibility} onChange={event => setConnectionResponsibility(event.target.value)}><option value="">No change</option><option value="CLIENT">Client</option><option value="TICKETFLOW1">TicketFlow1</option></select></label><button className="btn-primary">Save connection</button><button type="button" className="btn-secondary" onClick={cancelConnection}>Cancel</button></form> : null}
    {selectedTransition ? <form className="graph-connection-editor mt-4" onSubmit={event => void saveSelectedConnection(event)}>
      <div className="min-w-48"><span className="eyebrow">Edit connection</span><strong className="block">{stateName(workflow, selectedTransition.fromStateId)} → {stateName(workflow, selectedTransition.toStateId)}</strong><small className="text-slate-500">Line path is saved with your canvas layout.</small></div>
      <label className="flex-1">Who can choose?<select name="requiredParty" className="field mt-1" defaultValue={selectedTransition.requiredParty ?? ""}><option value="">Any permitted party</option><option value="CLIENT">Client</option><option value="TICKETFLOW1">TicketFlow1</option></select></label>
      <label className="flex-1">Responsibility after<select name="responsibilityAfter" className="field mt-1" defaultValue={selectedTransition.responsibilityAfter ?? ""}><option value="">No change</option><option value="CLIENT">Client</option><option value="TICKETFLOW1">TicketFlow1</option></select></label>
      <label>From side<select className="field mt-1" value={selectedRoute.source} onChange={event => setSelectedRouteSide("source", event.target.value as Side)}>{SIDES.map(side => <option key={side} value={side}>{prettySide(side)}</option>)}</select></label>
      <label>To side<select className="field mt-1" value={selectedRoute.target} onChange={event => setSelectedRouteSide("target", event.target.value as Side)}>{SIDES.map(side => <option key={side} value={side}>{prettySide(side)}</option>)}</select></label>
      <button className="btn-primary">Save connection</button><button type="button" className="btn-secondary" onClick={() => setSelectedEdgeId(null)}>Close</button>
    </form> : null}
    <div className="react-flow-shell mt-5"><ReactFlow nodes={nodes} edges={edges} nodeTypes={workflowNodeTypes} edgeTypes={workflowEdgeTypes} onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} onConnect={connect} onNodeClick={(_, node) => { const edgeId = edgeIdFromRouteNode(node.id); if (edgeId) setSelectedEdgeId(String(edgeId)); }} onEdgeClick={(_, edge) => setSelectedEdgeId(edge.id)} connectionLineType={ConnectionLineType.Step} connectionLineStyle={{ stroke: "rgb(250 204 21)", strokeWidth: 4 }} connectOnClick onEdgesDelete={removed => void deleteEdges(removed)} onNodeDrag={moveRoutePoint} onNodeDragStop={rememberPosition} fitView fitViewOptions={{ padding: 0.12 }} defaultEdgeOptions={{ type: "manualStep" }} deleteKeyCode={["Backspace", "Delete"]} minZoom={0.2} maxZoom={2} colorMode="dark"><MiniMap pannable zoomable /><Controls /><Background gap={20} size={1} /></ReactFlow></div>
    <p className="mt-4 text-xs text-slate-500">Drag state cards and the small yellow bend handles. Each line routes through its handle with straight 90° segments, so loop-back branches can go around nodes instead of being forced through the center. Dashed proposal decisions are protected.</p>
  </section>;
}

function flowElements(workflow: Workflow, canvasLayout: CanvasLayout, routeVersion = 0): { nodes: Node[]; edges: Edge[] } {
  void routeVersion;
  const layout = graphLayout(workflow);
  const statePositions = new Map(workflow.states.map(state => [state.id, canvasLayout.nodes[String(state.id)] ?? layout.positions.get(state.id)!]));
  const stateNodes: Node[] = workflow.states.map(state => ({ id: String(state.id), type: "workflowState", position: statePositions.get(state.id)!, data: { label: `${state.isInitial ? "▶ " : state.isTerminal ? "■ " : ""}${state.key.replaceAll("_", " ")}` }, className: `flow-state ${state.isInitial ? "flow-state-start" : ""} ${state.isTerminal ? "flow-state-end" : ""}` }));
  const routeNodes: Node[] = workflow.transitions.map(edge => {
    const point = canvasLayout.edgePoints[String(edge.id)] ?? defaultRoutePoint(statePositions.get(edge.fromStateId)!, statePositions.get(edge.toStateId)!);
    return {
      id: `route-${edge.id}`,
      type: "routeHandle",
      position: { x: point.x - ROUTE_HANDLE_SIZE / 2, y: point.y - ROUTE_HANDLE_SIZE / 2 },
      data: { label: edge.operationKind === "STANDARD" ? "Move line" : "Move protected line" },
      draggable: true,
      selectable: true,
      connectable: false,
      className: edge.operationKind === "STANDARD" ? "route-handle-node" : "route-handle-node route-handle-protected",
    };
  });
  const edges: Edge[] = workflow.transitions.map(edge => {
    const route = canvasLayout.edgeRoutes[String(edge.id)] ?? defaultRoute;
    const point = canvasLayout.edgePoints[String(edge.id)] ?? defaultRoutePoint(statePositions.get(edge.fromStateId)!, statePositions.get(edge.toStateId)!);
    return {
      id: String(edge.id), source: String(edge.fromStateId), target: String(edge.toStateId),
      sourceHandle: `source-${route.source}`, targetHandle: `target-${route.target}`,
      type: "manualStep", label: edge.operationKind === "STANDARD" ? edge.requiredParty ?? "Choice" : edge.operationKind.replace("PROPOSAL_", ""),
      data: { routeX: point.x, routeY: point.y },
      deletable: edge.operationKind === "STANDARD", animated: edge.operationKind !== "STANDARD",
      markerEnd: edgeMarker(), className: edge.operationKind === "STANDARD" ? "flow-edge" : "flow-edge-protected",
    };
  });
  return { nodes: [...stateNodes, ...routeNodes], edges };
}

function graphLayout(workflow: Workflow) {
  const levels = new Map<number, number>();
  const initial = workflow.states.find(state => state.isInitial) ?? workflow.states[0];
  const queue: number[] = [];
  if (initial) { levels.set(initial.id, 0); queue.push(initial.id); }
  while (queue.length) {
    const fromId = queue.shift()!, nextLevel = levels.get(fromId)! + 1;
    workflow.transitions.filter(edge => edge.fromStateId === fromId).forEach(edge => {
      if (!levels.has(edge.toStateId)) { levels.set(edge.toStateId, nextLevel); queue.push(edge.toStateId); }
    });
  }
  workflow.states.forEach((state, index) => { if (!levels.has(state.id)) levels.set(state.id, Math.max(1, index)); });
  const maxLevel = Math.max(0, ...levels.values());
  // Pull terminal nodes to the final column, making approve/decline branches
  // read like an ER diagram instead of a reordered list.
  workflow.states.filter(state => state.isTerminal).forEach(state => levels.set(state.id, maxLevel));
  const columns = new Map<number, State[]>();
  workflow.states.forEach(state => { const level = levels.get(state.id)!; columns.set(level, [...(columns.get(level) ?? []), state]); });
  const positions = new Map<number, { x: number; y: number }>();
  let tallest = 1;
  columns.forEach((states, level) => { tallest = Math.max(tallest, states.length); states.sort((a, b) => a.sortOrder - b.sortOrder).forEach((state, row) => positions.set(state.id, { x: 40 + level * 270, y: 40 + row * 130 })); });
  return { positions };
}

function normalize(value: string) { return value.trim().toUpperCase().replaceAll(/\s+/g, "_"); }
type Side = "top" | "right" | "bottom" | "left";
type EdgeRoute = { source: Side; target: Side };
type RoutePoint = { x: number; y: number };
type CanvasLayout = { nodes: Record<string, RoutePoint>; edgeRoutes: Record<string, EdgeRoute>; edgePoints: Record<string, RoutePoint> };
const SIDES: Side[] = ["right", "bottom", "left", "top"];
const defaultRoute: EdgeRoute = { source: "right", target: "left" };
const ROUTE_HANDLE_SIZE = 34;
const STATE_NODE_WIDTH = 190;
const STATE_NODE_HEIGHT = 64;
const sidePositions: Record<Side, Position> = { top: Position.Top, right: Position.Right, bottom: Position.Bottom, left: Position.Left };
function WorkflowStateNode({ data }: { data: { label?: string } }) {
  return <div className="workflow-state-node">
    {SIDES.map(side => <Handle className="workflow-handle-source" id={`source-${side}`} key={`source-${side}`} type="source" position={sidePositions[side]} />)}
    {SIDES.map(side => <Handle className="workflow-handle-target" id={`target-${side}`} key={`target-${side}`} type="target" position={sidePositions[side]} />)}
    <span>{data.label}</span>
  </div>;
}
function RouteHandleNode() {
  return <div className="workflow-route-handle" title="Drag to move this line">↕</div>;
}
function ManualStepEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, markerEnd, label, selected, data }: EdgeProps) {
  const route = data as { routeX?: number; routeY?: number } | undefined;
  const routeX = route?.routeX ?? (sourceX + targetX) / 2;
  const routeY = route?.routeY ?? (sourceY + targetY) / 2;
  const sourceStub = offsetPoint(sourceX, sourceY, sourcePosition, 28);
  const targetStub = offsetPoint(targetX, targetY, targetPosition, 28);
  const path = [
    `M ${sourceX} ${sourceY}`,
    `L ${sourceStub.x} ${sourceStub.y}`,
    `L ${routeX} ${sourceStub.y}`,
    `L ${routeX} ${routeY}`,
    `L ${targetStub.x} ${routeY}`,
    `L ${targetStub.x} ${targetStub.y}`,
    `L ${targetX} ${targetY}`,
  ].join(" ");
  return <BaseEdge id={id} path={path} markerEnd={markerEnd} label={label} labelX={routeX} labelY={routeY} className={selected ? "selected-manual-edge" : undefined} interactionWidth={30} />;
}
function offsetPoint(x: number, y: number, position: Position, distance: number) {
  if (position === Position.Left) return { x: x - distance, y };
  if (position === Position.Right) return { x: x + distance, y };
  if (position === Position.Top) return { x, y: y - distance };
  return { x, y: y + distance };
}
function edgeMarker() { return { type: MarkerType.ArrowClosed, width: 26, height: 26, color: "rgb(91 179 255)" }; }
function edgeIdFromRouteNode(nodeId: string) {
  if (!nodeId.startsWith("route-")) return null;
  const edgeId = Number(nodeId.slice("route-".length));
  return Number.isFinite(edgeId) ? edgeId : null;
}
function emptyCanvasLayout(): CanvasLayout { return { nodes: {}, edgeRoutes: {}, edgePoints: {} }; }
function parseCanvasLayout(value?: string | null): CanvasLayout {
  if (!value) return emptyCanvasLayout();
  try {
    const parsed = JSON.parse(value) as Partial<CanvasLayout>;
    return {
      nodes: validPoints(parsed.nodes),
      edgeRoutes: validRoutes(parsed.edgeRoutes),
      edgePoints: validPoints(parsed.edgePoints),
    };
  } catch {
    return emptyCanvasLayout();
  }
}
function cleanCanvasLayout(layout: CanvasLayout, workflow: Workflow): CanvasLayout {
  const nodeIds = new Set(workflow.states.map(state => String(state.id)));
  const edgeIds = new Set(workflow.transitions.map(edge => String(edge.id)));
  return {
    nodes: Object.fromEntries(Object.entries(layout.nodes).filter(([id]) => nodeIds.has(id))),
    edgeRoutes: Object.fromEntries(Object.entries(layout.edgeRoutes).filter(([id]) => edgeIds.has(id))),
    edgePoints: Object.fromEntries(Object.entries(layout.edgePoints).filter(([id]) => edgeIds.has(id))),
  };
}
function validPoints(value: unknown): Record<string, RoutePoint> {
  if (!value || typeof value !== "object") return {};
  return Object.fromEntries(Object.entries(value as Record<string, unknown>).filter(([, point]) => isPoint(point))) as Record<string, RoutePoint>;
}
function validRoutes(value: unknown): Record<string, EdgeRoute> {
  if (!value || typeof value !== "object") return {};
  return Object.fromEntries(Object.entries(value as Record<string, unknown>).filter(([, route]) => isRoute(route))) as Record<string, EdgeRoute>;
}
function isPoint(value: unknown): value is RoutePoint {
  return !!value && typeof value === "object" && Number.isFinite((value as RoutePoint).x) && Number.isFinite((value as RoutePoint).y);
}
function isRoute(value: unknown): value is EdgeRoute {
  return !!value && typeof value === "object" && SIDES.includes((value as EdgeRoute).source) && SIDES.includes((value as EdgeRoute).target);
}
function defaultRoutePoint(from: { x: number; y: number }, to: { x: number; y: number }) {
  return {
    x: (from.x + STATE_NODE_WIDTH / 2 + to.x + STATE_NODE_WIDTH / 2) / 2,
    y: (from.y + STATE_NODE_HEIGHT / 2 + to.y + STATE_NODE_HEIGHT / 2) / 2,
  };
}
function prettySide(side: Side) { return side[0].toUpperCase() + side.slice(1); }
function stateName(workflow: Workflow, id: number) { return workflow.states.find(state => state.id === id)?.key.replaceAll("_", " ") ?? "Unknown"; }
function stringOrNull(value: FormDataEntryValue | null) { return typeof value === "string" && value ? value : null; }
const workflowNodeTypes = { workflowState: WorkflowStateNode, routeHandle: RouteHandleNode };
const workflowEdgeTypes = { manualStep: ManualStepEdge };
