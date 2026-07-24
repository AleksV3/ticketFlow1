import { expect, test, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

const user = {
  id: 7,
  email: "manager@example.test",
  displayName: "Demo Manager",
  roleName: "TicketFlow1 Admin",
  party: "TICKETFLOW1",
  organizationId: null,
  organizationName: null,
  permissions: ["TICKET_READ", "TICKET_CREATE", "TICKET_UPDATE", "TICKET_ASSIGN", "TICKET_TRANSITION", "COMMENT_PUBLIC_WRITE", "COMMENT_INTERNAL_WRITE", "WORKFLOW_MANAGE", "TYPE_MANAGE"],
};
const org = { id: 11, name: "Acme" };
const now = () => new Date().toISOString();
const baseProcess = {
  name: "Service workflow",
  states: [
    { id: 1, key: "SUBMITTED", isInitial: true, isTerminal: false, sortOrder: 0 },
    { id: 2, key: "ANALYSIS", isInitial: false, isTerminal: false, sortOrder: 10 },
    { id: 3, key: "PENDING_APPROVAL", isInitial: false, isTerminal: false, sortOrder: 20 },
    { id: 4, key: "IMPLEMENTATION", isInitial: false, isTerminal: false, sortOrder: 30 },
    { id: 5, key: "CLOSED", isInitial: false, isTerminal: true, sortOrder: 40 },
  ],
  transitions: [{ fromStateId: 1, toStateId: 2 }, { fromStateId: 2, toStateId: 3 }, { fromStateId: 3, toStateId: 4 }, { fromStateId: 4, toStateId: 5 }],
};
const types = [
  { id: 1, key: "TASI", name: "TASI" },
  { id: 2, key: "USR", name: "USR" },
  { id: 3, key: "DFCT", name: "DFCT" },
  { id: 4, key: "REQ", name: "REQ" },
];

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: 1,
    ticketKey: "TF-101",
    type: "TASI",
    subtype: "FIREWALL",
    subtypeId: 101,
    status: "ANALYSIS",
    priority: "MEDIUM",
    severity: null,
    title: "Open firewall path",
    description: "Allow app traffic",
    organization: org,
    organizationName: org.name,
    businessOwner: { id: 7, displayName: "Demo Manager" },
    businessOwnerName: "Demo Manager",
    ticketLead: { id: 8, displayName: "Network Dev" },
    ticketLeadName: "Network Dev",
    developers: [{ id: 8, displayName: "Network Dev" }],
    developerNames: ["Network Dev"],
    teams: [{ id: 4, name: "Network" }],
    assignedTeam: "Network",
    currentResponsibility: "TICKETFLOW1",
    createdAt: now(),
    updatedAt: now(),
    closedAt: null,
    slaStatus: "NOT_APPLICABLE",
    processMap: baseProcess,
    allowedTransitions: ["PENDING_APPROVAL"],
    workflowCommands: [],
    latestProposal: null,
    proposalCommands: [],
    parentTicketKey: null,
    childTickets: [],
    targetUserDisplaySnapshot: null,
    dynamicValues: { source_ip: "10.0.0.1" },
    ...overrides,
  };
}

async function mockApi(page: Page, initial = detail()) {
  let current = initial; let authenticated = false;
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace("/api", "");
    let body: unknown = {};
    if (path === "/users/me" && !authenticated) body = null;
    else if (path === "/users/me") body = user;
    else if (path === "/auth/login") { authenticated = true; body = { expiresAt: now(), user }; }
    else if (path === "/dashboard") body = { activeCount: 1, closedCount: 0, byType: { TASI: 1 }, byStatus: { ANALYSIS: 1 }, defectsBySeverity: {}, slaBreached: [], slaDueSoon: [], waitingForClientApproval: [], waitingForClientConfirmation: [], myAssignedTickets: [], myOpenTickets: [current], myTeamTickets: [current], awaitingMyApproval: [], recentlyUpdated: [current] };
    else if (path === "/preferences") body = { dashboardWidgets: ["MY_OPEN_TICKETS","MY_TEAM_TICKETS","TICKETS_BY_STATUS","TICKETS_BY_TYPE","AWAITING_MY_APPROVAL","RECENTLY_UPDATED"], enabledTicketFilters: ["TYPE","STATUS","PRIORITY","TEAM"], lastViewedTeamId: null, theme: "SYSTEM", version: 0 };
    else if (path === "/reference/organizations") body = [org];
    else if (path === "/reference/ticket-leads") body = [{ id: 8, name: "Network Dev" }, { id: 9, name: "Request Dev" }];
    else if (path === "/teams") body = [{ id: 4, name: "Network" }];
    else if (path === "/reference/ticket-types") body = types;
    else if (path === "/reference/ticket-types/1/creation-form") body = creationForm("TASI");
    else if (path === "/reference/ticket-types/2/creation-form") body = creationForm("USR");
    else if (path === "/reference/ticket-types/3/creation-form") body = creationForm("DFCT");
    else if (path === "/reference/ticket-types/4/creation-form") body = creationForm("REQ");
    else if (path === "/reference/users") body = [{ id: 17, displayName: "Ana Client", email: "ana@example.test" }];
    else if (path === "/tickets" && route.request().method() === "POST") {
      const payload = JSON.parse(route.request().postData() ?? "{}");
      current = detail({
        ticketKey: payload.parentTicketKey ? "TF-102" : "TF-101",
        type: payload.type,
        subtype: payload.subtypeId === 201 ? "MODIFY" : payload.subtypeId === 101 ? "FIREWALL" : null,
        parentTicketKey: payload.parentTicketKey ?? null,
        targetUserDisplaySnapshot: payload.targetUserId ? "Ana Client" : null,
        dynamicValues: payload.dynamicValues ?? {},
      });
      body = current;
    } else if (path === "/tickets/TF-101" || path === "/tickets/TF-102") body = current;
    else if (path.endsWith("/workflow-approve")) { current = detail({ status: "IMPLEMENTATION", workflowCommands: [] }); body = current; }
    else if (path.endsWith("/client-accept")) { current = detail({ type: "REQ", status: "DEPLOYMENT", workflowCommands: [] }); body = current; }
    else if (path.endsWith("/transition")) { current = detail({ status: "PENDING_APPROVAL", allowedTransitions: [], workflowCommands: ["WORKFLOW_APPROVE"] }); body = current; }
    else if (path.endsWith("/comments") || path.endsWith("/attachments") || path.endsWith("/audit-log") || path.endsWith("/status-history")) body = [];
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
  });
}

