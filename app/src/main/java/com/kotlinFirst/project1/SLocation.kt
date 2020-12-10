package com.kotlinFirst.project1

import android.location.Location
import java.text.SimpleDateFormat
import java.util.*


class SLocation @JvmOverloads constructor(location: Location?) : Location(location) {


    fun getTimeStamp(): String {
        val date = Calendar.getInstance().time
        val formatter = SimpleDateFormat.getDateTimeInstance()
        val formattedDate = formatter.format(date)
        return formattedDate
    }

    override fun toString(): String {
        return if (this != null) {
            "($latitude, $longitude, ${getTimeStamp()})"
        } else {
            "Unknown location"
        }
    }

    init {
        // TODO Auto-generated constructor stub

    }
}