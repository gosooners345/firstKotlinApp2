package com.kotlinFirst.project1


import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer
import kotlinx.android.synthetic.main.results_page.*
import java.lang.StringBuilder
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList


class ResultsLoaded : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, IBaseGpsListener {
    var ACTIVITY_ID: Int? = null
    var extractedTextString: String? = null
    //Speed Limit Scanner Location Variables


    var foregroundOnlyLocationService: LocationOnlyService? = null
    private lateinit var foregroundOnlyBroadcastReceiver: ForegroundOnlyBroadcastReceiver
    private var foregroundOnlyLocationServiceBound = false
    lateinit var timer: CountDownTimer
    private lateinit var sharedPreferences: SharedPreferences
    lateinit var locationCallback: Object
    var firstLocation: Location? = null
    var wordCount: Int? = null
    var locManager: LocationManager? = null
    var li: LocationListener? = null
    var speedLimitValue: String? = null
    var currentSpeed: String? = null
    var listCount: Int = 0
    var uri: Uri? = null
    var postedSpeedLimit: Int? = null

    private val foregroundOnlyServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocationOnlyService.LocalBinder
            foregroundOnlyLocationService = binder.service
            foregroundOnlyLocationServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            foregroundOnlyLocationService = null
            foregroundOnlyLocationServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        uri = Uri.parse(intent.getStringExtra("IMAGE"))
        title = intent.getStringExtra("MODE")
        if (title.contains("Speed")) {
            setContentView(R.layout.results_page)

        } else {
            setContentView(R.layout.document_results)
            processPhotoResults(uri, false)
            resultImage.setImageURI(uri)
        }
        if (allPermissionsGranted() && title.contains("Speed")) {

            sharedPreferences =
                    getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
            foregroundOnlyBroadcastReceiver = ForegroundOnlyBroadcastReceiver()

            stopButton.setOnClickListener {
                val enabled = sharedPreferences.getBoolean(
                        SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)

                if (!enabled) {

                    if (allPermissionsGranted()) {
                        foregroundOnlyLocationService?.serviceRunningInForeground = true
                        foregroundOnlyLocationService?.subscribeToLocationUpdates()
                                ?: Log.d("THISAPP", "Service Not Bound")

                    } else
                        ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_CODE_PERMISSIONS)

                } else {
                    foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
                    foregroundOnlyLocationService?.serviceRunningInForeground = false
                }
            }
            clear_button.setOnClickListener {
                resultStatsView.text = ""
                currSpeedLabel.text = "0.000 MPH"

            }
            if (title == "Speed Limit Scanner") {
                processPhotoResults(uri, true)
                //resultImage.setImageURI(uri)
            } else {
                processPhotoResults(uri, false)
                resultImage.setImageURI(uri)
            }
        } else
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_CODE_PERMISSIONS)

    }



    override fun onStart() {
        super.onStart()
        if (title.contains("Speed")) {
            updateButtonState(
                    sharedPreferences.getBoolean(SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)
            )
            sharedPreferences.registerOnSharedPreferenceChangeListener(this)

            val serviceIntent = Intent(this, LocationOnlyService::class.java)
            bindService(serviceIntent, foregroundOnlyServiceConnection, Context.BIND_AUTO_CREATE)
        }

    }

    override fun onResume() {
        super.onResume()
        if (title.contains("Speed")) {
            // stopButton.performClick()
            LocalBroadcastManager.getInstance(this).registerReceiver(
                    foregroundOnlyBroadcastReceiver,
                    IntentFilter(
                            LocationOnlyService.ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST)
            )
        }
    }

    override fun onPause() {
        if (title.contains("Speed")) {
            stopButton.performClick()
            LocalBroadcastManager.getInstance(this).unregisterReceiver(
                    foregroundOnlyBroadcastReceiver

            )
            if (foregroundOnlyLocationServiceBound) {
                unbindService(foregroundOnlyServiceConnection)
                foregroundOnlyLocationServiceBound = false
            }
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }
        super.onPause()
    }

    override fun onStop() {
        if (title.contains("Speed")) {
            stopButton.performClick()

            if (foregroundOnlyLocationServiceBound) {
                unbindService(foregroundOnlyServiceConnection)
                foregroundOnlyLocationServiceBound = false
            }
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        }
        super.onStop()
    }


    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults:
            IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                foregroundOnlyLocationService?.subscribeToLocationUpdates()

                processPhotoResults(uri, true)
                resultImage.setImageURI(uri)

            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show()
                processPhotoResults(uri, false)
                resultImage.setImageURI(uri)
            }
        }

    }

    private fun allPermissionsGranted() = LOCATION_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    //Process photo results and get text for extraction
    private fun processPhotoResults(uri: Uri?, locationON: Boolean?) {
        var fireText: FirebaseVisionText?

        var detectedWords: String?
        val image: FirebaseVisionImage =
                FirebaseVisionImage.fromFilePath(this, uri!!)
        val extractMode = intent.getStringExtra("MODE")
        title = extractMode

        //Text Extraction
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
                        if (blockText.contains("SPEED'\n'LIMIT")) {
                            Toast.makeText(baseContext, blockText, Toast.LENGTH_SHORT).show()

                        }
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

                            }

                        }
                    }
                }
                .addOnFailureListener { _ ->
                    extractedTextString = "failed"
                }
        while (!result.isComplete) {

            if (textBox.text != null)
                continue
        }
        extractedTextString = (if (result.isComplete) result.result?.text; else {
            "not yet"
        }).toString()
        Toast.makeText(this, extractedTextString, Toast.LENGTH_SHORT).show()
        textBox.text = extractedTextString
        resultsView.text = result.result?.textBlocks?.size.toString()
        if (title.contains("Speed")) {
            try {
//Code for speed limit detection
                if (locationON!!) {
                    if (extractedTextString!!.toUpperCase().contains(("SPEED" + "\n" + "LIMIT")) || extractedTextString!!.toUpperCase().contains("SPEED")) {
                        stopButton.performClick()
                        val stringArray = extractedTextString!!.split("\n")
                        speedLimitValue = "SPEED LIMIT " + stringArray[2]
                        postedSpeedLimit = stringArray[2].toInt()
                        listCount = locationList.count()
                        postedSpeedLimitLabel.text = postedSpeedLimit.toString() + " MPH"
                        updateSpeedViews()

                    }
                }
            } catch (ex: Exception) {
                Log.d("FAIL", ex.message)
                ex.printStackTrace()
                currSpeedLabel.text = ex.message
            }
        } else {
            Toast.makeText(this, "Nothing new here", Toast.LENGTH_LONG).show()
        }
    }

    // Updates the speed limit scanner text fields
    fun updateSpeedViews() {

        currSpeedLabel.text = (locationList!!.speed!!).toString() + " MPH"
        val fmt = Formatter(StringBuilder())
        fmt.format(Locale.US, "%5.5f", locationList!!.speed)
        var curStrSpeed = fmt.toString()

        if (curStrSpeed.contains("NaN"))
            curStrSpeed = "0.0"
        currSpeedLabel.text = curStrSpeed + " MPH"
        logResultsToScreen("Foreground location: ${foregroundOnlyLocationService?.currentLocation?.toText()}")
        logResultsToScreen("Speed is $curStrSpeed  MPH")

        if (locationList!!.speed!! > postedSpeedLimit!!) {
            logResultsToScreen("Going Too FAST!!")
            currSpeedLabel.setTextColor(Color.RED)
        } else {
            currSpeedLabel.setTextColor(Color.BLACK)
        }

    }

    //Implements of Location class really not needed, but here because
    override fun onLocationChanged(location: Location) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
    override fun onGpsStatusChanged(event: Int) {}
    override fun onBackPressed() {
        if (title.contains("Speed"))
            if (stopButton.text.contains("Stop Updates"))
                stopButton.performClick()
        super.onBackPressed()

    }

    //Handles Location broadcast
    private inner class ForegroundOnlyBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(
                    LocationOnlyService.EXTRA_LOCATION
            )
            if (location != null) {
                updateSpeedViews()
            }
        }
    }

    private fun updateButtonState(trackingLocation: Boolean) {
        if (title.contains("Speed"))
            if (trackingLocation) {
                stopButton.text = getString(R.string.stop_location_updates_button_text)
                firstLocation = foregroundOnlyLocationService?.currentLocation


            } else {
                stopButton.text = getString(R.string.start_location_updates_button_text)

            }
    }
    private fun logResultsToScreen(output: String) {
        val outputWithPreviousLogs = "$output\r\n${resultStatsView.text}"
        resultStatsView.text = outputWithPreviousLogs
    }

    override fun onSharedPreferenceChanged(p0: SharedPreferences?, p1: String?) {
        if (title.contains("Speed"))
            if (p1 == SharedPreferenceUtil.KEY_FOREGROUND_ENABLED) {
                updateButtonState(sharedPreferences.getBoolean(
                        SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)
                )
            }
    }


    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        private const val REQUEST_CODE_PERMISSIONS = 10
        var locationList: LocationList = LocationList()
    }
}