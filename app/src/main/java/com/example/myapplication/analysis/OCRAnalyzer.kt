package com.example.myapplication.analysis

import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object OCRAnalyzer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun processImageProxy(
        imageProxy: ImageProxy,
        onResult: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                val text = result.text
                Log.d("OCRAnalyzer", "Recognized text length=${text.length}")
                onResult(text)
                imageProxy.close()
            }
            .addOnFailureListener { e ->
                onError(e)
                imageProxy.close()
            }
    }
}
