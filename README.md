# ScreenAI Client - Cross-Platform Screen Sharing Desktop Application

> A **JavaFX desktop client** for real-time screen sharing using **JavaCV/FFmpeg** for screen capture and H.264 encoding, with WebSocket connectivity to a relay server. **Supports macOS, Windows, and Linux.**

![JavaFX 21](https://img.shields.io/badge/UI-JavaFX_21-blue) ![JavaCV 1.5.9](https://img.shields.io/badge/Video-JavaCV_1.5.9-orange) ![Spring Framework](https://img.shields.io/badge/Framework-Spring_6.x-green) ![WebSocket](https://img.shields.io/badge/Protocol-WebSocket-brightgreen) ![Java 21](https://img.shields.io/badge/Java-21-red) ![Cross Platform](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Linux-purple)

## 📖 Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [System Architecture](#system-architecture)
- [Technology Stack](#technology-stack)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Troubleshooting](#troubleshooting)

---

## Overview

**ScreenAI Client** is a desktop application enabling real-time screen sharing with two distinct roles:

### 🔴 **HOST Mode (Presenter)**
- Encode to **H.264/MPEG-TS** using FFmpeg with hardware acceleration support
- Stream at **~28-30 FPS** with ultrafast/zerolatency preset
- Short GOP (15 frames) with MPEG-TS resend_headers for reliable viewer joining
- Monitor streaming stats (frames sent, data transferred, viewer count)
- Support for multiple encoders: VideoToolbox (macOS), NVENC (NVIDIA), libx264 (CPU)

### 🔵 **VIEWER Mode (Watcher)**
- Connect to a host's room using Room ID
- Receive H.264 chunks via WebSocket with frame buffering
- **Accumulated chunk decoding** (80KB threshold + 200ms time-based flush)
- Decode using **FFmpegFrameGrabber** with batch processing (~12-15 FPS)
- Display live video in JavaFX ImageView with real-time FPS metrics

## Quick Start

### Prerequisites
- **Java 21+** (verify with `java -version`)
- **Maven 3.9+** (included as `./mvnw`)
- **ScreenAI-Server** running on `ws://localhost:8080/screenshare`

### 1. Clone and Build

```bash
git clone https://github.com/vijayvir/ScreenAiClient.git
cd ScreenAI-Client
```

### 2. Run the Application

**Using Maven Wrapper (Recommended):**
```bash
./mvnw compile exec:java -Dexec.mainClass="App"
```

**Using the run script:**
```bash
./run.sh
```

**From IntelliJ IDEA:**
1. Open project in IntelliJ IDEA
2. Navigate to `src/main/java/App.java`
3. Click green **▶️ Run** button

### 3. Running Multiple Instances

To test both HOST and VIEWER modes, run two instances:
```bash
# Terminal 1 - HOST
./mvnw compile exec:java -Dexec.mainClass="App"

# Terminal 2 - VIEWER  
./mvnw compile exec:java -Dexec.mainClass="App"
```

### 4. Using the Application

#### As a **HOST (Presenter)**
```
1. Select: 🔴 HOST (Share Screen)
2. Enter Server: localhost (or server IP)
3. Enter Port: 8080
4. Click: [Connect]
5. Room ID auto-generates (or enter custom)
6. Select screen source and encoder
7. Click: [▶ START STREAMING]
8. Share Room ID with viewers
```

#### As a **VIEWER (Watcher)**
```
1. Select: 🔵 VIEWER (Watch Stream)
2. Enter Server: localhost (or server IP)
3. Enter Port: 8080
4. Click: [Connect]
5. Enter Room ID from host
6. Click: [Join Room]
7. Watch live stream!
```

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        ScreenAI Client                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────────────┐        ┌──────────────────────┐          │
│  │    HOST MODE         │        │    VIEWER MODE        │          │
│  │   (~28-30 FPS)       │        │   (~12-15 FPS)        │          │
│  │                      │        │                       │          │
│  │  ScreenCaptureService│        │  ServerConnectionService│        │
│  │  (Cross-Platform)    │        │  (WebSocket Receive)  │          │
│  │  • macOS: AVFoundation        │       ↓               │          │
│  │  • Windows: GDIGrab  │        │  FrameBufferService   │          │
│  │  • Linux: X11Grab    │        │  (Chunk Accumulation) │          │
│  │       ↓              │        │       ↓               │          │
│  │  VideoEncoderFactory │        │  H264DecoderService   │          │
│  │  (Strategy Pattern)  │        │  (Batch FFmpegGrabber)│          │
│  │       ↓              │        │  • 80KB threshold     │          │
│  │  H.264 Encoder       │        │  • 200ms time flush   │          │
│  │  • VideoToolbox(GPU) │        │       ↓               │          │
│  │  • NVENC (GPU)       │        │  JavaFX ImageView     │          │
│  │  • libx264 (CPU)     │        │                       │          │
│  │  • GOP=15            │        │                       │          │
│  │  • resend_headers    │        │                       │          │
│  │       ↓              │        │                       │          │
│  │  ServerConnectionService      │                       │          │
│  │  (WebSocket Send)    │        │                       │          │
│  └──────────────────────┘        └──────────────────────┘          │
│                                                                     │
│  ┌──────────────────────────────────────────────────────┐          │
│  │              MainController (JavaFX FXML)             │          │
│  │  • Role Selection (HOST/VIEWER)                       │          │
│  │  • Connection Management                              │          │
│  │  • Performance Metrics Display                        │          │
│  └──────────────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ WebSocket (ws://)
                              │ Binary H.264 MPEG-TS + JSON Control
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│                     ScreenAI Server                                  │
│              (Spring WebFlux + Netty)                               │
│                                                                     │
│  ws://localhost:8080/screenshare                                    │
│                                                                     │
│  • Room Management (create-room, join-room)                         │
│  • Binary Relay (H.264 chunks to viewers)                           │
│  • Viewer Count Updates                                             │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Technology Stack

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **UI Framework** | JavaFX | 21.0.2 | Desktop GUI with FXML |
| **Video Processing** | JavaCV | 1.5.9 | FFmpeg Java bindings |
| **Screen Capture** | Platform-Specific | - | AVFoundation (macOS), GDIGrab (Windows), X11Grab (Linux) |
| **Video Codec** | H.264 | - | Video encoding/decoding |
| **Hardware Encoders** | VideoToolbox / NVENC | - | GPU-accelerated encoding |
| **Container** | MPEG-TS | - | Streamable video format |
| **DI Framework** | Spring Context | 6.0.13 | Dependency injection |
| **WebSocket** | Spring WebSocket + Tyrus | 6.0.13 / 2.1.3 | WebSocket client |
| **JSON** | Jackson | 2.16.0 | JSON processing |
| **Logging** | SLF4J + Logback | 2.0.9 / 1.4.11 | Logging framework |
| **Java** | OpenJDK | 21+ | Programming language |
| **Build Tool** | Maven | 3.9.x | Dependency management |

---

## Hardware-Accelerated Encoding

The client automatically selects the best available encoder for your platform:

| Platform | Hardware Encoder | CPU Reduction | Fallback |
|----------|-----------------|---------------|----------|
| **macOS** | VideoToolbox (GPU) | ~70% | libx264 |
| **Windows** | NVENC (NVIDIA GPU) | ~80% | libx264 |
| **Linux** | NVENC (NVIDIA GPU) | ~80% | libx264 |

### Encoder Selection Logic

```
┌─────────────────────────────────────────────────────┐
│              VideoEncoderFactory                     │
├─────────────────────────────────────────────────────┤
│  1. Detect Platform (macOS/Windows/Linux)           │
│  2. Try Hardware Encoder:                           │
│     ├── macOS → VideoToolbox (h264_videotoolbox)    │
│     ├── Windows/Linux → NVENC (h264_nvenc)          │
│  3. If hardware fails → LibX264 (ultrafast preset)  │
└─────────────────────────────────────────────────────┘
```

### Expected Performance

| Encoder | Encoding Time | End-to-End Latency | CPU Usage |
|---------|--------------|-------------------|-----------|
| **VideoToolbox** | ~5ms | 50-100ms | 5-10% |
| **NVENC** | ~3ms | 30-80ms | 2-5% |
| **LibX264** | ~15ms | 100-200ms | 30-40% |

---

## Platform-Specific Requirements

### macOS
- **Screen Recording Permission Required**
  - Go to **System Preferences** → **Privacy & Security** → **Screen Recording**
  - Enable permission for your Java/IDE application
  - Restart the application after granting permission
- Uses **AVFoundation** for native screen capture

### Windows
- No special permissions required
- Uses **GDIGrab** for screen capture
- Alternatively supports **DirectShow** (`dshow`) if available

### Linux (Ubuntu/Debian)
- **X11 Display Server Required** - Wayland is NOT currently supported
- If using Wayland, switch to X11:
  - At login screen, click gear icon and select "Ubuntu on Xorg"
  - Or set `GDK_BACKEND=x11` before running
- Install FFmpeg if not present:
  ```bash
  sudo apt install ffmpeg
  ```
- Uses **X11Grab** for screen capture

---

## Project Structure

```
ScreenAI-Client/
├── pom.xml                           # Maven configuration
├── README.md                         # This file
├── run.sh                            # Launch script
├── mvnw / mvnw.cmd                   # Maven wrapper
│
├── src/main/java/
│   ├── App.java                      # JavaFX Application entry point
│   ├── ScreenAIClientApplication.java # Spring Context manager
│   │
│   ├── controller/
│   │   ├── MainController.java       # Main UI controller (FXML)
│   │   ├── HostController.java       # Host mode logic
│   │   └── ViewerController.java     # Viewer mode logic
│   │
│   ├── service/
│   │   ├── ServerConnectionService.java  # Thread-safe WebSocket client
│   │   ├── ScreenCaptureService.java     # AVFoundation + H.264 encoding
│   │   ├── H264DecoderService.java       # Chunk-based video decoding
│   │   ├── FrameBufferService.java       # Frame buffer for jitter handling
│   │   ├── PerformanceMonitorService.java # CPU/FPS metrics
│   │   └── ScreenSourceDetector.java     # Screen enumeration
│   │
│   ├── encoder/
│   │   ├── VideoEncoderStrategy.java     # Encoder interface (Strategy pattern)
│   │   ├── VideoEncoderFactory.java      # Auto-selects best encoder
│   │   ├── H264VideoToolboxEncoder.java  # macOS GPU encoder
│   │   ├── NvencEncoder.java             # NVIDIA GPU encoder
│   │   └── LibX264Encoder.java           # CPU fallback encoder
│   │
│   └── model/
│       ├── ScreenSource.java             # Screen source metadata
│       └── PerformanceMetrics.java       # Performance data model
│
├── src/main/resources/
│   ├── application.yml               # Configuration
│   └── ui/
│       └── main.fxml                 # JavaFX UI layout
│
└── src/test/java/                    # Unit tests (empty)
```

---

## Configuration

### application.yml

```yaml
spring:
  application:
    name: ScreenAI-Client

logging:
  level:
    root: INFO
    service: DEBUG
    controller: DEBUG

screenai:
  server:
    url: ws://localhost:8080/screenshare
    reconnect-attempts: 3
    reconnect-delay: 5000

  video:
    frame-rate: 30
    bitrate: 2500      # kbps
    quality: high
```

### Video Encoding (ScreenCaptureService)

```java
// Platform-specific screen capture
if (IS_MAC) {
    grabber.setFormat("avfoundation");    // macOS
} else if (IS_WINDOWS) {
    grabber.setFormat("gdigrab");         // Windows
} else if (IS_LINUX) {
    grabber.setFormat("x11grab");         // Linux (X11 only)
}

// Low-latency H.264 Encoding with optimized GOP
recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
recorder.setFormat("mpegts");
recorder.setFrameRate(30);                // 30 FPS target
recorder.setVideoBitrate(1500000);        // 1.5 Mbps bitrate
recorder.setVideoOption("preset", "ultrafast");
recorder.setVideoOption("tune", "zerolatency");
recorder.setGopSize(15);                  // Keyframe every ~0.5 sec (optimized for mid-stream joining)
recorder.setOption("mpegts_flags", "resend_headers");  // Resend SPS/PPS with every segment
```

### Video Decoding (H264DecoderService)

```java
// Accumulated chunk decoding approach
private static final int MIN_BATCH_SIZE = 80_000;   // 80KB threshold
private static final long MAX_BATCH_TIME_MS = 200;  // 200ms time-based flush

// Decoder creates FFmpegFrameGrabber per batch for reliable decoding
// - Collects chunks until MIN_BATCH_SIZE or MAX_BATCH_TIME_MS
// - Each batch starts fresh with SPS/PPS headers
// - Achieves ~12-15 FPS on viewer side
```

**Why not per-chunk decoding?**
- Creating FFmpegFrameGrabber has ~100ms overhead per instance
- Single-chunk decoding results in only 1-2 FPS
- Batch processing amortizes the overhead across multiple frames

### Encoder Selection (Strategy Pattern)

```java
// Auto-select best encoder for platform
VideoEncoderStrategy encoder = VideoEncoderFactory.getBestEncoder();

// Available encoders:
// - H264VideoToolboxEncoder (macOS GPU - ~70% CPU reduction)
// - NvencEncoder (NVIDIA GPU - ~80% CPU reduction)
// - LibX264Encoder (CPU fallback with repeat-headers for streaming)
```

### Performance Characteristics

| Role | Expected FPS | Notes |
|------|-------------|-------|
| **Host (Encoding)** | ~28-30 FPS | GPU-accelerated with VideoToolbox/NVENC |
| **Viewer (Decoding)** | ~12-15 FPS | Accumulated chunk decoding approach |

**Why viewer FPS is lower:**
- FFmpegFrameGrabber initialization overhead (~100ms per batch)
- Batch processing required for reliable H.264 stream parsing
- Trade-off between latency and throughput

### WebSocket Protocol

**Server Endpoint:** `ws://localhost:8080/screenshare`

**Host Commands:**
```json
{"type": "create-room", "roomId": "room123"}
{"type": "leave-room"}
// Then send binary H.264 chunks
```

**Viewer Commands:**
```json
{"type": "join-room", "roomId": "room123"}
{"type": "leave-room"}
// Then receive binary H.264 chunks
```

**Server Responses:**
```json
{"type": "connected", "sessionId": "abc123"}
{"type": "room-joined", "roomId": "room123", "role": "viewer", "viewerCount": 1}
{"type": "viewer-count", "count": 2}
{"type": "presenter-left"}
{"type": "error", "message": "Room not found"}
```

---

## Known Limitations

### Viewer FPS (~12-15 FPS vs Host ~28-30 FPS)

The viewer achieves lower FPS than the host due to the decoder architecture:

**Why this happens:**
1. **FFmpegFrameGrabber Initialization Overhead:** Creating a new FFmpegFrameGrabber instance has ~100ms overhead for codec detection and initialization
2. **Accumulated Chunk Decoding:** To achieve reliable decoding, chunks are accumulated to 80KB or 200ms before creating a decoder
3. **H.264 Stream Requirements:** The decoder needs complete NAL units with SPS/PPS headers for proper initialization

**Current Approach:**
- Chunks are accumulated until 80KB threshold OR 200ms time limit
- A new FFmpegFrameGrabber is created per batch
- All frames in the batch are decoded sequentially
- This trades some FPS for reliable, artifact-free decoding

**Alternative approaches (not implemented):**
- Persistent decoder with piped streams (complex SPS/PPS management for mid-stream joins)
- Native FFmpeg process with frame extraction (adds IPC complexity)
- Hardware-accelerated decoder on viewer (requires platform-specific code)

---

## Troubleshooting

### macOS Screen Recording Permission
On first run, macOS will ask for screen recording permission:
1. Go to **System Preferences** → **Privacy & Security** → **Screen Recording**
2. Enable permission for your Java/IDE application
3. Restart the application

### macOS AVFoundation Device Selection
The client tries these device configurations in order:
```
"1:none"   - First screen (main display)
"2:none"   - Second screen
"3:none"   - Third screen
```

### Linux/Ubuntu Screen Capture Issues
**Problem:** Screen sharing not working on Ubuntu
- **Wayland Limitation:** X11Grab does not work on Wayland
- **Solution:** Switch to X11 session:
  1. Log out of your current session
  2. At login screen, click the gear icon
  3. Select "Ubuntu on Xorg" or "GNOME on Xorg"
  4. Log back in and try again

**Check your display server:**
```bash
echo $XDG_SESSION_TYPE
# Should output: x11 (not wayland)
```

### Windows Screen Capture
If GDIGrab fails, the client will attempt DirectShow as fallback.

### Common Issues

| Issue | Platform | Solution |
|-------|----------|----------|
| "No screen capture device" | macOS | Grant screen recording permission in System Preferences |
| "Screen capture failed" | Linux | Switch from Wayland to X11 session |
| "Connection refused" | All | Ensure ScreenAI-Server is running on port 8080 |
| "Connection timeout" | All | Check server IP/port, firewall settings |
| "Room not found" | All | Verify room ID matches host's room |
| "Video not displaying" | All | Wait for keyframe, check decoder logs |
| "Viewer FPS ~12-15" | All | This is expected - accumulated chunk decoding architecture |
| "mvnw: No such file" | All | Run: `mvn -N io.takari:maven:wrapper` |
| "avcodec_open2 error" | All | Encoder compatibility issue - check FFmpeg installation |
| "non-existing PPS referenced" | All | Mid-stream join issue - wait for next keyframe (GOP=15) |

### Network Troubleshooting
```bash
# Test server connectivity
ping <server-ip>
nc -zv <server-ip> 8080

# Check if server is running
curl -I http://<server-ip>:8080
```

### Debug Logging
Enable debug logging by setting environment variable:
```bash
export JAVA_OPTS="-Dlogging.level.service=DEBUG"
./mvnw compile exec:java -Dexec.mainClass="App"
```

---

## Building

### Compile
```bash
./mvnw compile
```

### Package JAR
```bash
./mvnw package
```

### Clean Build
```bash
./mvnw clean compile
```

---

## Dependencies

Key dependencies from `pom.xml`:

```xml
<!-- JavaFX 21 -->
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>21.0.2</version>
</dependency>

<!-- JavaCV + FFmpeg -->
<dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>javacv</artifactId>
    <version>1.5.9</version>
</dependency>
<dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>ffmpeg-platform</artifactId>
    <version>6.0-1.5.9</version>
</dependency>

<!-- Spring WebSocket -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-websocket</artifactId>
    <version>6.0.13</version>
</dependency>

<!-- Tyrus WebSocket Client -->
<dependency>
    <groupId>org.glassfish.tyrus.bundles</groupId>
    <artifactId>tyrus-standalone-client</artifactId>
    <version>2.1.3</version>
</dependency>
```

---

## Related Projects

- **[ScreenAI-Server](https://github.com/vijayvir/ScreenAi)** - Reactive WebSocket relay server (Spring WebFlux + Netty)

---
