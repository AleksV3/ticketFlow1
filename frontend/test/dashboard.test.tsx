import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import {
  DASHBOARD_WIDGETS,
  DashboardPreferencesEditor,
  DashboardWidgetGrid,
  type Dashboard,
} from "@/app/dashboard/page";

vi.mock("next/navigation", () => ({}));
vi.mock("@/lib/realtime", () => ({ useTicketEvents: vi.fn() }));

describe("dashboard customization", () => {
  it("shows, hides, and reorders the bounded widget catalog", () => {
    const onChange = vi.fn();
    const { rerender } = render(<DashboardPreferencesEditor
      widgets={["MY_OPEN_TICKETS", "RECENTLY_UPDATED"]}
      saving={false}
      onChange={onChange}
      onSave={vi.fn()}
      onReset={vi.fn()}
    />);

    fireEvent.click(screen.getByLabelText("My team’s tickets"));
    expect(onChange).toHaveBeenLastCalledWith([
      "MY_OPEN_TICKETS", "RECENTLY_UPDATED", "MY_TEAM_TICKETS",
    ]);

    rerender(<DashboardPreferencesEditor
      widgets={["MY_OPEN_TICKETS", "RECENTLY_UPDATED"]}
      saving={false}
      onChange={onChange}
      onSave={vi.fn()}
      onReset={vi.fn()}
    />);
    fireEvent.click(screen.getByRole("button", { name: "Move Recently updated up" }));
    expect(onChange).toHaveBeenLastCalledWith([
      "RECENTLY_UPDATED", "MY_OPEN_TICKETS",
    ]);
  });

  it("renders saved order and ignores retired widget keys", () => {
    render(<DashboardWidgetGrid data={dashboard()} widgets={[
      "RECENTLY_UPDATED", "RETIRED_WIDGET", "TICKETS_BY_TYPE",
    ]} />);
    const headings = screen.getAllByRole("heading").map(item => item.textContent);
    expect(headings).toEqual(["Recently updated", "Tickets by type"]);
    expect(screen.queryByText("RETIRED_WIDGET")).not.toBeInTheDocument();
  });

  it("renders every initial dashboard widget", () => {
    render(<DashboardWidgetGrid data={dashboard()} widgets={[...DASHBOARD_WIDGETS]} />);
    expect(screen.getByRole("heading", { name: "My open tickets" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "My team’s tickets" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Tickets by workflow state" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Tickets by type" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Awaiting my approval" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Recently updated" })).toBeInTheDocument();
  });
});

function dashboard(): Dashboard {
  const ticket = {
    ticketKey: "TF-1",
    title: "Updated ticket",
    type: "REQ",
    status: "ANALYSIS",
    slaStatus: "OK",
  };
  return {
    activeCount: 1,
    closedCount: 0,
    byType: { REQ: 1 },
    byStatus: { ANALYSIS: 1 },
    defectsBySeverity: {},
    slaBreached: [],
    slaDueSoon: [],
    waitingForClientApproval: [],
    waitingForClientConfirmation: [],
    myAssignedTickets: [],
    myOpenTickets: [ticket],
    myTeamTickets: [],
    awaitingMyApproval: [],
    recentlyUpdated: [ticket],
  };
}
