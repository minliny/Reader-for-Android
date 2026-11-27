package io.reader.rebuild.host.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream

internal class AuthorizedDocument(val name: String, val open: () -> InputStream)

/** The returned document can be consumed while the SAF read grant is valid. */
internal object SystemDocumentPicker {
    fun request() = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).apply {
        type = "*/*"
    }

    fun granted(resolver: ContentResolver, uri: Uri): AuthorizedDocument {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
        return AuthorizedDocument(requireNotNull(name)) { requireNotNull(resolver.openInputStream(uri)) }
    }
}
