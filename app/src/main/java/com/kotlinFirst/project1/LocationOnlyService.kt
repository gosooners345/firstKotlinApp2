package com.kotlinFirst.project1

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.common.util.SharedPreferencesUtils
import com.google.android.gms.location.*
import java.util.concurrent.TimeUnit

class LocationOnlyService : Service(), LocationListener {
    //location Variables
    private lateinit var locationRequest: LocationRequest
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    public lateinit var locationCallback: LocationCallback
    public var currentLocation: SLocation? = null

    //Service Binder
    private val localBinder = LocalBinder()

    //is it here?
    private var configurationChange = false


    var serviceRunningInForeground = false

    //Notifications
    private lateinit var notificationManager: NotificationManager
    override fun onCreate() {
        Log.d("LOCATIONPROV", "OnCreate()")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest().apply {
            interval = TimeUnit.SECONDS.toMillis(1)
            fastestInterval = TimeUnit.SECONDS.toMillis(0)
            maxWaitTime = TimeUnit.SECONDS.toMillis(1)
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult?) {
                super.onLocationResult(p0)
                if (p0?.lastLocation != null) {
                    if (serviceRunningInForeground) {
                        currentLocation = SLocation(p0.lastLocation)
                        Log.d(TAG, "Location updated ${currentLocation.toString()}")
                        ResultsLoaded.locationList.add(currentLocation!!)
                        var count = ResultsLoaded.locationList.count()

                        if (count > 1) {
                            Log.d(TAG, "More than one location update")
                            ResultsLoaded.locationList.updateSpeed(currentLocation, ResultsLoaded.locationList.get(count - 2))
                        } else {
                            Log.d(TAG, "Solo Location Speed Update")
                            ResultsLoaded.locationList.updateSpeed(currentLocation)
                        }
                        // ResultsLoaded.locationList.speed=updateSpeed(currentLocation)
                        val intent = Intent(ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST)
                        intent.putExtra(EXTRA_LOCATION, currentLocation)
                        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                    }
                } else {
                    Log.d(TAG, "Location missing in callback.")
                }
            }
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        Log.d(TAG, "onBind()")

        // MainActivity (client) comes into foreground and binds to service, so the service can
        // become a background services.
        stopForeground(true)
        serviceRunningInForeground = false
        configurationChange = false
        return localBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand()")

        val cancelLocationTrackingFromNotification =
                intent.getBooleanExtra(EXTRA_CANCEL_LOCATION_TRACKING_FROM_NOTIFICATION, false)

        if (cancelLocationTrackingFromNotification) {
            unsubscribeToLocationUpdates()
            stopSelf()
        }
        // Tells the system not to recreate the service after it's been killed.
        return START_NOT_STICKY
    }
    override fun onRebind(intent: Intent) {
        Log.d(TAG, "onRebind()")

        // MainActivity (client) returns to the foreground and rebinds to service, so the service
        // can become a background services.
        stopForeground(true)
        serviceRunningInForeground = false
        configurationChange = false
        super.onRebind(intent)
    }
    override fun onUnbind(intent: Intent): Boolean {
        Log.d(TAG, "onUnbind()")

        // MainActivity (client) leaves foreground, so service needs to become a foreground service
        // to maintain the 'while-in-use' label.
        // NOTE: If this method is called due to a configuration change in MainActivity,
        // we do nothing.
        if (!configurationChange && SharedPreferenceUtil.getLocationTrackingPref(this)) {
            Log.d(TAG, "Start foreground service")

        }

        // Ensures onRebind() is called if MainActivity (client) rebinds.
        return true
    }
    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationChange = true
    }

    //Handles Location subscription information
    fun subscribeToLocationUpdates() {
        Log.d(TAG, "subscribeToLocationUpdates()")

        SharedPreferenceUtil.saveLocationTrackingPref(this, true)

        // Binding to this service doesn't actually trigger onStartCommand(). That is needed to
        // ensure this Service can be promoted to a foreground service, i.e., the service needs to
        // be officially started (which we do here).
        startService(Intent(applicationContext, LocationOnlyService::class.java))

        try {

            fusedLocationProviderClient.requestLocationUpdates(
                    locationRequest, locationCallback, Looper.myLooper())
            Log.i(TAG, "Location updates activated. ")
        } catch (unlikely: SecurityException) {
            SharedPreferenceUtil.saveLocationTrackingPref(this, false)
            Log.e(TAG, "Lost location permissions. Couldn't remove updates. $unlikely")
        }
    }
    fun unsubscribeToLocationUpdates() {
        Log.d(TAG, "unsubscribeToLocationUpdates()")

        try {
            // TODO: Step 1.6, Unsubscribe to location changes.
            val removeTask = fusedLocationProviderClient.removeLocationUpdates(locationCallback)
            removeTask.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Location Callback removed.")
                    stopSelf()
                } else {
                    Log.d(TAG, "Failed to remove Location Callback.")
                }
            }

            SharedPreferenceUtil.saveLocationTrackingPref(this, false)

        } catch (unlikely: SecurityException) {
            SharedPreferenceUtil.saveLocationTrackingPref(this, true)
            Log.e(TAG, "Lost location permissions. Couldn't remove updates. $unlikely")
        }
    }

    inner class LocalBinder : Binder() {
        internal val service: LocationOnlyService
            get() = this@LocationOnlyService
    }

    override fun onLocationChanged(p0: Location?) {}

    companion object {
        private const val TAG = "LocationOnlyService"

        private const val PACKAGE_NAME = R.string.app_name

        internal const val ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST =
                "$PACKAGE_NAME.action.FOREGROUND_ONLY_LOCATION_BROADCAST"

        internal const val EXTRA_LOCATION = "$PACKAGE_NAME.extra.LOCATION"

        private const val EXTRA_CANCEL_LOCATION_TRACKING_FROM_NOTIFICATION =
                "$PACKAGE_NAME.extra.CANCEL_LOCATION_TRACKING_FROM_NOTIFICATION"
    }
}