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
            speed = location?.speed
        else {
            this.speed = location?.speed?.times(-3.6f)
            //  this.speed = (this.speed!!*2.2369362920544f / 3.6f)
        }
    }

    fun updateSpeed(location1: Location?, location2: Location?) {

        this.speed = ((location1!!.distanceTo(location2) * 3.281f) / 5280f / (location2!!.elapsedRealtimeNanos - location1!!.elapsedRealtimeNanos))// (location2!!.time - location1.time))
        //this.speed =(this.speed!! * 2.237f)

        var doubleMiles = (location1!!.distanceTo(location2).toDouble() * 3.281) / 5280.00
        //  this.speed=(speed!!*2.2369362920544f / 3.6f)
    }


}