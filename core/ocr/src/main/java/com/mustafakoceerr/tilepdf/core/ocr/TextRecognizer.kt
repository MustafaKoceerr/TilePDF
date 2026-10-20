package com.mustafakoceerr.tilepdf.core.ocr

import android.graphics.Bitmap
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads the words in a picture of a page. */
interface TextRecognizer {
    suspend fun recognize(bitmap: Bitmap): List<PixelWord>
}

/**
 * ML Kit's Latin recogniser, whose model ships inside the app: recognition
 * runs on the device and works offline. Pages never leave the device; ML
 * Kit's own usage metrics may, which the Data safety answers declare.
 */
@Singleton
class MlKitTextRecognizer @Inject constructor() : TextRecognizer {

    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(bitmap: Bitmap): List<PixelWord> {
        val text = suspendCancellableCoroutine<Text> { continuation ->
            client.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }
        return text.textBlocks.flatMap { block -> block.lines.flatMap { it.elements } }.mapNotNull { element ->
            val corners = element.cornerPoints ?: return@mapNotNull null
            PixelWord(element.text, corners.map { PdfPoint(it.x.toFloat(), it.y.toFloat()) })
        }
    }
}
