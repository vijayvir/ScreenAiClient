package service;

import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;

import encoder.VideoEncoderFactory;
import encoder.VideoEncoderStrategy;

public class ScreenCaptureService {
    private final Consumer<byte[]> frameCallback;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread captureThread;
    private Thread senderThread;  // Separate thread for sending (non-blocking)
    
    private long frameCount = 0;
    private long startTime = 0;
    private final int targetFPS = 30;
    private long totalBytesTransferred = 0;
    
    private FFmpegFrameGrabber grabber;
    private FFmpegFrameRecorder recorder;
    private FlushableByteArrayOutputStream outputStream;
    private int width;
    private int height;
    
    // Encoder strategy for hardware acceleration
    private VideoEncoderStrategy encoderStrategy;
    private String activeEncoderType = "Unknown";
    
    // Async send queue - prevents blocking capture loop
    private BlockingQueue<byte[]> sendQueue;
    private static final int SEND_QUEUE_SIZE = 60;  // Buffer up to 2 seconds of frames
    
    // Platform detection
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
    private static final boolean IS_MAC = OS_NAME.contains("mac");
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");
    private static final boolean IS_LINUX = OS_NAME.contains("nux") || OS_NAME.contains("nix");

    public ScreenCaptureService(Consumer<byte[]> frameCallback) {
        this.frameCallback = frameCallback;
        FFmpegLogCallback.set();
        avutil.av_log_set_level(avutil.AV_LOG_WARNING);
        System.out.println("ScreenCaptureService initialized");
        System.out.println("Platform: " + (IS_MAC ? "macOS" : IS_WINDOWS ? "Windows" : IS_LINUX ? "Linux" : "Unknown"));
        
        // Select best encoder at initialization
        selectBestEncoder();
    }
    
    /**
     * Select the best available encoder for this platform
     */
    private void selectBestEncoder() {
        System.out.println("🔍 Detecting best encoder for platform...");
        try {
            encoderStrategy = VideoEncoderFactory.getBestEncoder();
            activeEncoderType = encoderStrategy.getEncoderType();
            System.out.println("✅ Selected encoder: " + encoderStrategy.getCodecName() + " (" + activeEncoderType + ")");
            if (encoderStrategy.isHardwareAccelerated()) {
                System.out.println("🚀 Hardware acceleration enabled! ~" + encoderStrategy.getCpuReduction() + "% CPU reduction expected");
            }
        } catch (Exception e) {
            System.err.println("⚠️ Encoder detection failed, will use default: " + e.getMessage());
            encoderStrategy = null;
        }
    }
    
    /**
     * Get the active encoder type (for UI display)
     */
    public String getActiveEncoderType() {
        return activeEncoderType;
    }
    
    /**
     * Check if hardware acceleration is active
     */
    public boolean isHardwareAccelerated() {
        return encoderStrategy != null && encoderStrategy.isHardwareAccelerated();
    }

    private static class FlushableByteArrayOutputStream extends ByteArrayOutputStream {
        public FlushableByteArrayOutputStream(int size) {
            super(size);
        }
        
        public synchronized byte[] getDataAndReset() {
            byte[] data = toByteArray();
            reset();
            return data;
        }
        
        public synchronized boolean hasData() {
            return count > 0;
        }
    }
    
