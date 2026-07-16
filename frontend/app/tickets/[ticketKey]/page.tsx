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

export default function TicketPage() { return <AppShell require="TICKET_READ">{user => <Detail canEdit={user.permissions.includes("TICKET_UPDATE")} canAssign={user.permissions.includes("TICKET_ASSIGN")} />}</AppShell>; }

function Detail({ canEdit, canAssign }: { canEdit: boolean; canAssign: boolean }) {
  const { ticketKey } = useParams<{ ticketKey: string }>();
  const [ticket, setTicket] = useState<TicketDetail | null>(null), [history, setHistory] = useState<History[]>([]), [error, setError] = useState(""), [editing, setEditing] = useState(false);
  const load = useCallback(async () => { try { const [detail, events] = await Promise.all([get<TicketDetail>(`/tickets/${ticketKey}`), get<History[]>(`/tickets/${ticketKey}/status-history`)]); setTicket(detail); setHistory(events); } catch (error) { setError(error instanceof Error ? error.message : "Could not load ticket."); } }, [ticketKey]);
  useEffect(() => { void load(); }, [load]);
  if (error) return <div className="card text-red-700">{error}</div>;
  if (!ticket) return <div className="card">Loading ticket…</div>;
  return <div className="space-y-4">
    <header className="flex flex-wrap items-start justify-between gap-3"><div><div className="flex flex-wrap items-center gap-2"><h1 className="text-2xl font-bold">{ticket.ticketKey}</h1><StatusBadge value={ticket.status}/>{ticket.sla ? <SlaBadge value={ticket.sla.status}/> : null}</div><h2 className="mt-2 text-lg font-semibold">{ticket.title}</h2></div>{canEdit||canAssign ? <button className="btn-secondary" onClick={() => setEditing(value => !value)}>{editing ? "Close edit" : "Edit ticket"}</button> : null}</header>
    <section className="card py-4"><div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-7"><Row k="Type" v={ticket.type}/><Row k="Priority" v={ticket.priority}/><Row k="Responsibility" v={ticket.currentResponsibility}/><Row k="Organization" v={ticket.organization.name}/><Row k="Lead" v={ticket.ticketLead?.displayName ?? "Unassigned"}/><Row k="Developers" v={ticket.developers?.map(item=>item.displayName).join(", ")||"Unassigned"}/><Row k="Owner" v={ticket.businessOwner.displayName}/></div><p className="mt-4 border-t pt-4 text-sm leading-6 text-slate-600 whitespace-pre-wrap">{ticket.description}</p></section>
    {editing ? <Edit ticket={ticket} canEdit={canEdit} canAssign={canAssign} done={async () => { setEditing(false); await load(); }}/> : null}
    <TicketCommunication ticketKey={ticketKey}/>
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_300px]"><ProcessMap ticket={ticket} history={history}/><section className="card py-4"><div className="mb-3"><p className="eyebrow">Next step</p><h2 className="text-sm font-bold">Available actions</h2></div><TransitionButtons allowedTransitions={ticket.allowedTransitions} onTransition={async status => { await post(`/tickets/${ticketKey}/transition`, { toStatus: status }); await load(); }}/></section></div>
    <ProposalActions ticketKey={ticketKey} proposal={ticket.latestProposal} commands={ticket.proposalCommands ?? []} onDone={load}/>
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
  return <section className="card py-4"><div className="mb-2 flex flex-wrap items-center justify-between gap-2"><div><p className="eyebrow">Process overview</p><h2 className="text-sm font-bold">{ticket.processMap.name}</h2></div><div className="flex gap-2 text-[10px] text-slate-500"><span className="text-emerald-400">● Done</span><span className="text-yellow-300">● Current</span><span className="text-blue-400">● Next</span></div></div><div className="react-flow-shell ticket-view-map"><ReactFlow nodes={elements.nodes} edges={elements.edges} nodesDraggable={false} nodesConnectable={false} edgesFocusable={false} elementsSelectable={false} fitView fitViewOptions={{ padding: .12 }} minZoom={.2} maxZoom={2} colorMode="dark"><Controls showInteractive={false}/><Background gap={20} size={1}/></ReactFlow></div></section>;
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
type Person={id:number;name:string};
function Edit({ ticket, canEdit, canAssign, done }: { ticket: TicketDetail; canEdit:boolean; canAssign:boolean; done: () => Promise<void> }) { const [title, setTitle] = useState(ticket.title), [description, setDescription] = useState(ticket.description), [people,setPeople]=useState<Person[]>([]),[leadId,setLeadId]=useState(ticket.ticketLead?String(ticket.ticketLead.id):""),[developerIds,setDeveloperIds]=useState<number[]>(ticket.developers?.map(item=>item.id)??[]), [saving, setSaving] = useState(false); useEffect(()=>{if(canAssign)void get<Person[]>("/reference/ticket-leads").then(setPeople)},[canAssign]); function toggle(id:number){setDeveloperIds(values=>values.includes(id)?values.filter(value=>value!==id):[...values,id])} async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); try { await patch(`/tickets/${ticket.ticketKey}`, { ...(canEdit?{title,description}:{}),...(canAssign?{ticketLeadId:Number(leadId),developerIds}: {}) }); await done(); } finally { setSaving(false); } } return <form className="card grid gap-4" onSubmit={submit}>{canEdit?<><label>Title<input className="field mt-1" value={title} onChange={event => setTitle(event.target.value)}/></label><label>Description<textarea className="field mt-1 min-h-24" value={description} onChange={event => setDescription(event.target.value)}/></label></>:null}{canAssign?<fieldset className="space-y-3"><legend className="font-bold">Ticket team</legend><label className="block">Ticket lead<select className="field mt-1" required value={leadId} onChange={event=>setLeadId(event.target.value)}><option value="">Select a ticket lead</option>{people.map(person=><option key={person.id} value={person.id}>{person.name}</option>)}</select></label><div><span className="text-sm font-medium">Developers</span><div className="mt-2 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{people.map(person=><label className={`permission-option ${developerIds.includes(person.id)?"permission-option-selected":""}`} key={person.id}><input type="checkbox" checked={developerIds.includes(person.id)} onChange={()=>toggle(person.id)}/><span><strong>{person.name}</strong><small>Developer</small></span></label>)}</div></div></fieldset>:null}<button className="btn-primary justify-self-start" disabled={saving||(canAssign&&!leadId)}>{saving ? "Saving…" : "Save changes"}</button></form>; }
