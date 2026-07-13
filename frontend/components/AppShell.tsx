"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ReactNode, useEffect, useState } from "react";
import { fetchCurrentUser, logout, type CurrentUser } from "@/lib/auth";

export function AppShell({ children, require }: { children: (user: CurrentUser) => ReactNode; require?: string }) {
  const router = useRouter(); const [user,setUser]=useState<CurrentUser|null>(null); const [error,setError]=useState("");
  useEffect(()=>{fetchCurrentUser().then(value=>{if(!value)router.replace("/login");else if(require&&!value.permissions.includes(require))setError("You do not have permission to open this page.");else setUser(value);}).catch(()=>setError("Could not load your session."));},[router,require]);
  if(error)return <StatePanel title="Access unavailable" message={error}/>;
  if(!user)return <StatePanel title="Loading" message="Checking your session…"/>;
  const links=[{href:"/dashboard",label:"Dashboard",permission:"TICKET_READ"},{href:"/tickets",label:"Tickets",permission:"TICKET_READ"},{href:"/tickets/new",label:"New ticket",permission:"TICKET_CREATE"},{href:"/admin/users",label:"Users",permission:"USER_MANAGE"},{href:"/admin/roles",label:"Roles",permission:"ROLE_MANAGE"},{href:"/admin/workflows",label:"Workflows",permission:"WORKFLOW_MANAGE"}];
  return <div className="min-h-screen bg-slate-100"><header className="border-b bg-white"><div className="mx-auto flex max-w-7xl flex-wrap items-center gap-5 px-5 py-4"><Link href="/dashboard" className="text-lg font-bold">TicketFlow1</Link><nav aria-label="Main navigation" className="flex flex-1 flex-wrap gap-1">{links.filter(l=>user.permissions.includes(l.permission)).map(l=><Link className="rounded-lg px-3 py-2 text-sm hover:bg-slate-100" href={l.href} key={l.href}>{l.label}</Link>)}</nav><span className="text-sm text-slate-600">{user.displayName}</span><button className="btn-secondary" onClick={async()=>{await logout();router.replace("/login");}}>Sign out</button></div></header><main className="mx-auto max-w-7xl p-5 sm:p-8">{children(user)}</main></div>;
}
export function StatePanel({title,message}:{title:string;message:string}){return <main className="grid min-h-screen place-items-center p-6"><section role="status" className="card max-w-md text-center"><h1 className="text-xl font-bold">{title}</h1><p className="mt-2 text-slate-600">{message}</p></section></main>}
