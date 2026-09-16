package moe.https.syncthing.storage

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.https.syncthing.R
import java.io.IOException

/** 接收系统分享的文件，并将它们复制到用户选择的默认同步文件夹。 */
class ShareToSyncthingActivity : ComponentActivity() {
    private var sourceUris: List<Uri> = emptyList()
    private var destinationUri: Uri? = null
    private var importing = false

    private val destinationPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        when {
            treeUri == null -> finish()
            !isSyncthingDestination(treeUri) -> {
                Toast.makeText(
                    this,
                    R.string.share_choose_sync_folder,
                    Toast.LENGTH_LONG,
                ).show()
                chooseDestination()
            }
            !importing -> importSharedFiles(treeUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourceUris = savedInstanceState
            ?.getStringArrayList(STATE_SOURCE_URIS)
            ?.map(Uri::parse)
            ?: sharedUrisFrom(intent)

        if (sourceUris.isEmpty()) {
            Toast.makeText(this, R.string.share_no_files, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        showProgressIndicator()
        destinationUri = savedInstanceState
            ?.getString(STATE_DESTINATION_URI)
            ?.let(Uri::parse)
        when {
            destinationUri != null -> importSharedFiles(requireNotNull(destinationUri))
            savedInstanceState == null -> chooseDestination()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            STATE_SOURCE_URIS,
            ArrayList(sourceUris.map(Uri::toString)),
        )
        destinationUri?.let { outState.putString(STATE_DESTINATION_URI, it.toString()) }
        super.onSaveInstanceState(outState)
    }

    private fun importSharedFiles(treeUri: Uri) {
        destinationUri = treeUri
        importing = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                copySharedFiles(treeUri)
            }
            val message = when {
                result.failed == 0 -> resources.getQuantityString(
                    R.plurals.share_files_saved,
                    result.saved,
                    result.saved,
                )
                result.saved > 0 -> getString(
                    R.string.share_files_partially_saved,
                    result.saved,
                    result.failed,
                )
                else -> getString(
                    R.string.share_files_failed,
                    result.firstError ?: getString(R.string.share_unknown_error),
                )
            }
            Toast.makeText(this@ShareToSyncthingActivity, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun copySharedFiles(treeUri: Uri): ImportResult {
        val resolver = contentResolver
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val destinationDirectory = DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            treeDocumentId,
        )
        var saved = 0
        var failed = 0
        var firstError: String? = null

        sourceUris.forEachIndexed { index, sourceUri ->
            var destinationUri: Uri? = null
            try {
                val displayName = displayNameFor(sourceUri, index)
                val mimeType = resolver.getType(sourceUri) ?: DEFAULT_MIME_TYPE
                destinationUri = DocumentsContract.createDocument(
                    resolver,
                    destinationDirectory,
                    mimeType,
                    displayName,
                ) ?: throw IOException("无法创建目标文件：$displayName")

                resolver.openInputStream(sourceUri).use { input ->
                    if (input == null) throw IOException("无法读取来源文件：$displayName")
                    resolver.openOutputStream(destinationUri, "w").use { output ->
                        if (output == null) throw IOException("无法写入目标文件：$displayName")
                        input.copyTo(output)
                    }
                }
                saved += 1
            } catch (error: Exception) {
                destinationUri?.let { partiallyWrittenUri ->
                    runCatching {
                        DocumentsContract.deleteDocument(resolver, partiallyWrittenUri)
                    }
                }
                failed += 1
                if (firstError == null) {
                    firstError = error.message
                        ?.takeIf(String::isNotBlank)
                        ?: error.javaClass.simpleName
                }
            }
        }
        return ImportResult(saved, failed, firstError)
    }

    private fun displayNameFor(uri: Uri, index: Int): String {
        val queriedName = runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
        }.getOrNull()
        return queriedName
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.takeIf(String::isNotBlank)
            ?: "shared-file-${index + 1}"
    }

    private fun isSyncthingDestination(treeUri: Uri): Boolean {
        if (treeUri.authority != SyncthingDocumentsProvider.AUTHORITY) return false
        val documentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return false
        return documentId != SyncthingDocumentsProvider.ROOT_DOCUMENT_ID
    }

    private fun initialDirectoryUri(): Uri = DocumentsContract.buildDocumentUri(
        SyncthingDocumentsProvider.AUTHORITY,
        SyncthingDocumentsProvider.ROOT_DOCUMENT_ID,
    )

    private fun chooseDestination() {
        destinationPicker.launch(initialDirectoryUri())
    }

    @Suppress("DEPRECATION")
    private fun sharedUrisFrom(intent: Intent): List<Uri> {
        val uris = buildList {
            intent.clipData?.let { clipData ->
                for (index in 0 until clipData.itemCount) {
                    clipData.getItemAt(index).uri?.let(::add)
                }
            }
            when (intent.action) {
                Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
                Intent.ACTION_SEND_MULTIPLE -> {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let(::addAll)
                }
            }
        }
        return uris.distinct()
    }

    private fun showProgressIndicator() {
        val progressIndicator = ProgressBar(this)
        setContentView(
            FrameLayout(this).apply {
                addView(
                    progressIndicator,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER,
                    ),
                )
            },
        )
    }

    private data class ImportResult(
        val saved: Int,
        val failed: Int,
        val firstError: String?,
    )

    private companion object {
        const val STATE_SOURCE_URIS = "source_uris"
        const val STATE_DESTINATION_URI = "destination_uri"
        const val DEFAULT_MIME_TYPE = "application/octet-stream"
    }
}
