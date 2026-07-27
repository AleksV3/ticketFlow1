"use client";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { Background, BaseEdge, Controls, Handle, MarkerType, Position, ReactFlow, type Edge, type EdgeProps, type Node } from "@xyflow/react";
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
    <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_300px]">
      <main className="min-w-0 overflow-hidden rounded-2xl border border-blue-500/70 bg-slate-900/60 shadow-xl">
        <header className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-700 bg-slate-950/70 px-5 py-4"><div className="flex min-w-0 items-center gap-3"><span className="rounded-lg bg-blue-600 px-3 py-1 text-sm font-bold text-white">{ticket.ticketKey}</span><span className="truncate text-lg font-semibold" title={ticket.title}>{ticket.title}</span><StatusBadge value={ticket.status}/>{ticket.sla ? <SlaBadge value={ticket.sla.status}/> : null}</div>{canEdit||canAssign ? <button className="btn-secondary" onClick={() => setEditing(value => !value)}>{editing ? "Close edit" : "Edit ticket"}</button> : null}</header>
        <div className="space-y-4 p-4"><section className="rounded-xl border border-slate-700 bg-slate-900/70 p-4"><div className="grid gap-3 sm:grid-cols-3 lg:grid-cols-5"><Row k="Type" v={ticket.type}/><Row k="Priority" v={ticket.priority}/><Row k="Responsibility" v={ticket.currentResponsibility}/><Row k="Organization" v={ticket.organization.name}/><Row k="Owner" v={ticket.businessOwner.displayName}/></div><div className="mt-4 border-t border-slate-700 pt-4"><p className="text-[10px] uppercase tracking-wider text-slate-500">Description</p><p className="mt-2 text-sm leading-6 text-slate-300 whitespace-pre-wrap">{ticket.description}</p></div></section>
          <TeamPanel ticket={ticket}/><TicketContext ticket={ticket}/>{editing ? <Edit ticket={ticket} canEdit={canEdit} canAssign={false} done={async () => { setEditing(false); await load(); }}/> : null}{canAssign ? <details className="rounded-xl border border-slate-700 p-3"><summary className="cursor-pointer font-bold">Edit assignment</summary><div className="mt-4"><Edit ticket={ticket} canEdit={false} canAssign done={load}/></div></details> : null}</div>
      </main>
      <TicketSidebar ticket={ticket} onTransition={async status => { await post(`/tickets/${ticketKey}/transition`, { toStatus: status }); await load(); }}/>
    </div>
    <TicketCommunication ticketKey={ticketKey} internal={internal}/>
    <ProcessMap ticket={ticket} history={history}/>
    <WorkflowDecisionPanel ticketKey={ticketKey} commands={ticket.workflowCommands ?? []} onDone={load}/>
    <ProposalActions ticketKey={ticketKey} proposal={ticket.latestProposal} commands={ticket.proposalCommands ?? []} onDone={load}/>
    <details className="card"><summary className="cursor-pointer font-bold">Status history and audit log</summary><div className="mt-5"><TicketHistory ticketKey={ticketKey}/></div></details>
  </div>;
}

function ProcessMap({ ticket, history }: { ticket: TicketDetail; history: History[] }) {
  const elements = useMemo(() => {
    const visited = new Set(history.map(item => item.toStatus));
    const states = ticket.processMap.states, positions = processLayout(ticket), layout = parseProcessCanvasLayout(ticket.processMap.canvasLayout);
    const nodes: Node[] = states.map(state => ({ id: String(state.id), type: "workflowState", position: layout.nodes[String(state.id)] ?? positions.get(state.id)!, data: { label: `${state.isInitial ? "▶ " : state.isTerminal ? "■ " : ""}${state.key.replaceAll("_", " ")}` }, className: `flow-state ${state.isInitial ? "flow-state-start" : ""} ${state.isTerminal ? "flow-state-end" : ""} ${state.key === ticket.status ? "process-current" : ticket.allowedTransitions.includes(state.key) ? "process-next" : visited.has(state.key) ? "process-visited" : ""}` }));
    const statePositions = new Map(nodes.map(node => [Number(node.id), node.position]));
    const currentStateId = states.find(state => state.key === ticket.status)?.id;
    const edges: Edge[] = ticket.processMap.transitions.map(edge => {
      const id = String(edge.id);
      const route = layout.edgeRoutes[id] ?? defaultRoute;
      const point = layout.edgePoints[id] ?? defaultRoutePoint(statePositions.get(edge.fromStateId)!, statePositions.get(edge.toStateId)!);
      return { id, source: String(edge.fromStateId), target: String(edge.toStateId), sourceHandle: `source-${route.source}`, targetHandle: `target-${route.target}`, type: "manualStep", data: { routeX: point.x, routeY: point.y }, markerEnd: { type: MarkerType.ArrowClosed, width: 26, height: 26, color: "rgb(91 179 255)" }, animated: edge.toStateId === currentStateId, className: "flow-edge" };
    });
    return { nodes, edges };
  }, [history, ticket]);
  return <section className="card py-4"><div className="mb-2 flex flex-wrap items-center justify-between gap-2"><div><p className="eyebrow">Process overview</p><h2 className="text-sm font-bold">{ticket.processMap.name}</h2></div><div className="flex gap-2 text-[10px] text-slate-500"><span className="text-emerald-400">● Done</span><span className="text-yellow-300">● Current</span><span className="text-blue-400">● Next</span></div></div><div className="react-flow-shell ticket-view-map"><ReactFlow nodes={elements.nodes} edges={elements.edges} nodeTypes={workflowNodeTypes} edgeTypes={workflowEdgeTypes} nodesDraggable={false} nodesConnectable={false} edgesFocusable={false} elementsSelectable={false} fitView fitViewOptions={{ padding: .12 }} minZoom={.2} maxZoom={2} colorMode="dark"><Controls showInteractive={false}/><Background gap={20} size={1}/></ReactFlow></div></section>;
}

