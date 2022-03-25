package com.shong.imageconverter

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.io.*

class ImageConverter constructor(val activity: Activity) {
    private val TAG = this::class.java.simpleName + "_sHong"
    private val MAX_IMAGE_SIZE = 1024 * 1024 * 2

    val MAX_WIDTH = 4096f      //320f
    val MAX_HEIGHT = 4096f     //240f

    internal fun encodeBase64(uri: Uri) : String?{
        val matrix = activity.contentResolver.openInputStream(uri)?.run {
            getRotateMatrix(this)
        } ?: Matrix()
        var bitmap = activity.contentResolver.openInputStream(uri)?.run {
            fixRotate(this, matrix)
        } ?: return null
//        val bitmap = fixRotate(context.contentResolver.openInputStream(uri), matrix)

        Log.d(TAG, "resolution -> width: ${bitmap.width} height: ${bitmap.height}")

        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        if(w > MAX_WIDTH || h > MAX_HEIGHT){
            try{
                bitmap = if (w / h > MAX_WIDTH / MAX_HEIGHT) Bitmap.createScaledBitmap(
                    bitmap,
                    MAX_WIDTH.toInt(),
                    (MAX_WIDTH / w * h).toInt(),
                    true
                ) else Bitmap.createScaledBitmap(
                    bitmap,
                    (MAX_HEIGHT / h * w).toInt(),
                    MAX_HEIGHT.toInt(),
                    true
                )
            }catch (e: Exception){
                try{
                    bitmap = if (w / h > MAX_WIDTH / MAX_HEIGHT) Bitmap.createScaledBitmap(
                        bitmap,
                        MAX_WIDTH.toInt(),
                        (MAX_WIDTH / w * h).toInt(),
                        false
                    ) else Bitmap.createScaledBitmap(
                        bitmap,
                        (MAX_HEIGHT / h * w).toInt(),
                        MAX_HEIGHT.toInt(),
                        false
                    )
                }catch (e: Exception){
                    Log.d(TAG,"이미지가 크기가 너무 큽니다.")
                    return null
                }
            }
            Log.d(TAG, "resized resolution -> width: ${bitmap.width} height: ${bitmap.height}")
        }

        val byteArrayOutputStream = ByteArrayOutputStream()

        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        Log.d(TAG, "resized size: ${byteArrayOutputStream.toByteArray().size / 1024f}kb")

        val byteArray: ByteArray?
        //퀄리티 조절로 용량 조절
        if(byteArrayOutputStream.toByteArray().size > MAX_IMAGE_SIZE){
//            val quality = MAX_IMAGE_SIZE.toFloat() / byteArrayOutputStream.toByteArray().size * 100
//            val byteArrayOutputStream2 = ByteArrayOutputStream()
//            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.toInt(), byteArrayOutputStream2)
//            byteArray = byteArrayOutputStream2.toByteArray()
            byteArray = qualityRecursive(bitmap, byteArrayOutputStream, 99).toByteArray()
        }else{
            byteArray = byteArrayOutputStream.toByteArray()
        }
        Log.d(TAG, "final size: ${byteArray.size / 1024f}kb")

        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    // quality 최대 사용을 위한 재귀함수
    fun qualityRecursive(bitmap: Bitmap, byteArrayOutputStream: ByteArrayOutputStream, quality: Int): ByteArrayOutputStream{
        val byteArrayOutputStream2 = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream2)
        Log.d(TAG,"down quility size: ${byteArrayOutputStream2.toByteArray().size / 1024f} quality $quality")

        if(byteArrayOutputStream2.toByteArray().size > MAX_IMAGE_SIZE){
            return qualityRecursive(bitmap, byteArrayOutputStream, quality - 1)
        }else{
            return byteArrayOutputStream2
        }
    }

    internal fun decodeBase64(dataStr: String) : Bitmap {
        return byteArrayToBitmap(stringToByteArray(dataStr))
    }

    internal fun stringToByteArray(str: String) : ByteArray{
        return Base64.decode(str, Base64.DEFAULT)
    }

    internal fun byteArrayToBitmap(byteArray: ByteArray): Bitmap{
        val matrix = getRotateMatrix(ByteArrayInputStream(byteArray))

        return fixRotate(ByteArrayInputStream(byteArray), matrix)
    }

    private fun getRotateMatrix(ins: InputStream): Matrix{
        val matrix = Matrix()
        try {
            val exif = ExifInterface(ins)
//            val exifOrientation = exif.getAttribute(ExifInterface.TAG_ORIENTATION)
//            KLog.d(TAG,"exifOrientation : $exifOrientation")
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            }
        } catch (e: Exception) {
            Log.d(TAG,"Exif Error! ${e.localizedMessage}: ${e.cause}")
        }
        ins.close()

        return matrix
    }

    private fun fixRotate(ins: InputStream, matrix: Matrix): Bitmap{
        val bitmap: Bitmap = BitmapFactory.decodeStream(ins)
        ins.close()

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    // 이미지 저장 요청
    internal fun imageSaveJPEG(bitmap: Bitmap, saveQuality: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageOnAboveAndroidQ(bitmap, saveQuality)
            Log.d(TAG, "이미지 저장이 완료되었습니다.")
        } else {
            val writePermission = ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

            if(writePermission == PackageManager.PERMISSION_GRANTED) {
                saveImageOnUnderAndroidQ(bitmap, saveQuality)
                Log.d(TAG, "이미지 저장이 완료되었습니다.")
            } else {
                val requestExternalStorageCode = 1

                val permissionStorage = arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )

                ActivityCompat.requestPermissions(activity, permissionStorage, requestExternalStorageCode)
            }
        }

    }

    // SDK 29이상 저장시
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveImageOnAboveAndroidQ(bitmap: Bitmap, saveQuality: Int) {
        val fileName = System.currentTimeMillis().toString() + "_kct.jpg"
        val contentValues = ContentValues()
        contentValues.apply {
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/KCTApp")
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpg")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        try {
            if(uri != null) {
                val image = activity.contentResolver.openFileDescriptor(uri, "w", null)

                if(image != null) {
                    val fos = FileOutputStream(image.fileDescriptor)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, saveQuality, fos)
                    fos.close()

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    activity.contentResolver.update(uri, contentValues, null, null)
                }
            }
        } catch(e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // SDK 29미만 저장시
    private fun saveImageOnUnderAndroidQ(bitmap: Bitmap, saveQuality: Int) {
        val fileName = System.currentTimeMillis().toString() + "_kct.jpg"
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath
        val path = "$externalStorage/DCIM/KCTApp"
        val dir = File(path)
        if(dir.exists().not()) dir.mkdirs()

        try {
            val fileItem = File("$dir/$fileName")
            fileItem.createNewFile()
            val fos = FileOutputStream(fileItem) // 파일 아웃풋 스트림
            bitmap.compress(Bitmap.CompressFormat.JPEG, saveQuality, fos)
            fos.close()

            activity.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(fileItem)))
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}