package encoder;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameRecorder;

/**
 * Software H.264 Encoder (CPU-based fallback)
 * Optimized for ultra-low latency screen sharing
 */
public class LibX264Encoder implements VideoEncoderStrategy {
    
    @Override
    public boolean configure(FFmpegFrameRecorder recorder) {
        try {
            // Use codec ID instead of name for better compatibility
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            
            // Ultra-low latency software encoding settings
            recorder.setVideoOption("preset", "ultrafast");
            recorder.setVideoOption("tune", "zerolatency");
            
            // These options improve latency without compatibility issues
            recorder.setVideoOption("profile", "baseline");  // Simpler profile = faster
            recorder.setVideoOption("level", "3.1");         // Common level
            
            // Include SPS/PPS with every keyframe for mid-stream joining
            recorder.setVideoOption("x264opts", "repeat-headers=1");
            
            System.out.println("✅ Configured libx264 software encoder (ultrafast + zerolatency)");
            return true;
        } catch (Exception e) {
            System.err.println("❌ libx264 configuration failed: " + e.getMessage());
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
