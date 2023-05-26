package com.shong.sample

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.shong.imageconverter.ImageConverter

// TODO: 설명, 예제 업로드하자
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        val imageByteArray = ImageConverter.convertToJpgByteArray(this, uri)
//        val bitmap = ImageConverter.byteArrayToBitmap(imageByteArray ?: byteArrayOf())

        val imageConverter = ImageConverter(this).apply {
            initialize()
        }

        imageConverter.setOnUriListener(object : ImageConverter.OnUriListener {
            override fun onUri(uri: Uri) {
                findViewById<ImageView>(R.id.imageView).setImageURI(uri)
            }

            override fun onError(msg: String) {
                TODO("Not yet implemented")
            }
        })

        findViewById<Button>(R.id.cameraButton).setOnClickListener {
            imageConverter.getUriForCamera(this)
        }

        findViewById<Button>(R.id.albumButton).setOnClickListener {
            imageConverter.getUriForAlbum()
        }
    }

}