package com.shong.imageconverter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val TAG = this::class.java.simpleName + "_sHong"

    private lateinit var photoView: ImageView
    private val imageConverter: ImageConverter by lazy {
        ImageConverter(this)
    }

    var cameraPhotoFilePath: Uri? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermission()

        photoView = findViewById(R.id.photoView)

        val resultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { result ->

            if (result?.resultCode == Activity.RESULT_OK) {
                val selectedImageUri : Uri
                if(cameraPhotoFilePath != null){
                    selectedImageUri = cameraPhotoFilePath ?: return@registerForActivityResult
                }else{
                    selectedImageUri= result.data?.data ?: return@registerForActivityResult
                }
                cameraPhotoFilePath = null

                val encodedImageStr = imageConverter.encodeBase64(selectedImageUri) ?: return@registerForActivityResult
                val imageByteArray = imageConverter.stringToByteArray(encodedImageStr)
                val bitmap = imageConverter.byteArrayToBitmap(imageByteArray)

                if(bitmap.byteCount > 0) photoView.setImageBitmap(bitmap)
                else photoView.setImageResource(R.drawable.camera)
            }
        }

        findViewById<Button>(R.id.cameraButton).setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val photoFile: File? = try {
                val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                val imagePath = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                File(imagePath, "JPEG_${timeStamp}_" + ".jpg")
            } catch (e: Exception) {
                Log.d(TAG,"file create ERROR! : $e")
                null
            }

            photoFile?.also { file ->
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "com.shong.imageconverter.provider",
                    file
                )
                cameraPhotoFilePath = photoURI
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            resultLauncher.launch(intent)
        }

        findViewById<Button>(R.id.albumButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")

            resultLauncher.launch(intent)
        }
    }

    private val EXTERNAL_REQ = 22
    private fun checkPermission() {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "읽기 권한 요청", Toast.LENGTH_SHORT).show()
            }
            requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), EXTERNAL_REQ
            )
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            EXTERNAL_REQ ->{
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    Log.d(TAG,"external read permission is granted")
                }else{
                    Log.d(TAG,"external read permission is denied")
                    Toast.makeText(applicationContext,"권한이 거부되어있습니다.\n(설정>애플리케이션>AngelNet>권한>저장공간>허용)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}