"use client";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useParams } from "next/navigation";
import { Background, Controls, MarkerType, MiniMap, Position, ReactFlow, type Edge, type Node } from "@xyflow/react";
import { AppShell } from "@/components/AppShell";
import { ProposalActions, TicketCommunication, TicketHistory } from "@/components/TicketExtras";
import { SlaBadge, StatusBadge, TransitionButtons } from "@/components/TicketUi";
import { get, patch, post } from "@/lib/api";
import type { TicketDetail } from "@/lib/types";

type History = { id: number; fromStatus: string | null; toStatus: string; createdAt: string };

export default function TicketPage() { return <AppShell require="TICKET_READ">{user => <Detail canEdit={user.permissions.includes("TICKET_UPDATE")} />}</AppShell>; }

function Detail({ canEdit }: { canEdit: boolean }) {
  const { ticketKey } = useParams<{ ticketKey: string }>();
  const [ticket, setTicket] = useState<TicketDetail | null>(null), [history, setHistory] = useState<History[]>([]), [error, setError] = useState(""), [editing, setEditing] = useState(false);
  const load = useCallback(async () => { try { const [detail, events] = await Promise.all([get<TicketDetail>(`/tickets/${ticketKey}`), get<History[]>(`/tickets/${ticketKey}/status-history`)]); setTicket(detail); setHistory(events); } catch (error) { setError(error instanceof Error ? error.message : "Could not load ticket."); } }, [ticketKey]);
  useEffect(() => { void load(); }, [load]);
  if (error) return <div className="card text-red-700">{error}</div>;
  if (!ticket) return <div className="card">Loading ticket…</div>;
  return <div className="space-y-4">
    <header className="flex flex-wrap items-start justify-between gap-3"><div><div className="flex flex-wrap items-center gap-2"><h1 className="text-2xl font-bold">{ticket.ticketKey}</h1><StatusBadge value={ticket.status}/>{ticket.sla ? <SlaBadge value={ticket.sla.status}/> : null}</div><h2 className="mt-2 text-lg font-semibold">{ticket.title}</h2></div>{canEdit ? <button className="btn-secondary" onClick={() => setEditing(value => !value)}>{editing ? "Close edit" : "Edit"}</button> : null}</header>
    <section className="card py-4"><div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-6"><Row k="Type" v={ticket.type}/><Row k="Priority" v={ticket.priority}/><Row k="Responsibility" v={ticket.currentResponsibility}/><Row k="Organization" v={ticket.organization.name}/><Row k="Lead" v={ticket.ticketLead?.displayName ?? "Unassigned"}/><Row k="Owner" v={ticket.businessOwner.displayName}/></div><p className="mt-4 border-t pt-4 text-sm leading-6 text-slate-600 whitespace-pre-wrap">{ticket.description}</p></section>
    {editing ? <Edit ticket={ticket} done={async () => { setEditing(false); await load(); }}/> : null}
    <ProcessMap ticket={ticket} history={history}/>
    <section className="card py-4"><div className="mb-3"><p className="eyebrow">Next step</p><h2 className="font-bold">Available actions</h2></div><TransitionButtons allowedTransitions={ticket.allowedTransitions} onTransition={async status => { await post(`/tickets/${ticketKey}/transition`, { toStatus: status }); await load(); }}/></section>
    <ProposalActions ticketKey={ticketKey} proposal={ticket.latestProposal} commands={ticket.proposalCommands ?? []} onDone={load}/>
    <TicketCommunication ticketKey={ticketKey}/>
    <details className="card"><summary className="cursor-pointer font-bold">Status history and audit log</summary><div className="mt-5"><TicketHistory ticketKey={ticketKey}/></div></details>
  </div>;
}

