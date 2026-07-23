"use client";

export type DevLogLevel = "info" | "warn" | "error";
export type DevLogEntry = {
  id: string;
  at: string;
  level: DevLogLevel;
  source: string;
  message: string;
  details?: unknown;
};

const MAX_LOGS = 120;
const EVENT_NAME = "ticketflow1:dev-log";

export function devLogsEnabled() {
  return process.env.NODE_ENV === "development" || process.env.NEXT_PUBLIC_ENABLE_DEV_LOGS === "true";
}

export function recordDevLog(level: DevLogLevel, source: string, message: string, details?: unknown) {
  if (!devLogsEnabled() || typeof window === "undefined") return;
  const entry: DevLogEntry = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    at: new Date().toISOString(),
    level,
    source,
    message,
    details: sanitize(details),
  };
  window.dispatchEvent(new CustomEvent<DevLogEntry>(EVENT_NAME, { detail: entry }));
  const method = level === "error" ? "error" : level === "warn" ? "warn" : "info";
  console[method](`[TicketFlow1:${source}] ${message}`, entry.details ?? "");
}

export function subscribeDevLogs(listener: (entry: DevLogEntry) => void) {
  function handle(event: Event) {
    listener((event as CustomEvent<DevLogEntry>).detail);
  }
  window.addEventListener(EVENT_NAME, handle);
  return () => window.removeEventListener(EVENT_NAME, handle);
}

export function appendDevLog(entries: DevLogEntry[], entry: DevLogEntry) {
  return [entry, ...entries].slice(0, MAX_LOGS);
}

function sanitize(value: unknown): unknown {
  if (!value || typeof value !== "object") return value;
  if (value instanceof Error) return { name: value.name, message: value.message, stack: value.stack };
  if (value instanceof Headers) return Object.fromEntries(value.entries());
  try {
    return JSON.parse(JSON.stringify(value, (_key, item) => {
      if (typeof item === "string" && item.length > 500) return `${item.slice(0, 500)}…`;
      return item;
    }));
  } catch {
    return String(value);
  }
}
