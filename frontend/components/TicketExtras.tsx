"use client";

import { ChangeEvent, FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { api, get, post } from "@/lib/api";

/**
 * Shared ticket detail widgets for activity, attachments, history, and change
 * proposals.
 *
 * The page composes these pieces so comments, files, history, and proposal
 * decisions all use the same loading and refresh logic.
 */
type Person = { id: number; displayName: string };
type Comment = { id: number; author: Person; body: string; visibility: "PUBLIC" | "INTERNAL"; createdAt: string };
type Attachment = { id: number; uploadedBy: Person; fileName: string; contentType: string; sizeBytes: number; createdAt: string; contentAvailable: boolean };
type Audit = { id: number; actor: Person; action: string; fieldName?: string; createdAt: string };
type History = { id: number; fromStatus?: string; toStatus: string; changedBy: Person; createdAt: string };
type Proposal = { id: number; description: string; estimatedDeliveryDate: string | null; effortEstimate: string | null; status: string; version?: number };

function useTicketActivity(ticketKey: string) {
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
  return { comments, attachments, audit, history, body, setBody, visibility, setVisibility, error, addComment, load };
}

export function TicketCommunication({ ticketKey, internal = false }: { ticketKey: string; internal?: boolean }) {
  const { comments, attachments, body, setBody, visibility, setVisibility, error, addComment, load } = useTicketActivity(ticketKey);
  const [uploading, setUploading] = useState(false), [attachmentError, setAttachmentError] = useState("");
  const [preview, setPreview] = useState<{ attachment: Attachment; url?: string; text?: string } | null>(null);
  const [fullPreview, setFullPreview] = useState(false);
  const autoPreviewed = useRef(false);
  useEffect(() => () => { if (preview?.url) URL.revokeObjectURL(preview.url); }, [preview]);
  const contentUrl = (attachment: Attachment) => `${process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081/api"}/tickets/${ticketKey}/attachments/${attachment.id}/content`;
  async function openPreview(attachment: Attachment) {
    if (!attachment.contentAvailable) return;
    setAttachmentError("");
    try {
      const response = await fetch(contentUrl(attachment), { credentials: "include" });
      if (!response.ok) throw new Error("Could not open file.");
      const blob = await response.blob();
      if (attachment.contentType.startsWith("text/") && attachment.contentType !== "text/html") setPreview({ attachment, text: await blob.text() });
      else setPreview({ attachment, url: URL.createObjectURL(blob) });
    } catch (error) { setAttachmentError(error instanceof Error ? error.message : "Could not open file."); }
  }
  useEffect(() => {
    if (autoPreviewed.current || !attachments.length) return;
    autoPreviewed.current = true;
    const newest = [...attachments].reverse().find(attachment => attachment.contentAvailable);
    if (newest) void openPreview(newest);
  }, [attachments]);
  async function uploadFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]; if (!file) return;
    setUploading(true); setAttachmentError("");
    try { const data = new FormData(); data.append("file", file); const saved = await api<Attachment>(`/tickets/${ticketKey}/attachments/upload`, { method: "POST", body: data }); await load(); await openPreview(saved); }
    catch (error) { setAttachmentError(error instanceof Error ? error.message : "Could not upload file."); }
    finally { setUploading(false); event.target.value = ""; }
  }
  async function downloadFile(attachment: Attachment) {
    try { const response = await fetch(contentUrl(attachment), { credentials: "include" }); if (!response.ok) throw new Error("Could not download file."); const url = URL.createObjectURL(await response.blob()); const link = document.createElement("a"); link.href = url; link.download = attachment.fileName; link.click(); URL.revokeObjectURL(url); }
    catch (error) { setAttachmentError(error instanceof Error ? error.message : "Could not download file."); }
  }
  async function removeFile(attachment: Attachment) {
    if (!window.confirm(`Remove ${attachment.fileName}?`)) return;
    try { await api(`/tickets/${ticketKey}/attachments/${attachment.id}`, { method: "DELETE" }); setPreview(null); await load(); }
    catch (error) { setAttachmentError(error instanceof Error ? error.message : "Could not remove file."); }
  }
  return <><div className="grid gap-4 lg:grid-cols-3">
    <section className="card lg:col-span-2"><h2 className="text-lg font-bold">Comments</h2><form className="my-4 grid gap-3 sm:grid-cols-[1fr_150px_auto] sm:items-end" onSubmit={addComment}>
      <label className="block">Message<textarea className="field mt-1 min-h-20" value={body} maxLength={10000} required onChange={e => setBody(e.target.value)} /></label>
      {internal ? <label className="block">Visibility<select className="field mt-1" value={visibility} onChange={e => setVisibility(e.target.value as "PUBLIC" | "INTERNAL")}><option value="PUBLIC">Public</option><option value="INTERNAL">Internal</option></select></label> : null}
      <button className="btn-primary">Add comment</button></form>{error ? <p role="alert" className="text-red-700">{error}</p> : null}
      <div className="max-h-72 space-y-2 overflow-y-auto">{comments.length ? comments.map(c => <article className="rounded border p-3" key={c.id}><div className="flex justify-between gap-3 text-sm"><strong>{c.author.displayName}</strong><span className="text-xs text-slate-500">{c.visibility}</span></div><p className="mt-1 whitespace-pre-wrap text-sm">{c.body}</p><time className="text-xs text-slate-500">{new Date(c.createdAt).toLocaleString()}</time></article>) : <p className="text-sm text-slate-500">No comments yet.</p>}</div>
    </section>
    <section className="card"><div className="mb-3 flex items-center justify-between gap-2"><h2 className="text-lg font-bold">Attachments</h2><label className="btn-primary cursor-pointer"><input className="sr-only" type="file" disabled={uploading} onChange={event => void uploadFile(event)}/>{uploading ? "Uploading…" : "+ Add file"}</label></div>{attachmentError ? <p className="mb-2 text-sm text-red-400" role="alert">{attachmentError}</p> : null}{attachments.length ? <ul className="max-h-44 space-y-2 overflow-y-auto">{attachments.map(a => <li className={`rounded border p-3 ${preview?.attachment.id === a.id ? "border-blue-500 bg-blue-950/30" : ""}`} key={a.id}><div className="flex items-start justify-between gap-2"><button className="min-w-0 text-left hover:text-blue-400" disabled={!a.contentAvailable} onClick={() => void openPreview(a)}><strong className="block truncate text-sm">{a.fileName}</strong><span className="text-xs text-slate-500">{Math.ceil(a.sizeBytes / 1024)} KB · {a.contentType}</span></button>{a.contentAvailable ? <button className="btn-secondary px-2 py-1 text-xs" onClick={() => void downloadFile(a)}>Download</button> : <span className="text-[10px] text-slate-500">Reference only</span>}</div></li>)}</ul> : <p className="text-sm text-slate-500">No files attached.</p>}{preview ? <AttachmentPreview preview={preview} download={() => void downloadFile(preview.attachment)} fullView={() => setFullPreview(true)}/> : <div className="attachment-inline-empty">Upload or select a file to preview it here.</div>}</section>
    {preview && fullPreview ? <FullAttachmentPreview preview={preview} close={() => setFullPreview(false)} download={() => void downloadFile(preview.attachment)}/> : null}
  </div>{preview ? <button type="button" className="btn-secondary text-red-300" onClick={() => void removeFile(preview.attachment)}>Remove selected attachment</button> : null}</>;
}

