package com.example.flags;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Reads "Authorization: Bearer <token>" and, if the token is valid, authenticates the request with the user's roles. */
public class TokenAuthFilter extends OncePerRequestFilter {

    private final AuthService auth;

    public TokenAuthFilter(AuthService auth) { this.auth = auth; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            auth.authenticate(header.substring(7).trim()).ifPresent(principal ->
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            principal.username(), null,
                            principal.role().authorities().stream().map(SimpleGrantedAuthority::new).toList())));
        }
        chain.doFilter(request, response);
    }
}
