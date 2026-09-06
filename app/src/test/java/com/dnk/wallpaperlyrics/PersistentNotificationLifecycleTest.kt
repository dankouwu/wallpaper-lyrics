package com.dnk.wallpaperlyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersistentNotificationLifecycleTest {

    @Test
    fun `loading a disabled preference cancels a stale notification`() {
        val lifecycle = PersistentNotificationLifecycle()

        assertEquals(NotificationAction.CANCEL, lifecycle.onPreferenceChanged(enabled = false))
    }

    @Test
    fun `enabling with an active wallpaper shows the notification`() {
        val lifecycle = PersistentNotificationLifecycle()
        val engine = Any()

        assertEquals(NotificationAction.NONE, lifecycle.onEngineCreated(engine, isPreview = false))
        assertEquals(NotificationAction.SHOW_OR_UPDATE, lifecycle.onPreferenceChanged(enabled = true))
    }

    @Test
    fun `metadata cannot restore a disabled notification`() {
        val lifecycle = PersistentNotificationLifecycle()
        val engine = Any()
        lifecycle.onEngineCreated(engine, isPreview = false)
        lifecycle.onPreferenceChanged(enabled = true)

        assertEquals(NotificationAction.CANCEL, lifecycle.onPreferenceChanged(enabled = false))
        assertEquals(NotificationAction.NONE, lifecycle.onMetadataChanged(engine, isPreview = false))
    }

    @Test
    fun `preview lifecycle does not cancel the active wallpaper notification`() {
        val lifecycle = PersistentNotificationLifecycle()
        val realEngine = Any()
        val previewEngine = Any()
        lifecycle.onPreferenceChanged(enabled = true)
        assertEquals(NotificationAction.SHOW_OR_UPDATE, lifecycle.onEngineCreated(realEngine, isPreview = false))

        assertEquals(NotificationAction.NONE, lifecycle.onEngineCreated(previewEngine, isPreview = true))
        assertEquals(NotificationAction.NONE, lifecycle.onEngineDestroyed(previewEngine, isPreview = true))
        assertEquals(NotificationAction.SHOW_OR_UPDATE, lifecycle.onMetadataChanged(realEngine, isPreview = false))
    }

    @Test
    fun `only the final real engine teardown cancels the notification`() {
        val lifecycle = PersistentNotificationLifecycle()
        val firstEngine = Any()
        val secondEngine = Any()
        lifecycle.onPreferenceChanged(enabled = true)
        lifecycle.onEngineCreated(firstEngine, isPreview = false)
        lifecycle.onEngineCreated(secondEngine, isPreview = false)

        assertEquals(NotificationAction.NONE, lifecycle.onEngineDestroyed(firstEngine, isPreview = false))
        assertEquals(NotificationAction.CANCEL, lifecycle.onEngineDestroyed(secondEngine, isPreview = false))
    }

    @Test
    fun `destroyed engine metadata cannot overwrite a surviving engine notification`() {
        val lifecycle = PersistentNotificationLifecycle()
        val destroyedEngine = Any()
        val survivingEngine = Any()
        lifecycle.onPreferenceChanged(enabled = true)
        lifecycle.onEngineCreated(destroyedEngine, isPreview = false)
        lifecycle.onEngineCreated(survivingEngine, isPreview = false)
        lifecycle.onEngineDestroyed(destroyedEngine, isPreview = false)

        assertNull(lifecycle.onMetadataChanged(destroyedEngine, isPreview = false))
        assertEquals(NotificationAction.SHOW_OR_UPDATE, lifecycle.onMetadataChanged(survivingEngine, isPreview = false))
    }

    @Test
    fun `duplicate engine teardown cannot consume another engine`() {
        val lifecycle = PersistentNotificationLifecycle()
        val firstEngine = Any()
        val secondEngine = Any()
        lifecycle.onPreferenceChanged(enabled = true)
        lifecycle.onEngineCreated(firstEngine, isPreview = false)
        lifecycle.onEngineCreated(secondEngine, isPreview = false)

        assertEquals(NotificationAction.NONE, lifecycle.onEngineDestroyed(firstEngine, isPreview = false))
        assertEquals(NotificationAction.NONE, lifecycle.onEngineDestroyed(firstEngine, isPreview = false))
        assertEquals(NotificationAction.CANCEL, lifecycle.onEngineDestroyed(secondEngine, isPreview = false))
    }
}
