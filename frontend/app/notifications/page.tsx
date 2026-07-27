"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { get, patch, post } from "@/lib/api";
import { useTicketEvents } from "@/lib/realtime";

type Notification = { id: number; eventType: string; title: string; message: string; ticketKey: string | null; ticketTitle: string | null; actorId: number | null; actorDisplayName: string | null; read: boolean; createdAt: string };

export default function NotificationsPage() {
  return <AppShell require="TICKET_READ">{() => <NotificationsContent />}</AppShell>;
}

function NotificationsContent() {
  const [items, setItems] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [ticketFilter, setTicketFilter] = useState("");
  const [eventFilter, setEventFilter] = useState("");
  const [readFilter, setReadFilter] = useState<"all" | "unread" | "read">("all");
  const load = useCallback(async () => { try { setItems(await get<Notification[]>("/notifications")); setError(""); } catch (e) { setError(e instanceof Error ? e.message : "Could not load notifications."); } finally { setLoading(false); } }, []);
  useEffect(() => { void load(); }, [load]);
  useTicketEvents(load);
  useEffect(() => { const timer = window.setInterval(() => void load(), 30000); return () => window.clearInterval(timer); }, [load]);
  async function markRead(id: number) { await patch(`/notifications/${id}/read`, {}); setItems(current => current.map(item => item.id === id ? { ...item, read: true } : item)); }
  async function markAllRead() { await post("/notifications/read-all"); setItems(current => current.map(item => ({ ...item, read: true }))); }
  const unread = items.filter(item => !item.read).length;
  const ticketOptions = useMemo(() => [...new Set(items.filter(item => item.ticketKey).map(item => item.ticketKey as string))].sort(), [items]);
  const eventOptions = useMemo(() => [...new Set(items.map(item => item.eventType))].sort(), [items]);
  const filteredItems = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return items.filter(item => {
      const matchesSearch = !needle || [item.ticketKey, item.ticketTitle, item.title, item.message, item.actorDisplayName]
        .filter(Boolean).some(value => value!.toLowerCase().includes(needle));
      const matchesTicket = !ticketFilter || item.ticketKey === ticketFilter;
      const matchesEvent = !eventFilter || item.eventType === eventFilter;
      const matchesRead = readFilter === "all" || (readFilter === "read" ? item.read : !item.read);
      return matchesSearch && matchesTicket && matchesEvent && matchesRead;
    });
  }, [items, search, ticketFilter, eventFilter, readFilter]);
  const hasFilters = Boolean(search.trim() || ticketFilter || eventFilter || readFilter !== "all");
  function clearFilters() { setSearch(""); setTicketFilter(""); setEventFilter(""); setReadFilter("all"); }
  return <div className="space-y-6"><header className="flex flex-wrap items-end justify-between gap-4"><div><p className="eyebrow">Updates and assignments</p><h1 className="mt-1 text-3xl font-bold">Notifications</h1><p className="mt-2 text-slate-500">Assignments and ticket updates sent to you.</p></div>{unread > 0 ? <button className="btn-secondary" onClick={() => void markAllRead()}>Mark all as read</button> : null}</header>
    {error ? <section className="card text-red-700">{error}</section> : loading ? <section className="card">Loading notifications…</section> : items.length === 0 ? <section className="card"><h2 className="font-bold">You’re all caught up</h2><p className="mt-2 text-sm text-slate-500">New ticket assignments and updates will appear here.</p></section> : <>
      <section className="card grid gap-3 md:grid-cols-[minmax(0,1fr)_minmax(12rem,auto)_minmax(11rem,auto)_auto] md:items-end">
        <label className="block"><span className="text-sm font-medium">Search tickets</span><input className="field mt-1" value={search} onChange={event => setSearch(event.target.value)} placeholder="Ticket key, title, or update..." /></label>
        <label className="block"><span className="text-sm font-medium">Ticket filter</span><select className="field mt-1" value={ticketFilter} onChange={event => setTicketFilter(event.target.value)}><option value="">All tickets</option>{ticketOptions.map(key => <option value={key} key={key}>{key}</option>)}</select></label>
        <label className="block"><span className="text-sm font-medium">Update type</span><select className="field mt-1" value={eventFilter} onChange={event => setEventFilter(event.target.value)}><option value="">All updates</option>{eventOptions.map(type => <option value={type} key={type}>{type.replaceAll("_", " ")}</option>)}</select></label>
        <div className="flex items-end gap-2"><label className="block"><span className="text-sm font-medium">Status</span><select className="field mt-1" value={readFilter} onChange={event => setReadFilter(event.target.value as typeof readFilter)}><option value="all">All</option><option value="unread">Unread</option><option value="read">Read</option></select></label>{hasFilters ? <button className="btn-secondary" onClick={clearFilters}>Clear</button> : null}</div>
        <p className="text-xs text-slate-500 md:col-span-full">Showing {filteredItems.length} of {items.length} notifications.</p>
      </section>
      {filteredItems.length === 0 ? <section className="card"><h2 className="font-bold">No matching notifications</h2><p className="mt-2 text-sm text-slate-500">Try another ticket search or clear the filters.</p></section> : <section className="space-y-3">{filteredItems.map(item => <article className={`card flex flex-wrap items-start justify-between gap-4 ${item.read ? "opacity-80" : "border-blue-500 bg-blue-950/20"}`} key={item.id}><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><span className={`badge ${item.read ? "bg-slate-100" : "bg-blue-100"}`}>{item.read ? "Read" : "New"}</span><h2 className="font-bold">{item.title}</h2></div><p className="mt-2 text-sm">{item.message}</p><p className="mt-2 text-xs text-slate-500">{item.actorDisplayName ? `By ${item.actorDisplayName} · ` : ""}{new Date(item.createdAt).toLocaleString()}</p><p className="mt-1 text-xs uppercase tracking-wide text-slate-500">{item.eventType.replaceAll("_", " ")}</p></div><div className="flex items-center gap-2">{item.ticketKey ? <Link className="btn-secondary" href={`/tickets/${item.ticketKey}`} onClick={async event => { if (!item.read) { event.preventDefault(); await markRead(item.id); window.location.assign(`/tickets/${item.ticketKey}`); } }}>Open {item.ticketKey}</Link> : null}{!item.read ? <button className="btn-primary" onClick={() => void markRead(item.id)}>Mark read</button> : null}</div></article>)}</section>}
    </>}
  </div>;
}
