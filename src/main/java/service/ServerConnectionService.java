package service;

import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * WebSocket Client Service
 * Connects to ScreenAI-Server relay for screen sharing
 *
 * Provides connection management with proper error handling
 * Note: Not a Spring bean - instantiated directly by controllers
 */
public class ServerConnectionService {
    private final String serverUrl;
    private Consumer<String> onTextMessage;
    private Consumer<byte[]> onBinaryMessage;
    private Runnable onConnectionOpen;
    private Runnable onConnectionClosed;
    
    private WebSocketSession session;
    private StandardWebSocketClient webSocketClient;
    private volatile boolean isConnected = false;
    private static final int CONNECTION_TIMEOUT = 10000; // 10 seconds
    
    // Buffer sizes for video streaming
    private static final int MAX_TEXT_MESSAGE_BUFFER_SIZE = 128 * 1024; // 128 KB
    private static final int MAX_BINARY_MESSAGE_BUFFER_SIZE = 10 * 1024 * 1024; // 10 MB

    /**
     * Constructor - accepts server URL directly
     * Used for direct instantiation by controllers
     */
    public ServerConnectionService(String serverUrl) {
        this.serverUrl = serverUrl;
        
        // Configure WebSocket container with larger buffer sizes
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
            container.setDefaultMaxBinaryMessageBufferSize(MAX_BINARY_MESSAGE_BUFFER_SIZE);
            container.setAsyncSendTimeout(5000); // 5 seconds timeout for async sends
            System.out.println("✅ WebSocket container configured:");
            System.out.println("   - Max text buffer: " + MAX_TEXT_MESSAGE_BUFFER_SIZE + " bytes");
            System.out.println("   - Max binary buffer: " + MAX_BINARY_MESSAGE_BUFFER_SIZE + " bytes");
        } catch (Exception e) {
            System.err.println("⚠️ Warning: Could not configure WebSocket container: " + e.getMessage());
        }
        
