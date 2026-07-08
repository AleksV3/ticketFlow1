package com.ticketflow1.ticketing.rbac.dto;

import java.util.Set;

/** Both fields optional (PATCH semantics): null means "leave unchanged". */
public record UpdateRoleRequest(String name, Set<String> permissionKeys) {
}
