"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { get, patch, post } from "@/lib/api";
import type { CurrentUser } from "@/lib/auth";
import type { Paged } from "@/lib/types";

type User={id:number;email:string;displayName:string;roleId:number;roleIds:number[];organizationId:number|null;organizationName:string|null;active:boolean};
type Role={id:number;name:string;party:"CLIENT"|"TICKETFLOW1";template?:boolean};
type Organization={id:number;name:string};

export default function UsersPage(){return <AppShell require="USER_MANAGE">{user=><UserAdmin current={user}/>}</AppShell>}

function UserAdmin({current}:{current:CurrentUser}){
  const [users,setUsers]=useState<User[]>([]),[createRoles,setCreateRoles]=useState<Role[]>([]),[assignmentRoles,setAssignmentRoles]=useState<Role[]>([]),[organizations,setOrganizations]=useState<Organization[]>([]);
  const [email,setEmail]=useState(""),[password,setPassword]=useState(""),[displayName,setDisplayName]=useState(""),[roleId,setRoleId]=useState("");
  const [createOrg,setCreateOrg]=useState(current.organizationId?.toString()??""),[assignmentOrg,setAssignmentOrg]=useState(current.organizationId?.toString()??"");
  const [selectedUserId,setSelectedUserId]=useState(""),[draftRoles,setDraftRoles]=useState<Record<number,number[]>>({}),[savingUser,setSavingUser]=useState<number|null>(null),[message,setMessage]=useState("");
  const loadUsers=useCallback(()=>get<Paged<User>>("/admin/users?pageSize=100").then(page=>{setUsers(page.items);setDraftRoles(Object.fromEntries(page.items.map(user=>[user.id,user.roleIds?.length?user.roleIds:[user.roleId]])))}),[]);

  useEffect(()=>{void loadUsers();if(current.party==="TICKETFLOW1")void get<Organization[]>("/admin/organizations").then(setOrganizations)},[current.party,loadUsers]);
  useEffect(()=>{const query=createOrg?`?organizationId=${createOrg}`:"";void get<Role[]>(`/reference/assignable-roles${query}`).then(value=>{setCreateRoles(value);setRoleId("")})},[createOrg]);
  useEffect(()=>{const query=assignmentOrg?`?organizationId=${assignmentOrg}`:"";setSelectedUserId("");void get<Role[]>(`/reference/assignable-roles${query}`).then(setAssignmentRoles)},[assignmentOrg]);

  async function createUser(event:FormEvent){event.preventDefault();setMessage("");try{const role=createRoles.find(item=>item.id===Number(roleId));await post("/admin/users",{email,password,displayName,roleId:Number(roleId),organizationId:role?.party==="CLIENT"?Number(createOrg):null});setEmail("");setPassword("");setDisplayName("");setMessage("User created.");await loadUsers()}catch(error){setMessage(error instanceof Error?error.message:"Could not create user.")}}
  function toggleRole(userId:number,id:number){setDraftRoles(all=>{const values=all[userId]??[];return{...all,[userId]:values.includes(id)?values.filter(value=>value!==id):[...values,id]}})}
  async function saveRoles(person:User){const roleIds=draftRoles[person.id]??[];if(!roleIds.length){setMessage("Every user must have at least one role.");return}setSavingUser(person.id);try{await patch(`/admin/users/${person.id}/role`,{roleIds});setMessage(`${person.displayName}'s roles were updated. They apply after the next sign-in.`);await loadUsers()}catch(error){setMessage(error instanceof Error?error.message:"Could not update roles.")}finally{setSavingUser(null)}}

  const scopedUsers=users.filter(user=>assignmentOrg?user.organizationId===Number(assignmentOrg):user.organizationId===null);
  const selectedUser=scopedUsers.find(user=>user.id===Number(selectedUserId));
  return <div className="space-y-6"><header><h1 className="text-3xl font-bold">User administration</h1><p className="text-slate-600">Create users and manage their role assignments independently.</p></header>
    <form className="card grid gap-4 md:grid-cols-2" onSubmit={createUser}><label>Display name<input className="field mt-1" required value={displayName} onChange={event=>setDisplayName(event.target.value)}/></label><label>Email<input className="field mt-1" type="email" required value={email} onChange={event=>setEmail(event.target.value)}/></label><label>Temporary password<input className="field mt-1" type="password" minLength={6} required value={password} onChange={event=>setPassword(event.target.value)}/></label>{current.party==="TICKETFLOW1"?<label>Organization for new user<select className="field mt-1" value={createOrg} onChange={event=>setCreateOrg(event.target.value)}><option value="">TicketFlow1 (internal)</option>{organizations.map(org=><option key={org.id} value={org.id}>{org.name}</option>)}</select></label>:null}<label>Initial role<select className="field mt-1" required value={roleId} onChange={event=>setRoleId(event.target.value)}><option value="">Select a role</option>{createRoles.map(role=><option key={role.id} value={role.id}>{role.name}</option>)}</select></label><button type="submit" className="btn-primary self-end">Create user</button></form>
    <section className="card"><div className="mb-4"><p className="eyebrow">Role assignments</p><h2 className="text-xl font-bold">Edit one user’s roles</h2><p className="text-sm text-slate-500">Saving here updates only the selected user’s role table.</p></div><div className="grid max-w-3xl gap-4 md:grid-cols-2">{current.party==="TICKETFLOW1"?<label>Users from<select className="field mt-1" value={assignmentOrg} onChange={event=>setAssignmentOrg(event.target.value)}><option value="">TicketFlow1 (internal)</option>{organizations.map(org=><option key={org.id} value={org.id}>{org.name}</option>)}</select></label>:null}<label>User<select className="field mt-1" value={selectedUserId} onChange={event=>setSelectedUserId(event.target.value)}><option value="">Select a user</option>{scopedUsers.map(user=><option key={user.id} value={user.id}>{user.displayName} · {user.email}</option>)}</select></label></div>
      {selectedUser?<div className="mt-4 rounded-lg border p-4"><div className="flex justify-between gap-3"><div><strong>{selectedUser.displayName}</strong><p className="text-xs text-slate-500">{selectedUser.email} · {selectedUser.organizationName??"TicketFlow1"}</p></div><button type="button" className="btn-primary" disabled={savingUser===selectedUser.id||!(draftRoles[selectedUser.id]?.length)} onClick={()=>void saveRoles(selectedUser)}>{savingUser===selectedUser.id?"Saving…":"Save roles"}</button></div><div className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-3">{assignmentRoles.map(role=><label className={`permission-option ${(draftRoles[selectedUser.id]??[]).includes(role.id)?"permission-option-selected":""}`} key={role.id}><input type="checkbox" checked={(draftRoles[selectedUser.id]??[]).includes(role.id)} onChange={()=>toggleRole(selectedUser.id,role.id)}/><span><strong>{role.name}</strong><small>{role.template?"Default role":"Custom role"}</small></span></label>)}</div></div>:<p className="mt-4 rounded-lg border border-dashed p-5 text-center text-sm text-slate-500">Select a user to edit their roles.</p>}
    </section>{message?<p className="card" role="status">{message}</p>:null}
  </div>;
}
