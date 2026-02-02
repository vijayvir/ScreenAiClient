package model;

import java.time.LocalDateTime;

/**
 * Data Transfer Object for performance metrics
 */
public class PerformanceMetrics {
    private double fps;
    private long latencyMs;
    private int droppedFrames;
    private int totalFrames;
    private double cpuUsage;
    private long memoryUsageMb;
    private LocalDateTime timestamp;
    private String encoderType;
    
    private PerformanceMetrics() {
        this.timestamp = LocalDateTime.now();
    }
    
    public static class Builder {
        private double fps;
        private long latencyMs;
        private int droppedFrames;
        private int totalFrames;
        private double cpuUsage;
        private long memoryUsageMb;
        private String encoderType;
        
        public Builder fps(double fps) {
            this.fps = fps;
            return this;
        }
        
        public Builder latency(long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }
        
        public Builder droppedFrames(int droppedFrames) {
            this.droppedFrames = droppedFrames;
            return this;
        }
        
        public Builder totalFrames(int totalFrames) {
            this.totalFrames = totalFrames;
            return this;
        }
        
        public Builder cpuUsage(double cpuUsage) {
            this.cpuUsage = cpuUsage;
            return this;
        }
        
        public Builder memoryUsage(long memoryUsageMb) {
            this.memoryUsageMb = memoryUsageMb;
            return this;
        }
        
        public Builder encoderType(String encoderType) {
            this.encoderType = encoderType;
            return this;
        }
        
        public PerformanceMetrics build() {
            PerformanceMetrics metrics = new PerformanceMetrics();
            metrics.fps = this.fps;
            metrics.latencyMs = this.latencyMs;
            metrics.droppedFrames = this.droppedFrames;
            metrics.totalFrames = this.totalFrames;
            metrics.cpuUsage = this.cpuUsage;
            metrics.memoryUsageMb = this.memoryUsageMb;
            metrics.encoderType = this.encoderType;
            return metrics;
        }
    }
    
    // Getters
    public double getFps() { return fps; }
    public long getLatencyMs() { return latencyMs; }
    public int getDroppedFrames() { return droppedFrames; }
    public int getTotalFrames() { return totalFrames; }
    public double getCpuUsage() { return cpuUsage; }
    public long getMemoryUsageMb() { return memoryUsageMb; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getEncoderType() { return encoderType; }
    
    public double getDropRate() {
        return totalFrames > 0 ? (droppedFrames * 100.0 / totalFrames) : 0.0;
    }
    
    @Override
    public String toString() {
        return String.format("PerformanceMetrics[fps=%.2f, latency=%dms, dropped=%d/%d (%.2f%%), cpu=%.1f%%, mem=%dMB, encoder=%s]",
            fps, latencyMs, droppedFrames, totalFrames, getDropRate(), cpuUsage, memoryUsageMb, encoderType);
    }
}
