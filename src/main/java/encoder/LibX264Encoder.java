package encoder;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Software H.264 Encoder (CPU-based fallback)
 * Uses OpenH264 which is bundled with JavaCV FFmpeg
 * Optimized for low latency screen sharing
 */
public class LibX264Encoder implements VideoEncoderStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(LibX264Encoder.class);
    
    @Override
    public boolean configure(FFmpegFrameRecorder recorder) {
        try {
            // Use OpenH264 encoder which is bundled with JavaCV's FFmpeg
            // libx264 requires GPL and isn't included by default
            recorder.setVideoCodecName("libopenh264");
            
            // OpenH264 specific options for low latency
            // Note: OpenH264 uses different option names than libx264
            recorder.setVideoOption("allow_skip_frames", "0");  // Don't skip frames
            recorder.setVideoOption("rc_mode", "bitrate");      // Constant bitrate mode
            
            logger.info("✅ Configured OpenH264 software encoder");
            return true;
        } catch (Exception e) {
            logger.warn("❌ OpenH264 configuration failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getCodecName() {
        return "libopenh264";
    }
    
    @Override
    public boolean isHardwareAccelerated() {
        return false;
    }
    
    @Override
    public String getEncoderType() {
        return "CPU (OpenH264)";
    }
    
    @Override
    public int getCpuReduction() {
        return 0; // No reduction (baseline)
    }
}
