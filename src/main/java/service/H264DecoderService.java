package service;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * H.264 Decoder Service using JavaCV/FFmpeg
 * 
 * Optimized accumulated chunk decoder:
 * - Smaller batches for lower latency
 * - Time-based flush to prevent stalls
 * - Reuses converters for efficiency
 */
public class H264DecoderService {
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private Consumer<Image> frameConsumer;
    private Thread decoderThread;
    private Java2DFrameConverter converter;
    
    // Queue for incoming video chunks
    private final BlockingQueue<byte[]> chunkQueue = new LinkedBlockingQueue<>(100);
    
    // Accumulation buffer - smaller batches for lower latency
    private final ByteArrayOutputStream accumBuffer = new ByteArrayOutputStream();
    private static final int DECODE_THRESHOLD = 80000;   // Decode at ~80KB (about 1 GOP)
    private static final int MAX_BUFFER_SIZE = 500000;   // Force decode at 500KB
    private static final long MAX_BUFFER_TIME_MS = 200;  // Max 200ms before forced decode
    
    // Reusable image for better performance
    private WritableImage reusableImage;
    private int lastImageWidth = 0;
    private int lastImageHeight = 0;
    
    private int width = 0;
    private int height = 0;
    private final AtomicLong totalFramesDecoded = new AtomicLong(0);
    private final AtomicLong totalChunksProcessed = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private long startTime = 0;
    
    // Performance tracking
    private long lastStatsTime = 0;
    private long framesAtLastStats = 0;
    private long lastBufferTime = 0;

    public H264DecoderService() {
        FFmpegLogCallback.set();
        avutil.av_log_set_level(avutil.AV_LOG_ERROR);
        System.out.println("H264DecoderService created (optimized batch decoder)");
    }

    public void initialize() throws Exception {
        System.out.println("Decoder initialize() called (no-op)");
    }

    public void initialize(Consumer<Image> onFrameDecoded) throws IOException {
        if (initialized.get()) {
            System.out.println("⚠️ Decoder already initialized");
            return;
        }

        this.frameConsumer = onFrameDecoded;
        this.converter = new Java2DFrameConverter();
        
        running.set(true);
        decoderThread = new Thread(this::decoderLoop, "H264DecoderThread");
        decoderThread.setDaemon(true);
        decoderThread.setPriority(Thread.MAX_PRIORITY);
        decoderThread.start();
        
        initialized.set(true);
        startTime = System.currentTimeMillis();
        lastStatsTime = startTime;
        lastBufferTime = startTime;
        System.out.println("✅ Decoder initialized (optimized batch mode, threshold=" + DECODE_THRESHOLD/1000 + "KB)");
    }

    public Image decodeFrame(byte[] data) {
        if (!initialized.get() || !running.get() || data == null || data.length == 0) {
            return null;
        }
        
        totalBytesReceived.addAndGet(data.length);
        
        if (!chunkQueue.offer(data)) {
            chunkQueue.poll();
            chunkQueue.offer(data);
        }
        
        return null;
    }

