package service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Client-side authentication service.
 * Handles login, registration, token refresh, and logout with the ScreenAI server.
 * Features automatic token refresh before expiry.
 */
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Refresh token 1 minute before expiry
    private static final long REFRESH_BUFFER_MS = 60_000;

    private final HttpClient httpClient;
    private final TokenStorageService tokenStorage;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> refreshTask;
    private String serverBaseUrl;
    
    // Callback for when token refresh fails and re-login is needed
    private Consumer<String> onAuthenticationRequired;

    public AuthenticationService(TokenStorageService tokenStorage) {
        this.tokenStorage = tokenStorage;
        
        // Load server URL from environment config
        EnvConfig config = EnvConfig.getInstance();
        this.serverBaseUrl = config.getHttpUrl();
        
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(config.getHttpConnectTimeoutSeconds()))
                .build();
        
        // Single-threaded scheduler for token refresh
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TokenRefreshScheduler");
            t.setDaemon(true);
            return t;
        });
        
        log.info("AuthenticationService initialized with server URL: {}", serverBaseUrl);
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
    
    /**
     * Set callback for when re-authentication is required (token refresh failed).
     */
    public void setOnAuthenticationRequired(Consumer<String> callback) {
        this.onAuthenticationRequired = callback;
    }
    
    /**
     * Shutdown the service and stop scheduled tasks.
     */
    public void shutdown() {
        cancelRefreshTask();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("AuthenticationService shutdown complete");
    }

    // ==================== Token Refresh Scheduling ====================
    
    /**
     * Schedule automatic token refresh before expiry.
     */
    private void scheduleTokenRefresh(long expiresInMs) {
        cancelRefreshTask();
        
        // Schedule refresh 1 minute before expiry
        long delayMs = Math.max(expiresInMs - REFRESH_BUFFER_MS, 30_000); // At least 30 seconds
        
        log.info("Scheduling token refresh in {} seconds", delayMs / 1000);
        
        refreshTask = scheduler.schedule(this::performScheduledRefresh, delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Cancel any pending refresh task.
     */
    private void cancelRefreshTask() {
        if (refreshTask != null && !refreshTask.isDone()) {
            refreshTask.cancel(false);
            refreshTask = null;
        }
    }
    
    /**
     * Perform scheduled token refresh.
     */
    private void performScheduledRefresh() {
        log.info("Performing scheduled token refresh...");
        
        refreshToken().thenAccept(result -> {
            if (result.success()) {
                log.info("Scheduled token refresh successful");
            } else {
                log.warn("Scheduled token refresh failed: {}", result.message());
                // Notify callback that re-authentication is needed
                if (onAuthenticationRequired != null) {
                    onAuthenticationRequired.accept(result.message());
                }
            }
        }).exceptionally(ex -> {
            log.error("Scheduled token refresh error: {}", ex.getMessage());
            if (onAuthenticationRequired != null) {
                onAuthenticationRequired.accept("Token refresh failed: " + ex.getMessage());
            }
            return null;
        });
    }
    
    /**
     * Try to auto-login using persisted credentials (remember-me).
     * Returns true if auto-login was successful.
     */
    public CompletableFuture<AuthResult> tryAutoLogin() {
        if (!tokenStorage.hasPersistedCredentials()) {
            return CompletableFuture.completedFuture(
                new AuthResult(false, "No persisted credentials", null)
            );
        }
        
        log.info("Attempting auto-login with persisted credentials...");
        
        return refreshToken().thenApply(result -> {
            if (result.success()) {
                log.info("Auto-login successful for user: {}", tokenStorage.getUsername().orElse("unknown"));
            } else {
                log.warn("Auto-login failed: {}", result.message());
                // Clear invalid credentials
                tokenStorage.clearTokens();
            }
            return result;
        });
    }

    // ==================== Authentication Operations ====================

    /**
     * Login with username and password.
     * Returns AuthResult with success status and message.
     */
    public CompletableFuture<AuthResult> login(String username, String password) {
        return login(username, password, false);
    }
    
    /**
     * Login with username and password, with optional remember-me.
     * @param rememberMe if true, persists refresh token for auto-login
     */
    public CompletableFuture<AuthResult> login(String username, String password, boolean rememberMe) {
        log.info("Attempting login for user: {} (remember-me: {})", username, rememberMe);

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
                .thenApply(response -> handleAuthResponse(response, username, rememberMe))
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
                            
                            // Schedule next refresh
                            scheduleTokenRefresh(expiresIn);

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
        // Cancel any pending refresh
        cancelRefreshTask();
        
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

    private AuthResult handleAuthResponse(HttpResponse<String> response, String username, boolean rememberMe) {
        if (response.statusCode() == 200) {
            try {
                JsonNode json = objectMapper.readTree(response.body());
                String accessToken = json.get("accessToken").asText();
                String refreshToken = json.has("refreshToken") ? json.get("refreshToken").asText() : null;
                long expiresIn = json.has("expiresIn") ? json.get("expiresIn").asLong() : 900000;

                tokenStorage.storeTokens(accessToken, refreshToken, expiresIn, username, rememberMe);
                
                // Schedule automatic token refresh
                scheduleTokenRefresh(expiresIn);

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
    
    // Overload for backward compatibility
    private AuthResult handleAuthResponse(HttpResponse<String> response, String username) {
        return handleAuthResponse(response, username, false);
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
