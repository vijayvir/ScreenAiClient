package encoder;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Software H.264 Encoder (CPU-based fallback)
 * Optimized for ultra-low latency screen sharing
 */
public class LibX264Encoder implements VideoEncoderStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(LibX264Encoder.class);
    
    @Override
    public boolean configure(FFmpegFrameRecorder recorder) {
        try {
            // Use codec ID instead of name for better compatibility
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            
            // Ultra-low latency software encoding settings
            // Only use options that are widely supported
            recorder.setVideoOption("preset", "ultrafast");
            recorder.setVideoOption("tune", "zerolatency");
            
            // These options improve latency without compatibility issues
            recorder.setVideoOption("profile", "baseline");  // Simpler profile = faster
            recorder.setVideoOption("level", "3.1");         // Common level
            
            logger.info("✅ Configured libx264 software encoder (ultrafast + zerolatency)");
            return true;
        } catch (Exception e) {
            logger.warn("❌ libx264 configuration failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getCodecName() {
        return "libx264";
    }
    
    @Override
    public boolean isHardwareAccelerated() {
        return false;
    }
    
    @Override
    public String getEncoderType() {
        return "CPU (libx264)";
    }
    
    @Override
    public int getCpuReduction() {
        return 0; // No reduction (baseline)
    }
}
