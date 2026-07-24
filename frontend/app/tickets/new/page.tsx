"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AppShell } from "@/components/AppShell";
import { get, post } from "@/lib/api";
import type { CurrentUser } from "@/lib/auth";
import type { TicketDetail } from "@/lib/types";

type Ref = { id: number; key?: string; name: string };
type Team = { id: number; name: string };
type TargetUser = { id: number; displayName: string; email: string };
type FieldKind = "SHORT_TEXT" | "LONG_TEXT" | "INTEGER" | "DECIMAL" | "DATE" | "BOOLEAN" | "SINGLE_SELECT" | "MULTI_SELECT" | "USER_REFERENCE" | "TEAM_REFERENCE";
type OptionRef = { id: number; key: string; label: string; sortOrder: number; version: number };
type FieldDef = {
  id: number;
  key: string;
  label: string;
  helpText: string | null;
  fieldKind: FieldKind;
  required: boolean;
  visibility: "PUBLIC" | "INTERNAL";
  sortOrder: number;
  minLength: number | null;
  maxLength: number | null;
  minNumber: number | null;
  maxNumber: number | null;
  version: number;
  options: OptionRef[];
};
type SubtypeDef = { id: number; key: string; name: string; description: string | null; sortOrder: number; version: number; fields: FieldDef[] };
type CreationForm = { id: number; key: string; name: string; version: number; subtypes: SubtypeDef[] };

export default function NewTicketPage() {
  return <AppShell require="TICKET_CREATE">{user => <TicketForm user={user}/>}</AppShell>;
}

