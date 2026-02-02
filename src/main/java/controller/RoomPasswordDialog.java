package controller;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Dialog for room password entry and room password setup.
 * Used for both joining password-protected rooms and creating protected rooms.
 */
public class RoomPasswordDialog extends Dialog<RoomPasswordDialog.RoomPasswordResult> {

    private static final Logger log = LoggerFactory.getLogger(RoomPasswordDialog.class);

    // UI Components
    private TextField roomIdField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private TextField accessCodeField;
    private CheckBox requireApprovalCheckBox;
    private Label statusLabel;
    private Button submitButton;
    
    private final boolean isCreatingRoom;

    /**
     * Create a dialog for joining a password-protected room.
     */
    public RoomPasswordDialog(Stage owner, String roomId) {
        this(owner, roomId, false);
    }

    /**
     * Create a dialog for room setup (with password creation options).
     */
    public RoomPasswordDialog(Stage owner, boolean isCreatingRoom) {
        this(owner, null, isCreatingRoom);
    }

    private RoomPasswordDialog(Stage owner, String existingRoomId, boolean isCreatingRoom) {
        this.isCreatingRoom = isCreatingRoom;
        
        setTitle(isCreatingRoom ? "Create Room" : "Enter Room Password");
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);
        setResizable(false);
        
        buildUI(existingRoomId);
    }

    private void buildUI(String existingRoomId) {
        VBox mainContainer = new VBox(12);
        mainContainer.setPadding(new Insets(20));
        mainContainer.setAlignment(Pos.CENTER_LEFT);
        mainContainer.setStyle("-fx-background-color: #2D2D2D;");
        mainContainer.setPrefWidth(350);

        // Title
        Label titleLabel = new Label(isCreatingRoom ? "Create New Room" : "Room Password Required");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #4A90D9;");

        Label subtitleLabel = new Label(
                isCreatingRoom 
                        ? "Set up your room with optional password protection"
                        : "Enter the password to join this room"
        );
        subtitleLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888;");
        subtitleLabel.setWrapText(true);

        mainContainer.getChildren().addAll(titleLabel, subtitleLabel, new Separator());

        if (isCreatingRoom) {
            // Room ID field for creation
            Label roomIdLabel = new Label("Room ID:");
            roomIdLabel.setStyle("-fx-text-fill: #CCCCCC;");
            roomIdField = new TextField();
            roomIdField.setPromptText("Enter a unique room ID (3-50 chars)");
            styleTextField(roomIdField);
            mainContainer.getChildren().addAll(roomIdLabel, roomIdField);

            // Password (optional for creation)
            Label passwordLabel = new Label("Room Password (optional):");
            passwordLabel.setStyle("-fx-text-fill: #CCCCCC;");
            passwordField = new PasswordField();
            passwordField.setPromptText("Leave empty for public room");
            styleTextField(passwordField);
            
            Label confirmLabel = new Label("Confirm Password:");
            confirmLabel.setStyle("-fx-text-fill: #CCCCCC;");
            confirmPasswordField = new PasswordField();
            confirmPasswordField.setPromptText("Confirm password");
            styleTextField(confirmPasswordField);
            
            // Require approval checkbox
            requireApprovalCheckBox = new CheckBox("Require approval for viewers");
            requireApprovalCheckBox.setStyle("-fx-text-fill: #CCCCCC;");
            requireApprovalCheckBox.setSelected(false);
            
            // Info about password-protected rooms
            Label infoLabel = new Label("ℹ Password-protected rooms always require viewer approval.");
            infoLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");
            infoLabel.setWrapText(true);
            
            mainContainer.getChildren().addAll(
                    passwordLabel, passwordField,
                    confirmLabel, confirmPasswordField,
                    requireApprovalCheckBox,
                    infoLabel
            );
        } else {
            // Joining existing room
            if (existingRoomId != null) {
                Label roomLabel = new Label("Room: " + existingRoomId);
                roomLabel.setStyle("-fx-text-fill: #CCCCCC; -fx-font-weight: bold;");
                mainContainer.getChildren().add(roomLabel);
            }

            // Password or access code
            Label passwordLabel = new Label("Password:");
            passwordLabel.setStyle("-fx-text-fill: #CCCCCC;");
            passwordField = new PasswordField();
            passwordField.setPromptText("Enter room password");
            styleTextField(passwordField);

            Label orLabel = new Label("— OR —");
            orLabel.setStyle("-fx-text-fill: #666666;");
            
            Label accessCodeLabel = new Label("Access Code:");
            accessCodeLabel.setStyle("-fx-text-fill: #CCCCCC;");
            accessCodeField = new TextField();
            accessCodeField.setPromptText("Enter access code (if provided)");
            styleTextField(accessCodeField);

            mainContainer.getChildren().addAll(
                    passwordLabel, passwordField,
                    orLabel,
                    accessCodeLabel, accessCodeField
            );
        }

        // Status label
        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #FF6B6B;");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(300);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        submitButton = new Button(isCreatingRoom ? "Create Room" : "Join Room");
        submitButton.setPrefWidth(120);
        styleButton(submitButton, true);

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(80);
        styleButton(cancelButton, false);

        buttonBox.getChildren().addAll(submitButton, cancelButton);

        mainContainer.getChildren().addAll(statusLabel, buttonBox);

        getDialogPane().setContent(mainContainer);
        getDialogPane().setStyle("-fx-background-color: #2D2D2D;");

        // Button actions
        submitButton.setOnAction(e -> handleSubmit());
        cancelButton.setOnAction(e -> {
            setResult(null);
            close();
        });

        // Enter key handlers
        if (passwordField != null) {
            passwordField.setOnAction(e -> {
                if (!isCreatingRoom && (accessCodeField == null || accessCodeField.getText().isEmpty())) {
                    handleSubmit();
                }
            });
        }

        // Focus
        Platform.runLater(() -> {
            if (isCreatingRoom) {
                roomIdField.requestFocus();
            } else {
                passwordField.requestFocus();
            }
        });
    }

    private void handleSubmit() {
        if (isCreatingRoom) {
            handleCreateRoom();
        } else {
            handleJoinRoom();
        }
    }

    private void handleCreateRoom() {
        String roomId = roomIdField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        boolean requireApproval = requireApprovalCheckBox.isSelected();

        // Validation
        if (roomId.isEmpty()) {
            showError("Room ID is required");
            return;
        }
        if (roomId.length() < 3 || roomId.length() > 50) {
            showError("Room ID must be 3-50 characters");
            return;
        }
        if (!roomId.matches("^[a-zA-Z0-9_-]+$")) {
            showError("Room ID can only contain letters, numbers, hyphens, and underscores");
            return;
        }

        // Password validation (only if password provided)
        if (!password.isEmpty()) {
            if (password.length() < 4) {
                showError("Password must be at least 4 characters");
                return;
            }
            if (!password.equals(confirmPassword)) {
                showError("Passwords do not match");
                return;
            }
            // Password-protected rooms always require approval
            requireApproval = true;
        }

        RoomPasswordResult result = new RoomPasswordResult(
                roomId,
                password.isEmpty() ? null : password,
                null,
                requireApproval
        );
        setResult(result);
        close();
    }

    private void handleJoinRoom() {
        String password = passwordField.getText();
        String accessCode = accessCodeField != null ? accessCodeField.getText().trim() : null;

        if (password.isEmpty() && (accessCode == null || accessCode.isEmpty())) {
            showError("Please enter password or access code");
            return;
        }

        RoomPasswordResult result = new RoomPasswordResult(
                null,
                password.isEmpty() ? null : password,
                (accessCode == null || accessCode.isEmpty()) ? null : accessCode,
                false
        );
        setResult(result);
        close();
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: #FF6B6B;");
    }

    private void styleTextField(TextField field) {
        field.setStyle(
                "-fx-background-color: #3C3C3C; " +
                "-fx-text-fill: #FFFFFF; " +
                "-fx-border-color: #555555; " +
                "-fx-border-radius: 4px; " +
                "-fx-background-radius: 4px; " +
                "-fx-padding: 8px;"
        );
        field.setPrefWidth(300);
    }

    private void styleButton(Button button, boolean primary) {
        if (primary) {
            button.setStyle(
                    "-fx-background-color: #4A90D9; " +
                    "-fx-text-fill: white; " +
                    "-fx-font-weight: bold; " +
                    "-fx-border-radius: 4px; " +
                    "-fx-background-radius: 4px; " +
                    "-fx-padding: 8px 16px;"
            );
        } else {
            button.setStyle(
                    "-fx-background-color: #555555; " +
                    "-fx-text-fill: white; " +
                    "-fx-border-radius: 4px; " +
                    "-fx-background-radius: 4px; " +
                    "-fx-padding: 8px 16px;"
            );
        }
    }

    /**
     * Show dialog for joining a password-protected room.
     */
    public static Optional<RoomPasswordResult> showJoinDialog(Stage owner, String roomId) {
        RoomPasswordDialog dialog = new RoomPasswordDialog(owner, roomId);
        return dialog.showAndWait();
    }

    /**
     * Show dialog for creating a new room with optional password.
     */
    public static Optional<RoomPasswordResult> showCreateDialog(Stage owner) {
        RoomPasswordDialog dialog = new RoomPasswordDialog(owner, true);
        return dialog.showAndWait();
    }

    /**
     * Result of room password dialog.
     */
    public record RoomPasswordResult(
            String roomId,
            String password,
            String accessCode,
            boolean requireApproval
    ) {}
}
