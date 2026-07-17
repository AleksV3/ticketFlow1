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

function cookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  return document.cookie.split(";").map(value => value.trim())
    .find(value => value.startsWith(`${name}=`))?.slice(name.length + 1);
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = cookie("XSRF-TOKEN");
    if (token) headers.set("X-XSRF-TOKEN", decodeURIComponent(token));
  }
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    method,
    headers,
    credentials: "include",
    cache: "no-store"
  });
  if (!response.ok) {
    let body: Partial<ApiErrorBody> = {};
    try { body = await response.json() as ApiErrorBody; } catch { /* non-JSON proxy error */ }
    throw new ApiError(response.status, body.error ?? "HTTP_ERROR",
      body.message ?? `Request failed with status ${response.status}.`, body.fieldErrors ?? []);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

export const get = <T>(path: string) => api<T>(path);
export const post = <T>(path: string, body?: unknown) => api<T>(path,
  { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });
export const patch = <T>(path: string, body: unknown) => api<T>(path,
  { method: "PATCH", body: JSON.stringify(body) });
export const put = <T>(path: string, body: unknown) => api<T>(path,
  { method: "PUT", body: JSON.stringify(body) });
