package com.flashmall.user.controller;

import com.flashmall.user.entity.User;
import com.flashmall.user.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Validated
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<User> register(@Valid @RequestBody User user) {
        User created = userService.register(user);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/login")
    public ResponseEntity<User> login(@Valid @RequestBody LoginRequest req) {
        User u = userService.login(req.getUsername(), req.getPassword());
        if (u == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(u);
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        String instance = System.getenv("SERVER_PORT");
        if (instance == null) instance = "unknown";
        return ResponseEntity.ok("user-service OK - port:" + instance);
    }
}
