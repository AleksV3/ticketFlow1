export const AUTH_STORAGE_KEY = "ticketflow1.auth";

export type LoginRequest = {
  email: string;
  password: string;
};

export type LoginResponse = {
  token: string;
  expiresAt: string;
  user: {
    id: number;
    email: string;
    displayName: string;
    roleName: string;
    party: "CLIENT" | "TICKETFLOW1";
    organizationId: number | null;
  };
};

export type CurrentUser = {
  id: number;
  email: string;
  displayName: string;
  roleName: string;
  party: "CLIENT" | "TICKETFLOW1";
  organizationId: number | null;
  organizationName: string | null;
  permissions: string[];
};

export type StoredSession = {
  token: string;
  expiresAt: string;
  user: CurrentUser;
};

export function getApiBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081/api";
}

export function storeSession(session: StoredSession): void {
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
}

export function readSession(): StoredSession | null {
  const raw = window.localStorage.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as StoredSession;
  } catch {
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

export function clearSession(): void {
  window.localStorage.removeItem(AUTH_STORAGE_KEY);
}
