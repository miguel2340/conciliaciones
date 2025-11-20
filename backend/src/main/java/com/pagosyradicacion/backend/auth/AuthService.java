package com.pagosyradicacion.backend.auth;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.pagosyradicacion.backend.user.UserAccount;
import com.pagosyradicacion.backend.user.UserAccountRepository;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserAccountRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private static final Map<String, AuthSession> SESSIONS = new ConcurrentHashMap<>();

  public AuthService(UserAccountRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public AuthResponse login(AuthRequest request) {
    UserAccount user = userRepository
        .findByEmailIgnoreCase(request.username())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }

    String accessToken = UUID.randomUUID().toString();
    String refreshToken = UUID.randomUUID().toString();
    long ttlSeconds = 3_600L;
    Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
    long expiresIn = ttlSeconds;

    AuthSession session = new AuthSession(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        Set.copyOf(user.getRoles()),
        expiresAt);
    SESSIONS.put(accessToken, session);

    log.info("Creada sesión para {} con token {} expira {}", user.getEmail(), accessToken, expiresAt);

    return new AuthResponse(accessToken, refreshToken, expiresIn);
  }

  public Optional<AuthSession> findSession(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }

    AuthSession session = SESSIONS.get(token);
    log.info("Buscando sesión para token {} -> {}", token, session != null ? "encontrada" : "no encontrada");
    if (session == null) {
      return Optional.empty();
    }

    if (session.expiresAt().isBefore(Instant.now())) {
      log.info("Sesión expirada para token {}", token);
      SESSIONS.remove(token);
      return Optional.empty();
    }

    return Optional.of(session);
  }

  public void invalidateSession(String token) {
    if (token != null) {
      SESSIONS.remove(token);
    }
  }
}
