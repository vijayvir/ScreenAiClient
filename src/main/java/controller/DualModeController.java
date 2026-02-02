package controller;

import config.EnvConfig;
import service.ServerConnectionService;
import service.ScreenCaptureService;
import service.H264DecoderService;
import service.AuthenticationService;
import service.TokenStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.image.Image;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Dual Mode Controller
 * Supports bidirectional screen sharing - user can be both host and viewer simultaneously
 * Includes authentication integration with automatic token refresh
 */
public class DualModeController {
    
    // Authentication services
    private final TokenStorageService tokenStorage;
    private final AuthenticationService authService;
    private boolean isAuthenticated = false;
    
    // Connection
    private ServerConnectionService serverConnection;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    
    // Host functionality
    private ScreenCaptureService screenCaptureService;
    private ScheduledExecutorService hostMetricsExecutor;
    private boolean isHosting = false;
    private String hostRoomId;
    private int viewerCount = 0;
    private long framesSent = 0;
    private long hostStartTime = 0;
    
    // Viewer functionality
    private H264DecoderService decoderService;
    private boolean isViewing = false;
    private String viewingRoomId;
    private long framesReceived = 0;
    private long bytesReceived = 0;
    private long viewerStartTime = 0;
    
    // Connection state
    private boolean isConnected = false;
    private String serverHost;
    private int serverPort;
    
    // Callbacks - General
    private Consumer<String> onStatusUpdate;
    private Consumer<Boolean> onConnectionStatusUpdate;
    
    // Callbacks - Host
    private Consumer<String> onHostPerformanceUpdate;
    private Consumer<Integer> onViewerCountUpdate;
    private Consumer<Boolean> onHostingStateUpdate;
    private Consumer<String> onRoomCreated;
    private Consumer<String> onAccessCodeReceived;  // New: callback for access code display
    
    // Host room security info
    private String accessCode;
    
    // Callbacks - Viewer
    private Consumer<Image> onFrameReceived;
    private Consumer<String> onViewerPerformanceUpdate;
    private Consumer<Boolean> onViewingStateUpdate;
    
    // Callbacks - Authentication
    private Consumer<String> onAuthenticationRequired;
    private Consumer<String> onAuthenticationSuccess;

    public DualModeController() {
        System.out.println("🔄 DualModeController created - Bidirectional streaming enabled");
        
        // Initialize authentication services
        this.tokenStorage = new TokenStorageService();
        this.authService = new AuthenticationService(tokenStorage);
        
        // Set up callback for when re-authentication is needed
        this.authService.setOnAuthenticationRequired(message -> {
            Platform.runLater(() -> {
                if (onAuthenticationRequired != null) {
                    onAuthenticationRequired.accept(message);
                }
            });
        });
    }

    // ==================== Initialization ====================
    
    /**
     * Initialize with all callbacks
     */
    public void initialize(
            // General
            Consumer<String> statusCallback,
            Consumer<Boolean> connectionCallback,
            // Host
            Consumer<String> hostPerformanceCallback,
            Consumer<Integer> viewerCountCallback,
            Consumer<Boolean> hostingStateCallback,
            Consumer<String> roomCreatedCallback,
            // Viewer
            Consumer<Image> frameCallback,
            Consumer<String> viewerPerformanceCallback,
            Consumer<Boolean> viewingStateCallback,
            // Authentication
            Consumer<String> authRequiredCallback,
            Consumer<String> authSuccessCallback
    ) {
        this.onStatusUpdate = statusCallback;
        this.onConnectionStatusUpdate = connectionCallback;
        this.onHostPerformanceUpdate = hostPerformanceCallback;
        this.onViewerCountUpdate = viewerCountCallback;
        this.onHostingStateUpdate = hostingStateCallback;
        this.onRoomCreated = roomCreatedCallback;
        this.onFrameReceived = frameCallback;
        this.onViewerPerformanceUpdate = viewerPerformanceCallback;
        this.onViewingStateUpdate = viewingStateCallback;
        this.onAuthenticationRequired = authRequiredCallback;
        this.onAuthenticationSuccess = authSuccessCallback;
        
        System.out.println("✅ DualModeController initialized with all callbacks");
    }
    
