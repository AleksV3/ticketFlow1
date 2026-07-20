import { afterEach, describe, expect, it, vi } from "vitest";
import { post } from "@/lib/api";

describe("API client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("sends cookies, JSON, and the CSRF cookie on mutations", async () => {
    Object.defineProperty(document, "cookie", { configurable: true, value: "XSRF-TOKEN=safe-token" });
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ok: true }), {
      status: 200, headers: { "Content-Type": "application/json" }
    }));
    vi.stubGlobal("fetch", fetchMock);
    await post("/tickets", { title: "Example" });
    const [, init] = fetchMock.mock.calls[0];
    expect(init.credentials).toBe("include");
    expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("safe-token");
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
  });

  it("refreshes an expired CSRF token and retries a mutation exactly once", async () => {
    Object.defineProperty(document, "cookie", { configurable: true, writable: true, value: "XSRF-TOKEN=stale-token" });
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 403, error: "CSRF_INVALID", message: "Expired" }), {
        status: 403, headers: { "Content-Type": "application/json" }
      }))
      .mockImplementationOnce(async () => {
        document.cookie = "XSRF-TOKEN=fresh-token";
        return new Response(JSON.stringify({ id: 1 }), { status: 200, headers: { "Content-Type": "application/json" } });
      })
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), {
        status: 200, headers: { "Content-Type": "application/json" }
      }));
    vi.stubGlobal("fetch", fetchMock);

    await post("/tickets/TF-1/comments", { body: "Update", visibility: "PUBLIC" });

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toContain("/users/me");
    expect(new Headers(fetchMock.mock.calls[2][1].headers).get("X-XSRF-TOKEN")).toBe("fresh-token");
  });

  it("does not retry a genuine permission denial", async () => {
    Object.defineProperty(document, "cookie", { configurable: true, value: "XSRF-TOKEN=safe-token" });
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      status: 403, error: "FORBIDDEN", message: "You do not have permission to perform this action."
    }), { status: 403, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(post("/tickets/TF-1/comments", { body: "Update", visibility: "INTERNAL" }))
      .rejects.toMatchObject({ status: 403, code: "FORBIDDEN" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("lets the browser set the multipart boundary for FormData", async () => {
    Object.defineProperty(document, "cookie", { configurable: true, value: "XSRF-TOKEN=safe-token" });
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 1 }), {
      status: 200, headers: { "Content-Type": "application/json" }
    }));
    vi.stubGlobal("fetch", fetchMock);
    const data = new FormData();
    data.append("file", new Blob(["hello"]), "hello.txt");
    const { api } = await import("@/lib/api");
    await api("/tickets/TF-1/attachments/upload", { method: "POST", body: data });
    const [, init] = fetchMock.mock.calls[0];
    expect(new Headers(init.headers).has("Content-Type")).toBe(false);
    expect(init.body).toBe(data);
  });
});
