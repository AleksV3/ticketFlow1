"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { get, post } from "@/lib/api";

type Person = { id: number; displayName: string };
type Comment = { id: number; author: Person; body: string; visibility: "PUBLIC" | "INTERNAL"; createdAt: string };
type Attachment = { id: number; uploadedBy: Person; fileName: string; contentType: string; sizeBytes: number; createdAt: string };
type Audit = { id: number; actor: Person; action: string; fieldName?: string; createdAt: string };
type History = { id: number; fromStatus?: string; toStatus: string; changedBy: Person; createdAt: string };
type Proposal = { id: number; description: string; estimatedDeliveryDate: string | null; effortEstimate: string | null; status: string; version?: number };

export function TicketActivity({ ticketKey }: { ticketKey: string }) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [attachments, setAttachments] = useState<Attachment[]>([]);
  const [audit, setAudit] = useState<Audit[]>([]);
  const [history, setHistory] = useState<History[]>([]);
  const [body, setBody] = useState("");
  const [visibility, setVisibility] = useState<"PUBLIC" | "INTERNAL">("PUBLIC");
  const [error, setError] = useState("");
  const load = useCallback(async () => {
    try {
      const [c, a, log, h] = await Promise.all([
        get<Comment[]>(`/tickets/${ticketKey}/comments`), get<Attachment[]>(`/tickets/${ticketKey}/attachments`),
        get<Audit[]>(`/tickets/${ticketKey}/audit-log`), get<History[]>(`/tickets/${ticketKey}/status-history`),
      ]);
      setComments(c); setAttachments(a); setAudit(log); setHistory(h); setError("");
    } catch (e) { setError(e instanceof Error ? e.message : "Could not load activity"); }
  }, [ticketKey]);
  useEffect(() => { void load(); }, [load]);
  async function addComment(e: FormEvent) {
    e.preventDefault(); if (!body.trim()) return;
    try { await post(`/tickets/${ticketKey}/comments`, { body, visibility }); setBody(""); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : "Could not add comment"); }
  }
  return <div className="grid gap-6 xl:grid-cols-2">
    <section className="card"><h2 className="text-lg font-bold">Comments</h2><form className="my-4 space-y-3" onSubmit={addComment}>
      <label className="block">Message<textarea className="field mt-1" value={body} maxLength={10000} required onChange={e => setBody(e.target.value)} /></label>
      <label className="block">Visibility<select className="field mt-1" value={visibility} onChange={e => setVisibility(e.target.value as "PUBLIC" | "INTERNAL")}><option value="PUBLIC">Public</option><option value="INTERNAL">Internal</option></select></label>
      <button className="btn-primary">Add comment</button></form>{error ? <p role="alert" className="text-red-700">{error}</p> : null}
      <div className="space-y-3">{comments.map(c => <article className="rounded border p-3" key={c.id}><div className="flex justify-between gap-3 text-sm"><strong>{c.author.displayName}</strong><span>{c.visibility}</span></div><p className="mt-2 whitespace-pre-wrap">{c.body}</p><time className="text-xs text-slate-500">{new Date(c.createdAt).toLocaleString()}</time></article>)}</div>
    </section>
    <div className="space-y-6">
      <section className="card"><h2 className="mb-3 text-lg font-bold">Attachment references</h2>{attachments.length ? <ul className="space-y-2">{attachments.map(a => <li key={a.id}><strong>{a.fileName}</strong> <span className="text-sm text-slate-500">({Math.ceil(a.sizeBytes / 1024)} KB, {a.contentType})</span></li>)}</ul> : <p className="text-slate-500">No attachments referenced.</p>}</section>
      <section className="card"><h2 className="mb-3 text-lg font-bold">Status history</h2><ol className="space-y-2">{history.map(h => <li key={h.id}><strong>{h.fromStatus ?? "Created"} → {h.toStatus}</strong><div className="text-sm text-slate-500">{h.changedBy.displayName} · {new Date(h.createdAt).toLocaleString()}</div></li>)}</ol></section>
      <section className="card"><h2 className="mb-3 text-lg font-bold">Audit log</h2><ol className="space-y-2">{audit.map(a => <li key={a.id}><strong>{a.action.replaceAll("_", " ")}</strong>{a.fieldName ? ` · ${a.fieldName}` : ""}<div className="text-sm text-slate-500">{a.actor.displayName} · {new Date(a.createdAt).toLocaleString()}</div></li>)}</ol></section>
    </div>
  </div>;
}

export function ProposalActions({ ticketKey, proposal, commands, onDone }: { ticketKey: string; proposal?: Proposal | null; commands: string[]; onDone: () => void }) {
  const [description, setDescription] = useState(""); const [date, setDate] = useState(""); const [effort, setEffort] = useState(""); const [comment, setComment] = useState(""); const [error, setError] = useState("");
  async function run(path: string, payload: object) { try { await post(path, payload); setError(""); onDone(); } catch (e) { setError(e instanceof Error ? e.message : "Proposal action failed"); } }
  if (!commands.length && !proposal) return null;
  return <section className="card space-y-4"><h2 className="text-lg font-bold">Change proposal</h2>{proposal ? <div><p className="whitespace-pre-wrap">{proposal.description}</p><p className="text-sm text-slate-500">{proposal.effortEstimate} · delivery {proposal.estimatedDeliveryDate} · {proposal.status}</p></div> : null}
    {commands.includes("CREATE") ? <form className="space-y-3" onSubmit={e => { e.preventDefault(); void run(`/tickets/${ticketKey}/proposals`, { description, estimatedDeliveryDate: date, effortEstimate: effort }); }}><label className="block">Proposal<textarea className="field mt-1" required value={description} onChange={e => setDescription(e.target.value)} /></label><label className="block">Estimated delivery<input className="field mt-1" type="date" required value={date} onChange={e => setDate(e.target.value)} /></label><label className="block">Effort estimate<input className="field mt-1" required value={effort} onChange={e => setEffort(e.target.value)} /></label><button className="btn-primary">Create proposal</button></form> : null}
    {proposal && (commands.includes("APPROVE") || commands.includes("REJECT")) ? <div className="space-y-3"><label className="block">Decision note<textarea className="field mt-1" value={comment} onChange={e => setComment(e.target.value)} /></label><div className="flex gap-3">{commands.includes("APPROVE") ? <button className="btn-primary" onClick={() => void run(`/proposals/${proposal.id}/approve`, { comment, version: proposal.version })}>Approve</button> : null}{commands.includes("REJECT") ? <button className="btn-secondary" onClick={() => void run(`/proposals/${proposal.id}/reject`, { comment, version: proposal.version })}>Reject</button> : null}</div></div> : null}{error ? <p role="alert" className="text-red-700">{error}</p> : null}
  </section>;
}
