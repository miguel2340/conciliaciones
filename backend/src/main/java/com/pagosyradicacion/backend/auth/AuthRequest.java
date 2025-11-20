package com.pagosyradicacion.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
    @NotBlank(message = "El usuario es obligatorio") @Email(message = "Debe ser un correo válido") String username,
    @NotBlank(message = "La contraseña es obligatoria") @Size(min = 6, message = "Debe tener al menos 6 caracteres") String password) {}
