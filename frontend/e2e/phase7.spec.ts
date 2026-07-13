import { expect, test, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const user = { id: 7, email: "manager@example.test", displayName: "Demo Manager", roleName: "Client Approver", party: "CLIENT", organizationId: 11, organizationName: "Acme", permissions: ["TICKET_READ", "TICKET_CREATE", "TICKET_UPDATE", "TICKET_TRANSITION", "COMMENT_PUBLIC_WRITE", "PROPOSAL_APPROVE"] };
const now = () => new Date().toISOString();
function ticket(status = "SUBMITTED", latestProposal: object | null = null, proposalCommands = ["CREATE"]) {
  return { id: 1, ticketKey: "TF-101", type: "CHANGE_REQUEST", status, priority: "MEDIUM", severity: null, title: "Add SSO", description: "Support company login", organization: { id: 11, name: "Acme" }, organizationName: "Acme", businessOwner: { id: 7, displayName: "Demo Manager" }, ticketLead: null, assignedTeam: null, currentResponsibility: "CLIENT", createdAt: now(), updatedAt: now(), closedAt: null, slaStatus: "NOT_APPLICABLE", allowedTransitions: status === "SUBMITTED" ? ["ANALYSIS"] : [], latestProposal, proposalCommands };
}

async function mockApi(page: Page) {
  let current = ticket(); let comments: object[] = []; let authenticated = false;
  await page.route("**/api/**", async route => {
    const path = new URL(route.request().url()).pathname.replace("/api", ""); let body: unknown = {};
    if (path === "/users/me" && !authenticated) return route.fulfill({ status: 200, contentType: "application/json", body: "null" });
    if (path === "/users/me") body = user;
    else if (path === "/auth/login") { authenticated = true; body = { expiresAt: now(), user }; }
    else if (path === "/reference/ticket-types") body = [{ id: 1, key: "CHANGE_REQUEST", name: "Change request" }];
    else if (path === "/tickets" && route.request().method() === "POST") body = current;
    else if (path === "/tickets/TF-101" && route.request().method() === "GET") body = current;
    else if (path.endsWith("/transition")) { current = ticket("ANALYSIS"); body = current; }
    else if (path.endsWith("/comments") && route.request().method() === "POST") { comments = [{ id: 1, author: { id: 7, displayName: "Demo Manager" }, body: "Ready for review", visibility: "PUBLIC", createdAt: now() }]; body = comments[0]; }
    else if (path.endsWith("/comments")) body = comments;
    else if (path.endsWith("/attachments") || path.endsWith("/audit-log") || path.endsWith("/status-history")) body = [];
    else if (path.endsWith("/proposals")) { const proposal = { id: 9, description: "Deliver SSO", estimatedDeliveryDate: "2026-08-01", effortEstimate: "3 days", status: "PENDING", version: 0 }; current = ticket("ANALYSIS", proposal, ["APPROVE", "REJECT"]); body = proposal; }
    else if (path.endsWith("/approve")) { current = ticket("PROPOSAL_APPROVED", { id: 9, description: "Deliver SSO", estimatedDeliveryDate: "2026-08-01", effortEstimate: "3 days", status: "APPROVED", version: 1 }, []); body = current.latestProposal; }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
}

test("login, create, transition, comment, and approve a proposal", async ({ page }) => {
  await mockApi(page); await page.goto("/login");
  await page.getByLabel("Email").fill("manager@example.test"); await page.getByLabel("Password").fill("secret1"); await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/dashboard/); await page.goto("/tickets/new");
  await page.getByLabel("Title").fill("Add SSO"); await page.getByLabel("Description").fill("Support company login"); await page.getByRole("button", { name: "Create ticket" }).click();
  await expect(page).toHaveURL(/TF-101/); await page.getByRole("button", { name: "ANALYSIS" }).click();
  await page.getByLabel("Message").fill("Ready for review"); await page.getByRole("button", { name: "Add comment" }).click(); await expect(page.getByText("Ready for review")).toBeVisible();
  await page.getByLabel("Proposal").fill("Deliver SSO"); await page.getByLabel("Estimated delivery").fill("2026-08-01"); await page.getByLabel("Effort estimate").fill("3 days"); await page.getByRole("button", { name: "Create proposal" }).click();
  await page.getByRole("button", { name: "Approve" }).click(); await expect(page.getByText("PROPOSAL APPROVED")).toBeVisible();
});

test("responsive, keyboard reachable, accessible login without console errors", async ({ page }) => {
  const errors: string[] = []; page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  await mockApi(page); await page.setViewportSize({ width: 375, height: 667 }); await page.goto("/login"); await page.keyboard.press("Tab");
  await expect(page.getByLabel("Email")).toBeFocused(); const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]); expect(errors).toEqual([]);
});
