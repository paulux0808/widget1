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

        val json = JSONObject(extractJsonPayload(raw))
        val table = json.getJSONObject("table")
        val cols = table.getJSONArray("cols")
        val rows = table.getJSONArray("rows")

        val idx = mutableMapOf<String, Int>()
        for (i in 0 until cols.length()) {
            val col = cols.optJSONObject(i) ?: continue
            val label = col.optString("label").trim()
            val id = col.optString("id").trim()
            if (label.isNotEmpty()) idx[label] = i
            if (id.isNotEmpty()) idx[id] = i
        }
        val iG = colIndex(idx, "학년", "grade")
        val iB = colIndex(idx, "반", "ban", "class")
        val iD = colIndex(idx, "요일", "day")
        val iP = colIndex(idx, "교시", "period")
        val iS = colIndex(idx, "과목", "교과", "subject")
        val iT = colIndex(idx, "교사", "선생님", "teacher")
        val iL = colIndex(idx, "슬롯", "slot")

        val list = mutableListOf<Entry>()
        for (r in 0 until rows.length()) {
            val c = rows.optJSONObject(r)?.optJSONArray("c") ?: continue

            val grade = parseIntCell(c, iG) ?: continue
            val ban = parseIntCell(c, iB) ?: continue

            val day = normalizeDay(cell(c, iD))
            if (day.isEmpty()) continue

            val period = parseIntCell(c, iP) ?: continue

            val subject = cell(c, iS)
            if (subject.isEmpty()) continue

            list += Entry(grade, ban, day, period, subject, cell(c, iT), cell(c, iL))
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

    private fun extractJsonPayload(raw: String): String {
        val startToken = "google.visualization.Query.setResponse("
        val start = raw.indexOf(startToken)
        if (start >= 0) {
            val contentStart = start + startToken.length
            val end = raw.lastIndexOf(");")
            if (end > contentStart) return raw.substring(contentStart, end).trim()
        }

        val fallbackStart = raw.indexOf('{')
        val fallbackEnd = raw.lastIndexOf('}')
        require(fallbackStart >= 0 && fallbackEnd > fallbackStart) {
            "Invalid gviz response (len=${raw.length}, head=${raw.take(120)})"
        }
        return raw.substring(fallbackStart, fallbackEnd + 1)
    }

    private fun cell(cells: org.json.JSONArray, index: Int): String {
        if (index < 0 || index >= cells.length()) return ""
        val obj = cells.optJSONObject(index) ?: return ""

        val formatted = obj.optString("f", "").trim()
        if (formatted.isNotEmpty() && formatted != "null") return formatted

        val value = obj.opt("v") ?: return ""
        return if (value == JSONObject.NULL) "" else value.toString().trim()
    }

    private fun parseIntCell(cells: org.json.JSONArray, index: Int): Int? {
        val raw = cell(cells, index).replace(",", "").trim()
        if (raw.isEmpty()) return null

        raw.toIntOrNull()?.let { return it }
        raw.toDoubleOrNull()?.let { return it.toInt() }

        // JS parseInt("3교시") -> 3 처럼 동작하게 맞춘다.
        val leadingInt = Regex("""^[+-]?\d+""").find(raw)?.value
        if (!leadingInt.isNullOrEmpty()) return leadingInt.toIntOrNull()

        // 셀 서식 때문에 앞에 문자가 붙은 경우를 대비한 마지막 보정.
        val anyInt = Regex("""[+-]?\d+""").find(raw)?.value
        return anyInt?.toIntOrNull()
    }

    private fun normalizeDay(value: String): String {
        return when (value.trim()) {
            "월", "월요일" -> "월"
            "화", "화요일" -> "화"
            "수", "수요일" -> "수"
            "목", "목요일" -> "목"
            "금", "금요일" -> "금"
            else -> value.trim()
        }
    }

    private fun colIndex(indexMap: Map<String, Int>, vararg candidates: String): Int {
        for (candidate in candidates) {
            indexMap[candidate]?.let { return it }
        }
        return -1
    }
}
