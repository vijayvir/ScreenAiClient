package encoder;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tries hardware acceleration first, falls back to software
 */
public class VideoEncoderFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(VideoEncoderFactory.class);
    
    /**
     * Get best available encoder for current platform
     * Tries hardware encoders first, falls back to software
     */
    public static VideoEncoderStrategy getBestEncoder() {
        List<VideoEncoderStrategy> candidates = getEncoderCandidates();
        
        for (VideoEncoderStrategy encoder : candidates) {
            if (isEncoderAvailable(encoder)) {
                logger.info("🎯 Selected encoder: {} ({})", 
                           encoder.getCodecName(), 
                           encoder.getEncoderType());
                return encoder;
            }
        }
        
        // Fallback to software
        logger.warn("⚠️ No hardware encoder available, using software fallback");
        return new LibX264Encoder();
    }
    
    /**
     * Get ordered list of encoder candidates based on OS
     */
    private static List<VideoEncoderStrategy> getEncoderCandidates() {
        List<VideoEncoderStrategy> candidates = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase();
        
        if (os.contains("mac")) {
            // macOS: Try VideoToolbox first
            candidates.add(new H264VideoToolboxEncoder());
        } else if (os.contains("windows") || os.contains("linux")) {
            // Windows/Linux: Try NVENC first
            candidates.add(new NvencEncoder());
        }
        
        // Software fallback (always available)
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
                    new java.io.ByteArrayOutputStream(), 1920, 1080);
            
            boolean success = encoder.configure(testRecorder);
            testRecorder.release();
            
            return success;
        } catch (Exception e) {
            logger.debug("Encoder {} not available: {}", encoder.getCodecName(), e.getMessage());
            return false;
        }
    }
}
