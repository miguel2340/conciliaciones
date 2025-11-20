package com.pagosyradicacion.backend.user;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class UserDataInitializer {

  private static final Logger log = LoggerFactory.getLogger(UserDataInitializer.class);

  @Bean
  ApplicationRunner seedDefaultUsers(UserAccountRepository repository, PasswordEncoder passwordEncoder) {
    return args -> {
      if (repository.count() > 0) {
        return;
      }

      UserAccount admin = new UserAccount(
          "admin@empresa.com",
          passwordEncoder.encode("admin123"),
          "Administrador",
          Set.of("ADMIN", "USER"));

      UserAccount operator = new UserAccount(
          "operador@empresa.com",
          passwordEncoder.encode("operador123"),
          "Operador",
          Set.of("USER"));

      repository.save(admin);
      repository.save(operator);
      log.info("Usuarios iniciales creados en la base de datos");
    };
  }
}
