package com.steganography.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Serves robots.txt and sitemap.xml for search engine crawlers.
 */
@RestController
public class SeoController {

  private static final List<String> SITEMAP_PATHS = List.of("/", "/encode", "/decode", "/visualize");

  @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> robots(HttpServletRequest request) {
    String baseUrl = getBaseUrl(request);
    String body = """
        User-agent: *
        Allow: /

        Sitemap: %ssitemap.xml
        """.formatted(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
    return ResponseEntity.ok(body);
  }

  @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
  public ResponseEntity<String> sitemap(HttpServletRequest request) {
    String baseUrl = getBaseUrl(request).replaceAll("/$", "");
    String today = LocalDate.now().toString();

    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
    for (String path : SITEMAP_PATHS) {
      String loc = baseUrl + path;
      xml.append("  <url>\n");
      xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
      xml.append("    <lastmod>").append(today).append("</lastmod>\n");
      xml.append("    <changefreq>weekly</changefreq>\n");
      xml.append("    <priority>").append(path.equals("/") ? "1.0" : "0.8").append("</priority>\n");
      xml.append("  </url>\n");
    }
    xml.append("</urlset>");
    return ResponseEntity.ok(xml.toString());
  }

  private String getBaseUrl(HttpServletRequest request) {
    String scheme = request.getScheme();
    String host = request.getHeader("Host");
    String contextPath = request.getContextPath();
    return scheme + "://" + host + contextPath;
  }

  private static String escapeXml(String s) {
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
