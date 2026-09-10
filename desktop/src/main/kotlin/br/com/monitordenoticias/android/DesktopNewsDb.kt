package br.com.monitordenoticias.android

import android.content.Context
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class NewsDb(context: Context) : AutoCloseable {
    private val connection: Connection

    init {
        Class.forName("org.sqlite.JDBC")
        val dbFile = File(context.filesDir, "news.db")
        dbFile.parentFile?.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection.createStatement().use {
            it.execute("PRAGMA journal_mode=WAL")
            it.execute("PRAGMA busy_timeout=5000")
            it.execute("CREATE TABLE IF NOT EXISTS news(id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,source TEXT,date INTEGER NOT NULL,link TEXT UNIQUE,snippet TEXT,important INTEGER DEFAULT 0,demand INTEGER DEFAULT 0,matched_term TEXT DEFAULT '',matched_demand TEXT DEFAULT '',captured_at INTEGER NOT NULL DEFAULT 0)")
            it.execute("CREATE TABLE IF NOT EXISTS terms(id INTEGER PRIMARY KEY AUTOINCREMENT,term TEXT UNIQUE NOT NULL)")
            it.execute("CREATE TABLE IF NOT EXISTS demands(id INTEGER PRIMARY KEY AUTOINCREMENT,vehicle TEXT NOT NULL,subject TEXT NOT NULL,active INTEGER DEFAULT 1,last_checked_at INTEGER NOT NULL DEFAULT 0,last_found_count INTEGER NOT NULL DEFAULT 0,last_new_count INTEGER NOT NULL DEFAULT 0,last_error TEXT DEFAULT '')")
        }
        ensureColumns()
        seedTerms()
    }

    private fun ensureColumns() {
        val cols = connection.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(news)").use { rs ->
                buildSet { while (rs.next()) add(rs.getString("name")) }
            }
        }
        connection.createStatement().use { st ->
            if ("matched_term" !in cols) st.execute("ALTER TABLE news ADD COLUMN matched_term TEXT DEFAULT ''")
            if ("matched_demand" !in cols) st.execute("ALTER TABLE news ADD COLUMN matched_demand TEXT DEFAULT ''")
            if ("captured_at" !in cols) {
                st.execute("ALTER TABLE news ADD COLUMN captured_at INTEGER NOT NULL DEFAULT 0")
                st.execute("UPDATE news SET captured_at=date WHERE captured_at=0")
            }
        }
        val demandCols = connection.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info(demands)").use { rs ->
                buildSet { while (rs.next()) add(rs.getString("name")) }
            }
        }
        connection.createStatement().use { st ->
            if ("last_checked_at" !in demandCols) st.execute("ALTER TABLE demands ADD COLUMN last_checked_at INTEGER NOT NULL DEFAULT 0")
            if ("last_found_count" !in demandCols) st.execute("ALTER TABLE demands ADD COLUMN last_found_count INTEGER NOT NULL DEFAULT 0")
            if ("last_new_count" !in demandCols) st.execute("ALTER TABLE demands ADD COLUMN last_new_count INTEGER NOT NULL DEFAULT 0")
            if ("last_error" !in demandCols) st.execute("ALTER TABLE demands ADD COLUMN last_error TEXT DEFAULT ''")
        }
    }

    private fun seedTerms() {
        val defaults = listOf(
            "Marinha do Brasil","Capitania dos Portos","Distrito Naval","NAM Atlântico",
            "Cisne Branco","Fragata Marinha do Brasil","Navio-Patrulha Marinha","Programa Nuclear da Marinha"
        )
        connection.prepareStatement("INSERT OR IGNORE INTO terms(term) VALUES(?)").use { ps ->
            defaults.forEach { value -> ps.setString(1, value); ps.addBatch() }
            ps.executeBatch()
        }
    }

    @Synchronized
    fun insertNews(items: List<News>): List<News> {
        val inserted = mutableListOf<News>()
        connection.autoCommit = false
        try {
            items.forEach { n ->
                connection.prepareStatement(
                    "INSERT OR IGNORE INTO news(title,source,date,link,snippet,important,demand,matched_term,matched_demand,captured_at) VALUES(?,?,?,?,?,?,?,?,?,?)"
                ).use { ps ->
                    ps.setString(1, n.title); ps.setString(2, n.source); ps.setLong(3, n.date); ps.setString(4, n.link)
                    ps.setString(5, n.snippet); ps.setInt(6, if (n.important) 1 else 0); ps.setInt(7, if (n.demand) 1 else 0)
                    ps.setString(8, n.matchedTerm); ps.setString(9, n.matchedDemand); ps.setLong(10, n.capturedAt)
                    if (ps.executeUpdate() > 0) {
                        val id = connection.createStatement().use { st ->
                            st.executeQuery("SELECT last_insert_rowid()").use { rs -> if (rs.next()) rs.getLong(1) else 0L }
                        }
                        inserted += n.copy(id = id)
                    } else {
                        connection.prepareStatement(
                            """UPDATE news SET title=?,source=?,date=?,snippet=?,
                               important=CASE WHEN ?=1 THEN 1 ELSE important END,
                               demand=CASE WHEN ?=1 THEN 1 ELSE demand END,
                               matched_term=CASE WHEN ?<>'' THEN ? ELSE matched_term END,
                               matched_demand=CASE WHEN ?<>'' THEN ? ELSE matched_demand END
                               WHERE link=?""".trimIndent()
                        ).use { up ->
                            up.setString(1,n.title); up.setString(2,n.source); up.setLong(3,n.date); up.setString(4,n.snippet)
                            up.setInt(5,if(n.important)1 else 0); up.setInt(6,if(n.demand)1 else 0)
                            up.setString(7,n.matchedTerm); up.setString(8,n.matchedTerm)
                            up.setString(9,n.matchedDemand); up.setString(10,n.matchedDemand); up.setString(11,n.link)
                            up.executeUpdate()
                        }
                    }
                }
            }
            connection.commit()
        } catch (t: Throwable) {
            connection.rollback()
            throw t
        } finally {
            connection.autoCommit = true
        }
        return inserted
    }

    fun listRecent(hours: Int = 24, limit: Int = 500): List<News> {
        val cutoff = System.currentTimeMillis() - hours * 60L * 60L * 1000L
        return queryNews("date>=?", listOf(cutoff), limit)
    }

    fun listNews(limit: Int = 500): List<News> = queryNews(null, emptyList(), limit)

    private fun queryNews(where: String?, args: List<Long>, limit: Int): List<News> {
        val sql = buildString {
            append("SELECT id,title,source,date,link,snippet,important,demand,matched_term,matched_demand,captured_at FROM news")
            if (where != null) append(" WHERE ").append(where)
            append(" ORDER BY date DESC LIMIT ?")
        }
        return connection.prepareStatement(sql).use { ps ->
            var i=1; args.forEach { ps.setLong(i++,it) }; ps.setInt(i,limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while(rs.next()) add(News(
                        id=rs.getLong(1), title=rs.getString(2), source=rs.getString(3).orEmpty(), date=rs.getLong(4),
                        link=rs.getString(5), snippet=rs.getString(6).orEmpty(), important=rs.getInt(7)==1,
                        demand=rs.getInt(8)==1, matchedTerm=rs.getString(9).orEmpty(),
                        matchedDemand=rs.getString(10).orEmpty(), capturedAt=rs.getLong(11)
                    ))
                }
            }
        }
    }

    fun listTerms(): List<String> = connection.createStatement().use { st ->
        st.executeQuery("SELECT term FROM terms ORDER BY term COLLATE NOCASE").use { rs ->
            buildList { while(rs.next()) add(rs.getString(1)) }
        }
    }

    fun addTerm(term: String) {
        val clean=term.trim(); if(clean.isBlank()) return
        connection.prepareStatement("INSERT OR IGNORE INTO terms(term) VALUES(?)").use { it.setString(1,clean); it.executeUpdate() }
    }

    fun removeTerm(term: String) {
        connection.prepareStatement("DELETE FROM terms WHERE term=?").use { it.setString(1,term); it.executeUpdate() }
    }

    fun listDemands(): List<Demand> = connection.createStatement().use { st ->
        st.executeQuery("SELECT id,vehicle,subject,active,last_checked_at,last_found_count,last_new_count,last_error FROM demands ORDER BY id DESC").use { rs ->
            buildList {
                while(rs.next()) add(Demand(
                    id=rs.getLong(1), vehicle=rs.getString(2), subject=rs.getString(3), active=rs.getInt(4)==1,
                    lastCheckedAt=rs.getLong(5), lastFoundCount=rs.getInt(6), lastNewCount=rs.getInt(7),
                    lastError=rs.getString(8).orEmpty()
                ))
            }
        }
    }

    fun addDemand(vehicle: String, subject: String) {
        if(vehicle.isBlank() || subject.isBlank()) return
        connection.prepareStatement("INSERT INTO demands(vehicle,subject) VALUES(?,?)").use {
            it.setString(1,vehicle.trim()); it.setString(2,subject.trim()); it.executeUpdate()
        }
    }

    fun updateDemandStatus(id: Long, checkedAt: Long, foundCount: Int, newCount: Int, error: String = "") {
        connection.prepareStatement("UPDATE demands SET last_checked_at=?,last_found_count=?,last_new_count=?,last_error=? WHERE id=?").use {
            it.setLong(1,checkedAt); it.setInt(2,foundCount); it.setInt(3,newCount); it.setString(4,error); it.setLong(5,id); it.executeUpdate()
        }
    }

    fun removeDemand(id: Long) {
        connection.prepareStatement("DELETE FROM demands WHERE id=?").use { it.setLong(1,id); it.executeUpdate() }
    }

    fun clearHistory() { connection.createStatement().use { it.executeUpdate("DELETE FROM news") } }

    override fun close() { runCatching { connection.close() } }
}
