package encoder;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * macOS Hardware-Accelerated Encoder using VideoToolbox
 * Provides significant CPU reduction through GPU encoding
 */
public class H264VideoToolboxEncoder implements VideoEncoderStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(H264VideoToolboxEncoder.class);
    
    @Override
    public boolean configure(FFmpegFrameRecorder recorder) {
        try {
            recorder.setVideoCodecName("h264_videotoolbox");
            
            // VideoToolbox specific options for low latency
            recorder.setVideoOption("realtime", "1");       // Enable realtime mode
            recorder.setVideoOption("allow_sw", "1");       // Allow software fallback if GPU busy
            recorder.setVideoOption("profile", "baseline"); // Simpler profile = faster encoding
            recorder.setVideoOption("level", "3.1");        // Common compatibility level
            
            logger.info("✅ Configured VideoToolbox hardware encoder (macOS GPU)");
            return true;
        } catch (Exception e) {
            logger.warn("❌ VideoToolbox configuration failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getCodecName() {
        return "h264_videotoolbox";
    }
    
    @Override
    public boolean isHardwareAccelerated() {
        return true;
    }
    
    @Override
    public String getEncoderType() {
        return "GPU (VideoToolbox)";
    }
    
    @Override
    public int getCpuReduction() {
        return 70; // ~70% CPU reduction vs software
    }
}
