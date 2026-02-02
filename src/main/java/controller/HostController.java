package controller;

import service.ServerConnectionService;
import service.ScreenCaptureService;
import service.TokenStorageService;
import service.AuthenticationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Host Controller
 * Manages presenter/host role - screen capture and streaming
 * Now with authentication and room security support
 */
public class HostController {
    private ServerConnectionService serverConnection;
    private ScreenCaptureService screenCaptureService;
    private TokenStorageService tokenStorage;
    private AuthenticationService authService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private ScheduledExecutorService metricsExecutor;  // Recreated each streaming session

    private boolean isStreaming = false;
    private String roomId;
    private String roomPassword;
    private String accessCode;
    private int viewerCount = 0;
    private long frameCount = 0;
    private long startTime = 0;
    
    // Pending viewers waiting for approval
    private final List<PendingViewer> pendingViewers = new ArrayList<>();

    // Callbacks
    private final Consumer<String> onStatusUpdate;
    private final Consumer<String> onPerformanceUpdate;
    private final Consumer<Integer> onViewerCountUpdate;
    private final Consumer<Boolean> onConnectionStatusUpdate;
    private Consumer<Boolean> onStreamingStateUpdate;  // Called when streaming starts/stops
    private Consumer<PendingViewer> onViewerRequest;  // Called when viewer requests to join
    
    public HostController(Consumer<String> statusCallback,
                         Consumer<String> performanceCallback,
                         Consumer<Integer> viewerCountCallback,
                         Consumer<Boolean> connectionCallback) {
        this.onStatusUpdate = statusCallback;
        this.onPerformanceUpdate = performanceCallback;
        this.onViewerCountUpdate = viewerCountCallback;
        this.onConnectionStatusUpdate = connectionCallback;
        
        // Initialize auth services
        this.tokenStorage = new TokenStorageService();
        this.authService = new AuthenticationService(tokenStorage);
    }
    
    /**
     * Set callback for streaming state changes (for enabling/disabling stop button)
     */
    public void setOnStreamingStateUpdate(Consumer<Boolean> callback) {
        this.onStreamingStateUpdate = callback;
    }
    
    /**
     * Set callback for viewer approval requests
     */
    public void setOnViewerRequest(Consumer<PendingViewer> callback) {
        this.onViewerRequest = callback;
    }
    
    /**
     * Get the authentication service for external login handling
     */
    public AuthenticationService getAuthService() {
        return authService;
    }
    
