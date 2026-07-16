"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { get, patch, post } from "@/lib/api";
import type { CurrentUser } from "@/lib/auth";
import { Background, ConnectionLineType, Controls, MarkerType, MiniMap, Position, ReactFlow, useEdgesState, useNodesState, type Connection, type Edge, type Node } from "@xyflow/react";
import "@xyflow/react/dist/style.css";

type State = { id: number; key: string; isInitial: boolean; isTerminal: boolean; sortOrder: number };
type Transition = { id: number; fromStateId: number; toStateId: number; requiredPermission: string; requiredParty: string | null; responsibilityAfter: string | null; operationKind: string };
type Workflow = { id: number; name: string; organizationId: number | null; version: number; states: State[]; transitions: Transition[] };
type TicketType = { id: number; key: string; name: string; workflowId: number; requiresProposal: boolean };
type Organization = { id: number; name: string };

export default function WorkflowsPage() {
  return <AppShell require="WORKFLOW_MANAGE">{user => <Editor user={user} />}</AppShell>;
}

function Editor({ user }: { user: CurrentUser }) {
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [organizationId, setOrganizationId] = useState(user.organizationId?.toString() ?? "");
  const [workflows, setWorkflows] = useState<Workflow[]>([]);
  const [types, setTypes] = useState<TicketType[]>([]);
  const [selected, setSelected] = useState<Workflow | null>(null);
  const [workflowName, setWorkflowName] = useState("");
  const [initialKey, setInitialKey] = useState("OPEN");
  const [terminalKey, setTerminalKey] = useState("DONE");
  const [typeKey, setTypeKey] = useState("");
  const [typeName, setTypeName] = useState("");
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (user.party === "TICKETFLOW1") {
      get<Organization[]>("/admin/organizations").then(setOrganizations)
        .catch(error => setMessage(error instanceof Error ? error.message : "Could not load organizations."));
    }
  }, [user.party]);

  const load = useCallback(async () => {
    if (!organizationId) { setWorkflows([]); setTypes([]); setSelected(null); return; }
    try {
      const query = `?organizationId=${organizationId}`;
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
  }, [organizationId]);
  useEffect(() => { void load(); }, [load]);

  async function createWorkflow(event: FormEvent) {
    event.preventDefault();
    const first = normalize(initialKey), terminal = normalize(terminalKey);
    if (!organizationId || first === terminal) { setMessage("Choose an organization and use two different state keys."); return; }
    try {
      await post("/admin/workflows", {
        name: workflowName, organizationId: Number(organizationId),
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

  async function addType(event: FormEvent) {
    event.preventDefault(); if (!selected || !organizationId) return;
    try {
      await post("/admin/ticket-types", { key: normalize(typeKey), name: typeName, workflowId: selected.id,
        organizationId: Number(organizationId), requiresProposal: false });
      setTypeKey(""); setTypeName(""); setMessage("Custom ticket type created."); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not create ticket type."); }
  }

  async function applyWorkflowToType(type: TicketType, workflowId: number) {
    if (type.workflowId === workflowId) return;
    try { await patch(`/admin/ticket-types/${type.id}`, { workflowId }); setMessage("Ticket type workflow updated."); await load(); }
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

  return <div className="space-y-6">
    <div><p className="eyebrow">Ticket configuration</p><h1 className="mt-1 text-3xl font-bold">Ticket types apply workflows</h1><p className="mt-2 max-w-3xl text-slate-600">A <strong>ticket type</strong> defines what kind of ticket is created. Each ticket type applies exactly one <strong>workflow</strong>: a branching map of states and allowed choices. Admins can add states and connect one state to multiple next states.</p></div>
    {user.party === "TICKETFLOW1" ? <label className="card block">Organization<select className="field mt-1" value={organizationId} onChange={event => setOrganizationId(event.target.value)}><option value="">Select an organization…</option>{organizations.map(org => <option key={org.id} value={org.id}>{org.name}</option>)}</select></label> : null}
    {!organizationId ? <p className="card">Select an organization before creating or editing configuration.</p> : <div className="space-y-6">
      <section className="card grid gap-5 lg:grid-cols-2"><div><h2 className="font-bold">1. Choose a workflow map</h2><div className="mt-2 grid gap-2 sm:grid-cols-3">{workflows.map(workflow => <button className="block w-full rounded border p-3 text-left hover:bg-slate-50" key={workflow.id} onClick={() => setSelected(workflow)}><strong>{workflow.name}</strong><span className="mt-1 block text-xs text-slate-500">Applied by: {types.filter(type => type.workflowId === workflow.id).map(type => type.name).join(", ") || "No ticket type"}</span><span className="mt-1 block text-xs text-blue-400">{workflow.states.length} states · {workflow.transitions.length} choices</span></button>)}</div></div><div><h2 className="font-bold">2. Select workflow for each ticket type</h2><div className="mt-2 grid gap-2 sm:grid-cols-3">{types.map(type => <label className="block rounded border p-3" key={type.id}><strong>{type.name}</strong><span className="block text-xs text-slate-500">{type.key}</span><select aria-label={`Workflow for ${type.name}`} className="field mt-2" value={type.workflowId} onChange={event => void applyWorkflowToType(type, Number(event.target.value))}>{workflows.map(workflow => <option value={workflow.id} key={workflow.id}>{workflow.name}</option>)}</select></label>)}</div><p className="mt-2 text-xs text-slate-500">For safety, a ticket type can only switch workflows before its first ticket is created.</p></div></section>
      <section className="space-y-6">
        {selected ? <WorkflowMap workflow={selected} types={types.filter(type => type.workflowId === selected.id)} addState={addGraphState} addTransition={addGraphTransition} removeTransition={removeTransition} /> : <div className="card">Select a workflow to view its branching map.</div>}
        <form className="card space-y-3" onSubmit={addType}><h2 className="font-bold">Create ticket type for selected workflow</h2><label className="block">Key<input required disabled={!selected} className="field mt-1" value={typeKey} onChange={event => setTypeKey(event.target.value)} /></label><label className="block">Display name<input required disabled={!selected} className="field mt-1" value={typeName} onChange={event => setTypeName(event.target.value)} /></label><button disabled={!selected} className="btn-primary">Create ticket type</button></form>
        <form className="card grid gap-3 sm:grid-cols-2" onSubmit={createWorkflow}><div className="sm:col-span-2"><p className="eyebrow">New map</p><h2 className="font-bold">Create custom workflow</h2><p className="text-sm text-slate-500">Start with two states, then arrange and connect everything directly in the canvas.</p></div><label className="sm:col-span-2">Workflow name<input className="field mt-1" required value={workflowName} onChange={event => setWorkflowName(event.target.value)} /></label><label>Starting state<input className="field mt-1" required value={initialKey} onChange={event => setInitialKey(event.target.value)} /></label><label>Ending state<input className="field mt-1" required value={terminalKey} onChange={event => setTerminalKey(event.target.value)} /></label><button className="btn-primary sm:col-span-2">Create workflow</button></form>
      </section>
    </div>}
    {message ? <p className="card" role="status">{message}</p> : null}
  </div>;
}

function WorkflowMap({ workflow, types, addState, addTransition, removeTransition }: { workflow: Workflow; types: TicketType[]; addState: (name: string, isTerminal: boolean) => Promise<void>; addTransition: (from: string, to: string, party?: string | null, responsibility?: string | null) => Promise<void>; removeTransition: (edge: Transition) => Promise<void> }) {
  const [addingState, setAddingState] = useState(false);
  const [newStateName, setNewStateName] = useState("");
  const [newStateTerminal, setNewStateTerminal] = useState(false);
  const [savingState, setSavingState] = useState(false);
  const [pendingConnection, setPendingConnection] = useState<{ from: string; to: string } | null>(null);
  const [connectionParty, setConnectionParty] = useState("");
  const [connectionResponsibility, setConnectionResponsibility] = useState("");
  const initial = useMemo(() => flowElements(workflow), [workflow]);
  const [nodes, setNodes, onNodesChange] = useNodesState(initial.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(initial.edges);
  useEffect(() => { setNodes(initial.nodes); setEdges(initial.edges); }, [initial, setEdges, setNodes]);
  const connect = useCallback((connection: Connection) => {
    const from = workflow.states.find(state => String(state.id) === connection.source)?.key;
    const to = workflow.states.find(state => String(state.id) === connection.target)?.key;
    if (!from || !to || from === to) return;
    setPendingConnection({ from, to });
    setEdges(current => [...current.filter(edge => edge.id !== "draft-connection"), {
      id: "draft-connection", source: connection.source!, target: connection.target!,
      label: "Configure…", animated: true, deletable: false,
      markerEnd: { type: MarkerType.ArrowClosed }, className: "flow-edge-draft",
    }]);
  }, [setEdges, workflow.states]);
  const deleteEdges = useCallback(async (removed: Edge[]) => {
    for (const edge of removed) {
      const transition = workflow.transitions.find(item => String(item.id) === edge.id);
      if (transition?.operationKind === "STANDARD") await removeTransition(transition);
    }
  }, [removeTransition, workflow.transitions]);
  const rememberPosition = useCallback((_: unknown, node: Node) => {
    const saved = JSON.parse(localStorage.getItem(`workflow-layout-${workflow.id}`) ?? "{}") as Record<string, { x: number; y: number }>;
    saved[node.id] = node.position; localStorage.setItem(`workflow-layout-${workflow.id}`, JSON.stringify(saved));
  }, [workflow.id]);
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
  function cancelConnection() { setPendingConnection(null); setEdges(current => current.filter(edge => edge.id !== "draft-connection")); }
  return <section className="card overflow-hidden">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="eyebrow">Interactive workflow canvas</p><h2 className="mt-1 text-xl font-bold">{workflow.name}</h2><p className="mt-1 text-sm text-slate-500">Applied to: <strong className="text-slate-700">{types.map(type => type.name).join(", ") || "No ticket type"}</strong></p></div><div className="flex items-center gap-2"><span className="badge bg-blue-900 text-blue-100">Drag nodes · connect handles</span><button type="button" className="btn-primary" onClick={() => setAddingState(value => !value)}>+ Add state</button></div></div>
    {addingState ? <form className="graph-add-state mt-4" onSubmit={event => void submitState(event)}><label className="flex-1"><span className="sr-only">State name</span><input autoFocus required className="field" placeholder="State name, e.g. REQUEST CHANGES" value={newStateName} onChange={event => setNewStateName(event.target.value)} /></label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={newStateTerminal} onChange={event => setNewStateTerminal(event.target.checked)} /> End state</label><button className="btn-primary" disabled={savingState}>{savingState ? "Adding…" : "Add to canvas"}</button><button type="button" className="btn-secondary" onClick={() => setAddingState(false)}>Cancel</button></form> : null}
    {pendingConnection ? <form className="graph-connection-editor mt-4" onSubmit={event => void saveConnection(event)}><div className="min-w-48"><span className="eyebrow">New connection</span><strong className="block">{pendingConnection.from.replaceAll("_", " ")} → {pendingConnection.to.replaceAll("_", " ")}</strong></div><label className="flex-1">Who can choose?<select className="field mt-1" value={connectionParty} onChange={event => setConnectionParty(event.target.value)}><option value="">Any permitted party</option><option value="CLIENT">Client</option><option value="TICKETFLOW1">TicketFlow1</option></select></label><label className="flex-1">Responsibility after<select className="field mt-1" value={connectionResponsibility} onChange={event => setConnectionResponsibility(event.target.value)}><option value="">No change</option><option value="CLIENT">Client</option><option value="TICKETFLOW1">TicketFlow1</option></select></label><button className="btn-primary">Save connection</button><button type="button" className="btn-secondary" onClick={cancelConnection}>Cancel</button></form> : null}
    <div className="react-flow-shell mt-5"><ReactFlow nodes={nodes} edges={edges} onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} onConnect={connect} connectionLineType={ConnectionLineType.SmoothStep} connectionLineStyle={{ stroke: "rgb(250 204 21)", strokeWidth: 4 }} connectOnClick onEdgesDelete={removed => void deleteEdges(removed)} onNodeDragStop={rememberPosition} fitView fitViewOptions={{ padding: 0.12 }} defaultEdgeOptions={{ type: "smoothstep" }} deleteKeyCode={["Backspace", "Delete"]} minZoom={0.2} maxZoom={2} colorMode="dark"><MiniMap pannable zoomable /><Controls /><Background gap={20} size={1} /></ReactFlow></div>
    <p className="mt-4 text-xs text-slate-500">Add a state here, drag it into place, then connect its handles. Select a solid connection and press Delete to remove it. Dashed proposal decisions are protected.</p>
  </section>;
}

function flowElements(workflow: Workflow): { nodes: Node[]; edges: Edge[] } {
  const layout = graphLayout(workflow), saved = typeof window === "undefined" ? {} : JSON.parse(localStorage.getItem(`workflow-layout-${workflow.id}`) ?? "{}") as Record<string, { x: number; y: number }>;
  const nodes: Node[] = workflow.states.map(state => ({ id: String(state.id), position: saved[String(state.id)] ?? layout.positions.get(state.id)!, sourcePosition: Position.Right, targetPosition: Position.Left, data: { label: `${state.isInitial ? "▶ " : state.isTerminal ? "■ " : ""}${state.key.replaceAll("_", " ")}` }, className: `flow-state ${state.isInitial ? "flow-state-start" : ""} ${state.isTerminal ? "flow-state-end" : ""}` }));
  const edges: Edge[] = workflow.transitions.map(edge => ({ id: String(edge.id), source: String(edge.fromStateId), target: String(edge.toStateId), type: "smoothstep", label: edge.operationKind === "STANDARD" ? edge.requiredParty ?? "Choice" : edge.operationKind.replace("PROPOSAL_", ""), deletable: edge.operationKind === "STANDARD", animated: edge.operationKind !== "STANDARD", markerEnd: { type: MarkerType.ArrowClosed }, className: edge.operationKind === "STANDARD" ? "flow-edge" : "flow-edge-protected" }));
  return { nodes, edges };
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
