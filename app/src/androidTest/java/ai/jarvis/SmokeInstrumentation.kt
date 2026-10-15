package ai.jarvis

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import java.io.File

/** Runs without a real API key or ROOT. Only uses a disposable emulator. */
class SmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun descendants(view: View): List<View> = listOf(view) +
        if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
    override fun onStart() {
        val result=Bundle()
        var activity: Activity?=null
        try {
            val settings=targetContext.getSharedPreferences("settings",0)
            settings.edit().putBoolean("onboarded",true).putBoolean("voice-auto-enabled",true).putBoolean("voice-notification-asked",true).commit()
            for(permission in listOf("android.permission.RECORD_AUDIO","android.permission.POST_NOTIFICATIONS")) {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("pm grant ${targetContext.packageName} $permission")).use { it.readBytes() }
            }
            val startsBefore=VoiceService.foregroundStarts
            val vault=Vault(targetContext)
            val sample="emulator-only-test-value-한국어"
            vault.put("smoke",sample)
            check(Vault(targetContext).get("smoke")==sample) { "Keystore round-trip failed" }
            val raw=targetContext.getSharedPreferences("vault",0).getString("smoke","") ?: ""
            check(!raw.contains(sample) && raw.isNotBlank()) { "Plaintext stored" }
            vault.clear()
            val localModel=LocalModel.load(targetContext)
            val find=com.sun.jna.NativeLibrary.getInstance("vosk").getFunction("vosk_model_find_word")
            val tokens=listOf("자비스","자비서","자비","비스","자","스")
            val words=tokens.associateWith { find.invokeInt(arrayOf(localModel.pointer,it)) }
            android.util.Log.i("JARVIS_TEST","WAKE_VOCAB: $words")
            val localRecognizer=org.vosk.Recognizer(localModel,16000f)
            try {
                localRecognizer.acceptWaveForm(ByteArray(32000),32000)
                org.json.JSONObject(localRecognizer.partialResult)
            } finally { localRecognizer.close(); localModel.close() }
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            check(VoiceService.foregroundStarts>startsBefore) { "Visible app launch did not start microphone foreground service automatically" }
            runOnMainSync {
                check(descendants(activity!!.window.decorView).filterIsInstance<Button>().none { it.isShown }) { "Control buttons visible on voice-first screen" }
            }
            var core: Hologram?=null
            runOnMainSync { core=descendants(activity!!.window.decorView).filterIsInstance<Hologram>().single() }
            val folder=File(targetContext.getExternalFilesDir(null),"smoke").apply { mkdirs() }
            val deadline=android.os.SystemClock.uptimeMillis()+15000
            var preview: Bitmap?=null
            var luminous=0
            // Wait for real frame presentation rather than assuming a 2-second cold start.
            while(android.os.SystemClock.uptimeMillis()<deadline) {
                val next=uiAutomation.takeScreenshot()
                if(next!=null) {
                    preview?.recycle(); preview=next; luminous=0
                    for(y in next.height*3/10 until next.height*7/10 step 4) {
                        for(x in next.width/5 until next.width*4/5 step 4) {
                            val color=next.getPixel(x,y)
                            if(android.graphics.Color.green(color)>110 && android.graphics.Color.blue(color)>120) luminous++
                        }
                    }
                    if((core?.renderedFrames ?: 0)>0 && luminous>100) break
                }
                Thread.sleep(250)
            }
            val shot=preview ?: error("Screenshot unavailable")
            try {
                File(folder,"hologram.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) }
                check(luminous>100) { "Particle mesh not visible: $luminous samples; frames=${core?.renderedFrames}; GL=${core?.drawError}" }
                check(core?.drawError==android.opengl.GLES20.GL_NO_ERROR) { "OpenGL error: ${core?.drawError}" }
            } finally { shot.recycle() }
            val animationFolder=File(targetContext.getExternalFilesDir(null),"smoke").apply { mkdirs() }
            var first: Bitmap?=null
            var changed=0
            try {
                for(i in 0 until 8) {
                    Thread.sleep(180)
                    val frame=uiAutomation.takeScreenshot() ?: error("Animation screenshot unavailable")
                    try {
                        File(animationFolder,"frame%02d.png".format(i)).outputStream().use { frame.compress(Bitmap.CompressFormat.PNG,100,it) }
                        if(i==0) first=frame.copy(Bitmap.Config.ARGB_8888,false)
                        if(i==7) {
                            val initial=first ?: error("First frame missing")
                            for(y in frame.height*3/10 until frame.height*7/10 step 4) {
                                for(x in frame.width/5 until frame.width*4/5 step 4) {
                                    val a=initial.getPixel(x,y); val b=frame.getPixel(x,y)
                                    if(kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))>30) changed++
                                }
                            }
                        }
                    } finally { frame.recycle() }
                }
                check(changed>100) { "Particle geometry is static or not visible: $changed changed samples" }
            } finally { first?.recycle() }
            runOnMainSync {
                val views=descendants(activity!!.window.decorView)
                check(views.filterIsInstance<Hologram>().any { it.width>0 && it.height>0 }) { "Hologram has no layout" }
                check(views.filterIsInstance<TextView>().any { it.text.contains("J.A.R.V.I.S.") }) { "Main UI missing" }
                views.filterIsInstance<TextView>().first { it.text.contains("J.A.R.V.I.S.") }.performLongClick()
                check(views.filterIsInstance<Button>().first { it.text=="설정·권한" }.isShown) { "Settings panel did not expand" }
                views.filterIsInstance<Button>().first { it.text=="설정·권한" }.performClick()
            }
            waitForIdleSync()
            val directory=File(targetContext.getExternalFilesDir(null),"smoke").apply { mkdirs() }
            uiAutomation.takeScreenshot()?.let { bitmap ->
                File(directory,"settings.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            }
            // User shutdown is persistent; reopening must not immediately re-enable the microphone.
            settings.edit().putBoolean("voice-auto-enabled",false).commit()
            targetContext.stopService(Intent(targetContext,VoiceService::class.java))
            runOnMainSync { activity!!.finish() }
            waitForIdleSync()
            val startsAfter=VoiceService.foregroundStarts
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync(); Thread.sleep(700)
            check(VoiceService.foregroundStarts==startsAfter) { "Disabled voice service restarted on app launch" }
            result.putString("stream","PASS: Android launch, visible animated particle mesh rendering, hologram layout, button-free main screen, long-press settings, automatic foreground microphone service start after granted permissions, persistent shutdown on reopening, Keystore AES-GCM encrypted round-trip. Korean native offline model load and silence decoding passed. Wake vocabulary: $words. Wake accuracy, screen-off standby, real voice/API/ROOT/device remain unverified.\n")
            finish(Activity.RESULT_OK,result)
        } catch(t: Throwable) {
            result.putString("stream","FAIL: ${t.javaClass.simpleName}: ${t.message}\n")
            finish(Activity.RESULT_CANCELED,result)
        } finally { targetContext.stopService(Intent(targetContext,VoiceService::class.java)); activity?.let { runOnMainSync { it.finish() } } }
    }
}