    // ==================== Authentication ====================
    
    /**
     * Try to auto-login using persisted credentials (remember-me).
     * Returns CompletableFuture that completes when authentication check is done.
     */
    public CompletableFuture<Boolean> tryAutoLogin() {
        if (tokenStorage.hasPersistedCredentials()) {
            updateStatus("🔐 Found saved credentials, attempting auto-login...");
            
            return authService.tryAutoLogin().thenApply(result -> {
                if (result.success()) {
                    isAuthenticated = true;
                    String username = tokenStorage.getUsername().orElse("User");
                    Platform.runLater(() -> {
                        updateStatus("✅ Welcome back, " + username + "!");
                        if (onAuthenticationSuccess != null) {
                            onAuthenticationSuccess.accept(username);
                        }
                    });
                    return true;
                } else {
                    updateStatus("🔐 Please login to continue");
                    return false;
                }
            });
        }
        
        updateStatus("🔐 Please login to continue");
        return CompletableFuture.completedFuture(false);
    }
    
    /**
     * Login with credentials.
     */
    public CompletableFuture<AuthenticationService.AuthResult> login(String username, String password, boolean rememberMe) {
        updateStatus("🔐 Logging in...");
        
        return authService.login(username, password, rememberMe).thenApply(result -> {
            Platform.runLater(() -> {
                if (result.success()) {
                    isAuthenticated = true;
                    updateStatus("✅ Login successful!");
                    if (onAuthenticationSuccess != null) {
                        onAuthenticationSuccess.accept(username);
                    }
                } else {
                    updateStatus("❌ Login failed: " + result.message());
                }
            });
            return result;
        });
    }
    
    /**
     * Logout the current user.
     */
    public CompletableFuture<Void> logout() {
        updateStatus("🔐 Logging out...");
        
        // Disconnect from server first
        if (isConnected) {
            disconnect();
        }
        
        return authService.logout().thenAccept(result -> {
            isAuthenticated = false;
            Platform.runLater(() -> {
                updateStatus("🔐 Logged out. Please login to continue.");
                if (onAuthenticationRequired != null) {
                    onAuthenticationRequired.accept("Logged out");
                }
            });
        });
    }
    
    /**
     * Check if user is authenticated.
     */
    public boolean isAuthenticated() {
        return isAuthenticated && authService.isAuthenticated();
    }
    
    /**
     * Get current username.
     */
    public Optional<String> getCurrentUsername() {
        return tokenStorage.getUsername();
    }
    
    /**
     * Get the authentication service.
     */
    public AuthenticationService getAuthService() {
        return authService;
    }
    
    /**
     * Get the token storage service.
     */
    public TokenStorageService getTokenStorage() {
        return tokenStorage;
    }
    
    /**
     * Set callback for access code received (for password-protected rooms)
     */
    public void setOnAccessCodeReceived(Consumer<String> callback) {
        this.onAccessCodeReceived = callback;
    }
    
    /**
     * Get the current room access code (if any)
     */
    public String getAccessCode() {
        return accessCode;
    }

    // ==================== Connection Management ====================
    
    /**
     * Connect to server (requires authentication)
     */
    public void connect(String serverHost, int serverPort) {
        // Check authentication first
        if (!isAuthenticated()) {
            updateStatus("⚠️ Please login before connecting");
            if (onAuthenticationRequired != null) {
                onAuthenticationRequired.accept("Authentication required to connect");
            }
            return;
        }
        
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        
        System.out.println("🔌 [DUAL] Connecting to " + serverHost + ":" + serverPort);
        updateStatus("Connecting to server...");
        
        try {
            // Get the access token for WebSocket authentication
            Optional<String> tokenOpt = tokenStorage.getAccessToken();
            if (tokenOpt.isEmpty()) {
                // Try to refresh token first
                authService.getValidAccessToken().thenAccept(refreshedToken -> {
                    if (refreshedToken.isPresent()) {
                        Platform.runLater(() -> connectWithToken(serverHost, serverPort, refreshedToken.get()));
                    } else {
                        Platform.runLater(() -> {
                            updateStatus("❌ Authentication expired. Please login again.");
                            if (onAuthenticationRequired != null) {
                                onAuthenticationRequired.accept("Session expired");
                            }
                        });
                    }
                });
                return;
            }
            
            connectWithToken(serverHost, serverPort, tokenOpt.get());
            
        } catch (Exception e) {
            System.err.println("❌ [DUAL] Error: " + e.getMessage());
            updateStatus("❌ Error: " + e.getMessage());
        }
    }
    
