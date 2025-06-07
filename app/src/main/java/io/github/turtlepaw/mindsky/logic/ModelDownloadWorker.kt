package io.github.turtlepaw.mindsky.logic

import android.content.Context
import android.os.Build
import androidx.work.*
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File

enum class DownloadStage {
    Model,
    Tokenizer,
}

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val MODEL_ARM64 = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_qint8_arm64.onnx"
        private const val MODEL_CROSS = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_O4.onnx"
        private const val TOKENIZER_URL = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json"

        const val MODEL_FILENAME = "model.onnx"
        const val TOKENIZER_FILENAME = "tokenizer.json"

        private const val KEY_STAGE = "stage"

        fun buildWorkRequest(stage: DownloadStage): OneTimeWorkRequest {
            return OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setInputData(workDataOf(KEY_STAGE to stage.name))
                .build()
        }
    }

    private fun getDownloadUrl(stage: DownloadStage): String {
        return when (stage) {
            DownloadStage.Model -> {
                val arch = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase() ?: return MODEL_CROSS
                if ("arm64" in arch) MODEL_ARM64 else MODEL_CROSS
            }

            DownloadStage.Tokenizer -> TOKENIZER_URL
        }
    }

    private fun getOutputFilename(stage: DownloadStage): String {
        return when (stage) {
            DownloadStage.Model -> MODEL_FILENAME
            DownloadStage.Tokenizer -> TOKENIZER_FILENAME
        }
    }

    override suspend fun doWork(): Result {
        val stageName = inputData.getString(KEY_STAGE) ?: DownloadStage.Model.name
        val stage = DownloadStage.valueOf(stageName)
        setProgress(workDataOf("progress" to 0, "stage" to stage.name))

        if(stage == DownloadStage.Tokenizer){
            // delay to let user read text
            delay(1200)
        }

        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(getDownloadUrl(stage)).build()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful || response.body == null) {
                return Result.failure()
            }

            val totalBytes = response.body!!.contentLength()
            val source = response.body!!.source()
            val file = File(applicationContext.filesDir, getOutputFilename(stage))
            val sink = file.sink().buffer()

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Long = 0
            var read: Int

            while (source.inputStream().read(buffer).also { read = it } != -1) {
                sink.write(buffer, 0, read)
                bytesRead += read

                // Report progress
                val progress = (bytesRead * 100 / totalBytes).toInt()
                setProgress(workDataOf("progress" to progress, "stage" to stage.name))
            }

            sink.close()
            source.close()

            // If just finished model, queue tokenizer
            if (stage == DownloadStage.Model) {
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "modelDownload",
                    ExistingWorkPolicy.REPLACE,
                    buildWorkRequest(DownloadStage.Tokenizer)
                )
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
