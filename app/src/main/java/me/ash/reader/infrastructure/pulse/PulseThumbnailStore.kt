package me.ash.reader.infrastructure.pulse

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Persistent local storage for Pulse card thumbnails.
 *
 * The article link is deliberately used as the stable key so no database
 * column is needed and the same article can be found after a resync.
 */
object PulseThumbnailStore {
    private const val DIRECTORY_NAME = "pulse_thumbnails"
    private const val EXTENSION = ".webp"

    fun fileFor(context: Context, articleLink: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(articleLink.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        return context.filesDir.resolve(DIRECTORY_NAME).resolve(name + EXTENSION)
    }

    fun existing(context: Context, articleLink: String): File? {
        return fileFor(context, articleLink).takeIf { it.isFile && it.length() > 0L }
    }
}
