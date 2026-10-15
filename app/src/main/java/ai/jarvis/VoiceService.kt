package ai.jarvis

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.Locale
import java.util.concurrent.Executors

class VoiceService : Service(), RecognitionListener {
    companion object {
        @Volatile var running=false; private set
        const val STOP="ai.jarvis.STOP"
        const val TEXT="ai.jarvis.TEXT"
        const val PING="ai.jarvis.PING"
        const val STOP_REPLY="ai.jarvis.STOP_REPLY"
    }
    private enum class Phase { LOADING, WAKE, SPEAKING, COMMAND, CONFIRM, BUSY, STOPPED }
    private var phase=Phase.LOADING
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("settings",MODE_PRIVATE) }
    private lateinit var vault: Vault
    private var model: Model?=null
    private var recognizer: Recognizer?=null
    private var speech: SpeechService?=null
    private var tts: TextToSpeech?=null
    private var ttsReady=false
    private var dead=false
    private var speakNext: (() -> Unit)?=null
    private var pending: VoicePolicy.Action?=null
    private var speakingId=0
    private var lock: PowerManager.WakeLock?=null
    private var history=JSONArray()
    private val api=OpenAi()
    private val renewLock=object: Runnable {
        override fun run() {
            if(!dead && running) {
                lock?.acquire(60*60*1000L)
                main.postDelayed(this,30*60*1000L)
            }
        }
    }
    private val timeout=Runnable { if(phase==Phase.COMMAND || phase==Phase.CONFIRM) { pending=null; say("호출 대기로 돌아갑니다.") { waitWake() } } }
    private val speechTimeout=Runnable { if(phase==Phase.SPEAKING) { tts?.stop(); afterSpeech() } }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate(); vault=Vault(this)
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("voice","자비스 상시 음성 대기",NotificationManager.IMPORTANCE_LOW))
        try {
            check(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED) { "마이크 권한 필요" }
            val note=notification("음성 대기 준비 중")
            if(Build.VERSION.SDK_INT>=30) startForeground(7,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(7,note)
            lock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"jarvis:voice").apply { setReferenceCounted(false); acquire(60*60*1000L) }
            running=true; main.postDelayed(renewLock,30*60*1000L); update("한국어 오프라인 모델 준비 중 · 처음에는 시간이 걸립니다.",2)
            tts=TextToSpeech(this) { result -> main.post {
                if(dead) return@post
                val language=if(result==TextToSpeech.SUCCESS) tts?.setLanguage(Locale.KOREAN) else null
                ttsReady=language!=null && language!=TextToSpeech.LANG_MISSING_DATA && language!=TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setPitch(0.85f); tts?.setSpeechRate(0.94f)
                if(!ttsReady) fail("한국어 TTS 엔진을 설치한 후 상시 대기를 다시 시작하세요.") else prepared()
            } }
            tts?.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { main.post { if(id=="voice-$speakingId") afterSpeech() } }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { main.post { if(!dead) fail("음성 출력 실패 · TTS 설정을 확인하세요.") } }
            })
            worker.execute {
                try {
                    val loaded=LocalModel.load(this)
                    val saved=vault.get("history")
                    main.post {
                        if(dead) { loaded.close(); return@post }
                        model=loaded
                        try { if(saved.isNotBlank()) history=JSONArray(saved); prepared() }
                        catch(e: Exception) { fail("대화 이력 복원 실패 · 설정에서 대화를 초기화하세요.") }
                    }
                } catch(e: Exception) { main.post { if(!dead) fail("오프라인 음성 모델 초기화 실패 · 앱을 다시 실행하세요.") } }
            }
        } catch(e: Exception) { fail("상시 대기 시작 실패 · 앱을 연 상태에서 마이크 권한을 허용하세요.") }
    }
    private fun notification(message: String): Notification {
        val flags=PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),flags)
        val stop=PendingIntent.getService(this,1,Intent(this,VoiceService::class.java).setAction(STOP),flags)
        return Notification.Builder(this,"voice").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("J.A.R.V.I.S. 상시 대기")
            .setContentText(message).setContentIntent(open).setOngoing(true).addAction(Notification.Action.Builder(null,"대기 종료",stop).build()).build()
    }
    private fun update(text: String, mode: Int=0) {
        prefs.edit().putString("voice-status",text).putInt("voice-mode",mode).apply()
        if(running && (Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)) getSystemService(NotificationManager::class.java).notify(7,notification(text))
    }
    private fun prepared() {
        if(model==null || !ttsReady || speech!=null || dead) return
        try {
            recognizer=Recognizer(model,16000f)
            speech=SpeechService(recognizer,16000f)
            speech?.startListening(this)
            say("자비스 상시 대기를 시작합니다. 자비스라고 불러 주십시오.") { waitWake() }
        } catch(e: Exception) { fail("마이크 시작 실패 · 통화나 다른 녹음을 종료한 뒤 다시 시작하세요.") }
    }
    private fun fail(message: String) { update(message,4); stopSelf() }
    private fun resetListening() { speech?.reset(); speech?.setPause(false) }
    private fun waitWake() {
        if(dead) return
        main.removeCallbacks(timeout); pending=null; phase=Phase.WAKE; resetListening(); update("상시 대기 중 · ‘자비스’라고 부르세요")
    }
    private fun listen(confirm: Boolean=false) {
        if(dead) return
        phase=if(confirm) Phase.CONFIRM else Phase.COMMAND
        resetListening(); update(if(confirm) "‘실행’ 또는 ‘취소’라고 말씀하세요" else "지시를 말씀하세요",1)
        main.removeCallbacks(timeout); main.postDelayed(timeout,20000)
    }
    private fun afterSpeech() {
        main.removeCallbacks(speechTimeout)
        if(dead || phase!=Phase.SPEAKING) return
        val next=speakNext; speakNext=null
        // Let speaker reverberation fade so the local recognizer cannot hear its own greeting.
        main.postDelayed({ if(!dead) next?.invoke() },650)
    }
    private fun say(text: String, next: () -> Unit) {
        if(dead) return
        main.removeCallbacks(timeout); main.removeCallbacks(speechTimeout)
        phase=Phase.SPEAKING; speech?.setPause(true); speakNext=next; speakingId++
        update(text,3)
        if(ttsReady && tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"voice-$speakingId")==TextToSpeech.SUCCESS) main.postDelayed(speechTimeout,90000)
        else fail("한국어 음성 출력 실패 · TTS 설정을 확인하세요.")
    }
    private fun heard(text: String, partial: Boolean) {
        if(dead || text.isBlank()) return
        when(phase) {
            Phase.WAKE -> if(VoicePolicy.wake(text)) {
                say("돌아오셨습니까, 사장님. 지시 기다리겠습니다.") { listen() }
            }
            Phase.COMMAND -> if(!partial) command(text)
            Phase.CONFIRM -> if(!partial) {
                val action=pending
                when {
                    VoicePolicy.cancelled(text) -> { pending=null; say("취소했습니다.") { listen() } }
                    VoicePolicy.confirmed(text) && action!=null -> { pending=null; perform(action) }
                    else -> say("실행 또는 취소라고 말씀해 주십시오.") { listen(true) }
                }
            }
            else -> {}
        }
    }
    private fun command(text: String) {
        main.removeCallbacks(timeout); speech?.setPause(true)
        val action=VoicePolicy.action(text)
        if(action!=null) { handleAction(action); return }
        if(VoicePolicy.cancelled(text) || VoicePolicy.normalize(text) in setOf("고마워", "대기해", "대기해줘")) { say("호출 대기하겠습니다.") { waitWake() }; return }
        phase=Phase.BUSY; update("생각 중",2)
        val request=JSONArray(history.toString()).put(JSONObject().put("role","user").put("content",text.take(6000)))
        val chosen=prefs.getString("model","gpt-4.1-mini") ?: "gpt-4.1-mini"
        worker.execute {
            try {
                val decision=api.plan(vault.get("api-key"),chosen,request)
                if(decision.action!=null) {
                    main.post { if(!dead) handleAction(decision.action) }
                    return@execute
                }
                val response=decision.text ?: error("빈 응답입니다.")
                request.put(JSONObject().put("role","assistant").put("content",response))
                val bounded=JSONArray(); for(i in maxOf(0,request.length()-20) until request.length()) bounded.put(request.getJSONObject(i))
                vault.put("history",bounded.toString())
                main.post { if(!dead) { history=bounded; prefs.edit().putLong("voice-history-revision",System.currentTimeMillis()).apply(); say(response) { listen() } } }
            } catch(e: Exception) { main.post { if(!dead) say(e.message ?: "통신 오류입니다. 다시 말씀해 주십시오.") { listen() } } }
        }
    }
    private fun handleAction(action: VoicePolicy.Action) {
        if(action==VoicePolicy.Action.LOCK) {
            pending=action; say("화면을 잠글까요? 실행 또는 취소라고 말씀해 주십시오.") { listen(true) }
        } else perform(action)
    }
    private fun perform(action: VoicePolicy.Action) {
        phase=Phase.BUSY; speech?.setPause(true)
        if(action==VoicePolicy.Action.STOP) { say("상시 대기를 종료합니다.") { stopSelf() }; return }
        try {
            val message=when(action) {
                VoicePolicy.Action.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME,"홈 이동 요청이 처리됐습니다.")
                VoicePolicy.Action.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK,"뒤로 이동 요청이 처리됐습니다.")
                VoicePolicy.Action.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS,"최근 앱 요청이 처리됐습니다.")
                VoicePolicy.Action.LOCK -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,"화면 잠금 요청이 처리됐습니다.")
                VoicePolicy.Action.VOLUME_UP, VoicePolicy.Action.VOLUME_DOWN -> {
                    getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC,if(action==VoicePolicy.Action.VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,0)
                    "미디어 볼륨을 조절했습니다."
                }
                VoicePolicy.Action.YOUTUBE -> openViaAccessibility(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://www.youtube.com")).setPackage("com.google.android.youtube"))
                VoicePolicy.Action.CAMERA -> openViaAccessibility(Intent("android.media.action.STILL_IMAGE_CAMERA"))
                VoicePolicy.Action.CALENDAR -> openViaAccessibility(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR))
                VoicePolicy.Action.WIFI -> openViaAccessibility(Intent(Settings.ACTION_WIFI_SETTINGS))
                else -> error("지원하지 않는 동작")
            }
            say(message) { listen() }
        } catch(e: Exception) { say(e.message ?: "해당 동작은 기기가 허용하지 않습니다.") { listen() } }
    }
    private fun global(action: Int, success: String): String {
        val service=ControlService.current ?: error("접근성에서 자비스 사용자 제어를 켜 주십시오.")
        check(service.performGlobalAction(action)) { "기기가 제어 요청을 허용하지 않았습니다." }
        return success
    }
    private fun openViaAccessibility(intent: Intent): String {
        check(!getSystemService(KeyguardManager::class.java).isKeyguardLocked) { "먼저 휴대폰 잠금을 직접 해제해 주십시오." }
        val service=ControlService.current ?: error("앱을 열려면 접근성에서 자비스 사용자 제어를 켜 주십시오.")
        service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "화면 열기를 요청했습니다."
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(intent?.action==STOP) stopSelf()
        else if(intent?.action==STOP_REPLY && phase==Phase.SPEAKING) { tts?.stop(); afterSpeech() }
        else if(intent?.action==PING && (phase==Phase.WAKE || phase==Phase.COMMAND)) say("상시 대기가 작동 중입니다.") { waitWake() }
        else if(intent?.action==TEXT && (phase==Phase.WAKE || phase==Phase.COMMAND)) intent.getStringExtra("text")?.let { command(it) }
        return START_NOT_STICKY
    }
    override fun onPartialResult(hypothesis: String?) { try { heard(JSONObject(hypothesis ?: "{}").optString("partial"),true) } catch(_: Exception) {} }
    override fun onResult(hypothesis: String?) { try { heard(JSONObject(hypothesis ?: "{}").optString("text"),false) } catch(_: Exception) {} }
    override fun onFinalResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) { if(!dead) fail("마이크 인식 오류 · 상시 대기를 다시 시작하세요.") }
    override fun onTimeout() {}
    override fun onDestroy() {
        dead=true; running=false; phase=Phase.STOPPED; main.removeCallbacksAndMessages(null)
        speech?.cancel(); speech?.shutdown(); recognizer?.close(); model?.close()
        tts?.stop(); tts?.shutdown(); api.cancel(); worker.shutdownNow()
        lock?.let { if(it.isHeld) it.release() }; stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
