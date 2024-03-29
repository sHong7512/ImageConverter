package com.shong.sample

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
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
    }

}