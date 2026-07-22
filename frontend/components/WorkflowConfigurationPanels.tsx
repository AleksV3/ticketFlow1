"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { api, get, patch, post, put } from "@/lib/api";

type Workflow = { id: number; name: string };
type TicketType = {
  id: number;
  key: string;
  name: string;
  workflowId: number;
  organizationId: number | null;
  active?: boolean;
  sortOrder?: number;
  capability?: string;
  version?: number;
};
type Subtype = { id: number; ticketTypeId: number; key: string; name: string; description: string | null; active: boolean; sortOrder: number; version: number };
type Field = {
  id: number;
  subtypeId: number;
  key: string;
  label: string;
  helpText: string | null;
  fieldKind: FieldKind;
  required: boolean;
  visibility: "PUBLIC" | "INTERNAL";
  active: boolean;
  sortOrder: number;
  minLength: number | null;
  maxLength: number | null;
  minNumber: number | null;
  maxNumber: number | null;
  version: number;
};
type Option = { id: number; fieldId: number; key: string; label: string; active: boolean; sortOrder: number; version: number };
type Routing = { id: number; subtypeId: number; organizationId: number | null; teamId: number; primaryDeveloperId: number | null; fallbackDeveloperId: number | null; approverId: number | null; active: boolean; version: number };
type Person = { id: number; name: string; party: string; organizationName: string | null };
type Team = { id: number; name: string; members: Person[]; leader: Person };
type FieldKind = "SHORT_TEXT" | "LONG_TEXT" | "INTEGER" | "DECIMAL" | "DATE" | "BOOLEAN" | "SINGLE_SELECT" | "MULTI_SELECT" | "USER_REFERENCE" | "TEAM_REFERENCE";

const FIELD_KINDS: FieldKind[] = ["SHORT_TEXT", "LONG_TEXT", "INTEGER", "DECIMAL", "DATE", "BOOLEAN", "SINGLE_SELECT", "MULTI_SELECT", "USER_REFERENCE", "TEAM_REFERENCE"];
const CAPABILITIES = ["STANDARD", "DEFECT_SLA"];

export function WorkflowConfigurationPanels({
  organizationId,
  workflows,
  types,
  reload,
  report,
}: {
  organizationId: string;
  workflows: Workflow[];
  types: TicketType[];
  reload: () => Promise<void>;
  report: (message: string) => void;
}) {
  const [selectedTypeId, setSelectedTypeId] = useState<number | null>(types[0]?.id ?? null);
  useEffect(() => {
    setSelectedTypeId(current => current && types.some(type => type.id === current) ? current : types[0]?.id ?? null);
  }, [types]);
  const selectedType = types.find(type => type.id === selectedTypeId) ?? null;

  return <section className="grid gap-6 xl:grid-cols-[minmax(0,420px)_minmax(0,1fr)]">
    <TypeAdministration
      organizationId={organizationId}
      workflows={workflows}
      types={types}
      selectedTypeId={selectedTypeId}
      selectType={setSelectedTypeId}
      reload={reload}
      report={report}
    />
    {selectedType ? <SubtypeAdministration
      organizationId={organizationId}
      type={selectedType}
      reloadTypes={reload}
      report={report}
    /> : <div className="card grid min-h-72 place-items-center text-center text-slate-500">Create or select a ticket type to configure its subtypes, form fields, and routing.</div>}
  </section>;
}

