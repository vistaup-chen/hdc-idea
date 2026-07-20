package com.deveco.hdcidea.actions;

import com.deveco.hdcidea.HdcCommandResult;
import com.deveco.hdcidea.HdcNotification;
import com.deveco.hdcidea.ProjectDetector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 授予运行时权限
 *
 * 尝试命令：hdc -t <device> shell bm grant-permission -n <bundleName> -p <permission>
 *
 * ⚠️ bm grant-permission 需要较新鸿蒙 API。当前设备可能不支持，
 * 会检测错误并提示替代方案（hdc install -g）。
 */
public class HdcGrantPermissionAction extends HdcAction {

    @Override
    protected @NotNull String getActionName() {
        return "授予权限";
    }

    @Override
    protected boolean executeAction(@NotNull Project project,
                                    @NotNull String device,
                                    @Nullable ProjectDetector.AppIdentity identity) {
        String bundleName = resolveBundleName(project, identity);
        if (bundleName == null) return false;

        String permission = Messages.showInputDialog(
                project,
                "请输入权限名（如 ohos.permission.CAMERA）：",
                "授予权限",
                Messages.getQuestionIcon());
        if (permission == null || permission.isBlank()) {
            HdcNotification.notifyInfo(project, "已取消：未输入权限名。");
            return false;
        }

        String permTrimmed = permission.trim();
        HdcCommandResult result = hdcService.execute(device,
                "shell", "bm", "grant-permission", "-n", bundleName, "-p", permTrimmed);

        if (result.isSuccess()) {
            String cmdStr = hdcService.getCommandString(device,
                    "shell", "bm", "grant-permission", "-n", bundleName, "-p", permTrimmed);
            String output = truncate(result.getOutput());
            HdcNotification.notifyInfo(project,
                    "已授予 " + permTrimmed + " 给 " + bundleName + " @ " + device + "\n"
                            + "命令: " + cmdStr + "\n输出: " + output);
        } else {
            String err = result.getOutput().toLowerCase();
            String cmdStr = hdcService.getCommandString(device,
                    "shell", "bm", "grant-permission", "-n", bundleName, "-p", permTrimmed);
            if (err.contains("not a valid") || err.contains("unsupported")) {
                HdcNotification.notifyWarning(project,
                        "当前设备不支持 bm grant-permission。\n"
                                + "替代方案：hdc install -g <hap> 安装时自动授权。\n"
                                + "命令: " + cmdStr + "\n设备返回: " + truncate(result.getOutput()));
            } else {
                HdcNotification.notifyError(project,
                        "授权失败 @ " + device + "\n"
                                + "命令: " + cmdStr + "\n设备返回: " + truncate(result.getOutput()));
            }
        }
        return true;
    }

    private String truncate(String s) {
        return s.length() > 2000 ? s.substring(0, 2000) + "..." : s;
    }
}
