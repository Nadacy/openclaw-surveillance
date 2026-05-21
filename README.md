# OpenClaw 智能监控模块

为 OpenClaw Android Node App 添加智能监控能力。

## 功能

- 📷 双摄像头管理（前后置独立）
- 📸 远程拍照（`camera.snap`）
- 🎥 远程录像+录音（`camera.clip`）
- 🧠 动态感知（帧差异 + ML Kit 人脸检测）
- 🤖 自动录像（有人→录，无人30秒→停）
- 🌑 假关屏（亮度0 + WakeLock，屏幕关相机不停）
- 📡 Gateway 命令集成

## 架构

```
SurveillanceService (前台服务)
├── CameraManager (双摄管理)
│   ├── MotionDetector (帧差异)
│   ├── FaceDetector (ML Kit 人脸)
│   └── VideoRecorder (录像+录音)
├── ScreenDimController (假关屏)
├── PresenceStateMachine (状态机)
└── CommandHandler (Gateway 协议)
```

## 命令

| 命令 | 参数 | 说明 |
|------|------|------|
| `surveillance.start` | rear, front (bool) | 启动监控 |
| `surveillance.stop` | — | 停止监控 |
| `surveillance.status` | — | 查看状态 |
| `surveillance.dim` | — | 假关屏 |
| `surveillance.undim` | — | 恢复亮度 |
| `camera.snap` | camera ("rear"/"front") | 拍照 |
| `camera.clip` | camera, duration, audio | 录像 |

## 构建

通过 GitHub Actions 自动编译 APK，下载地址见 Releases。
