package com.pranvirsingh.wordlens.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.pranvirsingh.wordlens.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

/**
 * The offline dictionary: a read-only SQLite file shipped inside the app,
 * copied once into private storage on first use (SQLite can't read straight
 * out of the APK).
 */
class DictionaryDatabase(context: Context) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var database: SQLiteDatabase? = null

    /** Opens the dictionary on first call (blocking I/O — call off the main thread). */
    suspend fun lexicon(): Lexicon = mutex.withLock {
        val db = database ?: open().also { database = it }
        SqliteLexicon(db)
    }

    private fun open(): SQLiteDatabase {
        val file = install()
        return SQLiteDatabase.openDatabase(
            file.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )
    }

    private fun install(): File {
        val dir = File(appContext.noBackupFilesDir, "dictionary")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Can't create $dir")
        // Named after a fingerprint of the bundled file (computed at build
        // time), so any change to the dictionary — even in a rebuild with the
        // same app version — installs the new file and removes the old one.
        val target = File(dir, "wordnet-${BuildConfig.DICTIONARY_FINGERPRINT}.db")

        if (!target.exists()) {
            // Copy to a temp file first so a crash mid-copy never leaves a half-written dictionary.
            val partial = File(dir, "${target.name}.partial")
            appContext.assets.open(ASSET_PATH).use { input ->
                partial.outputStream().use { output -> input.copyTo(output, COPY_BUFFER) }
            }
            if (!partial.renameTo(target)) throw IOException("Can't move dictionary into place")
        }

        dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
        return target
    }

    private class SqliteLexicon(private val db: SQLiteDatabase) : Lexicon {

        override fun senses(lemma: String): List<SenseRecord> =
            db.rawQuery(DictionarySql.SENSES, arrayOf(lemma)).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val pos = PartOfSpeech.fromCode(cursor.getString(0)) ?: continue
                        add(
                            SenseRecord(
                                pos = pos,
                                rank = cursor.getInt(1),
                                tagCount = cursor.getInt(2),
                                definition = cursor.getString(3),
                                examples = DictionarySql.splitList(cursor.getString(4)),
                                lemmas = DictionarySql.splitList(cursor.getString(5)),
                            ),
                        )
                    }
                }
            }

        override fun exceptionBases(form: String): List<Pair<PartOfSpeech, String>> =
            db.rawQuery(DictionarySql.EXCEPTIONS, arrayOf(form)).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val pos = PartOfSpeech.fromCode(cursor.getString(0)) ?: continue
                        add(pos to cursor.getString(1))
                    }
                }
            }
    }

    private companion object {
        const val ASSET_PATH = "dictionary/wordnet.db"
        const val COPY_BUFFER = 64 * 1024
    }
}

/** The queries against the dictionary file (schema: tools/build_dictionary.py). */
internal object DictionarySql {
    const val SENSES = """
        SELECT ls.pos, ls.rank, ls.tag_count, s.definition, s.examples, s.lemmas
        FROM lemma_senses ls JOIN synsets s ON s.id = ls.synset_id
        WHERE ls.lemma = ?
        ORDER BY ls.pos, ls.rank
    """
    const val EXCEPTIONS = "SELECT pos, base FROM exceptions WHERE form = ?"

    /** Lists (examples, synonyms) are stored newline-separated. */
    fun splitList(stored: String): List<String> = stored.split('\n').filter { it.isNotBlank() }
}
