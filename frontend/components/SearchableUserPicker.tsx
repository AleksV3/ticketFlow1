"use client";

import { useMemo, useState } from "react";

export type UserPickerOption = {
  id: number;
  name: string;
  email?: string | null;
  party?: string | null;
  organizationName?: string | null;
  roleNames?: string[];
};

/** Shared keyboard-friendly search and card selection control for user assignment. */
export function SearchableUserPicker({ users, selectedIds, onToggle, label = "Users", empty = "No users match this search." }: {
  users: UserPickerOption[];
  selectedIds: number[];
  onToggle: (id: number) => void;
  label?: string;
  empty?: string;
}) {
  const [query, setQuery] = useState("");
  const filtered = useMemo(() => {
    const term = query.trim().toLowerCase();
    if (!term) return users;
    return users.filter(user => [user.name, user.email, user.organizationName, ...(user.roleNames ?? [])].filter(Boolean).join(" ").toLowerCase().includes(term));
  }, [query, users]);
  return <fieldset>
    <legend className="mb-2 font-bold">{label}</legend>
    <label className="block"><span className="sr-only">Search {label.toLowerCase()}</span><input className="field" type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder={`Search ${label.toLowerCase()}…`} aria-label={`Search ${label.toLowerCase()}`} /></label>
    <div className="mt-2 grid gap-2 sm:grid-cols-2" role="listbox" aria-label={label} aria-multiselectable="true">
      {filtered.map(user => { const selected = selectedIds.includes(user.id); return <button type="button" role="option" aria-selected={selected} className={`permission-option w-full text-left ${selected ? "permission-option-selected" : ""}`} key={user.id} onClick={() => onToggle(user.id)}>
        <span className="min-w-0"><strong className="block truncate">{user.name}</strong><small className="block truncate">{user.email ?? (user.party === "CLIENT" ? user.organizationName : "TicketFlow1")}</small>{user.roleNames?.length ? <small className="block truncate text-slate-400">{user.roleNames.join(", ")}</small> : null}</span><span aria-hidden="true" className="ml-auto text-xs">{selected ? "Selected" : "Select"}</span>
      </button>; })}
      {!filtered.length ? <p className="rounded-lg border border-dashed p-4 text-sm text-slate-500 sm:col-span-2">{empty}</p> : null}
    </div>
  </fieldset>;
}
