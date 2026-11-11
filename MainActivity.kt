package com.sufi.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var chat: TextView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private val client = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val model = "gemini-2.5-flash"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        chat = findViewById(R.id.chat); input = findViewById(R.id.input); status = findViewById(R.id.status)
        tts = TextToSpeech(this, this)
        findViewById<Button>(R.id.send).setOnClickListener { sendMessage() }
        findViewById<Button>(R.id.voice).setOnClickListener { startSpeechInput() }
        findViewById<Button>(R.id.youtube).setOnClickListener { openApp("com.google.android.youtube") }
    }

    private fun append(line: String) { chat.append("\n\n$line") }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        input.setText(""); append("YOU: $prompt")
        when {
            prompt.contains("open youtube", true) || prompt.contains("youtube kholo", true) -> { openApp("com.google.android.youtube"); reply("YouTube kholne ki koshish kar rahi hoon."); return }
            prompt.contains("open chrome", true) || prompt.contains("chrome kholo", true) -> { openApp("com.android.chrome"); reply("Chrome kholne ki koshish kar rahi hoon."); return }
        }
        if (BuildConfig.GEMINI_API_KEY.isBlank()) { reply("Gemini API key set nahi hai. README mein diye gaye steps follow karein."); return }
        status.text = "THINKING..."
        val part = JSONObject().put("text", "You are SUFI, a helpful professional female AI assistant. Reply naturally in Hindi, Hinglish or English according to the user. Be concise, safe and honest. User says: $prompt")
        val bodyJson = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(part))))
        val requestBody = bodyJson.toString().toRequestBody(jsonType)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
        val request = Request.Builder().url(url).post(requestBody).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { runOnUiThread { status.text = "ONLINE • READY"; reply("Network error: ${e.localizedMessage ?: "connection failed"}") } }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val raw = it.body?.string().orEmpty()
                    val answer = try { JSONObject(raw).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text") }
                    catch (_: Exception) { "AI response nahi mil saka (HTTP ${it.code}). API key aur Gemini access check karein." }
                    runOnUiThread { status.text = "ONLINE • READY"; reply(answer) }
                }
            }
        })
    }

    private fun reply(message: String) { append("SUFI: $message"); tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "sufi_reply") }

    private fun startSpeechInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 41); return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN"); putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to SUFI")
        }
        try { startActivityForResult(intent, 42) } catch (_: Exception) { Toast.makeText(this, "Speech recognition unavailable", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Uses activity result for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == Activity.RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) { input.setText(spoken); sendMessage() }
        }
    }

    private fun openApp(packageName: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) startActivity(launch) else Toast.makeText(this, "App nahi mila", Toast.LENGTH_SHORT).show()
    }
    override fun onInit(result: Int) { if (result == TextToSpeech.SUCCESS) { tts?.language = Locale("hi", "IN"); tts?.setSpeechRate(0.96f) } }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); client.dispatcher.executorService.shutdown(); super.onDestroy() }
}
