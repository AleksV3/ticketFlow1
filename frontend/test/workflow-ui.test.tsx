import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { TicketContext, WorkflowDecisionPanel } from "@/app/tickets/[ticketKey]/page";
import { compactValues } from "@/app/tickets/new/page";
import type { TicketDetail } from "@/lib/types";

vi.mock("next/navigation", () => ({ useParams: () => ({ ticketKey: "TF-1" }), useRouter: () => ({ push: vi.fn() }) }));
vi.mock("@/lib/realtime", () => ({ useTicketEvents: vi.fn() }));

describe("service workflow UI", () => {
  it("strips stale dynamic values when the selected subtype changes", () => {
    expect(compactValues({ source_ip: "10.0.0.1", old_field: "stale", empty: "" }, [
      { key: "source_ip" },
      { key: "empty" },
    ])).toEqual({ source_ip: "10.0.0.1" });
  });

  it("renders only workflow decisions provided by the server", () => {
    render(<WorkflowDecisionPanel ticketKey="TF-1" commands={["WORKFLOW_APPROVE", "CORRECTION_RETURN"]} onDone={vi.fn()}/>);
    expect(screen.getByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Return for correction" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Reject" })).not.toBeInTheDocument();
  });

  it("shows parent, child, subtype, and target-user context on ticket detail", () => {
    render(<TicketContext ticket={ticketDetail()}/>);
    expect(screen.getByRole("link", { name: "TF-1" })).toHaveAttribute("href", "/tickets/TF-1");
    expect(screen.getByRole("link", { name: "TF-3" })).toHaveAttribute("href", "/tickets/TF-3");
    expect(screen.getByText("FIREWALL")).toBeInTheDocument();
    expect(screen.getByText("Target user: Ana Client")).toBeInTheDocument();
  });
});

function ticketDetail(): TicketDetail {
  return {
    id: 2,
    ticketKey: "TF-2",
    type: "TASI",
    subtype: "FIREWALL",
    subtypeId: 10,
    status: "ANALYSIS",
    priority: "MEDIUM",
    severity: null,
    title: "Open firewall path",
    description: "Need network rule.",
    organizationName: "ClientCo",
    businessOwnerName: "Ana Client",
    ticketLeadName: "Dev Lead",
    developerNames: ["Dev One"],
    currentResponsibility: "TICKETFLOW1",
    slaStatus: "NOT_APPLICABLE",
    createdAt: "2026-07-22T00:00:00Z",
    updatedAt: "2026-07-22T00:00:00Z",
    closedAt: null,
    organization: { id: 1, name: "ClientCo" },
    businessOwner: { id: 5, displayName: "Ana Client" },
    ticketLead: { id: 6, displayName: "Dev Lead" },
    developers: [{ id: 7, displayName: "Dev One" }],
    teams: [{ id: 8, name: "Network" }],
    assignedTeam: "Network",
    processMap: { name: "TASI", states: [], transitions: [] },
    allowedTransitions: [],
    workflowCommands: [],
    proposalCommands: [],
    parentTicketKey: "TF-1",
    childTickets: [{ ticketKey: "TF-3", title: "Child implementation", type: "TASI", status: "IMPLEMENTATION", currentResponsibility: "TICKETFLOW1" }],
    targetUserDisplaySnapshot: "Ana Client",
    dynamicValues: { source_ip: "10.0.0.1" },
  };
}
