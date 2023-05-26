package com.shong.imageconverter

import android.app.Activity
import android.content.ContentValues
import android.content.Context
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
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import java.io.*
import java.text.SimpleDateFormat
import java.util.Date


/**
 *
 * This class is a "Convert & Store & Get Image" class to help you develop.
 *
 * Class initialization must be done in "onCreate"
 * Use Companion Function is everyWhere
 *
 * @see BitmapFactory
 * @see ContentResolver
 * @see ExifInterface
 * @see Metrix
 * @see JPEG
 * @see MediaStore
 * @author SoonHong Kwon (ksksh7512@gmail.com)
 */
class ImageConverter(private val activity: ComponentActivity) {
    companion object {
        private const val dirPath = "DCIM/imgConv"
        private const val lastName = "_imgConv"
        private const val tag = "ImageConverter"

        //이미지 JPEG변환 및 최대 해상도, 최대 용량 지정
        fun convertToJpgByteArray(
            context: Context,
            uri: Uri,
            maxResolution: MaxResolution? = null,
            maxCapacity: Int? = null
        ): ByteArray? {
            val matrix = context.contentResolver.openInputStream(uri)?.run {
                getRotateMatrix(this)
            } ?: Matrix()
            var bitmap = context.contentResolver.openInputStream(uri)?.run {
                fixRotate(this, matrix)
            } ?: run {
                val ins = context.contentResolver.openInputStream(uri)
                BitmapFactory.decodeStream(ins).apply {
                    ins?.close()
                }
            }
            val byteArrayOutputStream = ByteArrayOutputStream()
            Log.d(
                tag,
                "current resolution: ${bitmap.width} ${bitmap.height} capacity: ${byteArrayOutputStream.toByteArray().size}"
            )

            // Resolution convert
            if (maxResolution != null) {
                val w = bitmap.width.toFloat()
                val h = bitmap.height.toFloat()

                val maxWidth = maxResolution.width
                val maxHeight = maxResolution.height

                if (w > maxWidth || h > maxHeight) {
                    try {
                        bitmap = if (w / h > maxWidth / maxHeight) Bitmap.createScaledBitmap(
                            bitmap,
                            maxWidth,
                            (maxWidth / w * h).toInt(),
                            true
                        ) else Bitmap.createScaledBitmap(
                            bitmap,
                            (maxHeight / h * w).toInt(),
                            maxHeight,
                            true
                        )
                    } catch (e: Exception) {
                        try {
                            bitmap = if (w / h > maxWidth / maxHeight) Bitmap.createScaledBitmap(
                                bitmap,
                                maxWidth,
                                (maxWidth / w * h).toInt(),
                                false
                            ) else Bitmap.createScaledBitmap(
                                bitmap,
                                (maxHeight / h * w).toInt(),
                                maxHeight,
                                false
                            )
                        } catch (e: Exception) {
                            Log.e(tag, "resized resolution Error! $e")
                            return null
                        }
                    }
                    Log.d(tag, "resized resolution")
                }
            }

            // JPEG convert
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
            Log.d(tag, "convert with JPEG complete")

            // Capacity size convert
            val byteArray: ByteArray? =
                if (maxCapacity != null && byteArrayOutputStream.toByteArray().size > maxCapacity) {
                    qualityRecursive(bitmap, byteArrayOutputStream, 99, maxCapacity)?.toByteArray()
                } else {
                    byteArrayOutputStream.toByteArray()
                }
            Log.d(
                tag,
                "final resolution: ${bitmap.width} ${bitmap.height} capacity: ${byteArrayOutputStream.toByteArray().size}"
            )

            return byteArray
        }

        // quality 최대 사용을 위한 재귀함수
        private fun qualityRecursive(
            bitmap: Bitmap,
            byteArrayOutputStream: ByteArrayOutputStream,
            quality: Int,
            maxCapacity: Int,
        ): ByteArrayOutputStream? {
            if (quality < 1) return null

            val byteArrayOutputStream2 = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream2)

            return if (byteArrayOutputStream2.toByteArray().size > maxCapacity) {
                qualityRecursive(bitmap, byteArrayOutputStream, quality - 1, maxCapacity)
            } else {
                Log.d(tag, "final quality : $quality ")
                byteArrayOutputStream2
            }
        }

        //ByteArray 비트맵 변환
        fun byteArrayToBitmap(byteArray: ByteArray): Bitmap {
            val matrix = getRotateMatrix(ByteArrayInputStream(byteArray))

            return fixRotate(ByteArrayInputStream(byteArray), matrix)
        }

        fun byteArrayEncodeBase64(byteArray: ByteArray): ByteArray = Base64.encode(byteArray, Base64.DEFAULT)
        fun byteArrayDecodeBase64(byteArray: ByteArray): ByteArray = Base64.decode(byteArray, Base64.DEFAULT)

