# VCAM — 安卓虚拟摄像头

[简体中文](./README.md) | [繁體中文](./README_tc.md) | [English](./README_en.md)

基于 Xposed / LSPosed / LSPatch 的虚拟摄像头模块，可替换任意应用的相机画面和麦克风输入。

> ⚠️ **请勿用于任何非法用途，所有后果自负。**

---

## 特性

- 🎥 **视频替换** — 用本地视频/网络流替换摄像头预览和拍照
- 🎤 **麦克风控制** — 静音、音频替换、视频原声三种模式
- 📸 **拍照替换** — 拦截拍照请求，注入自定义图片
- 🔀 **随机播放** — 从视频列表中随机选择播放
- 🔔 **通知栏控制** — 下拉通知栏快速切换视频
- 🌐 **网络流支持** — 支持 RTSP/RTMP/HLS 等流媒体源
- 📱 **Jetpack Compose 界面** — Material3 现代化 UI
- 🔧 **LSPatch 兼容** — 免 Root 注入，无需 Xposed 框架

---

## 支持平台

| 框架 | 方式 | 需要 Root |
|------|------|-----------|
| LSPatch | APK 免 Root 注入 | ❌ 否 |
| LSPosed | Xposed 模块加载 | ✅ 是 |
| EdXposed | Xposed 模块加载 | ✅ 是 |
| 传统 Xposed | Xposed 模块加载 | ✅ 是 |

- Android 5.0+
- 支持 Camera1 和 Camera2 API
- 支持 H.264/H.265 硬件解码

---

## 快速开始

### 安装

1. **LSPatch（免 Root 推荐）**：在 LSPatch Manager 中加载本模块 APK，选择目标应用，注入后生效
2. **LSPosed**：在 LSPosed 管理器的作用域中勾选目标应用，重启目标应用
3. **传统 Xposed**：在 Xposed Installer 中启用模块，重启手机

### 使用

1. 授予目标应用「读取本地存储」权限，强制停止目标应用
2. 打开目标应用，进入相机预览，会弹出分辨率提示（如「宽：1920 高：1080」）
3. 根据提示分辨率准备替换视频，命名为 `virtual.mp4`，放入 `Camera1` 目录：
   - 有存储权限：`/内部存储/DCIM/Camera1/virtual.mp4`
   - 无存储权限：`/内部存储/Android/data/[应用包名]/files/Camera1/virtual.mp4`
4. 重新打开目标应用，相机画面已被替换

### 控制文件（全局实时生效）

| 文件名 | 作用 | 路径 |
|--------|------|------|
| `no-silent.jpg` | 播放视频声音 | `DCIM/Camera1/` |
| `disable.jpg` | 临时禁用替换 | `DCIM/Camera1/` |
| `no_toast.jpg` | 关闭气泡提示 | `DCIM/Camera1/` |
| `force_show.jpg` | 强制显示目录提示 | `DCIM/Camera1/` |
| `private_dir.jpg` | 强制使用私有目录 | `DCIM/Camera1/` |

> 💡 上述开关也可在 VCAM 管理 App 中通过界面直接配置，无需手动创建文件。

---

## App 界面

VCAM 管理 App 基于 Jetpack Compose Material3 构建，提供三页式布局：

- **首页** — 模块状态、播放模式、麦克风模式、视频声音、通知状态一目了然
- **管理** — 视频/音频文件管理，支持导入、选择、删除
- **设置** — 常规设置（随机播放、声音、麦克风 Hook 等）、高级设置（强制私有目录、禁用 Toast 等）、网络流设置

---

## 常见问题

### 前置摄像头方向不对？
替换前置摄像头的视频通常需要水平翻转 + 右旋 90°，处理后分辨率需与提示一致。

### 黑屏/启动失败？
- 确认视频路径正确（只需一级 `Camera1` 目录）
- 部分系统相机无法 Hook
- 检查视频编码格式是否支持

### 画面花屏/扭曲？
视频分辨率与提示分辨率不匹配，请调整视频后重试。

### LSPatch 下管理 App 显示"模块未激活"？
v5.0+ 已修复。管理 App 和目标 App 运行在不同进程，管理 App 无需 Xposed 环境。

---

## 开发

```bash
# 克隆
git clone https://github.com/Yaahua/vcam.git

# 构建要求
# JDK 17+, Gradle 8.4, AGP 8.2.2

# 构建
./gradlew assembleRelease
```

详细开发日志和踩坑记录见 [docs/DEVELOPMENT_LOG.md](./docs/DEVELOPMENT_LOG.md)

### 技术栈

| 组件 | 版本 |
|------|------|
| Gradle | 8.4 |
| AGP | 8.2.2 |
| Kotlin | 1.9.22 |
| Jetpack Compose BOM | 2024.04.00 |
| Xposed API | 82 (compileOnly) |

---

## 致谢

- Hook 思路：[CameraHook](https://github.com/wangwei1237/CameraHook)
- H.264 硬解码：[Android-VideoToImages](https://github.com/zhantong/Android-VideoToImages)
- JPEG-YUV 转换：[CSDN Blog](https://blog.csdn.net/jacke121/article/details/73888732)

---

## License

本项目仅供学习研究使用。使用者需遵守当地法律法规，作者不承担任何因使用本项目产生的法律责任。