        this.webSocketClient = new StandardWebSocketClient();
        System.out.println("✅ ServerConnectionService initialized");
        System.out.println("📍 Server URL: " + serverUrl);
    }

    public void setTextMessageHandler(Consumer<String> handler) {
        this.onTextMessage = handler;
    }

    public void setBinaryMessageHandler(Consumer<byte[]> handler) {
        this.onBinaryMessage = handler;
    }

    public void setConnectionOpenHandler(Runnable handler) {
        this.onConnectionOpen = handler;
    }

    public void setConnectionClosedHandler(Runnable handler) {
        this.onConnectionClosed = handler;
    }

    /**
     * Connect to ScreenAI-Server WebSocket endpoint
     */
    public void connect() {
        if (isConnected && session != null && session.isOpen()) {
            System.out.println("⚠️ [ServerConnectionService] Already connected");
            return;
        }
        
        try {
            System.out.println("🔌 [ServerConnectionService] Attempting to connect to: " + serverUrl);

            // Validate URL format
            if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
                throw new IllegalArgumentException("Invalid WebSocket URL - must start with ws:// or wss://");
            }

            URI uri = new URI(serverUrl);
            System.out.println("📍 Host: " + uri.getHost() + ":" + uri.getPort());
            System.out.println("📍 Path: " + uri.getPath());

            // Create WebSocket handler
            WebSocketHandler handler = new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                    ServerConnectionService.this.session = session;
                    ServerConnectionService.this.isConnected = true;
                    System.out.println("✅ [ServerConnectionService] WebSocket connected! Session ID: " + session.getId());
                    
                    if (onConnectionOpen != null) {
                        try {
                            onConnectionOpen.run();
                        } catch (Exception e) {
                            System.err.println("❌ Error in connection open handler: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                    if (message instanceof TextMessage textMessage) {
                        String payload = textMessage.getPayload();
                        System.out.println("📨 [ServerConnectionService] Received text: " + payload.substring(0, Math.min(100, payload.length())));
                        if (onTextMessage != null) {
                            try {
                                onTextMessage.accept(payload);
                            } catch (Exception e) {
                                System.err.println("❌ Error in text message handler: " + e.getMessage());
                            }
                        }
                    } else if (message instanceof BinaryMessage binaryMessage) {
                        byte[] payload = binaryMessage.getPayload().array();
                        System.out.println("📦 [ServerConnectionService] Received binary: " + payload.length + " bytes");
                        if (onBinaryMessage != null) {
                            try {
                                onBinaryMessage.accept(payload);
                            } catch (Exception e) {
                                System.err.println("❌ Error in binary message handler: " + e.getMessage());
                            }
                        }
                    }
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
                    System.err.println("❌ [ServerConnectionService] Transport error: " + exception.getMessage());
                    exception.printStackTrace();
                    isConnected = false;
                    if (onConnectionClosed != null) {
                        try {
                            onConnectionClosed.run();
                        } catch (Exception e) {
                            System.err.println("❌ Error in connection closed handler: " + e.getMessage());
                        }
                    }
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                    System.out.println("🚪 [ServerConnectionService] Connection closed: " + closeStatus);
                    ServerConnectionService.this.session = null;
                    isConnected = false;
                    if (onConnectionClosed != null) {
                        try {
                            onConnectionClosed.run();
                        } catch (Exception e) {
                            System.err.println("❌ Error in connection closed handler: " + e.getMessage());
                        }
                    }
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            };

            // Connect using execute method (Spring 6.0+ API)
            System.out.println("⏳ [ServerConnectionService] Starting WebSocket handshake...");
            CompletableFuture<WebSocketSession> future = webSocketClient.execute(
                handler, null, uri
            );
            
            // Wait for connection with timeout
            try {
                System.out.println("⏳ [ServerConnectionService] Waiting for connection (timeout: " + CONNECTION_TIMEOUT + "ms)...");
                session = future.get(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
                System.out.println("✅ [ServerConnectionService] Connection established! Session: " + session.getId());
            } catch (java.util.concurrent.TimeoutException e) {
                System.err.println("❌ [ServerConnectionService] Connection timeout after " + CONNECTION_TIMEOUT + "ms");
                System.err.println("   Make sure the server is running on " + uri.getHost() + ":" + uri.getPort());
                isConnected = false;
                if (onConnectionClosed != null) {
                    onConnectionClosed.run();
                }
                throw new RuntimeException("Connection timeout - server may not be running", e);
            } catch (Exception e) {
                // Handle other exceptions
                System.err.println("❌ [ServerConnectionService] Connection failed: " + e.getMessage());
                System.err.println("   Exception type: " + e.getClass().getName());
                e.printStackTrace();
                isConnected = false;
                if (onConnectionClosed != null) {
                    onConnectionClosed.run();
                }
                throw new RuntimeException("Connection failed: " + e.getMessage(), e);
            }

        } catch (Exception e) {
            System.err.println("❌ [ServerConnectionService] WebSocket connection failed: " + e.getMessage());
            e.printStackTrace();
            isConnected = false;

            if (onConnectionClosed != null) {
                onConnectionClosed.run();
            }
            throw new RuntimeException("Failed to connect: " + e.getMessage(), e);
        }
    }

    /**
     * Send text message to server (JSON)
     */
    public void sendText(String message) {
        if (isConnected && session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                System.out.println("📤 [ServerConnectionService] Sent text: " + message.substring(0, Math.min(50, message.length())));
            } catch (Exception e) {
                System.err.println("⚠️ Failed to send text message: " + e.getMessage());
                e.printStackTrace();
                isConnected = false;
            }
        } else {
            System.err.println("⚠️ WebSocket not connected - cannot send message");
        }
    }

    /**
     * Send binary message to server (video frames)
     */
    public void sendBinary(byte[] data) {
        if (isConnected && session != null && session.isOpen()) {
            try {
                session.sendMessage(new BinaryMessage(data));
                // Only log occasionally to avoid spam - commented out for performance
                // System.out.println("📦 [ServerConnectionService] Sent binary: " + data.length + " bytes");
            } catch (Exception e) {
                System.err.println("⚠️ Failed to send binary message: " + e.getMessage());
                isConnected = false;
            }
        } else {
            System.err.println("⚠️ WebSocket not connected - cannot send binary data");
        }
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return isConnected && session != null && session.isOpen();
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                System.out.println("✅ [ServerConnectionService] Disconnected from server");
            } catch (Exception e) {
                System.err.println("⚠️ Error closing connection: " + e.getMessage());
            }
        }
        session = null;
        isConnected = false;
    }
}
