import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import controller.MainController;
import controller.DualModeMainController;
import org.springframework.context.ConfigurableApplicationContext;
import java.io.IOException;
import java.util.List;

/**
 * ScreenAI Client - JavaFX + Spring Application
 * Supports both classic mode (separate host/viewer) and dual mode (bidirectional)
 */
public class App extends Application {
    private ConfigurableApplicationContext springContext;
    private static boolean useDualMode = true; // Default to dual mode

    @Override
    public void start(Stage stage) throws IOException {
        // Check command line arguments for mode selection
        List<String> args = getParameters().getRaw();
        if (args.contains("--classic")) {
            useDualMode = false;
            System.out.println("🔧 Running in CLASSIC mode (separate host/viewer)");
        } else {
            useDualMode = true;
            System.out.println("🔧 Running in DUAL mode (bidirectional screen sharing)");
        }

        // Initialize Spring context for dependency injection
        System.out.println("🚀 Initializing Spring context...");
        ScreenAIClientApplication.startSpringContext();
        springContext = ScreenAIClientApplication.getSpringContext();
        System.out.println("✅ Spring context initialized");
        
        // Load appropriate FXML based on mode
        FXMLLoader fxmlLoader;
        if (useDualMode) {
            System.out.println("� Loading Dual Mode UI...");
            fxmlLoader = new FXMLLoader(App.class.getResource("/ui/dual-mode.fxml"));
            // Dual mode controller is NOT a Spring bean, create it manually
            fxmlLoader.setControllerFactory(controllerClass -> {
                if (controllerClass == DualModeMainController.class) {
                    return new DualModeMainController();
                }
                return springContext.getBean(controllerClass);
            });
        } else {
            System.out.println("📺 Loading Classic Mode UI...");
            fxmlLoader = new FXMLLoader(App.class.getResource("/ui/main.fxml"));
            fxmlLoader.setControllerFactory(springContext::getBean);
        }

        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        stage.setTitle("ScreenAI - " + (useDualMode ? "Bidirectional Screen Sharing" : "Screen Sharing"));
        stage.setScene(scene);

        // Handle window close
        stage.setOnCloseRequest(e -> {
            System.out.println("🔌 Closing application...");
            ScreenAIClientApplication.stopSpringContext();
            System.exit(0);
        });

        stage.show();
        System.out.println("✅ JavaFX UI loaded successfully");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

