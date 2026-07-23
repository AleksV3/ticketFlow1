"use client";

import { useEffect } from "react";
import { getApiBaseUrl } from "@/lib/auth";
import { recordDevLog } from "@/lib/devLogs";

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
          else if (response.status === 401 || response.status === 403) {
            recordDevLog("warn", "realtime", "Realtime listener stopped because authentication failed", { status: response.status });
            return;
          } else {
            recordDevLog("warn", "realtime", "Realtime listener returned non-OK status", { status: response.status });
          }
        } catch (error) {
          if (controller.signal.aborted) return;
          recordDevLog("warn", "realtime", "Realtime listener reconnecting after error", error);
          await new Promise(resolve => setTimeout(resolve, 1500));
        }
      }
    }
    void listen();
    return () => controller.abort();
  }, [refresh]);
}
