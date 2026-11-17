package com.dayflow.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.dayflow.app.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.dayflow.app.data.Repo
import com.dayflow.app.notify.Notifier
import com.dayflow.app.notify.runNow
import com.dayflow.app.notify.schedule
import com.dayflow.app.ui.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val vm: Vm by viewModels()
    private var recognizer: SpeechRecognizer? = null

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (t != null) { vm.cmd = t; vm.refreshSugs(); vm.say("Voice captured — review, then send") }
        }
        vm.listening = false
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val micPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startVoice() else vm.say("Microphone permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.init(this); schedule(this); runNow(this)
        // POST_NOTIFICATIONS only exists on Android 13+; on Android 12 notifications are allowed without asking.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (savedInstanceState == null) handleIntent(intent)  // don't restart the moment on recreation
        setContent {
            DayflowTheme(vm.prefs.value.accent) {
                Box(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()) {
                    Column(Modifier.fillMaxSize()) {
                        TopBar(vm)
                        Box(Modifier.weight(1f)) {
                            when (vm.tab) {
                                "today" -> TodayScreen(vm) { mic() }
                                "tasks" -> TasksScreen(vm)
                                "moments" -> MomentsScreen(vm)
                                "stats" -> StatsScreen(vm)
                                else -> MoreScreen(vm)
                            }
                        }
                        BottomBar(vm)
                    }
                    if (vm.tab == "tasks" || vm.tab == "moments")
                        Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 84.dp).size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.horizontalGradient(listOf(LocalAcc.current.first, LocalAcc.current.second)))
                            .clickable { vm.openSheet(if (vm.tab == "tasks") "task" else "moment") },
                            contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = col("#1A1206")) }
                    if (vm.runner) Runner(vm)
                    Sheets(vm)
                    vm.toast?.let { msg ->
                        LaunchedEffect(msg) { delay(2600); vm.toast = null }
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp, start = 14.dp, end = 14.dp)
                            .clip(RoundedCornerShape(15.dp)).background(C.surf2).padding(14.dp)) {
                            Text(msg, color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    private fun handleIntent(i: Intent) {
        val id = i.getStringExtra("moment")
        i.removeExtra("moment")
        if (!id.isNullOrBlank()) vm.startMoment(id)
    }
    private fun mic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPerm.launch(Manifest.permission.RECORD_AUDIO); return
        }
        startVoice()
    }
    private fun startVoice() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { systemVoice(); return }
        vm.listening = true
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { vm.say("🎙️ Listening…") }
                override fun onResults(b: Bundle?) {
                    val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (t != null) { vm.cmd = t; vm.refreshSugs(); vm.say("Voice captured — review, then send") }
                    vm.listening = false
                }
                override fun onError(e: Int) { vm.listening = false; systemVoice() }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
        }
    }
    private fun systemVoice() {
        vm.listening = true
        try {
            voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a Dayflow command"))
        } catch (e: Exception) { vm.listening = false; vm.say("Voice unavailable — type instead") }
    }
    override fun onDestroy() { recognizer?.destroy(); super.onDestroy() }
}

@Composable private fun TopBar(vm: Vm) {
    val best = vm.habits.value.maxOfOrNull { vm.streak(it) } ?: 0
    Row(Modifier.fillMaxWidth().height(58.dp).background(C.bg2).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(LocalAcc.current.first, LocalAcc.current.second))),
            contentAlignment = Alignment.Center) { Text("☀", fontSize = 16.sp) }
        Spacer(Modifier.width(9.dp))
        Column { Text("Dayflow", color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text("YOUR DAY IN MOMENTS", color = C.dim, fontSize = 8.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.clip(RoundedCornerShape(99.dp)).background(colA("#FFB347", .1f)).padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text("🔥 $best", color = LocalAcc.current.first, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        IconButton(onClick = { vm.run { Repo.prefs.sound(!vm.prefs.value.sound) } }) {
            Icon(if (vm.prefs.value.sound) Icons.Default.VolumeUp else Icons.Default.VolumeOff, null, tint = C.mut) }
        IconButton(onClick = { vm.tab = "more" }) {
            Icon(if (vm.prefs.value.notifications) Icons.Default.Notifications else Icons.Default.NotificationsOff, null, tint = C.mut) }
    }
}
@Composable private fun BottomBar(vm: Vm) {
    val tabs = listOf(Triple("today","Today",Icons.Default.WbSunny), Triple("tasks","Tasks",Icons.Default.Checklist),
        Triple("moments","Moments",Icons.Default.Bolt), Triple("stats","Stats",Icons.Default.BarChart),
        Triple("more","More",Icons.Default.Tune))
    Row(Modifier.fillMaxWidth().height(66.dp).background(C.bg2)) {
        tabs.forEach { (k, label, icon) ->
            val on = vm.tab == k
            Column(Modifier.weight(1f).fillMaxHeight().clickable { vm.tab = k },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (on) Box(Modifier.width(34.dp).height(3.dp).clip(RoundedCornerShape(99.dp))
                    .background(Brush.horizontalGradient(listOf(LocalAcc.current.first, LocalAcc.current.second))))
                Spacer(Modifier.height(4.dp))
                Icon(icon, null, tint = if (on) LocalAcc.current.first else C.dim, modifier = Modifier.size(21.dp))
                Text(label, color = if (on) LocalAcc.current.first else C.dim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
