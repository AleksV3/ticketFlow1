"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { SlaBadge, StatusBadge } from "@/components/TicketUi";
import { api, ApiError, get, put } from "@/lib/api";
import { useTicketEvents } from "@/lib/realtime";

export type Ticket = {
  ticketKey: string;
  title: string;
  type: string;
  status: string;
  slaStatus: string;
};

export type Dashboard = {
  activeCount: number;
  closedCount: number;
  byType: Record<string, number>;
  byStatus: Record<string, number>;
  defectsBySeverity: Record<string, number>;
  slaBreached: Ticket[];
  slaDueSoon: Ticket[];
  waitingForClientApproval: Ticket[];
  waitingForClientConfirmation: Ticket[];
  myAssignedTickets: Ticket[];
  myOpenTickets: Ticket[];
  myTeamTickets: Ticket[];
  awaitingMyApproval: Ticket[];
  recentlyUpdated: Ticket[];
};

export type Preferences = {
  dashboardWidgets: string[];
  enabledTicketFilters: string[];
  lastViewedTeamId: number | null;
  theme: string;
  version: number;
};

export const DASHBOARD_WIDGETS = [
  "MY_OPEN_TICKETS",
  "MY_TEAM_TICKETS",
  "TICKETS_BY_STATUS",
  "TICKETS_BY_TYPE",
  "AWAITING_MY_APPROVAL",
  "RECENTLY_UPDATED",
] as const;

const DEFAULT_PREFERENCES: Preferences = {
  dashboardWidgets: [...DASHBOARD_WIDGETS],
  enabledTicketFilters: ["TYPE", "STATUS", "PRIORITY", "TEAM"],
  lastViewedTeamId: null,
  theme: "SYSTEM",
  version: 0,
};

const WIDGET_LABELS: Record<string, string> = {
  MY_OPEN_TICKETS: "My open tickets",
  MY_TEAM_TICKETS: "My team’s tickets",
  TICKETS_BY_STATUS: "Tickets by status",
  TICKETS_BY_TYPE: "Tickets by type",
  AWAITING_MY_APPROVAL: "Awaiting my approval",
  RECENTLY_UPDATED: "Recently updated",
};

const humanize = (value: string) =>
  value.replaceAll("_", " ").toLowerCase().replace(/^./, c => c.toUpperCase());

export default function DashboardPage() {
  return <AppShell require="TICKET_READ">{() => <DashboardContent />}</AppShell>;
}

function DashboardContent() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [preferences, setPreferences] = useState<Preferences>(DEFAULT_PREFERENCES);
  const [draftWidgets, setDraftWidgets] = useState<string[]>(DEFAULT_PREFERENCES.dashboardWidgets);
  const [customizing, setCustomizing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const load = useCallback(() => {
    Promise.all([
      get<Dashboard>("/dashboard"),
      get<Preferences>("/preferences").catch(() => DEFAULT_PREFERENCES),
    ]).then(([dashboard, saved]) => {
      setData(dashboard);
      setPreferences(saved);
      setDraftWidgets(saved.dashboardWidgets);
      setError("");
    }).catch(e => setError(
      e instanceof ApiError ? e.message : "Could not load dashboard."));
  }, []);

  useEffect(() => { void load(); }, [load]);
  useTicketEvents(load);

  async function save() {
    setSaving(true);
    setError("");
    try {
      let saved: Preferences;
      try { saved = await put<Preferences>("/preferences", { ...preferences, dashboardWidgets: draftWidgets }); }
      catch (cause) {
        // Theme/team changes can update the same preference row in another tab.
        // Reload once and merge the widget draft instead of losing the layout.
        const latest = await get<Preferences>("/preferences");
        saved = await put<Preferences>("/preferences", { ...latest, dashboardWidgets: draftWidgets });
      }
      setPreferences(saved);
      setDraftWidgets(saved.dashboardWidgets);
      setCustomizing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save dashboard layout.");
    } finally {
      setSaving(false);
    }
  }

  async function reset() {
    setSaving(true);
    setError("");
    try {
      const saved = await api<Preferences>("/preferences", { method: "DELETE" });
      setPreferences(saved);
      setDraftWidgets(saved.dashboardWidgets);
      setCustomizing(false);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not reset dashboard layout.");
    } finally {
      setSaving(false);
    }
  }

  if (error && !data) return <div className="card text-red-400" role="alert">{error}</div>;
  if (!data) return <div className="card" role="status">Loading dashboard…</div>;

  return <div className="space-y-7">
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className="eyebrow">Workspace overview</p>
        <h1 className="mt-1 text-3xl font-bold">Dashboard</h1>
        <p className="mt-2 text-sm text-slate-500">Choose the information that matters to you.</p>
      </div>
      <div className="flex gap-2">
        <button className="btn-secondary" type="button"
          onClick={() => setCustomizing(value => !value)}>
          {customizing ? "Close customization" : "Customize dashboard"}
        </button>
        <Link className="btn-primary" href="/tickets/new">+ New ticket</Link>
      </div>
    </header>

    {error ? <div className="card text-red-600" role="alert">{error}</div> : null}

    {customizing ? <DashboardPreferencesEditor
      widgets={draftWidgets}
      saving={saving}
      onChange={setDraftWidgets}
      onSave={() => void save()}
      onReset={() => void reset()}
    /> : null}

    <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <Metric label="Active tickets" value={data.activeCount}
        href="/tickets?lifecycle=active" tone="blue" />
      <Metric label="Closed tickets" value={data.closedCount}
        href="/tickets?lifecycle=closed" tone="green" />
      {Object.entries(data.defectsBySeverity).map(([key, value]) =>
        <Metric key={key} label={`${humanize(key)} defects`} value={value}
          href={`/tickets?type=DEFECT&severity=${key}`} tone="amber" />)}
    </section>

    <DashboardWidgetGrid data={data} widgets={preferences.dashboardWidgets} />
  </div>;
}

