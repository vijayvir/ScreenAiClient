package service;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

import model.ScreenSource;
import model.ScreenSource.SourceType;

/**
 * Screen Source Detector Service
 * Detects available screens and windows for capture
 */
@Service
public class ScreenSourceDetector {

    /**
     * Detect all available screen sources
     */
    public List<ScreenSource> detectAvailableSources() {
        List<ScreenSource> sources = new ArrayList<>();
        sources.addAll(detectScreens());
        return sources;
    }

    /**
     * Detect all available screens/displays
     */
    private List<ScreenSource> detectScreens() {
        List<ScreenSource> screens = new ArrayList<>();

        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] devices = ge.getScreenDevices();

            for (int i = 0; i < devices.length; i++) {
                GraphicsDevice device = devices[i];
                Rectangle bounds = device.getDefaultConfiguration().getBounds();
                int width = bounds.width;
                int height = bounds.height;
                boolean isPrimary = device == ge.getDefaultScreenDevice();

                ScreenSource source = new ScreenSource.Builder()
                    .id("screen-" + i)
                    .type(SourceType.ENTIRE_SCREEN)
                    .title("Screen " + (i + 1) + " (" + width + "x" + height + ")")
                    .appName("System")
                    .isPrimary(isPrimary)
                    .build();

                screens.add(source);
            }
        } catch (Exception e) {
            // Error detecting screens
        }

        return screens;
    }

    /**
     * Detect open application windows
     */
    public List<ScreenSource> detectWindows() {
        List<ScreenSource> windows = new ArrayList<>();

        try {
            // Platform-specific window detection
            String os = System.getProperty("os.name", "").toLowerCase();

            if (os.contains("mac")) {
                // macOS window detection
            } else if (os.contains("win")) {
                // Windows window detection
            } else if (os.contains("linux")) {
                // Linux window detection
            }
        } catch (Exception e) {
            // Error detecting windows
        }

        return windows;
    }

    /**
     * Get screen properties
     */
    public ScreenSource getScreenProperties(int screenIndex) {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] devices = ge.getScreenDevices();

            if (screenIndex >= 0 && screenIndex < devices.length) {
                GraphicsDevice device = devices[screenIndex];
                Rectangle bounds = device.getDefaultConfiguration().getBounds();

                return new ScreenSource.Builder()
                    .id("screen-" + screenIndex)
                    .type(SourceType.ENTIRE_SCREEN)
                    .title("Screen " + (screenIndex + 1))
                    .isPrimary(device == ge.getDefaultScreenDevice())
                    .build();
            }
        } catch (Exception e) {
            // Error getting screen properties
        }

        return null;
    }

    /**
     * Get total number of screens
     */
    public int getScreenCount() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            return ge.getScreenDevices().length;
        } catch (Exception e) {
            return 0;
        }
    }
}