function TicketForm({ user }: { user: CurrentUser }) {
  const router = useRouter();
  const canAssign = user.permissions.includes("TICKET_ASSIGN");
  const [orgs, setOrgs] = useState<Ref[]>([]);
  const [types, setTypes] = useState<Ref[]>([]);
  const [people, setPeople] = useState<Ref[]>([]);
  const [teams, setTeams] = useState<Team[]>([]);
  const [org, setOrg] = useState(user.organizationId ? String(user.organizationId) : "");
  const [type, setType] = useState("");
  const [selectedTypeId, setSelectedTypeId] = useState<number | null>(null);
  const [creationForm, setCreationForm] = useState<CreationForm | null>(null);
  const [subtypeId, setSubtypeId] = useState("");
  const [dynamicValues, setDynamicValues] = useState<Record<string, unknown>>({});
  const [targetQuery, setTargetQuery] = useState("");
  const [targetResults, setTargetResults] = useState<TargetUser[]>([]);
  const [targetUser, setTargetUser] = useState<TargetUser | null>(null);
  const [parentTicketKey, setParentTicketKey] = useState("");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [priority, setPriority] = useState("MEDIUM");
  const [severity, setSeverity] = useState("SEV_3");
  const [leadId, setLeadId] = useState("");
  const [developerIds, setDeveloperIds] = useState<number[]>([]);
  const [teamIds, setTeamIds] = useState<number[]>([]);
  const [error, setError] = useState("");

  const selectedType = useMemo(() => types.find(item => item.id === selectedTypeId) ?? null, [selectedTypeId, types]);
  const selectedSubtype = useMemo(() => creationForm?.subtypes.find(item => String(item.id) === subtypeId) ?? null, [creationForm, subtypeId]);
  const needsUsrTarget = selectedType?.key === "USR" && ["MODIFY", "DELETE"].includes(selectedSubtype?.key ?? "");
  const isSubticket = parentTicketKey.trim().length > 0;
  const internalOrganization = useMemo(() => orgs.find(item => item.name.toLowerCase() === "ticketflow1 internal"), [orgs]);

  useEffect(() => {
    if (user.party !== "TICKETFLOW1") return;
    void get<Ref[]>("/reference/organizations").then(values => {
      setOrgs(values);
      const internal = values.find(item => item.name.toLowerCase() === "ticketflow1 internal");
      setOrg(current => current || (internal ? String(internal.id) : values[0]?.id ? String(values[0].id) : ""));
    });
  }, [user.party]);
  useEffect(() => { if (canAssign) void get<Ref[]>("/reference/ticket-leads").then(setPeople); }, [canAssign]);
  useEffect(() => { if (canAssign) void get<Team[]>("/teams").then(setTeams); }, [canAssign]);
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    setParentTicketKey(params.get("parent") ?? "");
  }, []);
  useEffect(() => {
    if (user.party === "TICKETFLOW1" && isSubticket && internalOrganization) {
      setOrg(String(internalOrganization.id));
    }
  }, [internalOrganization, isSubticket, user.party]);
  useEffect(() => {
    if (!org) return;
    void get<Ref[]>(`/reference/ticket-types?organizationId=${org}`).then(values => {
      setTypes(values);
      setType("");
      setSelectedTypeId(null);
      setCreationForm(null);
      setSubtypeId("");
      setDynamicValues({});
      setTargetUser(null);
      setTargetQuery("");
    }).catch(error => setError(error instanceof Error ? error.message : "Could not load ticket types."));
  }, [org]);
  useEffect(() => {
    setCreationForm(null);
    setSubtypeId("");
    setDynamicValues({});
    setTargetUser(null);
    setTargetQuery("");
    if (!selectedType) return;
    void get<CreationForm>(`/reference/ticket-types/${selectedType.id}/creation-form`).then(form => {
      setCreationForm(form);
      setSubtypeId(form.subtypes[0]?.id ? String(form.subtypes[0].id) : "");
    }).catch(error => setError(error instanceof Error ? error.message : "Could not load ticket creation form."));
  }, [selectedType]);
  useEffect(() => {
    setDynamicValues({});
    setTargetUser(null);
    setTargetQuery("");
  }, [subtypeId]);
  useEffect(() => {
    if (!needsUsrTarget || targetQuery.trim().length < 2 || !org) {
      setTargetResults([]);
      return;
    }
    const timeout = window.setTimeout(() => {
      void get<TargetUser[]>(`/reference/users?q=${encodeURIComponent(targetQuery.trim())}&purpose=USR_TARGET&organizationId=${org}`)
        .then(setTargetResults)
        .catch(error => setError(error instanceof Error ? error.message : "Could not search users."));
    }, 250);
    return () => window.clearTimeout(timeout);
  }, [needsUsrTarget, org, targetQuery]);

  function toggleDeveloper(id: number) { setDeveloperIds(values => values.includes(id) ? values.filter(value => value !== id) : [...values, id]); }
  function toggleTeam(id: number) { setTeamIds(values => values.includes(id) ? values.filter(value => value !== id) : [...values, id]); }
  function setDynamic(key: string, value: unknown) { setDynamicValues(values => ({ ...values, [key]: value })); }
  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    if (!selectedType) { setError("Select a ticket type from the available results."); return; }
    const selectedTypeKey = selectedType.key ?? selectedType.name;
    if (creationForm?.subtypes.length && !selectedSubtype) { setError("Select a subtype."); return; }
    if (needsUsrTarget && !targetUser) { setError("Select the user affected by this USR request."); return; }
    try {
      const ticket = await post<TicketDetail>("/tickets", {
        type: selectedTypeKey,
        title,
        description,
        priority,
        severity: selectedTypeKey === "DEFECT" || selectedTypeKey === "DFCT" ? severity : null,
        organizationId: user.party === "TICKETFLOW1" ? Number(org) : null,
        ticketLeadId: canAssign && leadId ? Number(leadId) : null,
        developerIds: canAssign ? developerIds : null,
        teamIds: canAssign ? teamIds : null,
        subtypeId: selectedSubtype ? selectedSubtype.id : null,
        dynamicValues: selectedSubtype ? compactValues(dynamicValues, selectedSubtype.fields) : null,
        parentTicketKey: parentTicketKey.trim() || null,
        targetUserId: targetUser?.id ?? null,
      });
      router.push(`/tickets/${ticket.ticketKey}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not create ticket.");
    }
  }

  return <div className="max-w-4xl">
    <h1 className="text-3xl font-bold">Create ticket</h1>
    <form className="card mt-6 space-y-5" onSubmit={submit}>
      {user.party === "TICKETFLOW1" ? <Select label="Organization" value={org} set={setOrg} disabled={isSubticket} options={orgs.map(item => [String(item.id), item.name])} help={isSubticket ? "Subtickets created by TicketFlow1 are internal work items. The parent can still be a client ticket." : "TASI and USR are saved under TicketFlow1 Internal automatically."}/> : null}
      <fieldset className="rounded-lg border p-4"><legend className="px-2 font-bold">Ticket type</legend><label className="block"><span className="sr-only">Search ticket types</span><input aria-label="Ticket type" className="field mt-1" required value={type} onChange={event => { setType(event.target.value); setSelectedTypeId(null); setCreationForm(null); setSubtypeId(""); }} placeholder="Search standard or custom ticket types..." autoComplete="off"/><small className="text-slate-500">Select a configured ticket type to open its subtype form.</small></label>{!selectedType ? <div className="mt-3 grid gap-2 sm:grid-cols-2" role="listbox" aria-label="Available ticket types">{types.filter(item => !type.trim() || (item.key ?? item.name).toLowerCase().includes(type.trim().toLowerCase()) || item.name.toLowerCase().includes(type.trim().toLowerCase())).map(item => <button type="button" role="option" aria-selected={false} className="permission-option w-full text-left" key={item.id} onClick={() => { setType(item.key ?? item.name); setSelectedTypeId(item.id); }}><span><strong>{item.name}</strong><small>{item.key ?? item.name}</small></span></button>)}{type.trim() && !types.some(item => (item.key ?? item.name).toLowerCase().includes(type.trim().toLowerCase()) || item.name.toLowerCase().includes(type.trim().toLowerCase())) ? <p className="text-sm text-slate-500">No matching ticket types.</p> : null}</div> : null}{selectedType ? <p className="mt-3 rounded border border-blue-500/30 bg-blue-950/20 p-2 text-sm">Selected: <strong>{selectedType.name}</strong> ({selectedType.key ?? selectedType.name})</p> : null}</fieldset>
      {creationForm?.subtypes.length ? <SubtypeAndFields subtypeId={subtypeId} setSubtypeId={setSubtypeId} selectedSubtype={selectedSubtype} form={creationForm} values={dynamicValues} setValue={setDynamic} people={people} teams={teams}/> : null}
      {needsUsrTarget ? <TargetUserSearch query={targetQuery} setQuery={setTargetQuery} results={targetResults} selected={targetUser} setSelected={setTargetUser}/> : null}
      <label className="block"><span>Parent ticket key</span><input className="field mt-1" value={parentTicketKey} onChange={event => setParentTicketKey(event.target.value.toUpperCase())} placeholder="Optional, e.g. TKT-1234"/><small className="text-slate-500">Use this when creating a subticket from an existing ticket.</small></label>
      <label className="block"><span>Title</span><input className="field mt-1" required maxLength={300} value={title} onChange={event => setTitle(event.target.value)}/></label>
      <label className="block"><span>Description</span><textarea className="field mt-1 min-h-32" required value={description} onChange={event => setDescription(event.target.value)}/></label>
      <Select label="Priority" value={priority} set={setPriority} options={["LOW","MEDIUM","HIGH","CRITICAL"].map(value => [value,value])}/>
      {selectedType?.key === "DEFECT" || selectedType?.key === "DFCT" ? <Select label="Severity" value={severity} set={setSeverity} options={["SEV_1","SEV_2","SEV_3","SEV_4"].map(value => [value,value])}/> : null}
      {canAssign ? <fieldset className="rounded-lg border p-4"><legend className="px-2 font-bold">Assign ticket team now</legend><label className="block">Ticket team lead<select className="field mt-1" value={leadId} onChange={event => setLeadId(event.target.value)}><option value="">Assign later</option>{people.map(person => <option key={person.id} value={person.id}>{person.name}</option>)}</select></label><div className="mt-3"><span className="text-sm">Developer teams</span><div className="mt-2 grid gap-2 sm:grid-cols-2">{teams.map(team=><label className={`permission-option ${teamIds.includes(team.id)?"permission-option-selected":""}`} key={team.id}><input type="checkbox" checked={teamIds.includes(team.id)} onChange={()=>toggleTeam(team.id)}/><span><strong>{team.name}</strong><small>{teamIds.includes(team.id)?"Assigned team":"Assign team"}</small></span></label>)}</div></div><div className="mt-3"><span className="text-sm">Developers</span><div className="mt-2 grid gap-2 sm:grid-cols-2">{people.map(person => <label className={`permission-option ${developerIds.includes(person.id) ? "permission-option-selected" : ""}`} key={person.id}><input type="checkbox" checked={developerIds.includes(person.id)} onChange={() => toggleDeveloper(person.id)}/><span><strong>{person.name}</strong><small>Developer</small></span></label>)}</div></div></fieldset> : null}
      {error ? <p role="alert" className="text-red-400">{error}</p> : null}
      <button className="btn-primary">Create ticket</button>
    </form>
  </div>;
}

function SubtypeAndFields({ subtypeId, setSubtypeId, selectedSubtype, form, values, setValue, people, teams }: {
  subtypeId: string;
  setSubtypeId: (value: string) => void;
  selectedSubtype: SubtypeDef | null;
  form: CreationForm;
  values: Record<string, unknown>;
  setValue: (key: string, value: unknown) => void;
  people: Ref[];
  teams: Team[];
}) {
  return <fieldset className="rounded-lg border p-4">
    <legend className="px-2 font-bold">Subtype-specific fields</legend>
    <label className="block">Subtype<select className="field mt-1" required value={subtypeId} onChange={event => setSubtypeId(event.target.value)}>
      {form.subtypes.map(subtype => <option value={subtype.id} key={subtype.id}>{subtype.name}</option>)}
    </select></label>
    {selectedSubtype?.description ? <p className="mt-2 text-sm text-slate-500">{selectedSubtype.description}</p> : null}
    {selectedSubtype ? <p className="mt-2 text-xs text-slate-500">{selectedSubtype.fields.length} dynamic field{selectedSubtype.fields.length === 1 ? "" : "s"} configured for this subtype.</p> : null}
    <div className="mt-4 grid gap-4 sm:grid-cols-2">
      {selectedSubtype?.fields.map(field => <DynamicField field={field} value={values[field.key]} setValue={value => setValue(field.key, value)} people={people} teams={teams} key={field.id}/>)}
      {selectedSubtype && selectedSubtype.fields.length === 0 ? <p className="rounded-lg border border-dashed p-4 text-sm text-slate-500 sm:col-span-2">This subtype has no active fields yet.</p> : null}
    </div>
  </fieldset>;
}

function DynamicField({ field, value, setValue, people, teams }: { field: FieldDef; value: unknown; setValue: (value: unknown) => void; people: Ref[]; teams: Team[] }) {
  const common = { required: field.required };
  const label = <span>{field.label}{field.visibility === "INTERNAL" ? <small className="ml-2 text-xs text-blue-400">Internal</small> : null}</span>;
  if (field.fieldKind === "LONG_TEXT") return <label className="block sm:col-span-2">{label}<textarea className="field mt-1 min-h-24" {...common} minLength={field.minLength ?? undefined} maxLength={field.maxLength ?? undefined} value={String(value ?? "")} onChange={event => setValue(event.target.value)}/>{field.helpText ? <small className="text-slate-500">{field.helpText}</small> : null}</label>;
  if (field.fieldKind === "BOOLEAN") return <label className="permission-option"><input type="checkbox" checked={Boolean(value)} onChange={event => setValue(event.target.checked)}/><span><strong>{field.label}</strong>{field.helpText ? <small>{field.helpText}</small> : null}</span></label>;
  if (field.fieldKind === "SINGLE_SELECT") return <label className="block">{label}<select className="field mt-1" {...common} value={String(value ?? "")} onChange={event => setValue(event.target.value)}><option value="">Select...</option>{field.options.map(option => <option value={option.key} key={option.id}>{option.label}</option>)}</select>{field.helpText ? <small className="text-slate-500">{field.helpText}</small> : null}</label>;
  if (field.fieldKind === "MULTI_SELECT") return <fieldset className="rounded-lg border p-3 sm:col-span-2"><legend className="px-2">{field.label}</legend><div className="grid gap-2 sm:grid-cols-2">{field.options.map(option => {
    const selected = Array.isArray(value) && value.includes(option.key);
    return <label className={`permission-option ${selected ? "permission-option-selected" : ""}`} key={option.id}><input type="checkbox" checked={selected} onChange={() => setValue(selected ? (value as string[]).filter(item => item !== option.key) : [...(Array.isArray(value) ? value as string[] : []), option.key])}/><span><strong>{option.label}</strong><small>{option.key}</small></span></label>;
  })}</div>{field.helpText ? <p className="mt-2 text-xs text-slate-500">{field.helpText}</p> : null}</fieldset>;
  if (field.fieldKind === "USER_REFERENCE") return <label className="block">{label}<select className="field mt-1" {...common} value={String(value ?? "")} onChange={event => setValue(event.target.value ? Number(event.target.value) : "")}><option value="">Select user...</option>{people.map(person => <option value={person.id} key={person.id}>{person.name}</option>)}</select>{field.helpText ? <small className="text-slate-500">{field.helpText}</small> : null}</label>;
  if (field.fieldKind === "TEAM_REFERENCE") return <label className="block">{label}<select className="field mt-1" {...common} value={String(value ?? "")} onChange={event => setValue(event.target.value ? Number(event.target.value) : "")}><option value="">Select team...</option>{teams.map(team => <option value={team.id} key={team.id}>{team.name}</option>)}</select>{field.helpText ? <small className="text-slate-500">{field.helpText}</small> : null}</label>;
  const type = field.fieldKind === "DATE" ? "date" : field.fieldKind === "INTEGER" || field.fieldKind === "DECIMAL" ? "number" : "text";
  return <label className="block">{label}<input className="field mt-1" {...common} type={type} minLength={field.minLength ?? undefined} maxLength={field.maxLength ?? undefined} min={field.minNumber ?? undefined} max={field.maxNumber ?? undefined} step={field.fieldKind === "DECIMAL" ? "any" : undefined} value={String(value ?? "")} onChange={event => setValue(event.target.value)}/>{field.helpText ? <small className="text-slate-500">{field.helpText}</small> : null}</label>;
}

function TargetUserSearch({ query, setQuery, results, selected, setSelected }: {
  query: string;
  setQuery: (value: string) => void;
  results: TargetUser[];
  selected: TargetUser | null;
  setSelected: (user: TargetUser | null) => void;
}) {
  return <fieldset className="rounded-lg border p-4">
    <legend className="px-2 font-bold">Affected user</legend>
    {selected ? <div className="mb-3 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-blue-500/30 bg-blue-950/20 p-3"><div><strong>{selected.displayName}</strong><p className="text-xs text-slate-500">{selected.email}</p></div><button type="button" className="btn-secondary" onClick={() => setSelected(null)}>Change user</button></div> : null}
    <label className="block">Search user<input className="field mt-1" value={query} onChange={event => setQuery(event.target.value)} placeholder="Type at least 2 characters..."/></label>
    {!selected && results.length ? <div className="mt-3 grid gap-2">{results.map(user => <button type="button" className="rounded-lg border p-3 text-left hover:bg-slate-50" key={user.id} onClick={() => setSelected(user)}><strong>{user.displayName}</strong><span className="block text-xs text-slate-500">{user.email}</span></button>)}</div> : null}
  </fieldset>;
}

function Select({label,value,set,options,disabled=false,help}:{label:string;value:string;set:(value:string)=>void;options:string[][];disabled?:boolean;help?:string}) {
  return <label className="block"><span>{label}</span><select className="field mt-1 disabled:opacity-70" required disabled={disabled} value={value} onChange={event => set(event.target.value)}><option value="">Select...</option>{options.map(([optionValue,label]) => <option value={optionValue} key={optionValue}>{label}</option>)}</select>{help ? <small className="text-slate-500">{help}</small> : null}</label>;
}

export function compactValues(values: Record<string, unknown>, fields: Pick<FieldDef, "key">[]) {
  const allowed = new Set(fields.map(field => field.key));
  return Object.fromEntries(Object.entries(values).filter(([key, value]) => allowed.has(key) && value !== "" && value !== null && value !== undefined && (!Array.isArray(value) || value.length)));
}
