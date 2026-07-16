"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { get, patch, post } from "@/lib/api";
import type { Paged } from "@/lib/types";

type Organization={id:number;name:string;active:boolean;createdAt:string};
type Role={id:number;name:string;party:"CLIENT";template:boolean;permissions:string[]};
type Permission={id:number;key:string};
type User={id:number;email:string;displayName:string;roleNames:string[];active:boolean};
const human=(key:string)=>key.toLowerCase().replaceAll("_"," ").replace(/^./,letter=>letter.toUpperCase());

export default function OrganizationsPage(){return <AppShell require="USER_MANAGE">{user=>user.party==="TICKETFLOW1"?<OrganizationAdmin/>:<div className="card">Organization administration is available to TicketFlow1 administrators.</div>}</AppShell>}

function OrganizationAdmin(){
  const [organizations,setOrganizations]=useState<Organization[]>([]),[selectedId,setSelectedId]=useState(""),[permissions,setPermissions]=useState<Permission[]>([]);
  const [roles,setRoles]=useState<Role[]>([]),[users,setUsers]=useState<User[]>([]),[message,setMessage]=useState("");
  const [newOrgName,setNewOrgName]=useState(""),[orgName,setOrgName]=useState(""),[orgActive,setOrgActive]=useState(true);
  const [roleName,setRoleName]=useState(""),[rolePermissions,setRolePermissions]=useState<string[]>([]);
  const [displayName,setDisplayName]=useState(""),[email,setEmail]=useState(""),[password,setPassword]=useState(""),[userRoleId,setUserRoleId]=useState("");
  const selected=useMemo(()=>organizations.find(org=>org.id===Number(selectedId)),[organizations,selectedId]);

  const loadOrganizations=useCallback(async(selectId?:number)=>{const rows=await get<Organization[]>("/admin/organizations");setOrganizations(rows);if(selectId)setSelectedId(String(selectId))},[]);
  const loadOrganization=useCallback(async()=>{if(!selectedId){setRoles([]);setUsers([]);return}const [roleRows,userPage]=await Promise.all([get<Role[]>(`/admin/roles?organizationId=${selectedId}`),get<Paged<User>>(`/admin/users?organizationId=${selectedId}&pageSize=100`)]);setRoles(roleRows);setUsers(userPage.items)},[selectedId]);
  useEffect(()=>{void loadOrganizations();void get<Permission[]>("/admin/permissions").then(setPermissions)},[loadOrganizations]);
  useEffect(()=>{if(selected){setOrgName(selected.name);setOrgActive(selected.active)}void loadOrganization()},[selected,loadOrganization]);

  async function createOrganization(event:FormEvent){event.preventDefault();try{const created=await post<Organization>("/admin/organizations",{name:newOrgName.trim()});setNewOrgName("");setMessage(`${created.name} created with its default roles, ticket types and workflows.`);await loadOrganizations(created.id)}catch(error){setMessage(error instanceof Error?error.message:"Could not create organization.")}}
  async function saveOrganization(event:FormEvent){event.preventDefault();if(!selected)return;try{await patch(`/admin/organizations/${selected.id}`,{name:orgName.trim(),active:orgActive});setMessage("Organization details saved.");await loadOrganizations(selected.id)}catch(error){setMessage(error instanceof Error?error.message:"Could not save organization.")}}
  function togglePermission(key:string){setRolePermissions(keys=>keys.includes(key)?keys.filter(item=>item!==key):[...keys,key])}
  async function createRole(event:FormEvent){event.preventDefault();if(!selected)return;try{await post("/admin/roles",{name:roleName.trim(),party:"CLIENT",organizationId:selected.id,permissionKeys:rolePermissions});setRoleName("");setRolePermissions([]);setMessage("Custom organization role created.");await loadOrganization()}catch(error){setMessage(error instanceof Error?error.message:"Could not create role.")}}
  async function createUser(event:FormEvent){event.preventDefault();if(!selected)return;try{await post("/admin/users",{displayName,email,password,roleId:Number(userRoleId),organizationId:selected.id});setDisplayName("");setEmail("");setPassword("");setUserRoleId("");setMessage("Organization user created.");await loadOrganization()}catch(error){setMessage(error instanceof Error?error.message:"Could not create user.")}}

  return <div className="space-y-6"><header><p className="eyebrow">Client administration</p><h1 className="mt-1 text-3xl font-bold">Organizations</h1><p className="mt-2 text-slate-600">Create or edit a client organization, then add its roles and users in one place.</p></header>
    <form className="card flex flex-wrap items-end gap-3" onSubmit={createOrganization}><label className="min-w-64 flex-1">New organization name<input className="field mt-1" required maxLength={200} value={newOrgName} onChange={event=>setNewOrgName(event.target.value)} placeholder="e.g. Northwind Finance"/></label><button className="btn-primary">Create organization</button></form>
    <label className="card block">Organization to manage<select className="field mt-1" value={selectedId} onChange={event=>setSelectedId(event.target.value)}><option value="">Select an organization</option>{organizations.map(org=><option key={org.id} value={org.id}>{org.name}{org.active?"":" · Inactive"}</option>)}</select></label>
    {selected?<div className="space-y-5"><form className="card grid gap-4 md:grid-cols-[1fr_auto]" onSubmit={saveOrganization}><div><p className="eyebrow">Organization details</p><label className="mt-2 block">Name<input className="field mt-1" required maxLength={200} value={orgName} onChange={event=>setOrgName(event.target.value)}/></label><label className="mt-3 flex items-center gap-2"><input type="checkbox" checked={orgActive} onChange={event=>setOrgActive(event.target.checked)}/> Organization is active</label></div><button className="btn-primary self-end">Save organization</button></form>
      <div className="grid gap-5 xl:grid-cols-2"><form className="card space-y-4" onSubmit={createRole}><div><p className="eyebrow">Roles</p><h2 className="text-xl font-bold">Add custom role</h2><p className="text-sm text-slate-500">{roles.length} roles currently available for {selected.name}.</p></div><label className="block">Role name<input className="field mt-1" required value={roleName} onChange={event=>setRoleName(event.target.value)}/></label><fieldset><legend className="mb-2 font-medium">Permissions</legend><div className="grid gap-2 sm:grid-cols-2">{permissions.map(permission=><label className={`permission-option ${rolePermissions.includes(permission.key)?"permission-option-selected":""}`} key={permission.id}><input type="checkbox" checked={rolePermissions.includes(permission.key)} onChange={()=>togglePermission(permission.key)}/><span><strong>{human(permission.key)}</strong></span></label>)}</div></fieldset><button className="btn-primary" disabled={!rolePermissions.length}>Create role</button></form>
        <form className="card space-y-4" onSubmit={createUser}><div><p className="eyebrow">Users</p><h2 className="text-xl font-bold">Add organization user</h2><p className="text-sm text-slate-500">{users.length} users currently belong to {selected.name}.</p></div><label className="block">Display name<input className="field mt-1" required value={displayName} onChange={event=>setDisplayName(event.target.value)}/></label><label className="block">Email<input className="field mt-1" type="email" required value={email} onChange={event=>setEmail(event.target.value)}/></label><label className="block">Temporary password<input className="field mt-1" type="password" required minLength={6} value={password} onChange={event=>setPassword(event.target.value)}/></label><label className="block">Initial role<select className="field mt-1" required value={userRoleId} onChange={event=>setUserRoleId(event.target.value)}><option value="">Select a role</option>{roles.map(role=><option key={role.id} value={role.id}>{role.name}{role.template?" · Default":" · Custom"}</option>)}</select></label><button className="btn-primary">Create user</button></form></div>
      <section className="card"><h2 className="font-bold">Organization overview</h2><div className="mt-3 grid gap-3 md:grid-cols-2"><div><h3 className="text-xs font-bold uppercase text-blue-400">Roles</h3>{roles.map(role=><p className="mt-1 text-sm" key={role.id}>{role.name} · {role.permissions.length} permissions</p>)}</div><div><h3 className="text-xs font-bold uppercase text-blue-400">Users</h3>{users.map(user=><p className="mt-1 text-sm" key={user.id}>{user.displayName} · {user.roleNames?.join(", ")}</p>)}</div></div></section>
    </div>:null}{message?<p className="card" role="status">{message}</p>:null}
  </div>;
}