function TypeAdministration({ organizationId, workflows, types, selectedTypeId, selectType, reload, report }: {
  organizationId: string;
  workflows: Workflow[];
  types: TicketType[];
  selectedTypeId: number | null;
  selectType: (id: number) => void;
  reload: () => Promise<void>;
  report: (message: string) => void;
}) {
  async function updateType(type: TicketType, patchBody: Record<string, unknown>) {
    if (type.version === undefined) {
      report("This ticket type needs to be reloaded before editing.");
      await reload();
      return;
    }
    const body = { version: type.version, ...patchBody };
    try {
      await patch(`/admin/ticket-types/${type.id}`, body);
      report("Ticket type updated.");
      await reload();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not update ticket type.");
    }
  }
  async function setActive(type: TicketType, active: boolean) {
    try {
      await post(`/admin/ticket-types/${type.id}/${active ? "activate" : "deactivate"}`);
      report(active ? "Ticket type activated." : "Ticket type deactivated.");
      await reload();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not change ticket type activity.");
    }
  }
  async function deleteType(type: TicketType) {
    if (!window.confirm(`Delete ${type.name}? Used ticket types can only be deactivated.`)) return;
    try {
      await api(`/admin/ticket-types/${type.id}`, { method: "DELETE" });
      report("Ticket type deleted.");
      await reload();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not delete ticket type.");
    }
  }
  async function reorder(type: TicketType, direction: -1 | 1) {
    await updateType(type, { sortOrder: Math.max(0, (type.sortOrder ?? 0) + direction * 10) });
  }

  return <section className="card self-start">
    <div className="mb-4 flex items-start justify-between gap-3">
      <div>
        <p className="eyebrow">Type administration</p>
        <h2 className="mt-1 text-xl font-bold">Ticket types</h2>
        <p className="mt-1 text-sm text-slate-500">Set workflow, active state, ordering, and capability.</p>
      </div>
      <span className="badge bg-slate-100 text-slate-700">{types.length}</span>
    </div>
    <div className="space-y-3">
      {types.map(type => <article className={`rounded-lg border p-3 ${selectedTypeId === type.id ? "border-blue-500 bg-blue-950/30" : ""}`} key={type.id}>
        <button type="button" className="w-full text-left" onClick={() => selectType(type.id)}>
          <span className="flex items-center justify-between gap-2">
            <strong>{type.name}</strong>
            <span className={`badge ${type.active === false ? "bg-red-100 text-red-700" : "bg-emerald-100 text-emerald-700"}`}>{type.active === false ? "Inactive" : "Active"}</span>
          </span>
          <span className="mt-1 block text-xs text-slate-500">{type.key} · order {type.sortOrder ?? 0}</span>
        </button>
        <div className="mt-3 grid gap-2">
          <label>Name<input className="field mt-1" defaultValue={type.name} onBlur={event => {
            const next = event.currentTarget.value.trim();
            if (next && next !== type.name) void updateType(type, { name: next });
          }} /></label>
          <label>Workflow<select className="field mt-1" value={type.workflowId} onChange={event => void updateType(type, { workflowId: Number(event.target.value) })}>
            {workflows.map(workflow => <option value={workflow.id} key={workflow.id}>{workflow.name}</option>)}
          </select></label>
          <label>Capability<select className="field mt-1" value={type.capability ?? "STANDARD"} onChange={event => void updateType(type, { capability: event.target.value })}>
            {CAPABILITIES.map(value => <option key={value} value={value}>{pretty(value)}</option>)}
          </select></label>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <button type="button" className="btn-secondary px-2 py-1 text-xs" onClick={() => void reorder(type, -1)}>Move up</button>
          <button type="button" className="btn-secondary px-2 py-1 text-xs" onClick={() => void reorder(type, 1)}>Move down</button>
          <button type="button" className="btn-secondary px-2 py-1 text-xs" onClick={() => void setActive(type, type.active === false)}>{type.active === false ? "Activate" : "Deactivate"}</button>
          <button type="button" className="btn-secondary px-2 py-1 text-xs text-red-300" onClick={() => void deleteType(type)}>Delete</button>
        </div>
      </article>)}
      {!types.length ? <p className="text-sm text-slate-500">No ticket types in this scope.</p> : null}
    </div>
    {organizationId === "internal" ? <p className="mt-4 text-xs text-slate-500">Internal template types are cloned to organizations when they are created.</p> : null}
  </section>;
}

