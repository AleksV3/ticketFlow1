"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { get, patch, post } from "@/lib/api";
import type { CurrentUser } from "@/lib/auth";

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
  const [stateKey, setStateKey] = useState("");
  const [typeKey, setTypeKey] = useState("");
  const [typeName, setTypeName] = useState("");
  const [fromState, setFromState] = useState("");
  const [toState, setToState] = useState("");
  const [requiredParty, setRequiredParty] = useState("");
  const [responsibilityAfter, setResponsibilityAfter] = useState("");
  const [draggedStateId, setDraggedStateId] = useState<number | null>(null);
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

  async function addState(event: FormEvent) {
    event.preventDefault(); if (!selected) return;
    const states = [...selected.states.map(({ key, isInitial, isTerminal, sortOrder }) => ({ key, isInitial, isTerminal, sortOrder })),
      { key: normalize(stateKey), isInitial: false, isTerminal: false, sortOrder: selected.states.length }];
    try {
      await patch(`/admin/workflows/${selected.id}`, { version: selected.version, states });
      setStateKey(""); setMessage("State added; existing states and transitions were preserved."); await load();
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

  const editableTransitions = (workflow: Workflow) => workflow.transitions.filter(edge => edge.operationKind === "STANDARD");
  const transitionRequest = (workflow: Workflow, edges: Transition[]) => edges.map(edge => ({
    fromState: workflow.states.find(state => state.id === edge.fromStateId)?.key,
    toState: workflow.states.find(state => state.id === edge.toStateId)?.key,
    requiredPermission: edge.requiredPermission,
    requiredParty: edge.requiredParty,
    responsibilityAfter: edge.responsibilityAfter,
    operationKind: "STANDARD",
  }));

  async function addTransition(event: FormEvent) {
    event.preventDefault(); if (!selected || !fromState || !toState || fromState === toState) return;
    const existing = transitionRequest(selected, editableTransitions(selected));
    if (selected.transitions.some(edge => selected.states.find(state => state.id === edge.fromStateId)?.key === fromState && selected.states.find(state => state.id === edge.toStateId)?.key === toState)) { setMessage("That connection already exists."); return; }
    try {
      await patch(`/admin/workflows/${selected.id}`, { version: selected.version, transitions: [...existing, {
        fromState, toState, requiredPermission: "TICKET_TRANSITION",
        requiredParty: requiredParty || null, responsibilityAfter: responsibilityAfter || null, operationKind: "STANDARD",
      }] });
      setMessage(`Branch added: ${fromState} → ${toState}.`); setFromState(""); setToState(""); await load();
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

  async function reorderStates(targetId: number) {
    if (!selected || draggedStateId === null || draggedStateId === targetId) return;
    const from = selected.states.findIndex(state => state.id === draggedStateId);
    const to = selected.states.findIndex(state => state.id === targetId);
    if (from < 0 || to < 0) return;
    const ordered = [...selected.states];
    const [moved] = ordered.splice(from, 1); ordered.splice(to, 0, moved);
    setSelected({ ...selected, states: ordered.map((state, index) => ({ ...state, sortOrder: index })) });
    setDraggedStateId(null);
    try {
      await patch(`/admin/workflows/${selected.id}`, {
        version: selected.version,
        states: ordered.map((state, sortOrder) => ({ key: state.key, isInitial: state.isInitial, isTerminal: state.isTerminal, sortOrder })),
      });
      setMessage("State order saved."); await load();
    } catch (error) { setMessage(error instanceof Error ? error.message : "Could not reorder states."); await load(); }
  }

  return <div className="space-y-6">
    <div><p className="eyebrow">Ticket configuration</p><h1 className="mt-1 text-3xl font-bold">Ticket types apply workflows</h1><p className="mt-2 max-w-3xl text-slate-600">A <strong>ticket type</strong> defines what kind of ticket is created. Each ticket type applies exactly one <strong>workflow</strong>: a branching map of states and allowed choices. Admins can add states and connect one state to multiple next states.</p></div>
    {user.party === "TICKETFLOW1" ? <label className="card block">Organization<select className="field mt-1" value={organizationId} onChange={event => setOrganizationId(event.target.value)}><option value="">Select an organization…</option>{organizations.map(org => <option key={org.id} value={org.id}>{org.name}</option>)}</select></label> : null}
    {!organizationId ? <p className="card">Select an organization before creating or editing configuration.</p> : <div className="grid gap-6 lg:grid-cols-3">
      <section className="card"><h2 className="font-bold">1. Choose a workflow map</h2>{workflows.map(workflow => <button className="mt-2 block w-full rounded border p-3 text-left hover:bg-slate-50" key={workflow.id} onClick={() => setSelected(workflow)}><strong>{workflow.name}</strong><span className="mt-1 block text-xs text-slate-500">Applied by: {types.filter(type => type.workflowId === workflow.id).map(type => type.name).join(", ") || "No ticket type"}</span><span className="mt-1 block text-xs text-blue-400">{workflow.states.length} states · {workflow.transitions.length} choices</span></button>)}<h2 className="mt-6 font-bold">2. Ticket type → workflow</h2>{types.map(type => <button type="button" className="mt-2 block w-full rounded border p-3 text-left hover:bg-slate-50" key={type.id} onClick={() => setSelected(workflows.find(workflow => workflow.id === type.workflowId) ?? null)}><strong>{type.name}</strong> <span className="text-xs text-slate-500">({type.key})</span><span className="mt-1 block text-sm"><span className="text-blue-400">applies →</span> {workflows.find(workflow => workflow.id === type.workflowId)?.name ?? "Unavailable"}</span></button>)}</section>
      <section className="space-y-6 lg:col-span-2">
        <form className="card grid gap-3 sm:grid-cols-2" onSubmit={createWorkflow}><h2 className="font-bold sm:col-span-2">Create custom workflow</h2><label className="sm:col-span-2">Name<input className="field mt-1" required value={workflowName} onChange={event => setWorkflowName(event.target.value)} /></label><label>Initial state<input className="field mt-1" required value={initialKey} onChange={event => setInitialKey(event.target.value)} /></label><label>Terminal state<input className="field mt-1" required value={terminalKey} onChange={event => setTerminalKey(event.target.value)} /></label><button className="btn-primary sm:col-span-2">Create workflow</button></form>
        {selected ? <WorkflowMap workflow={selected} types={types.filter(type => type.workflowId === selected.id)} removeTransition={removeTransition} /> : <div className="card">Select a workflow to view its branching map.</div>}
        <form className="card grid gap-3 sm:grid-cols-2" onSubmit={addTransition}><div className="sm:col-span-2"><h2 className="font-bold">Add another choice / branch</h2><p className="text-sm text-slate-500">Multiple connections may leave the same state, such as Approve, Decline, or Request changes.</p></div><SelectState label="From state" value={fromState} set={setFromState} states={selected?.states ?? []}/><SelectState label="To state" value={toState} set={setToState} states={selected?.states ?? []}/><label>Who can choose it?<select className="field mt-1" value={requiredParty} onChange={event => setRequiredParty(event.target.value)}><option value="">Any permitted party</option><option value="CLIENT">Client</option><option value="TICKETFLOW1">TicketFlow1</option></select></label><label>Responsibility after<select className="field mt-1" value={responsibilityAfter} onChange={event => setResponsibilityAfter(event.target.value)}><option value="">No change</option><option value="CLIENT">Client</option><option value="TICKETFLOW1">TicketFlow1</option></select></label><button disabled={!selected || !fromState || !toState} className="btn-primary sm:col-span-2">Add branch</button></form>
        <form className="card space-y-3" onSubmit={addState}><h2 className="font-bold">Add a state</h2><label className="block">State key<input required disabled={!selected} className="field mt-1" value={stateKey} onChange={event => setStateKey(event.target.value)} /></label><button disabled={!selected} className="btn-primary">Add state safely</button></form>
        <form className="card space-y-3" onSubmit={addType}><h2 className="font-bold">Create ticket type for selected workflow</h2><label className="block">Key<input required disabled={!selected} className="field mt-1" value={typeKey} onChange={event => setTypeKey(event.target.value)} /></label><label className="block">Display name<input required disabled={!selected} className="field mt-1" value={typeName} onChange={event => setTypeName(event.target.value)} /></label><button disabled={!selected} className="btn-primary">Create ticket type</button></form>
      </section>
    </div>}
    {message ? <p className="card" role="status">{message}</p> : null}
  </div>;
}

function WorkflowMap({ workflow, types, removeTransition }: { workflow: Workflow; types: TicketType[]; removeTransition: (edge: Transition) => Promise<void> }) {
  const layout = graphLayout(workflow);
  return <section className="card overflow-hidden">
    <div className="flex flex-wrap items-start justify-between gap-3"><div><p className="eyebrow">Branching workflow map</p><h2 className="mt-1 text-xl font-bold">{workflow.name}</h2><p className="mt-1 text-sm text-slate-500">Applied to ticket type{types.length === 1 ? "" : "s"}: <strong className="text-slate-700">{types.map(type => type.name).join(", ") || "None yet"}</strong></p></div><span className="badge bg-blue-900 text-blue-100">{workflow.transitions.length} allowed choices</span></div>
    <div className="workflow-canvas-scroll mt-6"><div className="workflow-canvas" style={{ width: layout.width, height: layout.height }}>
      <svg className="workflow-connectors" width={layout.width} height={layout.height} aria-hidden="true"><defs><marker id={`arrow-${workflow.id}`} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" /></marker></defs>{workflow.transitions.map(edge => { const from = layout.positions.get(edge.fromStateId), to = layout.positions.get(edge.toStateId); if (!from || !to) return null; const x1 = from.x + 190, y1 = from.y + 38, x2 = to.x, y2 = to.y + 38, bend = Math.max(45, Math.abs(x2 - x1) / 2); return <path key={edge.id} className={edge.operationKind === "STANDARD" ? "workflow-edge" : "workflow-edge workflow-edge-protected"} markerEnd={`url(#arrow-${workflow.id})`} d={`M ${x1} ${y1} C ${x1 + bend} ${y1}, ${x2 - bend} ${y2}, ${x2} ${y2}`} />; })}</svg>
      {workflow.states.map(state => { const position = layout.positions.get(state.id)!; return <article className={`workflow-node ${state.isInitial ? "workflow-node-start" : ""} ${state.isTerminal ? "workflow-node-end" : ""}`} style={{ left: position.x, top: position.y }} key={state.id}><span className="workflow-node-kind">{state.isInitial ? "START" : state.isTerminal ? "END" : "STATE"}</span><strong>{state.key.replaceAll("_", " ")}</strong><span className="workflow-node-count">{workflow.transitions.filter(edge => edge.fromStateId === state.id).length} outgoing choice(s)</span></article>; })}
      {workflow.transitions.map(edge => { const from = layout.positions.get(edge.fromStateId), to = layout.positions.get(edge.toStateId); if (!from || !to) return null; return <div className="workflow-edge-label" style={{ left: (from.x + to.x) / 2 + 82, top: (from.y + to.y) / 2 + 44 }} key={`label-${edge.id}`}><span>{edge.operationKind === "STANDARD" ? edge.requiredParty ? edge.requiredParty : "Choice" : edge.operationKind.replace("PROPOSAL_", "")}</span>{edge.operationKind === "STANDARD" ? <button type="button" title="Remove connection" aria-label="Remove connection" onClick={() => void removeTransition(edge)}>×</button> : <span title="Protected business decision">◆</span>}</div>; })}
    </div></div>
    <p className="mt-4 text-xs text-slate-500">Each arrow is an allowed user choice. More than one arrow from a state creates a branch. Protected proposal decisions remain available even when admins edit ordinary branches.</p>
  </section>;
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
  return { positions, width: Math.max(760, 80 + (maxLevel + 1) * 270), height: Math.max(260, 90 + tallest * 130) };
}

function SelectState({ label, value, set, states }: { label: string; value: string; set: (value: string) => void; states: State[] }) {
  return <label>{label}<select required className="field mt-1" value={value} onChange={event => set(event.target.value)}><option value="">Select a state…</option>{states.map(state => <option value={state.key} key={state.id}>{state.key.replaceAll("_", " ")}</option>)}</select></label>;
}

function normalize(value: string) { return value.trim().toUpperCase().replaceAll(/\s+/g, "_"); }
