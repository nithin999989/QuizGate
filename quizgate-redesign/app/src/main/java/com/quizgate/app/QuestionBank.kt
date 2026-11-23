package com.quizgate.app

import android.content.res.AssetManager
import org.json.JSONObject

data class PracticeQuestion(val id:String,val topic:String,val level:String,val question:String,val options:List<String>,val answer:Int,val explanation:String,val followUp:String,val sources:List<String>)

object QuestionBank {
    val topics=listOf("Spring Boot","Microservices","Redis")
    fun load(assets:AssetManager):List<PracticeQuestion>{
        val bank=JSONObject(assets.open("interview-bank.json").bufferedReader().use{it.readText()})
        val arr=bank.getJSONArray("questions")
        val all=(0 until arr.length()).map { i ->
            val q=arr.getJSONObject(i);val opts=q.getJSONArray("options");val refs=q.getJSONArray("sources")
            PracticeQuestion(q.getString("id"),q.getString("topic"),q.getString("level"),q.getString("q"),
                (0 until opts.length()).map{opts.getString(it)},q.getInt("answer"),q.getString("why"),q.getString("followUp"),
                (0 until refs.length()).map{bank.getJSONObject("sources").getString(refs.getString(it))})
        }
        require(all.size==30 && all.all{it.options.size==4 && it.answer in 0..3})
        return all
    }
}
