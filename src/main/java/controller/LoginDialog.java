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
import service.AuthenticationService;
import service.AuthenticationService.AuthResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Login/Register dialog for ScreenAI client.
 * Provides a modal dialog for user authentication.
 */
public class LoginDialog extends Dialog<AuthResult> {

    private static final Logger log = LoggerFactory.getLogger(LoginDialog.class);

    private final AuthenticationService authService;
    
    // UI Components
    private TextField usernameField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;
    private TextField serverUrlField;
    private CheckBox rememberMeCheckbox;
    private Label statusLabel;
    private Button loginButton;
    private Button registerButton;
    private ProgressIndicator progressIndicator;
    
    private boolean isRegisterMode = false;

    public LoginDialog(AuthenticationService authService, Stage owner) {
        this.authService = authService;
        
        setTitle("ScreenAI - Login");
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);
        setResizable(false);
        
        buildUI();
    }

    private void buildUI() {
        // Main container
        VBox mainContainer = new VBox(15);
        mainContainer.setPadding(new Insets(20));
        mainContainer.setAlignment(Pos.CENTER);
        mainContainer.setStyle("-fx-background-color: #2D2D2D;");
        mainContainer.setPrefWidth(350);
        mainContainer.setMinHeight(450);  // Ensure dialog is tall enough to show all fields

        // Title
        Label titleLabel = new Label("ScreenAI");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #4A90D9;");
        
        Label subtitleLabel = new Label("Sign in to continue");
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #888888;");

        // Server URL field
        Label serverLabel = new Label("Server URL:");
        serverLabel.setStyle("-fx-text-fill: #CCCCCC;");
        serverUrlField = new TextField(authService.getServerBaseUrl());
        serverUrlField.setPromptText("http://localhost:8080");
        styleTextField(serverUrlField);

        // Username field
        Label usernameLabel = new Label("Username:");
        usernameLabel.setStyle("-fx-text-fill: #CCCCCC;");
        usernameField = new TextField();
        usernameField.setPromptText("Enter username");
        styleTextField(usernameField);

        // Password field
        Label passwordLabel = new Label("Password:");
        passwordLabel.setStyle("-fx-text-fill: #CCCCCC;");
        passwordField = new PasswordField();
        passwordField.setPromptText("Enter password");
        styleTextField(passwordField);

        // Confirm password field (for registration)
        Label confirmLabel = new Label("Confirm Password:");
        confirmLabel.setStyle("-fx-text-fill: #CCCCCC;");
        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm password");
        styleTextField(confirmPasswordField);
        confirmLabel.setVisible(false);
        confirmLabel.setManaged(false);
        confirmPasswordField.setVisible(false);
        confirmPasswordField.setManaged(false);
        
        // Remember me checkbox
        rememberMeCheckbox = new CheckBox("Remember me");
        rememberMeCheckbox.setStyle("-fx-text-fill: #CCCCCC;");
        rememberMeCheckbox.setSelected(false);

        // Status label
        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #FF6B6B;");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(300);

        // Progress indicator
        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(30, 30);
        progressIndicator.setVisible(false);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER);

        loginButton = new Button("Login");
        loginButton.setPrefWidth(100);
        styleButton(loginButton, true);

        registerButton = new Button("Register");
        registerButton.setPrefWidth(100);
        styleButton(registerButton, false);

        Button cancelButton = new Button("Cancel");
        cancelButton.setPrefWidth(80);
        styleButton(cancelButton, false);

        buttonBox.getChildren().addAll(loginButton, registerButton, cancelButton);

        // Toggle mode link
        Hyperlink toggleLink = new Hyperlink("Need an account? Register here");
        toggleLink.setStyle("-fx-text-fill: #4A90D9;");
        toggleLink.setOnAction(e -> toggleMode(confirmLabel));

        // Add all components
        mainContainer.getChildren().addAll(
                titleLabel,
                subtitleLabel,
                new Separator(),
                serverLabel, serverUrlField,
                usernameLabel, usernameField,
                passwordLabel, passwordField,
                confirmLabel, confirmPasswordField,
                rememberMeCheckbox,
                statusLabel,
                progressIndicator,
                buttonBox,
                toggleLink
        );

        getDialogPane().setContent(mainContainer);
        getDialogPane().setStyle("-fx-background-color: #2D2D2D;");

        // Button actions
        loginButton.setOnAction(e -> handleLogin());
        registerButton.setOnAction(e -> handleRegister());
        cancelButton.setOnAction(e -> {
            setResult(new AuthResult(false, "Cancelled", null));
            close();
        });

        // Enter key handlers
        passwordField.setOnAction(e -> {
            if (!isRegisterMode) {
                handleLogin();
            }
        });
        confirmPasswordField.setOnAction(e -> {
            if (isRegisterMode) {
                handleRegister();
            }
        });

        // Initial focus
        Platform.runLater(() -> usernameField.requestFocus());
    }

    private void toggleMode(Label confirmLabel) {
        isRegisterMode = !isRegisterMode;
        
        if (isRegisterMode) {
            setTitle("ScreenAI - Register");
            confirmLabel.setVisible(true);
            confirmLabel.setManaged(true);
            confirmPasswordField.setVisible(true);
            confirmPasswordField.setManaged(true);
            loginButton.setVisible(false);
            loginButton.setManaged(false);
            registerButton.setVisible(true);
            registerButton.setManaged(true);
        } else {
            setTitle("ScreenAI - Login");
            confirmLabel.setVisible(false);
            confirmLabel.setManaged(false);
            confirmPasswordField.setVisible(false);
            confirmPasswordField.setManaged(false);
            loginButton.setVisible(true);
            loginButton.setManaged(true);
            registerButton.setVisible(true);
            registerButton.setManaged(true);
        }
        
        statusLabel.setText("");
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String serverUrl = serverUrlField.getText().trim();
        boolean rememberMe = rememberMeCheckbox.isSelected();

        // Validation
        if (username.isEmpty()) {
            showError("Username is required");
            return;
        }
        if (password.isEmpty()) {
            showError("Password is required");
            return;
        }

        setLoading(true);
        authService.setServerBaseUrl(serverUrl);

        authService.login(username, password, rememberMe)
                .thenAccept(result -> Platform.runLater(() -> {
                    setLoading(false);
                    if (result.success()) {
                        log.info("Login successful (remember-me: {})", rememberMe);
                        setResult(result);
                        close();
                    } else {
                        showError(result.message());
                    }
                }));
    }

    private void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String serverUrl = serverUrlField.getText().trim();

        // Validation
        if (username.isEmpty()) {
            showError("Username is required");
            return;
        }
        if (username.length() < 3 || username.length() > 50) {
            showError("Username must be 3-50 characters");
            return;
        }
        if (password.isEmpty()) {
            showError("Password is required");
            return;
        }
        if (password.length() < 8) {
            showError("Password must be at least 8 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }

        setLoading(true);
        authService.setServerBaseUrl(serverUrl);

        authService.register(username, password)
                .thenAccept(result -> Platform.runLater(() -> {
                    setLoading(false);
                    if (result.success()) {
                        log.info("Registration successful");
                        setResult(result);
                        close();
                    } else {
                        showError(result.message());
                    }
                }));
    }

    private void setLoading(boolean loading) {
        progressIndicator.setVisible(loading);
        loginButton.setDisable(loading);
        registerButton.setDisable(loading);
        usernameField.setDisable(loading);
        passwordField.setDisable(loading);
        confirmPasswordField.setDisable(loading);
        serverUrlField.setDisable(loading);
        rememberMeCheckbox.setDisable(loading);
        
        if (loading) {
            statusLabel.setText("Please wait...");
            statusLabel.setStyle("-fx-text-fill: #888888;");
        }
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
     * Show the login dialog and return the result.
     */
    public static Optional<AuthResult> showAndWait(AuthenticationService authService, Stage owner) {
        LoginDialog dialog = new LoginDialog(authService, owner);
        return dialog.showAndWait();
    }
}
