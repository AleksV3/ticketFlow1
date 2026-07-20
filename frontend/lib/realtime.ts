"use client";

import { useEffect } from "react";
import { getApiBaseUrl } from "@/lib/auth";

export function useTicketEvents(refresh: () => void) {
  useEffect(() => {
    const stream = new EventSource(`${getApiBaseUrl()}/events`, { withCredentials: true });
    stream.addEventListener("tickets-changed", refresh);
    return () => stream.close();
  }, [refresh]);
}
