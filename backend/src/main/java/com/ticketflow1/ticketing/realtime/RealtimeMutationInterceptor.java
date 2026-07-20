package com.ticketflow1.ticketing.realtime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RealtimeMutationInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final RealtimeEvents events;
    public RealtimeMutationInterceptor(RealtimeEvents events) { this.events = events; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(this); }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception exception) {
        String method = request.getMethod(), path = request.getRequestURI();
        boolean relevant = path.startsWith("/api/tickets") || path.startsWith("/api/proposals")
                || path.startsWith("/api/teams");
        if (!"GET".equals(method) && relevant && exception == null && response.getStatus() < 400) {
            events.ticketsChanged();
        }
    }
}
