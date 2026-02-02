package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ParallelTransition;
import javafx.util.Duration;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import org.springframework.stereotype.Component;
import service.ServerConnectionService;
import service.ScreenCaptureService;
import service.PerformanceMonitorService;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main JavaFX Controller
 * Managed by Spring for FXML to find it
 */
@Component
public class MainController {
    // Services - will be manually set from App.java
    @SuppressWarnings("unused")
    private ServerConnectionService serverConnectionService;
    @SuppressWarnings("unused")
    private ScreenCaptureService screenCaptureService;
    @SuppressWarnings("unused")
    private PerformanceMonitorService performanceMonitorService;

    // Role Selection
    @FXML private RadioButton hostRadio;
    @FXML private RadioButton viewerRadio;
    private ToggleGroup roleToggleGroup;

    // Host Section
    @FXML private VBox hostSection;
    @FXML private TextField hostServerInput;
    @FXML private TextField hostPortInput;
    @FXML private Button hostConnectButton;
    @FXML private Label hostConnectionStatusLabel;
    @FXML private TextField roomIdInput;
    @FXML private ComboBox<String> screenSourceCombo;
    @FXML private ComboBox<String> encoderCombo;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private Label hostStatusLabel;
    @FXML private Label viewerCountLabel;
    @FXML private Label hostPerformanceLabel;

    // Viewer Section
    @FXML private VBox viewerSection;
    @FXML private TextField viewerServerInput;
    @FXML private TextField viewerPortInput;
    @FXML private Button viewerConnectButton;
    @FXML private TextField joinRoomIdInput;
    @FXML private Button joinRoomButton;
    @FXML private Label videoDisplayLabel;
    @FXML private Label viewerStatusLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private Label roomStatusLabel;
    @FXML private Label viewerFpsLabel;
    @FXML private Label viewerDataLabel;
    @FXML private Label latencyLabel;
    @FXML private Label qualityLabel;
    @FXML private Button disconnectViewerButton;
    @FXML private ImageView videoImageView;  // ImageView for displaying decoded video frames
    @FXML private VBox videoPlaceholder;      // Placeholder shown when no video

    // Controllers
    private HostController hostController;
    private ViewerController viewerController;
    @SuppressWarnings("unused")
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    // Current role state - client can only be HOST or VIEWER, not both
    private enum ClientRole { NONE, HOST, VIEWER }
    private ClientRole currentRole = ClientRole.NONE;
    private boolean roleActive = false;  // True when connected as host or viewer

    @FXML
    public void initialize() {
        System.out.println("=================================================");
        System.out.println("🔧 Initializing MainController...");
        System.out.println("=================================================");
        
        // Check critical UI elements
        System.out.println("🔍 Checking FXML bindings...");
        System.out.println("   hostConnectButton: " + (hostConnectButton == null ? "❌ NULL" : "✅ OK"));
        System.out.println("   hostServerInput: " + (hostServerInput == null ? "❌ NULL" : "✅ OK"));
        System.out.println("   hostPortInput: " + (hostPortInput == null ? "❌ NULL" : "✅ OK"));
        System.out.println("   hostRadio: " + (hostRadio == null ? "❌ NULL" : "✅ OK"));
        System.out.println("   viewerRadio: " + (viewerRadio == null ? "❌ NULL" : "✅ OK"));

        // Setup role toggle group
        roleToggleGroup = new ToggleGroup();
        hostRadio.setToggleGroup(roleToggleGroup);
        viewerRadio.setToggleGroup(roleToggleGroup);

        // Role selection handlers
        hostRadio.setOnAction(e -> {
            System.out.println("🔴 Host radio selected");
            switchToHost();
        });
        viewerRadio.setOnAction(e -> {
            System.out.println("🔵 Viewer radio selected");
            switchToViewer();
        });

        // Initialize controllers
        System.out.println("🏗️ Creating HostController and ViewerController...");
        hostController = new HostController(
            (msg) -> updateHostStatus(msg, "#2196F3"),
            this::updateHostPerformance,
            this::updateViewerCount,
            (isConnected) -> updateConnectionStatus(isConnected, "host")
        );
        
        // Set streaming state callback to enable/disable stop button
        hostController.setOnStreamingStateUpdate(this::onStreamingStateChanged);

        viewerController = new ViewerController(
            (msg) -> updateViewerStatus(msg, "#2196F3"),
            this::updateViewerFps,
            this::updateViewerData,
            this::updateVideoDisplay,
            (isConnected) -> updateConnectionStatus(isConnected, "viewer")
        );
        System.out.println("✅ Controllers created");

        // Initialize Host UI
        initializeHostUI();

        // Initialize Viewer UI
        initializeViewerUI();

        // Start with Host selected
        hostRadio.setSelected(true);
        switchToHost();

        System.out.println("=================================================");
        System.out.println("✅ MainController initialized successfully");
        System.out.println("=================================================");
    }

