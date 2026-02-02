package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Dual Mode Main Controller for Bidirectional Screen Sharing
 * Supports simultaneous hosting and viewing
 */
public class DualModeMainController implements Initializable {

    // Connection UI
    @FXML private TextField serverHostField;
    @FXML private TextField serverPortField;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Label connectionStatusLabel;
    @FXML private Label statusLabel;

    // Host UI
    @FXML private TextField hostRoomIdField;
    @FXML private Button startHostingButton;
    @FXML private Button stopHostingButton;
    @FXML private Label createdRoomLabel;
    @FXML private Button copyRoomIdButton;
    @FXML private Label hostPerformanceLabel;
    @FXML private Label viewerCountLabel;
    @FXML private Label hostStatusIndicator;
    @FXML private ComboBox<String> screenSourceComboBox;
    
    // Access Code UI (for password-protected rooms)
    @FXML private VBox accessCodeSection;
    @FXML private TextField accessCodeField;
    @FXML private Button copyAccessCodeButton;

    // Viewer UI
    @FXML private TextField viewerRoomIdField;
    @FXML private Button startViewingButton;
    @FXML private Button stopViewingButton;
    @FXML private Label viewerPerformanceLabel;
    @FXML private ImageView videoView;

    @FXML private Label viewerStatusIndicator;
    @FXML private VBox noVideoPlaceholder;

    // Controller
    private DualModeController dualController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("🚀 DualModeMainController initializing...");

        // Initialize dual mode controller
        dualController = new DualModeController();
        dualController.initialize(
                // General callbacks
                this::onStatusUpdate,
                this::onConnectionStatusChanged,
                // Host callbacks
                this::onHostPerformanceUpdate,
                this::onViewerCountUpdate,
                this::onHostingStateChanged,
                this::onRoomCreated,
                // Viewer callbacks
                this::onFrameReceived,
                this::onViewerPerformanceUpdate,
                this::onViewingStateChanged,
                // Authentication callbacks
                this::onAuthenticationRequired,
                this::onAuthenticationSuccess
        );
        
        // Set access code callback
        dualController.setOnAccessCodeReceived(this::onAccessCodeReceived);

        // Set initial UI state
        setDisconnectedState();

