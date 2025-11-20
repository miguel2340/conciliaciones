package com.pagosyradicacion.backend.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Validated @RequestBody AuthRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @GetMapping("/me")
  public ResponseEntity<?> me(@RequestHeader(name = "Authorization", required = false) String authorization) {
    String token = null;
    if (authorization != null && authorization.startsWith("Bearer ")) token = authorization.substring(7);
    return authService.findSession(token)
        .<ResponseEntity<?>>map(s -> ResponseEntity.ok(new MeResponse(s.email(), s.fullName(), s.roles())))
        .orElseGet(() -> ResponseEntity.status(401).body(java.util.Map.of("error","No autorizado")));
  }

  public record MeResponse(String email, String fullName, java.util.Set<String> roles) {}
}