function SubtypeAdministration({ organizationId, type, reloadTypes, report }: {
  organizationId: string;
  type: TicketType;
  reloadTypes: () => Promise<void>;
  report: (message: string) => void;
}) {
  const [subtypes, setSubtypes] = useState<Subtype[]>([]);
  const [selectedSubtypeId, setSelectedSubtypeId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const selectedSubtype = subtypes.find(subtype => subtype.id === selectedSubtypeId) ?? null;
  const loadSubtypes = useCallback(async () => {
    setLoading(true);
    try {
      const rows = await get<Subtype[]>(`/admin/ticket-types/${type.id}/subtypes`);
      setSubtypes(rows);
      setSelectedSubtypeId(current => current && rows.some(row => row.id === current) ? current : rows[0]?.id ?? null);
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not load subtypes.");
    } finally {
      setLoading(false);
    }
  }, [report, type.id]);
  useEffect(() => { void loadSubtypes(); }, [loadSubtypes]);

  async function createSubtype(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await post(`/admin/ticket-types/${type.id}/subtypes`, {
        key: form.get("key"),
        name: form.get("name"),
        description: form.get("description"),
        sortOrder: subtypes.length * 10,
      });
      event.currentTarget.reset();
      report("Subtype created.");
      await loadSubtypes();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not create subtype.");
    }
  }
  async function updateSubtype(subtype: Subtype, body: Partial<Subtype>) {
    try {
      await put(`/admin/subtypes/${subtype.id}`, {
        version: subtype.version,
        name: body.name ?? subtype.name,
        description: body.description ?? subtype.description,
        sortOrder: body.sortOrder ?? subtype.sortOrder,
      });
      report("Subtype updated.");
      await loadSubtypes();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not update subtype.");
    }
  }
  async function setSubtypeActive(subtype: Subtype, active: boolean) {
    try {
      await post(`/admin/subtypes/${subtype.id}/${active ? "activate" : "deactivate"}`);
      report(active ? "Subtype activated." : "Subtype deactivated.");
      await loadSubtypes();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not change subtype activity.");
    }
  }
  async function deleteSubtype(subtype: Subtype) {
    if (!window.confirm(`Delete ${subtype.name}? Used subtypes can only be deactivated.`)) return;
    try {
      await api(`/admin/subtypes/${subtype.id}`, { method: "DELETE" });
      report("Subtype deleted.");
      await loadSubtypes();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not delete subtype.");
    }
  }
  async function reorderSubtypes(id: number, direction: -1 | 1) {
    const index = subtypes.findIndex(subtype => subtype.id === id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= subtypes.length) return;
    const next = [...subtypes];
    [next[index], next[target]] = [next[target], next[index]];
    try {
      await put(`/admin/ticket-types/${type.id}/subtypes/order`, { ids: next.map(subtype => subtype.id) });
      report("Subtype order saved.");
      await loadSubtypes();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not reorder subtypes.");
    }
  }

  return <section className="space-y-5">
    <div className="card">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="eyebrow">Subtype forms</p>
          <h2 className="mt-1 text-xl font-bold">{type.name}</h2>
          <p className="mt-1 text-sm text-slate-500">Subtypes define the extra fields users fill when creating this ticket type.</p>
        </div>
        {loading ? <span className="text-xs text-slate-500">Loading…</span> : null}
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="space-y-2">
          {subtypes.map((subtype, index) => <article className={`rounded-lg border p-3 ${selectedSubtypeId === subtype.id ? "border-blue-500 bg-blue-950/30" : ""}`} key={subtype.id}>
            <button type="button" className="w-full text-left" onClick={() => setSelectedSubtypeId(subtype.id)}>
              <span className="flex items-center justify-between gap-2">
                <strong>{subtype.name}</strong>
                <span className={`badge ${subtype.active ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"}`}>{subtype.active ? "Active" : "Inactive"}</span>
              </span>
              <span className="mt-1 block text-xs text-slate-500">{subtype.key} · {subtype.description || "No description"}</span>
            </button>
            <div className="mt-3 flex flex-wrap gap-2">
              <button type="button" className="btn-secondary px-2 py-1 text-xs" disabled={index === 0} onClick={() => void reorderSubtypes(subtype.id, -1)}>Move up</button>
              <button type="button" className="btn-secondary px-2 py-1 text-xs" disabled={index === subtypes.length - 1} onClick={() => void reorderSubtypes(subtype.id, 1)}>Move down</button>
              <button type="button" className="btn-secondary px-2 py-1 text-xs" onClick={() => void setSubtypeActive(subtype, !subtype.active)}>{subtype.active ? "Deactivate" : "Activate"}</button>
              <button type="button" className="btn-secondary px-2 py-1 text-xs text-red-300" onClick={() => void deleteSubtype(subtype)}>Delete</button>
            </div>
            {selectedSubtypeId === subtype.id ? <div className="mt-3 grid gap-2 sm:grid-cols-2">
              <label>Name<input className="field mt-1" defaultValue={subtype.name} onBlur={event => {
                const next = event.currentTarget.value.trim();
                if (next && next !== subtype.name) void updateSubtype(subtype, { name: next });
              }} /></label>
              <label>Description<input className="field mt-1" defaultValue={subtype.description ?? ""} onBlur={event => {
                const next = event.currentTarget.value.trim();
                if (next !== (subtype.description ?? "")) void updateSubtype(subtype, { description: next });
              }} /></label>
            </div> : null}
          </article>)}
          {!subtypes.length ? <p className="rounded-lg border border-dashed p-5 text-center text-sm text-slate-500">No subtypes yet.</p> : null}
        </div>
        <form className="rounded-lg border p-3" onSubmit={event => void createSubtype(event)}>
          <h3 className="font-bold">Add subtype</h3>
          <label className="mt-3 block">Key<input name="key" className="field mt-1" required placeholder="FIREWALL" /></label>
          <label className="mt-3 block">Name<input name="name" className="field mt-1" required placeholder="Firewall" /></label>
          <label className="mt-3 block">Description<textarea name="description" className="field mt-1 min-h-20" /></label>
          <button className="btn-primary mt-3 w-full">Create subtype</button>
        </form>
      </div>
    </div>
    {selectedSubtype ? <div className="grid gap-5 xl:grid-cols-2">
      <FieldAdministration subtype={selectedSubtype} report={report} />
      <RoutingAdministration organizationId={organizationId} subtype={selectedSubtype} reloadTypes={reloadTypes} report={report} />
    </div> : null}
  </section>;
}

