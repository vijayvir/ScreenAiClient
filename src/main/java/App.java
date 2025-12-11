import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import application.ScreenAIClientApplication;
import controller.MainController;
import org.springframework.context.ConfigurableApplicationContext;
import service.ServerConnectionService;
import java.io.IOException;

/**
 * ScreenAI Client - JavaFX + Spring Application
 * Main entry point combining JavaFX UI with Spring dependency injection
 */
public class App extends Application {
    private ConfigurableApplicationContext springContext;

    @Override
    public void start(Stage stage) throws IOException {
        // Initialize Spring context for dependency injection
        System.out.println("🚀 Initializing Spring context...");
        ScreenAIClientApplication.startSpringContext();
        springContext = ScreenAIClientApplication.getSpringContext();
        System.out.println("✅ Spring context initialized");
        
        // Debug: Check if MainController bean exists
        System.out.println("🔍 Checking for MainController bean...");
        try {
            MainController controller = springContext.getBean(MainController.class);
            System.out.println("✅ MainController bean found: " + controller);
        } catch (Exception e) {
            System.err.println("❌ MainController bean NOT found!");
            System.err.println("❌ Error: " + e.getMessage());
            System.out.println("📝 Available beans:");
            for (String beanName : springContext.getBeanDefinitionNames()) {
                System.out.println("   - " + beanName);
            }
        }

        // Load JavaFX FXML
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource("/ui/main.fxml"));

        // Set Spring context as controller factory for dependency injection in FXML
        fxmlLoader.setControllerFactory(springContext::getBean);

        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        stage.setTitle("ScreenAI - Screen Sharing Application");
        stage.setScene(scene);

        // Handle window close
        stage.setOnCloseRequest(e -> {
            System.out.println("🔌 Closing application...");

            // Note: ServerConnectionService instances are created by controllers, not Spring beans
            // Controllers will handle their own cleanup via disconnect() methods
            // This is handled by the MainController or individual controllers

            // Stop Spring context
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

