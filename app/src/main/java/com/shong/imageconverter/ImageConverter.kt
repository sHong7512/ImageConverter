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
            byteArray = qualityRecursive(bitmap, byteArrayOutputStream).toByteArray()
        }else{
            byteArray = byteArrayOutputStream.toByteArray()
        }
        Log.d(TAG, "final size: ${byteArray.size / 1024f}kb")

        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    var quality = 100
    // quality 최대 사용을 위한 재귀함수
    fun qualityRecursive(bitmap: Bitmap, byteArrayOutputStream: ByteArrayOutputStream): ByteArrayOutputStream{
        val byteArrayOutputStream2 = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream2)
        Log.d(TAG,"down quility size: ${byteArrayOutputStream2.toByteArray().size / 1024f} quality $quality")

        if(byteArrayOutputStream2.toByteArray().size > MAX_IMAGE_SIZE){
            quality -= 1
            return qualityRecursive(bitmap, byteArrayOutputStream)
        }else{
            quality = 100
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

    internal fun imageSaveJPEG(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            //Q 버전 이상일 경우. (안드로이드 10, API 29 이상일 경우)
            saveImageOnAboveAndroidQ(bitmap)
            Toast.makeText(activity, "이미지 저장이 완료되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            // Q 버전 이하일 경우. 저장소 권한을 얻어온다.
            val writePermission = ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

            if(writePermission == PackageManager.PERMISSION_GRANTED) {
                saveImageOnUnderAndroidQ(bitmap)
                Toast.makeText(activity, "이미지 저장이 완료되었습니다.", Toast.LENGTH_SHORT).show()
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

    //Android Q (Android 10, API 29 이상에서는 이 메서드를 통해서 이미지를 저장한다.)
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveImageOnAboveAndroidQ(bitmap: Bitmap) {
        val fileName = System.currentTimeMillis().toString() + "_sHong.jpg" // 파일이름 현재시간.png

        /*
        * ContentValues() 객체 생성.
        * ContentValues는 ContentResolver가 처리할 수 있는 값을 저장해둘 목적으로 사용된다.
        * */
        val contentValues = ContentValues()
        contentValues.apply {
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/sHongApp") // 경로 설정
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName) // 파일이름을 put해준다.
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpg")
            put(MediaStore.Images.Media.IS_PENDING, 1) // 현재 is_pending 상태임을 만들어준다.
            // 다른 곳에서 이 데이터를 요구하면 무시하라는 의미로, 해당 저장소를 독점할 수 있다.
        }

        // 이미지를 저장할 uri를 미리 설정해놓는다.
        val uri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        try {
            if(uri != null) {
                val image = activity.contentResolver.openFileDescriptor(uri, "w", null)
                // write 모드로 file을 open한다.

                if(image != null) {
                    val fos = FileOutputStream(image.fileDescriptor)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                    //비트맵을 FileOutputStream를 통해 compress한다.
                    fos.close()

                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0) // 저장소 독점을 해제한다.
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

    private fun saveImageOnUnderAndroidQ(bitmap: Bitmap) {
        val fileName = System.currentTimeMillis().toString() + "_sHong.jpg"
        val externalStorage = Environment.getExternalStorageDirectory().absolutePath
        val path = "$externalStorage/DCIM/sHongApp"
        val dir = File(path)

        if(dir.exists().not()) {
            dir.mkdirs() // 폴더 없을경우 폴더 생성
        }

        try {
            val fileItem = File("$dir/$fileName")
            fileItem.createNewFile()
            //0KB 파일 생성.

            val fos = FileOutputStream(fileItem) // 파일 아웃풋 스트림

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            //파일 아웃풋 스트림 객체를 통해서 Bitmap 압축.

            fos.close() // 파일 아웃풋 스트림 객체 close

            activity.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(fileItem)))
            // 브로드캐스트 수신자에게 파일 미디어 스캔 액션 요청. 그리고 데이터로 추가된 파일에 Uri를 넘겨준다.
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}