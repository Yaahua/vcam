# VCAM 开发日志与经验总结

> 记录从 v5.0 原始代码到可构建、可运行版本的完整修复历程。

---

## 一、构建环境升级

### 背景
原项目 Gradle/AGP/Kotlin 版本陈旧，与现代 Android Studio 和 SDK 不兼容。

### 历程

| 步骤 | 变更 | 说明 |
|------|------|------|
| 1 | JDK 11 → 17 | Android SDK cmdline-tools 16.0 要求 JDK 17+ |
| 2 | AGP 7.4.2 / Kotlin 1.8.22 / Compose BOM 2023.05.00 | 首次可构建组合 |
| 3 | AGP 8.2.0 / Gradle 8.2 / Kotlin 1.9.22 / BOM 2023.10.01 | 跟进升级 |
| 4 | AGP 8.2.2 / Gradle 8.4 / BOM 2024.04.00 | 最终版本 |

### 经验教训
- **AGP/Kotlin/Compose BOM 版本耦合极强**，升级必须三者联动
- Compose BOM 版本决定了可用 API 集合，M3 1.2+ 的 `cardElevation()` 在旧 BOM 中不存在，需回退为 `CardDefaults.elevation()`
- Kotlin 1.9.22 配合 AGP 8.2.x 是当前稳定组合

---

## 二、代码兼容性修复

### 2.1 Material3 API 差异

**问题**：`Unresolved reference: cardElevation` / `HorizontalDivider`

**根因**：Compose BOM 版本决定了 Material3 版本。旧版 M3 使用 `CardDefaults.elevation()` 和 `Divider()`，新版才引入 `CardDefaults.cardElevation()` 和 `HorizontalDivider()`。

**修复**：回退为 1.1.x 兼容 API：
```kotlin
// ❌ M3 1.2+
CardDefaults.cardElevation(defaultElevation = 4.dp)
HorizontalDivider(...)

// ✅ M3 1.1.x
CardDefaults.elevation(defaultElevation = 4.dp)
Divider(...)
```

### 2.2 @OptIn 注解位置

**问题**：`@file:OptIn` 必须在 package 声明之前，否则编译失败。

**修复**：将 `@file:OptIn(...)` 移到 `package com.yaahua.vcam` 之前。

### 2.3 BuildConfig 生成

**问题**：`Unresolved reference: BuildConfig`

**根因**：AGP 8.x 默认关闭 BuildConfig 生成。

**修复**：`app/build.gradle` 中添加：
```groovy
android {
    buildFeatures {
        buildConfig = true
    }
}
```

### 2.4 AndroidManifest package 属性

**问题**：`package` 属性与 `build.gradle` 中的 `namespace` 冲突。

**根因**：AGP 7.x+ 使用 `namespace` 在 `build.gradle` 中定义包名，`AndroidManifest.xml` 中不应再有 `package`。

**修复**：从 `AndroidManifest.xml` 删除 `package` 属性。

### 2.5 NotificationService 缺失 import

**问题**：`NotificationService.java` 中 `MainActivity.class` 找不到。

**修复**：添加 `import com.yaahua.vcam.ui.MainActivity;`

### 2.6 ExtraTranslation Lint 错误

**问题**：Release 构建时 Lint 报 `ExtraTranslation` fatal error。

**根因**：6 个字符串 (`click_to_go_to_repo`, `switch1`~`switch5`) 在 7 个翻译文件中存在，但默认 `values/strings.xml` 中没有对应条目，且代码中已无引用。

**修复**：从 7 个翻译文件中删除这些死字符串：
- `values-zh/strings.xml`
- `values-zh-rCN/strings.xml`
- `values-zh-rTW/strings.xml`
- `values-zh-rHK/strings.xml`
- `values-zh-rMO/strings.xml`
- `values-zh-rSG/strings.xml`
- `values-en/strings.xml`

**经验**：多语言翻译条目要么在默认文件中定义，要么全部删除；Lint 在 release 构建中将其视为错误。

### 2.7 Xposed 检测阻断 UI（关键修复）

**问题**：App 首页始终显示红色"模块未激活"，且隐藏所有功能状态。

**根因**：`MainViewModel.checkXposed()` 通过 `Class.forName("de.robv.android.xposed.XposedBridge")` 检测 Xposed 环境。在非 Xposed 环境（包括 LSPatch 管理 App 自身进程）中永远返回 `false`，导致 `HomeScreen` 显示错误状态。

**架构认知**：
```
VCAM 管理 App（自身进程）
  ├── 无 XposedBridge → checkXposed() 失败 → UI 被阻断
  └── 仅负责配置管理，不需要 Hook 环境

目标 App（被 LSPatch/Xposed 注入）
  ├── 有 XposedBridge → HookMain 正常工作
  └── 实际摄像头/麦克风 Hook 在此进程生效
```

**修复**：
1. `MainUiState.isXposedActive` 默认值改为 `true`
2. 删除 `checkXposed()` 方法（9 行）
3. `init` 中移除 `checkXposed()` 调用

