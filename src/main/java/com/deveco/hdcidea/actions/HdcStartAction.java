package com.deveco.hdcidea.actions;

import com.deveco.hdcidea.HdcCommandResult;
import com.deveco.hdcidea.HdcCommandService;
import com.deveco.hdcidea.ProjectDetector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 启动应用
 *
 * 执行命令：hdc -t <device> shell aa start -b <bundleName> -a <abilityName>
 */
public class HdcStartAction extends HdcAction {

    @Override
    protected @NotNull String getActionName() {
        return "启动应用";
    }

    @Override
    protected boolean executeAction(@NotNull Project project,
                                    @NotNull String device,
                                    @Nullable ProjectDetector.AppIdentity identity) {
        String bundleName = resolveBundleName(project, identity);
        if (bundleName == null) return false;
        String abilityName = resolveAbilityName(project, identity);

        HdcCommandResult result = hdcService.startApp(device, bundleName, abilityName);

        // 启动失败且与 ability 有关时，弹一次输入框让用户手输正确的 Ability
        if (!result.isSuccess() && HdcCommandService.isAbilityError(result)) {
            String corrected = promptAbilityNameOnFailure(project, abilityName);
            if (corrected != null) {
                abilityName = corrected;
                result = hdcService.startApp(device, bundleName, abilityName);
            }
        }

        // 通知中展示完整命令 + 设备输出
        notifyResult(project, device, result,
                "启动 " + bundleName + "/" + abilityName,
                "shell", "aa", "start", "-b", bundleName, "-a", abilityName);
        return true;
    }
}
