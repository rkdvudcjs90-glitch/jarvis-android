package ai.jarvis

import android.content.Context
import org.vosk.Model
import java.io.File

object LocalModel {
    @Synchronized fun load(context: Context): Model {
        val directory=File(context.filesDir,"korean-model-v1")
        val ready=File(directory,".ready")
        if(!ready.exists()) {
            directory.deleteRecursively(); check(directory.mkdirs())
            fun copy(asset: String, output: File) {
                val children=context.assets.list(asset) ?: error("모델 파일 누락")
                if(children.isEmpty()) context.assets.open(asset).use { input -> output.outputStream().use { input.copyTo(it) } }
                else { output.mkdirs(); children.forEach { copy("$asset/$it",File(output,it)) } }
            }
            copy("wake-model",directory)
            ready.writeText("vosk-model-small-ko-0.22")
        }
        return Model(directory.absolutePath)
    }
}
