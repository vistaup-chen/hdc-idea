package com.deveco.hdcidea.actions;

import com.deveco.hdcidea.HdcCommandResult;
import com.deveco.hdcidea.HdcCommandService;
import com.deveco.hdcidea.HdcNotification;
import com.deveco.hdcidea.ProjectDetector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 重启应用：先停止再启动
 *
 *   1. hdc -t <device> shell aa force-stop <bundleName>
 *   2. hdc -t <device> shell aa start -b <bundleName> -a <abilityName>
 */
public class HdcRestartAction extends HdcAction {

    @Override
    protected @NotNull String getActionName() {
        return "重启应用";
    }

    @Override
    protected boolean executeAction(@NotNull Project project,
                                    @NotNull String device,
                                    @Nullable ProjectDetector.AppIdentity identity) {
        String bundleName = resolveBundleName(project, identity);
        if (bundleName == null) return false;
        String abilityName = resolveAbilityName(project, identity);

        // 停止 —— 失败要明确告知用户，不能静默吞掉
        HdcCommandResult stopResult = hdcService.stopApp(device, bundleName);
        if (!stopResult.isSuccess()) {
            HdcNotification.notifyError(project,
                    "停止 " + bundleName + " @ " + device + " 失败 (exit=" + stopResult.getExitCode() + ")\n"
                            + "命令: " + hdcService.getCommandString(device,
                                    "shell", "aa", "force-stop", bundleName) + "\n"
                            + "设备返回: " + stopResult.getOutput());
            return true;
        }
        // 启动
        HdcCommandResult result = hdcService.startApp(device, bundleName, abilityName);

        // 启动失败且与 ability 有关时，弹一次输入框让用户手输正确的 Ability
        if (!result.isSuccess() && HdcCommandService.isAbilityError(result)) {
            String corrected = promptAbilityNameOnFailure(project, abilityName);
            if (corrected != null) {
                abilityName = corrected;
                result = hdcService.startApp(device, bundleName, abilityName);
            }
        }

        notifyResult(project, device, result,
                "重启 " + bundleName + "/" + abilityName,
                "shell", "aa", "start", "-b", bundleName, "-a", abilityName);
        return true;
    }
}
