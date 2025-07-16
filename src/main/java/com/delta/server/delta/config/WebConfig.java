package com.delta.server.delta.config;

import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // forward all routes (except those containing a period) to index.html
        registry.addViewController("/")
                .setViewName("forward:/index.html");

    }
}
