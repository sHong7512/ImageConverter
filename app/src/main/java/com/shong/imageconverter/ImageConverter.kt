package com.shong.imageconverter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ImageConverter constructor(val context: Context) {
    private val TAG = this::class.java.simpleName
    private val MAX_IMAGE_SIZE = 1024 * 1024 * 2

    internal var jpegImageSize : Double = 0.0
    internal fun encodeBase64(uri: Uri) : String?{
        val matrix = context.contentResolver.openInputStream(uri)?.run {
            getRotateMatrix(this)
        } ?: Matrix()
        val img = context.contentResolver.openInputStream(uri)?.run {
            fixRotate(this, matrix)
        } ?: return null
//        val img = fixRotate(context.contentResolver.openInputStream(uri), matrix)

        val byteArrayOutputStream = ByteArrayOutputStream()
        img.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)

        val byteArray: ByteArray?
        val ratio = byteArrayOutputStream.size().toFloat() / MAX_IMAGE_SIZE
        //퀄리티 조절로 용량 조절
        if(ratio > 1){
            val quality = (100f / ratio).toInt()

            val byteArrayOutputStream2 = ByteArrayOutputStream()
            img.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream2)
            Log.d(TAG,"quality : $quality trans size : ${byteArrayOutputStream2.size()}")

            if(byteArrayOutputStream2.size() > MAX_IMAGE_SIZE){
                Log.d(TAG,"변환값이 max를 초과")
                return null
            }

            jpegImageSize = byteArrayOutputStream2.size().toDouble()
            byteArray = byteArrayOutputStream2.toByteArray()
        }else{
            jpegImageSize = byteArrayOutputStream.size().toDouble()
            byteArray = byteArrayOutputStream.toByteArray()
        }

        return Base64.encodeToString(byteArray, Base64.DEFAULT)
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

}