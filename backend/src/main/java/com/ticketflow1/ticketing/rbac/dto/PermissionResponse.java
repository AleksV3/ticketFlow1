package com.ticketflow1.ticketing.rbac.dto;

import com.ticketflow1.ticketing.rbac.Permission;

public record PermissionResponse(Long id, String key) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getKey());
    }
}
