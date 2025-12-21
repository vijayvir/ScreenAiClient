package service;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.ffmpeg.global.swscale;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ScreenCaptureService {
    private final Consumer<byte[]> frameCallback;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private Thread captureThread;
    
    private long frameCount = 0;
    private long startTime = 0;
    private int targetFPS = 15; // AVFoundation supports 1-30 FPS
    private long totalBytesTransferred = 0;
    
    private FFmpegFrameGrabber grabber;
    private FFmpegFrameRecorder recorder;
    private FlushableByteArrayOutputStream outputStream;
    private int width;
    private int height;

    public ScreenCaptureService(Consumer<byte[]> frameCallback) {
        this.frameCallback = frameCallback;
        FFmpegLogCallback.set();
        avutil.av_log_set_level(avutil.AV_LOG_WARNING);
        System.out.println("ScreenCaptureService initialized");
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

    public void start() {
        if (isRunning.get()) {
            System.out.println("Already running");
            return;
        }

        try {
            System.out.println("Starting screen capture...");
            
            String osName = System.getProperty("os.name").toLowerCase();
            System.out.println("OS: " + osName);
            
            if (osName.contains("mac")) {
                System.out.println("Configuring AVFoundation for macOS...");
                // On macOS, screen capture devices are typically listed after video devices
                // Format: "video_device_index:audio_device_index" or "video:none" for video only
                // Screen devices are usually at higher indices (1, 2, 3...) 
                // Camera is typically 0
                // Using "none" for audio part means video only
                String[] deviceConfigs = {
                    "1:none",  // First screen (most common for main display)
                    "2:none",  // Second screen
                    "3:none",  // Third screen
                    "Capture screen 0:none",  // Named screen capture
                    "Capture screen 1:none"
                };
                boolean started = false;
                
                for (String deviceConfig : deviceConfigs) {
                    try {
                        System.out.println("Trying AVFoundation device: " + deviceConfig);
                        grabber = new FFmpegFrameGrabber(deviceConfig);
                        grabber.setFormat("avfoundation");
                        grabber.setOption("capture_cursor", "1");
                        grabber.setOption("pixel_format", "uyvy422"); // Common format for AVFoundation
                        grabber.setFrameRate(targetFPS);
                        grabber.start();
                        System.out.println("SUCCESS: Using device " + deviceConfig);
                        started = true;
                        break;
                    } catch (Exception e) {
                        System.out.println("Device " + deviceConfig + " failed: " + e.getMessage());
                        if (grabber != null) {
                            try { grabber.stop(); } catch (Exception ex) {}
                            try { grabber.release(); } catch (Exception ex) {}
                            grabber = null;
                        }
                    }
                }
                
                if (!started) {
                    throw new RuntimeException("Could not open any AVFoundation screen device. Grant screen recording permission in System Preferences > Privacy & Security > Screen Recording");
                }
            } else if (osName.contains("win")) {
                grabber = new FFmpegFrameGrabber("desktop");
                grabber.setFormat("gdigrab");
                grabber.setFrameRate(targetFPS);
                System.out.println("Starting grabber...");
                grabber.start();
            } else {
                grabber = new FFmpegFrameGrabber(":0.0");
                grabber.setFormat("x11grab");
                grabber.setFrameRate(targetFPS);
                System.out.println("Starting grabber...");
                grabber.start();
            }
            
            // Grabber is now started (macOS starts in the loop above)
            
            width = grabber.getImageWidth();
            height = grabber.getImageHeight();
            width = (width / 2) * 2;
            height = (height / 2) * 2;
            
            System.out.println("Grabber: " + width + "x" + height + " @ " + targetFPS + " FPS");
            
            int outWidth = (width / 2 / 2) * 2;
            int outHeight = (height / 2 / 2) * 2;
            System.out.println("Output: " + outWidth + "x" + outHeight);
            
            outputStream = new FlushableByteArrayOutputStream(256 * 1024);
            
            recorder = new FFmpegFrameRecorder(outputStream, outWidth, outHeight);
            recorder.setFormat("mpegts");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setFrameRate(targetFPS);
            recorder.setVideoBitrate(600000);
            recorder.setGopSize(targetFPS);
            recorder.setVideoOption("preset", "ultrafast");
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setImageScalingFlags(swscale.SWS_BILINEAR);
            
            System.out.println("Starting encoder...");
            recorder.start();
            System.out.println("Encoder started");
            
            isRunning.set(true);
            startTime = System.currentTimeMillis();
            frameCount = 0;
            totalBytesTransferred = 0;
            
            captureThread = new Thread(this::captureLoop, "CaptureThread");
            captureThread.setDaemon(true);
            captureThread.start();
            
            System.out.println("Capture running!");
            
        } catch (Exception e) {
            System.err.println("Start failed: " + e.getMessage());
            e.printStackTrace();
            cleanup();
            throw new RuntimeException("Start failed: " + e.getMessage(), e);
        }
    }

    private void captureLoop() {
        System.out.println("Capture loop started");
        
        long frameIntervalMs = 1000 / targetFPS;
        int errorCount = 0;
        int gopSize = targetFPS;
        int framesInGop = 0;
        
        while (isRunning.get()) {
            long start = System.currentTimeMillis();
            
            try {
                Frame frame = grabber.grab();
                
                if (frame == null || frame.image == null) {
                    Thread.sleep(5);
                    continue;
                }
                
                recorder.record(frame);
                frameCount++;
                framesInGop++;
                errorCount = 0;
                
                if (framesInGop >= gopSize && outputStream.hasData()) {
                    byte[] data = outputStream.getDataAndReset();
                    if (data.length > 0) {
                        totalBytesTransferred += data.length;
                        frameCallback.accept(data);
                    }
                    framesInGop = 0;
                }
                
                if (frameCount % (targetFPS * 2) == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double fps = frameCount * 1000.0 / elapsed;
                    double kbps = (totalBytesTransferred * 8.0) / elapsed;
                    System.out.println(String.format(
                        "Frames: %d | FPS: %.1f | Data: %.0f KB | Rate: %.0f kbps",
                        frameCount, fps, totalBytesTransferred / 1024.0, kbps
                    ));
                }
                
                long elapsed = System.currentTimeMillis() - start;
                long sleep = frameIntervalMs - elapsed;
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
                
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                errorCount++;
                if (isRunning.get() && errorCount <= 3) {
                    System.err.println("Error: " + e.getMessage());
                }
                if (errorCount > 10) break;
            }
        }
        
        if (outputStream != null && outputStream.hasData()) {
            byte[] data = outputStream.getDataAndReset();
            if (data.length > 0) {
                totalBytesTransferred += data.length;
                frameCallback.accept(data);
            }
        }
        
        System.out.println("Capture loop ended");
    }

    public void stop() {
        System.out.println("Stopping...");
        isRunning.set(false);
        
        if (captureThread != null) {
            captureThread.interrupt();
            try {
                captureThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            captureThread = null;
        }
        
        cleanup();
        
        if (startTime > 0 && frameCount > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            System.out.println(String.format(
                "Stats: %d frames, %.1f FPS, %.2f MB",
                frameCount, frameCount * 1000.0 / elapsed,
                totalBytesTransferred / (1024.0 * 1024.0)
            ));
        }
        System.out.println("Stopped");
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
