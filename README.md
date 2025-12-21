# ScreenAI Client - Real-Time Screen Sharing Desktop Application

> A **JavaFX desktop client** for real-time screen sharing using **JavaCV/FFmpeg** for screen capture and H.264 encoding, with WebSocket connectivity to a relay server.

![JavaFX 21](https://img.shields.io/badge/UI-JavaFX_21-blue) ![JavaCV 1.5.9](https://img.shields.io/badge/Video-JavaCV_1.5.9-orange) ![Spring Framework](https://img.shields.io/badge/Framework-Spring_6.x-green) ![WebSocket](https://img.shields.io/badge/Protocol-WebSocket-brightgreen) ![Java 21](https://img.shields.io/badge/Java-21-red)

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

**ScreenAI Client** is a professional desktop application enabling real-time screen sharing with two distinct roles:

### 🔴 **HOST Mode (Presenter)**
- Capture screen using **AVFoundation** (macOS) via JavaCV
- Encode to **H.264/MPEG-TS** using FFmpeg with hardware acceleration support
- Stream at configurable FPS with ultrafast/zerolatency preset
- Monitor streaming stats (frames sent, data transferred, viewer count)
- Support for multiple encoders: VideoToolbox (macOS), NVENC (NVIDIA), libx264 (CPU)

### 🔵 **VIEWER Mode (Watcher)**
- Connect to a host's room using Room ID
- Receive H.264 chunks via WebSocket with frame buffering
- Decode using **FFmpegFrameGrabber** (chunk-based decoding)
- Display live video in JavaFX ImageView with real-time FPS metrics

### Key Features
✅ **Pure JavaCV** - Native screen capture via AVFoundation (macOS)  
✅ **Hardware Acceleration** - VideoToolbox (macOS), NVENC (NVIDIA) encoder support  
✅ **H.264 Encoding** - FFmpeg-based encoding with Strategy pattern for encoder selection  
✅ **Thread-Safe** - AtomicReference-based connection management  
✅ **Spring-Managed** - Lightweight dependency injection (non-server mode)  
✅ **WebSocket Client** - Real-time binary streaming via Spring WebSocket  
✅ **Cross-Platform** - Windows, macOS, Linux support  
✅ **Frame Buffering** - Smooth playback with jitter handling  

---

## Quick Start

### Prerequisites
- **Java 21+** (verify with `java -version`)
- **Maven 3.9+** (included as `./mvnw`)
- **ScreenAI-Server** running on `ws://localhost:8080/screenshare`

### 1. Clone and Build

```bash
git clone https://github.com/rkumar1001/ScreenAI-Client.git
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
│  │                      │        │                       │          │
│  │  ScreenCaptureService│        │  ServerConnectionService│        │
│  │  (AVFoundation)      │        │  (WebSocket Receive)  │          │
│  │       ↓              │        │       ↓               │          │
│  │  VideoEncoderFactory │        │  FrameBufferService   │          │
│  │  (Strategy Pattern)  │        │  (Jitter Buffer)      │          │
│  │       ↓              │        │       ↓               │          │
│  │  H.264 Encoder       │        │  H264DecoderService   │          │
│  │  • VideoToolbox(GPU) │        │  (FFmpegFrameGrabber) │          │
│  │  • NVENC (GPU)       │        │       ↓               │          │
│  │  • libx264 (CPU)     │        │  JavaFX ImageView     │          │
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
                              │ Binary H.264 + JSON Control
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
| **Screen Capture** | AVFoundation | - | macOS native capture |
| **Video Codec** | H.264 | - | Video encoding/decoding |
| **Container** | MPEG-TS | - | Streamable video format |
| **DI Framework** | Spring Context | 6.0.13 | Dependency injection |
| **WebSocket** | Spring WebSocket + Tyrus | 6.0.13 / 2.1.3 | WebSocket client |
| **JSON** | Jackson | 2.16.0 | JSON processing |
| **Logging** | SLF4J + Logback | 2.0.9 / 1.4.11 | Logging framework |
| **Java** | OpenJDK | 21+ | Programming language |
| **Build Tool** | Maven | 3.9.x | Dependency management |

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
// Screen capture (macOS)
grabber.setFormat("avfoundation");
grabber.setFrameRate(15);
grabber.setOption("capture_cursor", "1");

// H.264 Encoding
recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
recorder.setFormat("mpegts");
recorder.setVideoBitrate(600000);
recorder.setVideoOption("preset", "ultrafast");
recorder.setVideoOption("tune", "zerolatency");
```

### Encoder Selection (Strategy Pattern)

```java
// Auto-select best encoder for platform
VideoEncoderStrategy encoder = VideoEncoderFactory.getBestEncoder();

// Available encoders:
// - H264VideoToolboxEncoder (macOS GPU - ~70% CPU reduction)
// - NvencEncoder (NVIDIA GPU - ~80% CPU reduction)
// - LibX264Encoder (CPU fallback)
```

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

## Troubleshooting

### macOS Screen Recording Permission
On first run, macOS will ask for screen recording permission:
1. Go to **System Preferences** → **Privacy & Security** → **Screen Recording**
2. Enable permission for your Java/IDE application
3. Restart the application

### AVFoundation Device Selection
The client tries these device configurations in order:
```
"1:none"   - First screen (main display)
"2:none"   - Second screen
"3:none"   - Third screen
```

### Common Issues

| Issue | Solution |
|-------|----------|
| "No screen capture device" | Grant screen recording permission in System Preferences |
| "Connection refused" | Ensure ScreenAI-Server is running on port 8080 |
| "Connection timeout" | Check server IP/port, firewall settings |
| "Room not found" | Verify room ID matches host's room |
| "Video not displaying" | Wait for keyframe, check decoder logs |
| "mvnw: No such file" | Run: `mvn -N io.takari:maven:wrapper` |

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

## License

MIT License - See LICENSE file for details.

---

## Related Projects

- **[ScreenAI-Server](https://github.com/rkumar1001/ScreenAI-Server)** - Reactive WebSocket relay server (Spring WebFlux + Netty)

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

**Made with ☕ and JavaFX**
