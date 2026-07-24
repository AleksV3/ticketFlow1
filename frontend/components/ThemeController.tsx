"use client";

import { useEffect, useState } from "react";
import { get, put } from "@/lib/api";

type Preferences = { dashboardWidgets: string[]; enabledTicketFilters: string[]; lastViewedTeamId: number | null; theme: string; version: number };

function systemTheme(): "light" | "dark" {
  return typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: light)").matches ? "light" : "dark";
}

function applyTheme(preference: string) {
  const resolved = preference === "LIGHT" ? "light" : preference === "DARK" ? "dark" : systemTheme();
  document.documentElement.dataset.theme = resolved;
  document.documentElement.style.colorScheme = resolved;
}

export function ThemeController() {
  const [preference, setPreference] = useState<Preferences | null>(null);
  const [resolved, setResolved] = useState<"light" | "dark">("dark");
  useEffect(() => {
    const stored = window.localStorage.getItem("ticketflow1-theme");
    if (stored) { applyTheme(stored); setResolved(stored === "LIGHT" ? "light" : stored === "DARK" ? "dark" : systemTheme()); }
    get<Preferences>("/preferences").then(value => {
      setPreference(value);
      // A local choice is applied immediately and must not be overwritten by
      // a slower response from the previous page's preference request.
      const selected = window.localStorage.getItem("ticketflow1-theme") ?? value.theme;
      applyTheme(selected); setResolved(selected === "LIGHT" ? "light" : selected === "DARK" ? "dark" : systemTheme());
      window.localStorage.setItem("ticketflow1-theme", selected);
    }).catch(() => applyTheme(stored ?? "SYSTEM"));
  }, []);
  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: light)");
    const listener = () => { if (!preference || preference.theme === "SYSTEM") { applyTheme("SYSTEM"); setResolved(systemTheme()); } };
    media.addEventListener("change", listener); return () => media.removeEventListener("change", listener);
  }, [preference]);
  async function toggle() {
    const next = resolved === "dark" ? "LIGHT" : "DARK";
    applyTheme(next); setResolved(next === "LIGHT" ? "light" : "dark"); window.localStorage.setItem("ticketflow1-theme", next);
    try {
      const latest = await get<Preferences>("/preferences");
      setPreference(await put<Preferences>("/preferences", { ...latest, theme: next, version: latest.version }));
    } catch { /* local theme remains usable and will retry on the next toggle */ }
  }
  return <button type="button" className="btn-secondary theme-toggle" aria-label={`Switch to ${resolved === "dark" ? "light" : "dark"} theme`} onClick={() => void toggle()}>{resolved === "dark" ? "☀ Light" : "◐ Dark"}</button>;
}
