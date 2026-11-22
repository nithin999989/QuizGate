package com.quizgate.app

import android.content.Context
import android.os.SystemClock

/** Session state is deliberately not restored after process death or reboot. */
object SessionManager {
    var sessionActive = false; private set
    var topic = "Java"; private set
    var intervalMillis = 300000L; private set
    var dueAt = 0L; private set
    var rightCount = 0; private set
    var wrongCount = 0; private set
    private var queue = mutableListOf<Question>()
    fun startSession(context: Context, topic: String, interval: Long = 300000L) {
        this.topic = topic
        intervalMillis = interval
        rightCount = 0; wrongCount = 0
        queue = QuizBank.load(context, topic).shuffled().toMutableList()
        sessionActive = true
        dueAt = SystemClock.elapsedRealtime() + intervalMillis
        android.util.Log.i("QuizGateTimer", "Session start intervalMs=$interval dueAt=$dueAt")
    }
    fun endSession(context: Context) { sessionActive = false; dueAt = 0 }
    fun nextQuestion(context: Context): Question {
        if (queue.isEmpty()) queue = QuizBank.load(context, topic).shuffled().toMutableList()
        return queue.removeAt(0)
    }
    fun recordAnswer(correct: Boolean) { if (correct) rightCount++ else wrongCount++ }
    fun scheduleNext(context: Context) { dueAt = SystemClock.elapsedRealtime() + intervalMillis }
    fun isDue() = sessionActive && SystemClock.elapsedRealtime() >= dueAt
}
