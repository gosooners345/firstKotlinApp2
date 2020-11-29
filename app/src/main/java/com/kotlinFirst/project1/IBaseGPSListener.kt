package com.kotlinFirst.project1

import android.location.GpsStatus
import android.location.Location
import android.location.LocationListener
import android.os.Bundle

public interface IBaseGpsListener : LocationListener, GpsStatus.Listener {
    override fun onLocationChanged(location: Location)
    override fun onProviderDisabled(provider: String)
    override fun onProviderEnabled(provider: String)
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle)
    override fun onGpsStatusChanged(event: Int)
}