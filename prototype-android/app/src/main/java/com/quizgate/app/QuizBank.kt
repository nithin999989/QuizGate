package com.quizgate.app

import android.content.Context
import org.json.JSONObject

data class Question(val q: String, val options: List<String>, val answer: Int, val why: String)

/** Loads the bundled question bank for a topic from assets/questions.json. */
object QuizBank {
    fun load(context: Context, topic: String): List<Question> {
        val json = context.assets.open("questions.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).getJSONArray(topic)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val opts = o.getJSONArray("options")
            Question(
                q = o.getString("q"),
                options = (0 until opts.length()).map { opts.getString(it) },
                answer = o.getInt("answer"),
                why = o.getString("why")
            )
        }
    }
}
