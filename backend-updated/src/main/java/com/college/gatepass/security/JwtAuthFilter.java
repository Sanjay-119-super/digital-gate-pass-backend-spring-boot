package com.college.gatepass.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;                          // ✅ FIX #1: @Slf4j import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            try {
                String token = header.substring(7);
                Claims claims = jwt.parse(token);

                Long userId = Long.parseLong(claims.getSubject());

                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);

                List<SimpleGrantedAuthority> authorities = (roles == null)
                        ? List.of()
                        : roles.stream()
                          .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                          .toList();

                var authentication = new UsernamePasswordAuthenticationToken(
                        userId, null, authorities
                );
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (ExpiredJwtException e) {
                log.debug("JWT expired for request to {}", request.getRequestURI());
            } catch (JwtException e) {
                log.warn("Invalid JWT token on request to {}: {}",
                        request.getRequestURI(), e.getMessage());
            } catch (NumberFormatException e) {
                log.error("JWT subject is not a valid user ID on {}: {}",
                        request.getRequestURI(), e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error parsing JWT on {}", request.getRequestURI(), e);
            }
            // ↑ if block yahan close hota hai — sahi jagah
        }

        chain.doFilter(request, response);
    }
}