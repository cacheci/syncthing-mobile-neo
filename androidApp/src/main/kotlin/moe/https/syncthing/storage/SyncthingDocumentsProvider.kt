package moe.https.syncthing.storage

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import moe.https.syncthing.BuildConfig
import moe.https.syncthing.R
import moe.https.syncthing.storage.SyncthingDocumentsProvider.Companion.DEFAULT_SYNC_DIRECTORY
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Base64
import java.util.Locale

/**
 * 通过 Android Storage Access Framework 暴露应用默认同步目录。
 *
 * Provider 只允许访问 [DEFAULT_SYNC_DIRECTORY] 下的普通文件和目录。符号链接不会显示，
 * 也不能通过构造 document ID 越过该目录边界。
 */
class SyncthingDocumentsProvider : DocumentsProvider() {
    private val syncRoot: File
        get() {
            val directory = requireNotNull(context).filesDir.resolve(DEFAULT_SYNC_DIRECTORY)
            check(!Files.isSymbolicLink(directory.toPath())) {
                "默认同步目录不能是符号链接"
            }
            check(directory.isDirectory || directory.mkdirs()) {
                "无法创建默认同步目录"
            }
            return directory
        }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: DEFAULT_ROOT_PROJECTION
        return MatrixCursor(columns).apply {
            newRow().apply {
                add(Root.COLUMN_ROOT_ID, ROOT_ID)
                add(Root.COLUMN_DOCUMENT_ID, ROOT_DOCUMENT_ID)
                add(Root.COLUMN_TITLE, requireNotNull(context).getString(R.string.saf_root_title))
                add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_LOCAL_ONLY)
                add(Root.COLUMN_ICON, R.drawable.app_logo)
                add(Root.COLUMN_MIME_TYPES, "*/*")
                add(Root.COLUMN_AVAILABLE_BYTES, syncRoot.usableSpace)
            }
        }
    }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?,
    ): Cursor {
        val columns = projection ?: DEFAULT_DOCUMENT_PROJECTION
        return MatrixCursor(columns).apply {
            includeDocument(this, requireDocument(documentId))
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: DEFAULT_DOCUMENT_PROJECTION
        val parent = requireDirectory(parentDocumentId)
        return MatrixCursor(columns).apply {
            parent.listFiles()
                .orEmpty()
                .asSequence()
                .filterNot { Files.isSymbolicLink(it.toPath()) }
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
                .forEach { includeDocument(this, it) }
            setNotificationUri(
                requireNotNull(context).contentResolver,
                DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId),
            )
        }
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor {
        signal?.throwIfCanceled()
        val file = requireDocument(documentId)
        if (!file.isFile) throw FileNotFoundException("文档不是普通文件：$documentId")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val parent = requireDirectory(parentDocumentId)
        val safeName = requireSafeDisplayName(displayName)
        val target = uniqueTarget(parent, safeName)
        val created = if (mimeType == Document.MIME_TYPE_DIR) {
            target.mkdir()
        } else {
            target.createNewFile()
        }
        if (!created) throw IOException("无法创建文档：$safeName")
        notifyDirectoryChanged(parentDocumentId)
        return documentIdFor(target)
    }

    override fun deleteDocument(documentId: String) {
        if (documentId == ROOT_DOCUMENT_ID) throw FileNotFoundException("不能删除默认同步目录")
        val target = requireDocument(documentId)
        val parentId = target.parentFile?.let(::documentIdFor)
        deleteWithoutFollowingLinks(target.toPath())
        parentId?.let(::notifyDirectoryChanged)
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        if (documentId == ROOT_DOCUMENT_ID) throw FileNotFoundException("不能重命名默认同步目录")
        val source = requireDocument(documentId)
        val parent = source.parentFile ?: throw FileNotFoundException("文档缺少父目录")
        val target = parent.resolve(requireSafeDisplayName(displayName))
        if (target.exists()) throw FileNotFoundException("同名文档已存在：$displayName")
        moveSafely(source.toPath(), target.toPath())
        notifyDirectoryChanged(documentIdFor(parent))
        return documentIdFor(target)
    }

    override fun moveDocument(
        sourceDocumentId: String,
        sourceParentDocumentId: String,
        targetParentDocumentId: String,
    ): String {
        if (sourceDocumentId == ROOT_DOCUMENT_ID) throw FileNotFoundException("不能移动默认同步目录")
        val source = requireDocument(sourceDocumentId)
        val sourceParent = requireDirectory(sourceParentDocumentId)
        val targetParent = requireDirectory(targetParentDocumentId)
        if (source.parentFile?.canonicalFile != sourceParent.canonicalFile) {
            throw FileNotFoundException("源文档不属于指定父目录")
        }
        val target = targetParent.resolve(source.name)
        if (target.exists()) throw FileNotFoundException("目标目录中存在同名文档：${source.name}")
        if (source.isDirectory && targetParent.canonicalFile.toPath().startsWith(source.canonicalFile.toPath())) {
            throw FileNotFoundException("不能将目录移动到其子目录中")
        }
        moveSafely(source.toPath(), target.toPath())
        notifyDirectoryChanged(sourceParentDocumentId)
        if (targetParentDocumentId != sourceParentDocumentId) {
            notifyDirectoryChanged(targetParentDocumentId)
        }
        return documentIdFor(target)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = runCatching { requireDocument(parentDocumentId).canonicalFile.toPath() }
            .getOrNull() ?: return false
        val child = runCatching { requireDocument(documentId).canonicalFile.toPath() }
            .getOrNull() ?: return false
        return child != parent && child.startsWith(parent)
    }

    private fun includeDocument(cursor: MatrixCursor, file: File) {
        val isDirectory = file.isDirectory
        cursor.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, documentIdFor(file))
            add(Document.COLUMN_DISPLAY_NAME, if (file == syncRoot) {
                requireNotNull(context).getString(R.string.saf_root_title)
            } else {
                file.name
            })
            add(Document.COLUMN_MIME_TYPE, if (isDirectory) Document.MIME_TYPE_DIR else mimeTypeFor(file))
            add(Document.COLUMN_FLAGS, documentFlags(file, isDirectory))
            add(Document.COLUMN_SIZE, if (isDirectory) null else file.length())
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        }
    }

    private fun documentFlags(file: File, isDirectory: Boolean): Int {
        var flags = 0
        if (file != syncRoot) {
            flags = flags or
                Document.FLAG_SUPPORTS_DELETE or
                Document.FLAG_SUPPORTS_RENAME or
                Document.FLAG_SUPPORTS_MOVE
        }
        return if (isDirectory) {
            flags or Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            flags or Document.FLAG_SUPPORTS_WRITE
        }
    }

    private fun requireDocument(documentId: String): File {
        val root = syncRoot.canonicalFile
        if (documentId == ROOT_DOCUMENT_ID) return root
        if (!documentId.startsWith(DOCUMENT_ID_PREFIX)) {
            throw FileNotFoundException("无效的文档 ID")
        }
        val relativePath = try {
            String(
                Base64.getUrlDecoder().decode(documentId.removePrefix(DOCUMENT_ID_PREFIX)),
                StandardCharsets.UTF_8,
            )
        } catch (_: IllegalArgumentException) {
            throw FileNotFoundException("无效的文档 ID")
        }
        val candidate = root.resolve(relativePath).toPath().toAbsolutePath().normalize()
        val rootPath = root.toPath()
        if (candidate == rootPath || !candidate.startsWith(rootPath)) {
            throw FileNotFoundException("文档不在默认同步目录中")
        }
        requireNoSymbolicLink(rootPath, candidate)
        val file = candidate.toFile()
        if (!file.exists()) throw FileNotFoundException("文档不存在：$documentId")
        if (!file.canonicalFile.toPath().startsWith(rootPath)) {
            throw FileNotFoundException("文档不在默认同步目录中")
        }
        return file
    }

    private fun requireDirectory(documentId: String): File = requireDocument(documentId).also {
        if (!it.isDirectory) throw FileNotFoundException("文档不是目录：$documentId")
    }

    private fun documentIdFor(file: File): String {
        val rootPath = syncRoot.canonicalFile.toPath()
        val path = file.toPath().toAbsolutePath().normalize()
        if (path == rootPath) return ROOT_DOCUMENT_ID
        if (!path.startsWith(rootPath)) throw FileNotFoundException("文档不在默认同步目录中")
        requireNoSymbolicLink(rootPath, path)
        val relativePath = rootPath.relativize(path).toString()
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(relativePath.toByteArray(StandardCharsets.UTF_8))
        return DOCUMENT_ID_PREFIX + encoded
    }

    private fun requireNoSymbolicLink(root: Path, target: Path) {
        var current = root
        root.relativize(target).forEach { segment ->
            current = current.resolve(segment)
            if (Files.isSymbolicLink(current)) {
                throw FileNotFoundException("不允许通过符号链接访问文档")
            }
        }
    }

    private fun requireSafeDisplayName(displayName: String): String {
        if (
            displayName.isBlank() ||
            displayName == "." ||
            displayName == ".." ||
            displayName.contains('/') ||
            displayName.contains('\\') ||
            displayName.indexOf('\u0000') >= 0
        ) {
            throw FileNotFoundException("无效的文档名称")
        }
        return displayName
    }

    private fun uniqueTarget(parent: File, requestedName: String): File {
        val requested = parent.resolve(requestedName)
        if (!requested.exists()) return requested
        val dotIndex = requestedName.lastIndexOf('.').takeIf { it > 0 } ?: requestedName.length
        val baseName = requestedName.substring(0, dotIndex)
        val extension = requestedName.substring(dotIndex)
        for (index in 1..MAX_UNIQUE_NAME_ATTEMPTS) {
            val candidate = parent.resolve("$baseName ($index)$extension")
            if (!candidate.exists()) return candidate
        }
        throw IOException("无法为文档生成不重复的名称：$requestedName")
    }

    private fun deleteWithoutFollowingLinks(path: Path) {
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                error?.let { throw it }
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun moveSafely(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target)
        }
    }

    private fun mimeTypeFor(file: File): String {
        val extension = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    private fun notifyDirectoryChanged(documentId: String) {
        requireNotNull(context).contentResolver.notifyChange(
            DocumentsContract.buildChildDocumentsUri(AUTHORITY, documentId),
            null,
        )
    }

    private companion object {
        const val AUTHORITY = BuildConfig.APPLICATION_ID + ".documents"
        const val ROOT_ID = "default-sync-directory"
        const val ROOT_DOCUMENT_ID = "root"
        const val DOCUMENT_ID_PREFIX = "file:"
        const val DEFAULT_SYNC_DIRECTORY = "syncfolders"
        const val MAX_UNIQUE_NAME_ATTEMPTS = 10_000

        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_AVAILABLE_BYTES,
        )

        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
    }
}
