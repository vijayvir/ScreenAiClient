package config;

import org.springframework.context.annotation.Configuration;

/**
 * Spring Configuration for WebSocket Client
 *
 * Note: ServerConnectionService is auto-detected as @Service
 * Server URL is injected via @Value from application.yml
 * No manual bean creation needed
 */
@Configuration
public class WebSocketClientConfig {
    // ServerConnectionService is auto-detected and created by Spring
    // Due to @Service annotation
}


