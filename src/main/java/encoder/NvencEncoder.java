package encoder;

import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NVIDIA NVENC Hardware-Accelerated Encoder
 */
public class NvencEncoder implements VideoEncoderStrategy {
    
    private static final Logger logger = LoggerFactory.getLogger(NvencEncoder.class);
    
    @Override
    public boolean configure(FFmpegFrameRecorder recorder) {
        try {
            recorder.setVideoCodecName("h264_nvenc");
            
            // NVENC low-latency options
            recorder.setVideoOption("preset", "p1"); // Fastest preset (P1-P7)
            recorder.setVideoOption("tune", "ll"); // Low latency
            recorder.setVideoOption("rc", "cbr"); // Constant bitrate
            recorder.setVideoOption("delay", "0"); // No B-frame delay
            recorder.setVideoOption("zerolatency", "1");
            recorder.setVideoOption("strict_gop", "1"); // Strict GOP
            
            logger.info("✅ Configured NVENC hardware encoder");
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
