package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client-side authentication service.
 * Handles login, registration, token refresh, and logout with the ScreenAI server.
 */
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient;
    private final TokenStorageService tokenStorage;
    private String serverBaseUrl;

    public AuthenticationService(TokenStorageService tokenStorage) {
        this.tokenStorage = tokenStorage;
        this.serverBaseUrl = "http://localhost:8080"; // Default, can be changed
        
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Set the server base URL.
     */
    public void setServerBaseUrl(String url) {
        this.serverBaseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        log.info("Server URL set to: {}", serverBaseUrl);
    }

    /**
     * Get the server base URL.
     */
    public String getServerBaseUrl() {
        return serverBaseUrl;
    }

    // ==================== Authentication Operations ====================

    /**
     * Login with username and password.
     * Returns AuthResult with success status and message.
     */
    public CompletableFuture<AuthResult> login(String username, String password) {
        log.info("Attempting login for user: {}", username);

        String requestBody = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\"}",
                escapeJson(username), escapeJson(password)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleAuthResponse(response, username))
                .exceptionally(ex -> {
                    log.error("Login request failed: {}", ex.getMessage());
                    return new AuthResult(false, "Connection failed: " + ex.getMessage(), null);
                });
    }

    /**
     * Register a new user account.
     */
    public CompletableFuture<AuthResult> register(String username, String password) {
        log.info("Attempting registration for user: {}", username);

        String requestBody = String.format(
                "{\"username\":\"%s\",\"password\":\"%s\"}",
                escapeJson(username), escapeJson(password)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleAuthResponse(response, username))
                .exceptionally(ex -> {
                    log.error("Registration request failed: {}", ex.getMessage());
                    return new AuthResult(false, "Connection failed: " + ex.getMessage(), null);
                });
    }

    /**
     * Refresh the access token using the refresh token.
     */
    public CompletableFuture<AuthResult> refreshToken() {
        Optional<String> refreshTokenOpt = tokenStorage.getRefreshToken();
        if (refreshTokenOpt.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new AuthResult(false, "No refresh token available", null)
            );
        }

        log.debug("Attempting token refresh");

        String requestBody = String.format(
                "{\"refreshToken\":\"%s\"}",
                escapeJson(refreshTokenOpt.get())
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/auth/refresh"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonNode json = objectMapper.readTree(response.body());
                            String newAccessToken = json.get("accessToken").asText();
                            long expiresIn = json.has("expiresIn") ? json.get("expiresIn").asLong() : 900000;

                            tokenStorage.updateAccessToken(newAccessToken, expiresIn);

                            log.info("Token refreshed successfully");
                            return new AuthResult(true, "Token refreshed", newAccessToken);
                        } catch (Exception e) {
                            log.error("Failed to parse refresh response: {}", e.getMessage());
                            return new AuthResult(false, "Failed to parse response", null);
                        }
                    } else {
                        String errorMessage = parseErrorMessage(response.body());
                        log.warn("Token refresh failed: {}", errorMessage);
                        // Clear tokens if refresh failed
                        tokenStorage.clearTokens();
                        return new AuthResult(false, errorMessage, null);
                    }
                })
                .exceptionally(ex -> {
                    log.error("Token refresh request failed: {}", ex.getMessage());
                    return new AuthResult(false, "Connection failed: " + ex.getMessage(), null);
                });
    }

    /**
     * Logout and clear stored tokens.
     */
    public CompletableFuture<AuthResult> logout() {
        Optional<String> accessTokenOpt = tokenStorage.getAccessToken();
        Optional<String> refreshTokenOpt = tokenStorage.getRefreshToken();

        // Clear local tokens regardless of server response
        String username = tokenStorage.getUsername().orElse("unknown");
        tokenStorage.clearTokens();

        if (refreshTokenOpt.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new AuthResult(true, "Logged out (locally)", null)
            );
        }

        log.info("Logging out user: {}", username);

        String requestBody = String.format(
                "{\"refreshToken\":\"%s\"}",
                escapeJson(refreshTokenOpt.get())
        );

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/auth/logout"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10));

        // Add access token if available
        accessTokenOpt.ifPresent(token -> 
                requestBuilder.header("Authorization", "Bearer " + token));

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        log.info("Logged out successfully");
                        return new AuthResult(true, "Logged out successfully", null);
                    } else {
                        log.debug("Server logout returned {}, but local tokens cleared", response.statusCode());
                        return new AuthResult(true, "Logged out (server may have already invalidated token)", null);
                    }
                })
                .exceptionally(ex -> {
                    log.warn("Logout request failed, but local tokens cleared: {}", ex.getMessage());
                    return new AuthResult(true, "Logged out locally (server unavailable)", null);
                });
    }

    /**
     * Validate current token with server.
     */
    public CompletableFuture<Boolean> validateToken() {
        Optional<String> accessTokenOpt = tokenStorage.getAccessToken();
        if (accessTokenOpt.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverBaseUrl + "/api/auth/validate"))
                .header("Authorization", "Bearer " + accessTokenOpt.get())
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(ex -> {
                    log.debug("Token validation failed: {}", ex.getMessage());
                    return false;
                });
    }

    // ==================== Convenience Methods ====================

    /**
     * Get a valid access token, refreshing if necessary.
     */
    public CompletableFuture<Optional<String>> getValidAccessToken() {
        // Check if current token is still valid
        Optional<String> currentToken = tokenStorage.getAccessToken();
        if (currentToken.isPresent()) {
            return CompletableFuture.completedFuture(currentToken);
        }

        // Try to refresh if we have a refresh token
        if (tokenStorage.needsRefresh()) {
            return refreshToken().thenApply(result -> {
                if (result.success()) {
                    return Optional.ofNullable(result.token());
                }
                return Optional.empty();
            });
        }

        return CompletableFuture.completedFuture(Optional.empty());
    }

    /**
     * Check if user is authenticated (has valid or refreshable session).
     */
    public boolean isAuthenticated() {
        return tokenStorage.isLoggedIn();
    }

    /**
     * Get the currently logged-in username.
     */
    public Optional<String> getCurrentUsername() {
        return tokenStorage.getUsername();
    }

    // ==================== Helper Methods ====================

    private AuthResult handleAuthResponse(HttpResponse<String> response, String username) {
        if (response.statusCode() == 200) {
            try {
                JsonNode json = objectMapper.readTree(response.body());
                String accessToken = json.get("accessToken").asText();
                String refreshToken = json.has("refreshToken") ? json.get("refreshToken").asText() : null;
                long expiresIn = json.has("expiresIn") ? json.get("expiresIn").asLong() : 900000;

                tokenStorage.storeTokens(accessToken, refreshToken, expiresIn, username);

                log.info("Authentication successful for user: {}", username);
                return new AuthResult(true, "Success", accessToken);
            } catch (Exception e) {
                log.error("Failed to parse auth response: {}", e.getMessage());
                return new AuthResult(false, "Failed to parse server response", null);
            }
        } else {
            String errorMessage = parseErrorMessage(response.body());
            log.warn("Authentication failed: {} - {}", response.statusCode(), errorMessage);
            return new AuthResult(false, errorMessage, null);
        }
    }

    private String parseErrorMessage(String responseBody) {
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            if (json.has("message")) {
                return json.get("message").asText();
            }
            if (json.has("error")) {
                return json.get("error").asText();
            }
        } catch (Exception ignored) {
        }
        return "Unknown error";
    }

    private String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ==================== Result Record ====================

    /**
     * Result of an authentication operation.
     */
    public record AuthResult(
            boolean success,
            String message,
            String token
    ) {}
}
