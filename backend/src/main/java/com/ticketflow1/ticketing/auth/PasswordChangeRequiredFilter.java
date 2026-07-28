package com.ticketflow1.ticketing.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketflow1.ticketing.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limits a one-time-password session to changing its password or signing out. */
@Component
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/health", "/api/auth/csrf", "/api/auth/logout",
            "/api/auth/change-password", "/api/users/me");

    private final ObjectMapper objectMapper;

    PasswordChangeRequiredFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof AuthPrincipal user && user.passwordChangeRequired()
                && !ALLOWED_PATHS.contains(request.getRequestURI())) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), ApiError.of(HttpStatus.FORBIDDEN.value(),
                    "PASSWORD_CHANGE_REQUIRED", "Change your password before using TicketFlow1.",
                    request.getRequestURI()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
