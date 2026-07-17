package com.deveco.hdcidea.actions;

import com.deveco.hdcidea.HdcCommandResult;
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
        if (abilityName == null) return false;

        // 停止
        hdcService.stopApp(device, bundleName);
        // 启动
        HdcCommandResult result = hdcService.startApp(device, bundleName, abilityName);

        notifyResult(project, device, result,
                "重启 " + bundleName + "/" + abilityName,
                "shell", "aa", "start", "-b", bundleName, "-a", abilityName);
        return true;
    }
}
