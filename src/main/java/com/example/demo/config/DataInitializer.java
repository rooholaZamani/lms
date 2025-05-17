package com.example.demo.config;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class DataInitializer {

//    @Bean
//    public CommandLineRunner initData(RoleRepository roleRepository) {
//        return args -> {
//            // Initialize roles if they don't exist
//            if (roleRepository.findByName("ROLE_STUDENT").isEmpty()) {
//                Role studentRole = new Role();
//                studentRole.setName("ROLE_STUDENT");
//                roleRepository.save(studentRole);
//            }
//
//            if (roleRepository.findByName("ROLE_TEACHER").isEmpty()) {
//                Role teacherRole = new Role();
//                teacherRole.setName("ROLE_TEACHER");
//                roleRepository.save(teacherRole);
//            }
//
//            // Add admin role
//            if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
//                Role adminRole = new Role();
//                adminRole.setName("ROLE_ADMIN");
//                roleRepository.save(adminRole);
//            }
//        };
//    }
    @Bean
    public CommandLineRunner initData(
            RoleRepository roleRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        return args -> {
            // Initialize roles if they don't exist
            Role studentRole;
            if (roleRepository.findByName("ROLE_STUDENT").isEmpty()) {
                studentRole = new Role();
                studentRole.setName("ROLE_STUDENT");
                roleRepository.save(studentRole);
            } else {
                studentRole = roleRepository.findByName("ROLE_STUDENT").get();
            }

            Role teacherRole;
            if (roleRepository.findByName("ROLE_TEACHER").isEmpty()) {
                teacherRole = new Role();
                teacherRole.setName("ROLE_TEACHER");
                roleRepository.save(teacherRole);
            } else {
                teacherRole = roleRepository.findByName("ROLE_TEACHER").get();
            }

            Role adminRole;
            if (roleRepository.findByName("ROLE_ADMIN").isEmpty()) {
                adminRole = new Role();
                adminRole.setName("ROLE_ADMIN");
                roleRepository.save(adminRole);
            } else {
                adminRole = roleRepository.findByName("ROLE_ADMIN").get();
            }

            // Create default admin user if it doesn't exist
            if (userRepository.findByUsername("admin").isEmpty()) {
                User adminUser = new User();
                adminUser.setUsername("admin");
                adminUser.setPassword(passwordEncoder.encode("Admin@123"));
                adminUser.setFirstName("مدیر");
                adminUser.setLastName("سیستم");
                adminUser.setEmail("admin@example.com");
                adminUser.setNationalId("0000000000");
                adminUser.setPhoneNumber("09000000000");
                adminUser.setAge(30);

                // Set admin role
                Set<Role> adminRoles = new HashSet<>();
                adminRoles.add(adminRole);
                adminUser.setRoles(adminRoles);

                userRepository.save(adminUser);

                System.out.println("Default admin user created with username: admin and password: Admin@123");
            }
        };
    }
}