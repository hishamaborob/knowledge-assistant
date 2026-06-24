package com.kbassistant.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String API_KEY_HEADER = "X-API-Key";

    private final Set<String> validKeys;

    // Comma-separated list from config/Secrets Manager.
    // When empty (local dev / tests), all requests are permitted.
    public ApiKeyAuthFilter(@Value("${app.security.api-keys:}") String apiKeysConfig) {
        this.validKeys = Arrays.stream(apiKeysConfig.split(","))
                .map(String::trim)
                .filter(k -> !k.isBlank())
                .collect(Collectors.toSet());
    }

    // Paths that bypass API key validation — must match SecurityConfig's permitAll() matchers.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (validKeys.isEmpty()) {
            // Dev mode: no keys configured → treat every request as authenticated
            // so Spring Security's authorization layer doesn't block with 403.
            var auth = new UsernamePasswordAuthenticationToken("dev", null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getHeader(API_KEY_HEADER);
        if (key != null && validKeys.contains(key)) {
            var auth = new UsernamePasswordAuthenticationToken(key, null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {"status":401,"title":"Unauthorized","detail":"Missing or invalid X-API-Key header"}
                    """);
        }
    }
}
