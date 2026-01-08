package encoder;

import org.bytedeco.javacv.FFmpegFrameRecorder;

/**
 * macOS Hardware-Accelerated Encoder using VideoToolbox
 * Provides significant CPU reduction through GPU encoding
 */
public class H264VideoToolboxEncoder implements VideoEncoderStrategy {
    
    @Override
    public boolean configure(FFmpegFrameRecorder recorder) {
        try {
            recorder.setVideoCodecName("h264_videotoolbox");
            
            // VideoToolbox specific options for low latency
            recorder.setVideoOption("realtime", "1");       // Enable realtime mode
            recorder.setVideoOption("allow_sw", "1");       // Allow software fallback if GPU busy
            recorder.setVideoOption("profile", "baseline"); // Simpler profile = faster encoding
            recorder.setVideoOption("level", "3.1");        // Common compatibility level
            
            System.out.println("✅ Configured VideoToolbox hardware encoder (macOS GPU)");
            return true;
        } catch (Exception e) {
            System.err.println("❌ VideoToolbox configuration failed: " + e.getMessage());
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
