package com.deveco.hdcidea.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows a popup listing all HDC Idea operations.
 * Bound to Shift+Ctrl+Alt+A (same shortcut as ADB Idea).
 *
 * Actions are fetched from the ActionManager (so they carry their plugin.xml
 * text/icon/presentation) rather than instantiated directly.
 */
public class HdcQuickListAction extends AnAction {

    private static final String[] ACTION_IDS = {
            "Hdcidea.Uninstall",
            "Hdcidea.Kill",
            "Hdcidea.Start",
            "Hdcidea.Restart",
            "Hdcidea.ClearData",
            "Hdcidea.ClearDataRestart",
            "Hdcidea.GrantPerm",
            "Hdcidea.RevokePerm",
    };

    private static final ActionGroup ACTION_GROUP = new ActionGroup("HDC Idea", true) {
        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
            ActionManager am = ActionManager.getInstance();
            java.util.List<AnAction> actions = new java.util.ArrayList<>();
            for (String id : ACTION_IDS) {
                AnAction action = am.getAction(id);
                if (action != null) {
                    actions.add(action);
                }
            }
            return actions.toArray(new AnAction[0]);
        }
    };

    public HdcQuickListAction() {
        super("HDC Operations Popup...", "List all HDC Idea operations in a popup", null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) return;

        ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                "HDC Idea Operations",
                ACTION_GROUP,
                event.getDataContext(),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
        );

        popup.showInBestPositionFor(event.getDataContext());
    }
}