    /**
     * Internal method to connect with a valid token
     */
    private void connectWithToken(String serverHost, int serverPort, String token) {
        try {
            // Use EnvConfig for server URL or construct from parameters
            String serverUrl = "ws://" + serverHost + ":" + serverPort + "/screenshare";
            serverConnection = new ServerConnectionService(serverUrl);
            
            // Set the authentication token
            serverConnection.setAuthToken(token);
            
            // Set up handlers
            serverConnection.setConnectionOpenHandler(this::onConnected);
            serverConnection.setConnectionClosedHandler(this::onDisconnected);
            serverConnection.setTextMessageHandler(this::handleTextMessage);
            serverConnection.setBinaryMessageHandler(this::handleBinaryMessage);
            
            // Connect in background
            executorService.submit(() -> {
                try {
                    serverConnection.connect();
                } catch (Exception e) {
                    System.err.println("❌ [DUAL] Connection error: " + e.getMessage());
                    Platform.runLater(() -> {
                        updateStatus("❌ Connection failed: " + e.getMessage());
                        if (onConnectionStatusUpdate != null) {
                            onConnectionStatusUpdate.accept(false);
                        }
                    });
                }
            });
            
        } catch (Exception e) {
            System.err.println("❌ [DUAL] Error: " + e.getMessage());
            updateStatus("❌ Error: " + e.getMessage());
        }
    }
    
    /**
     * Disconnect from server
     */
    public void disconnect() {
        System.out.println("🔌 [DUAL] Disconnecting...");
        
        // Stop hosting if active
        if (isHosting) {
            stopHosting();
        }
        
        // Stop viewing if active
        if (isViewing) {
            stopViewing();
        }
        
        // Disconnect from server
        if (serverConnection != null) {
            serverConnection.disconnect();
            serverConnection = null;
        }
        
        isConnected = false;
        if (onConnectionStatusUpdate != null) {
            onConnectionStatusUpdate.accept(false);
        }
        updateStatus("Disconnected");
    }
    
    private void onConnected() {
        System.out.println("✅ [DUAL] Connected to server");
        isConnected = true;
        Platform.runLater(() -> {
            updateStatus("✅ Connected to server");
            if (onConnectionStatusUpdate != null) {
                onConnectionStatusUpdate.accept(true);
            }
        });
    }
    
    private void onDisconnected() {
        System.out.println("🔌 [DUAL] Disconnected from server");
        isConnected = false;
        isHosting = false;
        isViewing = false;
        
        Platform.runLater(() -> {
            updateStatus("Disconnected from server");
            if (onConnectionStatusUpdate != null) {
                onConnectionStatusUpdate.accept(false);
            }
            if (onHostingStateUpdate != null) {
                onHostingStateUpdate.accept(false);
            }
            if (onViewingStateUpdate != null) {
                onViewingStateUpdate.accept(false);
            }
        });
    }

    // ==================== HOST Functions ====================
    
    /**
     * Start hosting (screen sharing)
     */
    public void startHosting(String customRoomId) {
        if (!isConnected) {
            updateStatus("⚠️ Not connected to server");
            return;
        }
        
        if (isHosting) {
            updateStatus("⚠️ Already hosting");
            return;
        }
        
        // Generate room ID
        hostRoomId = (customRoomId != null && !customRoomId.isEmpty()) ?
                customRoomId :
                "room-" + UUID.randomUUID().toString().substring(0, 8);
        
        // Create room on server
        String createRoomMsg = String.format(
                "{\"type\":\"create-room\",\"roomId\":\"%s\"}",
                hostRoomId
        );
        serverConnection.sendText(createRoomMsg);
        updateStatus("📍 Creating room: " + hostRoomId);
    }
    
