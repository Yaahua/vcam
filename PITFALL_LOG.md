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

---

## 踩坑5: MicrophoneHandler 异常穿透导致 ConfigWatcher 完全不初始化

**现象**: 修复踩坑2/3/4 后，ConfigWatcher 仍然没有任何日志输出，热切换完全失效。

**根因链**:
1. `HookMain.handleLoadPackage()` 中，`MicrophoneHandler.init(lpparam)` 在第130行
2. `initConfigWatcher(lpparam)` 在第133行
3. QQ 新版/某些 ROM 中 `AudioRecord.read(byte[],int)` 方法签名变更 → `NoSuchMethodError`
4. `NoSuchMethodError` 继承自 `Error`，**不是 `Exception` 的子类**
5. `MicrophoneHandler.java` 里 `catch (Exception e)` **捕不住 Error**
6. 异常穿透到 `handleLoadPackage()`，而第130行**没有外层 try-catch**
7. → 方法提前退出 → 第133行 `initConfigWatcher` **被跳过** → 热切换完全失效

**Java 异常继承链**:
```
Throwable
├── Exception      ← catch (Exception) 能捕住
│   └── RuntimeException
│       └── ... 各种运行时异常
└── Error          ← catch (Exception) 捕不住！
    └── NoSuchMethodError  ← 方法签名不匹配时抛出
```

**修复** (v5.4):
| 文件 | 改动 |
|------|------|
| `HookMain.java` | `MicrophoneHandler.init()` 外层加 `try/catch(Throwable)`，失败不影响 ConfigWatcher |
| `MicrophoneHandler.java` | 2 处 `catch(Exception)` → `catch(Throwable)`，防止 Error 穿透 |

**教训**: 
- Xposed Hook 代码中**永远用 `catch (Throwable)` 而不是 `catch (Exception)`**——目标 App 的任何方法签名变更都可能抛 `NoSuchMethodError`/`NoClassDefFoundError`
- Hook 入口函数中，子模块初始化必须各自包裹 try-catch，不能让一个模块的失败拖垮整个 Hook

**验证方式**:
修复后打开 QQ，logcat 过滤 `LSPosed-Bridge.*【VCAM】`：
- ✅ 看到 `【VCAM】【CS】初始化配置监听` → ConfigWatcher 正常工作
- ✅ 可能看到 `【VCAM】Microphone AudioRecord Hook 失败: NoSuchMethodError` → 不影响热切换

---

## 踩坑6: GitHub Actions 构建产物下载总是旧版本

**现象**: 从 GitHub Actions 页面下载 APK 安装后，代码是旧的，热切换不生效。但在终端用 API 下载安装就是新的。

**根因**:

1. **认证墙**: GitHub Actions artifacts **不是公开的**。未登录 GitHub 时访问 Actions 页面会看到 "Sign in to view logs"，artifact 下载链接也会被拦截。浏览器退回到某个可访问的旧页面/缓存。

2. **命名混淆**: 所有 CI 构建产物的文件名都是 `app-release.apk`，仅靠文件名无法区分是哪个提交构建的。下载后覆盖到手机，新旧不分。

3. **构建列表排序**: Actions 页面按提交时间倒序排列，最新构建在最上面。但如果在 CI 构建完成前就打开页面（或页面缓存），看到的可能是旧构建。

4. **Release ≠ Artifact**: GitHub 有两种发布方式：
   - **Release**: 手动创建，上传 APK。本项目从未创建过 Release（页面显示 "There aren't any releases here"）
   - **Actions Artifact**: CI 自动构建产物，每次 push 都会生成，但需要登录 GitHub 且知道在哪个 build 里找

**解决**:
| 方式 | 可靠性 | 说明 |
|------|--------|------|
| 浏览器打开 Actions | ❌ 低 | 需要登录、容易选错构建、文件名相同 |
| API + token 下载 | ✅ 高 | 精确指定 artifact ID，对应确切 commit |
| 创建 Release | ✅ 最高 | 手动发布命名版本，公开可访问，有版本号 |

**建议**: 每次稳定版本手动创建 GitHub Release，附上 APK 和 changelog，避免混淆。

---

## 踩坑7: Git Push 超时——终端会话污染

**现象**: 在 `super_admin:terminal` 中执行 `git push` 经常超时（30秒无响应），但实际 push 已经成功。

**根因**:
- `super_admin:terminal` 使用**共享终端会话**（默认 session `super_admin_default_session`）
- 前面的命令（如 `./gradlew assembleDebug`）如果在超时后继续在后台运行，会话被阻塞
- 新的 git 命令在同一个会话中排队，等到超时都没执行
- 多次重复提交导致本地领先远程 N 个 commit

**解决**:
| 方式 | 说明 |
|------|------|
| 用 `github:terminal_exec` | 独立会话，不受前面命令影响 |
| 每次传不同 `session_name` | 隔离会话 |
| `terminal_wait` | 等前一个命令完成再执行 |

**教训**: 编译和 git 操作不要共用同一个终端会话。编译耗时不定，超时后的残留进程会阻塞后续所有命令。<｜end▁of▁thinking｜>现在更新 DEVELOPMENT_LOG.md：

<｜｜DSML｜｜tool_calls>
<｜｜DSML｜｜invoke name="edit_file">
<｜｜DSML｜｜parameter name="environment" string="true">linux
