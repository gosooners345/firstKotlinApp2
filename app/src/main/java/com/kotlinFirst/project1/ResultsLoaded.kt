package com.kotlinFirst.project1


import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices.getFusedLocationProviderClient
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionText
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer
import kotlinx.android.synthetic.main.results_page.*
import java.util.*


class ResultsLoaded : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener, IBaseGpsListener {
    var ACTIVITY_ID: Int? = null
    var extractedTextString: String? = null
    //Speed Limit Scanner Location Variables


    private val UPDATE_INTERVAL = 10 * 1000 /* 10 secs */.toLong()
    private val FASTEST_INTERVAL: Long = 2000
    private var foregroundOnlyLocationService: LocationOnlyService? = null
    private lateinit var foregroundOnlyBroadcastReceiver: ForegroundOnlyBroadcastReceiver
    private var foregroundOnlyLocationServiceBound = false
    private lateinit var sharedPreferences: SharedPreferences

    var wordCount: Int? = null
    var locManager: LocationManager? = null
    var li: LocationListener? = null
    var speedLimitValue: String? = null
    var currentSpeed: String? = null
    var speed: Int? = null
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
        if (allPermissionsGranted()) {

            sharedPreferences =
                    getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE)
            foregroundOnlyBroadcastReceiver = ForegroundOnlyBroadcastReceiver()
            stopButton.setOnClickListener {
                val enabled = sharedPreferences.getBoolean(
                        SharedPreferenceUtil.KEY_FOREGROUND_ENABLED, false)

                if (enabled) {
                    foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
                } else {

                    // TODO: Step 1.0, Review Permissions: Checks and requests if needed.
                    if (allPermissionsGranted()) {
                        foregroundOnlyLocationService?.subscribeToLocationUpdates()
                                ?: Log.d("THISAPP", "Service Not Bound")
                    } else {


                        ActivityCompat.requestPermissions(this, LOCATION_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
                    }
                }
            }
            processPhotoResults(uri, true)
            resultImage.setImageURI(uri)
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
                                /*if (elementText.contains("MONOPOLY")) {
                                Toast.makeText(baseContext, elementText, Toast.LENGTH_SHORT).show()*/
                                //}
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
//Code for speed limit detection
        if (locationON!!) {

            if (extractedTextString!!.contains(("SPEED" + "\n" + "LIMIT"))) {
                val stringArray = extractedTextString!!.split("\n")
                speedLimitValue = "SPEED LIMIT " + stringArray[2]
                postedSpeedLimit = stringArray[2].toInt()

                this.updateSpeed(foregroundOnlyLocationService?.currentLocation as SpeedLimitLocation?)
                if (speed!! > postedSpeedLimit!!) {
                    Toast.makeText(this, "GOING TOO FAST", Toast.LENGTH_SHORT)
                }
            }

        }
    }


    override fun onLocationChanged(location: Location) {
        // TODO Auto-generated method stub
        if (location != null) {
            val myLocation = SpeedLimitLocation(location, false)
            this.updateSpeed(myLocation)
        }
    }


    private fun updateSpeed(location: SpeedLimitLocation?) {
        // TODO Auto-generated method stub

        var nCurrentSpeed = 0f
        if (location != null) {
            location.setUseMetricunits(this.useMetricUnits())
            nCurrentSpeed = location.speed
        }
        val fmt = Formatter(StringBuilder())
        fmt.format(Locale.US, "%5.1f", nCurrentSpeed)
        var strCurrentSpeed: String = fmt.toString()
        strCurrentSpeed = strCurrentSpeed.replace(' ', '0')
        var strUnits = "miles/hour"
        if (this.useMetricUnits()) {
            strUnits = "meters/second"
        }
        currentSpeed = "$strCurrentSpeed $strUnits"
        speed = try {
            strCurrentSpeed.toInt()
        } catch (ex: Exception) {
            Log.d("SPEED SCANNER", "Fail", ex.cause)
        }

    }

    private fun useMetricUnits(): Boolean {
        // TODO Auto-generated method stub
        return false
    }

    override fun onProviderDisabled(provider: String) {
        // TODO Auto-generated method stub
    }

    override fun onProviderEnabled(provider: String) {
        // TODO Auto-generated method stub
    }

    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        // TODO Auto-generated method stub
    }

    override fun onGpsStatusChanged(event: Int) {
        // TODO Auto-generated method stub
    }

    companion object {
        private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        private const val REQUEST_CODE_PERMISSIONS = 10

    }

    private inner class ForegroundOnlyBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(
                    LocationOnlyService.EXTRA_LOCATION
            )

            if (location != null) {
                logResultsToScreen("Foreground location: ${location.toText()}")

            }
        }
    }

    private fun updateButtonState(trackingLocation: Boolean) {
        if (trackingLocation) {
            stopButton.text = getString(R.string.stop_location_updates_button_text)
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

interface IBaseGpsListener : LocationListener, GpsStatus.Listener {
    override fun onLocationChanged(location: Location)
    override fun onProviderDisabled(provider: String)
    override fun onProviderEnabled(provider: String)
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle)
    override fun onGpsStatusChanged(event: Int)
}

class SpeedLimitLocation @JvmOverloads constructor(
        location: Location?,
        bUseMetricUnits: Boolean = true
) :
        Location(location) {
    var useMetricUnits = false
        private set

    fun setUseMetricunits(bUseMetricUntis: Boolean) {
        useMetricUnits = bUseMetricUntis
    }

    override fun distanceTo(dest: Location): Float {
        // TODO Auto-generated method stub
        var nDistance = super.distanceTo(dest)
        if (!useMetricUnits) {
            //Convert meters to feet
            nDistance = nDistance * 3.28083989501312f
        }
        return nDistance
    }

    override fun getAccuracy(): Float {
        // TODO Auto-generated method stub
        var nAccuracy = super.getAccuracy()
        if (!useMetricUnits) {
            //Convert meters to feet
            nAccuracy *= 3.28083989501312f
        }
        return nAccuracy
    }

    override fun getAltitude(): Double {
        // TODO Auto-generated method stub
        var nAltitude = super.getAltitude()
        if (!useMetricUnits) {
            //Convert meters to feet
            nAltitude = nAltitude * 3.28083989501312
        }
        return nAltitude
    }

    override fun getSpeed(): Float {
        // TODO Auto-generated method stub
        var nSpeed = super.getSpeed() * 3.6f
        if (!useMetricUnits) {
            //Convert meters/second to miles/hour
            nSpeed = nSpeed * 2.2369362920544f / 3.6f
        }
        return nSpeed
    }

    init {
        // TODO Auto-generated constructor stub
        useMetricUnits = bUseMetricUnits
    }
}