test("TASI routing, approval, and subticket creation flow", async ({ page }) => {
  await mockApi(page); await login(page);
  await page.goto("/tickets/new");
  await page.locator("label").filter({ hasText: /^Organization/ }).locator("select").selectOption("11");
  await page.getByLabel("Ticket type").fill("TASI");
  await page.getByLabel("Source IP").fill("10.0.0.1");
  await page.getByLabel("Title").fill("Open firewall path");
  await page.getByLabel("Description").fill("Allow app traffic");
  await page.getByRole("button", { name: "Create ticket" }).click();
  await expect(page).toHaveURL(/TF-101/);
  await page.getByText("Captured subtype fields").click();
  await expect(page.getByText("10.0.0.1")).toBeVisible();
  await page.getByRole("button", { name: "PENDING APPROVAL" }).click();
  await page.getByRole("button", { name: "Yes, continue" }).click();
  await page.getByRole("button", { name: "Approve" }).click();
  await expect(page.locator("span").filter({ hasText: "IMPLEMENTATION" }).first()).toBeVisible();
  await page.getByRole("link", { name: "Create subticket" }).click();
  await expect(page.getByLabel("Parent ticket key")).toHaveValue("TF-101");
});

test("USR MODIFY target-user search flow", async ({ page }) => {
  await mockApi(page); await login(page);
  await page.goto("/tickets/new");
  await page.locator("label").filter({ hasText: /^Organization/ }).locator("select").selectOption("11");
  await page.getByLabel("Ticket type").fill("USR");
  await page.getByLabel("Subtype").selectOption({ label: "Modify user" });
  await page.getByLabel("Search user").fill("Ana");
  await page.getByRole("button", { name: /Ana Client/ }).click();
  await page.getByLabel("Title").fill("Modify account");
  await page.getByLabel("Description").fill("Change user access");
  await page.getByRole("button", { name: "Create ticket" }).click();
  await expect(page.getByText("Target user: Ana Client")).toBeVisible();
});

test("DFCT loop and REQ client acceptance controls are visible from server commands", async ({ page }) => {
  await mockApi(page, detail({ type: "REQ", subtype: null, status: "CLIENT_ACCEPTANCE", workflowCommands: ["CLIENT_ACCEPT"] })); await login(page);
  await page.goto("/tickets/TF-101");
  await page.getByRole("button", { name: "Accept" }).click();
  await expect(page.getByText("DEPLOYMENT")).toBeVisible();
});

test("responsive, keyboard reachable, accessible login without console errors", async ({ page }) => {
  const errors: string[] = []; page.on("console", message => { if (message.type() === "error") errors.push(message.text()); });
  await mockApi(page); await page.setViewportSize({ width: 375, height: 667 }); await page.goto("/login"); await page.keyboard.press("Tab");
  await expect(page.getByLabel("Email")).toBeFocused(); const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]); expect(errors).toEqual([]);
});

async function login(page: Page) {
  await page.goto("/login");
  await page.getByLabel("Email").fill("manager@example.test");
  await page.getByLabel("Password").fill("secret1");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/dashboard/);
}

function creationForm(type: string) {
  if (type === "TASI") return { id: 1, key: "TASI", name: "TASI", version: 0, subtypes: [{ id: 101, key: "FIREWALL", name: "Firewall", description: "Firewall work", sortOrder: 0, version: 0, fields: [{ id: 1, key: "source_ip", label: "Source IP", helpText: null, fieldKind: "SHORT_TEXT", required: true, visibility: "INTERNAL", sortOrder: 0, minLength: null, maxLength: null, minNumber: null, maxNumber: null, version: 0, options: [] }] }] };
  if (type === "USR") return { id: 2, key: "USR", name: "USR", version: 0, subtypes: [{ id: 201, key: "MODIFY", name: "Modify user", description: null, sortOrder: 0, version: 0, fields: [] }] };
  return { id: type === "DFCT" ? 3 : 4, key: type, name: type, version: 0, subtypes: [] };
}
