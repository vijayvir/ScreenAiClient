package service;

import org.bytedeco.javacv.*;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacpp.BytePointer;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Screen Capture Service with H.264 Encoding
 * Uses PURE JavaCV/FFmpeg for screen capture and encoding
 * NO AWT/Robot - 100% JavaCV solution
 *
 * Note: Not a Spring @Service - instantiated manually by controllers
 * because it requires a Consumer<byte[]> callback parameter
 */
public class ScreenCaptureService {
    private final Consumer<byte[]> frameCallback;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private long frameCount = 0;
    private long startTime = 0;
    private int targetFPS = 15;
    private long totalBytesTransferred = 0;
    
    // JavaCV components - pure JavaCV, no AWT
    private FFmpegFrameGrabber grabber;  // For screen capture
    private FFmpegFrameRecorder recorder; // For H.264 encoding
    private java.io.PipedInputStream encodedDataStream; // For reading encoded H.264 data
    private int width;
    private int height;

    public ScreenCaptureService(Consumer<byte[]> frameCallback) {
        this.frameCallback = frameCallback;
        System.out.println("📹 ScreenCaptureService initialized (Pure JavaCV)");
    }

    public void start() {
        if (isRunning.get()) {
            System.out.println("⚠️ Screen capture already running");
            return;
        }
        
        try {
            System.out.println("🎬 Initializing Pure JavaCV screen capture...");
            
            // Initialize screen grabber using platform-specific input
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("mac")) {
                // macOS: avfoundation
                System.out.println("🍎 Detected macOS - using AVFoundation");
                grabber = new FFmpegFrameGrabber("Capture screen 0");
                grabber.setFormat("avfoundation");
            } else if (os.contains("win")) {
                // Windows: gdigrab
                System.out.println("🪟 Detected Windows - using GDIGrab");
                grabber = new FFmpegFrameGrabber("desktop");
                grabber.setFormat("gdigrab");
            } else {
                // Linux: x11grab
                System.out.println("🐧 Detected Linux - using X11Grab");
                grabber = new FFmpegFrameGrabber(":0.0");
                grabber.setFormat("x11grab");
            }
            
            // Set frame rate for capture
            grabber.setFrameRate(targetFPS);
            
            System.out.println("🔍 Starting screen grabber...");
            grabber.start();
            
            // Get dimensions from first frame
            org.bytedeco.javacv.Frame firstFrame = grabber.grab();
            if (firstFrame == null) {
                throw new Exception("Failed to capture first frame");
            }
            
            width = Math.min(1280, firstFrame.imageWidth);
            height = Math.min(720, firstFrame.imageHeight);
            
            System.out.println("✅ Screen grabber started");
            System.out.println("   - Original resolution: " + firstFrame.imageWidth + "x" + firstFrame.imageHeight);
            System.out.println("   - Encoding resolution: " + width + "x" + height);
            
            // Initialize H.264 recorder using in-memory stream
            // Create a pipe to capture encoded data
            java.io.PipedOutputStream pipedOut = new java.io.PipedOutputStream();
            java.io.PipedInputStream pipedIn = new java.io.PipedInputStream(pipedOut, 10 * 1024 * 1024); // 10MB buffer
            
            recorder = new FFmpegFrameRecorder(pipedOut, width, height);
            
            // H.264 encoding settings
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFormat("h264");
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setFrameRate(targetFPS);
            
            // Quality settings
            recorder.setVideoBitrate(1000000); // 1 Mbps
            
            // H.264 options for low latency streaming
            recorder.setVideoOption("preset", "ultrafast");
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setVideoOption("profile", "baseline");
            recorder.setVideoOption("annexb", "1"); // Annex B format (with start codes)
            
            // GOP settings
            recorder.setGopSize(30); // Keyframe every 30 frames
            
            System.out.println("🎬 Starting H.264 encoder...");
            recorder.start();
            System.out.println("✅ H.264 encoder started successfully");
            
            // Store the input stream for reading encoded data
            this.encodedDataStream = pipedIn;
            
        } catch (Exception e) {
            System.err.println("❌ Failed to start capture/encoder: " + e.getMessage());
            e.printStackTrace();
            cleanup();
            return;
        }
        
        isRunning.set(true);
        startTime = System.currentTimeMillis();
        frameCount = 0;
        totalBytesTransferred = 0;

        Thread captureThread = new Thread(this::captureLoop);
        captureThread.setDaemon(true);
        captureThread.setName("ScreenCapture-JavaCV-Thread");
        captureThread.start();
        
