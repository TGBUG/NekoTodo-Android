package cn.tgbug.nekotodo.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import cn.tgbug.nekotodo.domain.ImageSizing
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** 一件待上传的附件。Uri 可能来自相册、文件选择器,或我们自己的相机缓存目录。 */
data class Attachment(
    val uri: Uri,
    val displayName: String,
    val mime: String?,
    val sizeBytes: Long,
) {
    val isImage: Boolean get() = mime?.startsWith("image/") == true
}

/** 通过 ContentResolver 查询文件名 / 大小 / MIME。 */
fun Context.attachmentFrom(uri: Uri): Attachment {
    var name: String? = null
    var size = -1L
    contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    return Attachment(
        uri = uri,
        displayName = name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file",
        mime = contentResolver.getType(uri),
        sizeBytes = size,
    )
}

/** 以流的方式读取 content:// ,避免把整个文件读进内存。 */
private class ContentUriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val contentType: MediaType?,
) : RequestBody() {

    override fun contentType(): MediaType? = contentType

    override fun contentLength(): Long =
        runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }
            .getOrDefault(-1L)

    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw IOException("无法读取所选文件")
        input.use { sink.writeAll(it.source()) }
    }
}

fun Attachment.toUploadPart(resolver: ContentResolver): MultipartBody.Part =
    MultipartBody.Part.createFormData(
        "files",
        displayName,
        ContentUriRequestBody(resolver, uri, mime?.toMediaTypeOrNull()),
    )

/**
 * 照片上传前降采样并重新编码:后端单文件上限 20MB,而手机主摄直出常超过它。
 * 顺带也降低了 VLM 的处理成本。
 */
object PhotoCompressor {

    private const val JPEG_QUALITY = 85

    fun compress(context: Context, source: Uri, targetDirectory: File): File {
        // 注意:inJustDecodeBounds 这次解码**故意返回 null**(它只为了填 bounds),
        // 所以不能用它的返回值判断"文件打不开"——之前正是这么写才导致压缩永远失败。
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source).use { input ->
            if (input == null) throw IOException("无法读取图片")
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("无法识别图片尺寸")

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageSizing.inSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = context.contentResolver.openInputStream(source).use { input ->
            if (input == null) throw IOException("无法读取图片")
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IOException("无法解码图片")

        targetDirectory.mkdirs()
        val target = File(targetDirectory, "photo_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        } finally {
            bitmap.recycle()
        }
        return target
    }
}

/** 相机需要先有一个可写的目标位置;用 FileProvider 暴露缓存目录。 */
fun Context.createCaptureUri(): Uri {
    val directory = File(cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
    val file = File(directory, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}

fun Context.captureCacheDirectory(): File = File(cacheDir, CAPTURE_DIRECTORY)

/** 拍照结果是我们自己缓存目录里的文件,压缩后覆盖同一个 URI 的语义由这里表达。 */
fun Context.attachmentFromFile(file: File): Attachment = Attachment(
    uri = Uri.fromFile(file),
    displayName = file.name,
    mime = "image/jpeg",
    sizeBytes = file.length(),
)

private const val CAPTURE_DIRECTORY = "captures"
