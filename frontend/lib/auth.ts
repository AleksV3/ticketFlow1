export type LoginRequest = {
  email: string;
  password: string;
};

export type LoginResponse = {
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

export function getApiBaseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "https://ticketflow1-cm62j5alfq-og.a.run.app/api";
}

export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  const response = await fetch(`${getApiBaseUrl()}/users/me`, {
    credentials: "include"
  });

  if (response.status === 401) {
    return null;
  }

  if (!response.ok) {
    throw new Error("Could not load the current user.");
  }

  return (await response.json()) as CurrentUser;
}

export async function logout(): Promise<void> {
  await fetch(`${getApiBaseUrl()}/auth/logout`, {
    method: "POST",
    credentials: "include"
  });
}
