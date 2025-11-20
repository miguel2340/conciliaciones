package com.pagosyradicacion.backend.auth;

import java.io.IOException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationFilter.class);

  private static final String BEARER_PREFIX = "Bearer ";
  private final AuthService authService;

  public TokenAuthenticationFilter(AuthService authService) {
    this.authService = authService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

    // Skip auth processing for uploads (temporarily while testing business errors)
    if (request.getRequestURI().startsWith("/api/v1/cargas")) {
      filterChain.doFilter(request, response);
      return;
    }

    log.info("Procesando petición {} {} con Authorization: {}", request.getMethod(), request.getRequestURI(), authorization);

    if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
      String token = authorization.substring(BEARER_PREFIX.length());
      authService.findSession(token).ifPresentOrElse(session -> {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
          var authorities = session.roles().stream()
              .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
              .collect(Collectors.toSet());

          var authentication = new UsernamePasswordAuthenticationToken(session.email(), token, authorities);
          authentication.setDetails(session);
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      }, () -> log.info("Token inválido o expirado recibido"));
    } else if (authorization != null) {
      log.info("Encabezado Authorization sin prefijo Bearer: {}", authorization);
    }

    filterChain.doFilter(request, response);
  }
}