function FieldAdministration({ subtype, report }: { subtype: Subtype; report: (message: string) => void }) {
  const [fields, setFields] = useState<Field[]>([]);
  const [selectedFieldId, setSelectedFieldId] = useState<number | null>(null);
  const selectedField = fields.find(field => field.id === selectedFieldId) ?? null;
  const loadFields = useCallback(async () => {
    try {
      const rows = await get<Field[]>(`/admin/subtypes/${subtype.id}/fields`);
      setFields(rows);
      setSelectedFieldId(current => current && rows.some(row => row.id === current) ? current : rows[0]?.id ?? null);
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not load fields.");
    }
  }, [report, subtype.id]);
  useEffect(() => { void loadFields(); }, [loadFields]);

  async function createField(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const fieldKind = form.get("fieldKind") as FieldKind;
    try {
      await post(`/admin/subtypes/${subtype.id}/fields`, {
        key: form.get("key"),
        label: form.get("label"),
        helpText: form.get("helpText"),
        fieldKind,
        required: form.get("required") === "on",
        visibility: form.get("visibility"),
        sortOrder: fields.length * 10,
        minLength: textKind(fieldKind) ? numberOrNull(form.get("minLength")) : null,
        maxLength: textKind(fieldKind) ? numberOrNull(form.get("maxLength")) : null,
        minNumber: numberKind(fieldKind) ? numberOrNull(form.get("minNumber")) : null,
        maxNumber: numberKind(fieldKind) ? numberOrNull(form.get("maxNumber")) : null,
      });
      event.currentTarget.reset();
      report("Field created.");
      await loadFields();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not create field.");
    }
  }
  async function updateField(field: Field, body: Partial<Field>) {
    try {
      await put(`/admin/fields/${field.id}`, {
        version: field.version,
        label: body.label ?? field.label,
        helpText: body.helpText ?? field.helpText,
        required: body.required ?? field.required,
        visibility: body.visibility ?? field.visibility,
        sortOrder: body.sortOrder ?? field.sortOrder,
        minLength: body.minLength ?? field.minLength,
        maxLength: body.maxLength ?? field.maxLength,
        minNumber: body.minNumber ?? field.minNumber,
        maxNumber: body.maxNumber ?? field.maxNumber,
      });
      report("Field updated.");
      await loadFields();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not update field.");
    }
  }
  async function setActive(field: Field, active: boolean) {
    try {
      await post(`/admin/fields/${field.id}/${active ? "activate" : "deactivate"}`);
      report(active ? "Field activated." : "Field deactivated.");
      await loadFields();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not change field activity.");
    }
  }
  async function removeField(field: Field) {
    if (!window.confirm(`Delete ${field.label}? Used fields can only be deactivated.`)) return;
    try {
      await api(`/admin/fields/${field.id}`, { method: "DELETE" });
      report("Field deleted.");
      await loadFields();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not delete field.");
    }
  }
  async function reorder(id: number, direction: -1 | 1) {
    const index = fields.findIndex(field => field.id === id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= fields.length) return;
    const next = [...fields];
    [next[index], next[target]] = [next[target], next[index]];
    try {
      await put(`/admin/subtypes/${subtype.id}/fields/order`, { ids: next.map(field => field.id) });
      report("Field order saved.");
      await loadFields();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not reorder fields.");
    }
  }

  return <section className="card">
    <div className="mb-4">
      <p className="eyebrow">Dynamic fields</p>
      <h2 className="mt-1 text-xl font-bold">{subtype.name} form</h2>
    </div>
    <div className="space-y-2">
      {fields.map((field, index) => <article className={`rounded-lg border p-3 ${selectedFieldId === field.id ? "border-blue-500 bg-blue-950/30" : ""}`} key={field.id}>
        <button type="button" className="w-full text-left" onClick={() => setSelectedFieldId(field.id)}>
          <span className="flex items-center justify-between gap-2"><strong>{field.label}</strong><span className={`badge ${field.active ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"}`}>{field.active ? "Active" : "Inactive"}</span></span>
          <span className="mt-1 block text-xs text-slate-500">{field.key} · {pretty(field.fieldKind)} · {field.visibility.toLowerCase()}</span>
        </button>
        <div className="mt-3 flex flex-wrap gap-2">
          <button type="button" className="btn-secondary px-2 py-1 text-xs" disabled={index === 0} onClick={() => void reorder(field.id, -1)}>Move up</button>
          <button type="button" className="btn-secondary px-2 py-1 text-xs" disabled={index === fields.length - 1} onClick={() => void reorder(field.id, 1)}>Move down</button>
          <button type="button" className="btn-secondary px-2 py-1 text-xs" onClick={() => void setActive(field, !field.active)}>{field.active ? "Deactivate" : "Activate"}</button>
          <button type="button" className="btn-secondary px-2 py-1 text-xs text-red-300" onClick={() => void removeField(field)}>Delete</button>
        </div>
        {selectedFieldId === field.id ? <div className="mt-3 grid gap-2 sm:grid-cols-2">
          <label>Label<input className="field mt-1" defaultValue={field.label} onBlur={event => {
            const next = event.currentTarget.value.trim();
            if (next && next !== field.label) void updateField(field, { label: next });
          }} /></label>
          <label>Help text<input className="field mt-1" defaultValue={field.helpText ?? ""} onBlur={event => {
            const next = event.currentTarget.value.trim();
            if (next !== (field.helpText ?? "")) void updateField(field, { helpText: next });
          }} /></label>
          <label>Visibility<select className="field mt-1" value={field.visibility} onChange={event => void updateField(field, { visibility: event.target.value as Field["visibility"] })}><option value="PUBLIC">Public</option><option value="INTERNAL">Internal</option></select></label>
          <label className="flex items-center gap-2 pt-7"><input type="checkbox" checked={field.required} onChange={event => void updateField(field, { required: event.target.checked })} /> Required</label>
        </div> : null}
      </article>)}
      {!fields.length ? <p className="rounded-lg border border-dashed p-5 text-center text-sm text-slate-500">No fields yet.</p> : null}
    </div>
    <form className="mt-5 grid gap-3 rounded-lg border p-3 sm:grid-cols-2" onSubmit={event => void createField(event)}>
      <h3 className="font-bold sm:col-span-2">Add field</h3>
      <label>Key<input name="key" className="field mt-1" required placeholder="source_ip" /></label>
      <label>Label<input name="label" className="field mt-1" required placeholder="Source IP" /></label>
      <label>Field kind<select name="fieldKind" className="field mt-1" defaultValue="SHORT_TEXT">{FIELD_KINDS.map(kind => <option value={kind} key={kind}>{pretty(kind)}</option>)}</select></label>
      <label>Visibility<select name="visibility" className="field mt-1" defaultValue="INTERNAL"><option value="PUBLIC">Public</option><option value="INTERNAL">Internal</option></select></label>
      <label>Min length<input name="minLength" className="field mt-1" inputMode="numeric" /></label>
      <label>Max length<input name="maxLength" className="field mt-1" inputMode="numeric" /></label>
      <label>Min number<input name="minNumber" className="field mt-1" inputMode="decimal" /></label>
      <label>Max number<input name="maxNumber" className="field mt-1" inputMode="decimal" /></label>
      <label className="flex items-center gap-2"><input name="required" type="checkbox" /> Required</label>
      <label className="sm:col-span-2">Help text<input name="helpText" className="field mt-1" /></label>
      <button className="btn-primary sm:col-span-2">Create field</button>
    </form>
    {selectedField && selectKind(selectedField.fieldKind) ? <OptionAdministration field={selectedField} report={report} /> : null}
  </section>;
}

