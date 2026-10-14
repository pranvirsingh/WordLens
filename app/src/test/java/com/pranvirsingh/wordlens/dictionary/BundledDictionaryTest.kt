package com.pranvirsingh.wordlens.dictionary

import com.pranvirsingh.wordlens.dictionary.PartOfSpeech.ADJECTIVE
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech.NOUN
import com.pranvirsingh.wordlens.dictionary.PartOfSpeech.VERB
import kotlinx.coroutines.test.runTest
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs real words through the dictionary file that actually ships in the app,
 * using the same queries the app uses — proves the data, the schema and the
 * lookup logic all agree.
 */
class BundledDictionaryTest {

    private val repository = DictionaryRepository(openLexicon = { JdbcLexicon(connection) })

    private suspend fun lookUp(word: String) = repository.lookup(listOf(word))

    @Test
    fun `a plain word is found with a sensible first meaning`() = runTest {
        val entry = lookUp("serendipity")!!.entries.first()
        assertEquals("serendipity", entry.lemma)
        assertEquals(NOUN, entry.pos)
        assertTrue("luck" in entry.senses.first().definition)
    }

    @Test
    fun `irregular past tenses lead to the base verb`() = runTest {
        assertEquals("go" to VERB, lookUp("went")!!.entries.first().let { it.lemma to it.pos })
        assertEquals("see" to VERB, lookUp("saw")!!.entries.first().let { it.lemma to it.pos })
        assertEquals("run" to VERB, lookUp("ran")!!.entries.first().let { it.lemma to it.pos })
    }

    @Test
    fun `regular inflections lead to the base word`() = runTest {
        assertEquals("child", lookUp("children")!!.entries.first().lemma)
        assertEquals("whisper", lookUp("whispered")!!.entries.first().lemma)
        assertEquals("run", lookUp("running")!!.entries.first().lemma)
    }

    @Test
    fun `comparatives lead to the adjective`() = runTest {
        val first = lookUp("better")!!.entries.first()
        assertEquals("good" to ADJECTIVE, first.lemma to first.pos)
    }

    @Test
    fun `definitions read cleanly`() = runTest {
        val definitions = lookUp("able")!!.entries.flatMap { it.senses }.map { it.definition }
        assertTrue(definitions.none { '`' in it || '"' in it })
    }

    @Test
    fun `opposites are included, directly and through similar words`() = runTest {
        val hot = lookUp("hot")!!.entries.first { it.pos == ADJECTIVE }
        assertTrue("cold" in hot.senses.first().antonyms)
        val reluctant = lookUp("reluctant")!!.entries.first()
        assertTrue("willing" in reluctant.senses.first().antonyms)
    }

    @Test
    fun `nonsense is not found`() = runTest {
        assertNull(lookUp("qwrtzpl"))
    }

    companion object {
        private lateinit var connection: Connection

        @JvmStatic
        @BeforeClass
        fun open() {
            val file = File("src/main/assets/dictionary/wordnet.db")
            check(file.isFile) { "Bundled dictionary missing at ${file.absolutePath}" }
            connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
        }

        @JvmStatic
        @AfterClass
        fun close() = connection.close()
    }

    private class JdbcLexicon(private val connection: Connection) : Lexicon {
        override fun senses(lemma: String): List<SenseRecord> =
            connection.prepareStatement(DictionarySql.SENSES).use { statement ->
                statement.setString(1, lemma)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            val pos = PartOfSpeech.fromCode(rows.getString(1)) ?: continue
                            add(
                                SenseRecord(
                                    pos = pos,
                                    rank = rows.getInt(2),
                                    tagCount = rows.getInt(3),
                                    definition = rows.getString(4),
                                    examples = DictionarySql.splitList(rows.getString(5)),
                                    lemmas = DictionarySql.splitList(rows.getString(6)),
                                    antonyms = DictionarySql.splitList(rows.getString(7)),
                                ),
                            )
                        }
                    }
                }
            }

        override fun exceptionBases(form: String): List<Pair<PartOfSpeech, String>> =
            connection.prepareStatement(DictionarySql.EXCEPTIONS).use { statement ->
                statement.setString(1, form)
                statement.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            val pos = PartOfSpeech.fromCode(rows.getString(1)) ?: continue
                            add(pos to rows.getString(2))
                        }
                    }
                }
            }
    }
}