    private void initializeHostUI() {
        System.out.println("=================================================");
        System.out.println("📺 Initializing Host UI...");
        System.out.println("=================================================");
        
        // Check if button exists
        if (hostConnectButton == null) {
            System.err.println("❌ ERROR: hostConnectButton is NULL!");
            System.err.println("❌ FXML binding failed!");
            return;
        }
        System.out.println("✅ hostConnectButton exists: " + hostConnectButton);

        // Load available screens
        screenSourceCombo.getItems().clear();
        screenSourceCombo.getItems().addAll("Display 1", "Display 2", "Window 1");
        screenSourceCombo.setValue("Display 1");
        System.out.println("✅ Screen sources loaded");

        // Load available encoders
        encoderCombo.getItems().clear();
        encoderCombo.getItems().addAll("H.264 - Fast", "H.264 - Balanced", "H.264 - Quality");
        encoderCombo.setValue("H.264 - Balanced");
        System.out.println("✅ Encoders loaded");

        // Connect button
        System.out.println("🔧 Setting up Connect button handler...");
        hostConnectButton.setOnAction(e -> {
            System.out.println("=================================================");
            System.out.println("🔌🔌🔌 CONNECT BUTTON CLICKED! 🔌🔌🔌");
            System.out.println("=================================================");
            connectAsHost();
        });
        System.out.println("✅ Connect button handler installed successfully");

        // Start/Stop buttons
        startButton.setOnAction(e -> {
            System.out.println("▶️ Start button clicked!");
            hostController.startStreaming(
                hostServerInput.getText(),
                Integer.parseInt(hostPortInput.getText()),
                roomIdInput.getText().isEmpty() ? null : roomIdInput.getText(),
                screenSourceCombo.getValue(),
                encoderCombo.getValue()
            );
        });

        stopButton.setOnAction(e -> hostController.stopStreaming());
        stopButton.setDisable(true);

        // Initialize room ID if empty
        if (roomIdInput.getText().isEmpty()) {
            roomIdInput.setText("room-" + UUID.randomUUID().toString().substring(0, 8));
        }
    }

    private void initializeViewerUI() {
        // Connect button
        viewerConnectButton.setOnAction(e -> connectAsViewer());

        // Join room button
        joinRoomButton.setOnAction(e -> {
            String roomId = joinRoomIdInput.getText().trim();
            if (roomId.isEmpty()) {
                updateViewerStatus("⚠️ Please enter a room ID", "#f44336");
                return;
            }
            viewerController.joinRoom(
                viewerServerInput.getText(),
                Integer.parseInt(viewerPortInput.getText()),
                roomId
            );
        });

        // Disconnect button
        disconnectViewerButton.setOnAction(e -> {
            viewerController.disconnect();
            // Show placeholder when disconnected
            if (videoPlaceholder != null) {
                videoPlaceholder.setVisible(true);
            }
            if (videoImageView != null) {
                videoImageView.setImage(null);
            }
        });
        
        // Connect ImageView to ViewerController for video display
        if (videoImageView != null) {
            viewerController.setVideoImageView(videoImageView);
            
            // Set callback to hide placeholder when video starts
            viewerController.setOnImageUpdate(image -> {
                Platform.runLater(() -> {
                    if (image != null && videoPlaceholder != null) {
                        videoPlaceholder.setVisible(false);
                    }
                });
            });
            
            System.out.println("✅ ImageView connected to ViewerController");
        } else {
            System.out.println("⚠️ videoImageView is null - video display won't work");
        }
    }