function TicketSidebar({ ticket, onTransition }: { ticket: TicketDetail; onTransition: (status: string) => Promise<void> }) {
  return <aside className="card sticky top-4 space-y-5 py-4">
    <div><p className="eyebrow">Ticket metadata</p><h2 className="mt-1 text-lg font-bold">{ticket.ticketKey}</h2></div>
    <dl className="grid gap-3 border-t border-slate-700 pt-4"><Row k="Title" v={ticket.title}/><Row k="Type" v={ticket.type}/><Row k="Subtype" v={ticket.subtype ?? "None"}/><Row k="Created" v={formatDate(ticket.createdAt)}/><Row k="Business owner" v={ticket.businessOwner.displayName}/><Row k="Organization" v={ticket.organization.name}/><Row k="Team" v={ticket.teams?.map(team => team.name).join(", ") || "Not assigned"}/><Row k="Priority" v={ticket.priority}/>{ticket.severity ? <Row k="Severity" v={ticket.severity}/> : null}</dl>
    <div className="border-t border-slate-700 pt-4"><p className="eyebrow">Workflow position</p><p className="mt-1 text-sm font-semibold">{ticket.processMap.name}</p><div className="mt-3 rounded-lg border border-yellow-400/50 bg-yellow-400/10 p-3"><p className="text-[10px] uppercase tracking-wider text-slate-400">Current state</p><strong className="text-yellow-200">{ticket.status.replaceAll("_", " ")}</strong></div></div>
    <div className="border-t border-slate-700 pt-4"><p className="eyebrow">Next move</p><p className="mt-1 text-xs text-slate-400">Available actions from this workflow state.</p><div className="mt-3 grid gap-2"><TransitionButtons allowedTransitions={ticket.allowedTransitions} onTransition={onTransition}/></div>{!ticket.allowedTransitions.length ? <p className="mt-2 text-sm text-slate-500">No status transitions available.</p> : null}</div>
  </aside>;
}

function formatDate(value: string) {
  try { return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value)); } catch { return value; }
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

