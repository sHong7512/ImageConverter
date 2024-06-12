package com.shong.sample

import android.graphics.Bitmap
import android.net.Uri
import android.opengl.Visibility
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.shong.imageconverter.ImageConverter

// TODO: 설명, 예제 업로드하자
class MainActivity : AppCompatActivity() {
    private var currentUri: Uri? = null;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val imageConverter = ImageConverter(this).apply {
            initialize()
        }

        imageConverter.setOnUriListener(object : ImageConverter.OnUriListener {
            override fun onUri(uri: Uri) {
                findViewById<ImageView>(R.id.imageView).setImageURI(uri)
                currentUri = uri
            }

            override fun onError(msg: String) {
                // ...
            }
        })

        findViewById<Button>(R.id.cameraButton).setOnClickListener {
            imageConverter.getUriForCamera(this)
        }

        findViewById<Button>(R.id.albumButton).setOnClickListener {
            imageConverter.getUriForAlbum()
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            if (currentUri != null) {
                val imageByteArray = ImageConverter.convertToJpgByteArray(this, currentUri!!)
                if (imageByteArray != null) {
                    val bitmap = ImageConverter.byteArrayToBitmap(imageByteArray)
                    ImageConverter.imageSaveJPEG(this, bitmap, 100)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 26) {
            findViewById<Button>(R.id.captureFullButton).setOnClickListener {
                ImageConverter.captureFullScreen(this, object : ImageConverter.OnCaptureListener {
                    override fun onComplete(bitmap: Bitmap) {
                        ImageConverter.imageSaveJPEG(
                            activity = this@MainActivity,
                            bitmap = bitmap,
                            saveQuality = 100,
                            dirPathUser = "DCIM/imageConv",
                            fileNameUser = "imageConv_${System.currentTimeMillis()}"
                        )
                    }

                    override fun onFailed() {
                        Log.d("example", "error!")
                    }
                })
            }

            findViewById<Button>(R.id.captureImageButton).setOnClickListener {
                val imageView = findViewById<ImageView>(R.id.imageView)
                ImageConverter.captureFromView(
                    this,
                    imageView,
                    object : ImageConverter.OnCaptureListener {
                        override fun onComplete(bitmap: Bitmap) {
                            ImageConverter.imageSaveJPEG(
                                activity = this@MainActivity,
                                bitmap = bitmap,
                                saveQuality = 100,
                                dirPathUser = "DCIM/imageConv",
                                fileNameUser = "imageConv_${System.currentTimeMillis()}"
                            )
                        }

                        override fun onFailed() {
                            Log.d("example", "error!")
                        }
                    })
            }
        } else {
            findViewById<Button>(R.id.captureFullButton).visibility = View.GONE
            findViewById<Button>(R.id.captureImageButton).visibility = View.GONE
        }

    }

}