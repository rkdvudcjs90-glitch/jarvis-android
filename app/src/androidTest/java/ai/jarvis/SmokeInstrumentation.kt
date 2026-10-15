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
            targetContext.getSharedPreferences("settings",0).edit().putBoolean("onboarded",true).commit()
            val vault=Vault(targetContext)
            val sample="emulator-only-test-value-한국어"
            vault.put("smoke",sample)
            check(Vault(targetContext).get("smoke")==sample) { "Keystore round-trip failed" }
            val raw=targetContext.getSharedPreferences("vault",0).getString("smoke","") ?: ""
            check(!raw.contains(sample) && raw.isNotBlank()) { "Plaintext stored" }
            vault.clear()
            activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitForIdleSync()
            Thread.sleep(2000) // Permit EGL surface creation; this is an emulator test, not a production delay.
            runOnMainSync {
                val views=descendants(activity!!.window.decorView)
                check(views.filterIsInstance<Hologram>().any { it.width>0 && it.height>0 }) { "Hologram has no layout" }
                check(views.filterIsInstance<TextView>().any { it.text.contains("J.A.R.V.I.S.") }) { "Main UI missing" }
                views.filterIsInstance<Button>().first { it.text=="설정·권한" }.performClick()
            }
            waitForIdleSync()
            val directory=File(targetContext.getExternalFilesDir(null),"smoke").apply { mkdirs() }
            uiAutomation.takeScreenshot()?.let { bitmap ->
                File(directory,"settings.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
            }
            result.putString("stream","PASS: Android launch, hologram layout, settings entry, Keystore AES-GCM encrypted round-trip. No API/voice/ROOT/device verification.\n")
            finish(Activity.RESULT_OK,result)
        } catch(t: Throwable) {
            result.putString("stream","FAIL: ${t.javaClass.simpleName}: ${t.message}\n")
            finish(Activity.RESULT_CANCELED,result)
        } finally { activity?.let { runOnMainSync { it.finish() } } }
    }
}
