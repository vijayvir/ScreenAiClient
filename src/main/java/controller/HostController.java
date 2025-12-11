package controller;

import service.ServerConnectionService;
import service.ScreenCaptureService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Host Controller
 * Manages presenter/host role - screen capture and streaming
 * Note: Instantiated manually, not as Spring bean
 */
public class HostController {
    private ServerConnectionService serverConnection;
    private ScreenCaptureService screenCaptureService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private final ScheduledExecutorService metricsExecutor = Executors.newScheduledThreadPool(1);

    private boolean isStreaming = false;
    private String roomId;
    private int viewerCount = 0;
    private long frameCount = 0;
    private long startTime = 0;

    // Callbacks
    private final Consumer<String> onStatusUpdate;
    private final Consumer<String> onPerformanceUpdate;
    private final Consumer<Integer> onViewerCountUpdate;
    private final Consumer<Boolean> onConnectionStatusUpdate;
    public HostController(Consumer<String> statusCallback,
                         Consumer<String> performanceCallback,
                         Consumer<Integer> viewerCountCallback,
                         Consumer<Boolean> connectionCallback) {
        this.onStatusUpdate = statusCallback;
        this.onPerformanceUpdate = performanceCallback;
        this.onViewerCountUpdate = viewerCountCallback;
        this.onConnectionStatusUpdate = connectionCallback;
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
        if (serverConnection == null || !serverConnection.isConnected()) {
            onStatusUpdate.accept("⚠️ Not connected to server");
            return;
        }

        // Generate or use provided room ID
        roomId = (customRoomId != null && !customRoomId.isEmpty()) ?
                 customRoomId :
                 "room-" + UUID.randomUUID().toString().substring(0, 8);

        // Create room on server
        String createRoomMsg = String.format(
            "{\"type\":\"create-room\",\"roomId\":\"%s\"}",
            roomId
        );
        serverConnection.sendText(createRoomMsg);
        onStatusUpdate.accept("📍 Creating room: " + roomId);
    }

    /**
     * Stop screen sharing
     */
    public void stopStreaming() {
        if (!isStreaming) {
            return;
        }

        isStreaming = false;

        if (screenCaptureService != null) {
            screenCaptureService.stop();
        }

        if (serverConnection != null) {
            serverConnection.sendText("{\"type\":\"leave-room\"}");
        }

        metricsExecutor.shutdown();
        onStatusUpdate.accept("⏹ Streaming stopped");
        viewerCount = 0;
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
    private void onServerConnected() {
        System.out.println("✅ Host connected to server");
        onStatusUpdate.accept("✅ Connected to server");
        onConnectionStatusUpdate.accept(true);
    }

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
        onStatusUpdate.accept("✅ Room created: " + roomId);
        startScreenCapture();
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

            onStatusUpdate.accept("🎥 Streaming started");

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
}

