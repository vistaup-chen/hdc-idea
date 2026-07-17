package com.deveco.hdcidea.actions;

import com.deveco.hdcidea.HdcCommandResult;
import com.deveco.hdcidea.HdcNotification;
import com.deveco.hdcidea.ProjectDetector;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 强制停止应用
 *
 * 优先使用 aa force-stop：
 *   hdc -t <device> shell aa force-stop <bundleName>
 *
 * 失败时退到 ps 找 PID + kill -9。
 */
public class HdcKillAction extends HdcAction {

    @Override
    protected @NotNull String getActionName() {
        return "杀进程";
    }

    @Override
    protected boolean executeAction(@NotNull Project project,
                                    @NotNull String device,
                                    @Nullable ProjectDetector.AppIdentity identity) {
        String bundleName = resolveBundleName(project, identity);
        if (bundleName == null) return false;

        // 方法 1：aa force-stop
        HdcCommandResult result = hdcService.stopApp(device, bundleName);
        notifyResult(project, device, result,
                "停止 " + bundleName,
                "shell", "aa", "force-stop", bundleName);
        if (result.isSuccess()) return true;

        // 方法 2：findPidsByBundle + kill -9
        List<String> pids = hdcService.findPidsByBundle(device, bundleName);
        if (pids.isEmpty()) {
            pids = hdcService.listPids(device);
        }
        if (pids.isEmpty()) {
            HdcNotification.notifyWarning(project,
                    "未找到 " + bundleName + " 在 " + device + " 上的运行进程。");
            return true;
        }

        int killed = 0;
        StringBuilder detail = new StringBuilder();
        for (String pid : pids) {
            HdcCommandResult r = hdcService.execute(device, "shell", "kill", "-9", pid);
            detail.append("PID ").append(pid).append(": ")
                    .append(r.isSuccess() ? "killed" : r.getOutput()).append("\n");
            if (r.isSuccess()) killed++;
        }
        HdcNotification.notifyInfo(project,
                "已杀死 " + killed + " 个进程于 " + device + "。\n" + detail);
        return true;
    }
}
