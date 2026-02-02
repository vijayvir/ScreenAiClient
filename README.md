# ScreenAI Client - Secure Cross-Platform Screen Sharing

> A **secure JavaFX desktop client** for real-time screen sharing with JWT authentication, room password protection, and hardware-accelerated encoding. **Supports macOS, Windows, and Linux.**

![JavaFX 21](https://img.shields.io/badge/UI-JavaFX_21-blue) ![JavaCV 1.5.9](https://img.shields.io/badge/Video-JavaCV_1.5.9-orange) ![Spring Framework](https://img.shields.io/badge/Framework-Spring_6.x-green) ![WebSocket](https://img.shields.io/badge/Protocol-WebSocket-brightgreen) ![Java 21](https://img.shields.io/badge/Java-21-red) ![Cross Platform](https://img.shields.io/badge/Platform-macOS%20%7C%20Windows%20%7C%20Linux-purple)

## 📖 Table of Contents

- [Overview](#overview)
- [Security Features](#-security-features)
- [Quick Start](#-quick-start)
- [System Architecture](#system-architecture)
- [Configuration](#-configuration)
- [Troubleshooting](#troubleshooting)

---

## Overview

**ScreenAI Client** is a secure desktop application enabling real-time screen sharing with comprehensive authentication and bidirectional streaming support.

### 🔴 **HOST Mode (Presenter)**
- **Secure Authentication** - JWT-based login required before streaming
- **Room Password Protection** - Create password-protected private rooms
- **Access Code Display** - Auto-generated codes shown for protected rooms with copy button
- **Viewer Management** - Approve, kick, or ban viewers
- Encode to **H.264/MPEG-TS** using FFmpeg with hardware acceleration
- Stream at **~28-30 FPS** with ultrafast/zerolatency preset
- Support for multiple encoders: VideoToolbox (macOS), NVENC (NVIDIA), libx264 (CPU)

### 🔵 **VIEWER Mode (Watcher)**
- **Secure Join** - Enter access code for password-protected rooms
- **Room Password Dialog** - User-friendly dialog for entering security credentials
- Connect to host's room using Room ID
- Decode using **FFmpegFrameGrabber** with batch processing (~12-15 FPS)
- Display live video in JavaFX ImageView with real-time FPS metrics

### 🔄 **Dual Mode (Bidirectional)**
- Host and view streams simultaneously
- Modern tabbed interface
- Unified connection management

---

## 🔐 Security Features

### Authentication
- **JWT Token Authentication** - Secure login/register before connecting
- **Access Token + Refresh Token** - 15 min access tokens with automatic refresh
- **Encrypted Credential Storage** - AES-256-GCM encryption for saved credentials
- **Remember Me** - Optional persistent login with secure storage
- **Auto-Login** - Automatic authentication with saved credentials

### Room Security
- **Password Protection** - Optional password when creating rooms
- **Access Codes** - Auto-generated codes for password-protected rooms (displayed in UI)
- **Viewer Approval** - Optional manual approval for viewers
- **Kick/Ban Viewers** - Remove unwanted viewers from your room

### UI Components
- **Login Dialog** - Modern dark-themed login/register popup
- **Room Password Dialog** - Enter password or access code to join protected rooms
- **Access Code Display** - Visible access code with copy button for hosts

---

## 🚀 Quick Start

### Prerequisites
- **Java 21+** (verify with `java -version`)
- **Maven 3.9+** (included as `./mvnw`)
- **ScreenAI-Server** running on `ws://localhost:8080/screenshare`

### 1. Configure Environment

Create a `.env` file in the project root:

```env
# Server Configuration
SERVER_URL=ws://localhost:8080/screenshare
HTTP_URL=http://localhost:8080

# Security
TOKEN_ENCRYPTION_KEY=your-32-character-encryption-key!
CREDENTIALS_STORAGE_DIR=~/.screenai
```

### 2. Run the Application

**Using Maven Wrapper (Recommended):**
```bash
chmod +x mvnw
./mvnw javafx:run
```

**Alternative:**
```bash
./mvnw compile exec:java -Dexec.mainClass="App"
```

### 3. First Time Setup

1. **Login Dialog appears** - Enter credentials
2. **Register** - Create a new account (first time)
3. **Login** - Authenticate with your credentials
4. **Connect** - Click Connect button to join server
5. **Start Sharing** - Create a room and share your screen!

---

## 📱 Using the Application

### As a **HOST (Presenter)**

```
1. Launch application → Login Dialog appears
2. Enter username/password → Click Login (or Register first time)
3. Click [🔌 Connect] → Connects to server
4. (Optional) Enter custom Room ID
5. Click [▶ Start Sharing]
6. If room is password-protected:
   - Access Code appears in "Your Room" section
   - Click [📋 Copy] to copy access code
7. Share Room ID + Access Code with viewers
```

### As a **VIEWER (Watcher)**

```
1. Launch application → Login Dialog appears
2. Enter username/password → Click Login
3. Click [🔌 Connect] → Connects to server
4. Switch to "Watch Stream" tab
5. Enter Room ID from host
6. Click [👁 Watch]
7. If room is password-protected:
   - Password Dialog appears
   - Enter Access Code received from host
8. Watch live stream!
```

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      ScreenAI Client (Secure)                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                    Authentication Layer                         │ │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │ │
│  │  │ LoginDialog      │  │ TokenStorage     │  │ AuthService  │  │ │
│  │  │ • Username/Pass  │  │ • AES-256-GCM    │  │ • JWT Auth   │  │ │
│  │  │ • Register       │  │ • Refresh Token  │  │ • Auto-Login │  │ │
│  │  │ • Remember Me    │  │ • Persist Creds  │  │ • Token Mgmt │  │ │
│  │  └──────────────────┘  └──────────────────┘  └──────────────┘  │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  ┌──────────────────────┐        ┌──────────────────────┐          │
│  │    HOST MODE         │        │    VIEWER MODE        │          │
│  │                      │        │                       │          │
│  │  ScreenCaptureService│        │  RoomPasswordDialog   │          │
│  │       ↓              │        │  (Enter Access Code)  │          │
│  │  VideoEncoderFactory │        │       ↓               │          │
│  │  (Hardware Accel)    │        │  H264DecoderService   │          │
│  │       ↓              │        │       ↓               │          │
│  │  Access Code Display │        │  JavaFX ImageView     │          │
│  │  (Copy to Clipboard) │        │                       │          │
│  └──────────────────────┘        └──────────────────────┘          │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │           DualModeMainController (JavaFX FXML)                  │ │
│  │  • Tabbed Interface (Share Screen / Watch Stream)               │ │
│  │  • Access Code Display Section (for hosts)                      │ │
│  │  • Connection Management with Auth                              │ │
│  └────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ WebSocket (ws://) + JWT Auth
                              │ Binary H.264 + JSON Control
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│                     ScreenAI Server (Secure)                         │
│              (Spring WebFlux + Netty + JWT Auth)                    │
│                                                                      │
│  ws://localhost:8080/screenshare                                    │
│                                                                      │
│  • JWT Authentication Required                                       │
│  • Room Password Protection                                          │
│  • Access Code Generation                                            │
│  • Rate Limiting & IP Blocking                                       │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 🏗️ Project Structure

```
src/main/java/
├── App.java                          # Application entry (selects mode)
├── ScreenAIClientApplication.java    # Spring Boot + JavaFX launcher
├── config/
│   └── EnvConfig.java                # Environment configuration loader
├── controller/
│   ├── DualModeMainController.java   # Main UI controller (dual mode)
│   ├── DualModeController.java       # Business logic for dual mode
│   ├── HostController.java           # Host streaming logic
│   ├── ViewerController.java         # Viewer streaming logic
│   ├── LoginDialog.java              # Login/Register popup
│   ├── RoomPasswordDialog.java       # Room security dialog
│   └── MainController.java           # Legacy main controller
├── encoder/
│   ├── VideoEncoderFactory.java      # Encoder selection strategy
│   ├── VideoEncoderStrategy.java     # Encoder interface
│   ├── H264VideoToolboxEncoder.java  # macOS GPU encoder
│   ├── NvencEncoder.java             # NVIDIA GPU encoder
│   └── LibX264Encoder.java           # CPU fallback encoder
├── model/
│   ├── ScreenSource.java             # Screen capture source
│   └── PerformanceMetrics.java       # Streaming metrics
└── service/
    ├── AuthenticationService.java    # JWT auth client
    ├── TokenStorageService.java      # Encrypted token storage
    ├── ServerConnectionService.java  # WebSocket client
    ├── ScreenCaptureService.java     # Screen capture
    ├── H264DecoderService.java       # Video decoder
    ├── FrameBufferService.java       # Frame buffering
    ├── ScreenSourceDetector.java     # Display detection
    └── PerformanceMonitorService.java

src/main/resources/
├── application.yml                   # Spring configuration
└── ui/
    ├── dual-mode.fxml                # Dual mode UI (with access code section)
    ├── main.fxml                     # Legacy UI
    └── styles.css                    # UI styling
```

---

## ⚙️ Configuration

### Environment Variables (.env)

```env
# Server URLs
SERVER_URL=ws://localhost:8080/screenshare
HTTP_URL=http://localhost:8080

# Security Settings
TOKEN_ENCRYPTION_KEY=your-32-char-key-for-aes-256!!
CREDENTIALS_STORAGE_DIR=~/.screenai

# Optional: Debug
DEBUG_FFMPEG=false
```

### application.yml

```yaml
screenai:
  server:
    websocket-url: ${SERVER_URL:ws://localhost:8080/screenshare}
    http-url: ${HTTP_URL:http://localhost:8080}
  
  security:
    encryption-key: ${TOKEN_ENCRYPTION_KEY}
    storage-dir: ${CREDENTIALS_STORAGE_DIR:~/.screenai}
```

---

## 🖥️ Hardware-Accelerated Encoding

| Platform | Hardware Encoder | CPU Reduction | Fallback |
|----------|-----------------|---------------|----------|
| **macOS** | VideoToolbox (GPU) | ~70% | libx264 |
| **Windows** | NVENC (NVIDIA GPU) | ~80% | libx264 |
| **Linux** | NVENC (NVIDIA GPU) | ~80% | libx264 |

---

## 🔧 Troubleshooting

### Login Dialog Too Small
The dialog should now auto-size correctly. If not, drag to resize.

### "Connection Failed" Error
1. Ensure server is running: `./mvnw spring-boot:run` in server directory
2. Check server URL in `.env` matches server address
3. Verify firewall allows port 8080

### "Authentication Required" Message
1. You must login before connecting
2. Click Connect → Login dialog appears
3. Register a new account or login with existing credentials

### Access Code Not Showing
Access codes only appear for **password-protected rooms**:
1. Create a room with a password
2. Server returns `accessCode` in response
3. Code displays in "Your Room" section

### Token Expired
- Access tokens expire after 15 minutes
- App automatically refreshes using refresh token
- If refresh fails, login dialog appears

---

## 📄 License

MIT License - See [LICENSE](LICENSE) file