        System.out.println("✅ DualModeMainController initialized");
    }

    // ==================== Connection Handlers ====================

    @FXML
    private void handleConnect() {
        String host = serverHostField.getText().trim();
        String portStr = serverPortField.getText().trim();

        if (host.isEmpty()) {
            statusLabel.setText("⚠️ Please enter server host");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            statusLabel.setText("⚠️ Invalid port number");
            return;
        }

        connectButton.setDisable(true);
        dualController.connect(host, port);
    }

    @FXML
    private void handleDisconnect() {
        dualController.disconnect();
    }

    // ==================== Host Handlers ====================

    @FXML
    private void handleStartHosting() {
        System.out.println("▶️ Start Hosting clicked");
        String roomId = hostRoomIdField.getText().trim();
        dualController.startHosting(roomId.isEmpty() ? null : roomId);
    }

    @FXML
    private void handleStopHosting() {
        System.out.println("⏹️ Stop Hosting clicked");
        dualController.stopHosting();
    }

    @FXML
    private void handleCopyRoomId() {
        String roomId = createdRoomLabel.getText();
        if (roomId != null && !roomId.equals("-")) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(roomId);
            clipboard.setContent(content);
            statusLabel.setText("📋 Room ID copied to clipboard!");
        }
    }
    
    @FXML
    private void handleCopyAccessCode() {
        if (accessCodeField != null) {
            String code = accessCodeField.getText();
            if (code != null && !code.isEmpty()) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(code);
                clipboard.setContent(content);
                statusLabel.setText("📋 Access code copied to clipboard!");
            }
        }
    }

    // ==================== Viewer Handlers ====================

    @FXML
    private void handleStartViewing() {
        System.out.println("👁️ Start Viewing clicked");
        String roomId = viewerRoomIdField.getText().trim();
        if (roomId.isEmpty()) {
            statusLabel.setText("⚠️ Please enter a room ID to view");
            return;
        }
        dualController.startViewing(roomId);
    }

    @FXML
    private void handleStopViewing() {
        System.out.println("⏹️ Stop Viewing clicked");
        dualController.stopViewing();
    }

    // ==================== Callbacks ====================

    private void onStatusUpdate(String status) {
        Platform.runLater(() -> statusLabel.setText(status));
    }

    private void onConnectionStatusChanged(Boolean connected) {
        Platform.runLater(() -> {
            if (connected) {
                setConnectedState();
            } else {
                setDisconnectedState();
            }
        });
    }

    private void onHostPerformanceUpdate(String performance) {
        Platform.runLater(() -> hostPerformanceLabel.setText(performance));
    }

    private void onViewerCountUpdate(Integer count) {
        Platform.runLater(() -> viewerCountLabel.setText(String.valueOf(count)));
    }

    private void onHostingStateChanged(Boolean isHosting) {
        Platform.runLater(() -> {
            startHostingButton.setDisable(isHosting);
            stopHostingButton.setDisable(!isHosting);
            hostRoomIdField.setDisable(isHosting);
            copyRoomIdButton.setDisable(!isHosting);

            if (isHosting) {
                hostStatusIndicator.setText("🟢 Hosting");
                hostStatusIndicator.setStyle("-fx-text-fill: #4caf50;");
            } else {
                hostStatusIndicator.setText("⚫ Not Hosting");
                hostStatusIndicator.setStyle("-fx-text-fill: #888;");
                createdRoomLabel.setText("-");
                hostPerformanceLabel.setText("FPS: - | Viewers: 0");
                
                // Hide access code section when not hosting
                if (accessCodeSection != null) {
                    accessCodeSection.setVisible(false);
                    accessCodeSection.setManaged(false);
                }
                if (accessCodeField != null) {
                    accessCodeField.setText("");
                }
            }
        });
    }

    private void onRoomCreated(String roomId) {
        Platform.runLater(() -> {
            createdRoomLabel.setText(roomId);
            copyRoomIdButton.setDisable(false);
        });
    }
    
    /**
     * Called when access code is received for password-protected rooms
     */
    private void onAccessCodeReceived(String accessCode) {
        Platform.runLater(() -> {
            if (accessCode != null && !accessCode.isEmpty()) {
                // Show access code section
                if (accessCodeSection != null) {
                    accessCodeSection.setVisible(true);
                    accessCodeSection.setManaged(true);
                }
                if (accessCodeField != null) {
                    accessCodeField.setText(accessCode);
                }
                statusLabel.setText("🔐 Room created with access code - share it with viewers!");
            } else {
                // Hide access code section for non-protected rooms
                if (accessCodeSection != null) {
                    accessCodeSection.setVisible(false);
                    accessCodeSection.setManaged(false);
                }
                if (accessCodeField != null) {
                    accessCodeField.setText("");
                }
            }
        });
    }

    private void onFrameReceived(Image frame) {
        Platform.runLater(() -> {
            videoView.setImage(frame);
            if (noVideoPlaceholder != null) {
                noVideoPlaceholder.setVisible(false);
            }
        });
    }

    private void onViewerPerformanceUpdate(String performance) {
        Platform.runLater(() -> viewerPerformanceLabel.setText(performance));
    }

    private void onViewingStateChanged(Boolean isViewing) {
        Platform.runLater(() -> {
            startViewingButton.setDisable(isViewing);
            stopViewingButton.setDisable(!isViewing);
            viewerRoomIdField.setDisable(isViewing);

            if (isViewing) {
                viewerStatusIndicator.setText("🟢 Viewing");
                viewerStatusIndicator.setStyle("-fx-text-fill: #48bb78; -fx-font-weight: bold;");
            } else {
                viewerStatusIndicator.setText("⚫ Not Connected");
                viewerStatusIndicator.setStyle("-fx-text-fill: #718096;");
                if (noVideoPlaceholder != null) {
                    noVideoPlaceholder.setVisible(true);
                }
                videoView.setImage(null);
                viewerPerformanceLabel.setText("📥 Waiting for stream...");
            }
        });
    }

    // ==================== UI State Management ====================

    private void setConnectedState() {
        connectionStatusLabel.setText("● Connected");
        connectionStatusLabel.getStyleClass().removeAll("status-disconnected");
        connectionStatusLabel.getStyleClass().add("status-connected");

        connectButton.setDisable(true);
        disconnectButton.setDisable(false);
        serverHostField.setDisable(true);
        serverPortField.setDisable(true);

        // Enable host/viewer controls
        startHostingButton.setDisable(false);
        startViewingButton.setDisable(false);
        hostRoomIdField.setDisable(false);
        viewerRoomIdField.setDisable(false);
        
        statusLabel.setText("Connected - Ready to share or watch");
    }

    private void setDisconnectedState() {
        connectionStatusLabel.setText("● Disconnected");
        connectionStatusLabel.getStyleClass().removeAll("status-connected");
        connectionStatusLabel.getStyleClass().add("status-disconnected");

        connectButton.setDisable(false);
        disconnectButton.setDisable(true);
        serverHostField.setDisable(false);
        serverPortField.setDisable(false);

        // Disable all host/viewer controls
        startHostingButton.setDisable(true);
        stopHostingButton.setDisable(true);
        startViewingButton.setDisable(true);
        stopViewingButton.setDisable(true);
        hostRoomIdField.setDisable(true);
        viewerRoomIdField.setDisable(true);
        copyRoomIdButton.setDisable(true);

        // Reset indicators
        hostStatusIndicator.setText("Not Sharing");
        hostStatusIndicator.setStyle("-fx-text-fill: #718096;");
        viewerStatusIndicator.setText("⚫ Not Connected");
        viewerStatusIndicator.setStyle("-fx-text-fill: #718096;");

        createdRoomLabel.setText("-");
        hostPerformanceLabel.setText("--");
        viewerCountLabel.setText("0");
        viewerPerformanceLabel.setText("📥 Waiting for stream...");
        if (noVideoPlaceholder != null) {
            noVideoPlaceholder.setVisible(true);
        }
        videoView.setImage(null);
        
        statusLabel.setText("Ready to connect");
    }
    
    // ==================== Authentication Callbacks ====================
    
    /**
     * Called when authentication is required (no saved credentials or token expired)
     */
    private void onAuthenticationRequired(String message) {
        Platform.runLater(() -> {
            statusLabel.setText("🔐 " + message);
            
            // Get the current window as owner
            javafx.stage.Stage owner = (javafx.stage.Stage) statusLabel.getScene().getWindow();
            
            // Show login dialog with AuthenticationService from DualModeController
            LoginDialog loginDialog = new LoginDialog(
                    dualController.getAuthService(),
                    owner
            );
            loginDialog.showAndWait().ifPresent(authResult -> {
                // LoginDialog handles the actual authentication and returns AuthResult
                // If we get here with a successful result, the user is authenticated
                if (authResult.success()) {
                    // Authentication was already handled by LoginDialog
                    // Just update UI to reflect authenticated state
                    statusLabel.setText("✅ Authenticated successfully");
                    // Try to connect again now that we're authenticated
                    handleConnect();
                } else {
                    showError("Authentication failed: " + authResult.message());
                }
            });
        });
    }
    
    /**
     * Called when authentication succeeds
     */
    private void onAuthenticationSuccess(String username) {
        Platform.runLater(() -> {
            statusLabel.setText("✅ Logged in as " + username);
        });
    }
    
    /**
     * Show error dialog to user
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