function OptionAdministration({ field, report }: { field: Field; report: (message: string) => void }) {
  const [options, setOptions] = useState<Option[]>([]);
  const loadOptions = useCallback(async () => {
    try { setOptions(await get<Option[]>(`/admin/fields/${field.id}/options`)); }
    catch (error) { report(error instanceof Error ? error.message : "Could not load options."); }
  }, [field.id, report]);
  useEffect(() => { void loadOptions(); }, [loadOptions]);
  async function createOption(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      await post(`/admin/fields/${field.id}/options`, { key: form.get("key"), label: form.get("label"), sortOrder: options.length * 10 });
      event.currentTarget.reset();
      report("Option created.");
      await loadOptions();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not create option.");
    }
  }
  async function updateOption(option: Option, label: string) {
    try {
      await put(`/admin/field-options/${option.id}`, { version: option.version, label, sortOrder: option.sortOrder });
      report("Option updated.");
      await loadOptions();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not update option.");
    }
  }
  async function setActive(option: Option, active: boolean) {
    try {
      await post(`/admin/field-options/${option.id}/${active ? "activate" : "deactivate"}`);
      report(active ? "Option activated." : "Option deactivated.");
      await loadOptions();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not change option activity.");
    }
  }
  async function removeOption(option: Option) {
    if (!window.confirm(`Delete ${option.label}? Used options can only be deactivated.`)) return;
    try {
      await api(`/admin/field-options/${option.id}`, { method: "DELETE" });
      report("Option deleted.");
      await loadOptions();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not delete option.");
    }
  }
  async function reorder(id: number, direction: -1 | 1) {
    const index = options.findIndex(option => option.id === id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= options.length) return;
    const next = [...options];
    [next[index], next[target]] = [next[target], next[index]];
    try {
      await put(`/admin/fields/${field.id}/options/order`, { ids: next.map(option => option.id) });
      report("Option order saved.");
      await loadOptions();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not reorder options.");
    }
  }
  return <div className="mt-5 rounded-lg border p-3">
    <h3 className="font-bold">Select options</h3>
    <div className="mt-3 space-y-2">
      {options.map((option, index) => <div className="rounded-lg border p-2" key={option.id}>
        <div className="flex items-center justify-between gap-2">
          <div><strong className="text-sm">{option.label}</strong><p className="text-xs text-slate-500">{option.key}</p></div>
          <span className={`badge ${option.active ? "bg-emerald-100 text-emerald-700" : "bg-red-100 text-red-700"}`}>{option.active ? "Active" : "Inactive"}</span>
        </div>
        <label className="mt-2 block">Label<input className="field mt-1" defaultValue={option.label} onBlur={event => {
          const next = event.currentTarget.value.trim();
          if (next && next !== option.label) void updateOption(option, next);
        }} /></label>
        <div className="mt-2 flex flex-wrap gap-2">
          <button type="button" className="btn-secondary px-2 py-1 text-xs" disabled={index === 0} onClick={() => void reorder(option.id, -1)}>Move up</button>
          <button type="button" className="btn-secondary px-2 py-1 text-xs" disabled={index === options.length - 1} onClick={() => void reorder(option.id, 1)}>Move down</button>
          <button type="button" className="btn-secondary px-2 py-1 text-xs" onClick={() => void setActive(option, !option.active)}>{option.active ? "Deactivate" : "Activate"}</button>
          <button type="button" className="btn-secondary px-2 py-1 text-xs text-red-300" onClick={() => void removeOption(option)}>Delete</button>
        </div>
      </div>)}
    </div>
    <form className="mt-3 grid gap-2 sm:grid-cols-2" onSubmit={event => void createOption(event)}>
      <label>Key<input name="key" className="field mt-1" required placeholder="NEW" /></label>
      <label>Label<input name="label" className="field mt-1" required placeholder="New" /></label>
      <button className="btn-primary sm:col-span-2">Create option</button>
    </form>
  </div>;
}

