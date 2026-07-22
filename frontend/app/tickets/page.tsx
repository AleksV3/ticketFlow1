"use client";
/**
 * Ticket list page.
 *
 * It wires the filter controls to the API query string and displays the paged
 * summary table returned by the backend.
 */
import Link from "next/link";
import { Suspense, useCallback, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { Pagination, SlaBadge, StatusBadge } from "@/components/TicketUi";
import { get } from "@/lib/api";
import type { Paged, TicketSummary } from "@/lib/types";
import { useTicketEvents } from "@/lib/realtime";

export default function TicketsPage() {
  return <AppShell require="TICKET_READ">{() => <Suspense fallback={<div className="card">Loading filters…</div>}><Tickets /></Suspense>}</AppShell>;
}

function Tickets() {
  const query = useSearchParams(); const router = useRouter();
  const [data, setData] = useState<Paged<TicketSummary> | null>(null); const [error, setError] = useState("");
  const key = query.toString();
  const load = useCallback(() => { get<Paged<TicketSummary>>(`/tickets?${key}`).then(value => { setData(value); setError(""); }).catch(e => setError(e.message)); }, [key]);
  useEffect(() => { void load(); }, [load]);
  useTicketEvents(load);
  function set(name: string, value: string, reset = true) { const p = new URLSearchParams(key); value ? p.set(name, value) : p.delete(name); if (reset) p.set("page", "0"); router.push(`/tickets?${p}`); }
  return <div><div className="flex items-end justify-between"><div><h1 className="text-3xl font-bold">Tickets</h1><p className="mt-1 text-slate-600">Search and filter the current ticket scope.</p></div><Link className="btn-primary" href="/tickets/new">New ticket</Link></div>
    <div className="card mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-6"><label className="sm:col-span-2 lg:col-span-2"><span className="text-sm">Search tickets and people</span><input className="field mt-1" defaultValue={query.get("q") ?? ""} placeholder="Key, title, organization, lead or developer..." onBlur={e => set("q", e.target.value)} onKeyDown={e => { if (e.key === "Enter") set("q", e.currentTarget.value); }} /></label><Filter label="Lifecycle" value={query.get("lifecycle") ?? ""} onChange={v => set("lifecycle", v)} options={["active", "closed"]} /><Filter label="Type" value={query.get("type") ?? ""} onChange={v => set("type", v)} options={["TASI", "USR", "DFCT", "REQ", "DEFECT"]} /><label><span className="text-sm">Subtype</span><input className="field mt-1" defaultValue={query.get("subtype") ?? ""} placeholder="FIREWALL, MODIFY..." onBlur={e => set("subtype", e.target.value.toUpperCase())} onKeyDown={e => { if (e.key === "Enter") set("subtype", e.currentTarget.value.toUpperCase()); }}/></label><label><span className="text-sm">Parent key</span><input className="field mt-1" defaultValue={query.get("parentTicketKey") ?? ""} placeholder="TKT-1234" onBlur={e => set("parentTicketKey", e.target.value.toUpperCase())} onKeyDown={e => { if (e.key === "Enter") set("parentTicketKey", e.currentTarget.value.toUpperCase()); }}/></label><Filter label="Severity" value={query.get("severity") ?? ""} onChange={v => set("severity", v)} options={["SEV_1", "SEV_2", "SEV_3", "SEV_4"]} /><Filter label="SLA" value={query.get("slaStatus") ?? ""} onChange={v => set("slaStatus", v)} options={["OK", "DUE_SOON", "BREACHED", "NOT_APPLICABLE"]} /><Filter label="Assigned" value={query.get("assignedTo") ?? ""} onChange={v => set("assignedTo", v)} options={["me", "unassigned"]} /></div>
    {error ? <div className="card mt-5 text-red-700">{error}</div> : !data ? <div className="card mt-5">Loading tickets...</div> : data.items.length === 0 ? <div className="card mt-5">No tickets match these filters.</div> : <><div className="mt-5 overflow-x-auto rounded-xl border bg-white"><table className="w-full min-w-[1200px] text-left text-sm"><thead className="bg-slate-50"><tr>{["Key / ticket", "Type", "Subtype", "Parent", "Severity", "Status", "Team lead", "Developers", "SLA", "Updated"].map(h => <th className="p-3" key={h}>{h}</th>)}</tr></thead><tbody>{data.items.map(t => <tr className="border-t" key={t.ticketKey}><td className="max-w-72 p-3"><Link className="font-semibold hover:underline" href={`/tickets/${t.ticketKey}`}>{t.ticketKey}</Link><span className="mt-1 block truncate text-xs text-slate-500" title={t.title}>{t.title}</span>{t.targetUserDisplaySnapshot ? <span className="mt-1 block text-xs text-blue-400">Target: {t.targetUserDisplaySnapshot}</span> : null}</td><td className="p-3">{t.type.replaceAll("_", " ")}</td><td className="p-3">{t.subtype ? t.subtype.replaceAll("_", " ") : <span className="text-xs text-slate-500">None</span>}</td><td className="p-3">{t.parentTicketKey ? <Link className="text-blue-400 hover:underline" href={`/tickets/${t.parentTicketKey}`}>{t.parentTicketKey}</Link> : <span className="text-xs text-slate-500">Top level</span>}</td><td className="p-3"><Severity value={t.severity} /></td><td className="p-3"><StatusBadge value={t.status} /></td><td className="p-3"><Person value={t.ticketLeadName} empty="Unassigned" /></td><td className="max-w-60 p-3"><Person value={t.developerNames?.join(", ")} empty="No developers" /></td><td className="p-3"><SlaBadge value={t.slaStatus} /></td><td className="p-3">{new Date(t.updatedAt).toLocaleDateString()}</td></tr>)}</tbody></table></div><Pagination page={data.page} totalPages={data.totalPages} onPage={p => set("page", String(p), false)} /></>}
  </div>;
}
function Filter({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: string[] }) { return <label><span className="text-sm">{label}</span><select className="field mt-1" value={value} onChange={e => onChange(e.target.value)}><option value="">All</option>{options.map(o => <option key={o}>{o}</option>)}</select></label>; }
function Severity({ value }: { value: string | null }) { if (!value) return <span className="text-xs text-slate-500">Not applicable</span>; const color = value === "SEV_1" ? "bg-red-100 text-red-800" : value === "SEV_2" ? "bg-amber-100 text-amber-800" : "bg-slate-100 text-slate-700"; return <span className={`badge ${color}`}>{value.replace("_", " ")}</span>; }
function Person({ value, empty }: { value?: string | null; empty: string }) { return value ? <span className="block truncate text-xs font-semibold" title={value}>{value}</span> : <span className="text-xs text-slate-500">{empty}</span>; }
