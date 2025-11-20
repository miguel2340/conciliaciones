package com.pagosyradicacion.backend.user;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

  private final UserAccountRepository repo;
  private final PasswordEncoder passwordEncoder;

  public UserAdminController(UserAccountRepository repo, PasswordEncoder passwordEncoder) {
    this.repo = repo; this.passwordEncoder = passwordEncoder;
  }

  @GetMapping
  public Map<String, Object> list(@RequestParam(required = false) String q) {
    List<UserAccount> all = repo.findAll();
    List<Map<String, Object>> out = new ArrayList<>();
    for (UserAccount u : all) {
      if (q != null && !q.isBlank()) {
        String qq = q.toLowerCase(Locale.ROOT);
        if (!(u.getEmail().toLowerCase(Locale.ROOT).contains(qq) || u.getFullName().toLowerCase(Locale.ROOT).contains(qq))) continue;
      }
      Map<String, Object> row = new HashMap<>();
      row.put("id", u.getId());
      row.put("email", u.getEmail());
      row.put("fullName", u.getFullName());
      row.put("roles", u.getRoles());
      out.add(row);
    }
    return Map.of("data", out);
  }

  public record UpsertRequest(String email, String fullName, String password, List<String> roles) {}

  @PostMapping
  @Transactional
  public ResponseEntity<?> create(@RequestBody UpsertRequest req) {
    if (req.email() == null || req.email().isBlank()) return bad("Email obligatorio");
    if (req.fullName() == null || req.fullName().isBlank()) return bad("Nombre obligatorio");
    if (repo.findByEmailIgnoreCase(req.email()).isPresent()) return bad("Ya existe un usuario con ese email");
    Set<String> mapped = mapRoles(req.roles());
    String pass = (req.password() != null && !req.password().isBlank()) ? req.password() : "cambio123";
    UserAccount u = new UserAccount(req.email().trim(), passwordEncoder.encode(pass), req.fullName().trim(), mapped);
    repo.save(u);
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", u.getId()));
  }

  @PutMapping("/{id}")
  @Transactional
  public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody UpsertRequest req) {
    var u = repo.findById(id).orElse(null);
    if (u == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","Usuario no encontrado"));
    if (req.fullName() != null && !req.fullName().isBlank()) u.setFullName(req.fullName().trim());
    if (req.email() != null && !req.email().isBlank()) {
      // validar duplicidad
      var byEmail = repo.findByEmailIgnoreCase(req.email());
      if (byEmail.isPresent() && !byEmail.get().getId().equals(id)) return bad("Email ya en uso por otro usuario");
      u.setEmail(req.email().trim());
    }
    if (req.password() != null && !req.password().isBlank()) {
      u.setPasswordHash(passwordEncoder.encode(req.password()));
    }
    if (req.roles() != null) {
      u.setRoles(mapRoles(req.roles()));
    }
    repo.save(u);
    return ResponseEntity.ok(Map.of("ok", true));
  }

  @DeleteMapping("/{id}")
  @Transactional
  public ResponseEntity<?> delete(@PathVariable UUID id) {
    if (!repo.existsById(id)) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","Usuario no encontrado"));
    repo.deleteById(id);
    return ResponseEntity.ok(Map.of("ok", true));
  }

  private static ResponseEntity<Map<String, Object>> bad(String msg) { return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", msg)); }

  private static Set<String> mapRoles(List<String> in) {
    Set<String> out = new HashSet<>();
    if (in == null) return out;
    for (String r : in) {
      if (r == null) continue;
      String norm = normalize(r);
      switch (norm) {
        case "administrador", "admin" -> out.add("ADMIN");
        case "profesional", "usuario", "user", "operador" -> out.add("USER");
        default -> { if (r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("USER")) out.add(r.toUpperCase(Locale.ROOT)); }
      }
    }
    if (out.isEmpty()) out.add("USER");
    return out;
  }

  private static String normalize(String s) {
    String t = s.trim().toLowerCase(Locale.ROOT);
    t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    return t;
  }
}

