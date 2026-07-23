"use client";

import { useEffect, useMemo, useState } from "react";
import { appendDevLog, devLogsEnabled, recordDevLog, subscribeDevLogs, type DevLogEntry } from "@/lib/devLogs";

export function DevLogPanel() {
  const [enabled, setEnabled] = useState(false);
  const [open, setOpen] = useState(false);
  const [logs, setLogs] = useState<DevLogEntry[]>([]);

  useEffect(() => {
    setEnabled(devLogsEnabled());
    if (!devLogsEnabled()) return;
    recordDevLog("info", "frontend", "Development log panel enabled", {
      apiBase: process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081/api",
      nodeEnv: process.env.NODE_ENV,
    });
    const unsubscribe = subscribeDevLogs(entry => setLogs(current => appendDevLog(current, entry)));
    const onError = (event: ErrorEvent) => recordDevLog("error", "runtime", event.message, {
      filename: event.filename, lineno: event.lineno, colno: event.colno, error: event.error,
    });
    const onRejection = (event: PromiseRejectionEvent) => recordDevLog("error", "promise", "Unhandled promise rejection", event.reason);
    window.addEventListener("error", onError);
    window.addEventListener("unhandledrejection", onRejection);
    return () => {
      unsubscribe();
      window.removeEventListener("error", onError);
      window.removeEventListener("unhandledrejection", onRejection);
    };
  }, []);

  const errorCount = useMemo(() => logs.filter(log => log.level === "error").length, [logs]);
  if (!enabled) return null;

  return <div className={`dev-log-panel ${open ? "dev-log-panel-open" : ""}`}>
    <button type="button" className="dev-log-toggle" onClick={() => setOpen(value => !value)}>
      Dev logs {errorCount ? <span>{errorCount}</span> : null}
    </button>
    {open ? <section className="dev-log-drawer" aria-label="Development logs">
      <header>
        <div><p className="eyebrow">Development logs</p><h2>Frontend diagnostics</h2></div>
        <div className="flex gap-2">
          <button type="button" className="btn-secondary" onClick={() => void navigator.clipboard?.writeText(JSON.stringify(logs, null, 2))}>Copy</button>
          <button type="button" className="btn-secondary" onClick={() => setLogs([])}>Clear</button>
        </div>
      </header>
      <div className="dev-log-list">
        {logs.length ? logs.map(log => <article className={`dev-log-entry dev-log-${log.level}`} key={log.id}>
          <div className="dev-log-line"><strong>{log.level.toUpperCase()}</strong><span>{log.source}</span><time>{new Date(log.at).toLocaleTimeString()}</time></div>
          <p>{log.message}</p>
          {log.details !== undefined ? <pre>{JSON.stringify(log.details, null, 2)}</pre> : null}
        </article>) : <p className="text-sm text-slate-500">No logs yet.</p>}
      </div>
    </section> : null}
  </div>;
}
