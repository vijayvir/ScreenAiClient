package controller;

import service.ServerConnectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Viewer Controller
 * Manages viewer/watcher role - stream reception and display
 * Note: Instantiated manually, not as Spring bean
 */
public class ViewerController {
    private ServerConnectionService serverConnection;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final ScheduledExecutorService metricsExecutor = Executors.newScheduledThreadPool(1);

    private volatile boolean isConnected = false;
    private volatile boolean isWatchingStream = false;
    private String roomId;
    private long frameCount = 0;
    private long startTime = 0;
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private boolean initSegmentReceived = false;

    // Callbacks
    private final Consumer<String> onStatusUpdate;
    private final Consumer<String> onFpsUpdate;
    private final Consumer<String> onDataUpdate;
    private final Consumer<String> onVideoDisplayUpdate;
    private final Consumer<Boolean> onConnectionStatusUpdate;

    public ViewerController(Consumer<String> statusCallback,
                           Consumer<String> fpsCallback,
                           Consumer<String> dataCallback,
                           Consumer<String> displayCallback,
                           Consumer<Boolean> connectionCallback) {
        this.onStatusUpdate = statusCallback;
        this.onFpsUpdate = fpsCallback;
        this.onDataUpdate = dataCallback;
        this.onVideoDisplayUpdate = displayCallback;
        this.onConnectionStatusUpdate = connectionCallback;
    }

    /**
     * Connect to the server as viewer
     */
    public void connect(String serverHost, int serverPort) {
        System.out.println("🔌 [VIEWER] Connecting to server: " + serverHost + ":" + serverPort);

        try {
            String serverUrl = "ws://" + serverHost + ":" + serverPort + "/screenshare";
            System.out.println("🌐 WebSocket URL: " + serverUrl);

            serverConnection = new ServerConnectionService(serverUrl);

            serverConnection.setConnectionOpenHandler(() -> {
                System.out.println("✅ [VIEWER] Connected to server!");
                isConnected = true;
                onStatusUpdate.accept("✅ Connected to server");
                onConnectionStatusUpdate.accept(true);
                // Join room if waiting
                if (roomId != null && !roomId.isEmpty()) {
                    sendJoinRoomMessage();
                }
            });

            serverConnection.setTextMessageHandler(this::handleServerMessage);
            serverConnection.setBinaryMessageHandler(this::handleBinaryMessage);

            serverConnection.setConnectionClosedHandler(() -> {
                System.out.println("⏹️ [VIEWER] Disconnected from server");
                isConnected = false;
                isWatchingStream = false;
                onStatusUpdate.accept("⏹️ Disconnected");
                onConnectionStatusUpdate.accept(false);
            });

            System.out.println("⏳ [VIEWER] Attempting WebSocket connection...");
            
            executorService.submit(() -> {
                try {
                    serverConnection.connect();
                    // Connection successful - handlers will update UI
                } catch (Exception ex) {
                    System.err.println("❌ [VIEWER] Connection error: " + ex.getMessage());
                    ex.printStackTrace();
                    onStatusUpdate.accept("❌ Connection failed: " + ex.getMessage());
                    onConnectionStatusUpdate.accept(false);
                }
            });

        } catch (Exception e) {
            System.err.println("❌ [VIEWER] Connection error: " + e.getMessage());
            e.printStackTrace();
            onStatusUpdate.accept("❌ Error: " + e.getMessage());
            onConnectionStatusUpdate.accept(false);
        }
    }

    /**
     * Join a streaming room
     */
    public void joinRoom(String serverHost, int serverPort, String roomIdToJoin) {
        if (serverConnection == null || !serverConnection.isConnected()) {
            // Need to connect first
            this.roomId = roomIdToJoin;
            connect(serverHost, serverPort);
            // Will join after connection
        } else {
            this.roomId = roomIdToJoin;
            sendJoinRoomMessage();
        }
    }

    /**
     * Send join room message to server
     */
    private void sendJoinRoomMessage() {
        String joinRoomMsg = String.format(
            "{\"type\":\"join-room\",\"roomId\":\"%s\"}",
            roomId
        );
        serverConnection.sendText(joinRoomMsg);
        onStatusUpdate.accept("📍 Joining room: " + roomId);
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        if (serverConnection != null && serverConnection.isConnected()) {
            serverConnection.sendText("{\"type\":\"leave-room\"}");
            serverConnection.disconnect();
        }
        isConnected = false;
        isWatchingStream = false;
        metricsExecutor.shutdown();
        onStatusUpdate.accept("🔌 Disconnected from server");
        onConnectionStatusUpdate.accept(false);
    }

