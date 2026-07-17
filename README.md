# HDC Idea

DevEco Studio 插件 —— 将常用的 HDC 命令封装为 IDE 工具栏按钮，无需手动敲命令即可完成日常鸿蒙开发操作。

灵感来自 Android Studio 的 [ADB Idea](https://plugins.jetbrains.com/plugin/7380-adb-idea) 插件。

---

## 安装方法

1. 下载 `build/distributions/hdc-idea-1.0.0.zip`
2. 打开 **DevEco Studio**
3. 菜单 **File → Settings → Plugins**
4. 点击齿轮图标 ⚙️ → **Install Plugin from Disk...**
5. 选择下载的 zip 文件
6. 重启 IDE

---

## 功能列表

| 功能 | 说明 | HDC 命令 |
|---|---|---|
| 启动应用 | 启动指定 Ability | `hdc shell aa start -b <bundle> -a <ability>` |
| 杀进程 | 强制停止应用 | `hdc shell aa force-stop <bundle>` |
| 重启应用 | 先杀后启 | `aa force-stop` + `aa start` |
| 卸载应用 | 从设备移除 | `hdc uninstall <bundle>` |
| 清除数据 | 清除缓存+数据 | `hdc shell bm clean -n <bundle> -c -d` |
| 清除数据并重启 | 清数据后启动 | `bm clean` + `aa start` |
| 授予权限 | 授予运行时权限 | `hdc shell bm grant-permission`（新 API 支持） |
| 撤销权限 | 撤销运行时权限 | `hdc shell bm revoke-permission`（新 API 支持） |

---

## 使用方式

### 快捷键（推荐）
**Shift + Ctrl + Alt + A** —— 弹出操作列表，支持快速搜索。

### 菜单
顶部菜单栏 **工具 → HDC Idea** —— 下拉菜单选择操作。

### 工具栏
运行工具栏右侧的 **HDC Idea** 图标。

---

## 首次使用

### 包名自动检测
插件会自动从工程配置中读取包名：
- **bundleName**：从 `AppScope/app.json5` → `app.bundleName` 读取
- **abilityName**：从 `<module>/src/main/module.json5` → `abilities[0].name` 读取

如果自动检测失败，会弹出输入框让你手动输入，输入后自动记住下次用。

### hdc 路径
插件会自动在以下位置查找 hdc：
1. 用户配置的路径（Settings → Tools → HDC Idea）
2. DevEco Studio SDK 目录：`<安装目录>/sdk/default/openharmony/toolchains/hdc.exe`
3. 系统 PATH

如果找不到，请在设置页手动配置。

### 设备连接
确保设备已通过 USB 或 Wi-Fi 连接，且开启了 USB 调试模式。可用 `hdc list targets` 验证。

---

## 鸿蒙 HDC 命令与 Android ADB 对照

| 操作 | Android (ADB) | 鸿蒙 (HDC) |
|---|---|---|
| 启动应用 | `adb shell am start -n pkg/activity` | `hdc shell aa start -b bundle -a ability` |
| 停止应用 | `adb shell am force-stop pkg` | `hdc shell aa force-stop bundle` |
| 安装应用 | `adb install app.apk` | `hdc install app.hap` |
| 卸载应用 | `adb uninstall pkg` | `hdc uninstall bundle` |
| 清除数据 | `adb shell pm clear pkg` | `hdc shell bm clean -n bundle -c -d` |
| 列出设备 | `adb devices` | `hdc list targets` |
| 查看日志 | `adb logcat` | `hdc hilog` |

**关键区别：**
- 鸿蒙用 `aa`（App Aid）替代 Android 的 `am`（Activity Manager）
- 鸿蒙用 `bm`（Bundle Manager）替代 Android 的 `pm`（Package Manager）
- 鸿蒙的 `hdc start` 是启动 hDC 服务本身，**不是**启动应用！

---

## 项目结构

```
hdc-idea/
├── build.gradle                          # Gradle 构建配置
├── README.md                             # 本文件
└── src/main/
    ├── resources/
    │   ├── META-INF/plugin.xml           # 插件描述符（action 注册、分组）
    │   └── icons/hdc.svg                 # 工具栏图标
    └── java/com/deveco/hdcidea/
        ├── HdcCommandService.java        # 核心：hdc 路径发现 + 命令执行
        ├── HdcCommandResult.java         # 命令结果封装
        ├── HdcSettingsState.java         # 持久化设置（hdc 路径、包名）
        ├── HdcPathConfigurable.java      # 设置页（Settings → Tools → HDC Idea）
        ├── DeviceChooserDialog.java      # 多设备选择弹窗
        ├── ProjectDetector.java          # 从工程配置解析包名/Ability
        ├── HdcNotification.java          # 通知工具
        └── actions/
            ├── HdcAction.java            # 抽象基类（统一流程）
            ├── HdcStartAction.java       # 启动
            ├── HdcKillAction.java        # 杀进程
            ├── HdcRestartAction.java     # 重启
            ├── HdcUninstallAction.java   # 卸载
            ├── HdcClearDataAction.java   # 清数据
            ├── HdcClearDataAndRestartAction.java
            ├── HdcGrantPermissionAction.java
            └── HdcRevokePermissionAction.java
```

---

## 构建

```bash
# 设置 JAVA_HOME 指向 DevEco Studio 的 JDK（仅当前终端有效）
JAVA_HOME="C:/Program Files/Huawei/DevEco Studio/jbr" ./gradlew.bat buildPlugin

# 产物在 build/distributions/hdc-idea-1.0.0.zip
```

---

## 注意事项

- 授予/撤销权限功能需要较新的鸿蒙 API 支持，当前设备可能不可用
- 通知中会显示设备返回的实际输出，如果操作无效请查看通知详情
- 杀进程优先使用 `aa force-stop`，失败时退到 `kill -9 <PID>`
