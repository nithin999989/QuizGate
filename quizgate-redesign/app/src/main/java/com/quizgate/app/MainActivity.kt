package com.quizgate.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.OpenableColumns
import android.provider.DocumentsContract
import android.view.View
import android.webkit.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** In-app practice flow plus the controls for the voluntary phone-wide session. */
class MainActivity : Activity() {
    private lateinit var web:WebView
    private var loaded=false
    private var all=emptyList<PracticeQuestion>()
    private var selectedTopic="Spring Boot"
    private var interval=5
    private var right=0
    private var wrong=0
    private var queue=mutableListOf<PracticeQuestion>()
    private var queueKey=""
    private var current:PracticeQuestion?=null
    private var answered=false
    private var remaining=0
    private var countdown:CountDownTimer?=null
    private var selectedSkills=mutableSetOf<String>()
    private var projects=emptyList<String>()
    private var hasProfile=false
    private var projectIndex=0
    private var sourceName=""
    private var lastNote=""
    private val worker=Executors.newSingleThreadExecutor()
    private var readJob:Future<*>?=null
    private var screen="home"
    private var readGeneration=0

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val canvas=Color.rgb(253,252,251)
        window.statusBarColor=canvas;window.navigationBarColor=canvas
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        selectedTopic=getPreferences(0).getString("topic","Spring Boot") ?: "Spring Boot"
        interval=getPreferences(0).getInt("interval",5).takeIf{it in listOf(5,10,15)} ?: 5
        all=QuestionBank.load(assets)
        WebView.setWebContentsDebuggingEnabled(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
        web=WebView(this)
        web.setBackgroundColor(canvas)
        web.settings.apply {
            javaScriptEnabled=true
            allowFileAccess=false
            allowContentAccess=false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs=false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs=false
            blockNetworkLoads=true
            domStorageEnabled=false
            databaseEnabled=false
            cacheMode=WebSettings.LOAD_NO_CACHE
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
        }
        web.webViewClient=object:WebViewClient(){
            override fun onPageFinished(view:WebView,url:String){if(url=="file:///android_asset/index.html"){loaded=true;render()}}
            override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=true
        }
        web.addJavascriptInterface(Bridge(),"LocalApp")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }
    inner class Bridge {
        @JavascriptInterface fun send(action:String,payload:String){
            if(payload.length>16000)return
            runOnUiThread {
                if(isDestroyed)return@runOnUiThread
                val p=try{JSONObject(payload)}catch(_:Exception){JSONObject()}
                when(action){
                    "topic"->{val t=p.optString("value");if(t in listOf("Spring Boot","Microservices","Redis")){selectedTopic=t;saveChoices();render()}}
                    "interval"->{val n=p.optInt("value");if(n in listOf(5,10,15)){interval=n;saveChoices();render()}}
                    "preview"->showQuestion(setOf(selectedTopic))
                    "answer"->answer(p.optInt("index",-1))
                    "end"->endPractice()
                    "startSession"->startPhoneWideSession()
                    "endSession"->stopPhoneWideSession()
                    "pick"->chooseResume()
                    "review"->{if(hasProfile){screen="review";render()}}
                    "skill"->{val skill=p.optString("value");if(hasProfile&&skill in ResumeMatcher.skills){if(!selectedSkills.add(skill))selectedSkills.remove(skill);render()}}
                    "projects"->{if(hasProfile){projects=ResumeMatcher.reviewedProjects(p.optString("value"));render()}}
                    "profile"->{if(hasProfile){screen="profile";render()}}
                    "matched"->{if(hasProfile)showQuestion(ResumeMatcher.topicsFor(selectedSkills))}
                    "project"->{if(hasProfile&&projects.isNotEmpty()){projectIndex=0;screen="project";render()}}
                    "nextProject"->{projectIndex++;render()}
                    "clear"->clearProfile()
                    "licenses"->{screen="licenses";render()}
                }
            }
        }
    }
    private fun saveChoices(){getPreferences(0).edit().putString("topic",selectedTopic).putInt("interval",interval).apply()}
    private fun render(){
        if(!loaded||isDestroyed)return
        val topics=ResumeMatcher.topicsFor(selectedSkills)
        val state=JSONObject().put("screen",screen).put("selectedTopic",selectedTopic).put("interval",interval)
            .put("right",right).put("wrong",wrong).put("note",lastNote).put("hasProfile",hasProfile)
            .put("skills",JSONArray(selectedSkills.toList())).put("allSkills",JSONArray(ResumeMatcher.skills.keys.toList()))
            .put("projects",JSONArray(projects)).put("sourceName",sourceName)
            .put("matchedTopics",JSONArray(topics.toList())).put("matchedCount",all.count{it.topic in topics})
            .put("answered",answered).put("remaining",remaining).put("phoneWide",QuizSessionService.running)
        current?.let{q->state.put("question",JSONObject().put("id",q.id).put("topic",q.topic).put("level",q.level)
            .put("q",q.question).put("options",JSONArray(q.options)).put("followUp",q.followUp).put("sources",JSONArray(q.sources))
            .put("explanation",if(answered)q.explanation else "").put("correctAnswer",if(answered)q.options[q.answer] else ""))}
        val prompts=ResumeMatcher.projectPrompts(projects)
        state.put("promptCount",prompts.size)
        if(prompts.isNotEmpty()){
            val p=prompts[projectIndex%prompts.size]
            state.put("projectPrompt",p.first).put("projectChecklist",p.second).put("projectPosition",projectIndex%prompts.size+1)
        }
        if(screen=="licenses")state.put("licenses",listOf("newsreader","inter").joinToString("\n\n"){assets.open("fonts/$it-OFL.txt").bufferedReader().use{r->r.readText()}})
        val json=state.toString().replace("\u2028","\\u2028").replace("\u2029","\\u2029")
        web.evaluateJavascript("window.setState($json)",null)
    }
    private fun endPractice(){
        readGeneration++;readJob?.cancel(true);countdown?.cancel();countdown=null
        current=null;answered=false;remaining=0;screen="home"
        lastNote="Practice closed.";render()
    }
    private fun showQuestion(topics:Set<String>){
        countdown?.cancel();countdown=null
        val key=topics.sorted().joinToString("|")
        if(queue.isEmpty()||queueKey!=key){queue=all.filter{it.topic in topics}.shuffled().toMutableList();queueKey=key}
        if(queue.isEmpty()){lastNote="No matching questions. Review the selected skills.";screen="home";render();return}
        current=queue.removeAt(0);answered=false;remaining=0;screen="question";render()
    }
    private fun answer(index:Int){
        val q=current ?: return
        if(screen!="question"||answered||index !in 0..3)return
        answered=true
        if(index==q.answer){right++;lastNote="Correct. Practice returned immediately.";current=null;screen="home";render()}
        else {
            wrong++;remaining=10;render()
            countdown=object:CountDownTimer(10000,1000){
                override fun onTick(ms:Long){remaining=((ms+999)/1000).toInt();render()}
                override fun onFinish(){lastNote="Explanation complete. Practice returned automatically.";current=null;screen="home";remaining=0;render()}
            }.start()
        }
    }
    private fun chooseResume(){
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*";putExtra(Intent.EXTRA_MIME_TYPES,arrayOf("application/pdf","text/plain"));putExtra(DocumentsContract.EXTRA_INITIAL_URI,DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:Download"));addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        startActivityForResult(intent,40)
    }
    @Deprecated("Android activity callback retained for minSdk 26")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=40||resultCode!=RESULT_OK)return
        val uri=data?.data ?: return
        val generation=++readGeneration
        val pendingSourceName=try{contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0).take(120) else null} ?: "Selected resume"}catch(_:Exception){"Selected resume"}
        countdown?.cancel();screen="reading";render()
        readJob=worker.submit{
            try{
                val extracted=ResumeReader.read(applicationContext,uri)
                val found=ResumeMatcher.detectSkills(extracted)
                val titles=ResumeMatcher.projectCandidates(extracted)
                runOnUiThread{if(!isDestroyed&&generation==readGeneration&&screen=="reading"){sourceName=pendingSourceName;selectedSkills=found.toMutableSet();projects=titles;hasProfile=true;screen="review";render()}}
            }catch(e:Exception){runOnUiThread{if(!isDestroyed&&generation==readGeneration&&screen=="reading"){
                lastNote=when(e){is IllegalArgumentException->e.message ?: "Choose a readable PDF/text file.";is SecurityException->"Cannot read this protected document. Choose an unprotected PDF or text file.";else->"Could not read this file locally. Try a smaller, clearer PDF or plain text."}
                screen="home";render()
            }}}
        }
    }
    private fun clearProfile(){
        readGeneration++;readJob?.cancel(true);selectedSkills.clear();projects=emptyList();sourceName="";hasProfile=false
        queue.clear();queueKey="";lastNote="Resume details cleared. No resume file is retained by the app.";screen="home";render()
    }
    @Deprecated("Back behavior is explicit for the development flow")
    override fun onBackPressed(){if(screen=="home")super.onBackPressed() else endPractice()}
    override fun onResume(){super.onResume();render()}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==41)render()}
    private fun startPhoneWideSession(){
        if(!android.provider.Settings.canDrawOverlays(this)){
            lastNote="Allow 'Display over other apps' for QuizGate, come back, then start again."
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:$packageName")))
            render();return
        }
        if(android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            lastNote="Allow notifications so session controls stay visible, then start again."
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),41)
            render();return
        }
        try{
            QuizSessionService.start(this,selectedTopic,interval)
        }catch(e:Exception){
            lastNote="Could not start the session ("+e.javaClass.simpleName+"). Nothing is running."
            render();return
        }
        lastNote="Phone-wide session starting. A question appears every $interval minutes until you end it."
        render()
        watchServiceState(true)
    }

    // Re-render only when the service's real running flag reaches the wanted state (bounded: ~6s),
    // so the home never claims ON/OFF before the service actually is, however slow the device.
    private var stateWatch:Runnable?=null
    private fun watchServiceState(want:Boolean){
        stateWatch?.let{web.removeCallbacks(it)}
        var tries=0
        val r=object:Runnable{override fun run(){
            if(isDestroyed)return
            if(QuizSessionService.running==want){render();return}
            if(++tries<=24)web.postDelayed(this,250) else render()
        }}
        stateWatch=r
        web.postDelayed(r,250)
    }
    private fun stopPhoneWideSession(){QuizSessionService.stop(this);lastNote="Phone-wide session ending.";render();watchServiceState(false)}
    override fun onDestroy(){loaded=false;countdown?.cancel();readGeneration++;readJob?.cancel(true);worker.shutdownNow();web.removeJavascriptInterface("LocalApp");web.destroy();super.onDestroy()}
}
