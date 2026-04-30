package com.posgateway.aml.config;

import com.posgateway.aml.entity.Role;
import com.posgateway.aml.entity.User;
import com.posgateway.aml.repository.RoleRepository;
import com.posgateway.aml.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner seedUsers(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Ensure ADMIN Role exists
            Optional<Role> adminRoleOpt = roleRepository.findByNameAndPspIsNull("ADMIN");
            Role adminRole;
            if (adminRoleOpt.isEmpty()) {
                adminRole = new Role();
                adminRole.setName("ADMIN");
                adminRole.setDescription("System Administrator");
                adminRole = roleRepository.save(adminRole);
                System.out.println("DataSeeder: Created ADMIN role");
            } else {
                adminRole = adminRoleOpt.get();
            }

            // 2. Ensure Admin User exists
            if (userRepository.findByUsername("admin").isEmpty()) {
                if (userRepository.findByEmail("admin@posgateway.com").isPresent()) {
                    System.out.println(
                            "DataSeeder: User with email admin@posgateway.com already exists. Skipping creation.");
                    return;
                }

                try {
                    User admin = new User();
                    admin.setUsername("admin");
                    admin.setPasswordHash(passwordEncoder.encode("password123"));
                    admin.setEmail("admin@posgateway.com");
                    admin.setRole(adminRole);
                    admin.setFirstName("System");
                    admin.setLastName("Admin");
                    admin.setEnabled(true);
                    userRepository.save(admin);
                    System.out.println("DataSeeder: Created default admin user (admin/password123)");
                } catch (Exception e) {
                    System.err.println("DataSeeder: Failed to create admin user: " + e.getMessage());
                    // Do not rethrow, let app startup continue
                }
            }
        };
    }
}
