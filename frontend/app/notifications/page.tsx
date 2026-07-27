"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { get, patch, post } from "@/lib/api";

type Notification = { id: number; eventType: string; title: string; message: string; ticketKey: string | null; ticketTitle: string | null; read: boolean; createdAt: string };

export default function NotificationsPage() {
  return <AppShell require="TICKET_READ">{() => <NotificationsContent />}</AppShell>;
}

function NotificationsContent() {
  const [items, setItems] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const load = useCallback(async () => { try { setItems(await get<Notification[]>("/notifications")); setError(""); } catch (e) { setError(e instanceof Error ? e.message : "Could not load notifications."); } finally { setLoading(false); } }, []);
  useEffect(() => { void load(); }, [load]);
  async function markRead(id: number) { await patch(`/notifications/${id}/read`, {}); setItems(current => current.map(item => item.id === id ? { ...item, read: true } : item)); }
  async function markAllRead() { await post("/notifications/read-all"); setItems(current => current.map(item => ({ ...item, read: true }))); }
  const unread = items.filter(item => !item.read).length;
  return <div className="space-y-6"><header className="flex flex-wrap items-end justify-between gap-4"><div><p className="eyebrow">Updates and assignments</p><h1 className="mt-1 text-3xl font-bold">Notifications</h1><p className="mt-2 text-slate-500">Assignments and ticket updates sent to you.</p></div>{unread > 0 ? <button className="btn-secondary" onClick={() => void markAllRead()}>Mark all as read</button> : null}</header>
    {error ? <section className="card text-red-700">{error}</section> : loading ? <section className="card">Loading notifications…</section> : items.length === 0 ? <section className="card"><h2 className="font-bold">You’re all caught up</h2><p className="mt-2 text-sm text-slate-500">New ticket assignments and updates will appear here.</p></section> : <section className="space-y-3">{items.map(item => <article className={`card flex flex-wrap items-start justify-between gap-4 ${item.read ? "opacity-80" : "border-blue-500 bg-blue-950/20"}`} key={item.id}><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><span className={`badge ${item.read ? "bg-slate-100" : "bg-blue-100"}`}>{item.read ? "Read" : "New"}</span><h2 className="font-bold">{item.title}</h2></div><p className="mt-2 text-sm">{item.message}</p><p className="mt-2 text-xs text-slate-500">{new Date(item.createdAt).toLocaleString()}</p></div><div className="flex items-center gap-2">{item.ticketKey ? <Link className="btn-secondary" href={`/tickets/${item.ticketKey}`}>Open {item.ticketKey}</Link> : null}{!item.read ? <button className="btn-primary" onClick={() => void markRead(item.id)}>Mark read</button> : null}</div></article>)}</section>}
  </div>;
}
