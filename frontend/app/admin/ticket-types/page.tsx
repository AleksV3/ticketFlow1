"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { WorkflowConfigurationPanels } from "@/components/WorkflowConfigurationPanels";
import { get, post } from "@/lib/api";
import type { CurrentUser } from "@/lib/auth";

type Organization = { id: number; name: string };
type Workflow = { id: number; name: string };
type TicketType = { id: number; key: string; name: string; workflowId: number; organizationId: number | null; active?: boolean; sortOrder?: number; capability?: string; version?: number };

export default function TicketTypesPage() {
  return <AppShell require="WORKFLOW_MANAGE">{user => <TicketTypeAdmin user={user} />}</AppShell>;
}

function TicketTypeAdmin({ user }: { user: CurrentUser }) {
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [scope, setScope] = useState(user.organizationId?.toString() ?? "internal");
  const [workflows, setWorkflows] = useState<Workflow[]>([]), [types, setTypes] = useState<TicketType[]>([]), [message, setMessage] = useState("");
  const [key, setKey] = useState(""), [name, setName] = useState(""), [workflowId, setWorkflowId] = useState("");
  const internal = useMemo(() => organizations.find(item => item.name.toLowerCase() === "ticketflow1 internal"), [organizations]);
  const organizationId = scope === "internal" ? internal?.id ?? null : Number(scope);
  const ready = user.party !== "TICKETFLOW1" || scope !== "internal" || !!organizationId;
  useEffect(() => { if (user.party === "TICKETFLOW1") void get<Organization[]>("/admin/organizations").then(setOrganizations); }, [user.party]);
  const load = useCallback(async () => {
    if (!ready) return;
    try { const query = organizationId ? `?organizationId=${organizationId}` : ""; const [w, t] = await Promise.all([get<Workflow[]>(`/admin/workflows${query}`), get<TicketType[]>(`/admin/ticket-types${query}`)]); setWorkflows(w); setTypes(t); if (!workflowId && w[0]) setWorkflowId(String(w[0].id)); setMessage(""); }
    catch (error) { setMessage(error instanceof Error ? error.message : "Could not load ticket types."); }
  }, [organizationId, ready, workflowId]);
  useEffect(() => { void load(); }, [load]);
  async function create(event: FormEvent) { event.preventDefault(); if (!workflowId) return; try { await post("/admin/ticket-types", { key: key.trim().toUpperCase(), name: name.trim(), workflowId: Number(workflowId), organizationId, requiresProposal: false }); setKey(""); setName(""); setMessage("Ticket type created."); await load(); } catch (error) { setMessage(error instanceof Error ? error.message : "Could not create ticket type."); } }
  return <div className="space-y-6">
    <header><p className="eyebrow">Ticket configuration</p><h1 className="mt-1 text-3xl font-bold">Ticket types</h1><p className="mt-2 max-w-3xl text-slate-500">Manage ticket types, subtypes, dynamic fields, role grants, and routing in one place.</p></header>
    {user.party === "TICKETFLOW1" ? <label className="card block">Configuration scope<select className="field mt-1" value={scope} onChange={event => { setScope(event.target.value); setWorkflowId(""); }}><option value="internal">TicketFlow1 · Internal</option>{organizations.map(org => <option value={org.id} key={org.id}>{org.name} · Client</option>)}</select></label> : null}
    {ready ? <><form className="card grid gap-3 md:grid-cols-[1fr_1fr_1fr_auto] md:items-end" onSubmit={create}><label>Key<input className="field mt-1" required value={key} onChange={event => setKey(event.target.value)} placeholder="CHANGE_REQUEST" /></label><label>Name<input className="field mt-1" required value={name} onChange={event => setName(event.target.value)} placeholder="Change Request" /></label><label>Workflow<select className="field mt-1" required value={workflowId} onChange={event => setWorkflowId(event.target.value)}><option value="">Select workflow</option>{workflows.map(w => <option value={w.id} key={w.id}>{w.name}</option>)}</select></label><button className="btn-primary">Create type</button></form><WorkflowConfigurationPanels organizationId={organizationId ? String(organizationId) : "internal"} workflows={workflows} types={types} reload={load} report={setMessage}/></> : <div className="card">Loading TicketFlow1 Internal scope…</div>}
    {message ? <p className="card" role="status">{message}</p> : null}
  </div>;
}
