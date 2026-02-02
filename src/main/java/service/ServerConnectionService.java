package service;

import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * WebSocket Client Service
 * Connects to ScreenAI-Server relay for screen sharing
 *
 * Provides thread-safe connection management with proper error handling.
 * Supports JWT authentication for secure connections.
 * Note: Not a Spring bean - instantiated directly by controllers
 */
public class ServerConnectionService {
    private static final Logger logger = LoggerFactory.getLogger(ServerConnectionService.class);
    
    private final String baseServerUrl;
    private volatile Consumer<String> onTextMessage;
    private volatile Consumer<byte[]> onBinaryMessage;
    private volatile Runnable onConnectionOpen;
    private volatile Runnable onConnectionClosed;
    private volatile Consumer<String> onError;
    
    // Authentication
    private volatile String authToken;
    
    // Thread-safe session management
    private final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>(null);
    private final AtomicBoolean connecting = new AtomicBoolean(false);
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
        this.baseServerUrl = serverUrl;
        
        // Configure WebSocket container with larger buffer sizes
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.setDefaultMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_BUFFER_SIZE);
            container.setDefaultMaxBinaryMessageBufferSize(MAX_BINARY_MESSAGE_BUFFER_SIZE);
            container.setAsyncSendTimeout(5000); // 5 seconds timeout for async sends
            logger.info("WebSocket container configured: text={}KB, binary={}MB", 
                MAX_TEXT_MESSAGE_BUFFER_SIZE / 1024, MAX_BINARY_MESSAGE_BUFFER_SIZE / (1024 * 1024));
        } catch (Exception e) {
            logger.warn("Could not configure WebSocket container: {}", e.getMessage());
        }
        
        this.webSocketClient = new StandardWebSocketClient();
        logger.info("ServerConnectionService initialized for: {}", serverUrl);
    }

    /**
     * Set the authentication token for secure connections.
     */
    public void setAuthToken(String token) {
        this.authToken = token;
        logger.debug("Auth token set");
    }

    /**
     * Clear the authentication token.
     */
    public void clearAuthToken() {
        this.authToken = null;
        logger.debug("Auth token cleared");
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
    
    public void setErrorHandler(Consumer<String> handler) {
        this.onError = handler;
    }

    /**
     * Get the WebSocket URL with optional token authentication.
     */
    private String getAuthenticatedUrl() {
        if (authToken != null && !authToken.isEmpty()) {
            // Append token as query parameter for WebSocket authentication
            String separator = baseServerUrl.contains("?") ? "&" : "?";
            return baseServerUrl + separator + "token=" + authToken;
        }
        return baseServerUrl;
    }

    /**
     * Connect to ScreenAI-Server WebSocket endpoint
     */
    public void connect() {
        // Check if already connected
        WebSocketSession currentSession = sessionRef.get();
        if (isConnected && currentSession != null && currentSession.isOpen()) {
            logger.warn("Already connected to server");
            return;
        }
        
        // Prevent concurrent connection attempts
        if (!connecting.compareAndSet(false, true)) {
            logger.warn("Connection attempt already in progress");
            return;
        }
        
        try {
            String serverUrl = getAuthenticatedUrl();
            logger.info("Attempting to connect to: {}", 
                    authToken != null ? baseServerUrl + "?token=***" : baseServerUrl);

            // Validate URL format
            if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
                throw new IllegalArgumentException("Invalid WebSocket URL - must start with ws:// or wss://");
            }

            URI uri = new URI(serverUrl);
            logger.debug("Host: {}:{}, Path: {}", uri.getHost(), uri.getPort(), uri.getPath());

            // Create WebSocket handler
            WebSocketHandler handler = new WebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) {
                    sessionRef.set(session);
                    isConnected = true;
                    logger.info("WebSocket connected! Session ID: {}", session.getId());
                    
                    safeRunHandler(onConnectionOpen, "connection open");
                }

                @Override
                public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
                    if (message instanceof TextMessage textMessage) {
                        String payload = textMessage.getPayload();
                        logger.debug("Received text: {}...", payload.substring(0, Math.min(100, payload.length())));
                        safeAcceptHandler(onTextMessage, payload, "text message");
                    } else if (message instanceof BinaryMessage binaryMessage) {
                        byte[] payload = binaryMessage.getPayload().array();
                        if (logger.isTraceEnabled()) {
                            logger.trace("Received binary: {} bytes", payload.length);
                        }
                        safeAcceptHandler(onBinaryMessage, payload, "binary message");
                    }
                }

                @Override
                public void handleTransportError(WebSocketSession session, Throwable exception) {
                    logger.error("Transport error: {}", exception.getMessage(), exception);
                    handleDisconnection();
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
                    logger.info("Connection closed: {}", closeStatus);
                    handleDisconnection();
                }

                @Override
                public boolean supportsPartialMessages() {
                    return false;
                }
            };

            // Connect using execute method (Spring 6.0+ API)
            logger.debug("Starting WebSocket handshake...");
            CompletableFuture<WebSocketSession> future = webSocketClient.execute(handler, null, uri);
            
            // Wait for connection with timeout
            try {
                logger.debug("Waiting for connection (timeout: {}ms)...", CONNECTION_TIMEOUT);
                WebSocketSession session = future.get(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);
                sessionRef.set(session);
                logger.info("Connection established! Session: {}", session.getId());
            } catch (java.util.concurrent.TimeoutException e) {
                logger.error("Connection timeout after {}ms. Server: {}:{}", 
                    CONNECTION_TIMEOUT, uri.getHost(), uri.getPort());
                handleDisconnection();
                throw new RuntimeException("Connection timeout - server may not be running at " + 
                    uri.getHost() + ":" + uri.getPort(), e);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                String errorMsg = cause != null ? cause.getMessage() : e.getMessage();
                
                logger.error("Connection failed: {}", errorMsg);
                logTroubleshootingTips(uri, errorMsg);
                
                handleDisconnection();
                throw new RuntimeException("Connection failed: " + errorMsg, e);
            }

        } catch (Exception e) {
            logger.error("WebSocket connection failed: {}", e.getMessage(), e);
            handleDisconnection();
            throw new RuntimeException("Failed to connect: " + e.getMessage(), e);
        } finally {
            connecting.set(false);
        }
    }

    /**
     * Handle disconnection - cleanup and notify handlers
     */
    private void handleDisconnection() {
        sessionRef.set(null);
        isConnected = false;
        safeRunHandler(onConnectionClosed, "connection closed");
    }

    /**
     * Safely run a Runnable handler
     */
    private void safeRunHandler(Runnable handler, String handlerName) {
        if (handler != null) {
            try {
                handler.run();
            } catch (Exception e) {
                logger.error("Error in {} handler: {}", handlerName, e.getMessage());
            }
        }
    }

    /**
     * Safely accept a Consumer handler
     */
    private <T> void safeAcceptHandler(Consumer<T> handler, T value, String handlerName) {
        if (handler != null) {
            try {
                handler.accept(value);
            } catch (Exception e) {
                logger.error("Error in {} handler: {}", handlerName, e.getMessage());
            }
        }
    }

    /**
     * Log troubleshooting tips for connection failures
     */
    private void logTroubleshootingTips(URI uri, String errorMsg) {
        if (errorMsg != null && (errorMsg.contains("Connection refused") || errorMsg.contains("DeploymentException"))) {
            logger.info("");
            logger.info("TROUBLESHOOTING TIPS:");
            logger.info("   1. Make sure the server is running");
            logger.info("   2. If connecting from another machine, use the server's IP address (not 'localhost')");
            logger.info("   3. Check if both machines are on the same network");
            logger.info("   4. Check if firewall is blocking port {}", uri.getPort());
            logger.info("   5. Try pinging the server: ping {}", uri.getHost());
        }
    }

    /**
     * Send text message to server (JSON)
     */
    public void sendText(String message) {
        WebSocketSession session = sessionRef.get();
        if (isConnected && session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                logger.debug("Sent text: {}...", message.substring(0, Math.min(50, message.length())));
            } catch (Exception e) {
                logger.error("Failed to send text message: {}", e.getMessage());
                handleDisconnection();
            }
        } else {
            logger.warn("WebSocket not connected - cannot send message");
        }
    }

    /**
     * Send binary message to server (video frames)
     */
    public void sendBinary(byte[] data) {
        WebSocketSession session = sessionRef.get();
        if (isConnected && session != null && session.isOpen()) {
            try {
                session.sendMessage(new BinaryMessage(data));
            } catch (Exception e) {
                logger.error("Failed to send binary message: {}", e.getMessage());
                handleDisconnection();
            }
        } else {
            logger.warn("WebSocket not connected - cannot send binary data");
        }
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        WebSocketSession session = sessionRef.get();
        return isConnected && session != null && session.isOpen();
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        WebSocketSession session = sessionRef.get();
        if (session != null && session.isOpen()) {
            try {
                session.close();
                logger.info("Disconnected from server");
            } catch (Exception e) {
                logger.warn("Error closing connection: {}", e.getMessage());
            }
        }
        sessionRef.set(null);
        isConnected = false;
    }
}
