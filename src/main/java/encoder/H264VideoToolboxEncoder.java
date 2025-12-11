package encoder;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * macOS Hardware-Accelerated Encoder using VideoToolbox
 */
public class H264VideoToolboxEncoder implements VideoEncoderStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(H264VideoToolboxEncoder.class);
    
    @Override
    public boolean configure(FFmpegFrameRecorder recorder) {
        try {
            recorder.setVideoCodecName("h264_videotoolbox");
            
            // VideoToolbox specific options for low latency
            recorder.setVideoOption("realtime", "1");
            recorder.setVideoOption("allow_sw", "0"); // Force hardware
            
            logger.info("✅ Configured VideoToolbox hardware encoder");
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