    private void switchToHost() {
        // Check if currently active as viewer - must disconnect first
        if (currentRole == ClientRole.VIEWER && roleActive) {
            System.out.println("⚠️ Cannot switch to Host while connected as Viewer");
            // Show warning and revert selection
            Platform.runLater(() -> {
                viewerRadio.setSelected(true);
                updateHostStatus("⚠️ Disconnect from viewer first!", "#f44336");
            });
            return;
        }
        
        // Disconnect viewer if any residual connection
        if (viewerController != null) {
            viewerController.disconnect();
        }
        
        hostSection.setVisible(true);
        hostSection.setManaged(true);
        viewerSection.setVisible(false);
        viewerSection.setManaged(false);

        currentRole = ClientRole.HOST;
        updateHostStatus("ℹ️ Ready to connect as Host", "#2196F3");
        System.out.println("🔴 Switched to HOST mode");
    }

    private void switchToViewer() {
        // Check if currently active as host - must disconnect first
        if (currentRole == ClientRole.HOST && roleActive) {
            System.out.println("⚠️ Cannot switch to Viewer while connected as Host");
            // Show warning and revert selection
            Platform.runLater(() -> {
                hostRadio.setSelected(true);
                updateViewerStatus("⚠️ Stop streaming and disconnect first!", "#f44336");
            });
            return;
        }
        
        // Disconnect host if any residual connection
        if (hostController != null) {
            hostController.disconnect();
        }
        
        hostSection.setVisible(false);
        hostSection.setManaged(false);
        viewerSection.setVisible(true);
        viewerSection.setManaged(true);

        currentRole = ClientRole.VIEWER;
        updateViewerStatus("ℹ️ Ready to connect as Viewer", "#2196F3");
        System.out.println("🔵 Switched to VIEWER mode");
    }

    private void connectAsHost() {
        System.out.println("=================================================");
        System.out.println("🔌 connectAsHost() called - START");
        System.out.println("=================================================");
        
        String server = hostServerInput.getText().trim();
        String port = hostPortInput.getText().trim();
        
        System.out.println("📍 Server input: '" + server + "'");
        System.out.println("📍 Port input: '" + port + "'");

        if (server.isEmpty() || port.isEmpty()) {
            System.out.println("⚠️ Validation failed - server or port is empty");
            hostConnectionStatusLabel.setText("⚠️ Enter server address and port");
            hostConnectionStatusLabel.setStyle("-fx-text-fill: #ff9800; -fx-font-weight: bold;");
            return;
        }

        try {
            int portNum = Integer.parseInt(port);
            System.out.println("✅ Port parsed successfully: " + portNum);
            System.out.println("🔄 Updating UI to 'Connecting...'");
            
            hostConnectionStatusLabel.setText("⏳ Connecting...");
            hostConnectionStatusLabel.setStyle("-fx-text-fill: #2196f3; -fx-font-weight: bold;");
            hostConnectButton.setDisable(true);

            System.out.println("📞 Calling hostController.connect(" + server + ", " + portNum + ")");
            hostController.connect(server, portNum);
            System.out.println("✅ hostController.connect() called successfully");
        } catch (NumberFormatException ex) {
            System.err.println("❌ Port parsing failed: " + ex.getMessage());
            hostConnectionStatusLabel.setText("⚠️ Invalid port number");
            hostConnectionStatusLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold;");
            hostConnectButton.setDisable(false);
        }
        
        System.out.println("=================================================");
        System.out.println("🔌 connectAsHost() called - END");
        System.out.println("=================================================");
    }

