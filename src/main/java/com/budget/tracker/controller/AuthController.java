package com.budget.tracker.controller;

import com.budget.tracker.model.User;
import com.budget.tracker.payload.request.LoginRequest;
import com.budget.tracker.payload.request.SignupRequest;
import com.budget.tracker.payload.response.JwtResponse;
import com.budget.tracker.payload.response.MessageResponse;
import com.budget.tracker.repository.UserRepository;
import com.budget.tracker.security.JwtUtils;
import com.budget.tracker.security.UserDetailsImpl;
import com.budget.tracker.service.CategoryService;
import com.budget.tracker.service.LabelService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final CategoryService categoryService;
    private final LabelService labelService;

    public AuthController(AuthenticationManager authenticationManager, UserRepository userRepository, PasswordEncoder encoder, JwtUtils jwtUtils, CategoryService categoryService, LabelService labelService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
        this.categoryService = categoryService;
        this.labelService = labelService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication.getName());

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername()));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) {
        if (userRepository.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: Email is already in use!"));
        }

        // Create new user's account
        User user = new User();
        user.setEmail(signUpRequest.getEmail());
        user.setPasswordHash(encoder.encode(signUpRequest.getPassword()));
        user.setCreatedAt(OffsetDateTime.now());

        User savedUser = userRepository.save(user);

        // Initialize defaults
        categoryService.initializeDefaultCategories(savedUser.getId());
        labelService.initializeDefaultLabels(savedUser.getId());

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}