    /**
     * Get screen dimensions using AWT
     */
    private Rectangle getScreenBounds() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gd = ge.getDefaultScreenDevice();
            DisplayMode dm = gd.getDisplayMode();
            int w = dm.getWidth();
            int h = dm.getHeight();
            // Ensure dimensions are even (required for video encoding)
            w = (w / 2) * 2;
            h = (h / 2) * 2;
            System.out.println("Detected screen size: " + w + "x" + h);
            return new Rectangle(0, 0, w, h);
        } catch (Exception e) {
            System.out.println("Could not detect screen size, using default 1920x1080");
            return new Rectangle(0, 0, 1920, 1080);
        }
    }

    public void start() {
        if (isRunning.get()) {
            System.out.println("Already running");
            return;
        }

        try {
            System.out.println("🚀 Starting screen capture...");
            System.out.println("🎯 Target FPS: " + targetFPS);
            System.out.println("📱 OS: " + OS_NAME);
            
            // Initialize async send queue
            sendQueue = new ArrayBlockingQueue<>(SEND_QUEUE_SIZE);
            
            if (IS_MAC) {
                startMacOSCapture();
            } else if (IS_WINDOWS) {
                startWindowsCapture();
            } else if (IS_LINUX) {
                startLinuxCapture();
            } else {
                throw new RuntimeException("Unsupported operating system: " + OS_NAME);
            }
            
            // Grabber is now started
            width = grabber.getImageWidth();
            height = grabber.getImageHeight();
            
            // Ensure dimensions are even
            if (width <= 0 || height <= 0) {
                Rectangle bounds = getScreenBounds();
                width = bounds.width;
                height = bounds.height;
            }
            width = (width / 2) * 2;
            height = (height / 2) * 2;
            
            System.out.println("📺 Grabber: " + width + "x" + height + " @ " + targetFPS + " FPS");
            
            // Output at half resolution for bandwidth efficiency
            int outWidth = (width / 2 / 2) * 2;
            int outHeight = (height / 2 / 2) * 2;
            System.out.println("📤 Output: " + outWidth + "x" + outHeight);
            
            outputStream = new FlushableByteArrayOutputStream(512 * 1024);  // 512KB buffer
            
            recorder = new FFmpegFrameRecorder(outputStream, outWidth, outHeight);
            recorder.setFormat("mpegts");
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setFrameRate(targetFPS);
            recorder.setVideoBitrate(2000000);  // 2 Mbps for better quality
            recorder.setGopSize(15);  // Keyframe every 0.5 seconds for faster viewer join
            recorder.setImageScalingFlags(swscale.SWS_FAST_BILINEAR);
            
            // Low-latency options
            recorder.setVideoOption("flags", "low_delay");
            recorder.setVideoOption("fflags", "nobuffer");
            
            // MPEGTS muxer options for better streaming
            recorder.setOption("mpegts_flags", "resend_headers");  // Resend PAT/PMT with each packet
            
            // Configure encoder using strategy pattern (hardware or software)
            boolean encoderConfigured = false;
            if (encoderStrategy != null) {
                try {
                    encoderConfigured = encoderStrategy.configure(recorder);
                    if (encoderConfigured) {
                        System.out.println("🎯 Using encoder: " + encoderStrategy.getCodecName() + " (" + activeEncoderType + ")");
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Hardware encoder configuration failed: " + e.getMessage());
                    encoderConfigured = false;
                }
            }
            
            // Fallback to software encoding if hardware failed
            if (!encoderConfigured) {
                System.out.println("📦 Using fallback software encoder (libx264)");
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
                recorder.setVideoOption("preset", "ultrafast");
                recorder.setVideoOption("tune", "zerolatency");
                recorder.setVideoOption("profile", "baseline");
                // Include SPS/PPS with every keyframe for mid-stream joining
                recorder.setVideoOption("x264opts", "repeat-headers=1");
                activeEncoderType = "CPU (libx264 fallback)";
            }
            
            System.out.println("⚙️ Starting encoder...");
            recorder.start();
            System.out.println("✅ Encoder started");
            
            isRunning.set(true);
            startTime = System.currentTimeMillis();
            frameCount = 0;
            totalBytesTransferred = 0;
            
            // Start sender thread first (async, non-blocking)
            senderThread = new Thread(this::senderLoop, "ScreenShare-Sender");
            senderThread.setDaemon(true);
            senderThread.start();
            
            // Start capture thread
            captureThread = new Thread(this::captureLoop, "ScreenShare-Capture");
            captureThread.setDaemon(true);
            captureThread.start();
            
            System.out.println("✅ Screen capture running!");
            System.out.println("📊 Encoder: " + activeEncoderType);
            
        } catch (Exception e) {
            System.err.println("❌ Start failed: " + e.getMessage());
            e.printStackTrace();
            cleanup();
            throw new RuntimeException("Start failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * macOS screen capture using AVFoundation
     */
    private void startMacOSCapture() throws Exception {
        System.out.println("Configuring AVFoundation for macOS...");
        
        // On macOS, screen capture devices are typically listed after video devices
        // Format: "video_device_index:audio_device_index" or "video:none" for video only
        // Screen devices are usually at higher indices (1, 2, 3...) 
        // Camera is typically 0
        String[] deviceConfigs = {
            "1:none",  // First screen (most common for main display)
            "2:none",  // Second screen
            "3:none",  // Third screen
            "0:none",  // Sometimes screen is at 0
            "Capture screen 0:none",  // Named screen capture
            "Capture screen 1:none"
        };
        
        Exception lastException = null;
        
        for (String deviceConfig : deviceConfigs) {
            try {
                System.out.println("Trying AVFoundation device: " + deviceConfig);
                grabber = new FFmpegFrameGrabber(deviceConfig);
                grabber.setFormat("avfoundation");
                grabber.setOption("capture_cursor", "1");
                grabber.setOption("pixel_format", "uyvy422");
                grabber.setFrameRate(targetFPS);
                grabber.start();
                System.out.println("SUCCESS: Using AVFoundation device " + deviceConfig);
                return;
            } catch (Exception e) {
                lastException = e;
                System.out.println("Device " + deviceConfig + " failed: " + e.getMessage());
                cleanupGrabber();
            }
        }
        
        throw new RuntimeException(
            "Could not open any AVFoundation screen device.\n" +
            "Please grant screen recording permission:\n" +
            "System Preferences > Privacy & Security > Screen Recording\n" +
            "Last error: " + (lastException != null ? lastException.getMessage() : "unknown"),
            lastException
        );
    }
    
    /**
     * Windows screen capture using GDI
     */
    private void startWindowsCapture() throws Exception {
        System.out.println("Configuring GDI screen capture for Windows...");
        
        Rectangle bounds = getScreenBounds();
        
        // Try different Windows capture methods
        String[][] captureConfigs = {
            {"desktop", "gdigrab"},           // Standard desktop capture
            {"screen-capture-recorder", "dshow"}, // Alternative with screen-capture-recorder
        };
        
        Exception lastException = null;
        
        for (String[] config : captureConfigs) {
            try {
                String device = config[0];
                String format = config[1];
                
                System.out.println("Trying Windows capture: " + format + " with " + device);
                grabber = new FFmpegFrameGrabber(device);
                grabber.setFormat(format);
                grabber.setFrameRate(targetFPS);
                
                if (format.equals("gdigrab")) {
                    grabber.setOption("draw_mouse", "1");
                    grabber.setOption("offset_x", "0");
                    grabber.setOption("offset_y", "0");
                    grabber.setImageWidth(bounds.width);
                    grabber.setImageHeight(bounds.height);
                }
                
                grabber.start();
                System.out.println("SUCCESS: Using " + format + " with " + device);
                return;
            } catch (Exception e) {
                lastException = e;
                System.out.println(config[1] + " failed: " + e.getMessage());
                cleanupGrabber();
            }
        }
        
        throw new RuntimeException(
            "Could not start screen capture on Windows.\n" +
            "Please ensure FFmpeg is properly installed.\n" +
            "Last error: " + (lastException != null ? lastException.getMessage() : "unknown"),
            lastException
        );
    }
    
    /**
     * Linux screen capture using X11 or PipeWire
     */
    private void startLinuxCapture() throws Exception {
        System.out.println("Configuring screen capture for Linux...");
        
        // Check display environment
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        String xdgSessionType = System.getenv("XDG_SESSION_TYPE");
        String display = System.getenv("DISPLAY");
        
        System.out.println("WAYLAND_DISPLAY: " + waylandDisplay);
        System.out.println("XDG_SESSION_TYPE: " + xdgSessionType);
        System.out.println("DISPLAY: " + display);
        
        boolean isWayland = "wayland".equalsIgnoreCase(xdgSessionType) || 
                           (waylandDisplay != null && !waylandDisplay.isEmpty());
        
        Rectangle bounds = getScreenBounds();
        Exception lastException = null;
        
        // If running on Wayland, warn the user but still try X11 (XWayland)
        if (isWayland) {
            System.out.println("========================================");
            System.out.println("WARNING: Running under Wayland!");
            System.out.println("Screen capture works best with X11.");
            System.out.println("Options:");
            System.out.println("  1. Log out and select 'Ubuntu on Xorg'");
            System.out.println("  2. Try XWayland compatibility mode");
            System.out.println("========================================");
        }
        
        // Build list of X11 configurations to try
        String displayStr = (display != null && !display.isEmpty()) ? display : ":0";
        // Remove screen number if present for base display
        String baseDisplay = displayStr.contains(".") ? 
            displayStr.substring(0, displayStr.indexOf('.')) : displayStr;
        
        String[] x11Configs = {
            baseDisplay + ".0+0,0",   // Display.screen+offset
            baseDisplay + "+0,0",     // Display+offset  
            displayStr + "+0,0",      // Original display+offset
            displayStr,               // Original display
            baseDisplay + ".0",       // Display.screen
            ":0.0+0,0",              // Fallback default
            ":0.0",                   // Fallback default no offset
            ":0+0,0",                 // Alternative
            ":1.0+0,0",              // Alternative display
        };
        
        for (String config : x11Configs) {
            try {
                System.out.println("Trying x11grab with: " + config);
                grabber = new FFmpegFrameGrabber(config);
                grabber.setFormat("x11grab");
                grabber.setFrameRate(targetFPS);
                grabber.setImageWidth(bounds.width);
                grabber.setImageHeight(bounds.height);
                grabber.setOption("draw_mouse", "1");
                
                // Additional options for better compatibility
                grabber.setOption("show_region", "0");
                
                grabber.start();
                System.out.println("SUCCESS: Using x11grab with " + config);
                return;
            } catch (Exception e) {
                lastException = e;
                System.out.println("x11grab " + config + " failed: " + e.getMessage());
                cleanupGrabber();
            }
        }
        
        // If all X11 attempts failed, provide helpful error message
        String errorMsg = "Could not start screen capture on Linux.\n\n";
        
        if (isWayland) {
            errorMsg += "You are running Wayland, which has limited screen capture support.\n\n" +
                       "RECOMMENDED SOLUTIONS:\n" +
                       "1. Switch to X11:\n" +
                       "   - Log out\n" +
                       "   - Click the gear icon on login screen\n" +
                       "   - Select 'Ubuntu on Xorg' or 'GNOME on Xorg'\n" +
                       "   - Log back in\n\n" +
                       "2. If you must use Wayland:\n" +
                       "   - Install PipeWire: sudo apt install pipewire wireplumber\n" +
                       "   - This app currently requires X11 for screen capture\n\n";
        } else {
            errorMsg += "TROUBLESHOOTING STEPS:\n" +
                       "1. Grant X11 access:\n" +
                       "   xhost +local:\n\n" +
                       "2. Install required packages:\n" +
                       "   sudo apt install ffmpeg libavdevice-dev libxcb1\n\n" +
                       "3. Check DISPLAY variable:\n" +
                       "   echo $DISPLAY\n\n" +
                       "4. If using SSH, enable X11 forwarding:\n" +
                       "   ssh -X user@host\n\n";
        }
        
        errorMsg += "Last error: " + (lastException != null ? lastException.getMessage() : "unknown");
        
        throw new RuntimeException(errorMsg, lastException);
    }
    
    private void cleanupGrabber() {
        if (grabber != null) {
            try { grabber.stop(); } catch (Exception ex) {}
            try { grabber.release(); } catch (Exception ex) {}
            grabber = null;
        }
    }

    private void captureLoop() {
        System.out.println("🎬 Capture loop started (target: " + targetFPS + " FPS)");
        
        long frameIntervalNs = 1_000_000_000L / targetFPS;  // Nanosecond precision
        int errorCount = 0;
        long lastStatsTime = System.currentTimeMillis();
        long framesAtLastStats = 0;
        
        while (isRunning.get()) {
            long frameStartNs = System.nanoTime();
            
            try {
                Frame frame = grabber.grab();
                
                if (frame == null || frame.image == null) {
                    // Yield briefly instead of sleeping to maintain capture rate
                    Thread.yield();
                    continue;
                }
                
                // Encode frame
                recorder.record(frame);
                frameCount++;
                errorCount = 0;
                
                // Send data every frame for lowest latency (non-blocking)
                if (outputStream.hasData()) {
                    byte[] data = outputStream.getDataAndReset();
                    if (data.length > 0) {
                        totalBytesTransferred += data.length;
                        
                        // Non-blocking offer to queue - if full, drop frame to prevent backup
                        if (!sendQueue.offer(data)) {
                            // Queue full - network can't keep up
                            // This prevents memory buildup and capture slowdown
                        }
                    }
                }
                
                // Print stats every 3 seconds
                long now = System.currentTimeMillis();
                if (now - lastStatsTime >= 3000) {
                    long elapsed = now - startTime;
                    long recentFrames = frameCount - framesAtLastStats;
                    double overallFps = frameCount * 1000.0 / elapsed;
                    double recentFps = recentFrames * 1000.0 / (now - lastStatsTime);
                    double kbps = (totalBytesTransferred * 8.0) / elapsed;
                    int queueSize = sendQueue != null ? sendQueue.size() : 0;
                    
                    System.out.println(String.format(
                        "📊 Frames: %d | FPS: %.1f (recent: %.1f) | Rate: %.0f kbps | Queue: %d | Encoder: %s",
                        frameCount, overallFps, recentFps, kbps, queueSize, activeEncoderType
                    ));
                    
                    lastStatsTime = now;
                    framesAtLastStats = frameCount;
                }
                
                // Precise frame timing using nanoseconds
                long elapsedNs = System.nanoTime() - frameStartNs;
                long sleepNs = frameIntervalNs - elapsedNs;
                
                if (sleepNs > 1_000_000) {  // Only sleep if > 1ms remaining
                    long sleepMs = sleepNs / 1_000_000;
                    int sleepNanos = (int) (sleepNs % 1_000_000);
                    Thread.sleep(sleepMs, sleepNanos);
                } else if (sleepNs > 0) {
                    // Busy wait for sub-millisecond precision
                    long waitUntil = frameStartNs + frameIntervalNs;
                    while (System.nanoTime() < waitUntil) {
                        Thread.yield();
                    }
                }
                
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                errorCount++;
                if (isRunning.get() && errorCount <= 3) {
                    System.err.println("⚠️ Capture error: " + e.getMessage());
                }
                if (errorCount > 10) {
                    System.err.println("❌ Too many errors, stopping capture");
                    break;
                }
            }
        }
        
        // Flush remaining data
        if (outputStream != null && outputStream.hasData()) {
            byte[] data = outputStream.getDataAndReset();
            if (data.length > 0) {
                totalBytesTransferred += data.length;
                sendQueue.offer(data);
            }
        }
        
        System.out.println("🛑 Capture loop ended");
    }
    
    /**
     * Async sender loop - sends data without blocking capture
     */
    private void senderLoop() {
        System.out.println("📤 Sender loop started");
        
        while (isRunning.get() || !sendQueue.isEmpty()) {
            try {
                // Wait up to 100ms for data
                byte[] data = sendQueue.poll(100, TimeUnit.MILLISECONDS);
                if (data != null) {
                    frameCallback.accept(data);
                }
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                if (isRunning.get()) {
                    System.err.println("⚠️ Send error: " + e.getMessage());
                }
            }
        }
        
        // Drain remaining queue
        byte[] remaining;
        while ((remaining = sendQueue.poll()) != null) {
            try {
                frameCallback.accept(remaining);
            } catch (Exception e) {
                // Ignore errors during shutdown
            }
        }
        
        System.out.println("📤 Sender loop ended");
    }

    public void stop() {
        System.out.println("🛑 Stopping...");
        isRunning.set(false);
        
        // Stop capture thread
        if (captureThread != null) {
            captureThread.interrupt();
            try {
                captureThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }
        
        // Stop sender thread
        if (senderThread != null) {
            senderThread.interrupt();
            try {
                senderThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            senderThread = null;
        }
        
        cleanup();
        
        if (startTime > 0 && frameCount > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println(String.format(
                "📊 Final Stats: %d frames, %.1f FPS, %.2f MB transferred",
                frameCount, frameCount * 1000.0 / elapsed,
                totalBytesTransferred / (1024.0 * 1024.0)
            ));
        }
        System.out.println("✅ Stopped");
    }

    private void cleanup() {
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
            }
        } catch (Exception e) {
            System.err.println("Recorder: " + e.getMessage());
        }
        
        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
                grabber = null;
            }
        } catch (Exception e) {
            System.err.println("Grabber: " + e.getMessage());
        }
        
        if (outputStream != null) {
            outputStream.reset();
            outputStream = null;
        }
    }

    public boolean isRunning() { return isRunning.get(); }
    public long getFrameCount() { return frameCount; }
    public long getTotalBytesTransferred() { return totalBytesTransferred; }
    public double getCurrentFps() {
        if (startTime == 0 || frameCount == 0) return 0;
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed > 0 ? frameCount * 1000.0 / elapsed : 0;
    }
}