    /**
     * Stop hosting
     */
    public void stopHosting() {
        System.out.println("🛑 [DUAL] Stopping hosting...");
        
        if (!isHosting) {
            return;
        }
        
        isHosting = false;
        
        // Stop screen capture
        if (screenCaptureService != null) {
            screenCaptureService.stop();
            screenCaptureService = null;
        }
        
        // Send leave room
        if (serverConnection != null && serverConnection.isConnected()) {
            serverConnection.sendText("{\"type\":\"leave-room\",\"role\":\"host\"}");
        }
        
        // Stop metrics
        if (hostMetricsExecutor != null && !hostMetricsExecutor.isShutdown()) {
            hostMetricsExecutor.shutdown();
        }
        
        viewerCount = 0;
        framesSent = 0;
        
        Platform.runLater(() -> {
            if (onHostingStateUpdate != null) {
                onHostingStateUpdate.accept(false);
            }
            if (onViewerCountUpdate != null) {
                onViewerCountUpdate.accept(0);
            }
            updateStatus(isViewing ? "🎥 Viewing: " + viewingRoomId : "⏹ Hosting stopped");
        });
        
        System.out.println("✅ [DUAL] Hosting stopped");
    }
    
    private void startScreenCapture() {
        try {
            isHosting = true;
            framesSent = 0;
            hostStartTime = System.currentTimeMillis();
            
            screenCaptureService = new ScreenCaptureService(this::sendVideoFrame);
            screenCaptureService.start();
            
            Platform.runLater(() -> {
                updateStatus("🎥 Hosting room: " + hostRoomId + (isViewing ? " | Viewing: " + viewingRoomId : ""));
                if (onHostingStateUpdate != null) {
                    onHostingStateUpdate.accept(true);
                }
                if (onRoomCreated != null) {
                    onRoomCreated.accept(hostRoomId);
                }
            });
            
            // Start metrics
            hostMetricsExecutor = Executors.newScheduledThreadPool(1);
            startHostMetrics();
            
        } catch (Exception e) {
            System.err.println("❌ [DUAL] Failed to start screen capture: " + e.getMessage());
            isHosting = false;
            updateStatus("❌ Failed to start hosting: " + e.getMessage());
        }
    }
    
    private void sendVideoFrame(byte[] frameData) {
        if (isHosting && serverConnection != null && serverConnection.isConnected()) {
            framesSent++;
            serverConnection.sendBinary(frameData);
        }
    }
    
