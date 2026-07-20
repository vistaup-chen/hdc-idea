package com.deveco.hdcidea;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
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
     * 解析 hdc 可执行文件的绝对路径。按优先级依次尝试，命中即返回：
     * <ol>
     *   <li><b>用户手动配置</b> —— {@code Settings > Tools > HDC Idea}，最高优先级</li>
     *   <li><b>DevEco Studio SDK 目录</b> —— 自动搜索，路径形如：
     *       {@code <安装目录>/sdk/default/openharmony/toolchains/hdc.exe}
     *       （注意中间的 {@code default/openharmony} 这一层，很容易猜错）</li>
     *   <li><b>系统 PATH</b> —— 兜底：{@code hdc} 或 {@code hdc.exe} 在 PATH 里即可</li>
     * </ol>
     * <p>三档都找不到时返回 {@code null}，调用方应提示用户配置 hdc 路径。
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
     * 在 DevEco Studio 安装目录中搜索 hdc 可执行文件。
     *
     * <h3>为什么需要这么复杂的搜索？</h3>
     * hdc 的实际存放位置没有统一标准，会因 DevEco Studio 版本不同而变化：
     * <ul>
     *   <li>早期版本：{@code <安装目录>/sdk/toolchains/hdc.exe}</li>
     *   <li>API 12+（当前版本）：{@code <安装目录>/sdk/default/openharmony/toolchains/hdc.exe}</li>
     *   <li>极少数版本：{@code <安装目录>/tools/hdc/hdc.exe}</li>
     * </ul>
     * 同时，安装目录本身也可能是带版本号的，如 {@code DevEco Studio 6.0.0}，
     * 所以本函数分两步搜索：先查精确路径（覆盖历史写法），
     * 再模糊匹配（覆盖版本化 / 自定义安装）。
     *
     * @return hdc 的绝对路径，或 null 如果所有候选都不存在
     */
    @Nullable
    private String findInDevEcoSdk() {
        // -----------------------------------------------------------------
        // 第一层数据：hdc 在安装目录内的候选子路径（相对路径）
        // 每个 String[] 是一组等价变体（无扩展名 / .exe），每个都要试
        // -----------------------------------------------------------------
        String[][] candidateSets = {
                // 当前版本（API 12+）：sdk/default/openharmony/toolchains/hdc[.exe]
                {
                    "sdk" + File.separator + "default" + File.separator + "openharmony"
                            + File.separator + "toolchains" + File.separator + "hdc",
                    "sdk" + File.separator + "default" + File.separator + "openharmony"
                            + File.separator + "toolchains" + File.separator + "hdc.exe",
                },
                // 老版本：sdk/toolchains/hdc[.exe]
                {
                    "sdk" + File.separator + "toolchains" + File.separator + "hdc",
                    "sdk" + File.separator + "toolchains" + File.separator + "hdc.exe",
                },
                // 极少数版本：tools/hdc/hdc[.exe]
                {
                    "tools" + File.separator + "hdc" + File.separator + "hdc",
                    "tools" + File.separator + "hdc" + File.separator + "hdc.exe",
                },
        };

        String userHome = System.getProperty("user.home");

        // -----------------------------------------------------------------
        // 第一步：精确基路径
        // 直接写出最常见的 5 个「无版本号」安装位置。
        // 优点：匹配快、准确度高（不会误匹配名字相似的其他目录）。
        // -----------------------------------------------------------------
        String[] exactBaseDirs = {
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
        for (String base : exactBaseDirs) {
            String found = tryResolveInBase(base, candidateSets);
            if (found != null) {
                return found;
            }
        }

        // -----------------------------------------------------------------
        // 第二步：模糊匹配（覆盖版本化 / 自定义安装目录）
        //
        // 问题：DevEco Studio 安装时经常带版本号，比如：
        //   C:\Program Files\Huawei\DevEco Studio 6.0.0\
        //   C:\Users\foo\AppData\Local\Programs\DevEco Studio 6.0.1\
        // 这类路径无法穷举，所以改在「已知的父目录」下搜索名字含 "deveco" 的子目录。
        //
        // 为了防止扫描整个硬盘（太慢），这里做了三个限制：
        //   1. 只在下面这 8 个常见的父目录下搜索
        //   2. 只往下看 1 层（不递归孙目录）
        //   3. 目录名必须包含 "deveco"（DirectoryStream 的 glob 过滤）
        // 这样既能抓到版本化安装，又保证速度。
        // -----------------------------------------------------------------
        String[] globParents = {
                // 用户级安装（%LOCALAPPDATA% 下）
                userHome + File.separator + "AppData" + File.separator + "Local",
                userHome + File.separator + "AppData" + File.separator + "Local"
                        + File.separator + "Huawei",
                userHome + File.separator + "AppData" + File.separator + "Local"
                        + File.separator + "Programs",
                // 系统级安装（C:\Program Files 下）
                "C:" + File.separator + "Program Files",
                "C:" + File.separator + "Program Files" + File.separator + "Huawei",
                "C:" + File.separator + "Program Files (x86)",
                "C:" + File.separator + "Program Files (x86)" + File.separator + "Huawei",
                // 极少数情况：直接装在家目录
                userHome,
        };
        for (String parent : globParents) {
            for (String dir : listDevEcoDirectories(parent)) {
                String found = tryResolveInBase(dir, candidateSets);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * 在单个安装基路径下，依次尝试所有候选的 hdc 子路径。
     *
     * <p>逻辑分两层：
     * <ol>
     *   <li>先在基路径下直接拼接候选路径（如 {@code base + "/sdk/.../hdc.exe"}）</li>
     *   <li>如果基路径下存在 {@code sdk/} 子目录，则去掉候选路径开头的
     *       {@code sdk/} 再拼一次 —— 这是为了兼容「基路径本身就是 sdk 目录」的情况</li>
     * </ol>
     *
     * @param basePath        DevEco 的安装根目录（必须存在）
     * @param candidateSets   由 {@link #findInDevEcoSdk()} 定义的候选子路径组
     * @return hdc 的绝对路径，或 null 如果本基路径下没有匹配的 hdc
     */
    @Nullable
    private String tryResolveInBase(@NotNull String basePath, String[][] candidateSets) {
        Path basePathObj = Paths.get(basePath);
        if (!Files.isDirectory(basePathObj)) {
            return null;
        }
        // 直接拼路径
        for (String[] candidates : candidateSets) {
            for (String candidate : candidates) {
                Path resolved = basePathObj.resolve(candidate);
                if (Files.isRegularFile(resolved)) {
                    return resolved.toAbsolutePath().toString();
                }
            }
        }
        // 兜底：基路径下若有 sdk/ 子目录也搜一遍
        try {
            Path sdkPath = basePathObj.resolve("sdk");
            if (Files.isDirectory(sdkPath)) {
                for (String[] candidates : candidateSets) {
                    for (String candidate : candidates) {
                        String relativeToSdk = candidate.replaceFirst("^sdk" + File.separator, "");
                        Path resolved = sdkPath.resolve(relativeToSdk);
                        if (Files.isRegularFile(resolved)) {
                            return resolved.toAbsolutePath().toString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("搜索 " + basePath + "/sdk 失败", e);
        }
        return null;
    }

    /**
     * 列出指定父目录下，名字包含 "deveco" 的直接子目录（仅 1 层，不递归）。
     *
     * <p>这是模糊匹配的核心：通过 {@link Files#newDirectoryStream(Path, java.nio.file.DirectoryStream.Filter)}
     * 在操作系统层面按名字过滤，避免把整个目录树读进内存。
     *
     * @param parentDir 要搜索的父目录
     * @return 匹配到的子目录绝对路径列表（目录不存在时返回空列表）
     */
    @NotNull
    private List<String> listDevEcoDirectories(@NotNull String parentDir) {
        Path parentPath = Paths.get(parentDir);
        if (!Files.isDirectory(parentPath)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentPath, this::isDevEcoDirName)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    result.add(entry.toAbsolutePath().toString());
                }
            }
        } catch (IOException e) {
            LOG.warn("枚举目录 " + parentDir + " 失败", e);
        }
        return result;
    }

    /**
     * 判断一个路径的名字是否表示「DevEco Studio 安装目录」。
     * 规则：名字忽略大小写后包含 "deveco"。
     * 这样 {@code DevEco Studio}、{@code DevEco Studio 6.0.0}、{@code DevEcoStudio}
     * 都能匹配到。
     */
    private boolean isDevEcoDirName(@NotNull Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.contains("deveco");
    }

    /**
     * 在系统 PATH 环境变量中查找 hdc 可执行文件。
     *
     * <p>这是兜底的查找方式：只要用户把 hdc 所在目录加进了 PATH，
     * 即使没装 DevEco Studio（比如单独解压的 hdc 工具包），也能用这个工具。
     *
     * @return hdc 的绝对路径，如果 PATH 里没有则返回 null
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
    /** 最近一次 listDevices() 失败的原因，null 表示无错误。用于 UI 给出具体提示。 */
    @Nullable
    private String lastDeviceListError = null;

    /** 最近一次 listDevices() 失败的原因（找不到 hdc、命令执行失败等）。 */
    @Nullable
    public String getLastDeviceListError() {
        return lastDeviceListError;
    }

    @NotNull
    public List<String> listDevices() {
        lastDeviceListError = null;
        String hdc = resolveHdcPath();
        if (hdc == null) {
            lastDeviceListError = "未找到 hdc 可执行文件";
            return Collections.emptyList();
        }
        // hdc list targets 不需要指定设备（它就是列出所有设备）
        HdcCommandResult result = executeRaw(hdc, "list", "targets");
        if (!result.isSuccess()) {
            String err = result.getStderr();
            if (err == null || err.isBlank()) {
                err = result.getStdout();
            }
            lastDeviceListError = err;
            LOG.warn("hdc list targets 执行失败: " + err);
            return Collections.emptyList();
        }
        List<String> devices = new ArrayList<>();
        for (String line : result.getStdout().split("\\r?\\n")) {
            String trimmed = line.trim();
            // 跳过空行、hdc 的 debug 日志行（如 "[D][2026-07-17 ..."）
            if (!trimmed.isEmpty() && isLikelyDeviceSerial(trimmed)) {
                // 每行是一个序列号，可能后跟空格+状态（如 "device"、"offline"）
                String serial = trimmed.split("\\s+")[0];
                devices.add(serial);
            }
        }
        return devices;
    }

    /**
     * 粗略判断一行输出是否可能是鸿蒙设备的序列号。
     *
     * <p>过滤掉 hdc 打印的 debug 日志（形如 {@code [D][2026-07-17 17:14:04.619][9274] ...}）
     * 和多行分隔线等噪音。设备序列号通常为：
     * <ul>
     *   <li>纯数字+字母（USB 设备，如 {@code 2MH0224723022348}）</li>
     *   <li>IPv4:端口（如 {@code 192.168.1.100:5555}、{@code 127.0.0.1:5555}）</li>
     * </ul>
     */
    private boolean isLikelyDeviceSerial(@NotNull String trimmed) {
        String first = trimmed.split("\\s+")[0];
        // 去掉冒号和点后应该全是数字或字母
        String stripped = first.replaceAll("[.:]", "");
        return !stripped.isEmpty() && stripped.matches("[A-Za-z0-9]+");
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
            LOG.warn("jpid 执行失败 @" + device + " (exit=" + result.getExitCode() + "): " + result.getOutput());
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
            LOG.warn("ps -A 执行失败 @" + device + "，退到 ps (exit=" + result.getExitCode() + "): " + result.getOutput());
            result = execute(device, "shell", "ps");
            if (!result.isSuccess()) {
                LOG.warn("ps 也失败 @" + device + " (exit=" + result.getExitCode() + "): " + result.getOutput());
                return Collections.emptyList();
            }
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
