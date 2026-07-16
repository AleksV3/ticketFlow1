package com.ticketflow1.ticketing.user.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record UpdateUserRoleRequest(@NotEmpty Set<Long> roleIds) {
}
