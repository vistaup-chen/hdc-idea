package com.deveco.hdcidea;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Helper for showing notifications to the user.
 */
public class HdcNotification {

    private static final NotificationGroup GROUP =
            NotificationGroupManager.getInstance().getNotificationGroup("HDC Idea");

    public static void notify(@NotNull Project project, @NotNull String content, @NotNull NotificationType type) {
        Notification notification = GROUP.createNotification(content, type);
        notification.notify(project);
    }

    public static void notifyInfo(@NotNull Project project, @NotNull String content) {
        notify(project, content, NotificationType.INFORMATION);
    }

    public static void notifyWarning(@NotNull Project project, @NotNull String content) {
        notify(project, content, NotificationType.WARNING);
    }

    public static void notifyError(@NotNull Project project, @NotNull String content) {
        notify(project, content, NotificationType.ERROR);
    }
}
