package com.skydronex.fr24fortified.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ChangelogEntry(val versionCode: Int, val versionName: String, val items: List<String>)

class WhatsNewRepository(
    private val context: Context,
    private val configRepo: ConfigRepository
) {
    fun newEntries(currentVersionCode: Int): Flow<List<ChangelogEntry>> =
        configRepo.lastSeenVersionCode.map { lastSeen ->
            parseChangelog().filter { it.versionCode > lastSeen }
        }

    suspend fun markSeen(versionCode: Int) = configRepo.saveLastSeenVersionCode(versionCode)

    private fun parseChangelog(): List<ChangelogEntry> {
        val text = context.assets.open("changelog.txt").bufferedReader().readText()
        val entries = mutableListOf<ChangelogEntry>()
        var code = -1
        var name = ""
        var items = mutableListOf<String>()
        val headerRe = Regex("""^\[(\d+)]\s+(.+)$""")

        for (line in text.lines()) {
            val trimmed = line.trim()
            val match = headerRe.matchEntire(trimmed)
            when {
                match != null -> {
                    if (code >= 0) entries += ChangelogEntry(code, name, items.toList())
                    code  = match.groupValues[1].toInt()
                    name  = match.groupValues[2].trim()
                    items = mutableListOf()
                }
                trimmed.startsWith("-") && code >= 0 ->
                    items += trimmed.removePrefix("-").trim()
            }
        }
        if (code >= 0) entries += ChangelogEntry(code, name, items.toList())
        return entries.sortedByDescending { it.versionCode }
    }
}
