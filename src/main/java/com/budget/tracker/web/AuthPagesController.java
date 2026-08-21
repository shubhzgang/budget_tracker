package com.budget.tracker.web;

import com.budget.tracker.model.User;
import com.budget.tracker.repository.UserRepository;
import com.budget.tracker.security.JwtUtils;
import com.budget.tracker.service.CategoryService;
import com.budget.tracker.service.LabelService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;
import java.time.OffsetDateTime;

@Controller
public class AuthPagesController {

    @Value("${app.auth.register-enabled:false}")
    private boolean registerEnabled;

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtUtils jwtUtils;
    private final CategoryService categoryService;
    private final LabelService labelService;

    public AuthPagesController(AuthenticationManager authenticationManager, UserRepository userRepository,
                               PasswordEncoder encoder, JwtUtils jwtUtils,
                               CategoryService categoryService, LabelService labelService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.jwtUtils = jwtUtils;
        this.categoryService = categoryService;
        this.labelService = labelService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "expired", required = false) boolean expired, Model model) {
        if (expired) {
            model.addAttribute("notice", "Your session has expired. Please sign in again.");
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password,
                        HttpServletResponse response, Model model) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication.getName());
            response.setHeader(HttpHeaders.SET_COOKIE, jwtCookie(jwt).toString());
            return "redirect:/dashboard";
        } catch (BadCredentialsException e) {
            model.addAttribute("error", e.getMessage());
            return "login";
        }
    }

    @GetMapping("/register")
    public String registerPage() {
        if (!registerEnabled) {
            return "redirect:/login";
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String email, @RequestParam String password,
                           HttpServletResponse response, Model model) {
        if (!registerEnabled) {
            return "redirect:/login";
        }
        if (userRepository.existsByEmail(email)) {
            model.addAttribute("error", "Error: Email is already in use!");
            return "register";
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        user.setCreatedAt(OffsetDateTime.now());
        User savedUser = userRepository.save(user);
        categoryService.initializeDefaultCategories(savedUser.getId());
        labelService.initializeDefaultLabels(savedUser.getId());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication.getName());
        response.setHeader(HttpHeaders.SET_COOKIE, jwtCookie(jwt).toString());
        return "redirect:/dashboard";
    }

    private ResponseCookie jwtCookie(String jwt) {
        return ResponseCookie.from("jwt", jwt)
                .httpOnly(true)
                .secure(false) // set true in production
                .path("/")
                .maxAge(Duration.ofSeconds(86400))
                .sameSite("Lax")
                .build();
    }
}