    private void connectAsViewer() {
        String server = viewerServerInput.getText().trim();
        String port = viewerPortInput.getText().trim();

        if (server.isEmpty() || port.isEmpty()) {
            updateViewerStatus("⚠️ Enter server address and port", "#f44336");
            return;
        }

        try {
            int portNum = Integer.parseInt(port);
            viewerController.connect(server, portNum);
            viewerConnectButton.setDisable(true);
            updateViewerStatus("🔗 Connecting...", "#2196F3");
        } catch (NumberFormatException ex) {
            updateViewerStatus("⚠️ Invalid port number", "#f44336");
        }
    }

    // Update methods for Host
    private void updateHostStatus(String message, String color) {
        Platform.runLater(() -> {
            hostStatusLabel.setText(message);
            hostStatusLabel.setStyle("-fx-text-fill: " + color + ";");
        });
    }

    private void updateHostPerformance(String message) {
        Platform.runLater(() -> hostPerformanceLabel.setText(message));
    }

    private void updateViewerCount(int count) {
        Platform.runLater(() -> viewerCountLabel.setText(String.valueOf(count)));
    }

    // Update methods for Viewer
    private void updateViewerStatus(String message, String color) {
        Platform.runLater(() -> {
            viewerStatusLabel.setText(message);
            viewerStatusLabel.setStyle("-fx-text-fill: " + color + ";");
        });
    }

    private void updateViewerFps(String fps) {
        Platform.runLater(() -> viewerFpsLabel.setText(fps));
    }

    private void updateViewerData(String data) {
        Platform.runLater(() -> viewerDataLabel.setText(data));
    }

    private void updateVideoDisplay(String message) {
        Platform.runLater(() -> videoDisplayLabel.setText(message));
    }

    // Connection status update
    private void updateConnectionStatus(boolean connected, String role) {
        Platform.runLater(() -> {
            if (role.equals("host")) {
                roleActive = connected;
                // Disable viewer radio when connected as host
                viewerRadio.setDisable(connected);
                
                if (connected) {
                    hostConnectButton.setDisable(true);
                    startButton.setDisable(false);
                    hostConnectionStatusLabel.setText("✅ Connected");
                    hostConnectionStatusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                    // Show success toast
                    showToast("Connected to server as Host!", "success");
                } else {
                    hostConnectButton.setDisable(false);
                    startButton.setDisable(true);
                    stopButton.setDisable(true);
                    hostConnectionStatusLabel.setText("🔴 Disconnected");
                    hostConnectionStatusLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold;");
                    currentRole = ClientRole.NONE;
                    showToast("Disconnected from server", "warning");
                }
            } else if (role.equals("viewer")) {
                roleActive = connected;
                // Disable host radio when connected as viewer
                hostRadio.setDisable(connected);
                
                if (connected) {
                    viewerConnectButton.setDisable(true);
                    joinRoomButton.setDisable(false);
                    connectionStatusLabel.setText("✅ Connected");
                    connectionStatusLabel.setStyle("-fx-text-fill: #4CAF50;");
                    // Show success toast
                    showToast("Connected to server as Viewer!", "success");
                } else {
                    viewerConnectButton.setDisable(false);
                    joinRoomButton.setDisable(true);
                    connectionStatusLabel.setText("❌ Disconnected");
                    connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
                    currentRole = ClientRole.NONE;
                    showToast("Disconnected from server", "warning");
                }
            }
        });
    }

    public void enableStartButton(boolean enable) {
        Platform.runLater(() -> startButton.setDisable(!enable));
    }

    public void enableStopButton(boolean enable) {
        Platform.runLater(() -> stopButton.setDisable(!enable));
    }
    
