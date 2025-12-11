# ScreenAI Client - Real-Time Screen Sharing Desktop Application

> A **JavaFX desktop client** for real-time screen sharing with dual-role support (Host/Viewer) using Spring Framework dependency injection and WebSocket connectivity to a relay server.


![ScreenAI Client UI](https://img.shields.io/badge/UI-JavaFX-blue) ![Spring Framework](https://img.shields.io/badge/Framework-Spring-green) ![WebSocket](https://img.shields.io/badge/Protocol-WebSocket-brightgreen)

## 📖 Table of Contents

- [Overview](#overview)
- [Quick Start](#quick-start)
- [System Architecture](#system-architecture)
- [Features](#features)
- [Installation](#installation)
- [Usage Guide](#usage-guide)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Technology Stack](#technology-stack)
- [WebSocket Protocol](#websocket-protocol)
- [Troubleshooting](#troubleshooting)
- [Development](#development)
- [FAQ](#faq)

---

## Overview

**ScreenAI Client** is a professional desktop application enabling real-time screen sharing with two distinct roles:

### 🔴 **HOST Mode (Presenter)**
- Capture and broadcast your screen
- Choose display or window source
- Select video encoder (H.264)
- Monitor connected viewers in real-time
- Control streaming with Start/Stop buttons

### 🔵 **VIEWER Mode (Watcher)**
- Connect to a presenter's room using Room ID
- Watch live screen stream in high quality
- Monitor performance metrics (FPS, data, latency)


### Key Characteristics
✅ **Desktop GUI** - Pure JavaFX 
✅ **Spring-Managed** - Lightweight dependency injection
✅ **WebSocket Client** - Real-time binary streaming
✅ **Role-Based UI** - Separate interfaces for Host/Viewer
✅ **Cross-Platform** - Windows, macOS, Linux support
✅ **Zero Server** - No embedded server, pure client


---

## Quick Start

### Prerequisites
- **Java 21+** (verify with `java -version`)
- **Maven 3.9+** (included as `./mvnw`)
- **ScreenAI-Server** running (relay server)

### 1. Run the Application

**Option A: From IntelliJ IDE (Recommended)**
1. Open project in IntelliJ IDEA
2. Navigate to `src/main/java/App.java`
3. Click green **▶️ Run** button

**Option B: From Terminal**
```bash
cd /ScreenAI-Client/untitled
./mvnw clean compile exec:java -Dexec.mainClass="App"
```

**Option C: With Custom Server**
```bash
export SCREENAI_SERVER_URL="ws://your-server.com:8080/screenshare"
./mvnw compile exec:java -Dexec.mainClass="App"
```

### 2. Using the Application

#### As a **HOST (Presenter)**
```
1. Select: 🔴 HOST (Share Screen)
2. Click: [Connect to Server]
   - Host: localhost
   - Port: 8080
3. Configure:
   - Screen Source: Display 1 (or choose window)
   - Encoder: H.264 - Balanced
   - Room ID: auto-generated (or custom)
4. Click: [Start Streaming]
5. Share Room ID with viewers
6. View connected viewers count in real-time
```

#### As a **VIEWER (Watcher)**
```
1. Select: 🔵 VIEWER (Watch Stream)
2. Click: [Connect to Server]
   - Host: localhost
   - Port: 8080
3. Enter: Room ID (from presenter)
4. Click: [Join Room]
5. Watch presenter's screen in real-time
6. Monitor: FPS, Data Received, Latency, Quality
7. Controls: Pause, Mute Audio, Save Stream
```

---

## System Architecture

### Component Diagram

```
┌──────────────────────────────────────────────┐
│         ScreenAI Client (This App)           │
│                                              │
│  ┌─────────────────────────────────────┐   │
│  │         JavaFX UI (main.fxml)       │   │
│  │  ┌──────────────┐  ┌──────────────┐│   │
│  │  │ HOST MODE    │  │ VIEWER MODE  ││   │
│  │  │ - Connect    │  │ - Join Room  ││   │
│  │  │ - Settings   │  │ - Video Area ││   │
│  │  │ - Start/Stop │  │ - Metrics    ││   │
│  │  └──────────────┘  └──────────────┘│   │
│  └────────┬──────────────────────┬────┘   │
│           │                      │        │
│  ┌────────▼──────────┐  ┌───────▼──────┐ │
│  │  MainController   │  │ Spring       │ │
│  │  (Event Handlers) │  │ Context      │ │
│  └────────┬──────────┘  │ (DI & Beans) │ │
│           │             └───────┬──────┘ │
│           │                     │        │
│  ┌────────▼──────────────────────▼─────┐ │
│  │         Service Layer               │ │
│  │  ┌──────────────────────────────┐   │ │
│  │  │ ServerConnectionService      │   │ │
│  │  │ (WebSocket client)           │   │ │
│  │  ├──────────────────────────────┤   │ │
│  │  │ ScreenCaptureService         │   │ │
│  │  │ (Screen capture + encoding)  │   │ │
│  │  ├──────────────────────────────┤   │ │
│  │  │ PerformanceMonitorService    │   │ │
│  │  │ (Metrics collection)         │   │ │
│  │  └──────────────────────────────┘   │ │
│  └────────┬─────────────────────────────┘ │
│           │                              │
└───────────┼──────────────────────────────┘
            │
            │ WebSocket (Binary + JSON)
            │ ws://localhost:8080/screenshare
            │
┌───────────▼──────────────────────────────┐
│    ScreenAI-Server (Relay Server)        │
│    Spring Boot WebSocket Server          │
│                                          │
│  ✓ Room Management                       │
│  ✓ Stream Broadcasting                   │
│  ✓ Viewer Management                     │
└──────────────────────────────────────────┘
```

### Data Flow

**Presenter to Viewers:**
```
Screen Capture → H.264 Encode → Binary WebSocket → Server → Broadcast → Viewers
```

**Viewer Receives:**
```
Server → Binary Stream → Decode H.264 → Display in UI
```

---

## Features

### 🔴 **HOST (Presenter) Features**

| Feature | Description |
|---------|-------------|
| **Screen Source Selection** | Choose Display 1/2 or specific window |
| **H.264 Encoder** | Fast/Balanced/Quality presets |
| **Real-time Capture** | 30 FPS screen capture |
| **Auto Room ID** | UUID-based room generation |
| **Streaming Control** | Start/Stop buttons with visual feedback |
| **Viewer Monitoring** | Real-time count of connected viewers |
| **Performance Metrics** | FPS, Status, Live updates |
| **Server Connection** | Auto-reconnect with fallback |
| **Graceful Shutdown** | Clean disconnect and cleanup |

### 🔵 **VIEWER (Watcher) Features**

| Feature | Description |
|---------|-------------|
| **Room Joining** | Connect by Room ID |
| **Live Video Display** | Large 500px+ video area |
| **Stream Status** | Connection, Room, Presenter status |
| **Performance Monitoring** | FPS, Data received, Latency, Quality |
| **Playback Controls** | Pause, Mute Audio, Save Stream |
| **Auto-reconnect** | Recover from connection drops |
| **Real-time Metrics** | Live performance updates |
| **Clean UI** | Intuitive controls and status display |

### 🌐 **Network Features**

- ✅ Binary WebSocket streaming (H.264 fMP4)
- ✅ JSON control protocol
- ✅ Init segment caching (for late joiners)
- ✅ Automatic reconnection (3 attempts)
- ✅ Network error handling
- ✅ Bandwidth monitoring
- ✅ Latency tracking

---

## Installation

### Full Setup

1. **Verify Java**
   ```bash
   java -version
   # Output: openjdk version "21" or higher
   ```

2. **Navigate to Project**
   ```bash
   cd /Users/ScreenAI-Client/untitled
   ```

3. **Build Project**
   ```bash
   ./mvnw clean compile
   # Should see: BUILD SUCCESS
   ```

4. **Verify Build**
   ```bash
   ls -la target/classes/
   # Should see compiled .class files
   ```

### IDE Setup

#### IntelliJ IDEA (Recommended)
```
1. File → Open
2. Select: untitled folder
3. Trust project when prompted
4. Wait for Maven indexing
5. Run → Run 'App'
```

#### VS Code
```
1. File → Open Folder
2. Install: Extension Pack for Java
3. Terminal → Run Task → Maven: compile
4. Run: Java Application
```

#### Eclipse
```
1. File → Import → Maven Projects
2. Select: untitled folder
3. Right-click → Run As → Java Application
```

---

## Usage Guide

### Starting the Application

```bash
./mvnw compile exec:java -Dexec.mainClass="App"
```

Window opens showing:
```
┌─────────────────────────────────────────┐
│   🎬 ScreenAI - Real-Time Sharing      │
├─────────────────────────────────────────┤
│                                         │
│  Select Your Role:                      │
│  ○ 🔴 HOST (Share Screen)               │
│  ○ 🔵 VIEWER (Watch Stream)             │
│                                         │
│  [Host Mode Panel] OR [Viewer Panel]   │
│                                         │
└─────────────────────────────────────────┘
```

### HOST Workflow

```
STEP 1: Select Host Mode
├─ Click: 🔴 HOST radio button
└─ Displays: Host configuration panel

STEP 2: Server Connection
├─ Host: localhost
├─ Port: 8080
└─ Click: [Connect to Server]
   └─ Status changes to "Connected"

STEP 3: Configure Sharing
├─ Screen Source: Display 1 (or window)
├─ Encoder: H.264 - Balanced
├─ Room ID: auto-generated UUID
└─ View: Connected viewers count

STEP 4: Start Sharing
├─ Click: [Start Streaming]
├─ Status: 🟢 Streaming
├─ Share Room ID with viewers
└─ Monitor: Real-time viewer count

STEP 5: Stop Sharing
├─ Click: [Stop]
├─ Status: 🟡 Ready
└─ Click: [Disconnect]
   └─ Status: Disconnected
```

### VIEWER Workflow

```
STEP 1: Select Viewer Mode
├─ Click: 🔵 VIEWER radio button
└─ Displays: Viewer panel with video area

STEP 2: Server Connection
├─ Host: localhost
├─ Port: 8080
└─ Click: [Connect to Server]
   └─ Status: Connected

STEP 3: Join Room
├─ Room ID: (ask from presenter)
├─ Click: [Join Room]
└─ Status: Room Joined

STEP 4: Watch Stream
├─ Video displays in center area
├─ Monitor metrics:
│  ├─ Frame Rate (FPS)
│  ├─ Data Received (MB)
│  ├─ Latency (ms)
│  └─ Quality
└─ Use controls:
   ├─ [⏸ Pause Stream]
   ├─ [🔊 Mute Audio]
   └─ [📥 Save Stream]

STEP 5: Disconnect
└─ Click: [🔌 Disconnect]
   └─ Status: Disconnected
```

---

## Project Structure

### Source Code Organization

```
untitled/
├── src/main/java/
│   ├── App.java
│   │   └─ Entry point (launches JavaFX + Spring)
│   │
│   ├── ScreenAIClientApplication.java
│   │   └─ Spring context manager (@Configuration)
│   │
│   ├── MainController.java
│   │   └─ Main UI controller (role switching, events)
│   │
│   ├── config/
│   │   └── WebSocketClientConfig.java
│   │       └─ Spring beans (@Bean serverConnectionService)
│   │
│   ├── service/
│   │   ├── ServerConnectionService.java (WebSocket client)
│   │   ├── ScreenCaptureService.java (screen capture + H.264)
│   │   ├── PerformanceMonitorService.java (metrics)
│   │   └── ScreenSourceDetector.java (display detection)
│   │
│   ├── controller/
│   │   ├── HostController.java (presenter logic)
│   │   └── ViewerController.java (viewer logic)
│   │
│   ├── model/
│   │   ├── ScreenSource.java (display/window model)
│   │   └── PerformanceMetrics.java (metrics data)
│   │
│   ├── encoder/
│   │   ├── VideoEncoderStrategy.java (interface)
│   │   ├── H264VideoToolboxEncoder.java (macOS)
│   │   ├── LibX264Encoder.java (Linux/Windows)
│   │   ├── NvencEncoder.java (NVIDIA GPU)
│   │   └── VideoEncoderFactory.java (factory pattern)
│   │
│   └── ScreenSharingManager.java (legacy - optional)
│
├── src/main/resources/
│   ├── ui/
│   │   └── main.fxml (JavaFX layout - Host + Viewer modes)
│   │
│   └── application.yml (configuration)
│
├── target/ (build output)
├── pom.xml (Maven dependencies)
└── README.md (this file)
```

### Key Files Explained

| File | Lines | Purpose |
|------|-------|---------|
| `App.java` | ~50 | JavaFX entry point, launches Spring context |
| `MainController.java` | ~300 | Main UI, role switching, event handlers |
| `ServerConnectionService.java` | ~120 | WebSocket client, protocol communication |
| `HostController.java` | ~200 | Presenter logic, screen capture, streaming |
| `ViewerController.java` | ~200 | Viewer logic, stream reception, metrics |
| `main.fxml` | ~200 | UI layout (Host panel + Viewer panel) |
| `WebSocketClientConfig.java` | ~50 | Spring bean definitions |
| `ScreenCaptureService.java` | ~1000+ | Screen capture + H.264 encoding |
| `pom.xml` | ~150 | Maven dependencies (Spring, JavaFX, FFmpeg) |

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

# Server Configuration
screenai:
  server:
    url: ws://localhost:8080/screenshare
    reconnect-attempts: 3
    reconnect-delay: 5000
  
  # Video Settings
  video:
    frame-rate: 30
    bitrate: 2500
    quality: high  # high/medium/low
  
  # Performance
  performance:
    monitor-cpu: true
    monitor-memory: true
    monitor-network: true
```

### Environment Variables

Override without editing files:

```bash
# Custom server
export SCREENAI_SERVER_URL="ws://my-server.com:8080/screenshare"

# Run with custom config
./mvnw compile exec:java -Dexec.mainClass="App"

# Or run JAR
java -jar target/untitled-1.0-SNAPSHOT.jar
```

---

## Technology Stack

### Core Technologies

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **UI** | JavaFX | 21.0.2 | Desktop GUI |
| **DI Framework** | Spring Context | 6.0.13 | Dependency injection |
| **WebSocket** | Spring WebSocket | 6.0.13 | Real-time communication |
| **Screen Capture** | JavaCV | 1.5.9 | Display capture |
| **Encoding** | FFmpeg | 6.0 | H.264 video codec |
| **JSON** | Jackson | 2.16.0 | Protocol messages |
| **Logging** | SLF4J + Logback | 2.0.9 | Application logs |

### Java Requirements
- **Java Version**: 21
- **Build Tool**: Maven 3.9+
- **Target**: Java 21

---

## WebSocket Protocol

### Connection Details

**Endpoint**: `ws://localhost:8080/screenshare`

### Message Protocol

#### HOST: Create Room (JSON)
```json
{
  "type": "create-room",
  "roomId": "room-abc123"
}
```

**Server Response:**
```json
{
  "type": "room-created",
  "roomId": "room-abc123",
  "role": "presenter",
  "message": "Room created successfully"
}
```

#### VIEWER: Join Room (JSON)
```json
{
  "type": "join-room",
  "roomId": "room-abc123"
}
```

**Server Response:**
```json
{
  "type": "room-joined",
  "roomId": "room-abc123",
  "role": "viewer",
  "hasPresenter": true,
  "message": "Joined room successfully"
}
```

#### Binary Video Frames
```
Message 1: H.264 fMP4 Init Segment (codec info)
Message 2+: H.264 fMP4 Media Segments (video frames)
```

---

## Troubleshooting

### Build Problems

**Problem**: `BUILD FAILURE - Cannot compile`
```bash
Solution:
1. Verify Java: java -version (must be 21+)
2. Clean cache: ./mvnw clean
3. Rebuild: ./mvnw compile
4. Check internet: dependencies download
```

**Problem**: `Cannot find mvnw`
```bash
Solution:
1. Check location: ls -la mvnw
2. Make executable: chmod +x mvnw
3. Try: ./mvnw clean compile
```

### Runtime Problems

**Problem**: `Cannot connect to server`
```
Error: ❌ WebSocket connection failed

Solution:
1. Check server running: curl http://localhost:8080
2. Verify URL in application.yml
3. Firewall: Allow port 8080
4. Network: Ping server machine
```

**Problem**: `No video received in viewer`
```
Solution:
1. Verify presenter started streaming
2. Check screen capture working
3. Verify Room ID correct
4. Check network bandwidth
```

**Problem**: `Spring bean not found`
```
Error: No bean of type 'ServerConnectionService' found

Solution:
1. Verify @Service annotations exist
2. Check ComponentScan configuration
3. Rebuild: ./mvnw clean compile
4. Clear IDE caches
```

**Problem**: `JavaFX window won't display`
```
Solution:
1. Check DISPLAY variable (Linux): export DISPLAY=:0
2. Permissions (macOS): System Preferences → Security
3. Try running from IDE instead of terminal
4. Update JavaFX: ./mvnw dependency:download-sources
```

### Performance Issues

**Problem**: `High CPU usage`
```
Solution:
1. Lower FPS: screenai.video.frame-rate: 15
2. Lower quality: screenai.video.quality: low
3. Reduce bitrate: screenai.video.bitrate: 1500
4. Try different encoder
```

**Problem**: `Network lag / buffering`
```
Solution:
1. Check bandwidth: Need 2-5 Mbps minimum
2. Reduce FPS: 15 FPS instead of 30
3. Lower bitrate: 1500 kbps instead of 2500
4. Move closer to server: Reduce latency
```

---

## Development

### Setting Up Development Environment

```bash
# 1. Clone repository
git clone <your-repo>
cd untitled

# 2. Open in IDE (IntelliJ)
open -a "IntelliJ IDEA" .

# 3. Build
./mvnw clean install

# 4. Run tests (if any)
./mvnw test

# 5. Run application
./mvnw compile exec:java -Dexec.mainClass="App"
```

### Code Style Guidelines

- **Java Version**: 21
- **Compiler Target**: 21
- **Character Encoding**: UTF-8
- **Line Length**: 120 characters

### Adding New Features

```java
// In service/ package
@Service
public class MyNewService {
    // Spring manages lifecycle
}

// In controller/ package  
public class MyNewController {
    @Autowired
    private MyNewService service;
    
    // Use injected dependency
}

// In config package
@Bean
public MyNewService myNewService() {
    return new MyNewService();
}
```

### Building for Distribution

```bash
# Create executable JAR
./mvnw clean package -DskipTests

# Run JAR
java -jar target/untitled-1.0-SNAPSHOT.jar

# With custom server
export SCREENAI_SERVER_URL="ws://my-server:8080/screenshare"
java -jar target/untitled-1.0-SNAPSHOT.jar
```

---

## FAQ

### Q: Is this a Spring Boot application?
**A:** No. Uses Spring Framework for DI only, not Spring Boot. No embedded server.

### Q: Can I run without a server?
**A:** No. Requires ScreenAI-Server (your relay server) to be running.

### Q: What video formats are supported?
**A:** H.264 in fMP4 container format only. Audio not supported.

### Q: Can I modify the UI?
**A:** Yes. Edit `src/main/resources/ui/main.fxml` with any text editor or JavaFX Scene Builder.

### Q: How many viewers can connect?
**A:** Limited by server. Typical: 1-5 viewers per presenter.

### Q: What's the minimum internet speed?
**A:** At least 2-3 Mbps for smooth 30 FPS at 1080p.

### Q: Can I run multiple clients from same machine?
**A:** Yes. Each instance is independent, different Room IDs.

### Q: How do I add authentication?
**A:** Modify `ServerConnectionService.connect()` to include auth headers.

### Q: Is encryption supported?
**A:** Use `wss://` (WebSocket Secure) if server supports SSL.

---

## Common Commands

```bash
# Build
./mvnw clean compile

# Run
./mvnw compile exec:java -Dexec.mainClass="App"

# Package JAR
./mvnw clean package -DskipTests

# Run JAR
java -jar target/untitled-1.0-SNAPSHOT.jar

# With custom server
export SCREENAI_SERVER_URL="ws://your-server:8080/screenshare"
java -jar target/untitled-1.0-SNAPSHOT.jar

# Check Java
java -version

# Check Maven
./mvnw -v
```

---

## Version History

| Version | Date | Status | Notes |
|---------|------|--------|-------|
| 1.0-SNAPSHOT | Dec 5, 2025 | Production Ready | Initial release |

---

## Support

### Getting Help

1. **Check Logs** - Run from IDE for detailed logging
2. **Review Config** - Verify application.yml settings
3. **Test Server** - Ensure ScreenAI-Server is running
4. **Rebuild** - `./mvnw clean compile`
5. **Clear Caches** - Restart IDE if needed

---

## License

[Add your license information here]

---

## Author & Contact

**ScreenAI Client** - Real-Time Screen Sharing Solution

**Year**: 2025 | **Status**: Production Ready ✅

---

**Ready to start? Follow the [Quick Start](#quick-start) section!** 🚀

---

## Quick Start

### Prerequisites
- **Java 21** or higher
- **Maven 3.9+** (included as `./mvnw`)
- **ScreenAI-Server** running (your relay server)

### 1. Clone/Open Project
```bash
cd /ScreenAI-Client/untitled
```

### 2. Build
```bash
./mvnw clean compile
```

### 3. Run

#### Option A: From IntelliJ IDE (Recommended)
1. Open the project in IntelliJ
2. Navigate to `src/main/java/App.java`
3. Click the green ▶️ **Run** button
4. JavaFX window opens automatically

#### Option B: From Terminal
```bash
./mvnw compile exec:java -Dexec.mainClass="App"
```

#### Option C: With Custom Server
```bash
export SCREENAI_SERVER_URL="ws://your-server:8080/screenshare"
./mvnw compile exec:java -Dexec.mainClass="App"
```

### 4. Use the Application

**As a Presenter:**
1. Select **Host** mode
2. Click **Connect to Server**
3. Click **Create Room**
4. Click **Start Sharing** → Your screen is now broadcasting

**As a Viewer:**
1. Select **Viewer** mode
2. Click **Connect to Server**
3. Enter the **Room ID** from the presenter
4. Click **Join Room** → You can now see the presenter's screen

---

## Architecture

### System Diagram

```
┌─────────────────────────────────────────────┐
│         ScreenAI Client (This App)          │
│                                             │
│  ┌──────────────┐      ┌──────────────┐   │
│  │  JavaFX UI   │◄────►│Spring Context│   │
│  │(Presenter/   │      │  (DI, Beans) │   │
│  │  Viewer)     │      └──────────────┘   │
│  └──────────────┘              ▲           │
│         │                      │           │
│         ├──────────┬───────────┤           │
│         │          │           │           │
│     ┌───▼──┐  ┌────▼────┐ ┌──▼────────┐ │
│     │Screen│  │ Server  │ │Performance│ │
│     │Capture│  │ Conn    │ │ Monitor   │ │
│     └───┬──┘  └────┬────┘ └──┬────────┘ │
│         │          │         │           │
│         └──────┬───┴─────────┘           │
│                │                         │
│       ┌────────▼──────────┐              │
│       │ WebSocket Client  │              │
│       │(Spring Framework) │              │
│       └────────┬──────────┘              │
│                │                         │
└────────────────┼─────────────────────────┘
                 │ WebSocket
                 │ ws://server:8080/screenshare
                 │
┌────────────────▼─────────────────────────┐
│     ScreenAI-Server (Your Server)        │
│     Spring Boot WebSocket Server         │
│                                          │
│  - Room Management                       │
│  - Stream Relay                          │
│  - Viewer Broadcasting                   │
└──────────────────────────────────────────┘
```

### Data Flow

**Presenter Side:**
```
Screen → Capture → Encode (H.264) → Binary → WebSocket → Server → Broadcast
```

**Viewer Side:**
```
Server → Binary (Video) → Decode (H.264) → Display
```

---

## Features

### Host/Presenter Features
- ✅ Select screen source (display or window)
- ✅ Choose video encoder (H.264 options)
- ✅ Real-time screen capture at 30 FPS
- ✅ Adjustable quality and bitrate
- ✅ View connected viewer count
- ✅ Performance monitoring (CPU, memory, network)
- ✅ Graceful disconnect and cleanup

### Viewer Features
- ✅ Browse available rooms
- ✅ Join room by Room ID
- ✅ Real-time video playback
- ✅ Performance statistics display
- ✅ Frame rate monitoring
- ✅ Data throughput monitoring

### Server Integration
- ✅ Automatic reconnection (3 attempts)
- ✅ JSON-based control protocol
- ✅ Binary H.264 fMP4 video streaming
- ✅ Init segment caching for late joiners
- ✅ Clean disconnection handling

---

## Installation

### Full Installation Steps

1. **Verify Java**
   ```bash
   java -version
   # Should show: openjdk version "21" or higher
   ```

2. **Clone Repository**
   ```bash
   git clone <your-repo-url>
   cd ScreenAI-Client/untitled
   ```

3. **Download Dependencies**
   ```bash
   ./mvnw dependency:download-sources
   ```

4. **Build Project**
   ```bash
   ./mvnw clean compile
   ```

5. **Verify Build**
   ```bash
   # Should see: BUILD SUCCESS
   # Should compile: 21 source files
   ```

### IDEs

**IntelliJ IDEA (Recommended)**
1. File → Open → Select project folder
2. Trust the project when prompted
3. Maven automatically indexes dependencies
4. Run → Run 'App'

**Eclipse**
1. File → Import → Existing Maven Projects
2. Select project folder
3. Wait for dependencies to download
4. Run as Java Application (App.java)

**VS Code**
1. Open folder in VS Code
2. Install Extension Pack for Java
3. Terminal → Run Task → Maven: compile
4. Terminal → Java: Run Server or Debug Server

---

## Usage Guide

### Starting the Application

```bash
./mvnw compile exec:java -Dexec.mainClass="App"
```

### UI Components

#### Main Screen
```
┌─────────────────────────────────────┐
│        ScreenAI - Screen Sharing    │
├─────────────────────────────────────┤
│                                     │
│  Role Selection:                    │
│  ○ Host (Presenter)   ○ Viewer      │
│                                     │
├─────────────────────────────────────┤
│                                     │
│  [Host Section]      [Viewer Panel] │
│  ├─ Server URL       ├─ Status      │
│  ├─ Room ID          ├─ FPS         │
│  ├─ Screen Source    ├─ Data        │
│  ├─ Encoder          └─ Video       │
│  ├─ [Connect]                       │
│  ├─ [Start/Stop]                    │
│  └─ Performance                     │
│                                     │
└─────────────────────────────────────┘
```

### Host Workflow

1. **Select Host Mode**
   - Click `Host` radio button

2. **Connect to Server**
   ```
   Server URL: ws://localhost:8080/screenshare
   Port: 8080
   Click: [Connect to Server]
   ```

3. **Configure Sharing**
   ```
   Screen Source: Display 1 (or select window)
   Encoder: H.264 - Balanced
   Room ID: auto-generated or custom
   ```

4. **Start Sharing**
   ```
   Click: [Create Room]
   Click: [Start Sharing]
   ```

5. **Monitor Session**
   ```
   View:
   - Connected viewers count
   - FPS (frames per second)
   - CPU/Memory usage
   - Network throughput
   ```

6. **Stop Sharing**
   ```
   Click: [Stop]
   Click: [Disconnect]
   ```

### Viewer Workflow

1. **Select Viewer Mode**
   - Click `Viewer` radio button

2. **Connect to Server**
   ```
   Same as host
   ```

3. **Join Room**
   ```
   Room ID: [Ask from presenter]
   Click: [Join Room]
   ```

4. **Watch Stream**
   ```
   Video displays in center panel
   Monitor:
   - Current FPS
   - Data received
   - Connection status
   ```

5. **Disconnect**
   ```
   Click: [Disconnect Viewer]
   ```

---

## Project Structure

### Directory Layout

```
untitled/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── App.java                      # Entry point
│   │   │   ├── ScreenAIClientApplication.java # Spring context
│   │   │   │
│   │   │   ├── config/
│   │   │   │   └── WebSocketClientConfig.java # Spring beans
│   │   │   │
│   │   │   ├── service/
│   │   │   │   ├── ServerConnectionService.java    # WebSocket client
│   │   │   │   ├── ScreenCaptureService.java       # Screen capture
│   │   │   │   ├── PerformanceMonitorService.java  # Metrics
│   │   │   │   └── ScreenSourceDetector.java       # Display detection
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   ├── MainController.java       # Main UI
│   │   │   │   ├── HostController.java       # Presenter logic
│   │   │   │   └── ViewerController.java     # Viewer logic
│   │   │   │
│   │   │   ├── model/
│   │   │   │   ├── ScreenSource.java         # Screen data model
│   │   │   │   └── PerformanceMetrics.java   # Metrics model
│   │   │   │
│   │   │   └── encoder/
│   │   │       ├── VideoEncoderFactory.java
│   │   │       ├── H264VideoToolboxEncoder.java
│   │   │       ├── LibX264Encoder.java
│   │   │       └── NvencEncoder.java
│   │   │
│   │   └── resources/
│   │       ├── ui/
│   │       │   └── main.fxml                 # JavaFX UI layout
│   │       └── application.yml               # Configuration
│   │
│   └── test/
│       └── java/                             # Test files (if any)
│
├── target/                                   # Build output
├── pom.xml                                   # Maven config
├── mvnw & mvnw.cmd                          # Maven wrapper
└── README.md                                # This file
```

### Important Files

| File | Purpose |
|------|---------|
| `App.java` | Launches JavaFX + initializes Spring context |
| `ScreenAIClientApplication.java` | Manages Spring context lifecycle |
| `WebSocketClientConfig.java` | Defines Spring beans (WebSocket, services) |
| `ServerConnectionService.java` | Handles WebSocket protocol communication |
| `HostController.java` | Implements presenter/host logic |
| `ViewerController.java` | Implements viewer logic |
| `MainController.java` | Main UI event handlers |
| `application.yml` | Server URL and settings |
| `pom.xml` | Maven dependencies |

---

## Configuration

### Via application.yml

Located: `src/main/resources/application.yml`

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
    url: ws://localhost:8080/screenshare    # Server WebSocket endpoint
    reconnect-attempts: 3                    # Auto-reconnect tries
    reconnect-delay: 5000                    # Delay in milliseconds
  
  video:
    frame-rate: 30                          # Capture FPS
    bitrate: 2500                           # Video bitrate in kbps
    quality: high                           # high/medium/low
  
  performance:
    monitor-cpu: true                       # Track CPU usage
    monitor-memory: true                    # Track RAM usage
    monitor-network: true                   # Track bandwidth
```

### Via Environment Variables

Override settings without editing files:

```bash
# Custom server URL
export SCREENAI_SERVER_URL="ws://my-server.com:8080/screenshare"

# Run with custom config
./mvnw compile exec:java -Dexec.mainClass="App"
```

### Via Code (config/WebSocketClientConfig.java)

```java
@Bean
public ServerConnectionService serverConnectionService() {
    String serverUrl = System.getenv("SCREENAI_SERVER_URL") != null ? 
        System.getenv("SCREENAI_SERVER_URL") : 
        "ws://localhost:8080/screenshare";
    
    return new ServerConnectionService(serverUrl);
}
```

---

## Technology Stack

### Core Frameworks

| Component | Technology | Version | Purpose |
|-----------|-----------|---------|---------|
| **UI Framework** | JavaFX | 21.0.2 | Desktop GUI |
| **DI Container** | Spring Context | 6.0.13 | Dependency injection |
| **WebSocket** | Spring WebSocket | 6.0.13 | WebSocket protocol |
| **Screen Capture** | JavaCV | 1.5.9 | Screen capture |
| **Video Codec** | FFmpeg | 6.0 | H.264 encoding |

### Supporting Libraries

| Library | Version | Purpose |
|---------|---------|---------|
| Jackson | 2.16.0 | JSON processing |
| SLF4J | 2.0.9 | Logging API |
| Logback | 1.4.11 | Logging implementation |

### Java

- **Version**: 21
- **Maven**: 3.9.x (included as wrapper)
- **Build Tool**: Maven

---

## WebSocket Protocol

### Connection

**Server Endpoint**: `ws://localhost:8080/screenshare`

### Message Types

#### Text Messages (JSON Control)

**Presenter Creates Room:**
```json
{
  "type": "create-room",
  "roomId": "meeting-123"
}
```

**Server Response:**
```json
{
  "type": "room-created",
  "roomId": "meeting-123",
  "role": "presenter",
  "message": "Room created successfully"
}
```

**Viewer Joins Room:**
```json
{
  "type": "join-room",
  "roomId": "meeting-123"
}
```

**Server Response:**
```json
{
  "type": "room-joined",
  "roomId": "meeting-123",
  "role": "viewer",
  "hasPresenter": true,
  "message": "Joined room successfully"
}
```

**Leave Room:**
```json
{
  "type": "leave-room"
}
```

#### Binary Messages (Video Frames)

1. **First Message**: H.264 fMP4 init segment (codec info)
2. **Subsequent**: H.264 fMP4 media segments (video frames)

**Flow:**
```
Presenter:
  Send init segment → Server → Cache for late joiners
  Send frame 1 → Server → Broadcast to all viewers
  Send frame 2 → Server → Broadcast to all viewers
  ...
```

---

## Troubleshooting

### Build Issues

#### Problem: `BUILD FAILURE - Cannot compile`
```
Solution:
1. Verify Java 21: java -version
2. Clean cache: ./mvnw clean
3. Rebuild: ./mvnw compile
4. Check internet: dependencies may fail to download
```

#### Problem: `Dependencies not downloading`
```
Solution:
1. Check internet connection
2. Verify Maven wrapper: ls -la mvnw
3. Update Maven: ./mvnw -v
4. Try: ./mvnw dependency:download-sources
```

### Runtime Issues

#### Problem: `Cannot connect to server`
```
Log: ❌ WebSocket connection failed

Solution:
1. Check server is running: curl http://localhost:8080
2. Verify URL in application.yml
3. Firewall: Allow port 8080
4. Network: Check connectivity to server
```

#### Problem: `No video received by viewer`
```
Solution:
1. Verify presenter started sharing
2. Check screen capture is working
3. Look at logs for binary message errors
4. Try smaller resolution or lower quality
```

#### Problem: `Spring bean not found`
```
Log: No bean of type 'ServerConnectionService' found

Solution:
1. Check @Service, @Component annotations exist
2. Verify ComponentScan in ScreenAIClientApplication
3. Rebuild: ./mvnw clean compile
4. Restart IDE (clear caches)
```

#### Problem: `JavaFX window won't appear`
```
Solution:
1. Verify JavaFX display: export DISPLAY=:0 (Linux)
2. Check permissions: macOS may require accessibility
3. Run from IDE instead of terminal
4. Try: ./mvnw javafx:run
```

### Performance Issues

#### Problem: `High CPU usage`
```
Solution:
1. Lower frame rate: screenai.video.frame-rate: 15
2. Reduce resolution: 1280x720 instead of 1920x1080
3. Lower quality: screenai.video.quality: low
4. Check encoder: Try different H.264 encoder
```

#### Problem: `Network lag / buffering`
```
Solution:
1. Lower bitrate: screenai.video.bitrate: 1500
2. Reduce FPS: screenai.video.frame-rate: 15
3. Check bandwidth: Need 2-5 Mbps minimum
4. Move closer to server: Reduce network latency
```

---

## Development

### Local Development Setup

1. **Clone Repository**
   ```bash
   git clone <your-repo-url>
   cd untitled
   ```

2. **Open in IDE**
   ```bash
   # IntelliJ
   open -a "IntelliJ IDEA" .
   
   # Or VSCode
   code .
   ```

3. **Configure Maven**
   ```bash
   ./mvnw clean install
   ```

4. **Run Tests** (if any)
   ```bash
   ./mvnw test
   ```

### Code Style

- **Java Version**: 21
- **Compiler Target**: 21
- **UTF-8 Encoding**: All files
- **Line Length**: 120 characters recommended

### Adding New Features

1. **Create in appropriate package**
   - Service → `service/` (add @Service)
   - Controller → `controller/` (add @Component)
   - Model → `model/` (add @Data if needed)

2. **Add Spring annotations**
   ```java
   @Service
   public class MyNewService {
       // Spring will manage lifecycle
   }
   ```

3. **Inject dependencies**
   ```java
   @Autowired
   private ServerConnectionService serverConnection;
   ```

4. **Test locally**
   ```bash
   ./mvnw clean compile
   ./mvnw compile exec:java -Dexec.mainClass="App"
   ```

### Building for Distribution

```bash
# Create executable JAR
./mvnw clean package -DskipTests

# Run JAR
java -jar target/untitled-1.0-SNAPSHOT.jar

# With custom server
export SCREENAI_SERVER_URL="ws://my-server:8080/screenshare"
java -jar target/untitled-1.0-SNAPSHOT.jar
```

---

## Contributing

### Guidelines

1. **Fork & Branch**
   ```bash
   git checkout -b feature/your-feature
   ```

2. **Follow Code Style**
   - Use Spring annotations properly
   - Add meaningful comments
   - Keep methods focused and small

3. **Test Changes**
   ```bash
   ./mvnw clean compile
   ```

4. **Commit with Clear Messages**
   ```bash
   git commit -m "Add: WebSocket reconnection logic"
   ```

5. **Push & Create PR**
   ```bash
   git push origin feature/your-feature
   ```

---

## Support & Contact

### Getting Help

1. **Check Logs**
   - Run from IDE to see detailed logs
   - Look for ERROR and WARNING messages

2. **Review Configuration**
   - Verify `application.yml` settings
   - Check server URL is correct

3. **Test Server Connection**
   - Ensure ScreenAI-Server is running
   - Verify network connectivity

4. **Rebuild Project**
   - Run: `./mvnw clean compile`
   - Clear caches in IDE if needed

---

## License

[Add your license information here]

---

## Version Info

- **Application**: ScreenAI Client
- **Version**: 1.0-SNAPSHOT
- **Java**: 21
- **Spring**: 6.0.13
- **JavaFX**: 21.0.2
- **Last Updated**: December 4, 2025

---

## Quick Reference

### Common Commands

```bash
# Build
./mvnw clean compile

# Run
./mvnw compile exec:java -Dexec.mainClass="App"

# Package
./mvnw clean package -DskipTests

# Run JAR
java -jar target/untitled-1.0-SNAPSHOT.jar

# With custom server
export SCREENAI_SERVER_URL="ws://your-server:8080/screenshare"
java -jar target/untitled-1.0-SNAPSHOT.jar

# Check Java version
java -version

# Check Maven
./mvnw -v
```

### File Locations

```
Config:        src/main/resources/application.yml
Main Entry:    src/main/java/App.java
WebSocket:     src/main/java/service/ServerConnectionService.java
UI Layout:     src/main/resources/ui/main.fxml
Spring Beans:  src/main/java/config/WebSocketClientConfig.java
```

---

