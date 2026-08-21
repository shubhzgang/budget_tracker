package com.budget.tracker.web;

import com.budget.tracker.context.AuthContext;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LandingController {

    @GetMapping("/")
    public String index() {
        return AuthContext.getUserId() != null ? "redirect:/dashboard" : "redirect:/login";
    }
}
