package com.quizgate.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Voluntary phone-wide quiz session.
 *
 * Runs only after the user starts it from the app and only while "Display over
 * other apps" is granted. Least privilege by design: no accessibility service,
 * no usage access, no phone-state access. Exit is always available from the
 * popup, from the persistent notification, and from the app. The popup is a
 * normal overlay window: it never covers the status bar, and touches outside
 * it pass through to the app underneath.
 */
class QuizSessionService:Service(){
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var wm:WindowManager
    private var overlay:View?=null
    private var popupWeb:WebView?=null
    private var popupLoaded=false
    private var topic="Spring Boot"
    private var intervalMin=5
    private var timerMs=300000L
    private var bank=emptyList<PracticeQuestion>()
    private var queue=mutableListOf<PracticeQuestion>()
    private var current:PracticeQuestion?=null
    private var answered=false
    private var remaining=0
    private var right=0
    private var wrong=0
    private var popupRunnable:Runnable?=null
    private var tickRunnable:Runnable?=null

    override fun onBind(intent:Intent?)=null

    override fun onCreate(){
        super.onCreate()
        wm=getSystemService(Context.WINDOW_SERVICE) as WindowManager
        bank=QuestionBank.load(assets)
        running=true
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==ACTION_END){stopSession();return START_NOT_STICKY}
        topic=intent?.getStringExtra(EXTRA_TOPIC)?.takeIf{it in QuestionBank.topics} ?: topic
        intervalMin=intent?.getIntExtra(EXTRA_INTERVAL_MIN,intervalMin)?.takeIf{it in listOf(5,10,15)} ?: intervalMin
        val debuggable=(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
        val debugSeconds=intent?.getIntExtra(EXTRA_DEBUG_SECONDS,0) ?: 0
        timerMs=if(debuggable&&debugSeconds in 5..3600)debugSeconds*1000L else intervalMin*60000L
        startForegroundWithNotification()
        reshuffle()
        scheduleNext(timerMs)
        return START_NOT_STICKY
    }

    private fun reshuffle(){queue=bank.filter{it.topic==topic}.shuffled().toMutableList()}

