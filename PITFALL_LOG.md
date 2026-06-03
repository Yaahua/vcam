# VCAM 视频切换 & 禁用即时生效修复踩坑记录

## 涉及提交
- `5c2bae5` fix: 通知栏切换和UI选择在绝对路径下的匹配bug
- `4762232` fix: 主界面修改配置后即时通知目标应用刷新
- `ffbd92e` fix: 禁用模块时立即停止所有播放器/解码器

---

## 踩坑1: 绝对路径 vs 相对文件名匹配不一致

**现象**: 从通知栏点上下条切换视频，UI主界面管理页选择视频，都不生效；选择后选中状态也不显示。

**根因**: 
- `MediaViewModel.selectVideo()` 用 `current == name` 做精确匹配
- `ControlActionHelper.findAllVideos()` 用绝对路径存储 `selectedVideo`
- 当用户在 VCAM 管理页通过绝对路径选了外部视频后，`KEY_SELECTED_VIDEO` 存的是 `/sdcard/Download/xxx.mp4`
- 后续用相对文件名 `selectVideo("virtual.mp4")` 与绝对路径字符串比对永远 `false`
- 同理，`isCurrentVideo()` / `findVideoIndex()` 也只用文件名匹配

**修复**:
- `MediaViewModel.kt`: `selectVideo()` 中支持绝对路径与相对路径的兼容比较
- `VideoProvider.java`: `isCurrentVideo()` 和 `findVideoIndex()` 按 `File.name` 匹配
- `ManageScreen.kt`: 外部选中视频加 "(外部)" 标识

---

## 踩坑2: ViewModel 的 ConfigManager 没有 Context → 改配置后目标应用收不到通知

**现象**: 在 VCAM 主界面切换视频、搞禁用开关，目标拍摄应用没有任何反应。

**根因**: 
- `MediaViewModel` 和 `MainViewModel` 创建 `ConfigManager()` 时没传 `Context`
- `ConfigManager.save()` 中有通知代码：
  ```java
  if (context != null) {
      context.getContentResolver().notifyChange(IpcContract.URI_CONFIG, null);
      sendConfigBroadcast(context);
  }
  ```
- context 为 null → 整个通知块跳过
- 只有 `VideoProvider.call()`（通知栏上下条）会主动调 `notifyChange`
- 从 UI 直接改配置时无人通知目标进程

**修复**:
```kotlin
// 改前
private val configManager = ConfigManager()

// 改后
private val configManager = ConfigManager().apply { setContext(application) }
```

---

## 踩坑3: 禁用模块不会立刻停掉已有播放器

**现象**: 点击「禁用模块」开关后，视频画面没有立刻消失，仍在替换。

**根因链**:
1. 禁用开关 → `setBoolean(KEY_DISABLE_MODULE, true)` → 写入 config
2. `ConfigWatcher.ContentObserver.onChange()` 触发 → `onMediaSourceChanged()`
3. `onMediaSourceChanged()` **无条件**调用 `Camera1Handler.reloadVideo()` + `Camera2SessionHook.reloadVideo()`
4. `reloadVideo()` **不检查** `HookGuards.isDisabled()`，直接 `getVideoFile()` → 重启 MediaPlayer
5. 结果：禁用了模块，播放器照样继续跑
6. 只有**下次相机操作**（startPreview/openCamera 等）时，hook 回调里检查 `isDisabled()` 才会 return —— 但播放器已经启动且不会自己停下

**修复**: 三层防御

| 层 | 位置 | 动作 |
|---|---|---|
| 1 | `onMediaSourceChanged()` | 先调 `HookGuards.isDisabled()`，true 则调 `stopAllPlayers()` 并 return |
| 2 | `Camera1Handler.reloadVideo()` | 入口检查 `isDisabled()` → `stopAllPlayers()` → return |
| 3 | `Camera2SessionHook.reloadVideo()` | 同上 |

**stopAllPlayers()**: 停止并释放所有 MediaPlayer 实例 + 解码器

---

## 踩坑4: ConfigWatcher 的通知链路有多个冗余通道但都用不上

**ConfigWatcher 有三条通道**:
1. **ContentObserver** 监听 `URI_CONFIG` → 最快，`notifyChange` 触发即回调
2. **FileObserver** 监听 `.json` 文件变动 → 降级方案，有200ms延迟
3. **BroadcastReceiver** 监听 `ACTION_UPDATE_CONFIG` → 绕过 Android 限制较少

**但踩坑2之前的实际情况**:
- 通道1: 没通知（context=null，save 不调 notifyChange）
- 通道2: ContentObserver 注册成功 → FileObserver 不启动（只在 ContentObserver 失败时才 fallback）
- 通道3: 广播没发（同样 context=null）

**结论**: 三条通道全废。踩坑2修复后，通道1和3同时恢复。

---

## 关键文件清单

| 文件 | 改动 |
|---|---|
| `MediaViewModel.kt` | Context注入 + 绝对路径匹配 |
| `MainViewModel.kt` | Context注入 |
| `ManageScreen.kt` | 外部视频标识 |
| `ControlActionHelper.java` | 视频索引匹配改为 File.name |
| `VideoProvider.java` | isCurrentVideo/findVideoIndex 匹配 |
| `HookMain.java` | onMediaSourceChanged 加 disable 检查 |
| `Camera1Handler.java` | reloadVideo + stopAllPlayers |
| `Camera2SessionHook.java` | reloadVideo + stopAllPlayers |
