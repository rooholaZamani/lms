package com.example.demo.service;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.RoleRepository;
import com.example.demo.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(User user, boolean isTeacher) {
        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Assign role
        Role role = isTeacher ?
                roleRepository.findByName("ROLE_TEACHER")
                        .orElseThrow(() -> new RuntimeException("Role not found")) :
                roleRepository.findByName("ROLE_STUDENT")
                        .orElseThrow(() -> new RuntimeException("Role not found"));

        user.setRoles(Set.of(role));

        return userRepository.save(user);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public User findByNationalId(String nationalId) {
        return userRepository.findByNationalId(nationalId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // Other methods as needed...
}