package cn.tgbug.nekotodo.domain

/**
 * 后端支持的文件类型(见后端 README「POST /source-infos」一节)。
 * 文件选择器用通配符以免部分提供方把可选文件藏起来,所以要在客户端这里先把关,
 * 给出比"上传后才 400"更及时的反馈。
 */
object SupportedFiles {

    val EXTENSIONS: Set<String> = setOf(
        "png", "jpg", "jpeg", "webp", "gif",
        "docx", "pptx", "pdf",
        "txt", "md", "csv", "json",
    )

    private val IMAGE_MIME_PREFIX = "image/"

    fun extensionOf(name: String?): String? =
        name?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotEmpty() }

    fun isSupported(name: String?, mime: String?): Boolean {
        val extension = extensionOf(name)
        if (extension != null && extension in EXTENSIONS) return true
        // 有些提供方不给文件名(或名字里没有扩展名),退而看 MIME。
        if (mime == null) return false
        if (mime.startsWith(IMAGE_MIME_PREFIX)) return true
        return mime in MIME_ONLY_TYPES
    }

    private val MIME_ONLY_TYPES = setOf(
        "application/pdf",
        "application/json",
        "text/plain",
        "text/markdown",
        "text/csv",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    )

    /** 给用户看的支持列表。 */
    fun describe(): String = EXTENSIONS.sorted().joinToString("、") { ".$it" }
}