function AttachmentPreview({ preview, download, fullView }: { preview: { attachment: Attachment; url?: string; text?: string }; download: () => void; fullView: () => void }) {
  const { attachment, url, text } = preview;
  const image = attachment.contentType.startsWith("image/") && attachment.contentType !== "image/svg+xml";
  const pdf = attachment.contentType === "application/pdf";
  const audio = attachment.contentType.startsWith("audio/"), video = attachment.contentType.startsWith("video/");
  return <section className="attachment-inline-preview" aria-label={`Preview ${attachment.fileName}`}><header><div className="min-w-0"><strong className="block truncate text-sm">{attachment.fileName}</strong><span className="text-xs text-slate-500">Preview</span></div><div className="flex gap-1"><button className="btn-secondary px-2 py-1 text-xs" onClick={fullView}>Full view</button><button className="btn-secondary px-2 py-1 text-xs" onClick={download}>Download</button></div></header><PreviewContent preview={preview} className="attachment-inline-body" download={download}/></section>;
}

function FullAttachmentPreview({ preview, close, download }: { preview: { attachment: Attachment; url?: string; text?: string }; close: () => void; download: () => void }) { return <div className="attachment-preview-backdrop" role="dialog" aria-modal="true" aria-label={`Full preview ${preview.attachment.fileName}`} onMouseDown={event => { if (event.currentTarget === event.target) close(); }}><section className="attachment-preview"><header><strong className="truncate">{preview.attachment.fileName}</strong><div className="flex gap-2"><button className="btn-secondary" onClick={download}>Download</button><button className="btn-secondary" onClick={close}>Close</button></div></header><PreviewContent preview={preview} className="attachment-preview-body" download={download}/></section></div>; }

function PreviewContent({ preview, className, download }: { preview: { attachment: Attachment; url?: string; text?: string }; className: string; download: () => void }) { const { attachment, url, text } = preview; const image=attachment.contentType.startsWith("image/")&&attachment.contentType!=="image/svg+xml",pdf=attachment.contentType==="application/pdf",audio=attachment.contentType.startsWith("audio/"),video=attachment.contentType.startsWith("video/");return <div className={className}>{text!==undefined?<pre>{text}</pre>:image&&url?<img src={url} alt={attachment.fileName}/>:pdf&&url?<iframe src={url} title={attachment.fileName}/>:audio&&url?<audio controls src={url}/>:video&&url?<video controls src={url}/>:<div className="p-4 text-center text-sm text-slate-500"><p>This file type cannot be previewed safely.</p><button className="btn-primary mt-3" onClick={download}>Download and open</button></div>}</div>; }

export function TicketHistory({ ticketKey }: { ticketKey: string }) {
  const { audit, history, error } = useTicketActivity(ticketKey);
  return <div className="grid gap-4 lg:grid-cols-2">{error ? <p role="alert" className="text-red-700 lg:col-span-2">{error}</p> : null}
    <section><h2 className="mb-3 font-bold">Status history</h2><ol className="space-y-2">{history.map(h => <li className="rounded border p-3" key={h.id}><strong>{h.fromStatus ?? "Created"} → {h.toStatus}</strong><div className="text-sm text-slate-500">{h.changedBy.displayName} · {new Date(h.createdAt).toLocaleString()}</div></li>)}</ol></section>
    <section><h2 className="mb-3 font-bold">Audit log</h2><ol className="space-y-2">{audit.map(a => <li className="rounded border p-3" key={a.id}><strong>{a.action.replaceAll("_", " ")}</strong>{a.fieldName ? ` · ${a.fieldName}` : ""}<div className="text-sm text-slate-500">{a.actor.displayName} · {new Date(a.createdAt).toLocaleString()}</div></li>)}</ol></section>
  </div>;
}

export function TicketActivity({ ticketKey }: { ticketKey: string }) {
  return <div className="space-y-4"><TicketCommunication ticketKey={ticketKey}/><TicketHistory ticketKey={ticketKey}/></div>;
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
