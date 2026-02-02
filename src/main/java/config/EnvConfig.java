package config;

import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Environment configuration loader for the ScreenAI Client.
 * Loads configuration from .env file and provides type-safe access to settings.
 */
public class EnvConfig {

    private static final Logger log = LoggerFactory.getLogger(EnvConfig.class);
    
    private static EnvConfig instance;
    private final Dotenv dotenv;

    // Server connection
    private final String serverUrl;
    private final String httpUrl;
    
    // Reconnection settings
    private final int reconnectAttempts;
    private final int reconnectDelayMs;
    private final int connectionTimeoutMs;
    
    // Video settings
    private final int videoFrameRate;
    private final int videoBitrateKbps;
    private final String videoQuality;
    
    // Buffer sizes
    private final int maxTextMessageBufferSize;
    private final int maxBinaryMessageBufferSize;
    private final int decoderMaxBufferSize;
    private final int decoderMaxBufferTimeMs;
    
    // Performance monitoring
    private final boolean monitorCpu;
    private final boolean monitorMemory;
    private final boolean monitorNetwork;
    
    // HTTP client settings
    private final int httpConnectTimeoutSeconds;
    private final int httpRequestTimeoutSeconds;
    
    // Security
    private final String tokenEncryptionKey;
    private final String credentialsStorageDir;

    private EnvConfig() {
        log.info("Loading environment configuration from .env file...");
        
        // Load .env file, ignore if missing (use defaults)
        this.dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();
        
        // Server connection
        this.serverUrl = getEnv("SCREENAI_SERVER_URL", "ws://localhost:8080/screenshare");
        this.httpUrl = getEnv("SCREENAI_HTTP_URL", "http://localhost:8080");
        
        // Reconnection settings
        this.reconnectAttempts = getEnvInt("RECONNECT_ATTEMPTS", 3);
        this.reconnectDelayMs = getEnvInt("RECONNECT_DELAY_MS", 5000);
        this.connectionTimeoutMs = getEnvInt("CONNECTION_TIMEOUT_MS", 10000);
        
        // Video settings
        this.videoFrameRate = getEnvInt("VIDEO_FRAME_RATE", 30);
        this.videoBitrateKbps = getEnvInt("VIDEO_BITRATE_KBPS", 2500);
        this.videoQuality = getEnv("VIDEO_QUALITY", "high");
        
        // Buffer sizes
        this.maxTextMessageBufferSize = getEnvInt("MAX_TEXT_MESSAGE_BUFFER_SIZE", 131072);
        this.maxBinaryMessageBufferSize = getEnvInt("MAX_BINARY_MESSAGE_BUFFER_SIZE", 10485760);
        this.decoderMaxBufferSize = getEnvInt("DECODER_MAX_BUFFER_SIZE", 500000);
        this.decoderMaxBufferTimeMs = getEnvInt("DECODER_MAX_BUFFER_TIME_MS", 200);
        
        // Performance monitoring
        this.monitorCpu = getEnvBoolean("MONITOR_CPU", true);
        this.monitorMemory = getEnvBoolean("MONITOR_MEMORY", true);
        this.monitorNetwork = getEnvBoolean("MONITOR_NETWORK", true);
        
        // HTTP client settings
        this.httpConnectTimeoutSeconds = getEnvInt("HTTP_CONNECT_TIMEOUT_SECONDS", 10);
        this.httpRequestTimeoutSeconds = getEnvInt("HTTP_REQUEST_TIMEOUT_SECONDS", 30);
        
        // Security
        this.tokenEncryptionKey = getEnv("TOKEN_ENCRYPTION_KEY", "dev-encryption-key-32-chars-long!");
        this.credentialsStorageDir = getEnv("CREDENTIALS_STORAGE_DIR", "~/.screenai")
            .replace("~", System.getProperty("user.home"));
        
        log.info("Environment configuration loaded successfully");
        log.info("Server URL: {}", serverUrl);
        log.info("HTTP URL: {}", httpUrl);
    }

    /**
     * Get the singleton instance of EnvConfig
     */
    public static synchronized EnvConfig getInstance() {
        if (instance == null) {
            instance = new EnvConfig();
        }
        return instance;
    }

    /**
     * Reload configuration from .env file
     */
    public static synchronized void reload() {
        instance = null;
        getInstance();
    }

    // Helper methods for type-safe environment variable access
    private String getEnv(String key, String defaultValue) {
        String value = dotenv.get(key);
        return (value != null && !value.isEmpty()) ? value : defaultValue;
    }

    private int getEnvInt(String key, int defaultValue) {
        try {
            String value = dotenv.get(key);
            return (value != null && !value.isEmpty()) ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    private boolean getEnvBoolean(String key, boolean defaultValue) {
        String value = dotenv.get(key);
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    // ==================== Getters ====================

    // Server connection
    public String getServerUrl() {
        return serverUrl;
    }

    public String getHttpUrl() {
        return httpUrl;
    }
    
    public String getAuthBaseUrl() {
        return httpUrl + "/api/auth";
    }

    // Reconnection settings
    public int getReconnectAttempts() {
        return reconnectAttempts;
    }

    public int getReconnectDelayMs() {
        return reconnectDelayMs;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    // Video settings
    public int getVideoFrameRate() {
        return videoFrameRate;
    }

    public int getVideoBitrateKbps() {
        return videoBitrateKbps;
    }

    public String getVideoQuality() {
        return videoQuality;
    }

    // Buffer sizes
    public int getMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }

    public int getMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }

    public int getDecoderMaxBufferSize() {
        return decoderMaxBufferSize;
    }

    public int getDecoderMaxBufferTimeMs() {
        return decoderMaxBufferTimeMs;
    }

    // Performance monitoring
    public boolean isMonitorCpu() {
        return monitorCpu;
    }

    public boolean isMonitorMemory() {
        return monitorMemory;
    }

    public boolean isMonitorNetwork() {
        return monitorNetwork;
    }

    // HTTP client settings
    public int getHttpConnectTimeoutSeconds() {
        return httpConnectTimeoutSeconds;
    }

    public int getHttpRequestTimeoutSeconds() {
        return httpRequestTimeoutSeconds;
    }

    // Security
    public String getTokenEncryptionKey() {
        return tokenEncryptionKey;
    }

    public String getCredentialsStorageDir() {
        return credentialsStorageDir;
    }
}