type Side = "top" | "right" | "bottom" | "left";
type RoutePoint = { x: number; y: number };
type EdgeRoute = { source: Side; target: Side };
type CanvasLayout = { nodes: Record<string, RoutePoint>; edgeRoutes: Record<string, EdgeRoute>; edgePoints: Record<string, RoutePoint> };
const SIDES: Side[] = ["right", "bottom", "left", "top"];
const defaultRoute: EdgeRoute = { source: "right", target: "left" };
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
function ManualStepEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, markerEnd, label, selected, data }: EdgeProps) {
  const route = data as { routeX?: number; routeY?: number } | undefined;
  const routeX = route?.routeX ?? (sourceX + targetX) / 2;
  const routeY = route?.routeY ?? (sourceY + targetY) / 2;
  const sourceStub = offsetPoint(sourceX, sourceY, sourcePosition, 28);
  const targetStub = offsetPoint(targetX, targetY, targetPosition, 28);
  const path = [`M ${sourceX} ${sourceY}`, `L ${sourceStub.x} ${sourceStub.y}`, `L ${routeX} ${sourceStub.y}`, `L ${routeX} ${routeY}`, `L ${targetStub.x} ${routeY}`, `L ${targetStub.x} ${targetStub.y}`, `L ${targetX} ${targetY}`].join(" ");
  return <BaseEdge id={id} path={path} markerEnd={markerEnd} label={label} labelX={routeX} labelY={routeY} className={selected ? "selected-manual-edge" : undefined} interactionWidth={30} />;
}
function offsetPoint(x: number, y: number, position: Position, distance: number) {
  if (position === Position.Left) return { x: x - distance, y };
  if (position === Position.Right) return { x: x + distance, y };
  if (position === Position.Top) return { x, y: y - distance };
  return { x, y: y + distance };
}
function defaultRoutePoint(from: { x: number; y: number }, to: { x: number; y: number }) {
  return { x: (from.x + STATE_NODE_WIDTH / 2 + to.x + STATE_NODE_WIDTH / 2) / 2, y: (from.y + STATE_NODE_HEIGHT / 2 + to.y + STATE_NODE_HEIGHT / 2) / 2 };
}
function parseProcessCanvasLayout(value?: string | null): CanvasLayout {
  if (!value) return { nodes: {}, edgeRoutes: {}, edgePoints: {} };
  try {
    const parsed = JSON.parse(value) as Partial<CanvasLayout>;
    return {
      nodes: validPoints(parsed.nodes),
      edgeRoutes: validRoutes(parsed.edgeRoutes),
      edgePoints: validPoints(parsed.edgePoints),
    };
  } catch {
    return { nodes: {}, edgeRoutes: {}, edgePoints: {} };
  }
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
const workflowNodeTypes = { workflowState: WorkflowStateNode };
const workflowEdgeTypes = { manualStep: ManualStepEdge };

function Row({ k, v }: { k: string; v: string }) { return <div><dt className="text-[10px] uppercase tracking-wider text-slate-500">{k}</dt><dd className="truncate text-sm font-medium" title={v}>{v}</dd></div>; }
function TeamPanel({ ticket }: { ticket: TicketDetail }) { return <section className="card py-4"><div className="flex flex-wrap items-start justify-between gap-4"><div><p className="eyebrow">Assigned team</p><h2 className="mt-1 text-sm font-bold">Teams, lead and developers</h2></div><span className="badge bg-blue-900 text-blue-100">{ticket.developers?.length ?? 0} developer{ticket.developers?.length === 1 ? "" : "s"}</span></div><div className="mt-4 grid gap-4 md:grid-cols-3"><div><p className="text-[10px] uppercase tracking-wider text-slate-500">Developer teams</p><div className="mt-2 flex min-h-11 flex-wrap gap-2">{ticket.teams?.length?ticket.teams.map(team=><span className="badge bg-indigo-100 text-indigo-800" key={team.id}>{team.name}</span>):<span className="rounded-lg border border-dashed px-3 py-2 text-xs text-slate-500">No team assigned</span>}</div></div><div><p className="text-[10px] uppercase tracking-wider text-slate-500">Team lead</p><div className="mt-2 rounded-lg border border-blue-500/30 bg-blue-950/20 p-3 text-sm font-semibold">{ticket.ticketLead?.displayName ?? "Not assigned"}</div></div><div><p className="text-[10px] uppercase tracking-wider text-slate-500">Developers</p><div className="mt-2 flex min-h-11 flex-wrap gap-2">{ticket.developers?.length ? ticket.developers.map(person => <span className="badge bg-slate-100 text-slate-700" key={person.id}>{person.displayName}</span>) : <span className="rounded-lg border border-dashed px-3 py-2 text-xs text-slate-500">No developers assigned</span>}</div></div></div></section>; }
export function TicketContext({ ticket }: { ticket: TicketDetail }) {
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
export function WorkflowDecisionPanel({ ticketKey, commands, onDone }: { ticketKey: string; commands: string[]; onDone: () => Promise<void> }) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  if (!commands.length) return null;
  async function run(command: string) {
    const needsReason = command === "WORKFLOW_REJECT" || command === "CLIENT_REJECT" || command === "CORRECTION_RETURN";
    if (needsReason && !reason.trim()) { setError("A reason is required for this decision."); return; }
    setBusy(command); setError("");
    const path = command === "WORKFLOW_APPROVE" ? "workflow-approve" : command === "WORKFLOW_REJECT" ? "workflow-reject" : command === "CLIENT_ACCEPT" ? "client-accept" : command === "CLIENT_REJECT" ? "client-reject" : "correction-return";
    try {
      await post(`/tickets/${ticketKey}/${path}`, reason.trim() ? { reason } : {});
      setReason("");
      await onDone();
    } catch (error) {
      setError(error instanceof Error ? error.message : "Could not apply workflow decision.");
    } finally {
      setBusy("");
    }
  }
  return <section className="card py-4">
    <div className="mb-3"><p className="eyebrow">Approval decision</p><h2 className="text-sm font-bold">Protected workflow actions</h2></div>
    <label className="block">Reason or note<textarea className="field mt-1 min-h-24" value={reason} maxLength={10000} onChange={event => setReason(event.target.value)} /></label>
    <div className="mt-3 flex flex-wrap gap-2">
      {commands.map(command => <button type="button" className={command.includes("REJECT") || command === "CORRECTION_RETURN" ? "btn-secondary text-red-300" : "btn-primary"} disabled={!!busy} onClick={() => void run(command)} key={command}>{busy === command ? "Saving..." : decisionLabel(command)}</button>)}
    </div>
    {error ? <p className="mt-3 text-sm text-red-400" role="alert">{error}</p> : null}
  </section>;
}
function decisionLabel(command: string) {
  if (command === "WORKFLOW_APPROVE") return "Approve";
  if (command === "WORKFLOW_REJECT") return "Reject";
  if (command === "CLIENT_ACCEPT") return "Accept";
  if (command === "CLIENT_REJECT") return "Reject to development";
  if (command === "CORRECTION_RETURN") return "Return for correction";
  return command.replaceAll("_", " ");
}
type Person={id:number;name:string};
type TeamOption={id:number;name:string};
function Edit({ ticket, canEdit, canAssign, done }: { ticket: TicketDetail; canEdit:boolean; canAssign:boolean; done: () => Promise<void> }) { const [title, setTitle] = useState(ticket.title), [description, setDescription] = useState(ticket.description), [people,setPeople]=useState<Person[]>([]),[teams,setTeams]=useState<TeamOption[]>([]),[leadId,setLeadId]=useState(ticket.ticketLead?String(ticket.ticketLead.id):""),[developerIds,setDeveloperIds]=useState<number[]>(ticket.developers?.map(item=>item.id)??[]),[teamIds,setTeamIds]=useState<number[]>(ticket.teams?.map(item=>item.id)??[]), [saving, setSaving] = useState(false), [message,setMessage]=useState(""); useEffect(()=>{if(canAssign){void get<Person[]>("/reference/ticket-leads").then(setPeople).catch(error=>setMessage(error instanceof Error?error.message:"Could not load assignable people."));void get<TeamOption[]>("/teams").then(setTeams).catch(error=>setMessage(error instanceof Error?error.message:"Could not load teams."))}},[canAssign]); function toggle(id:number){setDeveloperIds(values=>values.includes(id)?values.filter(value=>value!==id):[...values,id])} function toggleTeam(id:number){setTeamIds(values=>values.includes(id)?values.filter(value=>value!==id):[...values,id])} async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); setMessage(""); try { await patch(`/tickets/${ticket.ticketKey}`, { ...(canEdit?{title,description}:{}),...(canAssign?{...(leadId?{ticketLeadId:Number(leadId)}:{}),developerIds,teamIds}: {}) }); await done(); setMessage("Ticket team saved successfully."); } catch(error) { setMessage(error instanceof Error?error.message:"Could not save the ticket team."); } finally { setSaving(false); } } return <form className="card grid gap-4" onSubmit={submit}>{canEdit?<><label>Title<input className="field mt-1" value={title} onChange={event => setTitle(event.target.value)}/></label><label>Description<textarea className="field mt-1 min-h-24" value={description} onChange={event => setDescription(event.target.value)}/></label></>:null}{canAssign?<fieldset className="space-y-3"><legend className="font-bold">Ticket team</legend><label className="block">Ticket lead <span className="text-xs font-normal text-slate-500">(optional)</span><select className="field mt-1" value={leadId} onChange={event=>setLeadId(event.target.value)}><option value="">No team lead selected</option>{people.map(person=><option key={person.id} value={person.id}>{person.name}</option>)}</select></label><div><span className="text-sm font-medium">Developer teams</span><div className="mt-2 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{teams.map(team=><label className={`permission-option ${teamIds.includes(team.id)?"permission-option-selected":""}`} key={team.id}><input type="checkbox" checked={teamIds.includes(team.id)} onChange={()=>toggleTeam(team.id)}/><span><strong>{team.name}</strong><small>{teamIds.includes(team.id)?"Assigned team":"Assign team"}</small></span></label>)}</div></div><div><div className="flex items-center justify-between gap-3"><span className="text-sm font-medium">Developers</span><span className="text-xs text-slate-500">{developerIds.length} selected</span></div><div className="mt-2 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{people.map(person=><label className={`permission-option ${developerIds.includes(person.id)?"permission-option-selected":""}`} key={person.id}><input type="checkbox" checked={developerIds.includes(person.id)} onChange={()=>toggle(person.id)}/><span><strong>{person.name}</strong><small>{developerIds.includes(person.id)?"Assigned developer":"Select as developer"}</small></span></label>)}</div></div></fieldset>:null}<div className="flex flex-wrap items-center gap-3"><button className="btn-primary" disabled={saving}>{saving ? "Saving team…" : canAssign?"Save team assignment":"Save changes"}</button>{message?<span className={`text-sm ${message.includes("successfully")?"text-emerald-400":"text-red-400"}`} role="status">{message}</span>:null}</div></form>; }
