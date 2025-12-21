package service;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * H.264 Decoder Service using JavaCV/FFmpeg
 * 
 * Uses chunk-based decoding - each incoming chunk is a complete GOP (starts with keyframe)
 * so we can decode each chunk independently using ByteArrayInputStream.
 */
@Service
public class H264DecoderService {
    private static final Logger logger = LoggerFactory.getLogger(H264DecoderService.class);
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private Consumer<Image> frameConsumer;
    private Thread decoderThread;
    private Java2DFrameConverter converter;
    
    // Queue for incoming video chunks
    private final BlockingQueue<byte[]> chunkQueue = new LinkedBlockingQueue<>(50);
    
    private int width = 0;
    private int height = 0;
    private long totalFramesDecoded = 0;
    private long totalChunksProcessed = 0;
    private long startTime = 0;

    public H264DecoderService() {
        FFmpegLogCallback.set();
        avutil.av_log_set_level(avutil.AV_LOG_ERROR);
        logger.info("H264DecoderService created (chunk-based decoding)");
    }

    /**
     * Empty initialize for compatibility
     */
    public void initialize() throws Exception {
        logger.info("Decoder initialize() called (no-op)");
    }

    /**
     * Initialize with frame callback
     */
    public void initialize(Consumer<Image> onFrameDecoded) throws IOException {
        if (initialized.get()) {
            logger.warn("Decoder already initialized");
            return;
        }

        this.frameConsumer = onFrameDecoded;
        this.converter = new Java2DFrameConverter();
        
        running.set(true);
        decoderThread = new Thread(this::decoderLoop, "H264DecoderThread");
        decoderThread.setDaemon(true);
        decoderThread.start();
        
        initialized.set(true);
        startTime = System.currentTimeMillis();
        logger.info("Decoder initialized with frame callback");
    }

    /**
     * Queue data for decoding - each chunk should be a complete segment
     */
    public Image decodeFrame(byte[] data) {
        if (!initialized.get() || !running.get() || data == null || data.length == 0) {
            return null;
        }
        
        // Queue chunk for decoding
        if (!chunkQueue.offer(data)) {
            // Queue full, drop oldest to keep up with live stream
            chunkQueue.poll();
            chunkQueue.offer(data);
        }
        
        return null;
    }

    /**
     * Main decoder loop - processes chunks from queue
     * Each chunk is decoded independently using ByteArrayInputStream
     */
    private void decoderLoop() {
        logger.info("🎬 Decoder loop started (chunk-based)");
        
        while (running.get()) {
            try {
                // Wait for chunk
                byte[] chunk = chunkQueue.poll(100, TimeUnit.MILLISECONDS);
                
                if (chunk == null || chunk.length == 0) {
                    continue;
                }
                
                // Decode this chunk
                decodeChunk(chunk);
                
            } catch (InterruptedException e) {
                logger.info("Decoder interrupted");
                break;
            } catch (Exception e) {
                if (running.get()) {
                    logger.debug("Decoder loop error: {}", e.getMessage());
                }
            }
        }
        
        logger.info("🎬 Decoder loop ended. Total frames: {}", totalFramesDecoded);
    }

    /**
     * Decode a single MPEG-TS chunk using ByteArrayInputStream
     * Each chunk from encoder contains complete GOPs, so can be decoded independently
     */
    private void decodeChunk(byte[] data) {
        if (data == null || data.length < 188) { // Minimum MPEG-TS packet size
            return;
        }
        
        totalChunksProcessed++;
        FFmpegFrameGrabber grabber = null;
        
        try {
            // Create grabber from byte array input stream
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            grabber = new FFmpegFrameGrabber(inputStream);
            grabber.setFormat("mpegts");
            grabber.start();
            
            // Get dimensions on first successful decode
            if (width == 0 || height == 0) {
                width = grabber.getImageWidth();
                height = grabber.getImageHeight();
                if (width > 0 && height > 0) {
                    logger.info("✅ Video dimensions detected: {}x{}", width, height);
                }
            }
            
            // Decode all frames in this chunk
            int framesInChunk = 0;
            Frame frame;
            while ((frame = grabber.grab()) != null && running.get()) {
                if (frame.image != null) {
                    BufferedImage bi = converter.convert(frame);
                    if (bi != null) {
                        Image fxImage = toFxImage(bi);
                        if (fxImage != null && frameConsumer != null) {
                            frameConsumer.accept(fxImage);
                            totalFramesDecoded++;
                            framesInChunk++;
                        }
                    }
                }
            }
            
            // Log progress periodically
            if (totalChunksProcessed % 5 == 0 && framesInChunk > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                double fps = elapsed > 0 ? totalFramesDecoded * 1000.0 / elapsed : 0;
                logger.info("📊 Chunks: {} | Frames: {} | FPS: {}", 
                    totalChunksProcessed, totalFramesDecoded, String.format("%.1f", fps));
            }
            
        } catch (Exception e) {
            // Chunk decode errors can happen, don't spam logs
            if (totalChunksProcessed % 20 == 1) {
                logger.debug("Chunk decode issue (chunk #{}): {}", totalChunksProcessed, e.getMessage());
            }
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    /**
     * Convert BufferedImage to JavaFX Image
     */
    private Image toFxImage(BufferedImage bi) {
        try {
            int w = bi.getWidth();
            int h = bi.getHeight();
            WritableImage img = new WritableImage(w, h);
            int[] pixels = new int[w * h];
            bi.getRGB(0, 0, w, h, pixels, 0, w);
            img.getPixelWriter().setPixels(0, 0, w, h,
                PixelFormat.getIntArgbInstance(), pixels, 0, w);
            return img;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        logger.info("🧹 Cleaning up decoder...");
        running.set(false);
        initialized.set(false);
        
        if (decoderThread != null) {
            decoderThread.interrupt();
            try {
                decoderThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            decoderThread = null;
        }
        
        if (converter != null) {
            converter.close();
            converter = null;
        }
        
        chunkQueue.clear();
        
        logger.info("✅ Decoder cleaned up. Total frames decoded: {}", totalFramesDecoded);
    }

    public boolean isInitialized() { return initialized.get(); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getTotalFramesDecoded() { return totalFramesDecoded; }
}
