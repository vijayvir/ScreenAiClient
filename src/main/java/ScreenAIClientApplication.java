import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Context Manager for ScreenAI Client
 * Manages Spring dependency injection without running as a server
 */
@Configuration
@ComponentScan(basePackages = {"service", "controller", "encoder", "model"})
public class ScreenAIClientApplication {
    private static ConfigurableApplicationContext springContext;

    /**
     * Start Spring context for dependency injection
     */
    public static void startSpringContext() {
        // Enable FFmpeg debug logging for troubleshooting encoder issues
        try {
            org.bytedeco.ffmpeg.global.avutil.av_log_set_level(org.bytedeco.ffmpeg.global.avutil.AV_LOG_DEBUG);
            org.bytedeco.javacv.FFmpegLogCallback.set();
            System.out.println("✅ FFmpeg debug logging enabled");
        } catch (Throwable t) {
            System.out.println("⚠️ Could not enable FFmpeg debug logging: " + t.getMessage());
        }
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

