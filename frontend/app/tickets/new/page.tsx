"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { get, post } from "@/lib/api";
import type { CurrentUser } from "@/lib/auth";
import type { TicketDetail } from "@/lib/types";

type Ref = { id: number; key?: string; name: string };

export default function NewTicketPage() {
  return <AppShell require="TICKET_CREATE">{user => <TicketForm user={user}/>}</AppShell>;
}

function TicketForm({ user }: { user: CurrentUser }) {
  const router = useRouter();
  const canAssign = user.permissions.includes("TICKET_ASSIGN");
  const [orgs, setOrgs] = useState<Ref[]>([]), [types, setTypes] = useState<Ref[]>([]), [people, setPeople] = useState<Ref[]>([]);
  const [org, setOrg] = useState(user.organizationId ? String(user.organizationId) : ""), [type, setType] = useState("");
  const [title, setTitle] = useState(""), [description, setDescription] = useState(""), [priority, setPriority] = useState("MEDIUM"), [severity, setSeverity] = useState("SEV_3");
  const [leadId, setLeadId] = useState(""), [developerIds, setDeveloperIds] = useState<number[]>([]), [error, setError] = useState("");

  useEffect(() => { if (user.party === "TICKETFLOW1") void get<Ref[]>("/reference/organizations").then(setOrgs); }, [user.party]);
  useEffect(() => { if (canAssign) void get<Ref[]>("/reference/ticket-leads").then(setPeople); }, [canAssign]);
  useEffect(() => { if (org) void get<Ref[]>(`/reference/ticket-types?organizationId=${org}`).then(values => { setTypes(values); setType(""); }); }, [org]);

  function toggleDeveloper(id: number) { setDeveloperIds(values => values.includes(id) ? values.filter(value => value !== id) : [...values, id]); }
  async function submit(event: FormEvent) {
    event.preventDefault(); setError("");
    const selectedType = types.find(item => item.key === type || item.name.toLowerCase() === type.toLowerCase());
    if (!selectedType?.key) { setError("Select a ticket type from the available results."); return; }
    try {
      const ticket = await post<TicketDetail>("/tickets", { type: selectedType.key, title, description, priority,
        severity: selectedType.key === "DEFECT" ? severity : null, organizationId: user.party === "TICKETFLOW1" ? Number(org) : null,
        ticketLeadId: canAssign && leadId ? Number(leadId) : null, developerIds: canAssign ? developerIds : null });
      router.push(`/tickets/${ticket.ticketKey}`);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Could not create ticket."); }
  }

  return <div className="max-w-3xl"><h1 className="text-3xl font-bold">Create ticket</h1><form className="card mt-6 space-y-4" onSubmit={submit}>
    {user.party === "TICKETFLOW1" ? <Select label="Organization" value={org} set={setOrg} options={orgs.map(item => [String(item.id), item.name])}/> : null}
    <label className="block"><span>Ticket type</span><input className="field mt-1" required list="ticket-type-options" value={type} onChange={event => setType(event.target.value)} placeholder="Search standard or custom ticket types…"/><datalist id="ticket-type-options">{types.map(item => <option key={item.id} value={item.key}>{item.name}</option>)}</datalist><small className="text-slate-500">Start typing to search all ticket types configured for this organization.</small></label>
    <label className="block"><span>Title</span><input className="field mt-1" required maxLength={300} value={title} onChange={event => setTitle(event.target.value)}/></label>
    <label className="block"><span>Description</span><textarea className="field mt-1 min-h-32" required value={description} onChange={event => setDescription(event.target.value)}/></label>
    <Select label="Priority" value={priority} set={setPriority} options={["LOW","MEDIUM","HIGH","CRITICAL"].map(value => [value,value])}/>
    {type === "DEFECT" ? <Select label="Severity" value={severity} set={setSeverity} options={["SEV_1","SEV_2","SEV_3","SEV_4"].map(value => [value,value])}/> : null}
    {canAssign ? <fieldset className="rounded-lg border p-4"><legend className="px-2 font-bold">Assign ticket team now</legend><label className="block">Ticket team lead<select className="field mt-1" value={leadId} onChange={event => setLeadId(event.target.value)}><option value="">Assign later</option>{people.map(person => <option key={person.id} value={person.id}>{person.name}</option>)}</select></label><div className="mt-3"><span className="text-sm">Developers</span><div className="mt-2 grid gap-2 sm:grid-cols-2">{people.map(person => <label className={`permission-option ${developerIds.includes(person.id) ? "permission-option-selected" : ""}`} key={person.id}><input type="checkbox" checked={developerIds.includes(person.id)} onChange={() => toggleDeveloper(person.id)}/><span><strong>{person.name}</strong><small>Developer</small></span></label>)}</div></div></fieldset> : null}
    {error ? <p role="alert" className="text-red-700">{error}</p> : null}<button className="btn-primary">Create ticket</button>
  </form></div>;
}

function Select({label,value,set,options}:{label:string;value:string;set:(value:string)=>void;options:string[][]}) {
  return <label className="block"><span>{label}</span><select className="field mt-1" required value={value} onChange={event => set(event.target.value)}><option value="">Select…</option>{options.map(([optionValue,label]) => <option value={optionValue} key={optionValue}>{label}</option>)}</select></label>;
}
