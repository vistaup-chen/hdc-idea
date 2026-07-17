package com.deveco.hdcidea;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HDC 命令服务 —— 核心引擎
 *
 * 负责：
 * 1. 自动发现 hdc 可执行文件路径（用户配置 → DevEco SDK → 系统 PATH）
 * 2. 查询已连接的鸿蒙设备列表
 * 3. 向指定设备发送 HDC 命令并返回结果
 *
 * HDC（HarmonyOS Device Connector）是华为鸿蒙的命令行调试工具，类似 Android 的 ADB。
 * 参考：developer.huawei.com/consumer/cn/doc/harmonyos-guides/hdc
 *
 * 注意：鸿蒙的很多命令语法与 Android ADB 不同：
 * - 启动应用：hdc shell aa start -b <bundle> -a <ability>  （不是 am start）
 * - 停止应用：hdc shell aa force-stop <bundle>           （不是 am force-stop）
 * - 清除数据：hdc shell bm clean -n <bundle> -d            （bm 不是 pm）
 *
 * @see HdcCommandResult
 * @see HdcSettingsState
 */
public class HdcCommandService {

    private static final Logger LOG = Logger.getInstance(HdcCommandService.class);

    /** 单条命令执行超时时间（秒） */
    private static final long TIMEOUT_SECONDS = 30;

    /**
     * 解析 hdc 可执行文件路径，按以下优先级：
     * 1. 用户在 Settings > Tools > HDC Idea 中手动配置的路径
     * 2. DevEco Studio 自带的 SDK toolchain 目录
     *    实际路径形如：<DevEco安装目录>/sdk/default/openharmony/toolchains/hdc.exe
     *                                            ^^^^^^^^^^^^^^^^^^^^^^
     *                                            注意这一层！容易猜错
     * 3. 系统 PATH 环境变量中的 hdc
     *
     * @return hdc 的绝对路径，如果都找不到则返回 null
     */
    @Nullable
    public String resolveHdcPath() {
        // === 优先级 1：用户手动配置的路径 ===
        String configured = HdcSettingsState.getInstance().hdcPath;
        if (configured != null && !configured.isBlank()) {
            if (Files.isRegularFile(Paths.get(configured))) {
                return configured;
            }
            LOG.warn("用户配置的 hdc 路径不存在: " + configured);
        }

        // === 优先级 2：DevEco Studio SDK toolchain 目录 ===
        String devEcoHdc = findInDevEcoSdk();
        if (devEcoHdc != null) {
            return devEcoHdc;
        }

        // === 优先级 3：系统 PATH ===
        String pathHdc = findInSystemPath();
        if (pathHdc != null) {
            return pathHdc;
        }

        LOG.warn("未找到 hdc 可执行文件。请在 Settings > Tools > HDC Idea 中配置路径。");
        return null;
    }

