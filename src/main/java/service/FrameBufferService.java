package service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Frame Buffer Service for video streaming
 * Manages a buffer of video frames to handle network jitter and ensure smooth playback.
 * Also handles init segment caching for H.264 fMP4 streams.
 */
@Service
public class FrameBufferService {
    private static final Logger logger = LoggerFactory.getLogger(FrameBufferService.class);
    
    // Buffer size: 3 seconds at 30 FPS = 90 frames
    private static final int MAX_BUFFER_SIZE = 90;

    private final BlockingQueue<byte[]> frameQueue = new LinkedBlockingQueue<>(MAX_BUFFER_SIZE);
    
    // Statistics
    private final AtomicLong totalFramesReceived = new AtomicLong(0);
    private final AtomicLong totalFramesDropped = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);

    // Init segment caching
    private byte[] initSegment = null;
    private volatile boolean initSegmentReceived = false;

    /**
     * Cache the init segment for decoder initialization
     * @param data The fMP4 init segment (ftyp + moov boxes)
     */
    public void setInitSegment(byte[] data) {
        if (data == null || data.length == 0) {
            logger.warn("⚠️ Attempted to set empty init segment");
            return;
        }
        
        this.initSegment = data.clone();  // Clone to prevent external modification
        this.initSegmentReceived = true;
        logger.info("🎬 Init segment cached: {} bytes", data.length);
    }

    /**
     * Get the cached init segment
     * @return The init segment data, or null if not received yet
     */
    public byte[] getInitSegment() {
        return initSegment;
    }

    /**
     * Check if init segment has been received
     */
    public boolean hasInitSegment() {
        return initSegmentReceived;
    }

    /**
     * Add a video frame to the buffer
     * If buffer is full, the oldest frame is dropped
     * 
     * @param frameData The video frame data
     * @return true if added successfully, false if dropped
     */
    public boolean addFrame(byte[] frameData) {
        if (frameData == null || frameData.length == 0) {
            return false;
        }

        totalFramesReceived.incrementAndGet();
        totalBytesReceived.addAndGet(frameData.length);

        if (!frameQueue.offer(frameData)) {
            // Buffer full - drop oldest frame to make room
            frameQueue.poll();  // Discard oldest frame
            totalFramesDropped.incrementAndGet();
            
            // Log warning periodically
            long dropped_count = totalFramesDropped.get();
            if (dropped_count % 30 == 0) {
                logger.warn("⚠️ Frame buffer full - dropped {} frames total (buffer size: {})", 
                           dropped_count, frameQueue.size());
            }
            
            return frameQueue.offer(frameData);
        }
        return true;
    }

    /**
     * Get the next frame from the buffer (non-blocking)
     * @return The next frame, or null if buffer is empty
     */
    public byte[] getNextFrame() {
        return frameQueue.poll();
    }

    /**
     * Get the next frame from the buffer with timeout
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return The next frame, or null if timeout or interrupted
     */
    public byte[] getNextFrame(long timeoutMs) throws InterruptedException {
        return frameQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Get current buffer size (number of frames)
     */
    public int getBufferSize() {
        return frameQueue.size();
    }

    /**
     * Get buffer fill percentage (0-100)
     */
    public int getBufferFillPercent() {
        return (frameQueue.size() * 100) / MAX_BUFFER_SIZE;
    }

    /**
     * Get total frames received since last clear
     */
    public long getTotalFramesReceived() {
        return totalFramesReceived.get();
    }

    /**
     * Get total frames dropped since last clear
     */
    public long getTotalFramesDropped() {
        return totalFramesDropped.get();
    }

    /**
     * Get total bytes received since last clear
     */
    public long getTotalBytesReceived() {
        return totalBytesReceived.get();
    }

    /**
     * Reset bytes counter (for rate calculation)
     * @return The bytes received before reset
     */
    public long resetBytesCounter() {
        return totalBytesReceived.getAndSet(0);
    }

    /**
     * Clear the buffer and reset all statistics
     */
    public void clear() {
        frameQueue.clear();
        initSegment = null;
        initSegmentReceived = false;
        totalFramesReceived.set(0);
        totalFramesDropped.set(0);
        totalBytesReceived.set(0);
        logger.info("🧹 Frame buffer cleared");
    }

    /**
     * Check if data is an H.264 init segment
     * For raw H.264 Annex B: Look for SPS (NAL type 7) or PPS (NAL type 8)
     * For fMP4: Look for ftyp or moov boxes
     * 
     * @param data Binary data to check
     * @return true if this appears to be an init segment
     */
    public boolean isInitSegment(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        
        // Check for fMP4 format: ftyp box (file type box)
        // Format: [4 bytes size][4 bytes type 'ftyp']
        boolean hasFtyp = data[4] == 'f' && data[5] == 't' && 
                          data[6] == 'y' && data[7] == 'p';
        
        if (hasFtyp) {
            logger.debug("📦 Detected ftyp box in data ({} bytes)", data.length);
            return true;
        }
        
        // Check for fMP4 format: moov box which contains codec parameters
        boolean hasMoov = data[4] == 'm' && data[5] == 'o' && 
                          data[6] == 'o' && data[7] == 'v';
        
        if (hasMoov) {
            logger.debug("📦 Detected moov box in data ({} bytes)", data.length);
            return true;
        }
        
        // Check for raw H.264 Annex B format: SPS or PPS NAL units
        // NAL start code: 00 00 00 01 or 00 00 01
        // SPS NAL type: 7 (0x67 with nal_ref_idc=3)
        // PPS NAL type: 8 (0x68 with nal_ref_idc=3)
        if (hasH264StartCode(data)) {
            int nalTypeIndex = findNalTypeIndex(data);
            if (nalTypeIndex >= 0) {
                int nalType = data[nalTypeIndex] & 0x1F;  // NAL unit type is in lower 5 bits
                if (nalType == 7 || nalType == 8) {  // SPS or PPS
                    logger.debug("📦 Detected H.264 {} NAL unit ({} bytes)", 
                               nalType == 7 ? "SPS" : "PPS", data.length);
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if data starts with H.264 Annex B start code
     */
    private boolean hasH264StartCode(byte[] data) {
        if (data.length < 4) return false;
        
        // Check for 4-byte start code: 00 00 00 01
        if (data[0] == 0 && data[1] == 0 && data[2] == 0 && data[3] == 1) {
            return true;
        }
        // Check for 3-byte start code: 00 00 01
        if (data[0] == 0 && data[1] == 0 && data[2] == 1) {
            return true;
        }
        return false;
    }
    
    /**
     * Find the index of the NAL type byte after the start code
     */
    private int findNalTypeIndex(byte[] data) {
        if (data.length < 5) return -1;
        
        // 4-byte start code
        if (data[0] == 0 && data[1] == 0 && data[2] == 0 && data[3] == 1) {
            return 4;
        }
        // 3-byte start code
        if (data[0] == 0 && data[1] == 0 && data[2] == 1) {
            return 3;
        }
        return -1;
    }

    /**
     * Check if data is a media segment (video frame data)
     * For raw H.264: IDR slice (NAL type 5) or non-IDR slice (NAL type 1)
     * For fMP4: moof + mdat boxes
     * 
     * @param data Binary data to check
     * @return true if this appears to be a media segment
     */
    public boolean isMediaSegment(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        
        // Check for fMP4 format: moof box (movie fragment)
        if (data[4] == 'm' && data[5] == 'o' && 
            data[6] == 'o' && data[7] == 'f') {
            return true;
        }
        
        // Check for raw H.264 Annex B: IDR or non-IDR slice
        if (hasH264StartCode(data)) {
            int nalTypeIndex = findNalTypeIndex(data);
            if (nalTypeIndex >= 0) {
                int nalType = data[nalTypeIndex] & 0x1F;
                // NAL types: 1=non-IDR slice, 5=IDR slice
                if (nalType == 1 || nalType == 5) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Get statistics summary string
     */
    public String getStats() {
        return String.format(
            "Buffer: %d/%d frames (%.1f%%), Received: %d, Dropped: %d, Bytes: %d",
            frameQueue.size(), MAX_BUFFER_SIZE, 
            (frameQueue.size() * 100.0) / MAX_BUFFER_SIZE,
            totalFramesReceived.get(), totalFramesDropped.get(), totalBytesReceived.get()
        );
    }
}
