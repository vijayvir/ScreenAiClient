package encoder;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NVIDIA NVENC Hardware-Accelerated Encoder
 * Requires NVIDIA GPU with NVENC support (GTX 600+, RTX series)
 */
public class NvencEncoder implements VideoEncoderStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(NvencEncoder.class);
    
    @Override
    public boolean configure(FFmpegFrameRecorder recorder) {
        try {
            recorder.setVideoCodecName("h264_nvenc");
            
            // NVENC low-latency options (use only widely supported options)
            recorder.setVideoOption("preset", "p1");        // Fastest preset (P1 = lowest latency)
            recorder.setVideoOption("tune", "ll");          // Low latency tuning
            recorder.setVideoOption("profile", "baseline"); // Simple profile for speed
            recorder.setVideoOption("rc", "cbr");           // Constant bitrate for consistency
            recorder.setVideoOption("delay", "0");          // Zero encoding delay
            recorder.setVideoOption("zerolatency", "1");    // Force zero latency mode
            
            logger.info("✅ Configured NVENC hardware encoder (NVIDIA GPU)");
            return true;
        } catch (Exception e) {
            logger.warn("❌ NVENC configuration failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getCodecName() {
        return "h264_nvenc";
    }
    
    @Override
    public boolean isHardwareAccelerated() {
        return true;
    }
    
    @Override
    public String getEncoderType() {
        return "GPU (NVENC)";
    }
    
    @Override
    public int getCpuReduction() {
        return 80; // ~80% CPU reduction vs software
    }
}
