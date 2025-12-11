package application;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Context Manager for ScreenAI Client
 * Manages Spring dependency injection without running as a server
 */
@Configuration
@ComponentScan(basePackages = {"application", "service", "controller", "config"})
public class ScreenAIClientApplication {
    private static ConfigurableApplicationContext springContext;

    /**
     * Start Spring context for dependency injection
     */
    public static void startSpringContext() {
        springContext = new AnnotationConfigApplicationContext(ScreenAIClientApplication.class);
        System.out.println("✅ Spring Context initialized for dependency injection");
    }

    /**
     * Get Spring context (for accessing beans)
     */
    public static ConfigurableApplicationContext getSpringContext() {
        return springContext;
    }

    /**
     * Stop Spring context
     */
    public static void stopSpringContext() {
        if (springContext != null) {
            springContext.close();
            System.out.println("✅ Spring Context closed");
        }
    }
}

