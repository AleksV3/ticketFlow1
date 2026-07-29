"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { ReactNode, useCallback, useEffect, useRef, useState } from "react";
import { fetchCurrentUser, logout, type CurrentUser } from "@/lib/auth";
import { ThemeController } from "@/components/ThemeController";
import { get, patch } from "@/lib/api";
import { useTicketEvents } from "@/lib/realtime";

/**
 * Top-level authenticated shell for the app.
 *
 * It loads the current session, blocks unauthenticated users, filters visible
 * navigation links by permission, and renders the active page inside the shared
 * header/layout chrome.
 */
export const NAV_LINKS=[{href:"/dashboard",label:"Dashboard",permission:"TICKET_READ"},{href:"/tickets",label:"Tickets",permission:"TICKET_READ"},{href:"/notifications",label:"Notifications",permission:"TICKET_READ"},{href:"/teams",label:"Teams",permission:"TICKET_READ"},{href:"/tickets/new",label:"New ticket",permission:"TICKET_CREATE"},{href:"/admin/organizations",label:"Organizations",permission:"USER_MANAGE"},{href:"/admin/users",label:"Users",permission:"USER_MANAGE"},{href:"/admin/roles",label:"Roles",permission:"ROLE_MANAGE"},{href:"/admin/ticket-types",label:"Ticket types",permission:"WORKFLOW_MANAGE"},{href:"/admin/workflows",label:"Workflows",permission:"WORKFLOW_MANAGE"}];
export const permittedLinks=(permissions:string[],party?:CurrentUser["party"])=>NAV_LINKS.filter(link=>permissions.includes(link.permission) && (link.href!=="/teams" || party==="TICKETFLOW1"));

export function AppShell({ children, require }: { children: (user: CurrentUser) => ReactNode; require?: string }) {
  const router = useRouter(); const pathname = usePathname(); const [user,setUser]=useState<CurrentUser|null>(null); const [unreadNotifications,setUnreadNotifications]=useState(0); const [toasts,setToasts]=useState<Array<{id:number;title:string;message:string;ticketKey:string|null}>>([]); const previousUnread=useRef<number|null>(null); const [error,setError]=useState("");
  const pushToast = useCallback((toast:{title:string;message:string;ticketKey?:string|null})=>{const id=Date.now()+Math.random(); setToasts(items=>[...items,{id,title:toast.title,message:toast.message,ticketKey:toast.ticketKey??null}]); window.setTimeout(()=>setToasts(items=>items.filter(item=>item.id!==id)),7000)},[]);
  const refreshUnread = useCallback(async () => { try { const count=await get<number>("/notifications/unread-count"); const previous=previousUnread.current; setUnreadNotifications(count); previousUnread.current=count; if(previous!==null && count>previous){ const latest=await get<Array<{id:number;title:string;message:string;read:boolean;ticketKey:string|null}>>("/notifications"); const item=latest.find(notification=>!notification.read); if(item)pushToast({title:item.title,message:item.message,ticketKey:item.ticketKey}); } } catch { /* session guard handles authentication */ } }, [pushToast]);
  useEffect(()=>{fetchCurrentUser().then(value=>{if(!value)router.replace("/login");else if(value.passwordChangeRequired)router.replace("/change-password");else if(require&&!value.permissions.includes(require))setError("You do not have permission to open this page.");else setUser(value);}).catch(()=>setError("Could not load your session."));},[router,require]);
  useEffect(()=>{if(user)void refreshUnread()},[user,refreshUnread]);
  useEffect(()=>{const show=(event: Event)=>{const detail=(event as CustomEvent<{title?:string;message?:string;ticketKey?:string|null}>).detail; if(detail?.message)pushToast({title:detail.title??"Message",message:detail.message,ticketKey:detail.ticketKey});}; window.addEventListener("ticketflow:toast",show); return()=>window.removeEventListener("ticketflow:toast",show)},[pushToast]);
  useTicketEvents(refreshUnread);
  if(error)return <StatePanel title="Access unavailable" message={error}/>;
  if(!user)return <StatePanel title="Loading" message="Checking your session…"/>;
  return <div className="app-frame"><header className="app-header"><div className="mx-auto flex max-w-7xl flex-wrap items-center gap-5 px-5 py-4"><Link href="/dashboard" className="brand-mark text-lg">TicketFlow1</Link><nav aria-label="Main navigation" className="flex min-w-0 flex-1 flex-wrap gap-1">{permittedLinks(user.permissions,user.party).map(l=>{const active=l.href==="/tickets" ? (pathname==="/tickets" || (pathname.startsWith("/tickets/") && !pathname.startsWith("/tickets/new"))) : pathname===l.href || (l.href!=="/dashboard" && pathname.startsWith(`${l.href}/`)); return <Link aria-current={active?"page":undefined} className={`nav-link ${active?"nav-link-active":""}`} href={l.href} key={l.href}>{l.label}{l.href==="/notifications" && unreadNotifications>0 ? <span className="notification-dot" aria-label={`${unreadNotifications} unread notifications`} title={`${unreadNotifications} unread notifications`} /> : null}</Link>})}</nav><span className="rounded-full border border-slate-200/20 px-3 py-2 text-sm text-slate-500">{user.displayName}</span><ThemeController/><button className="btn-secondary" onClick={async()=>{await logout();router.replace("/login");}}>Sign out</button></div></header><main className="page-shell mx-auto max-w-7xl p-5 sm:p-8">{children(user)}</main><div className="notification-stack">{toasts.map(toast=><aside className={`notification-toast ${toast.ticketKey?"notification-toast-clickable":""} ${toast.title==="Editing unavailable"?"lock-toast":""}`} role="status" key={toast.id}>{toast.ticketKey?<Link href={`/tickets/${toast.ticketKey}`}><p className="eyebrow">New notification</p><strong>{toast.title}</strong><p>{toast.message}</p></Link>:<><p className="eyebrow">{toast.title==="Editing unavailable"?"Ticket locked":"Message"}</p><strong>{toast.title}</strong><p>{toast.message}</p></>}<button className="notification-toast-close" aria-label="Dismiss notification" onClick={()=>setToasts(items=>items.filter(item=>item.id!==toast.id))}>×</button></aside>)}</div></div>;
}
export function StatePanel({title,message}:{title:string;message:string}){return <main className="grid min-h-screen place-items-center p-6"><section role="status" className="card max-w-md text-center"><h1 className="text-xl font-bold">{title}</h1><p className="mt-2 text-slate-600">{message}</p></section></main>}
