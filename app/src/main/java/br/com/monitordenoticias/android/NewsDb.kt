package br.com.monitordenoticias.android

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class NewsDb(context: Context) : SQLiteOpenHelper(context, "news.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE news(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,source TEXT,date INTEGER NOT NULL,link TEXT UNIQUE,snippet TEXT,important INTEGER DEFAULT 0,demand INTEGER DEFAULT 0,matched_term TEXT DEFAULT '',matched_demand TEXT DEFAULT '',captured_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE terms(id INTEGER PRIMARY KEY AUTOINCREMENT,term TEXT UNIQUE NOT NULL)")
        db.execSQL("CREATE TABLE demands(id INTEGER PRIMARY KEY AUTOINCREMENT,vehicle TEXT NOT NULL,subject TEXT NOT NULL,active INTEGER DEFAULT 1,last_checked_at INTEGER NOT NULL DEFAULT 0,last_found_count INTEGER NOT NULL DEFAULT 0,last_new_count INTEGER NOT NULL DEFAULT 0,last_error TEXT DEFAULT '')")
        val defaults = listOf("Marinha do Brasil","Capitania dos Portos","Distrito Naval","NAM Atlântico","Cisne Branco","Fragata Marinha do Brasil","Navio-Patrulha Marinha","Programa Nuclear da Marinha")
        defaults.forEach { db.execSQL("INSERT OR IGNORE INTO terms(term) VALUES(?)", arrayOf(it)) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE news ADD COLUMN matched_term TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE news ADD COLUMN matched_demand TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE news ADD COLUMN captured_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE news SET captured_at=date WHERE captured_at=0")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE demands ADD COLUMN last_checked_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE demands ADD COLUMN last_found_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE demands ADD COLUMN last_new_count INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE demands ADD COLUMN last_error TEXT DEFAULT ''")
        }
    }

    fun insertNews(items: List<News>): List<News> {
        val inserted = mutableListOf<News>()
        writableDatabase.beginTransaction()
        try {
            items.forEach { n ->
                val values = ContentValues().apply {
                    put("title", n.title); put("source", n.source); put("date", n.date); put("link", n.link)
                    put("snippet", n.snippet); put("important", if (n.important) 1 else 0); put("demand", if (n.demand) 1 else 0)
                    put("matched_term", n.matchedTerm); put("matched_demand", n.matchedDemand); put("captured_at", n.capturedAt)
                }
                val id = writableDatabase.insertWithOnConflict("news", null, values, SQLiteDatabase.CONFLICT_IGNORE)
                if (id != -1L) {
                    inserted += n.copy(id = id)
                } else {
                    // A matéria pode já ter sido capturada pela busca geral antes de ser
                    // encontrada por uma Demanda. Nesse caso ela não é "nova", mas a
                    // classificação precisa ser atualizada para que o resultado continue
                    // acessível dentro do cartão da Demanda.
                    val update = ContentValues().apply {
                        put("title", n.title)
                        put("source", n.source)
                        put("date", n.date)
                        put("snippet", n.snippet)
                        if (n.important) put("important", 1)
                        if (n.demand) put("demand", 1)
                        if (n.matchedTerm.isNotBlank()) put("matched_term", n.matchedTerm)
                        if (n.matchedDemand.isNotBlank()) put("matched_demand", n.matchedDemand)
                    }
                    writableDatabase.update("news", update, "link=?", arrayOf(n.link))
                }
            }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        return inserted
    }

    fun listRecent(hours: Int = 24, limit: Int = 500): List<News> {
        val cutoff = System.currentTimeMillis() - hours * 60L * 60L * 1000L
        return queryNews("date>=?", arrayOf(cutoff.toString()), limit)
    }

    fun listNews(limit: Int = 500): List<News> = queryNews(null, null, limit)

    private fun queryNews(where: String?, args: Array<String>?, limit: Int): List<News> {
        val out = mutableListOf<News>()
        val sql = buildString {
            append("SELECT id,title,source,date,link,snippet,important,demand,matched_term,matched_demand,captured_at FROM news")
            if (where != null) append(" WHERE ").append(where)
            append(" ORDER BY date DESC LIMIT ?")
        }
        val queryArgs = (args?.toList().orEmpty() + limit.toString()).toTypedArray()
        readableDatabase.rawQuery(sql, queryArgs).use { c ->
            while (c.moveToNext()) out += News(
                c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4), c.getString(5),
                c.getInt(6)==1, c.getInt(7)==1, c.getString(8).orEmpty(), c.getString(9).orEmpty(), c.getLong(10)
            )
        }
        return out
    }

    fun listTerms(): List<String> = readableDatabase.rawQuery("SELECT term FROM terms ORDER BY term",null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
    fun addTerm(term: String) { val clean=term.trim(); if(clean.isNotBlank()) writableDatabase.execSQL("INSERT OR IGNORE INTO terms(term) VALUES(?)", arrayOf(clean)) }
    fun removeTerm(term: String) { writableDatabase.execSQL("DELETE FROM terms WHERE term=?", arrayOf(term)) }

    fun listDemands(): List<Demand> = readableDatabase.rawQuery(
        "SELECT id,vehicle,subject,active,last_checked_at,last_found_count,last_new_count,last_error FROM demands ORDER BY id DESC", null
    ).use { c -> buildList {
        while(c.moveToNext()) add(Demand(
            id=c.getLong(0), vehicle=c.getString(1), subject=c.getString(2), active=c.getInt(3)==1,
            lastCheckedAt=c.getLong(4), lastFoundCount=c.getInt(5), lastNewCount=c.getInt(6), lastError=c.getString(7).orEmpty()
        ))
    } }

    fun addDemand(vehicle: String, subject: String) {
        if(vehicle.isNotBlank() && subject.isNotBlank())
            writableDatabase.execSQL("INSERT INTO demands(vehicle,subject) VALUES(?,?)", arrayOf(vehicle.trim(),subject.trim()))
    }

    fun updateDemandStatus(id: Long, checkedAt: Long, foundCount: Int, newCount: Int, error: String = "") {
        val values = ContentValues().apply {
            put("last_checked_at", checkedAt)
            put("last_found_count", foundCount)
            put("last_new_count", newCount)
            put("last_error", error)
        }
        writableDatabase.update("demands", values, "id=?", arrayOf(id.toString()))
    }

    fun removeDemand(id: Long) { writableDatabase.execSQL("DELETE FROM demands WHERE id=?", arrayOf(id)) }
    fun clearHistory() { writableDatabase.execSQL("DELETE FROM news") }
}
