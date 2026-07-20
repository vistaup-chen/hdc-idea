package com.deveco.hdcidea.actions;

import com.deveco.hdcidea.HdcCommandResult;
import com.deveco.hdcidea.HdcNotification;
import com.deveco.hdcidea.ProjectDetector;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 撤销运行时权限
 *
 * 尝试命令：hdc -t <device> shell bm revoke-permission -n <bundleName> -p <permission>
 *
 * ⚠️ 同 GrantPermissionAction，需要较新 API 支持。
 */
public class HdcRevokePermissionAction extends HdcAction {

    @Override
    protected @NotNull String getActionName() {
        return "撤销权限";
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
                "撤销权限",
                Messages.getQuestionIcon());
        if (permission == null || permission.isBlank()) {
            HdcNotification.notifyInfo(project, "已取消：未输入权限名。");
            return false;
        }

        String permTrimmed = permission.trim();
        HdcCommandResult result = hdcService.execute(device,
                "shell", "bm", "revoke-permission", "-n", bundleName, "-p", permTrimmed);

        if (result.isSuccess()) {
            String cmdStr = hdcService.getCommandString(device,
                    "shell", "bm", "revoke-permission", "-n", bundleName, "-p", permTrimmed);
            String output = truncate(result.getOutput());
            HdcNotification.notifyInfo(project,
                    "已撤销 " + permTrimmed + "（" + bundleName + "）@ " + device + "\n"
                            + "命令: " + cmdStr + "\n输出: " + output);
        } else {
            String err = result.getOutput().toLowerCase();
            String cmdStr = hdcService.getCommandString(device,
                    "shell", "bm", "revoke-permission", "-n", bundleName, "-p", permTrimmed);
            if (err.contains("not a valid") || err.contains("unsupported")) {
                HdcNotification.notifyWarning(project,
                        "当前设备不支持 bm revoke-permission。\n"
                                + "命令: " + cmdStr + "\n设备返回: " + truncate(result.getOutput()));
            } else {
                HdcNotification.notifyError(project,
                        "撤销失败 @ " + device + "\n"
                                + "命令: " + cmdStr + "\n设备返回: " + truncate(result.getOutput()));
            }
        }
        return true;
    }

    private String truncate(String s) {
        return s.length() > 2000 ? s.substring(0, 2000) + "..." : s;
    }
}
