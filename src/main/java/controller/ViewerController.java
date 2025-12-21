package controller;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import service.FrameBufferService;
import service.H264DecoderService;
import service.ServerConnectionService;

/**
 * Viewer Controller
 * Manages viewer/watcher role - stream reception, decoding, and display
 * 
 * Video Pipeline:
 * 1. Receive binary H.264 fMP4 data via WebSocket
 * 2. Detect init segment (ftyp/moov) vs media segment (moof/mdat)
 * 3. Buffer media segments in FrameBufferService
 * 4. Decode frames using H264DecoderService
 * 5. Display decoded frames in ImageView
 */
public class ViewerController {
    private ServerConnectionService serverConnection;
    
    // Video decoding and buffering services
    private H264DecoderService decoderService;
    private FrameBufferService frameBufferService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);
    private ScheduledExecutorService metricsExecutor;

    private volatile boolean isConnected = false;
    private volatile boolean isWatchingStream = false;
    private volatile boolean isPlaying = false;
    private Thread playbackThread;
    
    private String roomId;
    private final AtomicInteger frameCount = new AtomicInteger(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private long lastStatsTime = System.currentTimeMillis();

    // UI components (optional - set via setter)
    private ImageView videoImageView;

    // Callbacks
    private final Consumer<String> onStatusUpdate;
    private final Consumer<String> onFpsUpdate;
    private final Consumer<String> onDataUpdate;
    private final Consumer<String> onVideoDisplayUpdate;
    private final Consumer<Boolean> onConnectionStatusUpdate;
    private Consumer<Image> onImageUpdate;  // New callback for decoded frames

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
        
        // Initialize video services
        this.decoderService = new H264DecoderService();
        this.frameBufferService = new FrameBufferService();
    }

    /**
     * Set the ImageView for video display
     */
    public void setVideoImageView(ImageView imageView) {
        this.videoImageView = imageView;
    }

    /**
     * Set callback for decoded images
     */
    public void setOnImageUpdate(Consumer<Image> callback) {
        this.onImageUpdate = callback;
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
                stopPlayback();
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
                    
                    // Provide user-friendly error message
                    String errorMsg = ex.getMessage();
                    String userMessage;
                    
                    if (errorMsg != null && errorMsg.contains("Connection refused")) {
                        userMessage = "❌ Server not reachable at " + serverHost + ":" + serverPort;
                    } else if (errorMsg != null && errorMsg.contains("DeploymentException")) {
                        userMessage = "❌ Cannot connect to " + serverHost + ":" + serverPort + " - check server IP & firewall";
                    } else if (errorMsg != null && errorMsg.contains("timeout")) {
                        userMessage = "❌ Connection timeout - server may be down";
                    } else {
                        userMessage = "❌ Connection failed: " + (errorMsg != null ? errorMsg.substring(0, Math.min(50, errorMsg.length())) : "Unknown error");
                    }
                    
                    onStatusUpdate.accept(userMessage);
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
        System.out.println("🔌 [VIEWER] Disconnecting...");
        
        // Stop video playback first
        stopPlayback();
        
        if (serverConnection != null && serverConnection.isConnected()) {
            serverConnection.sendText("{\"type\":\"leave-room\"}");
            serverConnection.disconnect();
        }
        
        isConnected = false;
        isWatchingStream = false;
        
        if (metricsExecutor != null && !metricsExecutor.isShutdown()) {
            metricsExecutor.shutdown();
        }
        
        // Cleanup decoder and buffer
        if (decoderService != null) {
            decoderService.cleanup();
        }
        if (frameBufferService != null) {
            frameBufferService.clear();
        }
        
        onStatusUpdate.accept("🔌 Disconnected from server");
        onConnectionStatusUpdate.accept(false);
    }

    /**
     * Handle binary video data from server
     * This is the main entry point for video data
     * 
     * For raw H.264 Annex B streams:
     * - SPS/PPS NAL units (init) are fed to decoder first
     * - IDR and non-IDR slices are decoded to produce frames
     */
    private void handleBinaryMessage(byte[] data) {
        // Null and empty check
        if (data == null || data.length == 0) {
            System.out.println("⚠️ [VIEWER] Received empty binary data");
            return;
        }

        long frameNum = totalBytesReceived.incrementAndGet();
        
        // Always log first few frames to confirm data is flowing, then periodically
        if (frameNum <= 10 || frameNum % 30 == 0) {
            System.out.println("📦 [VIEWER] Binary frame #" + frameNum + ": " + data.length + " bytes");
        }

        // Initialize decoder if not already done
        if (!decoderService.isInitialized()) {
            try {
                System.out.println("🎬 [VIEWER] First data received, initializing decoder...");
                
                // Initialize decoder with frame callback to display decoded frames
                decoderService.initialize(decodedFrame -> {
                    // This callback is called for each decoded frame
                    if (decodedFrame != null) {
                        frameCount.incrementAndGet();
                        
                        // Update UI on JavaFX thread
                        Platform.runLater(() -> {
                            // Update ImageView if set
                            if (videoImageView != null) {
                                videoImageView.setImage(decodedFrame);
                            }
                            // Call image callback if set
                            if (onImageUpdate != null) {
                                onImageUpdate.accept(decodedFrame);
                            }
                        });
                    }
                });
                
                System.out.println("✅ [VIEWER] Decoder initialized successfully!");
                onVideoDisplayUpdate.accept("🎬 Decoder initialized - starting playback...");
                
                // Start playback thread for processing buffered data
                if (!isPlaying) {
                    startPlayback();
                }
            } catch (Exception e) {
                System.err.println("❌ [VIEWER] Failed to initialize decoder: " + e.getMessage());
                System.err.println("❌ [VIEWER] Make sure javacv-platform dependency is in pom.xml");
                e.printStackTrace();
                onVideoDisplayUpdate.accept("❌ Decoder failed: " + e.getMessage());
                onStatusUpdate.accept("❌ Decoder initialization failed");
                return;
            }
        }

        // Check if this is an init segment (SPS/PPS for H.264)
        boolean isInit = frameBufferService.isInitSegment(data);
        if (isInit) {
            System.out.println("🎬 [VIEWER] Init segment (SPS/PPS) detected: " + data.length + " bytes");
            frameBufferService.setInitSegment(data);
        }

        // Add ALL data to buffer - decoder will handle SPS/PPS and slice NALs
        boolean added = frameBufferService.addFrame(data);
        if (!added) {
            System.out.println("⚠️ [VIEWER] Failed to add frame to buffer (buffer full?)");
        }
        
        // Update display with frame count periodically
        long received = frameBufferService.getTotalFramesReceived();
        if (received % 30 == 0) {
            int bufferSize = frameBufferService.getBufferSize();
            onVideoDisplayUpdate.accept("🎥 Receiving... (" + received + " packets, buffer: " + bufferSize + ")");
        }
    }

    /**
     * Start the video playback thread
     */
    private void startPlayback() {
        if (isPlaying) {
            System.out.println("⚠️ [VIEWER] Playback already running");
            return;
        }

        isPlaying = true;
        frameCount.set(0);
        lastStatsTime = System.currentTimeMillis();
        
        System.out.println("▶️ [VIEWER] Starting video playback thread");
        onVideoDisplayUpdate.accept("▶️ Starting video playback...");

        // Start metrics updater
        startMetricsUpdater();

        playbackThread = new Thread(() -> {
            System.out.println("🎬 [VIEWER] Playback thread started");
            
            while (isPlaying) {
                try {
                    // Get frame from buffer (wait up to 50ms)
                    byte[] frameData = frameBufferService.getNextFrame(50);

                    if (frameData != null && frameData.length > 0) {
                        // Decode frame
                        Image image = decoderService.decodeFrame(frameData);

                        if (image != null) {
                            frameCount.incrementAndGet();
                            
                            // Update UI on JavaFX thread
                            final Image finalImage = image;
                            Platform.runLater(() -> {
                                // Update ImageView if set
                                if (videoImageView != null) {
                                    videoImageView.setImage(finalImage);
                                }
                                // Call image callback if set
                                if (onImageUpdate != null) {
                                    onImageUpdate.accept(finalImage);
                                }
                            });
                        }
                    }

                    // Small sleep to prevent busy-waiting when buffer is empty
                    Thread.sleep(1);

                } catch (InterruptedException e) {
                    System.out.println("⏹️ [VIEWER] Playback thread interrupted");
                    break;
                } catch (Exception e) {
                    System.err.println("❌ [VIEWER] Error in playback loop: " + e.getMessage());
                    // Continue playing despite errors
                }
            }
            
            System.out.println("⏹️ [VIEWER] Playback thread stopped");
        }, "ViewerPlaybackThread");

        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    /**
     * Stop video playback
     */
    private void stopPlayback() {
        System.out.println("⏹️ [VIEWER] Stopping playback...");
        isPlaying = false;
        
        if (playbackThread != null) {
            playbackThread.interrupt();
            try {
                playbackThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            playbackThread = null;
        }
        
        // Clear the video display
        Platform.runLater(() -> {
            if (videoImageView != null) {
                videoImageView.setImage(null);
            }
            onVideoDisplayUpdate.accept("⏹️ Playback stopped");
        });
        
        System.out.println("✅ [VIEWER] Playback stopped");
    }

    /**
     * Handle text messages from server (JSON control messages)
     */
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
                    stopPlayback();
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

    private void onConnected(JsonNode json) {
        String sessionId = json.get("sessionId").asText();
        System.out.println("🔑 [VIEWER] Session ID: " + sessionId);
        onStatusUpdate.accept("✅ Session established");
    }

    private void onRoomJoined(JsonNode json) {
        roomId = json.get("roomId").asText();
        String role = json.get("role").asText();
        int viewerCount = json.has("viewerCount") ? json.get("viewerCount").asInt() : 0;

        isWatchingStream = true;

        onStatusUpdate.accept("✅ Joined room: " + roomId + " | Viewers: " + viewerCount);
        onVideoDisplayUpdate.accept("⏳ Waiting for video stream...");

        System.out.println("✅ [VIEWER] Joined room as: " + role + " (Viewers: " + viewerCount + ")");
    }

    private void onViewerCount(JsonNode json) {
        int count = json.get("count").asInt();
        System.out.println("👥 [VIEWER] Total viewers in room: " + count);
    }

    private void onPresenterLeft(@SuppressWarnings("unused") JsonNode json) {
        isWatchingStream = false;
        stopPlayback();
        onStatusUpdate.accept("⚠️ Presenter disconnected");
        onVideoDisplayUpdate.accept("⚠️ Presenter disconnected - stream ended");
        onFpsUpdate.accept("N/A");
    }

    private void onError(JsonNode json) {
        String errorMsg = json.get("message").asText();
        onStatusUpdate.accept("❌ Server error: " + errorMsg);
    }

    /**
     * Start the metrics updater to show FPS and data rate
     */
    private void startMetricsUpdater() {
        if (metricsExecutor != null && !metricsExecutor.isShutdown()) {
            metricsExecutor.shutdown();
        }
        
        metricsExecutor = Executors.newScheduledThreadPool(1);
        
        metricsExecutor.scheduleAtFixedRate(() -> {
            if (isPlaying) {
                // Calculate FPS
                int frames = frameCount.getAndSet(0);
                onFpsUpdate.accept(frames + " FPS");

                // Calculate data rate
                long bytes = totalBytesReceived.getAndSet(0);
                double mbps = (bytes * 8.0) / (1024 * 1024);
                onDataUpdate.accept(String.format("%.2f Mbps", mbps));
                
                // Log stats periodically
                if (decoderService.isInitialized()) {
                    System.out.println(String.format(
                        "📊 [VIEWER] FPS: %d | Data: %.2f Mbps | Buffer: %d | Resolution: %dx%d | Decoded: %d",
                        frames, mbps, 
                        frameBufferService.getBufferSize(),
                        decoderService.getWidth(), decoderService.getHeight(),
                        decoderService.getTotalFramesDecoded()
                    ));
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    // Getters for testing/debugging
    public boolean isConnected() {
        return isConnected;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public int getBufferSize() {
        return frameBufferService != null ? frameBufferService.getBufferSize() : 0;
    }
}