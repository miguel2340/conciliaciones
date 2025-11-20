package com.pagosyradicacion.backend.user;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class UserAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true, length = 150)
  private String email;

  @Column(name = "password_hash", nullable = false, length = 120)
  private String passwordHash;

  @Column(name = "full_name", nullable = false, length = 180)
  private String fullName;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role_name", nullable = false, length = 60)
  private Set<String> roles = new HashSet<>();

  protected UserAccount() {
    // JPA only
  }

  public UserAccount(String email, String passwordHash, String fullName, Set<String> roles) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.fullName = fullName;
    if (roles != null) {
      this.roles.addAll(roles);
    }
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public void setRoles(Set<String> roles) {
    this.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof UserAccount other)) {
      return false;
    }
    return Objects.equals(id, other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