function RoutingAdministration({ organizationId, subtype, report }: { organizationId: string; subtype: Subtype; reloadTypes: () => Promise<void>; report: (message: string) => void }) {
  const [teams, setTeams] = useState<Team[]>([]);
  const [people, setPeople] = useState<Person[]>([]);
  const [routing, setRouting] = useState<Routing | null>(null);
  const [missingRouting, setMissingRouting] = useState(false);
  const [teamId, setTeamId] = useState("");
  const [primaryDeveloperId, setPrimaryDeveloperId] = useState("");
  const [fallbackDeveloperId, setFallbackDeveloperId] = useState("");
  const [approverId, setApproverId] = useState("");
  const [active, setActive] = useState(true);
  const routingOrg = organizationId === "internal" ? "" : `?organizationId=${organizationId}`;
  const loadRouting = useCallback(async () => {
    try {
      const [teamRows, opts, route] = await Promise.all([
        get<Team[]>("/teams"),
        get<{ people: Person[] }>("/teams/options"),
        get<Routing>(`/admin/subtypes/${subtype.id}/routing${routingOrg}`).catch(error => {
          if (error instanceof Error && error.message.toLowerCase().includes("not found")) return null;
          throw error;
        }),
      ]);
      setTeams(teamRows);
      setPeople(opts.people);
      setRouting(route);
      setMissingRouting(!route);
      setTeamId(route?.teamId ? String(route.teamId) : teamRows[0]?.id ? String(teamRows[0].id) : "");
      setPrimaryDeveloperId(route?.primaryDeveloperId ? String(route.primaryDeveloperId) : "");
      setFallbackDeveloperId(route?.fallbackDeveloperId ? String(route.fallbackDeveloperId) : "");
      setApproverId(route?.approverId ? String(route.approverId) : "");
      setActive(route?.active ?? true);
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not load routing.");
    }
  }, [report, routingOrg, subtype.id]);
  useEffect(() => { void loadRouting(); }, [loadRouting]);
  const selectablePeople = useMemo(() => {
    if (organizationId === "internal") return people.filter(person => person.party === "TICKETFLOW1");
    return people;
  }, [organizationId, people]);
  async function save(event: FormEvent) {
    event.preventDefault();
    if (!teamId) { report("Routing needs a team."); return; }
    try {
      await put(`/admin/subtypes/${subtype.id}/routing`, {
        organizationId: organizationId === "internal" ? null : Number(organizationId),
        teamId: Number(teamId),
        primaryDeveloperId: idOrNull(primaryDeveloperId),
        fallbackDeveloperId: idOrNull(fallbackDeveloperId),
        approverId: idOrNull(approverId),
        active,
        version: routing?.version ?? null,
      });
      report("Routing rule saved.");
      await loadRouting();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not save routing.");
    }
  }
  async function deactivate() {
    if (!routing) return;
    try {
      await api(`/admin/subtypes/${subtype.id}/routing${routingOrg}`, { method: "DELETE" });
      report("Routing rule deactivated.");
      await loadRouting();
    } catch (error) {
      report(error instanceof Error ? error.message : "Could not deactivate routing.");
    }
  }
  return <section className="card">
    <div className="mb-4">
      <p className="eyebrow">Routing</p>
      <h2 className="mt-1 text-xl font-bold">Assignment rule</h2>
      <p className="mt-1 text-sm text-slate-500">Choose the default team, developer, fallback, and approver for this subtype.</p>
    </div>
    {missingRouting ? <p className="mb-3 rounded-lg border border-dashed p-3 text-sm text-slate-500">No routing rule exists yet for this subtype and scope.</p> : null}
    <form className="grid gap-3" onSubmit={event => void save(event)}>
      <label>Team<select className="field mt-1" required value={teamId} onChange={event => setTeamId(event.target.value)}>
        <option value="">Select a team</option>
        {teams.map(team => <option value={team.id} key={team.id}>{team.name}</option>)}
      </select></label>
      <label>Primary developer<select className="field mt-1" value={primaryDeveloperId} onChange={event => setPrimaryDeveloperId(event.target.value)}>
        <option value="">No specific developer</option>
        {selectablePeople.map(person => <option value={person.id} key={person.id}>{person.name} · {person.organizationName ?? "TicketFlow1"}</option>)}
      </select></label>
      <label>Fallback developer<select className="field mt-1" value={fallbackDeveloperId} onChange={event => setFallbackDeveloperId(event.target.value)}>
        <option value="">No fallback</option>
        {selectablePeople.map(person => <option value={person.id} key={person.id}>{person.name} · {person.organizationName ?? "TicketFlow1"}</option>)}
      </select></label>
      <label>Approver<select className="field mt-1" value={approverId} onChange={event => setApproverId(event.target.value)}>
        <option value="">No approver</option>
        {selectablePeople.map(person => <option value={person.id} key={person.id}>{person.name} · {person.organizationName ?? "TicketFlow1"}</option>)}
      </select></label>
      <label className="flex items-center gap-2"><input type="checkbox" checked={active} onChange={event => setActive(event.target.checked)} /> Active routing</label>
      <div className="flex flex-wrap gap-2">
        <button className="btn-primary">Save routing</button>
        {routing ? <button type="button" className="btn-secondary" onClick={() => void deactivate()}>Deactivate rule</button> : null}
      </div>
    </form>
  </section>;
}

function pretty(value: string) { return value.replaceAll("_", " "); }
function selectKind(value: FieldKind) { return value === "SINGLE_SELECT" || value === "MULTI_SELECT"; }
function textKind(value: FieldKind) { return value === "SHORT_TEXT" || value === "LONG_TEXT"; }
function numberKind(value: FieldKind) { return value === "INTEGER" || value === "DECIMAL"; }
function numberOrNull(value: FormDataEntryValue | null) {
  if (typeof value !== "string" || !value.trim()) return null;
  return Number(value);
}
function idOrNull(value: string) { return value ? Number(value) : null; }
