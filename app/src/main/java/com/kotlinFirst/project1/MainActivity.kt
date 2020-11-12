package com.kotlinFirst.project1

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        upload_Button.setOnClickListener {
            val intent = Intent()
            intent.type = "image/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(Intent.createChooser(intent, "Select Picture"), IMAGE_PROCESSING)

        }
        camera_button.setOnClickListener {
            val intent = Intent(applicationContext, CameraActivity::class.java)
            startActivity(intent)

        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PROCESSING && resultCode == RESULT_OK) {


            if (data != null) {
                val uri = data.data

                val resultData = Intent(applicationContext, ResultsLoaded::class.java)
                resultData.putExtra("IMAGE", uri.toString())
                resultData.putExtra("ACTIVITY_ID", ACTIVITY_ID)
                startActivity(resultData)
            }

        }


    }

    companion object {
        private const val TAG = "Speed-Limit"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission_group.LOCATION)
        const val IMAGE_PROCESSING = 2
        const val ACTIVITY_ID = 11
    }

}



