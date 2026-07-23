import { recordDevLog } from "@/lib/devLogs";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081/api";

export type FieldError = { field: string; message: string };
export type ApiErrorBody = {
  status: number;
  error: string;
  message: string;
  path?: string;
  timestamp?: string;
  fieldErrors?: FieldError[];
};

export class ApiError extends Error {
  constructor(public readonly status: number, public readonly code: string,
    message: string, public readonly fieldErrors: FieldError[] = []) {
    super(message);
    this.name = "ApiError";
  }
}

async function getCsrfToken(): Promise<string | undefined> {
  const response = await fetch(`${API_BASE}/auth/csrf`, {
    credentials: "include",
    cache: "no-store"
  });
  if (!response.ok) return undefined;
  const body = await response.json() as { token?: string };
  return body.token;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const mutating = !["GET", "HEAD", "OPTIONS"].includes(method);
  const started = typeof performance === "undefined" ? Date.now() : performance.now();

  async function send(retriedAfterCsrfFailure: boolean): Promise<T> {
    const headers = new Headers(init.headers);
    if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
    if (mutating) {
      const token = await getCsrfToken();
      if (token) headers.set("X-XSRF-TOKEN", decodeURIComponent(token));
    }
    let response: Response;
    try {
      response = await fetch(`${API_BASE}${path}`, {
        ...init,
        method,
        headers,
        credentials: "include",
        cache: "no-store"
      });
    } catch (error) {
      recordDevLog("error", "api", `${method} ${path} network failure`, {
        apiBase: API_BASE,
        durationMs: elapsed(started),
        error,
      });
      throw error;
    }
    const requestId = response.headers.get("X-Request-Id");
    if (!response.ok) {
      let body: Partial<ApiErrorBody> = {};
      try { body = await response.json() as ApiErrorBody; } catch { /* non-JSON proxy error */ }
      // A CSRF denial happens in Spring Security before the controller runs,
      // so retrying once cannot duplicate a completed business operation.
      if (mutating && !retriedAfterCsrfFailure && response.status === 403 && body.error === "CSRF_INVALID") {
        recordDevLog("warn", "api", `${method} ${path} CSRF retry`, { status: response.status, requestId, durationMs: elapsed(started) });
        return send(true);
      }
      recordDevLog(response.status >= 500 ? "error" : "warn", "api", `${method} ${path} failed`, {
        status: response.status,
        code: body.error ?? "HTTP_ERROR",
        message: body.message,
        path: body.path,
        fieldErrors: body.fieldErrors,
        requestId,
        durationMs: elapsed(started),
      });
      throw new ApiError(response.status, body.error ?? "HTTP_ERROR",
        body.message ?? `Request failed with status ${response.status}.`, body.fieldErrors ?? []);
    }
    const durationMs = elapsed(started);
    if (durationMs >= 1000) recordDevLog("warn", "api", `${method} ${path} slow request`, { status: response.status, requestId, durationMs });
    if (response.status === 204) return undefined as T;
    return await response.json() as T;
  }

  return send(false);
}

function elapsed(started: number) {
  const now = typeof performance === "undefined" ? Date.now() : performance.now();
  return Math.round(now - started);
}

export const get = <T>(path: string) => api<T>(path);
export const post = <T>(path: string, body?: unknown) => api<T>(path,
  { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });
export const patch = <T>(path: string, body: unknown) => api<T>(path,
  { method: "PATCH", body: JSON.stringify(body) });
export const put = <T>(path: string, body: unknown) => api<T>(path,
  { method: "PUT", body: JSON.stringify(body) });
