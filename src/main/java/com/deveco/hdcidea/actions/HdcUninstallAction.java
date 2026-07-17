package com.deveco.hdcidea.actions;

import com.deveco.hdcidea.HdcCommandResult;
import com.deveco.hdcidea.ProjectDetector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 卸载应用
 *
 * 执行命令：hdc -t <device> uninstall <bundleName>
 *
 * 注意：hdc uninstall 是 HDC 自带子命令（不是通过 shell 调用的）。
 */
public class HdcUninstallAction extends HdcAction {

    @Override
    protected @NotNull String getActionName() {
        return "卸载应用";
    }

    @Override
    protected boolean executeAction(@NotNull Project project,
                                    @NotNull String device,
                                    @Nullable ProjectDetector.AppIdentity identity) {
        String bundleName = resolveBundleName(project, identity);
        if (bundleName == null) return false;

        HdcCommandResult result = hdcService.execute(device, "uninstall", bundleName);

        notifyResult(project, device, result,
                "卸载 " + bundleName,
                "uninstall", bundleName);
        return true;
    }
}
