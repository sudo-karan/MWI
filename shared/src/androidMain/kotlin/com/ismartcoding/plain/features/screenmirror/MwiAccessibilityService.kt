package com.ismartcoding.plain.features.screenmirror

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Remote control via AccessibilityService (spec §8): normalized taps/swipes/scrolls become
 * `dispatchGesture` strokes; Back/Home/Recents/Lock use `performGlobalAction`; typing edits the
 * focused editable node (SET_TEXT). No `INJECT_EVENTS`/root required.
 */
class MwiAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        ScreenMirror.controlEnabled.value = true
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            ScreenMirror.controlEnabled.value = false
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* content retrieval unused for mirroring */ }

    override fun onInterrupt() {}

    private fun handle(c: ScreenMirrorControl): Boolean {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        return when (c.type) {
            "tap" -> gesture(px(c.x, w), py(c.y, h), px(c.x, w), py(c.y, h), 50)
            "longpress" -> gesture(px(c.x, w), py(c.y, h), px(c.x, w), py(c.y, h), 600)
            "swipe", "scroll" -> gesture(
                px(c.x, w), py(c.y, h), px(c.x2, w), py(c.y2, h),
                if (c.durationMs > 0) c.durationMs else 250,
            )
            "key" -> globalKey(c.key)
            "text" -> typeText(c.text)
            else -> false
        }
    }

    private fun gesture(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun globalKey(key: String): Boolean = when (key) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "lock" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else false
        "enter" -> imeEnter()
        else -> false
    }

    private fun focusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
    }

    private fun typeText(text: String): Boolean {
        val node = focusedEditable() ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun imeEnter(): Boolean {
        val node = focusedEditable() ?: return false
        // Best-effort: trigger the editor action on the focused field.
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun px(norm: Float, size: Int) = ScreenGeometry.toPixel(norm, size)
    private fun py(norm: Float, size: Int) = ScreenGeometry.toPixel(norm, size)

    companion object {
        @Volatile
        private var instance: MwiAccessibilityService? = null

        fun dispatch(control: ScreenMirrorControl): Boolean = instance?.handle(control) ?: false
    }
}