    /**
     * 在 DevEco Studio 安装目录中搜索 hdc。
     *
     * hdc 实际存放路径经历过多次变化：
     * - 早期：<sdk>/toolchains/hdc.exe
     * - API 12+：<sdk>/default/openharmony/toolchains/hdc.exe  ← 当前版本
     *
     * 所以本函数同时尝试这两条路径。
     */
    @Nullable
    private String findInDevEcoSdk() {
        // hdc 可能的子目录路径（相对于 DevEco 安装根目录或 sdk 目录）
        String[][] candidateSets = {
                // 新路径：sdk/default/openharmony/toolchains/hdc[.exe]
                {
                    "sdk" + File.separator + "default" + File.separator + "openharmony"
                            + File.separator + "toolchains" + File.separator + "hdc",
                    "sdk" + File.separator + "default" + File.separator + "openharmony"
                            + File.separator + "toolchains" + File.separator + "hdc.exe",
                },
                // 老路径：sdk/toolchains/hdc[.exe]
                {
                    "sdk" + File.separator + "toolchains" + File.separator + "hdc",
                    "sdk" + File.separator + "toolchains" + File.separator + "hdc.exe",
                },
                // 另一种可能：tools/hdc/hdc[.exe]
                {
                    "tools" + File.separator + "hdc" + File.separator + "hdc",
                    "tools" + File.separator + "hdc" + File.separator + "hdc.exe",
                },
        };

        // DevEco Studio 常见的安装位置
        String userHome = System.getProperty("user.home");
        String[] baseDirs = {
                userHome + File.separator + "AppData" + File.separator + "Local"
                        + File.separator + "Huawei" + File.separator + "DevEco Studio",
                userHome + File.separator + "AppData" + File.separator + "Local"
                        + File.separator + "Programs" + File.separator + "DevEco Studio",
                "C:" + File.separator + "Program Files" + File.separator + "Huawei"
                        + File.separator + "DevEco Studio",
                "C:" + File.separator + "Program Files (x86)" + File.separator + "Huawei"
                        + File.separator + "DevEco Studio",
                userHome + File.separator + "DevEcoStudio",
        };

        for (String base : baseDirs) {
            Path basePath = Paths.get(base);
            if (!Files.isDirectory(basePath)) {
                continue;
            }
            // 直接拼路径尝试每种候选
            for (String[] candidates : candidateSets) {
                for (String candidate : candidates) {
                    Path resolved = basePath.resolve(candidate);
                    if (Files.isRegularFile(resolved)) {
                        return resolved.toAbsolutePath().toString();
                    }
                }
            }
            // 也搜索版本化的子目录（如 "DevEco Studio 6.0.0"）
            try {
                Path sdkPath = basePath.resolve("sdk");
                if (Files.isDirectory(sdkPath)) {
                    for (String[] candidates : candidateSets) {
                        for (String candidate : candidates) {
                            // 去掉开头的 "sdk/" 因为已经定位到 sdk 目录了
                            String relativeToSdk = candidate.replaceFirst("^sdk" + File.separator, "");
                            Path resolved = sdkPath.resolve(relativeToSdk);
                            if (Files.isRegularFile(resolved)) {
                                return resolved.toAbsolutePath().toString();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 忽略，继续下一个
            }
        }
        return null;
    }

    /**
     * 在系统 PATH 环境变量中查找 hdc 可执行文件。
     */
    @Nullable
    private String findInSystemPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) {
            return null;
        }
        // Windows 上是 hdc.exe，macOS/Linux 上是 hdc
        String exeName = System.getProperty("os.name").toLowerCase().contains("win") ? "hdc.exe" : "hdc";
        for (String dir : pathEnv.split(File.pathSeparator)) {
            Path candidate = Paths.get(dir, exeName);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return null;
    }

    /**
     * 查询所有已连接的鸿蒙设备。
     *
     * 执行命令：hdc list targets
     * 输出示例：
     *   
<longcat_arg_value>XXXX12345678
     *   192.168.1.100:5555
     *
     * @return 设备序列号列表；如果无设备或 hdc 不可用则返回空列表
     */
    @NotNull
    public List<String> listDevices() {
        String hdc = resolveHdcPath();
        if (hdc == null) {
            return Collections.emptyList();
        }
        // hdc list targets 不需要指定设备（它就是列出所有设备）
        HdcCommandResult result = executeRaw(hdc, "list", "targets");
        if (!result.isSuccess()) {
            LOG.warn("hdc list targets 执行失败: " + result.getStderr());
            return Collections.emptyList();
        }
        List<String> devices = new ArrayList<>();
        for (String line : result.getStdout().split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                // 每行是一个序列号，可能后跟空格+状态（如 "-1"）
                String serial = trimmed.split("\\s+")[0];
                if (!serial.isEmpty()) {
                    devices.add(serial);
                }
            }
        }
        return devices;
    }

    /**
     * 列出设备上已打开的 debuggable 应用的进程 PID。
     *
     * 执行命令：hdc -t <device> jpid
     * 注意：jpid 只能找到"使用 debug 签名 + 已启动"的应用进程。
     *       如果应用未启动或不是 debug 签名，返回可能为空。
     *
     * @param device 目标设备序列号
     * @return PID 字符串列表
     */
    @NotNull
    public List<String> listPids(@NotNull String device) {
        HdcCommandResult result = execute(device, "jpid");
        if (!result.isSuccess()) {
            return Collections.emptyList();
        }
        List<String> pids = new ArrayList<>();
        for (String line : result.getStdout().split("\\r?\\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.matches("\\d+")) {
                pids.add(trimmed);
            }
        }
        return pids;
    }

    /**
     * 通过 bundle 名在设备进程列表中查找 PID。
     * 比 jpid 更通用，能找到任何正在运行的进程（不限于 debug 签名）。
     *
     * 执行命令：hdc -t <device> shell ps -A
     * 然后在客户端侧过滤包含 bundleName 的行提取 PID。
     *
     * @param device     目标设备序列号
     * @param bundleName 应用包名（如 com.example.app）
     * @return 匹配的 PID 列表
     */
    @NotNull
    public List<String> findPidsByBundle(@NotNull String device, @NotNull String bundleName) {
        // ps -A 列出所有进程；输出格式：USER PID PPID VSIZE RSS WCHAN PC NAME
        HdcCommandResult result = execute(device, "shell", "ps", "-A");
        if (!result.isSuccess()) {
            result = execute(device, "shell", "ps");
        }
        List<String> pids = new ArrayList<>();
        for (String line : result.getStdout().split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.contains(bundleName)) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 2 && parts[1].matches("\\d+")) {
                    pids.add(parts[1]);
                }
            }
        }
        return pids;
    }

    // ========================================================================
    // 应用管理命令（使用 aa 工具）
    // ========================================================================

    /**
     * 启动指定应用的指定 Ability。
     *
     * 执行命令：hdc -t <device> shell aa start -b <bundleName> -a <abilityName>
     *
     * 如果 abilityName 为空，则不带 -a 参数，让设备按 module.json5 的
     * mainElement 自动解析入口 Ability。
     *
     * aa（App Aid / Application Assistant）是鸿蒙的应用管理工具，
     * 类似 Android 的 am（Activity Manager），但命令语法不同。
     *
     * @param device     目标设备序列号
     * @param bundleName 应用包名
     * @param abilityName Ability 名称（如 EntryAbility），可为空
     * @return 命令结果
     */
    @NotNull
    public HdcCommandResult startApp(@NotNull String device,
                                    @NotNull String bundleName,
                                    @NotNull String abilityName) {
        if (abilityName == null || abilityName.isBlank()) {
            return execute(device, "shell", "aa", "start", "-b", bundleName);
        }
        return execute(device, "shell", "aa", "start", "-b", bundleName, "-a", abilityName);
    }

    /**
     * 判断命令失败是否与 Ability 名称不正确有关。
     * 用于启动失败时决定是否弹出输入框让用户手输正确的 Ability。
     *
     * @param result 命令结果
     * @return true 表示错误看起来是 ability 相关
     */
    public static boolean isAbilityError(@NotNull HdcCommandResult result) {
        String err = result.getOutput().toLowerCase();
        if (!err.contains("ability")) {
            return false;
        }
        return err.contains("not found") || err.contains("no such")
                || err.contains("cannot find") || err.contains("does not exist")
                || err.contains("not exist") || err.contains("invalid");
    }

    /**
     * 强制停止指定应用。
     *
     * 执行命令：hdc -t <device> shell aa force-stop <bundleName>
     *
     * ⚠️ 注意：aa force-stop 后面直接跟包名，没有 -b 标志！
     *    这和 aa start 的写法不同（start 用 -b 和 -a）。
     *
     * @param device     目标设备序列号
     * @param bundleName 应用包名
     * @return 命令结果
     */
    @NotNull
    public HdcCommandResult stopApp(@NotNull String device, @NotNull String bundleName) {
        return execute(device, "shell", "aa", "force-stop", bundleName);
    }

    // ========================================================================
    // 应用管理命令（使用 bm 工具）
    // ========================================================================

    /**
     * 清除应用的缓存和数据。
     *
     * 执行命令：hdc -t <device> shell bm clean -n <bundleName> -d
     *
     * bm（Bundle Manager）是鸿蒙的包管理工具，类似 Android 的 pm。
     * -n 指定包名
     * -d 清除数据（data）和缓存（cache）
     *
     * @param device     目标设备序列号
     * @param bundleName 应用包名
     * @return 命令结果
     */
    @NotNull
    public HdcCommandResult clearAppData(@NotNull String device, @NotNull String bundleName) {
        return execute(device, "shell", "bm", "clean", "-n", bundleName, "-d");
    }

    // ========================================================================
    // 通用命令执行器
    // ========================================================================

    /**
     * 向指定设备发送 HDC 命令。
     *
     * 使用 "-t <device>" 指定目标设备。
     * 例如：execute(device, "uninstall", "com.example.app")
     * 最终执行：hdc -t <device> uninstall com.example.app
     *
     * ⚠️ 参数说明（来自 hdc 帮助）：
     *   -t connectkey    → 通过设备标识符指定目标设备（用这个！）
     *   -s [ip:]port      → 设置 hdc 服务端网络监听端口（不是指定设备！）
     *
     * @param device 目标设备序列号
     * @param args   HDC 命令及其参数（不含 "-t <device>"）
     * @return 命令执行结果
     */
    @NotNull
    public HdcCommandResult execute(@NotNull String device, String @NotNull ... args) {
        String hdc = resolveHdcPath();
        if (hdc == null) {
            return new HdcCommandResult(-1, "",
                    "未找到 hdc 可执行文件。请在 Settings > Tools > HDC Idea 中配置路径。");
        }
        List<String> fullArgs = new ArrayList<>();
        fullArgs.add("-t");           // -t 指定目标设备（不是 -s！）
        fullArgs.add(device);
        Collections.addAll(fullArgs, args);
        return executeRaw(hdc, fullArgs.toArray(new String[0]));
    }

    /**
     * 将设备 + 参数列表格式化为可读的命令字符串，用于通知展示。
     * 例如："hdc -t 
<longcat_arg_value>XXXX12345678 shell aa start -b com.example.app -a EntryAbility"
     *
     * @param device 设备序列号
     * @param args   命令参数
     * @return 完整命令字符串
     */
    @NotNull
    public String getCommandString(@NotNull String device, String @NotNull ... args) {
        StringBuilder sb = new StringBuilder("hdc -t ");
        sb.append(device);
        for (String arg : args) {
            sb.append(' ').append(arg);
        }
        return sb.toString();
    }

    /**
     * 不指定设备，直接执行 HDC 命令（用于不需要设备参数的查询类命令）。
     *
     * @param hdc  hdc 可执行文件的绝对路径
     * @param args HDC 命令及其参数
     * @return 命令执行结果
     */
    @NotNull
    public HdcCommandResult executeRaw(@NotNull String hdc, String @NotNull ... args) {
        List<String> command = new ArrayList<>();
        command.add(hdc);
        Collections.addAll(command, args);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        try {
            Process process = pb.start();

            // 分别读取标准输出和错误输出
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new HdcCommandResult(-1, stdout,
                        "命令执行超时（" + TIMEOUT_SECONDS + " 秒）");
            }

            return new HdcCommandResult(process.exitValue(), stdout, stderr);
        } catch (IOException e) {
            LOG.warn("执行 hdc 命令时发生 IO 异常", e);
            return new HdcCommandResult(-1, "", "IOException: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new HdcCommandResult(-1, "", "线程被中断");
        }
    }

    /**
     * 读取一个 InputStream 的全部内容为字符串。
     */
    private String readStream(java.io.InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