    private void decoderLoop() {
        System.out.println("🔄 Decoder loop started (optimized batch mode)");
        
        while (running.get()) {
            try {
                byte[] chunk = chunkQueue.poll(20, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();
                
                if (chunk != null && chunk.length > 0) {
                    totalChunksProcessed.incrementAndGet();
                    
                    synchronized (accumBuffer) {
                        if (accumBuffer.size() == 0) {
                            lastBufferTime = now;  // Reset timer on first chunk
                        }
                        accumBuffer.write(chunk);
                    }
                }
                
                // Check if we should decode
                int bufferedSize;
                long bufferAge;
                synchronized (accumBuffer) {
                    bufferedSize = accumBuffer.size();
                    bufferAge = now - lastBufferTime;
                }
                
                boolean shouldDecode = bufferedSize >= DECODE_THRESHOLD ||
                                       bufferedSize >= MAX_BUFFER_SIZE ||
                                       (bufferedSize > 20000 && bufferAge > MAX_BUFFER_TIME_MS);
                
                if (shouldDecode && bufferedSize > 0) {
                    byte[] dataToProcess;
                    synchronized (accumBuffer) {
                        dataToProcess = accumBuffer.toByteArray();
                        accumBuffer.reset();
                        lastBufferTime = now;
                    }
                    
                    decodeAccumulatedData(dataToProcess);
                }
                
                // Print stats periodically
                if (now - lastStatsTime > 5000) {
                    printStats();
                    lastStatsTime = now;
                }
                
            } catch (InterruptedException e) {
                System.out.println("Decoder interrupted");
                break;
            } catch (Exception e) {
                if (running.get()) {
                    System.out.println("⚠️ Decoder loop error: " + e.getMessage());
                }
            }
        }
        
        System.out.println("🛑 Decoder loop ended. Total frames: " + totalFramesDecoded.get());
    }

    private void decodeAccumulatedData(byte[] data) {
        if (data == null || data.length < 188) {
            return;
        }
        
        FFmpegFrameGrabber grabber = null;
        
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            grabber = new FFmpegFrameGrabber(inputStream);
            grabber.setFormat("mpegts");
            
            // Minimal analysis for faster startup
            grabber.setOption("fflags", "nobuffer+discardcorrupt");
            grabber.setOption("flags", "low_delay");
            grabber.setOption("analyzeduration", "0");
            grabber.setOption("probesize", "32768");
            
            grabber.start();
            
            if (width == 0 || height == 0) {
                width = grabber.getImageWidth();
                height = grabber.getImageHeight();
                if (width > 0 && height > 0) {
                    System.out.println("📺 Video: " + width + "x" + height);
                }
            }
            
            // Decode all frames in accumulated data
            Frame frame;
            while ((frame = grabber.grabImage()) != null && running.get()) {
                if (frame.image != null) {
                    BufferedImage bi = converter.convert(frame);
                    if (bi != null) {
                        Image fxImage = toFxImageFast(bi);
                        if (fxImage != null && frameConsumer != null) {
                            frameConsumer.accept(fxImage);
                            totalFramesDecoded.incrementAndGet();
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // Decoding errors can happen on partial data, ignore
        } finally {
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    private void printStats() {
        long elapsed = System.currentTimeMillis() - startTime;
        long frames = totalFramesDecoded.get();
        long chunks = totalChunksProcessed.get();
        long bytes = totalBytesReceived.get();
        
        if (elapsed > 0) {
            double fps = frames * 1000.0 / elapsed;
            double recentFps = (frames - framesAtLastStats) * 1000.0 / 5000.0;
            System.out.println(String.format(
                "📊 Decoder: Chunks=%d | Data=%.1fMB | Frames=%d | FPS=%.1f (recent=%.1f)",
                chunks, bytes/1024.0/1024.0, frames, fps, recentFps));
            framesAtLastStats = frames;
        }
    }

    private Image toFxImageFast(BufferedImage bi) {
        try {
            int w = bi.getWidth();
            int h = bi.getHeight();
            
            if (reusableImage == null || lastImageWidth != w || lastImageHeight != h) {
                reusableImage = new WritableImage(w, h);
                lastImageWidth = w;
                lastImageHeight = h;
            }
            
            PixelWriter pw = reusableImage.getPixelWriter();
            int[] pixels = new int[w * h];
            bi.getRGB(0, 0, w, h, pixels, 0, w);
            pw.setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), pixels, 0, w);
            
            return reusableImage;
        } catch (Exception e) {
            return null;
        }
    }

    public void cleanup() {
        System.out.println("🧹 Cleaning up decoder...");
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
        synchronized (accumBuffer) {
            accumBuffer.reset();
        }
        reusableImage = null;
        
        System.out.println("✅ Decoder cleaned up. Total frames: " + totalFramesDecoded.get());
    }

    public boolean isInitialized() { return initialized.get(); }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getTotalFramesDecoded() { return totalFramesDecoded.get(); }
    public int getQueueSize() { return chunkQueue.size(); }
    
    public double getCurrentFPS() {
        long elapsed = System.currentTimeMillis() - startTime;
        long frames = totalFramesDecoded.get();
        return (elapsed > 0) ? (frames * 1000.0 / elapsed) : 0.0;
    }
}
