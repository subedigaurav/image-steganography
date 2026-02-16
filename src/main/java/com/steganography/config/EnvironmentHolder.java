package com.steganography.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Holds a reference to Spring's Environment so that non-Spring classes
 * (e.g. SteganographyConfig) can access properties. Config reads from env vars
 * and .env (via spring-dotenv) first.
 */
@Component
public class EnvironmentHolder {

    private static Environment environment;

    /**
     * Injects Spring's Environment for use by non-Spring classes.
     *
     * @param environment Spring's environment (property sources)
     */
    public EnvironmentHolder(Environment environment) {
        EnvironmentHolder.environment = environment;
    }

    /**
     * Returns the Spring Environment, or {@code null} when running outside Spring.
     */
    static Environment getEnvironment() {
        return environment;
    }
}