        //이미지 자동회전 방지 (sdk 24이상부터)
        private fun fixRotate(ins: InputStream, matrix: Matrix): Bitmap {
            val bitmap: Bitmap = BitmapFactory.decodeStream(ins)
            ins.close()

            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        //메트릭스 가져오기
        private fun getRotateMatrix(ins: InputStream): Matrix {
            val matrix = Matrix()
            if (Build.VERSION.SDK_INT < 24) return matrix

            try {
                val exif = ExifInterface(ins)
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
                Log.e(tag, "Exif Error! ${e.localizedMessage}: ${e.cause}")
            }
            ins.close()

            return matrix
        }

        // 이미지 저장 요청
        fun imageSaveJPEG(activity: Activity, bitmap: Bitmap, saveQuality: Int): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveImageOnAboveAndroidQ(activity, bitmap, saveQuality)
                return true
            } else {
                val writePermission = ActivityCompat.checkSelfPermission(
                    activity,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )

                if (writePermission == PackageManager.PERMISSION_GRANTED) {
                    saveImageOnUnderAndroidQ(activity, bitmap, saveQuality)
                    return true
                } else {
                    val requestExternalStorageCode = 1

                    val permissionStorage = arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )

                    ActivityCompat.requestPermissions(
                        activity,
                        permissionStorage,
                        requestExternalStorageCode
                    )
                }
            }
            return false
        }

        // SDK 29이상 저장시
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun saveImageOnAboveAndroidQ(activity: Activity, bitmap: Bitmap, saveQuality: Int) {
            val fileName = System.currentTimeMillis().toString() + lastName + ".jpg"
            val contentValues = ContentValues()
            contentValues.apply {
                put(MediaStore.Images.Media.RELATIVE_PATH, dirPath)
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpg")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = activity.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )

            try {
                if (uri != null) {
                    val image = activity.contentResolver.openFileDescriptor(uri, "w", null)

                    if (image != null) {
                        val fos = FileOutputStream(image.fileDescriptor)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, saveQuality, fos)
                        fos.close()

                        contentValues.clear()
                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                        activity.contentResolver.update(uri, contentValues, null, null)
                    }
                }
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // SDK 29미만 저장시
        private fun saveImageOnUnderAndroidQ(activity: Activity, bitmap: Bitmap, saveQuality: Int) {
            val fileName = System.currentTimeMillis().toString() + lastName + ".jpg"
            val externalStorage = Environment.getExternalStorageDirectory().absolutePath
            val path = "$externalStorage/$dirPath"
            val dir = File(path)
            if (dir.exists().not()) dir.mkdirs()

            try {
                val fileItem = File("$dir/$fileName")
                fileItem.createNewFile()
                val fos = FileOutputStream(fileItem) // 파일 아웃풋 스트림
                bitmap.compress(Bitmap.CompressFormat.JPEG, saveQuality, fos)
                fos.close()

                activity.sendBroadcast(
                    Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                        Uri.fromFile(fileItem)
                    )
                )
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    interface OnUriListener {
        fun onUri(uri: Uri)
        fun onError(msg: String)
    }

    private var onUriListener: OnUriListener? = null
    fun setOnUriListener(l: OnUriListener?) {
        onUriListener = l
    }

    private var cameraUri: Uri? = null
    private lateinit var cameraResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var albumResultLauncher: ActivityResultLauncher<Intent>

    fun initialize() {
        cameraResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (cameraUri == null) {
                onUriListener?.onError("Error! CameraUri is Null!")
            } else {
                if (result?.resultCode == Activity.RESULT_OK)
                    onUriListener?.onUri(cameraUri ?: return@registerForActivityResult)
                else
                    onUriListener?.onError("Error! Camera Result is not Ok <resultCode : ${result?.resultCode}>")
                cameraUri = null
            }
        }
        albumResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val albumUri = result.data?.data
            if (albumUri == null) {
                onUriListener?.onError("Error! AlbumUri is Null!")
            } else {
                if (result?.resultCode == Activity.RESULT_OK)
                    onUriListener?.onUri(albumUri)
                else
                    onUriListener?.onError("Error! Album Result is not Ok <resultCode : ${result?.resultCode}>")
            }
        }
    }

    fun getUriForCamera(context: Context) {
        if (!this::cameraResultLauncher.isInitialized) {
            Log.e(tag, "Must call initialize first!")
            onUriListener?.onError("Must call initialize first!")
            return
        }

        try {
            val cameraFile: File = try {
                val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                val imagePath = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                File(imagePath, "JPEG_${timeStamp}" + ".jpg")
            } catch (e: Exception) {
                Log.e(tag, "file create ERROR! : $e")
                onUriListener?.onError("file create ERROR! : $e")
                return
            }

            cameraUri = FileProvider.getUriForFile(
                context,
                "${activity.packageName}.imgcovprovider",
                cameraFile
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            cameraResultLauncher.launch(intent)
        } catch (e: Exception) {
            onUriListener?.onError("$e")
        }
    }

    fun getUriForAlbum() {
        if (!this::cameraResultLauncher.isInitialized) {
            Log.e(tag, "Must call initialize first!")
            onUriListener?.onError("Must call initialize first!")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_PICK)
            intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
            albumResultLauncher.launch(intent)
        } catch (e: Exception) {
            onUriListener?.onError("$e")
        }
    }
}

data class MaxResolution(
    val width: Int,
    val height: Int,
)