"use client";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Background, Controls, MarkerType, MiniMap, Position, ReactFlow, type Edge, type Node } from "@xyflow/react";
import { AppShell } from "@/components/AppShell";
import { ProposalActions, TicketCommunication, TicketHistory } from "@/components/TicketExtras";
import { SlaBadge, StatusBadge, TransitionButtons } from "@/components/TicketUi";
import { get, patch, post } from "@/lib/api";
import type { TicketDetail } from "@/lib/types";
import { useTicketEvents } from "@/lib/realtime";

type History = { id: number; fromStatus: string | null; toStatus: string; createdAt: string };

/**
 * Ticket detail page.
 *
 * It loads the ticket, status history, workflow map, communication widgets,
 * and transition actions, then switches into edit mode when the user has the
 * right permissions.
 */
export default function TicketPage() { return <AppShell require="TICKET_READ">{user => <Detail canEdit={user.permissions.includes("TICKET_UPDATE")} canAssign={user.permissions.includes("TICKET_ASSIGN")} internal={user.party === "TICKETFLOW1"} />}</AppShell>; }

function Detail({ canEdit, canAssign, internal }: { canEdit: boolean; canAssign: boolean; internal: boolean }) {
  const { ticketKey } = useParams<{ ticketKey: string }>();
  const [ticket, setTicket] = useState<TicketDetail | null>(null), [history, setHistory] = useState<History[]>([]), [error, setError] = useState(""), [editing, setEditing] = useState(false);
  const load = useCallback(async () => { try { const [detail, events] = await Promise.all([get<TicketDetail>(`/tickets/${ticketKey}`), get<History[]>(`/tickets/${ticketKey}/status-history`)]); setTicket(detail); setHistory(events); } catch (error) { setError(error instanceof Error ? error.message : "Could not load ticket."); } }, [ticketKey]);
  useEffect(() => { void load(); }, [load]);
  useTicketEvents(load);
  if (error) return <div className="card text-red-700">{error}</div>;
  if (!ticket) return <div className="card">Loading ticket…</div>;
  return <div className="space-y-4">
    <header className="flex flex-wrap items-start justify-between gap-3"><div><div className="flex flex-wrap items-center gap-2"><h1 className="text-2xl font-bold">{ticket.ticketKey}</h1><StatusBadge value={ticket.status}/>{ticket.sla ? <SlaBadge value={ticket.sla.status}/> : null}</div><h2 className="mt-2 text-lg font-semibold">{ticket.title}</h2></div>{canEdit||canAssign ? <button className="btn-secondary" onClick={() => setEditing(value => !value)}>{editing ? "Close edit" : "Edit ticket"}</button> : null}</header>
    <section className="card py-4"><div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5"><Row k="Type" v={ticket.type}/><Row k="Priority" v={ticket.priority}/><Row k="Responsibility" v={ticket.currentResponsibility}/><Row k="Organization" v={ticket.organization.name}/><Row k="Owner" v={ticket.businessOwner.displayName}/></div><p className="mt-4 border-t pt-4 text-sm leading-6 text-slate-600 whitespace-pre-wrap">{ticket.description}</p></section>
    <TeamPanel ticket={ticket}/>
    <TicketContext ticket={ticket}/>
    {editing ? <Edit ticket={ticket} canEdit={canEdit} canAssign={false} done={async () => { setEditing(false); await load(); }}/> : null}
    {canAssign ? <details className="card"><summary className="cursor-pointer font-bold">Quickly assign ticket lead and developers</summary><div className="mt-4"><Edit ticket={ticket} canEdit={false} canAssign done={load}/></div></details> : null}
    <TicketCommunication ticketKey={ticketKey} internal={internal}/>
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
function TeamPanel({ ticket }: { ticket: TicketDetail }) { return <section className="card py-4"><div className="flex flex-wrap items-start justify-between gap-4"><div><p className="eyebrow">Assigned team</p><h2 className="mt-1 text-sm font-bold">Teams, lead and developers</h2></div><span className="badge bg-blue-900 text-blue-100">{ticket.developers?.length ?? 0} developer{ticket.developers?.length === 1 ? "" : "s"}</span></div><div className="mt-4 grid gap-4 md:grid-cols-3"><div><p className="text-[10px] uppercase tracking-wider text-slate-500">Developer teams</p><div className="mt-2 flex min-h-11 flex-wrap gap-2">{ticket.teams?.length?ticket.teams.map(team=><span className="badge bg-indigo-100 text-indigo-800" key={team.id}>{team.name}</span>):<span className="rounded-lg border border-dashed px-3 py-2 text-xs text-slate-500">No team assigned</span>}</div></div><div><p className="text-[10px] uppercase tracking-wider text-slate-500">Team lead</p><div className="mt-2 rounded-lg border border-blue-500/30 bg-blue-950/20 p-3 text-sm font-semibold">{ticket.ticketLead?.displayName ?? "Not assigned"}</div></div><div><p className="text-[10px] uppercase tracking-wider text-slate-500">Developers</p><div className="mt-2 flex min-h-11 flex-wrap gap-2">{ticket.developers?.length ? ticket.developers.map(person => <span className="badge bg-slate-100 text-slate-700" key={person.id}>{person.displayName}</span>) : <span className="rounded-lg border border-dashed px-3 py-2 text-xs text-slate-500">No developers assigned</span>}</div></div></div></section>; }
function TicketContext({ ticket }: { ticket: TicketDetail }) {
  const dynamicEntries = Object.entries(ticket.dynamicValues ?? {});
  return <section className="card py-4">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div><p className="eyebrow">Ticket context</p><h2 className="mt-1 text-sm font-bold">Subtype, hierarchy and routing</h2></div>
      <Link className="btn-secondary" href={`/tickets/new?parent=${encodeURIComponent(ticket.ticketKey)}`}>Create subticket</Link>
    </div>
    <div className="mt-4 grid gap-4 lg:grid-cols-3">
      <div className="rounded-lg border p-3"><p className="text-[10px] uppercase tracking-wider text-slate-500">Subtype</p><strong className="mt-1 block">{ticket.subtype ?? "No subtype"}</strong>{ticket.targetUserDisplaySnapshot ? <p className="mt-2 text-xs text-slate-500">Target user: {ticket.targetUserDisplaySnapshot}</p> : null}{ticket.resolvedApproverId ? <p className="mt-1 text-xs text-slate-500">Approver ID: {ticket.resolvedApproverId}</p> : null}</div>
      <div className="rounded-lg border p-3"><p className="text-[10px] uppercase tracking-wider text-slate-500">Parent</p>{ticket.parentTicketKey ? <Link className="mt-1 block font-semibold text-blue-400 hover:underline" href={`/tickets/${ticket.parentTicketKey}`}>{ticket.parentTicketKey}</Link> : <span className="mt-1 block text-sm text-slate-500">Top-level ticket</span>}</div>
      <div className="rounded-lg border p-3"><p className="text-[10px] uppercase tracking-wider text-slate-500">Child tickets</p><strong className="mt-1 block">{ticket.childTickets?.length ?? 0}</strong><p className="text-xs text-slate-500">Immediate subtickets</p></div>
    </div>
    {dynamicEntries.length ? <details className="mt-4 rounded-lg border p-3"><summary className="cursor-pointer font-bold">Captured subtype fields</summary><dl className="mt-3 grid gap-3 sm:grid-cols-2">{dynamicEntries.map(([key, value]) => <div className="rounded-lg border p-3" key={key}><dt className="text-[10px] uppercase tracking-wider text-slate-500">{key.replaceAll("_", " ")}</dt><dd className="mt-1 break-words text-sm font-semibold">{formatDynamic(value)}</dd></div>)}</dl></details> : null}
    {ticket.childTickets?.length ? <div className="mt-4 overflow-x-auto rounded-lg border"><table className="w-full min-w-[680px] text-left text-sm"><thead><tr>{["Key", "Title", "Type", "Status", "Responsibility"].map(header => <th className="p-3" key={header}>{header}</th>)}</tr></thead><tbody>{ticket.childTickets.map(child => <tr className="border-t" key={child.ticketKey}><td className="p-3"><Link className="font-semibold text-blue-400 hover:underline" href={`/tickets/${child.ticketKey}`}>{child.ticketKey}</Link></td><td className="max-w-72 p-3"><span className="block truncate" title={child.title}>{child.title}</span></td><td className="p-3">{child.type.replaceAll("_", " ")}</td><td className="p-3"><StatusBadge value={child.status}/></td><td className="p-3">{child.currentResponsibility}</td></tr>)}</tbody></table></div> : null}
  </section>;
}
function formatDynamic(value: unknown) {
  if (Array.isArray(value)) return value.join(", ");
  if (typeof value === "boolean") return value ? "Yes" : "No";
  if (value == null || value === "") return "Not supplied";
  return String(value);
}
type Person={id:number;name:string};
type TeamOption={id:number;name:string};
function Edit({ ticket, canEdit, canAssign, done }: { ticket: TicketDetail; canEdit:boolean; canAssign:boolean; done: () => Promise<void> }) { const [title, setTitle] = useState(ticket.title), [description, setDescription] = useState(ticket.description), [people,setPeople]=useState<Person[]>([]),[teams,setTeams]=useState<TeamOption[]>([]),[leadId,setLeadId]=useState(ticket.ticketLead?String(ticket.ticketLead.id):""),[developerIds,setDeveloperIds]=useState<number[]>(ticket.developers?.map(item=>item.id)??[]),[teamIds,setTeamIds]=useState<number[]>(ticket.teams?.map(item=>item.id)??[]), [saving, setSaving] = useState(false), [message,setMessage]=useState(""); useEffect(()=>{if(canAssign){void get<Person[]>("/reference/ticket-leads").then(setPeople).catch(error=>setMessage(error instanceof Error?error.message:"Could not load assignable people."));void get<TeamOption[]>("/teams").then(setTeams).catch(error=>setMessage(error instanceof Error?error.message:"Could not load teams."))}},[canAssign]); function toggle(id:number){setDeveloperIds(values=>values.includes(id)?values.filter(value=>value!==id):[...values,id])} function toggleTeam(id:number){setTeamIds(values=>values.includes(id)?values.filter(value=>value!==id):[...values,id])} async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); setMessage(""); try { await patch(`/tickets/${ticket.ticketKey}`, { ...(canEdit?{title,description}:{}),...(canAssign?{...(leadId?{ticketLeadId:Number(leadId)}:{}),developerIds,teamIds}: {}) }); await done(); setMessage("Ticket team saved successfully."); } catch(error) { setMessage(error instanceof Error?error.message:"Could not save the ticket team."); } finally { setSaving(false); } } return <form className="card grid gap-4" onSubmit={submit}>{canEdit?<><label>Title<input className="field mt-1" value={title} onChange={event => setTitle(event.target.value)}/></label><label>Description<textarea className="field mt-1 min-h-24" value={description} onChange={event => setDescription(event.target.value)}/></label></>:null}{canAssign?<fieldset className="space-y-3"><legend className="font-bold">Ticket team</legend><label className="block">Ticket lead <span className="text-xs font-normal text-slate-500">(optional)</span><select className="field mt-1" value={leadId} onChange={event=>setLeadId(event.target.value)}><option value="">No team lead selected</option>{people.map(person=><option key={person.id} value={person.id}>{person.name}</option>)}</select></label><div><span className="text-sm font-medium">Developer teams</span><div className="mt-2 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{teams.map(team=><label className={`permission-option ${teamIds.includes(team.id)?"permission-option-selected":""}`} key={team.id}><input type="checkbox" checked={teamIds.includes(team.id)} onChange={()=>toggleTeam(team.id)}/><span><strong>{team.name}</strong><small>{teamIds.includes(team.id)?"Assigned team":"Assign team"}</small></span></label>)}</div></div><div><div className="flex items-center justify-between gap-3"><span className="text-sm font-medium">Developers</span><span className="text-xs text-slate-500">{developerIds.length} selected</span></div><div className="mt-2 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{people.map(person=><label className={`permission-option ${developerIds.includes(person.id)?"permission-option-selected":""}`} key={person.id}><input type="checkbox" checked={developerIds.includes(person.id)} onChange={()=>toggle(person.id)}/><span><strong>{person.name}</strong><small>{developerIds.includes(person.id)?"Assigned developer":"Select as developer"}</small></span></label>)}</div></div></fieldset>:null}<div className="flex flex-wrap items-center gap-3"><button className="btn-primary" disabled={saving}>{saving ? "Saving team…" : canAssign?"Save team assignment":"Save changes"}</button>{message?<span className={`text-sm ${message.includes("successfully")?"text-emerald-400":"text-red-400"}`} role="status">{message}</span>:null}</div></form>; }
