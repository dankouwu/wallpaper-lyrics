package com.dnk.wallpaperlyrics

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

// Holds the settings screens as a stack of pages and moves them the way Pixel settings does: a
// new page slides in from the right over the previous one, which drifts a quarter to the left
// under a scrim. Back reverses it, and the back gesture drags the page right with the finger,
// whichever edge the swipe starts from, so the previous page shows under it.
class MainActivity : AppCompatActivity() {

    private lateinit var container: FrameLayout
    private lateinit var scrim: View
    private lateinit var statusBarBackground: View
    private var barInsets = Insets.NONE
    private val stack = ArrayList<SettingsPage>()
    private var isActivityResumed = false
    private var animator: ValueAnimator? = null
    private var dragging = false
    private var dragProgress = 0f

    // Material emphasized decelerate.
    private val motion = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackStarted(backEvent: BackEventCompat) = startDrag()
        override fun handleOnBackProgressed(backEvent: BackEventCompat) = drag(backEvent.progress)
        override fun handleOnBackCancelled() = cancelDrag()
        override fun handleOnBackPressed() = closeTop()
    }

    private val pageFactories: List<Pair<Class<out SettingsPage>, () -> SettingsPage>> by lazy {
        listOf(
            MainPage::class.java to { MainPage(this) },
            PermissionsPage::class.java to { PermissionsPage(this) },
            BackgroundSettingsPage::class.java to { BackgroundSettingsPage(this) },
            LyricsSettingsPage::class.java to { LyricsSettingsPage(this) },
            EnhancedLyricsPage::class.java to { EnhancedLyricsPage(this) },
            TimingPage::class.java to { TimingPage(this) },
            BluetoothOffsetPage::class.java to { BluetoothOffsetPage(this) },
            VersionPage::class.java to { VersionPage(this) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this)
        scrim = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.GONE
        }
        container.addView(scrim)

        // Pages draw under both system bars, so lists scroll under the gesture bar and the offset
        // preview shows the wallpaper to every edge. Only the status bar keeps a solid background,
        // so scrolled text does not run under the clock.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        statusBarBackground = View(this).apply { setBackgroundColor(PAGE_BACKGROUND) }
        container.addView(statusBarBackground, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0))
        ViewCompat.setOnApplyWindowInsetsListener(container) { _, insets ->
            barInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            statusBarBackground.layoutParams.height = barInsets.top
            statusBarBackground.requestLayout()
            stack.forEach { applyInsets(it.view) }
            WindowInsetsCompat.CONSUMED
        }
        setContentView(container)
        onBackPressedDispatcher.addCallback(this, backCallback)

        // Rotation or a theme change recreates the activity, so the open pages are rebuilt.
        val saved = savedInstanceState?.getIntArray(KEY_PAGES)
        val pages = saved?.toList()?.mapNotNull { pageFactories.getOrNull(it)?.second?.invoke() }
        if (pages.isNullOrEmpty()) {
            addPage(MainPage(this))
        } else {
            pages.forEach { addPage(it) }
        }
        stack.dropLast(1).forEach { it.view.visibility = View.GONE }
        backCallback.isEnabled = stack.size > 1
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(
            KEY_PAGES,
            stack.map { page -> pageFactories.indexOfFirst { it.first == page.javaClass } }.toIntArray()
        )
    }

    private fun addPage(page: SettingsPage) {
        page.onCreate()
        stack.add(page)
        container.addView(page.view, container.indexOfChild(statusBarBackground))
        applyInsets(page.view)
    }

    private fun applyInsets(view: View) {
        view.setPadding(barInsets.left, barInsets.top, barInsets.right, barInsets.bottom)
        (view as? ViewGroup)?.clipToPadding = false
        view.setBackgroundColor(PAGE_BACKGROUND)
    }

    // Hidden while a dialog previews on the wallpaper, which should fill the whole screen.
    fun setStatusBarBackgroundVisible(visible: Boolean) {
        statusBarBackground.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    fun openPage(page: SettingsPage) {
        if (animator?.isRunning == true || dragging) return
        val below = stack.last()
        addPage(page)
        below.onPause()
        below.onStop()
        if (isActivityResumed) page.onResume()
        backCallback.isEnabled = true

        showScrimUnder(page)
        val width = container.width.toFloat()
        page.view.translationX = width
        animate(0f, 1f, OPEN_MS) { f ->
            page.view.translationX = width * (1f - f)
            below.view.translationX = -PARALLAX * width * f
            scrim.alpha = SCRIM * f
        }.doOnEnd {
            below.view.visibility = View.GONE
            below.view.translationX = 0f
            hideScrim()
        }
    }

    fun closePage(page: SettingsPage) {
        if (page === stack.lastOrNull()) closeTop()
    }

    private fun startDrag() {
        if (stack.size < 2 || animator?.isRunning == true) return
        dragging = true
        dragProgress = 0f
        val below = stack[stack.size - 2]
        below.view.visibility = View.VISIBLE
        showScrimUnder(stack.last())
        drag(0f)
    }

    private fun drag(progress: Float) {
        if (!dragging) return
        dragProgress = progress
        setClosing(progress)
    }

    private fun cancelDrag() {
        if (!dragging) return
        dragging = false
        val below = stack[stack.size - 2]
        animate(dragProgress, 0f, (CLOSE_MS * dragProgress).toLong().coerceAtLeast(MIN_MS)) { setClosing(it) }
            .doOnEnd {
                below.view.visibility = View.GONE
                below.view.translationX = 0f
                hideScrim()
            }
    }

    private fun closeTop() {
        if (stack.size < 2) {
            finish()
            return
        }
        if (animator?.isRunning == true && !dragging) return
        val from = if (dragging) dragProgress else 0f
        dragging = false
        val top = stack.last()
        val below = stack[stack.size - 2]
        if (from == 0f) {
            below.view.visibility = View.VISIBLE
            showScrimUnder(top)
        }
        top.isFinishing = true
        top.onPause()
        if (isActivityResumed) below.onResume()

        animate(from, 1f, (CLOSE_MS * (1f - from)).toLong().coerceAtLeast(MIN_MS)) { setClosing(it) }
            .doOnEnd {
                container.removeView(top.view)
                stack.remove(top)
                top.onStop()
                top.onDestroy()
                top.isDestroyed = true
                below.view.translationX = 0f
                hideScrim()
                backCallback.isEnabled = stack.size > 1
            }
    }

    // 0 is the top page in place, 1 is the top page gone off the right edge.
    private fun setClosing(progress: Float) {
        val width = container.width.toFloat()
        stack.last().view.translationX = width * progress
        stack[stack.size - 2].view.translationX = -PARALLAX * width * (1f - progress)
        scrim.alpha = SCRIM * (1f - progress)
    }

    private fun showScrimUnder(top: SettingsPage) {
        container.removeView(scrim)
        container.addView(scrim, container.indexOfChild(top.view))
        scrim.visibility = View.VISIBLE
    }

    private fun hideScrim() {
        scrim.visibility = View.GONE
        scrim.alpha = 0f
    }

    private fun animate(from: Float, to: Float, durationMs: Long, update: (Float) -> Unit): ValueAnimator {
        animator?.cancel()
        return ValueAnimator.ofFloat(from, to).apply {
            duration = durationMs
            interpolator = motion
            addUpdateListener { update(it.animatedValue as Float) }
            animator = this
            start()
        }
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        stack.lastOrNull()?.onResume()
    }

    override fun onPause() {
        super.onPause()
        isActivityResumed = false
        stack.lastOrNull()?.onPause()
    }

    override fun onStop() {
        super.onStop()
        stack.lastOrNull()?.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        animator?.cancel()
        stack.asReversed().forEach {
            it.onDestroy()
            it.isDestroyed = true
        }
    }

    @Deprecated("Pages still use the framework permission request")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        stack.forEach { it.onRequestPermissionsResult(requestCode, permissions, grantResults) }
    }

    private companion object {
        const val KEY_PAGES = "settings_pages"
        const val OPEN_MS = 450L
        const val CLOSE_MS = 400L
        const val MIN_MS = 150L
        const val PARALLAX = 0.25f
        const val SCRIM = 0.3f
        val PAGE_BACKGROUND = Color.parseColor("#242424")
    }
}
