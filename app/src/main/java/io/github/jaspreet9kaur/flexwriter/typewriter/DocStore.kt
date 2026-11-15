package io.github.jaspreet9kaur.flexwriter.typewriter

import android.content.Context
import java.io.File

data class Page(val id: String, val title: String, val modified: Long, val words: Int)

/** Typewriter pages, saved as plain text in the app's private storage. */
class DocStore(context: Context) {
    private val dir = File(context.filesDir, "pages").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("typewriter", Context.MODE_PRIVATE)
    private val idPattern = Regex("^page_\\d{1,19}$")

    fun currentId(): String =
        prefs.getString("current", null)?.takeIf { idPattern.matches(it) } ?: newId().also { setCurrent(it) }

    fun setCurrent(id: String) = prefs.edit().putString("current", id).apply()

    fun newId(): String = "page_${System.currentTimeMillis()}"

    fun load(id: String): String = file(id)?.takeIf { it.exists() }?.readText() ?: ""

    fun save(id: String, text: String) {
        val f = file(id) ?: return
        if (text.isEmpty() && !f.exists()) return
        f.writeText(text)
    }

    fun delete(id: String) {
        file(id)?.delete()
    }

    fun list(): List<Page> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".txt") } ?: emptyArray())
            .sortedByDescending { it.lastModified() }
            .map { f ->
                val txt = f.readText()
                Page(
                    id = f.nameWithoutExtension,
                    title = txt.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(42) ?: "Untitled",
                    modified = f.lastModified(),
                    words = countWords(txt),
                )
            }

    private fun file(id: String): File? = if (idPattern.matches(id)) File(dir, "$id.txt") else null

    companion object {
        fun countWords(s: String): Int = s.split(Regex("\\s+")).count { it.isNotEmpty() }
    }
}
