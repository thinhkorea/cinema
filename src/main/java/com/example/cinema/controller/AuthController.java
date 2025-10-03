package com.example.cinema.controller;

import com.example.cinema.domain.User;
import com.example.cinema.dto.LoginRequest;
import com.example.cinema.dto.LoginResponse;
import com.example.cinema.dto.RegisterRequest;
import com.example.cinema.repository.UserRepository;
import com.example.cinema.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtUtil jwtUtil, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername());
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(new LoginResponse(null, "Bad credentials", null));
        }
        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return ResponseEntity.ok(new LoginResponse(token, "OK", user.getRole().name()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (userRepository.findByUsername(req.getUsername()) != null) {
            return ResponseEntity.badRequest().body("Username already exists!");
        }

        User newUser = new User();
        newUser.setUsername(req.getUsername());
        newUser.setPassword(passwordEncoder.encode(req.getPassword())); // hash password
        newUser.setFullName(req.getFullName());
        newUser.setRole(User.Role.CUSTOMER); // mặc định CUSTOMER

        userRepository.save(newUser);
        return ResponseEntity.ok("Register success!");
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader("Authorization") String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body("Missing token");
        }
        String token = header.substring(7);
        String username = jwtUtil.extractUsername(token);
        User user = userRepository.findByUsername(username);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/register-admin")
    public ResponseEntity<?> registerAdmin(@RequestBody RegisterRequest req) {
        if (userRepository.findByUsername(req.getUsername()) != null) {
            return ResponseEntity.badRequest().body("Username already exists!");
        }

        User newUser = new User();
        newUser.setUsername(req.getUsername());
        newUser.setPassword(passwordEncoder.encode(req.getPassword()));
        newUser.setFullName(req.getFullName());
        newUser.setRole(User.Role.ADMIN);

        userRepository.save(newUser);
        return ResponseEntity.ok("Admin created!");
    }

}
