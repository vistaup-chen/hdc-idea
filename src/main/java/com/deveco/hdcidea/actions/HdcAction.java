package com.deveco.hdcidea.actions;

import com.deveco.hdcidea.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 所有 HDC Idea 操作的抽象基类。
 *
 * 统一处理通用流程：
 *   1. 获取当前 Project
 *   2. 解析目标设备（自动选择 / 单台直接用 / 多台弹选择框）
 *   3. 检测或提示输入包名
 *   4. 让子类执行具体的 HDC 命令
 *
 * 子类只需实现：
 *   - getActionName()       → 返回操作名称（用于通知标题）
 *   - executeAction()       → 执行具体逻辑
 */
public abstract class HdcAction extends AnAction {

    protected final HdcCommandService hdcService = new HdcCommandService();

    /**
     * @return 操作名称，用于通知标题（如 "启动应用"）
     */
    @NotNull
    protected abstract String getActionName();

    /**
     * 执行该操作的具体逻辑。
     *
     * @param project  当前 project
     * @param device   目标设备序列号
     * @param identity 自动检测到的应用身份（可能为 null）
     * @return true 表示操作已完成（无论成功还是预期的失败），false 表示用户取消应静默退出
     */
    protected abstract boolean executeAction(@NotNull Project project,
                                             @NotNull String device,
                                             @Nullable ProjectDetector.AppIdentity identity);

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            // 没有打开工程时点了按钮 —— 给个轻量反馈
            HdcNotification.notifyWarning(project, "请先打开一个鸿蒙工程，再执行该操作。");
            return;
        }

        // 步骤 1：解析目标设备
        String device = resolveDevice(project);
        if (device == null || device.isBlank()) {
            return; // 用户取消 / 无设备 / hdc 缺失 —— resolveDevice 已弹通知
        }

        // 步骤 2：自动检测应用身份（包名 + Ability）
        ProjectDetector.AppIdentity identity = ProjectDetector.detect(project);

        // 步骤 3：让子类执行
        executeAction(project, device, identity);
    }

    /**
     * 解析要使用的包名。
     * 优先级：自动检测 → 上次使用的（设置中） → 弹出输入框让用户手填。
     *
     * @return 包名，或 null 如果用户取消输入
     */
    @Nullable
    protected String resolveBundleName(@NotNull Project project,
                                       @Nullable ProjectDetector.AppIdentity identity) {
        // 优先使用自动检测到的
        if (identity != null && identity.bundleName != null && !identity.bundleName.isBlank()) {
            HdcSettingsState.getInstance().bundleName = identity.bundleName; // 记住
            return identity.bundleName;
        }
        // 退到上次使用的
        String lastUsed = HdcSettingsState.getInstance().bundleName;
        if (lastUsed != null && !lastUsed.isBlank()) {
            return lastUsed;
        }
        // 都没有，让用户手填
        String input = Messages.showInputDialog(
                project,
                "未检测到包名。请手动输入（如 com.example.app）：",
                "输入包名",
                Messages.getQuestionIcon(),
                lastUsed,
                null);
        if (input != null && !input.isBlank()) {
            HdcSettingsState.getInstance().bundleName = input.trim();
            return input.trim();
        }
        // 用户取消输入 —— 给个轻量反馈，区别于真正的错误
        HdcNotification.notifyInfo(project, "已取消：未输入包名。");
        return null;
    }

    /** 未检测到 ability 时的默认值（鸿蒙工程默认入口） */
    protected static final String DEFAULT_ABILITY_NAME = "EntryAbility";

    /**
     * 解析要启动的 Ability 名称，按以下优先级：
     *   1. 自动检测到的（来自工程 module.json5）
     *   2. 上次使用的（设置中记忆）
     *   3. 默认值 EntryAbility（静默使用，不弹窗）
     *
     * 检测失败时不弹窗阻塞，由调用方在启动失败且错误与 ability 有关时再提示。
     *
     * @return Ability 名称（永不为 null）
     */
    @NotNull
    protected String resolveAbilityName(@NotNull Project project,
                                        @Nullable ProjectDetector.AppIdentity identity) {
        // 优先使用自动检测到的，并记住
        if (identity != null && identity.abilityName != null && !identity.abilityName.isBlank()) {
            HdcSettingsState.getInstance().abilityName = identity.abilityName;
            return identity.abilityName;
        }
        // 退到上次使用的
        String lastUsed = HdcSettingsState.getInstance().abilityName;
        if (lastUsed != null && !lastUsed.isBlank()) {
            return lastUsed;
        }
        // 都没有，静默使用默认值
        return DEFAULT_ABILITY_NAME;
    }

    /**
     * 启动失败后，若错误与 ability 有关，弹出输入框让用户手输正确的 Ability。
     * 输入后自动记住，下次不再弹。
     *
     * @return 用户输入的 Ability 名称，或 null 如果用户取消
     */
    @Nullable
    protected String promptAbilityNameOnFailure(@NotNull Project project, @NotNull String currentAbility) {
        String input = Messages.showInputDialog(
                project,
                "启动失败，Ability 名称可能不正确（当前用的是 \"" + currentAbility + "\"）。\n"
                        + "请输入正确的 Ability 名称：",
                    "启动失败 - 输入 Ability",
                    Messages.getWarningIcon(),
                    currentAbility,
                    null);
        if (input != null && !input.isBlank() && !input.trim().equals(currentAbility)) {
            String trimmed = input.trim();
            HdcSettingsState.getInstance().abilityName = trimmed;
            return trimmed;
        }
        return null;
    }

    /**
     * 解析要操作的目标设备，全中文提示、错误信息完整。
     *
     * <p>流程与提示：
     * <ol>
     *   <li>hdc 工具找不到 → 提示去「设置 > 工具 > HDC Idea」配置路径或加入 PATH，返回 null</li>
     *   <li>{@code hdc list targets} 命令执行失败 → 提示命令返回的具体错误原文，返回 null</li>
     *   <li>hdc 就绪但未发现任何已连接设备 → 提示连接 USB 或用 {@code hdc tconn} 连网，返回 null</li>
     *   <li>发现 1 台设备 → 直接使用</li>
     *   <li>发现多台 → 弹出选择框（双击或点确定选中）</li>
     *   <li>用户取消选择框 → 返回 null，不弹通知</li>
     * </ol>
     *
     * @return 选中的设备序列号（非空），或在无法确定设备时返回 null
     */
    @Nullable
    protected String resolveDevice(@NotNull Project project) {
        List<String> devices = hdcService.listDevices();

        if (devices.isEmpty()) {
            // 区分三种"空列表"的真实原因，给出不同的处理建议
            String err = hdcService.getLastDeviceListError();
            if (err != null && !err.isBlank()) {
                if (err.contains("未找到 hdc")) {
                    HdcNotification.notifyError(project,
                            "✘ 未找到 hdc 工具。\n\n"
                                    + "「HDC 工具」是鸿蒙调试的核心程序。请按以下任一方式配置：\n"
                                    + "  1. 打开 IDE：设置 → 工具 → HDC Idea → 填写 hdc.exe 的绝对路径\n"
                                    + "  2. 将 hdc.exe 所在目录添加到系统 PATH 环境变量\n\n"
                                    + "hdc.exe 通常位于 DevEco Studio 安装目录下的：\n"
                                    + "  sdk/default/openharmony/toolchains/hdc.exe\n\n"
                                    + "配置后重启 IDE，或重新点击该按钮即可。");
                } else {
                    HdcNotification.notifyError(project,
                            "✘ hdc list targets 命令执行失败。\n\n"
                                    + "设备返回的错误信息：\n" + err + "\n\n"
                                    + "建议：请先在终端执行 hdc list targets 确认命令本身能正常返回。");
                }
            } else {
                HdcNotification.notifyWarning(project,
                        "✘ 未发现已连接的鸿蒙设备。\n\n"
                                + "请检查：\n"
                                + "  • USB 设备是否已通过数据线连接，且已开启「USB 调试」模式\n"
                                + "  • 模拟器是否已启动并正常运行\n"
                                + "  • 如需连接网络设备，先手动执行：hdc tconn <IP地址>:<端口>\n\n"
                                + "连接成功后重新点击该按钮。");
            }
            return null;
        }

        if (devices.size() == 1) {
            return devices.get(0);
        }

        // 多台设备：弹出选择框，让用户挑选
        DeviceChooserDialog dialog = new DeviceChooserDialog(devices);
        if (dialog.showAndGet()) {
            return dialog.getSelectedDevice();
        }
        return null; // 用户主动取消选择，无需通知
    }

    /**
     * 统一的通知展示方法。
     *
     * <p>第一行是中文描述结果（✔ 成功 / ✘ 失败），
     * 后面紧跟完整的命令、退出码、设备返回原文。
     * 失败时末尾追加简要排查建议。
     *
     * <p>示例（失败）：
     * <pre>
     * ✘ 设备返回错误：未匹配到目标设备 @ 127.0.0.1:5555
     *
     * 命令：hdc -t 127.0.0.1:5555 uninstall com.wasu.test_playcontrol
     * 退出码：1
     * 设备返回：[Fail]Not match target founded, check connect-key please
     *
     * 建议：设备序列号不正确或已断开。请在「选择设备」框中重新挑选，或重新连接后重试。
     * </pre>
     *
     * @param project   当前 project
     * @param device    目标设备（已解析的非空序列号）
     * @param result    命令执行结果
     * @param message   结果的中文描述（成功如"卸载完成"，失败如"设备返回错误：未匹配到目标设备"）
     * @param args      用于生成命令字符串的参数（传给 getCommandString）
     */
    protected void notifyResult(@NotNull Project project,
                                @NotNull String device,
                                @NotNull HdcCommandResult result,
                                @NotNull String message,
                                String @NotNull ... args) {
        String output = result.getOutput();
        boolean success = result.isSuccess();
        String cmdStr = hdcService.getCommandString(device, args);

        StringBuilder sb = new StringBuilder();
        // 第一行：中文主要原因
        sb.append(success ? "✔ " : "✘ ").append(message);
        sb.append(" @ ").append(device).append("\n\n");
        // 完整技术信息
        sb.append("命令：").append(cmdStr).append("\n");
        sb.append("退出码：").append(result.getExitCode()).append("\n");
        sb.append("设备返回：").append(output);

        // 失败时追加简要排查建议
        if (!success) {
            sb.append("\n\n建议：");
            String lower = output.toLowerCase();
            if (lower.contains("not match target") || lower.contains("check connect-key")) {
                sb.append("设备序列号不正确或已断开。请在「选择设备」框中重新挑选，或重新连接后重试。");
            } else if (lower.contains("not found") || lower.contains("could not find")) {
                sb.append("设备上未安装该应用，或包名/Ability 名不正确。请确认应用已安装、包名拼写无误。");
            } else if (lower.contains("permission denied") || lower.contains("not allowed")) {
                sb.append("权限不足。请确认该操作是否需要更高权限，或尝试重新连接设备。");
            } else if (lower.contains("device offline") || lower.contains("device unauthorized")) {
                sb.append("设备离线或未授权。请检查 USB 连接、设备屏幕上的「允许 USB 调试」提示。");
            } else {
                sb.append("请检查设备连接、hdc 工具、命令参数是否正确。可在终端手动执行上方命令获取详细信息。");
            }
        }

        if (success) {
            HdcNotification.notifyInfo(project, sb.toString());
        } else {
            HdcNotification.notifyError(project, sb.toString());
        }
    }
}
