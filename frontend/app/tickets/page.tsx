"use client";
/**
 * Ticket list page.
 *
 * It wires the filter controls to the API query string and displays the paged
 * summary table returned by the backend.
 */
import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { Pagination, SlaBadge, StatusBadge } from "@/components/TicketUi";
import { get } from "@/lib/api";
import type { Paged, TicketSummary } from "@/lib/types";

export default function TicketsPage() {
  return <AppShell require="TICKET_READ">{() => <Suspense fallback={<div className="card">Loading filters…</div>}><Tickets /></Suspense>}</AppShell>;
}

function Tickets() {
  const query = useSearchParams(); const router = useRouter();
  const [data, setData] = useState<Paged<TicketSummary> | null>(null); const [error, setError] = useState("");
  const key = query.toString();
  useEffect(() => { get<Paged<TicketSummary>>(`/tickets?${key}`).then(setData).catch(e => setError(e.message)); }, [key]);
  function set(name: string, value: string, reset = true) { const p = new URLSearchParams(key); value ? p.set(name, value) : p.delete(name); if (reset) p.set("page", "0"); router.push(`/tickets?${p}`); }
  return <div><div className="flex items-end justify-between"><div><h1 className="text-3xl font-bold">Tickets</h1><p className="mt-1 text-slate-600">Search and filter the current ticket scope.</p></div><Link className="btn-primary" href="/tickets/new">New ticket</Link></div>
    <div className="card mt-6 grid gap-3 sm:grid-cols-2 lg:grid-cols-5"><label><span className="text-sm">Search</span><input className="field mt-1" defaultValue={query.get("q") ?? ""} onBlur={e => set("q", e.target.value)} /></label><Filter label="Lifecycle" value={query.get("lifecycle") ?? ""} onChange={v => set("lifecycle", v)} options={["active", "closed"]} /><Filter label="Type" value={query.get("type") ?? ""} onChange={v => set("type", v)} options={["CHANGE_REQUEST", "TASK", "DEFECT"]} /><Filter label="SLA" value={query.get("slaStatus") ?? ""} onChange={v => set("slaStatus", v)} options={["OK", "DUE_SOON", "BREACHED", "NOT_APPLICABLE"]} /><Filter label="Assigned" value={query.get("assignedTo") ?? ""} onChange={v => set("assignedTo", v)} options={["me", "unassigned"]} /></div>
    {error ? <div className="card mt-5 text-red-700">{error}</div> : !data ? <div className="card mt-5">Loading tickets…</div> : data.items.length === 0 ? <div className="card mt-5">No tickets match these filters.</div> : <><div className="mt-5 overflow-x-auto rounded-xl border bg-white"><table className="w-full text-left text-sm"><thead className="bg-slate-50"><tr>{["Key", "Title", "Type", "Status", "SLA", "Updated"].map(h => <th className="p-3" key={h}>{h}</th>)}</tr></thead><tbody>{data.items.map(t => <tr className="border-t" key={t.ticketKey}><td className="p-3"><Link className="font-semibold hover:underline" href={`/tickets/${t.ticketKey}`}>{t.ticketKey}</Link></td><td className="p-3">{t.title}</td><td className="p-3">{t.type}</td><td className="p-3"><StatusBadge value={t.status} /></td><td className="p-3"><SlaBadge value={t.slaStatus} /></td><td className="p-3">{new Date(t.updatedAt).toLocaleDateString()}</td></tr>)}</tbody></table></div><Pagination page={data.page} totalPages={data.totalPages} onPage={p => set("page", String(p), false)} /></>}
  </div>;
}
function Filter({ label, value, onChange, options }: { label: string; value: string; onChange: (v: string) => void; options: string[] }) { return <label><span className="text-sm">{label}</span><select className="field mt-1" value={value} onChange={e => onChange(e.target.value)}><option value="">All</option>{options.map(o => <option key={o}>{o}</option>)}</select></label>; }