    private fun startForegroundWithNotification(){
        val nm=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel(CHANNEL_ID,"Quiz session",NotificationManager.IMPORTANCE_LOW))
        val openApp=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val end=PendingIntent.getService(this,1,Intent(this,QuizSessionService::class.java).setAction(ACTION_END),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder=if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,CHANNEL_ID) else @Suppress("DEPRECATION")Notification.Builder(this)
        val notification=builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("QuizGate session on")
            .setContentText("A question appears over other apps every $intervalMin min. End it whenever you choose.")
            .setContentIntent(openApp)
            .addAction(Notification.Action.Builder(null,"End session",end).build())
            .setOngoing(true)
            .build()
        if(Build.VERSION.SDK_INT>=34)startForeground(NOTIFICATION_ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID,notification)
    }

    private fun scheduleNext(delayMs:Long){
        popupRunnable?.let{handler.removeCallbacks(it)}
        val r=Runnable{showPopup()}
        popupRunnable=r
        handler.postDelayed(r,delayMs)
    }

    private fun showPopup(){
        if(!running)return
        if(!Settings.canDrawOverlays(this)){stopSession();return}
        if(overlay!=null)return
        if(queue.isEmpty())reshuffle()
        current=queue.removeAt(0);answered=false;remaining=0
        val canvas=Color.rgb(253,252,251)
        val container=FrameLayout(this)
        container.setBackgroundColor(canvas)
        container.elevation=8*resources.displayMetrics.density
        val view=WebView(this)
        view.setBackgroundColor(canvas)
        view.settings.apply{
            javaScriptEnabled=true
            allowFileAccess=false
            allowContentAccess=false
            @Suppress("DEPRECATION")allowFileAccessFromFileURLs=false
            @Suppress("DEPRECATION")allowUniversalAccessFromFileURLs=false
            blockNetworkLoads=true
            domStorageEnabled=false
            databaseEnabled=false
            cacheMode=WebSettings.LOAD_NO_CACHE
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        view.webViewClient=object:WebViewClient(){
            override fun onPageFinished(v:WebView,url:String){if(url=="file:///android_asset/index.html"){popupLoaded=true;renderPopup()}}
            override fun shouldOverrideUrlLoading(v:WebView,request:WebResourceRequest)=true
        }
        view.addJavascriptInterface(PopupBridge(),"LocalApp")
        container.addView(view,FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
        val dm=resources.displayMetrics
        val margin=(14*dm.density).toInt()
        val statusBarId=resources.getIdentifier("status_bar_height","dimen","android")
        val statusBar=if(statusBarId>0)resources.getDimensionPixelSize(statusBarId) else 0
        val params=WindowManager.LayoutParams(
            dm.widthPixels-2*margin,
            (dm.heightPixels*92)/100,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        params.gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y=statusBar+margin
        popupLoaded=false
        popupWeb=view
        try{
            wm.addView(container,params)
        }catch(e:RuntimeException){
            popupWeb=null
            view.removeJavascriptInterface("LocalApp")
            view.destroy()
            stopSession()
            return
        }
        overlay=container
        view.loadUrl("file:///android_asset/index.html")
    }

    private fun renderPopup(){
        val view=popupWeb ?: return
        if(!popupLoaded)return
        val state=JSONObject().put("screen","question").put("selectedTopic",topic).put("interval",intervalMin)
            .put("right",right).put("wrong",wrong).put("note","").put("hasProfile",false).put("phoneWide",true)
            .put("answered",answered).put("remaining",remaining)
        current?.let{q->state.put("question",JSONObject().put("id",q.id).put("topic",q.topic).put("level",q.level)
            .put("q",q.question).put("options",JSONArray(q.options)).put("followUp",q.followUp).put("sources",JSONArray(q.sources))
            .put("explanation",if(answered)q.explanation else "").put("correctAnswer",if(answered)q.options[q.answer] else ""))}
        val json=state.toString().replace("\u2028","\\u2028").replace("\u2029","\\u2029")
        view.evaluateJavascript("window.setState($json)",null)
    }

    private fun answer(index:Int){
        val q=current ?: return
        if(answered||index !in 0..3)return
        answered=true
        if(index==q.answer){right++;dismissPopup();scheduleNext(timerMs)}
        else{
            wrong++;remaining=10;renderPopup()
            val tick=object:Runnable{override fun run(){
                remaining--
                if(remaining<=0){dismissPopup();scheduleNext(timerMs)}
                else{renderPopup();handler.postDelayed(this,1000)}
            }}
            tickRunnable=tick
            handler.postDelayed(tick,1000)
        }
    }

    private fun dismissPopup(){
        tickRunnable?.let{handler.removeCallbacks(it)};tickRunnable=null
        popupWeb?.let{it.removeJavascriptInterface("LocalApp");it.destroy()}
        popupWeb=null;popupLoaded=false
        overlay?.let{try{wm.removeView(it)}catch(_:Exception){}}
        overlay=null
        current=null;answered=false;remaining=0
    }

    private fun stopSession(){
        popupRunnable?.let{handler.removeCallbacks(it)};popupRunnable=null
        dismissPopup()
        running=false
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy(){
        popupRunnable?.let{handler.removeCallbacks(it)}
        dismissPopup()
        running=false
        super.onDestroy()
    }

    inner class PopupBridge{
        @JavascriptInterface fun send(action:String,payload:String){
            if(payload.length>16000)return
            handler.post{
                val p=try{JSONObject(payload)}catch(_:Exception){JSONObject()}
                when(action){
                    "answer"->answer(p.optInt("index",-1))
                    "end"->stopSession()
                }
            }
        }
    }

    companion object{
        @Volatile var running=false
            private set
        const val ACTION_END="com.quizgate.app.END_SESSION"
        const val EXTRA_TOPIC="topic"
        const val EXTRA_INTERVAL_MIN="intervalMin"
        const val EXTRA_DEBUG_SECONDS="debugSeconds"
        private const val CHANNEL_ID="quizgate_session"
        private const val NOTIFICATION_ID=7
        fun start(context:Context,topic:String,intervalMin:Int){
            context.startForegroundService(Intent(context,QuizSessionService::class.java).putExtra(EXTRA_TOPIC,topic).putExtra(EXTRA_INTERVAL_MIN,intervalMin))
        }
        fun stop(context:Context){
            context.startService(Intent(context,QuizSessionService::class.java).setAction(ACTION_END))
        }
    }
}
