package com.gateway.controller;

import com.gateway.dto.MessageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints reachable only by ROLE_ADMIN. Double-guarded: once at the URL
 * level in SecurityConfig ("/api/admin/**" -> hasRole("ADMIN")) and again
 * with @PreAuthorize here for defense-in-depth if this controller is ever
 * re-mapped under a different path.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public MessageResponse dashboard() {
        return new MessageResponse("Welcome to the admin dashboard");
    }
}
