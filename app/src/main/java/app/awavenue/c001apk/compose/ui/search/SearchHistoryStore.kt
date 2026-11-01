package app.awavenue.c001apk.compose.ui.search

import android.content.Context

/**
 * Search history, mirroring the Flutter app's Hive 'searchHistory'
 * box (a plain string list): a term is written only when a search
 * actually runs, de-duplicated to the front. No cap in Flutter;
 * this store caps at 100 entries for hygiene.
 */
object SearchHistoryStore {

    private const val PREFS = "search_history"
    private const val KEY = "keywords"
    private const val MAX = 100

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return raw.split("\n").filter { it.isNotEmpty() }
    }

    fun add(context: Context, keyword: String) {
        val kw = keyword.trim().replace("\n", " ")
        if (kw.isEmpty()) return
        val next = (listOf(kw) + load(context).filter { it != kw }).take(MAX)
        save(context, next)
    }

    fun remove(context: Context, keyword: String) {
        save(context, load(context).filter { it != keyword })
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun save(context: Context, keywords: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, keywords.joinToString("\n"))
            .apply()
    }
}
