package controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.application.Platform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import service.ServerConnectionService;
import service.ScreenCaptureService;
import service.PerformanceMonitorService;
import controller.HostController;
import controller.ViewerController;

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
    private ServerConnectionService serverConnectionService;
    private ScreenCaptureService screenCaptureService;
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

    // Controllers
    private HostController hostController;
    private ViewerController viewerController;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

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
        disconnectViewerButton.setOnAction(e -> viewerController.disconnect());
    }

    private void switchToHost() {
        hostSection.setVisible(true);
        hostSection.setManaged(true);
        viewerSection.setVisible(false);
        viewerSection.setManaged(false);

        if (viewerController != null) {
            viewerController.disconnect();
        }

        updateHostStatus("ℹ️ Ready to connect", "#2196F3");
    }

    private void switchToViewer() {
        hostSection.setVisible(false);
        hostSection.setManaged(false);
        viewerSection.setVisible(true);
        viewerSection.setManaged(true);

        if (hostController != null) {
            hostController.stopStreaming();
        }

        updateViewerStatus("ℹ️ Ready to connect", "#2196F3");
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
    private void updateConnectionStatus(boolean isConnected, String role) {
        Platform.runLater(() -> {
            if (role.equals("host")) {
                if (isConnected) {
                    hostConnectButton.setDisable(true);
                    startButton.setDisable(false);
                    hostConnectionStatusLabel.setText("✅ Connected");
                    hostConnectionStatusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
                } else {
                    hostConnectButton.setDisable(false);
                    startButton.setDisable(true);
                    stopButton.setDisable(true);
                    hostConnectionStatusLabel.setText("🔴 Disconnected");
                    hostConnectionStatusLabel.setStyle("-fx-text-fill: #f44336; -fx-font-weight: bold;");
                }
            } else if (role.equals("viewer")) {
                if (isConnected) {
                    viewerConnectButton.setDisable(true);
                    joinRoomButton.setDisable(false);
                    connectionStatusLabel.setText("✅ Connected");
                    connectionStatusLabel.setStyle("-fx-text-fill: #4CAF50;");
                } else {
                    viewerConnectButton.setDisable(false);
                    joinRoomButton.setDisable(true);
                    connectionStatusLabel.setText("❌ Disconnected");
                    connectionStatusLabel.setStyle("-fx-text-fill: #f44336;");
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
}

