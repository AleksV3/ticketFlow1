import { afterEach, describe, expect, it, vi } from "vitest";
import { post } from "@/lib/api";

describe("API client", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("fetches a CSRF token, then sends cookies, JSON, and the CSRF header on mutations", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "safe-token" }), {
        status: 200, headers: { "Content-Type": "application/json" }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), {
        status: 200, headers: { "Content-Type": "application/json" }
      }));
    vi.stubGlobal("fetch", fetchMock);
    await post("/tickets", { title: "Example" });
    expect(fetchMock.mock.calls[0][0]).toContain("/auth/csrf");
    const [, init] = fetchMock.mock.calls[1];
    expect(init.credentials).toBe("include");
    expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("safe-token");
    expect(new Headers(init.headers).get("Content-Type")).toBe("application/json");
  });

  it("refreshes an expired CSRF token and retries a mutation exactly once", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "stale-token" }), {
        status: 200, headers: { "Content-Type": "application/json" }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 403, error: "CSRF_INVALID", message: "Expired" }), {
        status: 403, headers: { "Content-Type": "application/json" }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "fresh-token" }), {
        status: 200, headers: { "Content-Type": "application/json" }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ok: true }), {
        status: 200, headers: { "Content-Type": "application/json" }
      }));
    vi.stubGlobal("fetch", fetchMock);

    await post("/tickets/TF-1/comments", { body: "Update", visibility: "PUBLIC" });

    expect(fetchMock).toHaveBeenCalledTimes(4);
    expect(fetchMock.mock.calls[2][0]).toContain("/auth/csrf");
    expect(new Headers(fetchMock.mock.calls[3][1].headers).get("X-XSRF-TOKEN")).toBe("fresh-token");
  });

  it("does not retry a genuine permission denial", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "safe-token" }), {
        status: 200, headers: { "Content-Type": "application/json" }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        status: 403, error: "FORBIDDEN", message: "You do not have permission to perform this action."
      }), { status: 403, headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(post("/tickets/TF-1/comments", { body: "Update", visibility: "INTERNAL" }))
      .rejects.toMatchObject({ status: 403, code: "FORBIDDEN" });
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("lets the browser set the multipart boundary for FormData", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "safe-token" }), {
        status: 200, headers: { "Content-Type": "application/json" }
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 1 }), {
        status: 200, headers: { "Content-Type": "application/json" }
      }));
    vi.stubGlobal("fetch", fetchMock);
    const data = new FormData();
    data.append("file", new Blob(["hello"]), "hello.txt");
    const { api } = await import("@/lib/api");
    await api("/tickets/TF-1/attachments/upload", { method: "POST", body: data });
    const [, init] = fetchMock.mock.calls[1];
    expect(new Headers(init.headers).has("Content-Type")).toBe(false);
    expect(init.body).toBe(data);
  });
});