        System.out.println("🎥 Pure JavaCV screen capture started");
    }

    private void captureLoop() {
        System.out.println("🔄 Pure JavaCV capture loop started (FPS: " + targetFPS + ")");
        
        // Buffer for reading encoded H.264 packets
        byte[] readBuffer = new byte[512 * 1024]; // 512KB read buffer
        long frameDelay = 1000 / targetFPS; // milliseconds per frame

        while (isRunning.get()) {
            try {
                long frameStart = System.currentTimeMillis();
                
                // Grab frame from screen
                org.bytedeco.javacv.Frame capturedFrame = grabber.grab();
                
                if (capturedFrame == null || capturedFrame.image == null) {
                    System.err.println("⚠️ Null frame captured, skipping...");
                    Thread.sleep(frameDelay);
                    continue;
                }
                
                // Encode frame to H.264
                recorder.record(capturedFrame);
                
                // Read encoded data from the piped stream
                if (encodedDataStream.available() > 0) {
                    int bytesRead = encodedDataStream.read(readBuffer);
                    if (bytesRead > 0) {
                        // Create a properly sized byte array with the encoded data
                        byte[] encodedFrame = new byte[bytesRead];
                        System.arraycopy(readBuffer, 0, encodedFrame, 0, bytesRead);
                        
                        // Send encoded frame via callback
                        frameCallback.accept(encodedFrame);
                        frameCount++;
                        totalBytesTransferred += bytesRead;
                        
                        if (frameCount % 30 == 0) { // Log every 30 frames (~2 seconds)
                            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                            double actualFPS = elapsed > 0 ? (double) frameCount / elapsed : 0;
                            System.out.println(String.format("📊 Frames: %d, FPS: %.1f, Data: %d KB", 
                                frameCount, actualFPS, totalBytesTransferred / 1024));
                        }
                    }
                }
                
                // Maintain frame rate
                long frameTime = System.currentTimeMillis() - frameStart;
                if (frameTime < frameDelay) {
                    Thread.sleep(frameDelay - frameTime);
                }

            } catch (Exception e) {
                System.err.println("❌ Capture error: " + e.getMessage());
                if (frameCount % 30 == 0) {
                    e.printStackTrace();
                }
            }
        }
        
        System.out.println("⏹️ Pure JavaCV capture loop stopped");
    }

    public void stop() {
        if (!isRunning.get()) {
            System.out.println("⚠️ Screen capture not running");
            return;
        }
        
        System.out.println("🛑 Stopping Pure JavaCV screen capture...");
        isRunning.set(false);
        
        // Wait for capture thread to finish
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        cleanup();
        
        long duration = (System.currentTimeMillis() - startTime) / 1000;
        double avgFPS = duration > 0 ? (double) frameCount / duration : 0;
        
        System.out.println("📊 Final statistics:");
        System.out.println("   - Total frames: " + frameCount);
        System.out.println("   - Duration: " + duration + " seconds");
        System.out.println("   - Average FPS: " + String.format("%.2f", avgFPS));
        System.out.println("   - Total data: " + (totalBytesTransferred / 1024) + " KB");
        System.out.println("✅ Pure JavaCV screen capture stopped");
    }
    
    private void cleanup() {
        // Close encoded data stream
        if (encodedDataStream != null) {
            try {
                encodedDataStream.close();
                System.out.println("✅ Encoded data stream closed");
            } catch (Exception e) {
                System.err.println("⚠️ Error closing stream: " + e.getMessage());
            }
            encodedDataStream = null;
        }
        
        // Stop and release encoder
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
                System.out.println("✅ H.264 encoder stopped and released");
            } catch (Exception e) {
                System.err.println("⚠️ Error stopping encoder: " + e.getMessage());
            }
            recorder = null;
        }
        
        // Stop and release grabber
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
                System.out.println("✅ Screen grabber stopped and released");
            } catch (Exception e) {
                System.err.println("⚠️ Error stopping grabber: " + e.getMessage());
            }
            grabber = null;
        }
    }

    public int getFPS() {
        if (startTime == 0) return 0;
        long elapsed = System.currentTimeMillis() - startTime;
        return (int) (frameCount * 1000 / Math.max(elapsed, 1));
    }

    public int getBitrate() {
        if (startTime == 0) return 0;
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed < 1000) return 0;
        // Calculate bits per second, convert to kbps
        return (int) ((totalBytesTransferred * 8) / (elapsed / 1000.0) / 1000);
    }

    public long getFrameCount() {
        return frameCount;
    }

    public boolean isRunning() {
        return isRunning.get();
    }
    
    public void setTargetFPS(int fps) {
        this.targetFPS = fps;
    }
}
