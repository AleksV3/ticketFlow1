"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { get, post } from "@/lib/api";
import type { CurrentUser } from "@/lib/auth";
import type { Paged } from "@/lib/types";

type User = { id:number; email:string; displayName:string; party:string; roleName:string; organizationName:string|null; active:boolean };
type Role = { id:number; name:string; party:"CLIENT"|"TICKETFLOW1" };
type Organization = { id:number; name:string };

export default function UsersPage() { return <AppShell require="USER_MANAGE">{user => <UserAdmin current={user} />}</AppShell>; }

function UserAdmin({ current }: { current: CurrentUser }) {
  const [users, setUsers] = useState<User[]>([]); const [roles, setRoles] = useState<Role[]>([]); const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [email,setEmail]=useState(""); const [password,setPassword]=useState(""); const [displayName,setDisplayName]=useState(""); const [roleId,setRoleId]=useState("");
  const [organizationId,setOrganizationId]=useState(current.organizationId?.toString() ?? ""); const [message,setMessage]=useState("");
  const loadUsers=useCallback(()=>get<Paged<User>>("/admin/users?pageSize=100").then(r=>setUsers(r.items)),[]);
  useEffect(()=>{void loadUsers(); if(current.party==="TICKETFLOW1") void get<Organization[]>("/admin/organizations").then(setOrganizations);},[current.party,loadUsers]);
  useEffect(()=>{const query=organizationId?`?organizationId=${organizationId}`:"";void get<Role[]>(`/reference/assignable-roles${query}`).then(value=>{setRoles(value);setRoleId("");});},[organizationId]);
  async function submit(e:FormEvent){e.preventDefault();setMessage("");try{const selected=roles.find(r=>r.id===Number(roleId));await post("/admin/users",{email,password,displayName,roleId:Number(roleId),organizationId:selected?.party==="CLIENT"?Number(organizationId):null});setEmail("");setPassword("");setDisplayName("");setMessage("User created.");await loadUsers();}catch(e){setMessage(e instanceof Error?e.message:"Could not create user");}}
  return <div className="space-y-6"><div><h1 className="text-3xl font-bold">User administration</h1><p className="text-slate-600">Roles are loaded from the server, so only roles you may assign are shown.</p></div>
    <form className="card grid gap-4 md:grid-cols-2" onSubmit={submit}><label>Display name<input className="field mt-1" required value={displayName} onChange={e=>setDisplayName(e.target.value)}/></label><label>Email<input className="field mt-1" type="email" required value={email} onChange={e=>setEmail(e.target.value)}/></label><label>Temporary password<input className="field mt-1" type="password" minLength={6} required value={password} onChange={e=>setPassword(e.target.value)}/></label>{current.party==="TICKETFLOW1"?<label>Organization<select className="field mt-1" value={organizationId} onChange={e=>setOrganizationId(e.target.value)}><option value="">TicketFlow1 (internal)</option>{organizations.map(o=><option key={o.id} value={o.id}>{o.name}</option>)}</select></label>:null}<label>Assignable role<select className="field mt-1" required value={roleId} onChange={e=>setRoleId(e.target.value)}><option value="">Select a role</option>{roles.map(r=><option key={r.id} value={r.id}>{r.name} ({r.party})</option>)}</select></label><div className="self-end"><button className="btn-primary">Create user</button></div>{message?<p role="status" className="md:col-span-2">{message}</p>:null}</form>
    <section className="card overflow-x-auto"><table className="w-full text-left"><thead><tr><th className="p-2">Name</th><th className="p-2">Email</th><th className="p-2">Role</th><th className="p-2">Organization</th><th className="p-2">Status</th></tr></thead><tbody>{users.map(u=><tr className="border-t" key={u.id}><td className="p-2">{u.displayName}</td><td className="p-2">{u.email}</td><td className="p-2">{u.roleName}</td><td className="p-2">{u.organizationName??"TicketFlow1"}</td><td className="p-2">{u.active?"Active":"Inactive"}</td></tr>)}</tbody></table></section></div>;
}
