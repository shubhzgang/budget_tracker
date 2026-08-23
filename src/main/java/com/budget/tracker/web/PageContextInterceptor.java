package com.budget.tracker.web;

import com.budget.tracker.context.AuthContext;
import com.budget.tracker.model.User;
import com.budget.tracker.model.UserPreference;
import com.budget.tracker.repository.UserRepository;
import com.budget.tracker.service.UserPreferenceService;
import com.budget.tracker.util.TimeZones;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PageContextInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final UserPreferenceService userPreferenceService;

    public PageContextInterceptor(UserRepository userRepository, UserPreferenceService userPreferenceService) {
        this.userRepository = userRepository;
        this.userPreferenceService = userPreferenceService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/") || path.startsWith("/css/") || path.startsWith("/js/")) {
            return true;
        }

        String theme = "light";
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("theme".equals(c.getName())) {
                    theme = c.getValue();
                    break;
                }
            }
        }
        request.setAttribute("theme", theme);
        request.setAttribute("appZone", TimeZones.APP_ZONE);

        if (AuthContext.getUserId() != null) {
            User user = userRepository.findById(AuthContext.getUserId()).orElse(null);
            if (user != null) {
                request.setAttribute("userEmail", user.getEmail());
                UserPreference preference = userPreferenceService.getPreferences(user.getId());
                String symbol = preference != null ? preference.getCurrencySymbol() : null;
                request.setAttribute("currencySymbol", symbol != null ? symbol : CurrencyFormatter.DEFAULT_SYMBOL);
                request.setAttribute("fmt", new CurrencyFormatter(symbol));
            }
        }
        return true;
    }
}
