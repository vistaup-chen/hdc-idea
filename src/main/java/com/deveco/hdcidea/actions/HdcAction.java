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
            return;
        }

        // 步骤 1：解析目标设备
        String device = resolveDevice(project);
        if (device == null) {
            return; // 用户取消或无设备
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
     * 解析目标设备。
     * - hdc 未配置 → 提示配置路径，返回 null
     * - 命令执行失败 → 提示设备返回的具体错误，返回 null
     * - 0 台 → 提示无设备，返回 null
     * - 1 台 → 直接返回
     * - 多台 → 弹出选择框
     *
     * @return 选中的设备序列号，或 null
     */
    @Nullable
    protected String resolveDevice(@NotNull Project project) {
        List<String> devices = hdcService.listDevices();

        if (devices.isEmpty()) {
            // 区分"找不到 hdc"、"命令失败"、"真的没设备"三种情况
            String err = hdcService.getLastDeviceListError();
            if (err != null && !err.isBlank()) {
                if (err.contains("未找到 hdc")) {
                    HdcNotification.notifyError(project,
                            "未找到 hdc 工具。请在 Settings > Tools > HDC Idea 中配置 hdc 路径，"
                                    + "或将 hdc.exe 所在目录添加到系统 PATH。");
                } else {
                    HdcNotification.notifyError(project,
                            "hdc list targets 执行失败。\n设备返回: " + truncate(err));
                }
            } else {
                HdcNotification.notifyWarning(project,
                        "未连接鸿蒙设备。请用 USB 连接设备，或执行 hdc tconn <IP:port> 连接网络设备后重试。");
            }
            return null;
        }
        if (devices.size() == 1) {
            return devices.get(0);
        }
        // 多台设备 → 弹选择框
        DeviceChooserDialog dialog = new DeviceChooserDialog(devices);
        if (dialog.showAndGet()) {
            return dialog.getSelectedDevice();
        }
        return null; // 用户取消
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    /**
     * 统一的通知展示方法 —— 显示操作结果 + 完整命令 + 设备输出。
     *
     * 通知格式：
     *   <操作名> @ <设备>
     *   命令: hdc -t xxx shell aa start ...
     *   输出: <设备返回>
     *
     * @param project   当前 project
     * @param device    目标设备
     * @param result    命令执行结果
     * @param operation 操作描述（如 "启动 com.example.app/EntryAbility"）
     * @param args      用于生成命令字符串的参数（传给 getCommandString）
     */
    protected void notifyResult(@NotNull Project project,
                                @NotNull String device,
                                @NotNull HdcCommandResult result,
                                @NotNull String operation,
                                String @NotNull ... args) {
        // 截断过长的输出（保留更多内容，2000 字符对 balloon 通知足够）
        String output = result.getOutput();
        if (output.length() > 2000) {
            output = output.substring(0, 2000) + "...";
        }
        // 拼接完整命令字符串，含退出码方便定位
        String cmdStr = hdcService.getCommandString(device, args);
        String fullMsg = operation + " @ " + device + " (exit=" + result.getExitCode() + ")\n"
                + "命令: " + cmdStr + "\n"
                + "输出: " + output;

        if (result.isSuccess()) {
            HdcNotification.notifyInfo(project, fullMsg);
        } else {
            HdcNotification.notifyError(project, fullMsg);
        }
    }
}
