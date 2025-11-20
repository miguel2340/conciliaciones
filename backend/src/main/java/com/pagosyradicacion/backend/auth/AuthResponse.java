package com.pagosyradicacion.backend.auth;

public record AuthResponse(String accessToken, String refreshToken, long expiresIn) {}
