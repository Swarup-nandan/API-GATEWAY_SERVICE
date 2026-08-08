package com.gateway.controller;

import com.gateway.security.CustomUserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints reachable by any authenticated user, regardless of role.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> profile(@AuthenticationPrincipal CustomUserDetails principal) {
        return Map.of(
                "username", principal.getUsername(),
                "roles", principal.getAuthorities()
        );
    }
}
