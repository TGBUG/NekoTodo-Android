package cn.tgbug.nekotodo.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import cn.tgbug.nekotodo.domain.Blur
import cn.tgbug.nekotodo.domain.ImageSizing
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

object BackgroundImage {

    private const val TARGET_WIDTH = 1080
    private const val BLUR_RADIUS = 14
    private const val BLUR_PASSES = 3
    private const val JPEG_QUALITY = 80

    /**
     * 文件名带时间戳,每次都换一个新文件。这不是洁癖,是必需的:
     * 背景选项与路径都没变的话,Appearance(data class)与旧值相等,Compose 判定"值没变"
     * 就不会重组,新图永远不会显示;Coil 也会因为路径相同而命中旧的内存缓存。
     * 换文件名同时躲开这两个坑。
     */
    private const val FILE_PREFIX = "custom_background"
    private const val BACKGROUND_DIRECTORY = "backgrounds"

    /**
     * 把用户选的图片处理成"能当磨砂底"的图:统一到目标宽度后做**真正的模糊**
     * (见 [Blur]——不是缩小再放大那种只丢分辨率的做法),然后落盘。
     *
     * 之所以落盘而不是只记 URI:SAF 的读权限会被回收。
     */
    fun prepare(context: Context, source: Uri): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source).use { input ->
            if (input == null) throw IOException("无法读取所选图片")
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("无法识别图片尺寸")

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = ImageSizing.inSampleSize(bounds.outWidth, bounds.outHeight, TARGET_WIDTH)
        }
        val decoded = context.contentResolver.openInputStream(source).use { input ->
            if (input == null) throw IOException("无法读取所选图片")
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IOException("无法解码图片")

        val width = TARGET_WIDTH
        val height = max(1, (decoded.height.toFloat() / decoded.width * width).roundToInt())
        // createScaledBitmap 在尺寸相同时会原样返回入参,别把还在用的位图 recycle 掉。
        val scaled = if (decoded.width == width) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, width, height, true).also { decoded.recycle() }
        }

        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        scaled.recycle()

        Blur.boxBlur(pixels, width, height, BLUR_RADIUS, BLUR_PASSES)

        val blurred = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        blurred.setPixels(pixels, 0, width, 0, 0, width, height)

        val directory = File(context.filesDir, BACKGROUND_DIRECTORY).apply { mkdirs() }
        val target = File(directory, "${FILE_PREFIX}_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(target).use { blurred.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } finally {
            blurred.recycle()
        }
        return target
    }

    /** 换新图后删掉旧的那张;切到内置背景时**不删**,这样切回自定义是即时的。 */
    fun deletePrevious(context: Context, keep: File) {
        File(context.filesDir, BACKGROUND_DIRECTORY).listFiles()?.forEach { file ->
            if (file.name.startsWith(FILE_PREFIX) && file.absolutePath != keep.absolutePath) {
                file.delete()
            }
        }
    }
}
