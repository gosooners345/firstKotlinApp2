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
        setContentView(R.layout.results_page)
        uri = Uri.parse(intent.getStringExtra("IMAGE"))
        title = intent.getStringExtra("MODE")
        if (allPermissionsGranted()) {

            sharedPreferences =
                    getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
            foregroundOnlyBroadcastReceiver = ForegroundOnlyBroadcastReceiver()
            stopButton.setOnClickListener {
                val enabled = sharedPreferences.getBoolean(
                        SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)

                if (!enabled) {
                    // TODO: Step 1.0, Review Permissions: Checks and requests if needed.
                    if (allPermissionsGranted()) {
                        foregroundOnlyLocationService?.subscribeToLocationUpdates()
                                ?: Log.d("THISAPP", "Service Not Bound")

                    } else
                        ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_CODE_PERMISSIONS)

                } else
                    foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
            }
            if (title == "Speed Limit Scanner") {
                processPhotoResults(uri, true)
                resultImage.setImageURI(uri)
            } else {
                processPhotoResults(uri, false)
                resultImage.setImageURI(uri)
            }
        } else
            ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_CODE_PERMISSIONS)

    }


    //This should really transfer to a different class soon
    override fun onStart() {
        super.onStart()

        updateButtonState(
                sharedPreferences.getBoolean(SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)
        )
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)

        val serviceIntent = Intent(this, LocationOnlyService::class.java)
        bindService(serviceIntent, foregroundOnlyServiceConnection, Context.BIND_AUTO_CREATE)
        speed = updateSpeed(foregroundOnlyLocationService?.currentLocation)
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
                foregroundOnlyBroadcastReceiver,
                IntentFilter(
                        LocationOnlyService.ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
                foregroundOnlyBroadcastReceiver
        )
        super.onPause()
    }

    override fun onStop() {
        if (foregroundOnlyLocationServiceBound) {
            unbindService(foregroundOnlyServiceConnection)
            foregroundOnlyLocationServiceBound = false
        }
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)

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

    private fun processPhotoResults(uri: Uri?, locationON: Boolean?) {
        var fireText: FirebaseVisionText?
        //Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
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
        resultStatsView.text = result.result?.textBlocks?.size.toString()
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
    }


    public fun updateSpeedViews() {

        Toast.makeText(this, locationList!!.speed.toString(), Toast.LENGTH_SHORT).show()
        currSpeedLabel.text = (locationList.speed!!).toString() + " MPH"

        if (locationList.speed!! > postedSpeedLimit!!) {
            Toast.makeText(this, "GOING TOO FAST", Toast.LENGTH_SHORT).show()
            currSpeedLabel.setTextColor(Color.RED)
        }
        Toast.makeText(this, speed.toString(), Toast.LENGTH_LONG).show()


    }

    private fun updateSpeed(location: Location?): Float {
        // TODO Auto-generated method stub
        var nCurrentSpeed = 0f

        if (location != null) {

            nCurrentSpeed = location.speed * 3.6f
            nCurrentSpeed = nCurrentSpeed * 2.2369362920544f / 3.6f
        }
        return nCurrentSpeed
        /*val fmt = Formatter(StringBuilder())
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed)
        var strCurrentSpeed = fmt.toString()
        strCurrentSpeed = strCurrentSpeed.replace(' ', '0')
        var strUnits = "miles/hour"*/


    }

    private fun updateSpeed(location: Location?, lastLocation: Location?): Float {
        // TODO Auto-generated method stub
        var nCurrentSpeed = 0f
        var speed = 0f
        if (location != null) {
            /* Math.sqrt(Math.pow(location.longitude-lastLocation!!.longitude,2.0) +Math.pow(
                location.latitude - lastLocation.latitude, 2.0)
            )*/
            speed = (lastLocation!!.distanceTo(location) / (location.time - lastLocation.time))
            speed = speed * 2.2369362920544f / 3.6f
            if (location.hasSpeed()) {
                speed = location.speed * 2.2369362920544f / 3.6f
            }

        }


        /*  if (location != null) {
               var sLocation : SLocation = location as SLocation
            nCurrentSpeed = sLocation.speed
           nCurrentSpeed = location.speed * 3.6f
           nCurrentSpeed = nCurrentSpeed * 2.2369362920544f / 3.6f


       }*/
        nCurrentSpeed = Math.round(speed).toFloat()
        return nCurrentSpeed

        /*val fmt = Formatter(StringBuilder())
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed)
        var strCurrentSpeed = fmt.toString()
        strCurrentSpeed = strCurrentSpeed.replace(' ', '0')
        var strUnits = "miles/hour"*/


    }

    override fun onLocationChanged(location: Location) {
        updateSpeedViews()
        logResultsToScreen(location.toText())

    }

    override fun onProviderDisabled(provider: String) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
    override fun onGpsStatusChanged(event: Int) {}

    override fun onBackPressed() {
        if (stopButton.text.contains("Stop Updates"))
            stopButton.performClick()
        super.onBackPressed()

    }


    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        private const val REQUEST_CODE_PERMISSIONS = 10
        var locationList: LocationList = LocationList()
        var speed = 0f
    }

    private inner class ForegroundOnlyBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(
                    LocationOnlyService.EXTRA_LOCATION
            )

            if (location != null) {
                logResultsToScreen("Foreground location: ${location.toText()}")
                updateSpeedViews()

            }
        }
    }

    private fun updateButtonState(trackingLocation: Boolean) {
        if (trackingLocation) {
            stopButton.text = getString(R.string.stop_location_updates_button_text)
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
                Log.d("Speed Test", "ExecutorMethod Updating speed")
                updateSpeedViews()
            }, 1, 15, TimeUnit.SECONDS)

        } else {
            stopButton.text = getString(R.string.start_location_updates_button_text)

        }
    }

    private fun logResultsToScreen(output: String) {
        val outputWithPreviousLogs = "$output\n${resultStatsView.text}"
        resultStatsView.text = outputWithPreviousLogs
    }

    override fun onSharedPreferenceChanged(p0: SharedPreferences?, p1: String?) {
        if (p1 == SharedPreferenceUtil.KEY_FOREGROUND_ENABLED) {
            updateButtonState(sharedPreferences.getBoolean(
                    SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)
            )
        }
    }
}




