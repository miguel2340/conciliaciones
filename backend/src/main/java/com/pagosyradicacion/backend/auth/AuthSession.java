package com.pagosyradicacion.backend.auth;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuthSession(UUID userId, String email, String fullName, Set<String> roles, Instant expiresAt) {}
