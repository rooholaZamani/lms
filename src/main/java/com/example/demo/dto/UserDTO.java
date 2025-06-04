// src/main/java/com/example/demo/dto/UserDTO.java
package com.example.demo.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String nationalId;
    private String phoneNumber;
    private Integer age;
    private String email;
    private List<RoleDTO> roles = new ArrayList<>();

    @Data
    public static class RoleDTO {
        private Long id;
        private String name;
    }
}