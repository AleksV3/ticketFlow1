"use client";
import { useState } from "react";

function badgeTone(value: string) {
	const normalized = value.toUpperCase();
	if (normalized.includes("DONE") || normalized.includes("APPROVED") || normalized.includes("CLOSED") || normalized.includes("COMPLETED")) return "bg-emerald-100 text-emerald-800";
	if (normalized.includes("REJECT") || normalized.includes("CANCEL") || normalized.includes("BLOCK")) return "bg-red-100 text-red-800";
	if (normalized.includes("DUE") || normalized.includes("WAIT") || normalized.includes("PEND") || normalized.includes("WARN")) return "bg-amber-100 text-amber-800";
	if (normalized.includes("PROGRESS") || normalized.includes("OPEN") || normalized.includes("ANALYSIS") || normalized.includes("REVIEW")) return "bg-blue-100 text-blue-800";
	return "bg-slate-100 text-slate-700";
}

export function StatusBadge({value}:{value:string}){return <span className={`badge ${badgeTone(value)}`}>{value.replaceAll("_"," ")}</span>}
export function SlaBadge({value}:{value:string}){const color=value==="BREACHED"?"bg-red-100 text-red-800":value==="DUE_SOON"?"bg-amber-100 text-amber-800":"bg-emerald-100 text-emerald-800";return <span className={`badge ${color}`}>{value.replaceAll("_"," ")}</span>}
export function Pagination({page,totalPages,onPage}:{page:number;totalPages:number;onPage:(page:number)=>void}){if(totalPages<=1)return null;return <nav aria-label="Pagination" className="mt-5 flex flex-wrap items-center gap-3"><button className="btn-secondary" disabled={page===0} onClick={()=>onPage(page-1)}>Previous</button><span className="text-sm">Page {page+1} of {totalPages}</span><button className="btn-secondary" disabled={page+1>=totalPages} onClick={()=>onPage(page+1)}>Next</button></nav>}
export function TransitionButtons({allowedTransitions,onTransition}:{allowedTransitions:string[];onTransition:(status:string)=>Promise<void>}){const [busy,setBusy]=useState(""),[pending,setPending]=useState("");if(!allowedTransitions.length)return <p className="text-sm text-slate-500">No actions are available from this state.</p>;async function confirm(){setBusy(pending);try{await onTransition(pending);setPending("")}finally{setBusy("")}}return <><div className="flex flex-wrap gap-2">{allowedTransitions.map(status=><button className="btn-secondary" disabled={!!busy} key={status} onClick={()=>setPending(status)}>{status.replaceAll("_"," ")}</button>)}</div>{pending?<div className="confirm-action" role="alertdialog" aria-labelledby="confirm-action-title"><div><strong id="confirm-action-title">Move ticket to {pending.replaceAll("_"," ")}?</strong><p>This changes the workflow state and will be recorded in history.</p></div><div><button className="btn-secondary" disabled={!!busy} onClick={()=>setPending("")}>Cancel</button><button className="btn-primary" disabled={!!busy} onClick={()=>void confirm()}>{busy?"Updating…":"Yes, continue"}</button></div></div>:null}</>}
