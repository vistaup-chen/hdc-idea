package com.deveco.hdcidea.actions;

import com.deveco.hdcidea.HdcCommandResult;
import com.deveco.hdcidea.ProjectDetector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 清除应用数据并重启
 *
 *   1. hdc -t <device> shell bm clean -n <bundleName> -c -d
 *   2. hdc -t <device> shell aa start -b <bundleName> -a <abilityName>
 */
public class HdcClearDataAndRestartAction extends HdcAction {

    @Override
    protected @NotNull String getActionName() {
        return "清除数据并重启";
    }

    @Override
    protected boolean executeAction(@NotNull Project project,
                                    @NotNull String device,
                                    @Nullable ProjectDetector.AppIdentity identity) {
        String bundleName = resolveBundleName(project, identity);
        if (bundleName == null) return false;
        String abilityName = resolveAbilityName(project, identity);
        if (abilityName == null) return false;

        // 清数据
        HdcCommandResult clearResult = hdcService.clearAppData(device, bundleName);
        if (!clearResult.isSuccess()) {
            notifyResult(project, device, clearResult,
                    "清除数据失败 " + bundleName,
                    "shell", "bm", "clean", "-n", bundleName, "-c", "-d");
            return true;
        }

        // 启动
        HdcCommandResult startResult = hdcService.startApp(device, bundleName, abilityName);

        notifyResult(project, device, startResult,
                "已清除数据并重启 " + bundleName + "/" + abilityName,
                "shell", "aa", "start", "-b", bundleName, "-a", abilityName);
        return true;
    }
}
