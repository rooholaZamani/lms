package com.example.demo.config;

import com.example.demo.model.Role;
import com.example.demo.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initData(RoleRepository roleRepository) {
        return args -> {
            // Initialize roles if they don't exist
            if (roleRepository.findByName("ROLE_STUDENT").isEmpty()) {
                Role studentRole = new Role();
                studentRole.setName("ROLE_STUDENT");
                roleRepository.save(studentRole);
            }

            if (roleRepository.findByName("ROLE_TEACHER").isEmpty()) {
                Role teacherRole = new Role();
                teacherRole.setName("ROLE_TEACHER");
                roleRepository.save(teacherRole);
            }

            // Add admin role
            if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
                Role adminRole = new Role();
                adminRole.setName("ROLE_ADMIN");
                roleRepository.save(adminRole);
            }
        };
    }
}