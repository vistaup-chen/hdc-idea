package com.deveco.hdcidea.actions;

import com.deveco.hdcidea.HdcCommandResult;
import com.deveco.hdcidea.ProjectDetector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 清除应用数据
 *
 * 执行命令：hdc -t <device> shell bm clean -n <bundleName> -c -d
 *
 * bm = Bundle Manager（鸿蒙包管理工具）
 * -c = 清除缓存（cache）
 * -d = 清除数据（data）
 */
public class HdcClearDataAction extends HdcAction {

    @Override
    protected @NotNull String getActionName() {
        return "清除应用数据";
    }

    @Override
    protected boolean executeAction(@NotNull Project project,
                                    @NotNull String device,
                                    @Nullable ProjectDetector.AppIdentity identity) {
        String bundleName = resolveBundleName(project, identity);
        if (bundleName == null) return false;

        HdcCommandResult result = hdcService.clearAppData(device, bundleName);

        notifyResult(project, device, result,
                "清除数据 " + bundleName,
                "shell", "bm", "clean", "-n", bundleName, "-c", "-d");
        return true;
    }
}
