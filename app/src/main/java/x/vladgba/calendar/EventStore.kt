package x.vladgba.calendar

import android.util.AtomicFile
import java.io.File

/**
 * Tiny note storage: one line per note, "yyyymmdd<TAB>escaped text".
 * Written atomically through [AtomicFile], so a crash mid-save never corrupts data.
 */
class EventStore(file: File) {

    private val atomic = AtomicFile(file)
    private val notes = HashMap<Int, MutableList<String>>()

    init {
        load()
    }

    fun get(key: Int): List<String> = notes[key] ?: emptyList()

    fun has(key: Int): Boolean = !notes[key].isNullOrEmpty()

    fun add(key: Int, text: String) {
        notes.getOrPut(key) { ArrayList() }.add(text)
        save()
    }

    fun update(key: Int, index: Int, text: String) {
        val list = notes[key] ?: return
        if (index in list.indices) {
            list[index] = text
            save()
        }
    }

    fun remove(key: Int, index: Int) {
        val list = notes[key] ?: return
        if (index in list.indices) {
            list.removeAt(index)
            if (list.isEmpty()) notes.remove(key)
            save()
        }
    }

    private fun load() {
        val bytes = try {
            atomic.readFully()
        } catch (_: Exception) {
            return // no file yet
        }
        String(bytes, Charsets.UTF_8).lineSequence().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val key = line.substring(0, tab).toIntOrNull() ?: return@forEach
            notes.getOrPut(key) { ArrayList() }.add(unescape(line.substring(tab + 1)))
        }
    }

    private fun save() {
        val sb = StringBuilder()
        for (key in notes.keys.sorted()) {
            for (text in notes[key]!!) {
                sb.append(key).append('\t').append(escape(text)).append('\n')
            }
        }
        val out = atomic.startWrite()
        try {
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
        }
    }

    private fun escape(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) when (c) {
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\t' -> sb.append("\\t")
            '\r' -> {}
            else -> sb.append(c)
        }
        return sb.toString()
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    companion object {
        fun key(year: Int, month0: Int, day: Int) = year * 10000 + (month0 + 1) * 100 + day
    }
}