export function DashboardPreferencesEditor({
  widgets, saving, onChange, onSave, onReset,
}: {
  widgets: string[];
  saving: boolean;
  onChange: (widgets: string[]) => void;
  onSave: () => void;
  onReset: () => void;
}) {
  function toggle(key: string) {
    onChange(widgets.includes(key)
      ? widgets.filter(widget => widget !== key)
      : [...widgets, key]);
  }

  function move(key: string, direction: -1 | 1) {
    const current = widgets.indexOf(key);
    const target = current + direction;
    if (current < 0 || target < 0 || target >= widgets.length) return;
    const next = [...widgets];
    [next[current], next[target]] = [next[target], next[current]];
    onChange(next);
  }

  function drop(key: string) {
    const dragged = (window as Window & { __dashboardDrag?: string }).__dashboardDrag;
    if (!dragged || dragged === key) return;
    const next = widgets.filter(item => item !== dragged);
    const target = next.indexOf(key);
    next.splice(target < 0 ? next.length : target, 0, dragged);
    onChange(next);
    delete (window as Window & { __dashboardDrag?: string }).__dashboardDrag;
  }

  return <section className="card" aria-labelledby="dashboard-customization-title">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div>
        <h2 id="dashboard-customization-title" className="text-lg font-bold">Dashboard widgets</h2>
        <p className="mt-1 text-sm text-slate-500">Show, hide, and reorder your personal layout.</p>
      </div>
      <div className="flex gap-2">
        <button type="button" className="btn-secondary" disabled={saving} onClick={onReset}>
          Reset defaults
        </button>
        <button type="button" className="btn-primary" disabled={saving} onClick={onSave}>
          {saving ? "Saving…" : "Save layout"}
        </button>
      </div>
    </div>
    <ul className="mt-5 grid gap-3 md:grid-cols-2">
      {DASHBOARD_WIDGETS.map(key => {
        const enabled = widgets.includes(key);
        const position = widgets.indexOf(key);
        return <li key={key} draggable onDragStart={() => { (window as Window & { __dashboardDrag?: string }).__dashboardDrag = key; }} onDragOver={event => event.preventDefault()} onDrop={() => drop(key)} className="dashboard-widget-item rounded-xl border border-slate-200 p-3">
          <div className="flex items-center justify-between gap-3">
            <label className="flex cursor-pointer items-center gap-3 text-sm font-semibold">
              <input type="checkbox" aria-label={WIDGET_LABELS[key]} checked={enabled} onChange={() => toggle(key)} />
              <span aria-hidden="true" className="cursor-grab">⠿</span>{WIDGET_LABELS[key]}
            </label>
            <div className="flex gap-1">
              <button type="button" className="btn-secondary px-2 py-1 text-xs"
                aria-label={`Move ${WIDGET_LABELS[key]} up`}
                disabled={!enabled || position === 0}
                onClick={() => move(key, -1)}>↑</button>
              <button type="button" className="btn-secondary px-2 py-1 text-xs"
                aria-label={`Move ${WIDGET_LABELS[key]} down`}
                disabled={!enabled || position === widgets.length - 1}
                onClick={() => move(key, 1)}>↓</button>
            </div>
          </div>
        </li>;
      })}
    </ul>
  </section>;
}

