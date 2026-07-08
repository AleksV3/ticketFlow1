package com.ticketflow1.ticketing.auth;

import com.ticketflow1.ticketing.auth.dto.CurrentUserResponse;
import com.ticketflow1.ticketing.auth.dto.LoginRequest;
import com.ticketflow1.ticketing.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/users/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.currentUser(principal);
    }
}
