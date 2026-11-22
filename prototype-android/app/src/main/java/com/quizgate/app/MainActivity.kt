package com.quizgate.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.graphics.Color
import android.widget.*

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var topics: Spinner
    private lateinit var intervals: Spinner
    private var accepted = false
    private val update = object: Runnable { override fun run() { refresh(); handler.postDelayed(this,1000) } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        accepted = getPreferences(0).getBoolean("disclosureAccepted",false)
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(20),dp(24),dp(24)); setBackgroundColor(Color.WHITE) }
        fun text(value: String, size: Float=16f) = TextView(this).apply { text=value; textSize=size; setPadding(0,dp(8),0,dp(8)) }
        fun button(value: String, action: () -> Unit) { root.addView(Button(this).apply { text=value; isAllCaps=false; minimumHeight=dp(48); setOnClickListener { action() } }) }
        root.addView(text("QuizGate",28f))
        root.addView(text("Turn scrolling into interview practice. Offline, no account, no ads."))
        root.addView(text("Choose a topic"))
        topics=Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Java","Python","Data science")) }
        root.addView(topics)
        root.addView(text("Question interval"))
        intervals = Spinner(this).apply { adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("5 minutes","10 minutes","15 minutes")) }
        root.addView(intervals)
        root.addView(text("One question per selected interval while ON. Correct: unlock. Wrong: explanation for 10 seconds, then auto-unlock."))
        button("1. Read privacy notice and agree") { disclosure() }
        button("2. Allow display over other apps") { if (accepted) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) else disclosure() }
        button("3. Allow call status safety check") { if (accepted) requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE),11) else disclosure() }
        button("4. Enable QuizGate study watcher") { if (accepted) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) else disclosure() }
        button("Turn ON study session") { startSession(longArrayOf(300000L,600000L,900000L)[intervals.selectedItemPosition]) }
        button("End session, turn OFF") { SessionManager.endSession(this); refresh() }
        button("Preview a question in 10 seconds") { startSession(10000L) }
        root.addView(text("Preview uses a temporary 10-second interval. Turn OFF, then choose your interval and turn ON for normal use. Home, notifications and End session remain available. Calls, lock screen and Settings pause the quiz. Android may stop background services; after a restart, the session stays OFF." ,14f))
        status=text(""); root.addView(status)
        setContentView(ScrollView(this).apply { addView(root) })
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun disclosure() {
        AlertDialog.Builder(this).setTitle("What QuizGate can see")
            .setMessage("During a study session, the Accessibility service reads only the app package name so QuizGate knows when to hide quizzes for calls, Settings and system screens. It does not read messages, screen text, passwords or keystrokes, and never clicks anything for you. Display-over-apps lets a quiz cover the current app. Call-status permission checks only whether a call is active, not call history or audio. Nothing is sent anywhere. No internet permission. You can end a session, disable permissions or uninstall anytime. These permissions are optional, but sessions cannot start without them.")
            .setNegativeButton("Decline",null).setPositiveButton("I agree") { _,_ -> accepted=true; getPreferences(0).edit().putBoolean("disclosureAccepted",true).apply(); refresh() }.show()
    }
    private fun watcherEnabled():Boolean {
        val component = ComponentName(this,GateAccessibilityService::class.java)
        return Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')?.any { ComponentName.unflattenFromString(it) == component } ?: false
    }
    private fun ready() = GateAccessibilityService.connected != null && accepted && Settings.canDrawOverlays(this) && watcherEnabled() && checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED
    private fun startSession(interval:Long) {
        if (!ready()) { AlertDialog.Builder(this).setTitle("Complete setup first").setMessage("Read and accept the privacy notice, then enable the three permissions above. QuizGate will not start until call-safety checks and the watcher are available.").setPositiveButton("OK",null).show(); return }
        SessionManager.startSession(this,topics.selectedItem.toString(),interval)
        GateAccessibilityService.connected?.sessionStarted()
        refresh()
    }
    private fun refresh() {
        if (!::status.isInitialized) return
        val seconds = ((SessionManager.dueAt-SystemClock.elapsedRealtime()).coerceAtLeast(0)+999)/1000
        status.text=if (SessionManager.sessionActive) "ON: ${SessionManager.topic}\nNext question in ${seconds}s\nRight ${SessionManager.rightCount}, wrong ${SessionManager.wrongCount}" else "OFF. Setup ${if (ready()) "ready" else "incomplete"}. No quiz interruptions."
    }
    override fun onResume() { super.onResume(); handler.post(update) }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); if (hasFocus) GateAccessibilityService.connected?.observeOwnUi() }
    override fun onPause() { handler.removeCallbacks(update); super.onPause() }
}
