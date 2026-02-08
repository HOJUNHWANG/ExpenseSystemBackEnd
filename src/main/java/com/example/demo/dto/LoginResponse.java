// src/main/java/com/example/demo/dto/LoginResponse.java
package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private Long id;
    private String name;
    private String email;
    private String role;

}
