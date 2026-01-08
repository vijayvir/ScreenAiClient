package encoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Tries hardware acceleration first, falls back to software
 */
public class VideoEncoderFactory {
    
    /**
     * Get best available encoder for current platform
     * Tries hardware encoders first, falls back to software
     */
    public static VideoEncoderStrategy getBestEncoder() {
        List<VideoEncoderStrategy> candidates = getEncoderCandidates();
        
        for (VideoEncoderStrategy encoder : candidates) {
            if (isEncoderAvailable(encoder)) {
                System.out.println("🎯 Selected encoder: " + encoder.getCodecName() + 
                                   " (" + encoder.getEncoderType() + ")");
                return encoder;
            }
        }
        
        // Fallback to software
        System.out.println("⚠️ No hardware encoder available, using software fallback");
        return new LibX264Encoder();
    }
    
    /**
     * Get ordered list of encoder candidates based on OS
     */
    private static List<VideoEncoderStrategy> getEncoderCandidates() {
        List<VideoEncoderStrategy> candidates = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        
        System.out.println("🔍 Detecting encoders for OS: " + os);
        
        if (os.contains("mac")) {
            // macOS: Try VideoToolbox first
            System.out.println("  → Trying VideoToolbox (macOS hardware encoder)");
            candidates.add(new H264VideoToolboxEncoder());
        } else if (os.contains("win")) {
            // Windows: Try NVENC first
            System.out.println("  → Trying NVENC (NVIDIA hardware encoder)");
            candidates.add(new NvencEncoder());
        } else if (os.contains("nux") || os.contains("nix")) {
            // Linux: Try NVENC first
            System.out.println("  → Trying NVENC (NVIDIA hardware encoder)");
            candidates.add(new NvencEncoder());
        }
        
        // Software fallback (always available)
        System.out.println("  → Software fallback: libx264");
        candidates.add(new LibX264Encoder());
        
        return candidates;
    }
    
    /**
     * Check if encoder is available on this system
     */
    private static boolean isEncoderAvailable(VideoEncoderStrategy encoder) {
        try {
            // Try to create a test recorder with this codec
            org.bytedeco.javacv.FFmpegFrameRecorder testRecorder = 
                new org.bytedeco.javacv.FFmpegFrameRecorder(
                    new java.io.ByteArrayOutputStream(), 640, 480);
            
            testRecorder.setFormat("mpegts");
            boolean success = encoder.configure(testRecorder);
            
            if (success) {
                try {
                    testRecorder.start();
                    testRecorder.stop();
                    System.out.println("  ✅ " + encoder.getCodecName() + " is available");
                } catch (Exception e) {
                    System.out.println("  ❌ " + encoder.getCodecName() + " failed to start: " + e.getMessage());
                    success = false;
                }
            }
            
            testRecorder.release();
            return success;
        } catch (Exception e) {
            System.out.println("  ❌ " + encoder.getCodecName() + " not available: " + e.getMessage());
            return false;
        }
    }
}