    /**
     * Called when streaming starts or stops
     * @param isStreaming true when streaming starts, false when it stops
     */
    private void onStreamingStateChanged(Boolean isStreaming) {
        Platform.runLater(() -> {
            System.out.println("🎬 Streaming state changed: " + isStreaming);
            if (isStreaming) {
                // Streaming started - enable stop, disable start
                stopButton.setDisable(false);
                startButton.setDisable(true);
                hostConnectButton.setDisable(true);  // Can't disconnect while streaming
            } else {
                // Streaming stopped - disable stop, enable start
                stopButton.setDisable(true);
                startButton.setDisable(false);
                hostConnectButton.setDisable(false);  // Can disconnect now
            }
        });
    }
    
    /**
     * Show a toast notification message
     * @param message The message to display
     * @param type "success", "error", "info", or "warning"
     */
    public void showToast(String message, String type) {
        Platform.runLater(() -> {
            try {
                // Get the scene from any existing node
                Scene scene = hostSection != null ? hostSection.getScene() : 
                              (viewerSection != null ? viewerSection.getScene() : null);
                
                if (scene == null || scene.getRoot() == null) {
                    System.out.println("🔔 TOAST [" + type + "]: " + message);
                    return;
                }
                
                // Create toast label
                Label toastLabel = new Label(message);
                toastLabel.setWrapText(true);
                toastLabel.setMaxWidth(400);
                
                // Style based on type
                String bgColor, textColor, emoji;
                switch (type.toLowerCase()) {
                    case "success":
                        bgColor = "#4CAF50";
                        textColor = "white";
                        emoji = "✅ ";
                        break;
                    case "error":
                        bgColor = "#f44336";
                        textColor = "white";
                        emoji = "❌ ";
                        break;
                    case "warning":
                        bgColor = "#ff9800";
                        textColor = "white";
                        emoji = "⚠️ ";
                        break;
                    default:  // info
                        bgColor = "#2196F3";
                        textColor = "white";
                        emoji = "ℹ️ ";
                        break;
                }
                
                toastLabel.setText(emoji + message);
                toastLabel.setStyle(
                    "-fx-background-color: " + bgColor + ";" +
                    "-fx-text-fill: " + textColor + ";" +
                    "-fx-padding: 15 25 15 25;" +
                    "-fx-background-radius: 8;" +
                    "-fx-font-size: 14px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 3);"
                );
                
                // Create container for positioning
                StackPane toastContainer = new StackPane(toastLabel);
                toastContainer.setAlignment(Pos.BOTTOM_CENTER);
                toastContainer.setPickOnBounds(false);
                toastContainer.setMouseTransparent(true);
                
                // Add to scene
                if (scene.getRoot() instanceof StackPane) {
                    ((StackPane) scene.getRoot()).getChildren().add(toastContainer);
                } else if (scene.getRoot() instanceof javafx.scene.layout.Pane) {
                    ((javafx.scene.layout.Pane) scene.getRoot()).getChildren().add(toastContainer);
                } else {
                    // Just log if we can't add to scene
                    System.out.println("🔔 TOAST [" + type + "]: " + message);
                    return;
                }
                
                // Position at bottom
                toastLabel.setTranslateY(50);
                toastLabel.setOpacity(0);
                
                // Animate in
                FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toastLabel);
                fadeIn.setFromValue(0);
                fadeIn.setToValue(1);
                
                TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), toastLabel);
                slideIn.setFromY(50);
                slideIn.setToY(-30);
                
                ParallelTransition showAnimation = new ParallelTransition(fadeIn, slideIn);
                
                // Animate out after delay
                FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toastLabel);
                fadeOut.setFromValue(1);
                fadeOut.setToValue(0);
                fadeOut.setDelay(Duration.seconds(3));
                
                fadeOut.setOnFinished(e -> {
                    if (scene.getRoot() instanceof javafx.scene.layout.Pane) {
                        ((javafx.scene.layout.Pane) scene.getRoot()).getChildren().remove(toastContainer);
                    }
                });
                
                showAnimation.play();
                fadeOut.play();
                
                System.out.println("🔔 TOAST [" + type + "]: " + message);
                
            } catch (Exception e) {
                System.out.println("🔔 TOAST [" + type + "]: " + message);
            }
        });
    }
}