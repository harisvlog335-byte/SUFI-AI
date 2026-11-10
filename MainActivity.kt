package com.sufi.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

private val Bg = Color(0xFF080D16)
private val Panel = Color(0xFF121C2B)
private val Cyan = Color(0xFF43D9E8)
private val White = Color(0xFFEAF4FF)

data class ChatMessage(val text: String, val fromUser: Boolean)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechResult: ((String) -> Unit)? = null

    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startListeningInternal()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
        setContent { SufiScreen(
            speak = { speak(it) },
            listen = { callback ->
                speechResult = callback
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) startListeningInternal()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        ) }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("hi", "IN")
            tts?.setSpeechRate(0.95f)
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sufi_reply")
    }

    private fun startListeningInternal() {
        val recognizer = speechRecognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "SUFI ko boliye...")
        }
        recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                speechResult?.invoke("Voice samajh nahi aayi. Dobara try karo.")
            }
            override fun onResults(results: Bundle?) {
                val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                speechResult?.invoke(words?.firstOrNull().orEmpty())
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SufiScreen(
    speak: (String) -> Unit,
    listen: ((String) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("sufi_settings", 0) }
    var apiKey by remember { mutableStateOf(prefs.getString("gemini_key", "") ?: "") }
    var draft by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf(
            ChatMessage("Assalamualaikum! Main SUFI AI hoon. Bataiye, main aapki kya madad kar sakti hoon?", false)
        )
    }

    fun handleLocalCommand(raw: String): Boolean {
        val q = raw.lowercase(Locale.ROOT)
        if (q.contains("youtube")) {
            val intent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                ?: Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))
            context.startActivity(intent)
            messages.add(ChatMessage("YouTube open kar rahi hoon.", false))
            speak("YouTube open kar rahi hoon.")
            return true
        }
        if (q.contains("chrome")) {
            val intent = context.packageManager.getLaunchIntentForPackage("com.android.chrome")
            if (intent != null) context.startActivity(intent)
            else context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")))
            messages.add(ChatMessage("Browser open karne ki koshish kar rahi hoon.", false))
            speak("Browser open kar rahi hoon.")
            return true
        }
        if (q.contains("time") || q.contains("samay") || q.contains("kitne baje")) {
            val answer = "Abhi " + java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(java.util.Date()) + " baj rahe hain."
            messages.add(ChatMessage(answer, false)); speak(answer); return true
        }
        return false
    }

    suspend fun askGemini(question: String, key: String): String = withContext(Dispatchers.IO) {
        require(key.isNotBlank()) { "Pehle Settings mein Gemini API key add karo." }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" +
                URLEncoder.encode(key, "UTF-8")
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        try {
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", "You are SUFI, a helpful, professional female AI assistant. Reply naturally in the user's language, especially Hindi or Hinglish. Be concise, safe, and honest. You cannot directly control the phone except for commands implemented by the app.")
                )))
                .put("contents", JSONArray().put(
                    JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", question)))
                ))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                val message = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException("Gemini error $code: ${message ?: response.take(250)}")
            }
            val json = JSONObject(response)
            json.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        } finally { conn.disconnect() }
    }

    fun sendMessage(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || busy) return
        messages.add(ChatMessage(clean, true))
        draft = ""
        if (handleLocalCommand(clean)) return
        busy = true
        scope.launch {
            try {
                val answer = askGemini(clean, apiKey)
                messages.add(ChatMessage(answer, false))
                speak(answer)
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"
                messages.add(ChatMessage(error, false))
                speak("Maaf kijiye, abhi jawab nahi mil paaya.")
            } finally { busy = false }
        }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SUFI AI", color = White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("Your personal AI assistant", color = Cyan, fontSize = 11.sp)
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Cyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(Panel).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("SUFI se kuch bhi poochho...", color = Color.LightGray) },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White, unfocusedTextColor = White,
                        focusedBorderColor = Cyan, unfocusedBorderColor = Color.DarkGray,
                        cursorColor = Cyan
                    )
                )
                IconButton(onClick = { listen { result -> if (result.isNotBlank()) sendMessage(result) } }) {
                    Icon(Icons.Default.Mic, contentDescription = "Speak", tint = Cyan)
                }
                IconButton(onClick = { sendMessage(draft) }, enabled = !busy) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Cyan)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp)) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("◉", color = Cyan, fontSize = 42.sp)
                    Text(if (busy) "THINKING..." else "READY TO ASSIST", color = Cyan, fontSize = 12.sp, letterSpacing = 2.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 14.dp)
            ) {
                items(messages) { msg ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement =
                        if (msg.fromUser) Arrangement.End else Arrangement.Start) {
                        Box(
                            Modifier.widthIn(max = 310.dp)
                                .background(if (msg.fromUser) Color(0xFF123D50) else Panel, RoundedCornerShape(16.dp))
                                .padding(13.dp)
                        ) {
                            Text(msg.text, color = White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("SUFI Settings", color = White) },
            text = {
                Column {
                    Text("Gemini API key", color = Cyan)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("Paste your API key") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Key is stored on this device. Do not share it or upload it publicly.", color = Color.LightGray, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putString("gemini_key", apiKey.trim()).apply()
                    showSettings = false
                }) { Text("SAVE", color = Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("CANCEL", color = White) }
            }
        )
    }
}