    /**
     * Get the token storage service
     */
    public TokenStorageService getTokenStorage() {
        return tokenStorage;
    }
    
    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        return authService.isAuthenticated();
    }

    /**
     * Connect to the server as host
     */
    public void connect(String serverHost, int serverPort) {
        System.out.println("=================================================");
        System.out.println("🔌 [HOST] connect() called");
        System.out.println("=================================================");
        System.out.println("📍 serverHost: " + serverHost);
        System.out.println("📍 serverPort: " + serverPort);

        try {
            String serverUrl = "ws://" + serverHost + ":" + serverPort + "/screenshare";
            System.out.println("🌐 Full WebSocket URL: " + serverUrl);
            System.out.println("✅ URL constructed successfully");

            // Create connection service for this host
            System.out.println("🏗️ Creating ServerConnectionService...");
            serverConnection = new ServerConnectionService(serverUrl);
            System.out.println("✅ ServerConnectionService created");
            
            // Set authentication token if available
            tokenStorage.getAccessToken().ifPresent(token -> {
                serverConnection.setAuthToken(token);
                System.out.println("🔐 Auth token set for connection");
            });
            
            // Update auth service base URL
            String httpUrl = "http://" + serverHost + ":" + serverPort;
            authService.setServerBaseUrl(httpUrl);

            // Set up handlers
            System.out.println("🔧 Setting up connection handlers...");
            
            serverConnection.setConnectionOpenHandler(() -> {
                System.out.println("🎉 [HANDLER] Connection OPENED!");
                System.out.println("✅ [HOST] Connected to server!");
                onStatusUpdate.accept("✅ Connected to server");
                onConnectionStatusUpdate.accept(true);
            });

            serverConnection.setConnectionClosedHandler(() -> {
                System.out.println("🚪 [HANDLER] Connection CLOSED!");
                System.out.println("⏹️ [HOST] Disconnected from server");
                onStatusUpdate.accept("⏹️ Disconnected");
                onConnectionStatusUpdate.accept(false);
                isStreaming = false;
            });

            serverConnection.setTextMessageHandler(msg -> {
                System.out.println("📨 [HOST] Server message received: " + msg);
                handleServerMessage(msg);
            });
            
            System.out.println("✅ All handlers configured");

            // Attempt connection in background thread
            System.out.println("🚀 Starting connection in background thread...");
            
            executorService.submit(() -> {
                try {
                    System.out.println("🔄 [THREAD] Calling serverConnection.connect()...");
                    serverConnection.connect();
                    System.out.println("✅ [THREAD] serverConnection.connect() returned successfully");
                    // Connection successful - handlers will update UI
                } catch (Exception ex) {
                    System.err.println("❌ [THREAD] Connection error in background thread!");
                    System.err.println("❌ [HOST] Connection error: " + ex.getMessage());
                    System.err.println("❌ Exception type: " + ex.getClass().getName());
                    ex.printStackTrace();
                    onStatusUpdate.accept("❌ Connection failed: " + ex.getMessage());
                    onConnectionStatusUpdate.accept(false);
                }
            });
            
            System.out.println("✅ Background connection thread submitted");

        } catch (Exception e) {
            System.err.println("❌ [HOST] Exception in connect() method!");
            System.err.println("❌ [HOST] Connection error: " + e.getMessage());
            System.err.println("❌ Exception type: " + e.getClass().getName());
            e.printStackTrace();
            onStatusUpdate.accept("❌ Error: " + e.getMessage());
            onConnectionStatusUpdate.accept(false);
        }
        
        System.out.println("=================================================");
        System.out.println("🔌 [HOST] connect() method completed");
        System.out.println("=================================================");
    }

    /**
     * Start screen sharing
     */
    public void startStreaming(String serverHost, int serverPort, String customRoomId,
                               String screenSource, String encoder) {
        startStreaming(serverHost, serverPort, customRoomId, null, false, screenSource, encoder);
    }
    
    /**
     * Start screen sharing with password protection
     */
    public void startStreaming(String serverHost, int serverPort, String customRoomId, 
                               String password, boolean requireApproval,
                               String screenSource, String encoder) {
        if (serverConnection == null || !serverConnection.isConnected()) {
            onStatusUpdate.accept("⚠️ Not connected to server");
            return;
        }

        // Generate or use provided room ID
        roomId = (customRoomId != null && !customRoomId.isEmpty()) ?
                 customRoomId :
                 "room-" + UUID.randomUUID().toString().substring(0, 8);
        
        this.roomPassword = password;

        // Create room on server with optional password
        StringBuilder msgBuilder = new StringBuilder();
        msgBuilder.append("{\"type\":\"create-room\",\"roomId\":\"").append(roomId).append("\"");
        
        if (password != null && !password.isEmpty()) {
            msgBuilder.append(",\"password\":\"").append(escapeJson(password)).append("\"");
        }
        
        if (requireApproval) {
            msgBuilder.append(",\"requireApproval\":true");
        }
        
        msgBuilder.append("}");
        
        serverConnection.sendText(msgBuilder.toString());
        onStatusUpdate.accept("📍 Creating room: " + roomId + (password != null ? " 🔒" : ""));
    }
    
    /**
     * Escape JSON special characters
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    /**
     * Get the current room ID
     */
    public String getRoomId() {
        return roomId;
    }
    
    /**
     * Get the room access code (if generated)
     */
    public String getAccessCode() {
        return accessCode;
    }
    
    /**
     * Get list of pending viewers awaiting approval
     */
    public List<PendingViewer> getPendingViewers() {
        return new ArrayList<>(pendingViewers);
    }
    
    /**
     * Approve a pending viewer
     */
    public void approveViewer(String viewerSessionId) {
        if (serverConnection != null && serverConnection.isConnected()) {
            String msg = String.format("{\"type\":\"approve-viewer\",\"viewerSessionId\":\"%s\"}", viewerSessionId);
            serverConnection.sendText(msg);
            pendingViewers.removeIf(v -> v.sessionId().equals(viewerSessionId));
        }
    }
    
    /**
     * Deny a pending viewer
     */
    public void denyViewer(String viewerSessionId) {
        if (serverConnection != null && serverConnection.isConnected()) {
            String msg = String.format("{\"type\":\"deny-viewer\",\"viewerSessionId\":\"%s\"}", viewerSessionId);
            serverConnection.sendText(msg);
            pendingViewers.removeIf(v -> v.sessionId().equals(viewerSessionId));
        }
    }
    
    /**
     * Ban a viewer (they cannot rejoin)
     */
    public void banViewer(String viewerSessionId) {
        if (serverConnection != null && serverConnection.isConnected()) {
            String msg = String.format("{\"type\":\"ban-viewer\",\"viewerSessionId\":\"%s\"}", viewerSessionId);
            serverConnection.sendText(msg);
        }
    }
    
    /**
     * Kick a viewer (they can rejoin)
     */
    public void kickViewer(String viewerSessionId) {
        if (serverConnection != null && serverConnection.isConnected()) {
            String msg = String.format("{\"type\":\"kick-viewer\",\"viewerSessionId\":\"%s\"}", viewerSessionId);
            serverConnection.sendText(msg);
        }
    }

    /**
     * Stop screen sharing
     */
    public void stopStreaming() {
        System.out.println("🛑 [HOST] stopStreaming() called, isStreaming=" + isStreaming);
        
        if (!isStreaming) {
            System.out.println("⚠️ [HOST] Not streaming, nothing to stop");
            return;
        }

        isStreaming = false;

        // Stop screen capture first
        if (screenCaptureService != null) {
            System.out.println("🛑 [HOST] Stopping screen capture service...");
            screenCaptureService.stop();
            screenCaptureService = null;
            System.out.println("✅ [HOST] Screen capture service stopped");
        }

        // Send leave room message
        if (serverConnection != null && serverConnection.isConnected()) {
            System.out.println("📤 [HOST] Sending leave-room message...");
            serverConnection.sendText("{\"type\":\"leave-room\"}");
        }

        // Shutdown metrics executor
        if (metricsExecutor != null && !metricsExecutor.isShutdown()) {
            metricsExecutor.shutdown();
        }
        
        // Notify that streaming has stopped (disables stop button)
        if (onStreamingStateUpdate != null) {
            onStreamingStateUpdate.accept(false);
        }
        
        onStatusUpdate.accept("⏹ Streaming stopped");
        viewerCount = 0;
        onViewerCountUpdate.accept(0);
        
        System.out.println("✅ [HOST] Streaming fully stopped");
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        stopStreaming();
        if (serverConnection != null) {
            serverConnection.disconnect();
        }
        onConnectionStatusUpdate.accept(false);
    }

    // Event Handlers
    @SuppressWarnings("unused")
    private void onServerConnected() {
        System.out.println("✅ Host connected to server");
        onStatusUpdate.accept("✅ Connected to server");
        onConnectionStatusUpdate.accept(true);
    }

    @SuppressWarnings("unused")
    private void onServerDisconnected() {
        System.out.println("❌ Host disconnected from server");
        isStreaming = false;
        onStatusUpdate.accept("❌ Disconnected from server");
        onConnectionStatusUpdate.accept(false);
    }

    private void handleServerMessage(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String type = json.get("type").asText();

            switch (type) {
                case "connected":
                    // Initial connection acknowledgment from server
                    System.out.println("✅ [HOST] Server connection confirmed: " + json.get("message").asText());
                    break;
                case "room-created":
                    onRoomCreated(json);
                    break;
                case "viewer-joined":
                    onViewerJoined(json);
                    break;
                case "viewer-count":
                    onViewerCount(json);
                    break;
                case "viewer-request":
                    onViewerRequestReceived(json);
                    break;
                case "viewer-approved":
                    onViewerApproved(json);
                    break;
                case "viewer-denied":
                    onViewerDenied(json);
                    break;
                case "viewer-banned":
                    onViewerBanned(json);
                    break;
                case "viewer-kicked":
                    onViewerKicked(json);
                    break;
                case "presenter-left":
                    onPresenterLeft(json);
                    break;
                case "room-left":
                    System.out.println("✅ [HOST] Successfully left room");
                    onStatusUpdate.accept("⏹ Room left");
                    break;
                case "error":
                    onError(json);
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error parsing message: " + e.getMessage());
        }
    }

    private void onRoomCreated(JsonNode json) {
        // Extract access code if provided
        if (json.has("accessCode")) {
            this.accessCode = json.get("accessCode").asText();
            System.out.println("🔑 Room access code: " + accessCode);
        }
        
        String statusMsg = "✅ Room created: " + roomId;
        if (roomPassword != null && !roomPassword.isEmpty()) {
            statusMsg += " 🔒 (password protected)";
        }
        if (accessCode != null) {
            statusMsg += " | Code: " + accessCode;
        }
        
        onStatusUpdate.accept(statusMsg);
        startScreenCapture();
    }
    
    private void onViewerRequestReceived(JsonNode json) {
        String viewerSessionId = json.get("viewerSessionId").asText();
        String viewerUsername = json.has("viewerUsername") ? json.get("viewerUsername").asText() : "anonymous";
        int pendingCount = json.has("pendingCount") ? json.get("pendingCount").asInt() : 0;
        
        PendingViewer pending = new PendingViewer(viewerSessionId, viewerUsername, System.currentTimeMillis());
        pendingViewers.add(pending);
        
        System.out.println("👤 Viewer request from: " + viewerUsername + " (" + pendingCount + " pending)");
        onStatusUpdate.accept("👤 Viewer request: " + viewerUsername);
        
        if (onViewerRequest != null) {
            onViewerRequest.accept(pending);
        }
    }
    
    private void onViewerApproved(JsonNode json) {
        String viewerSessionId = json.get("viewerSessionId").asText();
        pendingViewers.removeIf(v -> v.sessionId().equals(viewerSessionId));
        System.out.println("✅ Viewer approved: " + viewerSessionId);
    }
    
    private void onViewerDenied(JsonNode json) {
        String viewerSessionId = json.get("viewerSessionId").asText();
        pendingViewers.removeIf(v -> v.sessionId().equals(viewerSessionId));
        System.out.println("❌ Viewer denied: " + viewerSessionId);
    }
    
    private void onViewerBanned(JsonNode json) {
        String viewerSessionId = json.get("viewerSessionId").asText();
        int newViewerCount = json.has("viewerCount") ? json.get("viewerCount").asInt() : viewerCount;
        viewerCount = newViewerCount;
        onViewerCountUpdate.accept(viewerCount);
        System.out.println("🚫 Viewer banned: " + viewerSessionId);
        onStatusUpdate.accept("🚫 Viewer banned");
    }
    
    private void onViewerKicked(JsonNode json) {
        String viewerSessionId = json.get("viewerSessionId").asText();
        int newViewerCount = json.has("viewerCount") ? json.get("viewerCount").asInt() : viewerCount;
        viewerCount = newViewerCount;
        onViewerCountUpdate.accept(viewerCount);
        System.out.println("👢 Viewer kicked: " + viewerSessionId);
        onStatusUpdate.accept("👢 Viewer kicked");
    }

    private void onViewerJoined(JsonNode json) {
        viewerCount++;
        onViewerCountUpdate.accept(viewerCount);
        System.out.println("👁️ Viewer joined - Total viewers: " + viewerCount);
    }

    private void onViewerCount(JsonNode json) {
        viewerCount = json.get("count").asInt();
        onViewerCountUpdate.accept(viewerCount);
    }

    private void onPresenterLeft(JsonNode json) {
        onStatusUpdate.accept("⚠️ Presenter disconnected");
    }

    private void onError(JsonNode json) {
        String errorMsg = json.get("message").asText();
        onStatusUpdate.accept("❌ Server error: " + errorMsg);
    }

    private void startScreenCapture() {
        try {
            isStreaming = true;
            frameCount = 0;
            startTime = System.currentTimeMillis();

            screenCaptureService = new ScreenCaptureService(this::sendVideoFrame);
            screenCaptureService.start();

            onStatusUpdate.accept("🎥 Streaming started - Room: " + roomId);
            
            // Notify that streaming has started (enables stop button)
            if (onStreamingStateUpdate != null) {
                onStreamingStateUpdate.accept(true);
            }

            // Create new metrics executor (previous one may have been shutdown)
            metricsExecutor = Executors.newScheduledThreadPool(1);
            
            // Start metrics updater
            startMetricsUpdater();

        } catch (Exception e) {
            System.err.println("❌ Failed to start screen capture: " + e.getMessage());
            onStatusUpdate.accept("❌ Failed to start streaming: " + e.getMessage());
            isStreaming = false;
        }
    }

    private void sendVideoFrame(byte[] frameData) {
        if (isStreaming && serverConnection != null && serverConnection.isConnected()) {
            frameCount++;
            serverConnection.sendBinary(frameData);
        }
    }

    private void startMetricsUpdater() {
        metricsExecutor.scheduleAtFixedRate(() -> {
            if (isStreaming && frameCount > 0) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                if (elapsed > 0) {
                    double fps = frameCount / (double) elapsed;
                    String metrics = String.format("FPS: %.2f | Viewers: %d", fps, viewerCount);
                    onPerformanceUpdate.accept(metrics);
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    /**
     * Record for pending viewer information
     */
    public record PendingViewer(String sessionId, String username, long requestedAt) {}
}