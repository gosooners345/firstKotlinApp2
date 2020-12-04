package com.kotlinFirst.project1

import android.location.Location
import androidx.annotation.FloatRange
import kotlin.math.roundToInt

class LocationList : ArrayList<Location>() {
    var speed: Float? = null

    init {
        speed = 0f
    }

    fun updateSpeed(location: Location?) {
        if (location!!.hasSpeed())
           this.speed = location.speed * 2.237f
        else {
            this.speed = 0.0f
        }
    }

    fun updateSpeed(location1: Location?, location2: Location?) {

        this.speed = ((location2!!.distanceTo(location1)) / ((location1!!.elapsedRealtimeNanos / 1000000000f) - (location2!!.elapsedRealtimeNanos / 1000000000f))) * 2.237f
    }

    fun speedString(): String? {
        return String.format("%5.5f MPH", this.speed)
    }

}