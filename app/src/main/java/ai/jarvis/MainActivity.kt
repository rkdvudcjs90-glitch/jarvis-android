package ai.jarvis

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.InputType
import android.view.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity(), RecognitionListener {
    private lateinit var vault: Vault
    private lateinit var orb: Hologram
    private lateinit var status: TextView
    private lateinit var title: TextView
    private lateinit var lastMessage: TextView
    private lateinit var quickStandby: Button
    private lateinit var transcript: TextView
    private lateinit var input: EditText
    private lateinit var mic: Button
    private lateinit var send: Button
    private var history = JSONArray()
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var busy = false
    private var listening = false
    private var visible = false
    private var rootVerified = false
    private val worker = Executors.newSingleThreadExecutor()
    private val api = OpenAi()
    private val main = Handler(Looper.getMainLooper())
    private var voiceRevision = 0L
    private var autoStartAttempted = false
    private val voicePoll = object : Runnable {
        override fun run() {
            if (!visible || isDestroyed) return
            title.text=if(VoiceService.running) "J.A.R.V.I.S. ACTIVE" else "J.A.R.V.I.S. STANDBY"
            quickStandby.text=if(VoiceService.running) "대기 종료" else "상시 대기 시작"
            if (VoiceService.running) {
                state(prefs.getString("voice-status", "상시 대기 준비 중") ?: "상시 대기", prefs.getInt("voice-mode", 0))
                val revision=prefs.getLong("voice-history-revision", 0)
                if(revision != voiceRevision) {
                    voiceRevision=revision
                    guarded { val saved=vault.get("history"); if(saved.isNotBlank()) { history=JSONArray(saved); renderHistory() } }
                }
            } else if(autoStartAttempted && !busy && !listening && prefs.getInt("voice-mode",0)==4) {
                problem(prefs.getString("voice-status","음성 대기 오류 · 제목을 길게 눌러 설정") ?: "음성 대기 오류")
            }
            main.postDelayed(this, 700)
        }
    }
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private fun text(value: String, size: Float = 15f) = TextView(this).apply { text = value; textSize = size; setTextColor(Color.rgb(195,227,240)); setPadding(12,8,12,8) }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text=label; textSize=12f; isAllCaps=false; setTextColor(Color.rgb(130,235,246))
        background=android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(5,20,29)); cornerRadius=12f
            setStroke(1,Color.rgb(27,87,101))
        }
        minHeight=(48*resources.displayMetrics.density).toInt(); setPadding(8,8,8,8)
        setOnClickListener { action() }
    }
    private fun state(message: String, mode: Int = 0) { status.text = message; orb.mode = mode }
    private fun problem(message: String) { state(message, 4) }
    private fun guarded(action: () -> Unit) { try { action() } catch (e: Exception) { problem(e.message ?: "동작 실패") } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vault = Vault(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(16,0,16,8) }
        layout.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(16,bars.top,16,bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(16,insets.systemWindowInsetTop,16,insets.systemWindowInsetBottom)
            }; insets
        }
        title=text("J.A.R.V.I.S. STANDBY",22f).apply {
            gravity=Gravity.CENTER; letterSpacing=0.09f; setTextColor(Color.rgb(100,244,255))
            setShadowLayer(16f,0f,0f,Color.rgb(0,180,210)); setPadding(12,28,12,4)
        }
        layout.addView(title)
        status=text("음성 대기 준비 중",13f).apply {
            gravity=Gravity.CENTER; maxLines=3; ellipsize=android.text.TextUtils.TruncateAt.END
        }
        layout.addView(status)
        orb=Hologram(this)
        layout.addView(orb,LinearLayout.LayoutParams(-1,0,1f))
        lastMessage=text("말씀을 기다리고 있습니다",14f).apply {
            gravity=Gravity.CENTER; maxLines=3; ellipsize=android.text.TextUtils.TruncateAt.END
            setTextColor(Color.rgb(155,205,218)); setPadding(16,4,16,18)
        }
        layout.addView(lastMessage)
        val panel=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=View.GONE }
        val toolbar=LinearLayout(this)
        quickStandby=button("상시 대기 시작") {
            if(VoiceService.running) { prefs.edit().putBoolean("voice-auto-enabled",false).apply(); stopService(Intent(this,VoiceService::class.java)); state("음성 대기 꺼짐 · 제목을 길게 눌러 설정") }
            else { prefs.edit().putBoolean("voice-auto-enabled",true).apply(); startStandby() }
        }
        // The normal screen has no control buttons. Keep recovery controls discoverable
        // through the title's standard Android long-click accessibility action.
        title.contentDescription="자비스. 길게 누르면 설정과 조작 패널을 엽니다."
        title.setOnLongClickListener {
            panel.visibility=if(panel.visibility==View.VISIBLE) View.GONE else View.VISIBLE
            true
        }
        toolbar.addView(quickStandby,LinearLayout.LayoutParams(-1,-2))
        panel.addView(toolbar)
        val scroll=ScrollView(this)
        transcript=text("")
        scroll.addView(transcript)
        panel.addView(scroll,LinearLayout.LayoutParams(-1,(120*resources.displayMetrics.density).toInt()))
        input=EditText(this).apply { hint="한국어로 입력하세요"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); maxLines=2; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        panel.addView(input)
        val actions=LinearLayout(this)
        mic=button("음성") { startListening() }
        send=button("전송") { submit(input.text.toString()) }
        actions.addView(mic,LinearLayout.LayoutParams(0,-2,1f)); actions.addView(send,LinearLayout.LayoutParams(0,-2,1f))
        actions.addView(button("음성 중지") { if(VoiceService.running) startService(Intent(this,VoiceService::class.java).setAction(VoiceService.STOP_REPLY)) else { tts?.stop(); if(!busy) state("대기 중") } },LinearLayout.LayoutParams(0,-2,1f))
        panel.addView(actions)
        val tools=LinearLayout(this)
        tools.addView(button("설정·권한") { settings() },LinearLayout.LayoutParams(0,-2,1f))
        tools.addView(button("휴대폰 제어") { controls() },LinearLayout.LayoutParams(0,-2,1f))
        tools.addView(button("대화 삭제") { confirm("저장된 대화를 삭제할까요?") {
            if(busy || VoiceService.running) problem("대화와 상시 대기를 종료한 뒤 삭제하세요.")
            else { history=JSONArray(); guarded { vault.put("history","[]") }; renderHistory() }
        } },LinearLayout.LayoutParams(0,-2,1f))
        panel.addView(tools)
        layout.addView(panel)
        setContentView(layout)
        guarded { val saved=vault.get("history"); if(saved.isNotEmpty()) history=JSONArray(saved) }
        renderHistory()
        tts = TextToSpeech(this) { result -> main.post {
            val language = if(result == TextToSpeech.SUCCESS) tts?.setLanguage(Locale.KOREAN) else null
            ttsReady = language != null && language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED
            if(!ttsReady && !VoiceService.running) state("한국어 TTS 미설치 · 설정에서 음성 엔진을 확인하세요")
        } }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { main.post { if(visible && !VoiceService.running) state("응답 중",3) } }
            override fun onDone(id: String?) { main.post { if(visible && !VoiceService.running && !busy && !listening) state("대기 중") } }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { main.post { if(visible) problem("음성 출력 실패 · 답변은 화면에서 확인하세요") } }
        })
        if(!prefs.getBoolean("onboarded",false)) {
            AlertDialog.Builder(this).setTitle("자비스 시작 안내")
                .setMessage("설정에서 OpenAI API 키를 입력하세요. 메시지와 최근 대화가 OpenAI로 전송되며 API 요금이 발생합니다. 음성 인식은 설치된 Android 음성 서비스가 처리하며 네트워크로 전송될 수 있습니다.\n\n권한 허용 후 앱을 열면 자동으로 마이크를 사용하여 기기 안에서 ‘자비스’를 감지합니다. 대화가 끝나고 12초 동안 말이 없으면 자동으로 호출 대기로 돌아갑니다. 상단 제목을 길게 누르면 설정이 열립니다. 알림이나 ‘상시 대기 종료’ 음성 명령으로 마이크 대기를 끌 수 있습니다. 재부팅·강제 종료 뒤에는 앱을 다시 열어 주세요. 접근성은 선택 사항이며 화면 내용을 수집하지 않습니다. 승인 후 지원되는 음성 지시 또는 제어 버튼으로 동작합니다. ROOT는 별도 선택 기능입니다.")
                .setPositiveButton("설정 열기") { _,_ -> prefs.edit().putBoolean("onboarded",true).apply(); settings() }.show()
        }
    }
    private fun renderHistory() {
        lastMessage.text=if(history.length()>0) history.getJSONObject(history.length()-1).optString("content").take(350) else "말씀을 기다리고 있습니다"
        transcript.text = (0 until history.length()).joinToString("\n\n") { val item=history.getJSONObject(it); (if(item.getString("role")=="user") "나: " else "자비스: ")+item.getString("content") }
    }
    private fun startStandby() {
        if(busy) { problem("현재 대화가 끝난 후 시작하세요."); return }
        if(VoiceService.running) { state("이미 상시 대기가 켜져 있습니다."); return }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),12); return }
        if(Build.VERSION.SDK_INT>=33 && !prefs.getBoolean("voice-notification-asked",false) && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) { prefs.edit().putBoolean("voice-notification-asked",true).apply(); requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),13); return }
        recognizer?.cancel(); tts?.stop()
        guarded { startForegroundService(Intent(this,VoiceService::class.java)) }
    }
    private fun autoStandby() {
        if(!autoStartAttempted && prefs.getBoolean("onboarded",false) && prefs.getBoolean("voice-auto-enabled",true)) {
            autoStartAttempted=true
            startStandby()
        } else if(!VoiceService.running && !prefs.getBoolean("voice-auto-enabled",true)) {
            state("음성 대기 꺼짐 · 제목을 길게 눌러 설정")
        }
    }

    private fun submit(raw: String) {
        val message = raw.trim()
        if(VoiceService.running) {
            if(message.isNotEmpty()) { startService(Intent(this,VoiceService::class.java).setAction(VoiceService.TEXT).putExtra("text",message)); input.text.clear() }
            return
        }
        if(busy || message.isEmpty()) return
        if(message.length > 6000) { problem("메시지를 6,000자 이내로 입력하세요."); return }
        val key = try { vault.get("api-key") } catch(e: Exception) { problem("암호화 저장소 오류 · 설정에서 초기화하세요."); return }
        if(key.isBlank()) { settings(); return }
        recognizer?.cancel(); listening=false; tts?.stop(); input.text.clear()
        busy=true; send.isEnabled=false; mic.isEnabled=false; state("생각 중",2)
        val requestHistory=JSONArray(history.toString()).put(JSONObject().put("role","user").put("content",message))
        transcript.text = transcript.text.toString()+"\n\n나: $message"
        val model=prefs.getString("model","gpt-4.1-mini") ?: "gpt-4.1-mini"
        worker.execute {
            try {
                val response=api.reply(key,model,requestHistory)
                requestHistory.put(JSONObject().put("role","assistant").put("content",response))
                val bounded=JSONArray()
                for(i in maxOf(0,requestHistory.length()-20) until requestHistory.length()) bounded.put(requestHistory.getJSONObject(i))
                var saved=true
                try { vault.put("history",bounded.toString()) } catch(e: Exception) { saved=false }
                main.post {
                    if(isDestroyed) return@post
                    history=bounded; busy=false; send.isEnabled=true; mic.isEnabled=true; renderHistory()
                    state(if(saved) "응답 완료" else "응답 완료 · 대화 저장 실패")
                    if(visible && ttsReady) tts?.speak(response,TextToSpeech.QUEUE_FLUSH,null,"reply")
                }
            } catch(e: Exception) { main.post { if(!isDestroyed) { busy=false; send.isEnabled=true; mic.isEnabled=true; problem(e.message ?: "네트워크 오류"); renderHistory(); input.setText(message) } } }
        }
    }
    private fun startListening() {
        if(VoiceService.running) { state("‘자비스’라고 부르면 응답합니다."); return }
        if(busy) return
        if(listening) { recognizer?.stopListening(); return }
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10); return }
        if(!SpeechRecognizer.isRecognitionAvailable(this)) { problem("음성 인식 서비스가 없습니다. Google 또는 Samsung 음성 서비스를 설정하세요."); return }
        tts?.stop()
        if(recognizer==null) recognizer=SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
        guarded {
            listening=true; state("듣는 중 · 다시 누르면 인식 종료",1)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true))
        }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==12 || code==13) {
            // Notification denial does not prohibit a microphone foreground service.
            if(code==13 || results.firstOrNull()==PackageManager.PERMISSION_GRANTED) startStandby()
            else problem("마이크 권한 필요 · 제목을 길게 눌러 설정하세요.")
        }
        if(code==10) { if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED) startListening() else problem("마이크 권한이 거부되었습니다. 설정·권한에서 변경할 수 있습니다.") }
    }
    private fun confirm(message: String, action: () -> Unit) { AlertDialog.Builder(this).setTitle("실행 확인").setMessage(message).setNegativeButton("취소",null).setPositiveButton("실행") { _,_ -> guarded(action) }.show() }
    private fun open(intent: Intent) = guarded { startActivity(intent) }
    private fun settings() {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(30,12,30,12) }
        box.addView(text("API 키는 기기 Keystore로 암호화됩니다. 빈칸이면 기존 키를 유지합니다. 키를 대화창에 입력하지 마세요."))
        val key=EditText(this).apply { hint="OpenAI API 키"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO }
        val model=EditText(this).apply { hint="모델 ID"; setText(prefs.getString("model","gpt-4.1-mini")); isSingleLine=true }
        box.addView(key); box.addView(model)
        box.addView(button("앱 권한 설정") { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:$packageName"))) })
        box.addView(button("접근성 안내·설정") {
            AlertDialog.Builder(this).setTitle("선택 접근성 권한").setMessage("사용자의 버튼 또는 음성 지시에 따른 홈·뒤로·최근 앱·화면 잠금 및 지정 앱 열기에만 사용합니다. 화면 내용, 입력 문자, 알림을 읽지 않습니다. Android 설정에서 JARVIS 사용자 제어를 직접 켜세요. 설치 경로에 따라 앱 정보 메뉴에서 ‘제한된 설정 허용’이 먼저 필요할 수 있습니다.")
                .setNegativeButton("취소",null).setPositiveButton("설정") { _,_ -> open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.show()
        })
        box.addView(button("배터리 제한 설정") { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        box.addView(button("한국어 TTS 설정") { open(Intent("com.android.settings.TTS_SETTINGS")) })
        box.addView(button("알림 권한·테스트") {
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),11)
            } else {
                val manager=getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel("status","자비스 상태",NotificationManager.IMPORTANCE_DEFAULT))
                manager.notify(1,Notification.Builder(this,"status").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("J.A.R.V.I.S.").setContentText("알림 설정이 완료되었습니다.").setAutoCancel(true).build())
            }
        })
        box.addView(button("키·대화 모두 삭제") { confirm("API 키와 저장된 대화를 삭제합니다. 진행 중인 대화가 있으면 완료 후 삭제하세요.") {
            check(!busy && !VoiceService.running) { "대화 또는 상시 대기를 종료한 뒤 삭제하세요." }; vault.clear(); history=JSONArray(); renderHistory(); state("키와 대화를 삭제했습니다.")
        } })
        AlertDialog.Builder(this).setTitle("설정·권한").setView(ScrollView(this).apply { addView(box) }).setNegativeButton("닫기",null).setPositiveButton("저장") { _,_ -> guarded {
            val value=key.text.toString().trim(); if(value.isNotEmpty()) vault.put("api-key",value)
            val modelId=model.text.toString().trim(); require(modelId.matches(Regex("[A-Za-z0-9._:-]{1,100}"))) { "올바른 모델 ID를 입력하세요." }
            prefs.edit().putString("model",modelId).apply(); key.text.clear(); state("설정 저장 완료"); autoStandby()
        } }.show()
    }
    private fun global(action: Int) {
        val service=ControlService.current
        if(service==null) problem("설정·권한에서 접근성을 켜세요.")
        else if(!service.performGlobalAction(action)) problem("기기가 이 동작을 허용하지 않았습니다.")
    }
    private fun controls() {
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,8,24,8) }
        box.addView(text("일반 제어 · 접근성은 선택 사항"))
        box.addView(button("홈") { global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) })
        box.addView(button("뒤로") { global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) })
        box.addView(button("최근 앱") { global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS) })
        box.addView(button("화면 잠금") { confirm("화면을 잠글까요?") { global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) } })
        box.addView(button("볼륨 설정") { open(Intent(Settings.ACTION_SOUND_SETTINGS)) })
        box.addView(button("Wi-Fi 설정") { open(Intent(Settings.ACTION_WIFI_SETTINGS)) })
        box.addView(text("ROOT 선택 모듈 · 루팅을 수행하지 않습니다. AI는 셸을 실행할 수 없습니다."))
        val toggle=Switch(this).apply { text="ROOT 고급 제어 사용"; isChecked=prefs.getBoolean("root-enabled",false) }
        box.addView(toggle)
        val rootButtons=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; visibility=if(toggle.isChecked) View.VISIBLE else View.GONE }
        toggle.setOnCheckedChangeListener { _,checked -> prefs.edit().putBoolean("root-enabled",checked).apply(); rootVerified=false; rootButtons.visibility=if(checked) View.VISIBLE else View.GONE }
        for(action in RootAction.entries) rootButtons.addView(button(action.label) {
            if(action!=RootAction.CHECK && !rootVerified) { problem("먼저 ROOT 확인을 실행하세요."); return@button }
            confirm("${action.label}을 실행할까요? 재부팅 시 저장하지 않은 작업이 사라질 수 있습니다. ROOT 관리자의 승인이 필요합니다.") {
                check(prefs.getBoolean("root-enabled",false)); state("ROOT 승인·실행 대기 중")
                worker.execute {
                    try { check(prefs.getBoolean("root-enabled",false)) { "ROOT 모듈이 꺼졌습니다." }; val result=RootController.run(action); main.post { if(!isDestroyed) { if(action==RootAction.CHECK) rootVerified=true; state(result) } } }
                    catch(e: Exception) { main.post { if(!isDestroyed) { rootVerified=false; problem(e.message ?: "ROOT 실행 실패") } } }
                }
            }
        })
        box.addView(rootButtons)
        AlertDialog.Builder(this).setTitle("휴대폰 제어").setView(ScrollView(this).apply { addView(box) }).setPositiveButton("닫기",null).show()
    }
    override fun onResume() { super.onResume(); visible=true; orb.onResume(); main.removeCallbacks(voicePoll); main.post(voicePoll); autoStandby() }
    override fun onPause() { visible=false; main.removeCallbacks(voicePoll); recognizer?.cancel(); listening=false; tts?.stop(); if(!busy) state("대기 중"); orb.onPause(); super.onPause() }
    override fun onDestroy() { recognizer?.destroy(); tts?.shutdown(); api.cancel(); worker.shutdownNow(); main.removeCallbacksAndMessages(null); super.onDestroy() }
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) { orb.level=(rmsdB/12f).coerceIn(0f,1f) }
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() { listening=false; state("음성 인식 처리 중",2) }
    override fun onError(error: Int) { listening=false; problem(when(error) { SpeechRecognizer.ERROR_NO_MATCH -> "인식하지 못했습니다. 다시 말해 주세요."; SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성이 감지되지 않았습니다."; SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "마이크 권한을 확인하세요."; else -> "음성 인식 오류 ($error). 서비스·네트워크를 확인하세요." }) }
    override fun onResults(results: Bundle?) { listening=false; val recognized=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if(!recognized.isNullOrBlank()) submit(recognized) else state("음성을 다시 입력하세요") }
    override fun onPartialResults(partialResults: Bundle?) { partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { status.text="듣는 중: $it" } }
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
