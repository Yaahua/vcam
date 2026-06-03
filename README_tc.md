# VCAM — 安卓虛擬攝影機

[简体中文](./README.md) | [繁體中文](./README_tc.md) | [English](./README_en.md)

基於 Xposed / LSPosed / LSPatch 的虛擬攝影機模組，可取代任意應用的相機畫面和麥克風輸入。

> ⚠️ **請勿用於任何非法用途，所有後果自負。**

---

## 特性

- 🎥 **影片取代** — 用本機影片/網路串流取代攝影機預覽和拍照
- 🎤 **麥克風控制** — 靜音、音訊取代、影片原聲三種模式
- 📸 **拍照取代** — 攔截拍照請求，注入自訂圖片
- 🔀 **隨機播放** — 從影片列表中隨機選擇播放
- 🔔 **通知欄控制** — 下拉通知欄快速切換影片
- 🌐 **網路串流支援** — 支援 RTSP/RTMP/HLS 等串流媒體源
- 📱 **Jetpack Compose 介面** — Material3 現代化 UI
- 🔧 **LSPatch 相容** — 免 Root 注入，無需 Xposed 框架

---

## 支援平台

| 框架 | 方式 | 需要 Root |
|------|------|-----------|
| LSPatch | APK 免 Root 注入 | ❌ 否 |
| LSPosed | Xposed 模組載入 | ✅ 是 |
| EdXposed | Xposed 模組載入 | ✅ 是 |
| 傳統 Xposed | Xposed 模組載入 | ✅ 是 |

- Android 5.0+
- 支援 Camera1 和 Camera2 API
- 支援 H.264/H.265 硬體解碼

---

## 快速開始

### 安裝

1. **LSPatch（免 Root 推薦）**：在 LSPatch Manager 中載入本模組 APK，選擇目標應用，注入後生效
2. **LSPosed**：在 LSPosed 管理器的作用域中勾選目標應用，重啟目標應用
3. **傳統 Xposed**：在 Xposed Installer 中啟用模組，重啟手機

### 使用

1. 授予目標應用「讀取本機儲存」許可權，強制停止目標應用
2. 打開目標應用，進入相機預覽，會彈出解析度提示（如「寬：1920 高：1080」）
3. 根據提示解析度準備取代影片，命名為 `virtual.mp4`，放入 `Camera1` 目錄：
   - 有儲存許可權：`/內部儲存/DCIM/Camera1/virtual.mp4`
   - 無儲存許可權：`/內部儲存/Android/data/[應用包名]/files/Camera1/virtual.mp4`
4. 重新打開目標應用，相機畫面已被取代

### 控制檔案（全域即時生效）

| 檔案名 | 作用 | 路徑 |
|--------|------|------|
| `no-silent.jpg` | 播放影片聲音 | `DCIM/Camera1/` |
| `disable.jpg` | 臨時禁用取代 | `DCIM/Camera1/` |
| `no_toast.jpg` | 關閉氣泡提示 | `DCIM/Camera1/` |
| `force_show.jpg` | 強制顯示目錄提示 | `DCIM/Camera1/` |
| `private_dir.jpg` | 強制使用私有目錄 | `DCIM/Camera1/` |

> 💡 上述開關也可在 VCAM 管理 App 中透過介面直接設定，無需手動建立檔案。

---

## App 介面

VCAM 管理 App 基於 Jetpack Compose Material3 構建，提供三頁式佈局：

- **首頁** — 模組狀態、播放模式、麥克風模式、影片聲音、通知狀態一目瞭然
- **管理** — 影片/音訊檔案管理，支援匯入、選擇、刪除
- **設定** — 一般設定（隨機播放、聲音、麥克風 Hook 等）、進階設定（強制私有目錄、 禁用 Toast 等）、網路串流設定

---

## 常見問題

### 前置攝影機方向不對？
取代前置攝影機的影片通常需要水平翻轉 + 右旋 90°，處理後解析度需與提示一致。

### 黑畫面/啟動失敗？
- 確認影片路徑正確（只需一級 `Camera1` 目錄）
- 部分系統相機無法 Hook
- 檢查影片編碼格式是否支援

### 畫面花屏/扭曲？
影片解析度與提示解析度不匹配，請調整影片後重試。

### LSPatch 下管理 App 顯示「模組未啟用」？
v5.0+ 已修復。管理 App 和目標 App 執行在不同程序，管理 App 無需 Xposed 環境。

---

## 開發

```bash
# 克隆
git clone https://github.com/Yaahua/vcam.git

# 構建要求
# JDK 17+, Gradle 8.4, AGP 8.2.2

# 構建
./gradlew assembleRelease
```

詳細開發日誌和踩坑記錄見 [docs/DEVELOPMENT_LOG.md](./docs/DEVELOPMENT_LOG.md)

### 技術棧

| 組件 | 版本 |
|------|------|
| Gradle | 8.4 |
| AGP | 8.2.2 |
| Kotlin | 1.9.22 |
| Jetpack Compose BOM | 2024.04.00 |
| Xposed API | 82 (compileOnly) |

---

## 致謝

- Hook 思路：[CameraHook](https://github.com/wangwei1237/CameraHook)
- H.264 硬解碼：[Android-VideoToImages](https://github.com/zhantong/Android-VideoToImages)
- JPEG-YUV 轉換：[CSDN Blog](https://blog.csdn.net/jacke121/article/details/73888732)

---

## License

本項目僅供學習研究使用。使用者需遵守當地法律法規，作者不承擔任何因使用本項目產生的法律責任。