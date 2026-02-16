package com.steganography.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * CORS configuration: only same-origin requests are allowed.
 * Cross-origin requests are blocked by not including their origin in allowed origins.
 */
@Configuration
public class CorsConfig {

  @Bean
  public CorsFilter corsFilter(CorsConfigurationSource corsConfigurationSource) {
    return new CorsFilter(corsConfigurationSource);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    return (HttpServletRequest request) -> {
      CorsConfiguration config = new CorsConfiguration();
      config.setAllowCredentials(true);
      config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
      config.setAllowedHeaders(List.of("*"));

      String origin = request.getHeader("Origin");
      if (origin == null) {
        return config;
      }

      String host = request.getHeader("Host");
      if (host == null) {
        return config;
      }

      String scheme = request.getScheme();
      String serverOrigin = scheme + "://" + host;
      if (origin.equals(serverOrigin)) {
        config.setAllowedOrigins(List.of(origin));
      }
      return config;
    };
  }
}
