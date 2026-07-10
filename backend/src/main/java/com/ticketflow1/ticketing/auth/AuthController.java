package com.ticketflow1.ticketing.auth;

import com.ticketflow1.ticketing.auth.dto.CurrentUserResponse;
import com.ticketflow1.ticketing.auth.dto.LoginRequest;
import com.ticketflow1.ticketing.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var login = authService.login(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, login.cookie().toString())
                .body(login.response());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authService.clearLoginCookie().toString())
                .build();
    }

    @GetMapping("/users/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return authService.currentUser(principal);
    }
}
