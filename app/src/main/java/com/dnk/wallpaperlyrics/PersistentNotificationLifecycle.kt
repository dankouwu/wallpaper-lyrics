package com.dnk.wallpaperlyrics

internal enum class NotificationAction {
    NONE,
    SHOW_OR_UPDATE,
    CANCEL
}

internal class PersistentNotificationLifecycle {
    private val activeEngines = mutableSetOf<Any>()
    private var isEnabled: Boolean? = null

    fun onPreferenceChanged(enabled: Boolean): NotificationAction {
        if (isEnabled == enabled) return NotificationAction.NONE

        isEnabled = enabled
        return when {
            !enabled -> NotificationAction.CANCEL
            activeEngines.isNotEmpty() -> NotificationAction.SHOW_OR_UPDATE
            else -> NotificationAction.NONE
        }
    }

    fun onEngineCreated(engine: Any, isPreview: Boolean): NotificationAction {
        if (isPreview) return NotificationAction.NONE

        val wasEmpty = activeEngines.isEmpty()
        val wasAdded = activeEngines.add(engine)
        return if (isEnabled == true && wasAdded && wasEmpty) {
            NotificationAction.SHOW_OR_UPDATE
        } else {
            NotificationAction.NONE
        }
    }

    fun onEngineDestroyed(engine: Any, isPreview: Boolean): NotificationAction {
        if (isPreview || !activeEngines.remove(engine)) return NotificationAction.NONE

        return if (activeEngines.isEmpty()) {
            NotificationAction.CANCEL
        } else {
            NotificationAction.NONE
        }
    }

    fun onMetadataChanged(engine: Any, isPreview: Boolean): NotificationAction? {
        if (isPreview || engine !in activeEngines) return null

        return if (isEnabled == true) {
            NotificationAction.SHOW_OR_UPDATE
        } else {
            NotificationAction.NONE
        }
    }
}
