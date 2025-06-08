package io.github.turtlepaw.mindsky.logic

import android.content.Context
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import java.io.File

class PostEmbedder(val context: Context) {
    private suspend fun getModel(): SentenceEmbedding {
        val sentenceEmbedding = SentenceEmbedding()

        val modelFile = File(context.filesDir, ModelDownloadWorker.MODEL_FILENAME)
        val tokenizerFile = File(context.filesDir, ModelDownloadWorker.TOKENIZER_FILENAME)
        val tokenizerBytes = tokenizerFile.readBytes()

        sentenceEmbedding.init(
            modelFilepath = modelFile.absolutePath,
            tokenizerBytes = tokenizerBytes,
            useTokenTypeIds = true,

            outputTensorName = "sentence_embedding",
            useFP16 = false,
            useXNNPack = false,
            normalizeEmbeddings = true
        )

        return sentenceEmbedding
    }

    suspend fun encode(text: String): FloatArray {
        return getModel().encode(text)
    }
}