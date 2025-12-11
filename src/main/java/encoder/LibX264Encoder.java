package encoder;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Software H.264 Encoder (CPU-based fallback)
 */
public class LibX264Encoder implements VideoEncoderStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(LibX264Encoder.class);
    
    @Override
    public boolean configure(FFmpegFrameRecorder recorder) {
        try {
            recorder.setVideoCodecName("libx264");
            
            // Ultra-low latency software encoding
            recorder.setVideoOption("preset", "ultrafast");
            recorder.setVideoOption("tune", "zerolatency");
            recorder.setVideoOption("bf", "0"); // No B-frames
            recorder.setVideoOption("refs", "1"); // Single reference frame
            recorder.setVideoOption("rc-lookahead", "0"); // No lookahead
            recorder.setVideoOption("sliced-threads", "1"); // Use sliced threads
            recorder.setVideoOption("sync-lookahead", "0"); // No sync lookahead
            
            logger.info("✅ Configured libx264 software encoder");
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