export function DashboardWidgetGrid({ data, widgets }: {
  data: Dashboard;
  widgets: string[];
}) {
  if (widgets.length === 0) {
    return <div className="card text-center text-slate-500">
      No dashboard widgets are visible. Use Customize dashboard to add one.
    </div>;
  }
  return <section className="grid gap-5 lg:grid-cols-2">
    {widgets.map(widget => {
      switch (widget) {
        case "MY_OPEN_TICKETS":
          return <TicketCard key={widget} title="My open tickets"
            description="Open tickets you requested" tickets={data.myOpenTickets}
            href="/tickets?creatorId=me&lifecycle=active" />;
        case "MY_TEAM_TICKETS":
          return <TicketCard key={widget} title="My team’s tickets"
            description="Open work assigned to one of your teams" tickets={data.myTeamTickets}
            href="/tickets?teamId=mine&lifecycle=active" />;
        case "TICKETS_BY_STATUS":
          return <Breakdown key={widget} title="Tickets by workflow state"
            values={data.byStatus} filter="status" />;
        case "TICKETS_BY_TYPE":
          return <Breakdown key={widget} title="Tickets by type"
            values={data.byType} filter="type" />;
        case "AWAITING_MY_APPROVAL":
          return <TicketCard key={widget} title="Awaiting my approval"
            description="Decisions currently assigned to you" tickets={data.awaitingMyApproval}
            href="/tickets?approvalStatus=PENDING" />;
        case "RECENTLY_UPDATED":
          return <TicketCard key={widget} title="Recently updated"
            description="Latest changes in your visible scope" tickets={data.recentlyUpdated}
            href="/tickets?sort=updatedAt,desc" />;
        default:
          return null;
      }
    })}
  </section>;
}

function Metric({ label, value, href, tone }: {
  label: string;
  value: number;
  href: string;
  tone: "blue" | "green" | "amber";
}) {
  return <Link className={`dashboard-metric dashboard-metric-${tone}`} href={href}
    aria-label={`View ${label}`}>
    <div><p>{label}</p><strong>{value}</strong></div><span aria-hidden="true">↗</span>
  </Link>;
}

function TicketCard({ title, description, tickets, href, sla = false }: {
  title: string;
  description: string;
  tickets: Ticket[];
  href: string;
  sla?: boolean;
}) {
  return <article className="dashboard-ticket-card">
    <Link className="dashboard-card-heading" href={href}>
      <span><h2 className="font-bold">{title}</h2><small>{description}</small></span><b>{tickets.length}</b>
    </Link>
    {tickets.length === 0
      ? <p className="dashboard-empty">Nothing needs attention.</p>
      : <ul>{tickets.slice(0, 4).map(ticket =>
        <li key={ticket.ticketKey}><Link href={`/tickets/${ticket.ticketKey}`}>
          <span className="truncate"><strong>{ticket.ticketKey}</strong>
            <small className="truncate">{ticket.title}</small></span>
          <span className="flex shrink-0 gap-1"><StatusBadge value={ticket.status} />
            {sla ? <SlaBadge value={ticket.slaStatus} /> : null}</span>
        </Link></li>)}</ul>}
    <Link className="dashboard-view-link" href={href}>Open filtered list →</Link>
  </article>;
}

function Breakdown({ title, values, filter }: {
  title: string;
  values: Record<string, number>;
  filter: string;
}) {
  const max = Math.max(...Object.values(values), 1);
  return <div className="card">
    <h2 className="text-base font-bold">{title}</h2>
    <div className="mt-4 space-y-2">{Object.entries(values).map(([key, value]) =>
      <Link className="dashboard-breakdown"
        href={`/tickets?${filter}=${encodeURIComponent(key)}`} key={key}>
        <span><span>{humanize(key)}</span><strong>{value}</strong></span>
        <i><b style={{ width: `${value / max * 100}%` }} /></i>
      </Link>)}</div>
  </div>;
}
