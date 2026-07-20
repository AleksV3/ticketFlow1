import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { permittedLinks } from "@/components/AppShell";
import { TransitionButtons } from "@/components/TicketUi";

describe("permission-driven UI", () => {
  it("only returns navigation authorized by the API permissions", () => {
    expect(permittedLinks(["TICKET_READ", "USER_MANAGE"]).map(link => link.label))
      .toEqual(["Dashboard", "Tickets", "Teams", "Organizations", "Users"]);
  });

  it.each([
    { role: "Admin", permissions: ["TICKET_READ", "TICKET_CREATE", "USER_MANAGE", "ROLE_MANAGE", "WORKFLOW_MANAGE"], links: 8 },
    { role: "TicketFlow1 User", permissions: ["TICKET_READ", "TICKET_CREATE"], links: 4 },
    { role: "TicketFlow1 Manager", permissions: ["TICKET_READ", "TICKET_CREATE", "USER_MANAGE"], links: 6 },
    { role: "Client User", permissions: ["TICKET_READ", "TICKET_CREATE"], links: 4 },
    { role: "Client Approver", permissions: ["TICKET_READ", "TICKET_CREATE", "ROLE_MANAGE"], links: 5 },
  ])("shows only permitted navigation for $role", ({ permissions, links }) => {
    expect(permittedLinks(permissions)).toHaveLength(links);
  });

  it("renders only server-provided transitions", () => {
    render(<TransitionButtons allowedTransitions={["ANALYSIS", "REJECTED"]} onTransition={vi.fn()}/>);
    expect(screen.getByRole("button", { name: "ANALYSIS" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "REJECTED" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "APPROVED" })).not.toBeInTheDocument();
  });

  it("confirms the selected transition", async () => {
    const action = vi.fn().mockResolvedValue(undefined);
    render(<TransitionButtons allowedTransitions={["DONE"]} onTransition={action}/>);
    fireEvent.click(screen.getByRole("button", { name: "DONE" }));
    expect(action).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Yes, continue" }));
    expect(action).toHaveBeenCalledWith("DONE");
  });
});
