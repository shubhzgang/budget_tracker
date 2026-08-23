package com.budget.tracker.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final PageContextInterceptor pageContextInterceptor;

    public WebConfig(PageContextInterceptor pageContextInterceptor) {
        this.pageContextInterceptor = pageContextInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pageContextInterceptor).addPathPatterns("/**");
    }
}