function ProcessMap({ ticket, history }: { ticket: TicketDetail; history: History[] }) {
  const elements = useMemo(() => {
    const visited = new Set(history.map(item => item.toStatus));
    const states = ticket.processMap.states, positions = processLayout(ticket);
    const nodes: Node[] = states.map(state => ({ id: String(state.id), position: positions.get(state.id)!, sourcePosition: Position.Right, targetPosition: Position.Left, data: { label: `${state.isInitial ? "▶ " : state.isTerminal ? "■ " : ""}${state.key.replaceAll("_", " ")}` }, className: `flow-state ${state.isInitial ? "flow-state-start" : ""} ${state.isTerminal ? "flow-state-end" : ""} ${state.key === ticket.status ? "process-current" : ticket.allowedTransitions.includes(state.key) ? "process-next" : visited.has(state.key) ? "process-visited" : ""}` }));
    const edges: Edge[] = ticket.processMap.transitions.map((edge, index) => ({ id: `process-${index}`, source: String(edge.fromStateId), target: String(edge.toStateId), type: "smoothstep", markerEnd: { type: MarkerType.ArrowClosed }, animated: edge.toStateId === states.find(state => state.key === ticket.status)?.id }));
    return { nodes, edges };
  }, [history, ticket]);
  return <section className="card py-4"><div className="mb-3 flex flex-wrap items-center justify-between gap-2"><div><p className="eyebrow">Workflow map · View only</p><h2 className="font-bold">{ticket.processMap.name}</h2></div><div className="flex gap-3 text-xs text-slate-500"><span className="text-emerald-400">● Completed</span><span className="text-yellow-300">● Current</span><span className="text-blue-400">● Available next</span></div></div><div className="react-flow-shell ticket-view-map"><ReactFlow nodes={elements.nodes} edges={elements.edges} nodesDraggable={false} nodesConnectable={false} edgesFocusable={false} elementsSelectable={false} fitView fitViewOptions={{ padding: .12 }} minZoom={.2} maxZoom={2} colorMode="dark"><MiniMap pannable zoomable/><Controls showInteractive={false}/><Background gap={20} size={1}/></ReactFlow></div></section>;
}

function processLayout(ticket: TicketDetail) {
  const states = ticket.processMap.states, transitions = ticket.processMap.transitions, levels = new Map<number, number>(), queue: number[] = [];
  const initial = states.find(state => state.isInitial) ?? states[0]; if (initial) { levels.set(initial.id, 0); queue.push(initial.id); }
  while (queue.length) { const from = queue.shift()!, next = levels.get(from)! + 1; transitions.filter(edge => edge.fromStateId === from).forEach(edge => { if (!levels.has(edge.toStateId)) { levels.set(edge.toStateId, next); queue.push(edge.toStateId); } }); }
  states.forEach((state, index) => { if (!levels.has(state.id)) levels.set(state.id, Math.max(1, index)); });
  const maxLevel = Math.max(0, ...levels.values()); states.filter(state => state.isTerminal).forEach(state => levels.set(state.id, maxLevel));
  const columns = new Map<number, typeof states>(); states.forEach(state => { const level = levels.get(state.id)!; columns.set(level, [...(columns.get(level) ?? []), state]); });
  const positions = new Map<number, { x: number; y: number }>(); columns.forEach((column, level) => column.sort((a, b) => a.sortOrder - b.sortOrder).forEach((state, row) => positions.set(state.id, { x: 40 + level * 270, y: 40 + row * 130 })));
  return positions;
}

function Row({ k, v }: { k: string; v: string }) { return <div><dt className="text-[10px] uppercase tracking-wider text-slate-500">{k}</dt><dd className="truncate text-sm font-medium" title={v}>{v}</dd></div>; }
function Edit({ ticket, done }: { ticket: TicketDetail; done: () => Promise<void> }) { const [title, setTitle] = useState(ticket.title), [description, setDescription] = useState(ticket.description), [saving, setSaving] = useState(false); async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); try { await patch(`/tickets/${ticket.ticketKey}`, { title, description }); await done(); } finally { setSaving(false); } } return <form className="card grid gap-3" onSubmit={submit}><label>Title<input className="field mt-1" value={title} onChange={event => setTitle(event.target.value)}/></label><label>Description<textarea className="field mt-1 min-h-24" value={description} onChange={event => setDescription(event.target.value)}/></label><button className="btn-primary justify-self-start" disabled={saving}>{saving ? "Saving…" : "Save changes"}</button></form>; }
