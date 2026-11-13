package com.dnk.wallpaperlyrics

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

// Read only. Query CONTENT_URI for one row, or none when the wallpaper is not running, and
// register a ContentObserver on it to hear about changes. Any app may read it: it holds only the
// now playing info and colors that an app with notification access already sees.
class WallpaperStateProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.dnk.wallpaperlyrics.state"
        val CONTENT_URI: Uri get() = Uri.parse("content://$AUTHORITY/current")

        const val STATE_IDLE = "idle"
        const val STATE_TRACK = "track"

        val COLUMNS = arrayOf(
            "state", "player_package", "title", "artist", "accent", "base", "mid", "highlight"
        )

        internal fun row(snapshot: WallpaperState.Snapshot): Array<Any?> {
            val idle = snapshot.title.isNullOrBlank()
            return arrayOf(
                if (idle) STATE_IDLE else STATE_TRACK,
                if (idle) null else snapshot.playerPackage,
                if (idle) null else snapshot.title,
                if (idle) null else snapshot.artist,
                snapshot.colors[0],
                snapshot.colors[1],
                snapshot.colors[2],
                snapshot.colors[3]
            )
        }

        fun notifyReaders(context: Context) {
            context.contentResolver.notifyChange(CONTENT_URI, null)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS, 1)
        WallpaperState.current?.let { cursor.addRow(row(it)) }
        context?.let { cursor.setNotificationUri(it.contentResolver, CONTENT_URI) }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.$AUTHORITY"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read only")
}