**经验**：Xposed 模块的管理 UI 和目标 Hook 运行在不同进程，不应在管理 UI 中检测 Xposed 环境来阻断用户操作。LSPatch 用户尤其如此——管理 App 永远不会加载 XposedBridge，但 Hook 功能正常工作。

---

## 三、构建配置要点汇总

### 最终可用版本组合

| 组件 | 版本 |
|------|------|
| Gradle | 8.4 |
| AGP | 8.2.2 |
| Kotlin | 1.9.22 |
| Compose BOM | 2024.04.00 |
| compileSdk | 34 |
| minSdk | 21 |
| targetSdk | 34 |
| JDK | 17 |
| Xposed API | 82 (compileOnly) |

### build.gradle 关键配置

```groovy
android {
    namespace 'com.yaahua.vcam'
    compileSdk 34

    buildFeatures {
        compose = true
        buildConfig = true  // 必须显式开启
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    compileOnly 'de.robv.android.xposed:api:82'
    compileOnly 'de.robv.android.xposed:api:82:sources'
    // Compose BOM ...
}
```

### AndroidManifest 关键配置

```xml
<!-- 不需要 package 属性，namespace 在 build.gradle -->
<manifest xmlns:android="...">

    <application>
        <!-- Xposed 模块声明（LSPatch 也会读取） -->
        <meta-data android:name="xposedmodule" android:value="true" />
        <meta-data android:name="xposedminversion" android:value="51" />
        <meta-data android:name="xposeddescription" android:value="安卓虚拟摄像头" />
    </application>
</manifest>
```

### assets/xposed_init

```
com.yaahua.vcam.HookMain
```

此文件是 Xposed/LSPosed/LSPatch 识别 Hook 入口点的关键，缺失会导致模块完全无法加载。

---

## 四、项目架构

```
app/src/main/
├── assets/
│   └── xposed_init              # Xposed模块入口声明
├── java/com/yaahua/vcam/
│   ├── HookMain.java            # IXposedHookLoadPackage 实现（Hook入口）
│   ├── Camera1Handler.java      # Camera1 API Hook
│   ├── Camera2Handler.java      # Camera2 API Hook
│   ├── Camera2SessionHook.java  # Camera2 Session 拦截
│   ├── MicrophoneHandler.java   # 麦克风 Hook
│   ├── NativeAudioHook.java     # 原生音频 Hook
│   ├── HookGuards.java          # Hook 守卫/开关
│   ├── SharedState.java         # 共享状态
│   ├── ConfigManager.java       # 配置管理器（SharedPreferences）
│   ├── NotificationService.java # 通知栏控制服务
│   ├── LogUtil.java             # 日志工具（XposedLog + Logcat）
│   └── ui/
│       ├── MainActivity.java    # 主Activity（Compose宿主）
│       ├── MainViewModel.kt     # 主ViewModel（状态管理）
│       ├── HomeScreen.kt        # 首页 Compose UI
│       ├── SettingsScreen.kt    # 设置页 Compose UI
│       ├── ManageScreen.kt      # 管理页 Compose UI
│       └── MediaViewModel.kt    # 媒体管理 ViewModel
└── res/
    ├── values/strings.xml       # 默认字符串（必须有所有条目的默认值）
    ├── values-zh/               # 简体中文
    ├── values-zh-rCN/           # 简体中文（中国大陆）
    ├── values-zh-rTW/           # 繁体中文（台湾）
    ├── values-zh-rHK/           # 繁体中文（香港）
    ├── values-zh-rMO/           # 繁体中文（澳门）
    ├── values-zh-rSG/           # 简体中文（新加坡）
    └── values-en/               # 英文
```

---

## 五、常见踩坑清单

| # | 现象 | 根因 | 解决 |
|---|------|------|------|
| 1 | `Unresolved reference: BuildConfig` | AGP 8.x 默认关闭 | `buildFeatures.buildConfig = true` |
| 2 | `Unresolved reference: cardElevation` | Compose BOM 版本不匹配 | 回退为 `CardDefaults.elevation()` |
| 3 | `ExtraTranslation` lint error | 翻译文件有多余条目 | 删掉死字符串或补默认值 |
| 4 | 首页显示"模块未激活" | Xposed 检测阻断 | 删除 `checkXposed()`，默认 true |
| 5 | `package` 属性冲突 | AGP 7+ namespace 机制 | 删掉 manifest 中 package |
| 6 | 签名配置失效 | 相对路径问题 | 使用 `rootProject.file()` |
| 7 | JDK 版本不兼容 | cmdline-tools 要求 | JDK 17 |

---

## 六、LSPatch 适配要点

VCAM 作为标准 Xposed 模块，完全兼容 LSPatch：

1. **不需要修改 Hook 代码**：`IXposedHookLoadPackage` + `xposed_init` 是标准接口
2. **Xposed API 保持 compileOnly**：LSPatch 自带 Bridge，编译期不打包是正确的
3. **管理 App 自身不应检测 Xposed**：管理进程和 Hook 进程是分离的，见修复 2.7
4. **meta-data 必须保留**：`xposedmodule=true` 是 LSPatch 识别模块的标志

---

*最后更新：2025-01-23*
*维护者：Yaahua*
