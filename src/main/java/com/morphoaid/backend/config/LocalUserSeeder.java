package com.morphoaid.backend.config;

import com.morphoaid.backend.entity.Role;
import com.morphoaid.backend.entity.User;
import com.morphoaid.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("local")
public class LocalUserSeeder {

    @Bean
    public CommandLineRunner seedLocalUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByEmail("admin@test.com").isEmpty()) {
                User admin = User.builder()
                        .email("admin@test.com")
                        .password(passwordEncoder.encode("password"))
                        .role(Role.ADMIN)
                        .fullName("Local Dev Admin")
                        .organization("MorphoAid Dev")
                        .build();
                userRepository.save(admin);
                System.out.println("Seeded dev-only admin user: admin@test.com");
            }
        };
    }
}
