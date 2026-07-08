package com.ticketflow1.ticketing.auth;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize (used by admin controllers in Step 5)
class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/api/auth/login",
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs/**"
    };

    private final JwtAuthFilter jwtAuthFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    SecurityConfig(JwtAuthFilter jwtAuthFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless JWT API: no CSRF tokens, no server session.
            .csrf(AbstractHttpConfigurer::disable)
            // CORS must be handled INSIDE the security chain (and before auth):
            // the browser's OPTIONS preflight carries no Authorization header,
            // so without this it would be rejected as unauthenticated before
            // the CorsFilter ever saw it.
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            // Our JWT filter runs before the username/password filter so the
            // SecurityContext is populated before authorization is checked.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Phase 7: the Next.js dev server calls this API directly from the
     * browser, which makes every request cross-origin. Any request with an
     * Authorization header triggers an OPTIONS preflight asking "may this
     * origin send these methods/headers?" — this bean is the answer.
     *
     * Origin PATTERNS (not a fixed list): two TicketFlow1 interns run this on the
     * same office/home wifi, each opening the other's `npm run dev` at
     * whatever private LAN IP DHCP handed out that day — 192.168.x.x is by
     * far the most common home-router range. localhost:3000 stays listed
     * for the single-machine case. Widen this list if your network uses a
     * different private range (10.x.x.x, 172.16-31.x.x).
     *
     * allowCredentials stays false: we authenticate with a Bearer HEADER the
     * frontend attaches explicitly, not with cookies the browser attaches
     * automatically. Credentials-mode CORS (and its stricter rules) is only
     * needed for the cookie case.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:3000",
                "http://192.168.*.*:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
