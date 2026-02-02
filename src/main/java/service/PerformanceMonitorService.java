package service;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

import model.PerformanceMetrics;

/**
 * Performance Monitor Service
 * Tracks screen sharing performance metrics
 */
@Service
public class PerformanceMonitorService {

    // Performance tracking
    private final ConcurrentLinkedQueue<Long> frameTimestamps = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> latencyMeasurements = new ConcurrentLinkedQueue<>();

    private volatile boolean monitoring = false;
    private int totalFrames = 0;
    private int droppedFrames = 0;
    private String currentEncoderType = "Unknown";

    private final List<MetricsListener> listeners = new CopyOnWriteArrayList<>();

    public PerformanceMonitorService() {
        // Initialize
    }

    /**
     * Start monitoring
     */
    public void startMonitoring() {
        if (!monitoring) {
            monitoring = true;
        }
    }

    /**
     * Stop monitoring
     */
    public void stopMonitoring() {
        if (monitoring) {
            monitoring = false;
        }
    }

    /**
     * Add metrics listener
     */
    public void addMetricsListener(MetricsListener listener) {
        listeners.add(listener);
    }

    /**
     * Record frame timestamp
     */
    public void recordFrameTimestamp() {
        if (monitoring) {
            frameTimestamps.add(System.currentTimeMillis());
            totalFrames++;
        }
    }

    /**
     * Record latency measurement
     */
    public void recordLatency(long latencyMs) {
        if (monitoring) {
            latencyMeasurements.add(latencyMs);
        }
    }

    /**
     * Get current metrics
     */
    public PerformanceMetrics getCurrentMetrics() {
        double fps = calculateFps();
        long avgLatency = calculateAverageLatency();

        return new PerformanceMetrics.Builder()
            .fps(fps)
            .latency(avgLatency)
            .droppedFrames(droppedFrames)
            .totalFrames(totalFrames)
            .cpuUsage(getSystemCpuUsage())
            .encoderType(currentEncoderType)
            .build();
    }

    /**
     * Calculate FPS
     */
    private double calculateFps() {
        if (frameTimestamps.isEmpty()) {
            return 0;
        }

        long now = System.currentTimeMillis();
        int count = 0;

        for (Long timestamp : frameTimestamps) {
            if (now - timestamp < 1000) {
                count++;
            }
        }

        return count;
    }

    /**
     * Calculate average latency
     */
    private long calculateAverageLatency() {
        if (latencyMeasurements.isEmpty()) {
            return 0;
        }

        long sum = 0;
        for (Long latency : latencyMeasurements) {
            sum += latency;
        }

        return sum / latencyMeasurements.size();
    }

    /**
     * Get system CPU usage
     */
    private double getSystemCpuUsage() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return osBean.getProcessCpuLoad() * 100;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Set encoder type
     */
    public void setEncoderType(String encoderType) {
        this.currentEncoderType = encoderType;
    }

    /**
     * Record dropped frame
     */
    public void recordDroppedFrame() {
        droppedFrames++;
    }

    /**
     * Metrics listener interface
     */
    public interface MetricsListener {
        void onMetricsUpdated(PerformanceMetrics metrics);
    }
}

