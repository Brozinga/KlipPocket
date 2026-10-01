package ru.ytkab0bp.beamklipper.provider

import android.content.res.AssetFileDescriptor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import ru.ytkab0bp.beamklipper.KlipperApp
import ru.ytkab0bp.beamklipper.KlipperInstance
import ru.ytkab0bp.beamklipper.R
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

class InstanceFilesProvider : DocumentsProvider() {
    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRoots(projection: Array<String>?): android.database.Cursor {
        val result = MatrixCursor(resolveRootProjection(projection))
        for (inst in KlipperInstance.getInstances()) {
            result.newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, inst.id)
                add(DocumentsContract.Root.COLUMN_SUMMARY, KlipperApp.INSTANCE.getString(R.string.InstanceN, inst.name))
                add(DocumentsContract.Root.COLUMN_FLAGS, DocumentsContract.Root.FLAG_SUPPORTS_CREATE)
                add(DocumentsContract.Root.COLUMN_TITLE, KlipperApp.INSTANCE.getString(R.string.AppName))
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getDocIdForFile(inst, inst.publicDirectory))
                add(DocumentsContract.Root.COLUMN_MIME_TYPES, getChildMimeTypes())
                add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, inst.publicDirectory.freeSpace)
                add(DocumentsContract.Root.COLUMN_ICON, R.drawable.icon_static)
            }
        }
        return result
    }

    override fun queryDocument(documentId: String, projection: Array<String>?): android.database.Cursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))
        includeFile(result, documentId, null, null)
        return result
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<String>?, sortOrder: String?): android.database.Cursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))
        val parent = getFileForDocId(parentDocumentId) ?: return result
        val instance = getInstanceForDocId(parentDocumentId)
        for (file in parent.listFiles() ?: return result) {
            includeFile(result, null, instance, file)
        }
        return result
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        val isWrite = mode.indexOf('w') != -1
        return if (isWrite) {
            try {
                val ctx = context ?: throw FileNotFoundException("No context")
                val handler = Handler(ctx.mainLooper)
                ParcelFileDescriptor.open(file, accessMode, handler) { e -> e?.printStackTrace() }
            } catch (e: IOException) {
                throw FileNotFoundException("Failed to open document with id $documentId and mode $mode")
            }
        } else {
            ParcelFileDescriptor.open(file, accessMode)
        }
    }

    override fun openDocumentThumbnail(documentId: String, sizeHint: Point?, signal: CancellationSignal?): AssetFileDescriptor {
        val file = getFileForDocId(documentId)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    override fun createDocument(documentId: String, mimeType: String, displayName: String): String {
        val parent = getFileForDocId(documentId) ?: throw FileNotFoundException("No parent for $documentId")
        val inst = getInstanceForDocId(documentId)
        val file = File(parent.path, displayName)
        try {
            file.createNewFile()
            file.setWritable(true)
            file.setReadable(true)
        } catch (e: IOException) {
            throw FileNotFoundException("Failed to create document with name $displayName and documentId $documentId")
        }
        return getDocIdForFile(inst ?: throw FileNotFoundException("No instance for $documentId"), file)
    }

    override fun deleteDocument(documentId: String) {
        val file = getFileForDocId(documentId) ?: throw FileNotFoundException("No file for $documentId")
        if (!file.delete()) {
            throw FileNotFoundException("Failed to delete document with id $documentId")
        }
    }

    private fun getDocIdForFile(instance: KlipperInstance, file: File): String =
        DocumentIds.format(instance.id, instance.publicDirectory.path, file.absolutePath)

    private fun getChildMimeTypes(): String {
        val mimeTypes = setOf("image/*", "text/*")
        return mimeTypes.joinToString("\n")
    }

    private fun getInstanceForDocId(docId: String): KlipperInstance? {
        val parsed = DocumentIds.parse(docId) ?: return null
        return KlipperInstance.getInstance(parsed.instanceId)
    }

    private fun getFileForDocId(docId: String): File? {
        val parsed = DocumentIds.parse(docId) ?: return null
        val inst = KlipperInstance.getInstance(parsed.instanceId) ?: return null
        return File(inst.publicDirectory, parsed.relativePath)
    }

    private fun includeFile(result: MatrixCursor, docId: String?, inst: KlipperInstance?, file: File?) {
        var id = docId
        var f = file
        if (id == null) {
            f?.let { f2 -> inst?.let { i -> id = getDocIdForFile(i, f2) } }
                ?: return
        } else {
            f = getFileForDocId(id!!)
        }

        val fileVal = f ?: return
        var flags = 0
        if (fileVal.isDirectory) {
            if (fileVal.canWrite()) {
                flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            }
        } else if (fileVal.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
        }

        val displayName = fileVal.name
        val mimeType = getTypeForFile(fileVal)
        if (mimeType.startsWith("image/")) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
        }

        result.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id)
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
            add(DocumentsContract.Document.COLUMN_SIZE, fileVal.length())
            add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, fileVal.lastModified())
            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
            add(DocumentsContract.Document.COLUMN_ICON, R.drawable.icon_static)
        }
    }

    companion object {
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )

        private fun resolveRootProjection(projection: Array<String>?) =
            projection ?: DEFAULT_ROOT_PROJECTION

        private fun resolveDocumentProjection(projection: Array<String>?) =
            projection ?: DEFAULT_DOCUMENT_PROJECTION

        private fun getTypeForName(name: String): String {
            val lastDot = name.lastIndexOf('.')
            if (lastDot >= 0) {
                val ext = name.substring(lastDot + 1)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                if (mime != null) return mime
            }
            return "application/octet-stream"
        }

        private fun getTypeForFile(file: File): String {
            return if (file.isDirectory) {
                DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                getTypeForName(file.name)
            }
        }
    }
}
