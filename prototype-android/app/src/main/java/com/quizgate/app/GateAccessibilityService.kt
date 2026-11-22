package com.quizgate.app

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.*

/** Package names only, never window text or content. No clicks or settings automation. */
class GateAccessibilityService : AccessibilityService() {
    companion object { internal var connected: GateAccessibilityService? = null }
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: ScrollView? = null
    private var foreground = ""
    private var launchers = emptySet<String>()
    private var launchable = emptySet<String>()
    private var dialer = ""
    private var keyboards = emptySet<String>()
    internal fun observeOwnUi() { if (SessionManager.sessionActive) foreground = packageName }
    internal fun sessionStarted() { foreground = packageName; startCallObserver(); handler.removeCallbacks(tick); handler.post(tick) }
    private var releaseAt = 0L
    private var answered = false
    private var message: TextView? = null
    private var observedCallState = -1
    private var callObserver: Any? = null
    private class ModernCallObserver(val changed: (Int) -> Unit) : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) { changed(state) }
    }
    private fun recordCallState(state: Int) {
        observedCallState = state
        if (state != TelephonyManager.CALL_STATE_IDLE) { hideGate(); android.util.Log.i("QuizGateSafety", "Call state is non-idle; gate paused") }
    }
    @Suppress("DEPRECATION")
    private fun startCallObserver() {
        stopCallObserver()
        observedCallState = -1
        try {
            val tm = getSystemService(TelephonyManager::class.java)
            if (Build.VERSION.SDK_INT >= 31) {
                val listener = ModernCallObserver { recordCallState(it) }
                tm.registerTelephonyCallback(mainExecutor, listener)
                callObserver = listener
            } else {
                val listener = object: PhoneStateListener() {
                    override fun onCallStateChanged(state: Int, ignoredNumber: String?) { recordCallState(state) }
                }
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                callObserver = listener
            }
        } catch (e: RuntimeException) { observedCallState = -1; SessionManager.endSession(this) }
    }
    @Suppress("DEPRECATION")
    private fun stopCallObserver() {
        try {
            val tm = getSystemService(TelephonyManager::class.java)
            val listener = callObserver
            if (Build.VERSION.SDK_INT >= 31 && listener is ModernCallObserver) tm.unregisterTelephonyCallback(listener)
            else if (listener is PhoneStateListener) tm.listen(listener, PhoneStateListener.LISTEN_NONE)
        } catch (_: RuntimeException) { }
        callObserver = null; observedCallState = -1
    }
    private val never = setOf("com.android.systemui", "com.android.phone", "com.android.server.telecom", "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer", "com.android.incallui", "com.samsung.android.incallui", "com.android.emergency", "com.android.settings", "com.android.permissioncontroller", "com.google.android.permissioncontroller")
    private val tick = object : Runnable {
        override fun run() {
            if (!SessionManager.sessionActive) { hideGate(); stopCallObserver(); foreground = ""; return }
            if (!Settings.canDrawOverlays(this@GateAccessibilityService) || checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) SessionManager.endSession(this@GateAccessibilityService)
            if (!SessionManager.sessionActive || !isSafe()) hideGate()
            else if (overlay == null && SessionManager.isDue()) showGate()
            if (overlay != null && releaseAt > 0) {
                val remaining = releaseAt - SystemClock.elapsedRealtime()
                if (remaining <= 0) releaseGate()
                else message?.contentDescription = "Explanation. Unlocking in ${(remaining + 999) / 1000} seconds"
            }
            handler.postDelayed(this, 250)
        }
    }
    override fun onServiceConnected() {
        connected = this
        keyboards = getSystemService(android.view.inputmethod.InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }.toSet()
        launchers = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
        launchable = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet() + packageName
        dialer = getSystemService(TelecomManager::class.java)?.defaultDialerPackage ?: ""
        handler.removeCallbacks(tick); handler.post(tick)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!SessionManager.sessionActive || event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Ignore our overlay's own events, while preserving the underlying app.
        if (pkg == packageName && overlay != null) return
        if (pkg in keyboards) return
        foreground = pkg
        if (!isSafe()) hideGate()
    }
    private fun isSafe(): Boolean {
        if (!Settings.canDrawOverlays(this)) return false
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return false
        if (!getSystemService(PowerManager::class.java).isInteractive) return false
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return false
        if (foreground.isEmpty() || foreground in never || foreground in launchers || foreground == dialer || foreground !in launchable) return false
        return observedCallState == TelephonyManager.CALL_STATE_IDLE
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(text: String, size: Float = 16f) = TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.rgb(37,31,33)); setPadding(0,dp(8),0,dp(8)) }
    private fun showGate() {
        if (!SessionManager.sessionActive || !isSafe()) return
        android.util.Log.i("QuizGateTimer", "Gate shown at=${SystemClock.elapsedRealtime()}")
        val q = SessionManager.nextQuestion(this)
        answered = false; releaseAt = 0
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24),dp(16),dp(24),dp(24)); setBackgroundColor(Color.WHITE) }
        root.addView(label("${SessionManager.topic} study check", 14f))
        val end = Button(this).apply { text = "End session, exit now"; setOnClickListener { SessionManager.endSession(this@GateAccessibilityService); hideGate() } }
        root.addView(end)
        root.addView(label(q.q,20f))
        val buttons = mutableListOf<Button>()
        val feedback = label("")
        q.options.forEachIndexed { i, option ->
            val button = Button(this).apply { text = option; isAllCaps = false; minimumHeight = dp(48) }
            buttons.add(button)
            button.setOnClickListener {
                if (!answered) {
                    answered = true
                    buttons.forEach { it.isEnabled = false }
                    SessionManager.recordAnswer(i == q.answer)
                    if (i == q.answer) releaseGate()
                    else {
                        feedback.text = "Correct answer: ${q.options[q.answer]}\n\n${q.why}\n\nUnlocking automatically after 10 seconds."
                        releaseAt = SystemClock.elapsedRealtime() + 10000
                    }
                }
            }
            root.addView(button)
        }
        root.addView(feedback); message = feedback
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.WHITE); addView(root) }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP }
        try { getSystemService(WindowManager::class.java).addView(scroll,params); overlay = scroll } catch (e: RuntimeException) { android.util.Log.e("QuizGate", "Overlay failed", e); SessionManager.endSession(this) }
    }
    private fun releaseGate() { hideGate(); SessionManager.scheduleNext(this) }
    private fun hideGate() {
        overlay?.let { try { getSystemService(WindowManager::class.java).removeView(it) } catch (_: RuntimeException) {} }
        overlay = null; message = null; releaseAt = 0; answered = false
    }
    override fun onInterrupt() { SessionManager.endSession(this); hideGate(); stopCallObserver() }
    override fun onDestroy() { connected = null; stopCallObserver(); handler.removeCallbacksAndMessages(null); SessionManager.endSession(this); hideGate(); super.onDestroy() }
}