    private void startHostMetrics() {
        hostMetricsExecutor.scheduleAtFixedRate(() -> {
            if (isHosting && framesSent > 0) {
                long elapsed = (System.currentTimeMillis() - hostStartTime) / 1000;
                if (elapsed > 0) {
                    double fps = framesSent / (double) elapsed;
                    String metrics = String.format("📤 FPS: %.1f | Viewers: %d | Frames: %d", 
                            fps, viewerCount, framesSent);
                    Platform.runLater(() -> {
                        if (onHostPerformanceUpdate != null) {
                            onHostPerformanceUpdate.accept(metrics);
                        }
                    });
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    // ==================== VIEWER Functions ====================
    
    /**
     * Start viewing a room
     */
    public void startViewing(String roomId) {
        if (!isConnected) {
            updateStatus("⚠️ Not connected to server");
            return;
        }
        
        if (isViewing) {
            updateStatus("⚠️ Already viewing. Stop first to switch rooms.");
            return;
        }
        
        if (roomId == null || roomId.trim().isEmpty()) {
            updateStatus("⚠️ Please enter a room ID");
            return;
        }
        
        // Trim and clean up room ID (remove any accidental spaces)
        String cleanRoomId = roomId.trim().replaceAll("\\s+", "");
        System.out.println("👁️ [DUAL] Attempting to join room: '" + cleanRoomId + "'");
        
        // Cannot view own room
        if (cleanRoomId.equals(hostRoomId) && isHosting) {
            updateStatus("⚠️ Cannot view your own room");
            return;
        }
        
        viewingRoomId = cleanRoomId;
        
        // Join room as viewer
        String joinRoomMsg = String.format(
                "{\"type\":\"join-room\",\"roomId\":\"%s\"}",
                cleanRoomId
        );
        serverConnection.sendText(joinRoomMsg);
        updateStatus("🔍 Joining room: " + cleanRoomId);
    }
    
    /**
     * Stop viewing
     */
    public void stopViewing() {
        System.out.println("🛑 [DUAL] Stopping viewing...");
        
        if (!isViewing) {
            return;
        }
        
        isViewing = false;
        
        // Stop decoder
        if (decoderService != null) {
            decoderService.cleanup();
            decoderService = null;
        }
        
        // Leave room
        if (serverConnection != null && serverConnection.isConnected()) {
            serverConnection.sendText("{\"type\":\"leave-room\",\"role\":\"viewer\"}");
        }
        
        framesReceived = 0;
        bytesReceived = 0;
        
        Platform.runLater(() -> {
            if (onViewingStateUpdate != null) {
                onViewingStateUpdate.accept(false);
            }
            updateStatus(isHosting ? "🎥 Hosting: " + hostRoomId : "⏹ Viewing stopped");
        });
        
        System.out.println("✅ [DUAL] Viewing stopped");
    }
    
    private void initializeDecoder() {
        try {
            if (decoderService != null) {
                decoderService.cleanup();
            }
            
            decoderService = new H264DecoderService();
            decoderService.initialize(frame -> {
                if (frame != null) {
                    framesReceived++;
                    Platform.runLater(() -> {
                        if (onFrameReceived != null) {
                            onFrameReceived.accept(frame);
                        }
                    });
                }
            });
            
            System.out.println("✅ [DUAL] Decoder initialized for viewing");
            
        } catch (Exception e) {
            System.err.println("❌ [DUAL] Failed to initialize decoder: " + e.getMessage());
        }
    }

    // ==================== Message Handling ====================
    
    private void handleTextMessage(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String type = json.has("type") ? json.get("type").asText() : "";
            
            System.out.println("📨 [DUAL] Message: " + type);
            
            switch (type) {
                case "connected":
                    System.out.println("✅ [DUAL] Server confirmed connection");
                    break;
                    
                // Host messages
                case "room-created":
                    handleRoomCreated(json);
                    break;
                case "viewer-joined":
                    handleViewerJoined(json);
                    break;
                case "viewer-left":
                    handleViewerLeft(json);
                    break;
                case "viewer-count":
                    handleViewerCount(json);
                    break;
                    
                // Viewer messages
                case "room-joined":
                    handleRoomJoined(json);
                    break;
                case "presenter-joined":
                    handlePresenterJoined(json);
                    break;
                case "presenter-left":
                    handlePresenterLeft(json);
                    break;
                    
                // Common
                case "room-left":
                    System.out.println("✅ [DUAL] Left room");
                    break;
                case "error":
                    handleError(json);
                    break;
                    
                default:
                    System.out.println("⚠️ [DUAL] Unknown message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("❌ [DUAL] Error parsing message: " + e.getMessage());
        }
    }
    
    private void handleBinaryMessage(byte[] data) {
        if (!isViewing || data == null || data.length == 0) {
            return;
        }
        
        bytesReceived += data.length;
        
        if (decoderService != null && decoderService.isInitialized()) {
            decoderService.decodeFrame(data);
        }
        
        // Update viewer metrics periodically
        if (bytesReceived % 500000 < data.length) { // Every ~500KB
            long elapsed = (System.currentTimeMillis() - viewerStartTime) / 1000;
            if (elapsed > 0) {
                double mbReceived = bytesReceived / (1024.0 * 1024.0);
                double fps = framesReceived / (double) elapsed;
                String metrics = String.format("📥 %.1f MB | %d frames | %.1f FPS", 
                        mbReceived, framesReceived, fps);
                Platform.runLater(() -> {
                    if (onViewerPerformanceUpdate != null) {
                        onViewerPerformanceUpdate.accept(metrics);
                    }
                });
            }
        }
    }
    
    // Host message handlers
    private void handleRoomCreated(JsonNode json) {
        System.out.println("✅ [DUAL] Room created: " + hostRoomId);
        
        // Extract access code if provided (for password-protected rooms)
        if (json.has("accessCode")) {
            this.accessCode = json.get("accessCode").asText();
            System.out.println("🔑 [DUAL] Access code received: " + accessCode);
            
            // Notify UI to display access code
            Platform.runLater(() -> {
                if (onAccessCodeReceived != null) {
                    onAccessCodeReceived.accept(accessCode);
                }
            });
        } else {
            this.accessCode = null;
            // Clear access code display for non-protected rooms
            Platform.runLater(() -> {
                if (onAccessCodeReceived != null) {
                    onAccessCodeReceived.accept(null);
                }
            });
        }
        
        startScreenCapture();
    }
    
    private void handleViewerJoined(JsonNode json) {
        viewerCount++;
        Platform.runLater(() -> {
            if (onViewerCountUpdate != null) {
                onViewerCountUpdate.accept(viewerCount);
            }
        });
        System.out.println("👁️ [DUAL] Viewer joined - Total: " + viewerCount);
    }
    
    private void handleViewerLeft(JsonNode json) {
        viewerCount = Math.max(0, viewerCount - 1);
        Platform.runLater(() -> {
            if (onViewerCountUpdate != null) {
                onViewerCountUpdate.accept(viewerCount);
            }
        });
        System.out.println("👁️ [DUAL] Viewer left - Total: " + viewerCount);
    }
    
    private void handleViewerCount(JsonNode json) {
        viewerCount = json.has("count") ? json.get("count").asInt() : 0;
        Platform.runLater(() -> {
            if (onViewerCountUpdate != null) {
                onViewerCountUpdate.accept(viewerCount);
            }
        });
    }
    
    // Viewer message handlers
    private void handleRoomJoined(JsonNode json) {
        System.out.println("✅ [DUAL] Joined room as viewer: " + viewingRoomId);
        isViewing = true;
        framesReceived = 0;
        bytesReceived = 0;
        viewerStartTime = System.currentTimeMillis();
        
        // Initialize decoder
        initializeDecoder();
        
        Platform.runLater(() -> {
            if (onViewingStateUpdate != null) {
                onViewingStateUpdate.accept(true);
            }
            updateStatus((isHosting ? "🎥 Hosting: " + hostRoomId + " | " : "") + "👁️ Viewing: " + viewingRoomId);
        });
    }
    
    private void handlePresenterJoined(JsonNode json) {
        System.out.println("📺 [DUAL] Presenter joined the room");
        updateStatus("📺 Presenter connected - Stream starting...");
    }
    
    private void handlePresenterLeft(JsonNode json) {
        System.out.println("📺 [DUAL] Presenter left the room");
        Platform.runLater(() -> {
            updateStatus("⚠️ Presenter disconnected" + (isHosting ? " | Still hosting: " + hostRoomId : ""));
        });
    }
    
    private void handleError(JsonNode json) {
        String error = json.has("message") ? json.get("message").asText() : "Unknown error";
        System.err.println("❌ [DUAL] Server error: " + error);
        Platform.runLater(() -> {
            updateStatus("❌ Error: " + error);
        });
    }
    
    // ==================== Utility ====================
    
    private void updateStatus(String status) {
        if (onStatusUpdate != null) {
            Platform.runLater(() -> onStatusUpdate.accept(status));
        }
    }
    
    // ==================== Getters ====================
    
    public boolean isConnected() { return isConnected; }
    public boolean isHosting() { return isHosting; }
    public boolean isViewing() { return isViewing; }
    public String getHostRoomId() { return hostRoomId; }
    public String getViewingRoomId() { return viewingRoomId; }
    public int getViewerCount() { return viewerCount; }
}
