package com.flashmall.user.controller;

import com.flashmall.user.entity.User;
import com.flashmall.user.service.UserService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    @Autowired
    private UserService userService;

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
}
