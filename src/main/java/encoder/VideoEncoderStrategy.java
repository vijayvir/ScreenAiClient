package encoder;

import org.bytedeco.javacv.FFmpegFrameRecorder;

/**
 * Allows runtime selection of encoding algorithm (GPU vs CPU)
 */
public interface VideoEncoderStrategy {
    
    /**
     * Configure the recorder with codec-specific settings
     * @param recorder The FFmpeg recorder to configure
     * @return true if configuration successful
     */
    boolean configure(FFmpegFrameRecorder recorder);
    
    /**
     * Get the codec name
     */
    String getCodecName();
    
    /**
     * Check if this encoder uses hardware acceleration
     */
    boolean isHardwareAccelerated();
    
    /**
     * Get the encoder type (for metrics)
     */
    String getEncoderType();
    
    /**
     * Get estimated CPU usage reduction (0-100%)
     */
    int getCpuReduction();
}
