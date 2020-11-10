package com.kotlinFirst.project1

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer
import kotlinx.android.synthetic.main.results_page.*

class ResultsLoaded : AppCompatActivity() {
    var ACTIVITY_ID: Int? = null
    var extractedTextString: String? = null
    var wordCount: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.results_page)
        val uri = Uri.parse(intent.getStringExtra("IMAGE"))
        processPhotoResults(uri)
        resultImage.setImageURI(uri)

    }

    private fun processPhotoResults(uri: Uri?) {
        var fireText: FirebaseVisionText?
        //Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
        var detectedWords: String?
        val image: FirebaseVisionImage =
                FirebaseVisionImage.fromFilePath(this, uri!!)


        val detector: FirebaseVisionTextRecognizer = FirebaseVision.getInstance()
                .onDeviceTextRecognizer
        val result = detector.processImage(image)
                .addOnSuccessListener { firebaseVisionText ->
                    fireText = firebaseVisionText
                    for (block in fireText!!.textBlocks) {
                        val blockText = block.text
                        val blockConfidence = block.confidence
                        val blockLanguages = block.recognizedLanguages
                        val blockCornerPoints = block.cornerPoints
                        val blockFrame = block.boundingBox

                        for (line in block.lines) {
                            val lineText = line.text
                            val lineConfidence = line.confidence
                            val lineLanguages = line.recognizedLanguages
                            val lineCornerPoints = line.cornerPoints
                            val lineFrame = line.boundingBox

                            for (element in line.elements) {
                                val elementText = element.text
                                val elementConfidence = element.confidence
                                val elementLanguages = element.recognizedLanguages
                                val elementCornerPoints = element.cornerPoints
                                val elementFrame = element.boundingBox
                                if (elementText.contains("MONOPOLY")) {
                                    Toast.makeText(baseContext, elementText, Toast.LENGTH_SHORT).show()
                                }
                            }

                        }
                    }
                }
                .addOnFailureListener { _ ->
                    extractedTextString = "failed"
                }
        while (!result.isComplete) {
            Log.e("Waitin", "Not Done Yet")
            if (textBox.text != null)
                continue
        }
        extractedTextString = (if (result.isComplete) result.result?.text; else {
            "not yet"
        }).toString()
        Toast.makeText(this, extractedTextString, Toast.LENGTH_SHORT).show()
        textBox.text = extractedTextString
        resultStatsView.text = result.result?.textBlocks?.size.toString()

    }

}