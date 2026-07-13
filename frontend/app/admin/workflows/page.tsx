"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { get, patch, post } from "@/lib/api";
import type { CurrentUser } from "@/lib/auth";

type State = { id: number; key: string; isInitial: boolean; isTerminal: boolean; sortOrder: number };
type Transition = { id: number; fromStateId: number; toStateId: number; requiredPermission: string; operationKind: string };
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

  return <div className="space-y-6">
    <div><h1 className="text-3xl font-bold">Workflows and ticket types</h1><p className="text-slate-600">Configuration belongs to one organization. Custom transitions are limited to the safe STANDARD operation.</p></div>
    {user.party === "TICKETFLOW1" ? <label className="card block">Organization<select className="field mt-1" value={organizationId} onChange={event => setOrganizationId(event.target.value)}><option value="">Select an organization…</option>{organizations.map(org => <option key={org.id} value={org.id}>{org.name}</option>)}</select></label> : null}
    {!organizationId ? <p className="card">Select an organization before creating or editing configuration.</p> : <div className="grid gap-6 lg:grid-cols-3">
      <section className="card"><h2 className="font-bold">Workflows</h2>{workflows.map(workflow => <button className="mt-2 block w-full rounded border p-3 text-left hover:bg-slate-50" key={workflow.id} onClick={() => setSelected(workflow)}>{workflow.name}</button>)}<h2 className="mt-6 font-bold">Ticket types</h2>{types.map(type => <p className="mt-2" key={type.id}>{type.name} <span className="text-xs text-slate-500">({type.key})</span></p>)}</section>
      <section className="space-y-6 lg:col-span-2">
        <form className="card grid gap-3 sm:grid-cols-2" onSubmit={createWorkflow}><h2 className="font-bold sm:col-span-2">Create custom workflow</h2><label className="sm:col-span-2">Name<input className="field mt-1" required value={workflowName} onChange={event => setWorkflowName(event.target.value)} /></label><label>Initial state<input className="field mt-1" required value={initialKey} onChange={event => setInitialKey(event.target.value)} /></label><label>Terminal state<input className="field mt-1" required value={terminalKey} onChange={event => setTerminalKey(event.target.value)} /></label><button className="btn-primary sm:col-span-2">Create workflow</button></form>
        {selected ? <div className="card"><h2 className="text-xl font-bold">{selected.name}</h2><div className="mt-3 flex flex-wrap gap-2">{selected.states.map(state => <span className="rounded bg-slate-100 px-3 py-1" key={state.id}>{state.key}{state.isInitial ? " · initial" : ""}{state.isTerminal ? " · terminal" : ""}</span>)}</div><p className="mt-4 text-sm text-slate-600">{selected.transitions.length} configured transitions</p></div> : <div className="card">Select a workflow to add a state or ticket type.</div>}
        <form className="card space-y-3" onSubmit={addState}><h2 className="font-bold">Add a state</h2><label className="block">State key<input required disabled={!selected} className="field mt-1" value={stateKey} onChange={event => setStateKey(event.target.value)} /></label><button disabled={!selected} className="btn-primary">Add state safely</button></form>
        <form className="card space-y-3" onSubmit={addType}><h2 className="font-bold">Create ticket type for selected workflow</h2><label className="block">Key<input required disabled={!selected} className="field mt-1" value={typeKey} onChange={event => setTypeKey(event.target.value)} /></label><label className="block">Display name<input required disabled={!selected} className="field mt-1" value={typeName} onChange={event => setTypeName(event.target.value)} /></label><button disabled={!selected} className="btn-primary">Create ticket type</button></form>
      </section>
    </div>}
    {message ? <p className="card" role="status">{message}</p> : null}
  </div>;
}

function normalize(value: string) { return value.trim().toUpperCase().replaceAll(/\s+/g, "_"); }
