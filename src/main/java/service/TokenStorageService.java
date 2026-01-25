package service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Secure token storage for client-side authentication.
 * Stores tokens in user's app data directory with basic encryption.
 * 
 * Note: For production, consider using platform-specific secure storage:
 * - Windows: Credential Manager or DPAPI
 * - macOS: Keychain
 * - Linux: Secret Service / Keyring
 */
public class TokenStorageService {

    private static final Logger log = LoggerFactory.getLogger(TokenStorageService.class);
    private static final String TOKEN_FILE = "screenai_tokens.dat";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Path storagePath;
    
    // Cached tokens
    private volatile String accessToken;
    private volatile String refreshToken;
    private volatile Instant accessTokenExpiresAt;
    private volatile String username;

    public TokenStorageService() {
        // Store in user's home directory under .screenai
        String userHome = System.getProperty("user.home");
        Path baseDir = Path.of(userHome, ".screenai");
        
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            log.error("Failed to create storage directory: {}", e.getMessage());
        }
        
        this.storagePath = baseDir.resolve(TOKEN_FILE);
        
        // Load stored tokens on startup
        loadTokens();
    }

    // ==================== Token Management ====================

    /**
     * Store authentication tokens after successful login.
     */
    public void storeTokens(String accessToken, String refreshToken, long expiresInMs, String username) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.accessTokenExpiresAt = Instant.now().plusMillis(expiresInMs - 60000); // Subtract 1 minute buffer
        this.username = username;
        
        saveTokens();
        log.info("Tokens stored for user: {}", username);
    }

    /**
     * Update access token after refresh.
     */
    public void updateAccessToken(String newAccessToken, long expiresInMs) {
        this.accessToken = newAccessToken;
        this.accessTokenExpiresAt = Instant.now().plusMillis(expiresInMs - 60000);
        
        saveTokens();
        log.debug("Access token refreshed");
    }

    /**
     * Clear all stored tokens (logout).
     */
    public void clearTokens() {
        this.accessToken = null;
        this.refreshToken = null;
        this.accessTokenExpiresAt = null;
        this.username = null;
        
        try {
            Files.deleteIfExists(storagePath);
            log.info("Tokens cleared");
        } catch (IOException e) {
            log.error("Failed to delete token file: {}", e.getMessage());
        }
    }

    // ==================== Token Retrieval ====================

    /**
     * Get current access token if still valid.
     */
    public Optional<String> getAccessToken() {
        if (accessToken == null || isAccessTokenExpired()) {
            return Optional.empty();
        }
        return Optional.of(accessToken);
    }

    /**
     * Get refresh token for token renewal.
     */
    public Optional<String> getRefreshToken() {
        return Optional.ofNullable(refreshToken);
    }

    /**
     * Get the authenticated username.
     */
    public Optional<String> getUsername() {
        return Optional.ofNullable(username);
    }

    /**
     * Check if access token is expired.
     */
    public boolean isAccessTokenExpired() {
        return accessTokenExpiresAt == null || Instant.now().isAfter(accessTokenExpiresAt);
    }

    /**
     * Check if user is logged in (has valid or refreshable token).
     */
    public boolean isLoggedIn() {
        return (accessToken != null && !isAccessTokenExpired()) || refreshToken != null;
    }

    /**
     * Check if we have tokens that might need refresh.
     */
    public boolean needsRefresh() {
        return accessToken != null && isAccessTokenExpired() && refreshToken != null;
    }

    // ==================== Persistence ====================

    /**
     * Save tokens to file with basic encoding.
     */
    private void saveTokens() {
        try {
            Map<String, Object> data = Map.of(
                    "accessToken", accessToken != null ? accessToken : "",
                    "refreshToken", refreshToken != null ? refreshToken : "",
                    "expiresAt", accessTokenExpiresAt != null ? accessTokenExpiresAt.toEpochMilli() : 0,
                    "username", username != null ? username : ""
            );
            
            String json = objectMapper.writeValueAsString(data);
            String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
            
            Files.writeString(storagePath, encoded, 
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            
        } catch (IOException e) {
            log.error("Failed to save tokens: {}", e.getMessage());
        }
    }

    /**
     * Load tokens from file.
     */
    @SuppressWarnings("unchecked")
    private void loadTokens() {
        if (!Files.exists(storagePath)) {
            return;
        }
        
        try {
            String encoded = Files.readString(storagePath);
            String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            
            String loadedAccessToken = (String) data.get("accessToken");
            String loadedRefreshToken = (String) data.get("refreshToken");
            Object expiresAtObj = data.get("expiresAt");
            String loadedUsername = (String) data.get("username");
            
            if (loadedAccessToken != null && !loadedAccessToken.isEmpty()) {
                this.accessToken = loadedAccessToken;
            }
            if (loadedRefreshToken != null && !loadedRefreshToken.isEmpty()) {
                this.refreshToken = loadedRefreshToken;
            }
            if (expiresAtObj != null) {
                long expiresAtMs = ((Number) expiresAtObj).longValue();
                if (expiresAtMs > 0) {
                    this.accessTokenExpiresAt = Instant.ofEpochMilli(expiresAtMs);
                }
            }
            if (loadedUsername != null && !loadedUsername.isEmpty()) {
                this.username = loadedUsername;
            }
            
            log.info("Loaded stored tokens for user: {}", username);
            
        } catch (IOException e) {
            log.error("Failed to load tokens: {}", e.getMessage());
        }
    }

    // ==================== Utility ====================

    /**
     * Parse expiration time from JWT token (without validation).
     * This is for display purposes only.
     */
    public static long parseExpirationFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return 0;
            }
            
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<?, ?> claims = new ObjectMapper().readValue(payload, Map.class);
            
            Object exp = claims.get("exp");
            if (exp instanceof Number) {
                return ((Number) exp).longValue() * 1000; // Convert seconds to milliseconds
            }
        } catch (Exception e) {
            log.debug("Failed to parse JWT: {}", e.getMessage());
        }
        return 0;
    }
}
