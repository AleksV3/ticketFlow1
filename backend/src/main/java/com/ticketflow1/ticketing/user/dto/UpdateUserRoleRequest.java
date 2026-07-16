package com.ticketflow1.ticketing.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull Long roleId) {
}
