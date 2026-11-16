package com.westly.opay.feature.notifications

import com.westly.opay.data.model.AppNotification

internal val NotificationTabs: List<String> = listOf("Transactions", "Services", "Activities")

/** The number shown on the bell badge: notifications that have not been read yet. */
internal fun unreadCount(notifications: List<AppNotification>): Int = notifications.count { !it.read }

/** The notifications of one tab, in the order given. */
internal fun notificationsForTab(notifications: List<AppNotification>, tab: String): List<AppNotification> =
    notifications.filter { it.tab == tab }
