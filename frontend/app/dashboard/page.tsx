"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { SlaBadge, StatusBadge } from "@/components/TicketUi";
import { ApiError, get } from "@/lib/api";
import { useTicketEvents } from "@/lib/realtime";

type Ticket = { ticketKey: string; title: string; type: string; status: string; slaStatus: string };
type Dashboard = { activeCount: number; closedCount: number; byType: Record<string, number>; byStatus: Record<string, number>; defectsBySeverity: Record<string, number>; slaBreached: Ticket[]; slaDueSoon: Ticket[]; waitingForClientApproval: Ticket[]; waitingForClientConfirmation: Ticket[]; myAssignedTickets: Ticket[] };

const humanize = (value: string) => value.replaceAll("_", " ").toLowerCase().replace(/^./, c => c.toUpperCase());

export default function DashboardPage() {
  return <AppShell require="TICKET_READ">{() => <DashboardContent />}</AppShell>;
}

function DashboardContent() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState("");
  const load = useCallback(() => { get<Dashboard>("/dashboard").then(value => { setData(value); setError(""); }).catch(e => setError(e instanceof ApiError ? e.message : "Could not load dashboard.")); }, []);
  useEffect(() => { void load(); }, [load]);
  useTicketEvents(load);
  if (error) return <div className="card text-red-400" role="alert">{error}</div>;
  if (!data) return <div className="card" role="status">Loading dashboard…</div>;

  return <div className="space-y-7">
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div><p className="eyebrow">Workspace overview</p><h1 className="mt-1 text-3xl font-bold">Dashboard</h1><p className="mt-2 text-sm text-slate-500">Select any card to open the tickets behind it.</p></div>
      <Link className="btn-primary" href="/tickets/new">+ New ticket</Link>
    </header>

    <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <Metric label="Active tickets" value={data.activeCount} href="/tickets?lifecycle=active" tone="blue" />
      <Metric label="Closed tickets" value={data.closedCount} href="/tickets?lifecycle=closed" tone="green" />
      {Object.entries(data.defectsBySeverity).map(([key, value]) => <Metric key={key} label={`${humanize(key)} defects`} value={value} href={`/tickets?type=DEFECT&severity=${key}`} tone="amber" />)}
    </section>

    <section className="grid gap-6 xl:grid-cols-[1.4fr_1fr]">
      <div>
        <div className="mb-3 flex items-center justify-between"><h2 className="text-lg font-bold">Needs attention</h2><Link className="text-sm font-semibold text-blue-400 hover:text-blue-300" href="/tickets">View all tickets →</Link></div>
        <div className="grid gap-4 md:grid-cols-2">
          <TicketCard title="SLA breached" description="Tickets already outside their SLA" tickets={data.slaBreached} href="/tickets?slaStatus=BREACHED" sla />
          <TicketCard title="SLA due soon" description="Deadlines that are approaching" tickets={data.slaDueSoon} href="/tickets?slaStatus=DUE_SOON" sla />
          <TicketCard title="Waiting for approval" description="Client decisions are needed" tickets={data.waitingForClientApproval} href="/tickets?status=PROPOSAL" />
          <TicketCard title="Waiting for confirmation" description="Work awaiting client confirmation" tickets={data.waitingForClientConfirmation} href="/tickets?status=CLIENT_CONFIRMATION" />
          <TicketCard title="My assigned tickets" description="Tickets where you are the team lead" tickets={data.myAssignedTickets} href="/tickets?assignedTo=me" />
        </div>
      </div>
      <aside className="space-y-5">
        <Breakdown title="Tickets by type" values={data.byType} filter="type" />
        <Breakdown title="Tickets by workflow state" values={data.byStatus} filter="status" />
      </aside>
    </section>
  </div>;
}

function Metric({ label, value, href, tone }: { label: string; value: number; href: string; tone: "blue" | "green" | "amber" }) {
  return <Link className={`dashboard-metric dashboard-metric-${tone}`} href={href} aria-label={`View ${label}`}><div><p>{label}</p><strong>{value}</strong></div><span aria-hidden="true">↗</span></Link>;
}

function TicketCard({ title, description, tickets, href, sla = false }: { title: string; description: string; tickets: Ticket[]; href: string; sla?: boolean }) {
  return <article className="dashboard-ticket-card">
    <Link className="dashboard-card-heading" href={href}><span><strong>{title}</strong><small>{description}</small></span><b>{tickets.length}</b></Link>
    {tickets.length === 0 ? <p className="dashboard-empty">Nothing needs attention.</p> : <ul>{tickets.slice(0, 4).map(t => <li key={t.ticketKey}><Link href={`/tickets/${t.ticketKey}`}><span className="truncate"><strong>{t.ticketKey}</strong><small className="truncate">{t.title}</small></span><span className="flex shrink-0 gap-1"><StatusBadge value={t.status} />{sla ? <SlaBadge value={t.slaStatus} /> : null}</span></Link></li>)}</ul>}
    <Link className="dashboard-view-link" href={href}>Open filtered list →</Link>
  </article>;
}

function Breakdown({ title, values, filter }: { title: string; values: Record<string, number>; filter: string }) {
  const max = Math.max(...Object.values(values), 1);
  return <div className="card"><h2 className="text-base font-bold">{title}</h2><div className="mt-4 space-y-2">{Object.entries(values).map(([key, value]) => <Link className="dashboard-breakdown" href={`/tickets?${filter}=${encodeURIComponent(key)}`} key={key}><span><span>{humanize(key)}</span><strong>{value}</strong></span><i><b style={{ width: `${value / max * 100}%` }} /></i></Link>)}</div></div>;
}
