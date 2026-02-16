package com.steganography.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Adds SEO-related model attributes for all Thymeleaf templates.
 * Provides requestUrl since #request is not available by default in Thymeleaf 3.1+.
 */
@ControllerAdvice
public class SeoModelAdvice {

  @ModelAttribute("requestUrl")
  public String addRequestUrl(HttpServletRequest request) {
    return request.getRequestURL().toString();
  }
}
