package org.sui.ide.notifications

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager

object Notifications {
    fun pluginNotifications(): NotificationGroup {
        return NotificationGroupManager.getInstance().getNotificationGroup("Sui Move Language")
    }
}
