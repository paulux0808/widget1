package com.my.time

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DataFetcher {
    private const val SHEET_ID = "1ZwOdHmsFGbCNTDJtUvLk5ExLxgLCAXyTVglkULv99lQ"
    private const val SCHEDULE_GID = "1926478023"

    data class Entry(
        val grade: Int, val ban: Int, val day: String,
        val period: Int, val subject: String,
        val teacher: String, val slot: String
    )

    @Volatile private var cache: List<Entry>? = null
    @Volatile private var cacheTime: Long = 0
    private const val CACHE_MS = 10 * 60 * 1000L

    fun fetchAll(forceRefresh: Boolean = false): List<Entry> {
        val now = System.currentTimeMillis()
        cache?.let { if (!forceRefresh && now - cacheTime < CACHE_MS) return it }

        val url = URL(
            "https://docs.google.com/spreadsheets/d/$SHEET_ID" +
                    "/gviz/tq?tqx=out:json&gid=$SCHEDULE_GID&headers=1&_=$now"
        )
        val raw = (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start) {
            "Invalid gviz response (len=${raw.length}, head=${raw.take(60)})"
        }
        val json = JSONObject(raw.substring(start, end + 1))
        val table = json.getJSONObject("table")
        val cols = table.getJSONArray("cols")
        val rows = table.getJSONArray("rows")

        val idx = mutableMapOf<String, Int>()
        for (i in 0 until cols.length()) {
            val label = cols.getJSONObject(i).optString("label").trim()
            if (label.isNotEmpty()) idx[label] = i
        }
        val iG = idx["학년"] ?: -1; val iB = idx["반"] ?: -1
        val iD = idx["요일"] ?: -1; val iP = idx["교시"] ?: -1
        val iS = idx["과목"] ?: -1; val iT = idx["교사"] ?: -1
        val iL = idx["슬롯"] ?: -1

        val list = mutableListOf<Entry>()
        for (r in 0 until rows.length()) {
            val c = rows.getJSONObject(r).optJSONArray("c") ?: continue

            fun cell(i: Int): String {
                if (i < 0 || i >= c.length()) return ""
                val o = c.optJSONObject(i) ?: return ""
                val f = o.optString("f", "").trim()
                if (f.isNotEmpty() && f != "null") return f
                return o.opt("v")?.toString()?.trim().orEmpty()
            }

            val grade = cell(iG).toIntOrNull() ?: continue
            val ban = cell(iB).toIntOrNull() ?: continue

            val day = cell(iD)
            if (day.isEmpty()) continue

            val period = cell(iP).toIntOrNull() ?: continue

            val subject = cell(iS)
            if (subject.isEmpty()) continue

            list += Entry(grade, ban, day, period, subject, cell(iT), cell(iL))
        }

        cache = list
        cacheTime = now
        return list
    }

    fun allTeachers(): List<String> =
        fetchAll().mapNotNull { it.teacher.takeIf(String::isNotEmpty) }.distinct().sorted()

    fun teacherToday(teacher: String, dayLabel: String): List<Entry> =
        fetchAll().filter { it.teacher == teacher && it.day == dayLabel }
            .sortedBy { it.period }
}
