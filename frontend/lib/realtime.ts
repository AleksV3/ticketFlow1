"use client";

import { useEffect } from "react";
import { getApiBaseUrl } from "@/lib/auth";

export function useTicketEvents(refresh: () => void) {
  useEffect(() => {
    const controller = new AbortController();
    async function listen() {
      while (!controller.signal.aborted) {
        try {
          const response = await fetch(`${getApiBaseUrl()}/events`, {
            credentials: "include", cache: "no-store", signal: controller.signal
          });
          if (response.status === 200) refresh();
          else if (response.status === 401 || response.status === 403) return;
        } catch (error) {
          if (controller.signal.aborted) return;
          await new Promise(resolve => setTimeout(resolve, 1500));
        }
      }
    }
    void listen();
    return () => controller.abort();
  }, [refresh]);
}