    // Event Handlers
    private void onServerConnected() {
        System.out.println("✅ Viewer connected to server");
        isConnected = true;
        onStatusUpdate.accept("✅ Connected to server");
        onConnectionStatusUpdate.accept(true);

        // If room ID was set before connection, join now
        if (roomId != null && !roomId.isEmpty()) {
            sendJoinRoomMessage();
        }
    }

    private void onServerDisconnected() {
        System.out.println("❌ Viewer disconnected from server");
        isConnected = false;
        isWatchingStream = false;
        onStatusUpdate.accept("❌ Disconnected from server");
        onVideoDisplayUpdate.accept("Connection lost");
        onConnectionStatusUpdate.accept(false);
    }

    private void handleServerMessage(String message) {
        try {
            JsonNode json = objectMapper.readTree(message);
            String type = json.get("type").asText();

            switch (type) {
                case "connected":
                    onConnected(json);
                    break;
                case "room-joined":
                    onRoomJoined(json);
                    break;
                case "room-left":
                    onStatusUpdate.accept("🚪 Left room");
                    isWatchingStream = false;
                    break;
                case "viewer-count":
                    onViewerCount(json);
                    break;
                case "presenter-left":
                    onPresenterLeft(json);
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

    private void handleBinaryMessage(byte[] data) {
        totalBytesReceived.addAndGet(data.length);

        if (!initSegmentReceived) {
            // First binary message is typically the init segment
            System.out.println("📦 Received init segment: " + data.length + " bytes");
            initSegmentReceived = true;
            onVideoDisplayUpdate.accept("🎬 Stream started - " + data.length + " bytes init segment");
            frameCount = 0;
            startTime = System.currentTimeMillis();
            startMetricsUpdater();
        } else {
            // Regular media segments
            frameCount++;
            onVideoDisplayUpdate.accept("🎥 Streaming... (" + frameCount + " frames received)");
        }
    }

    private void onConnected(JsonNode json) {
        String sessionId = json.get("sessionId").asText();
        System.out.println("🔑 Session ID: " + sessionId);
        onStatusUpdate.accept("✅ Session established");
    }

    private void onRoomJoined(JsonNode json) {
        roomId = json.get("roomId").asText();
        String role = json.get("role").asText();
        int viewerCount = json.has("viewerCount") ? json.get("viewerCount").asInt() : 0;

        isWatchingStream = true;

        // If we successfully joined a room, there must be a presenter (room exists)
        // The server will send the init segment if available
        onStatusUpdate.accept("✅ Joined room: " + roomId + " | Viewers: " + viewerCount);
        onVideoDisplayUpdate.accept("⏳ Waiting for video stream...");

        System.out.println("✅ Joined room as: " + role + " (Viewers: " + viewerCount + ")");
    }

    private void onViewerCount(JsonNode json) {
        int count = json.get("count").asInt();
        System.out.println("👥 Total viewers in room: " + count);
    }

    private void onPresenterLeft(@SuppressWarnings("unused") JsonNode json) {
        isWatchingStream = false;
        onStatusUpdate.accept("⚠️ Presenter disconnected");
        onVideoDisplayUpdate.accept("⚠️ Presenter disconnected - stream ended");
        onFpsUpdate.accept("N/A");
    }

    private void onError(JsonNode json) {
        String errorMsg = json.get("message").asText();
        onStatusUpdate.accept("❌ Server error: " + errorMsg);
    }

    private void startMetricsUpdater() {
        metricsExecutor.scheduleAtFixedRate(() -> {
            if (isWatchingStream && frameCount > 0) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                if (elapsed > 0) {
                    double fps = frameCount / (double) elapsed;
                    String metricsText = String.format("%.2f FPS", fps);
                    onFpsUpdate.accept(metricsText);

                    long totalBytes = totalBytesReceived.get();
                    String dataText = String.format("%.2f MB", totalBytes / (1024.0 * 1024.0));
                    onDataUpdate.accept(dataText);
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
}

