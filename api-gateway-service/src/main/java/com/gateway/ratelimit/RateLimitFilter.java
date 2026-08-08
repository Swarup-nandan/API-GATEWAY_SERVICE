package com.gateway.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gateway.dto.MessageResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Applies a Redis-backed token bucket limit per authenticated user (falls back
 * to client IP for unauthenticated requests, e.g. /api/auth/**).
 *
 * NOTE: this filter is NOT annotated @Component. It is instantiated as a bean
 * and wired explicitly into the Spring Security filter chain (placed AFTER
 * JwtAuthenticationFilter) in SecurityConfig, so that SecurityContextHolder
 * already has the authenticated principal (if any) by the time this runs.
 */
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String key = resolveClientKey(request);
        RateLimiterService.RateLimitResult result = rateLimiterService.tryConsume(key);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));

        if (!result.allowed()) {
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write(
                    objectMapper.writeValueAsString(new MessageResponse("Rate limit exceeded. Try again later.")));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return "user:" + auth.getName();
        }
        return "ip:" + resolveClientIp(request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
