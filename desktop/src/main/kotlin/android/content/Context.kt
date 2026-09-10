package android.content

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

open class Context(
    val filesDir: File = PortablePaths.dataDir
) {
    companion object {
        const val MODE_PRIVATE: Int = 0
    }

    val applicationContext: Context get() = this

    private val preferences = ConcurrentHashMap<String, SharedPreferences>()

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        preferences.computeIfAbsent(name) {
            SharedPreferences(File(filesDir, "prefs/$name.properties"))
        }
}

object PortablePaths {
    val appRoot: File by lazy {
        val command = runCatching { ProcessHandle.current().info().command().orElse("") }.getOrDefault("")
        val start = command.takeIf { it.isNotBlank() }?.let(::File)?.absoluteFile?.parentFile
        val candidates = generateSequence(start) { it.parentFile }.take(6).toList()
        candidates.firstOrNull { File(it, "app").isDirectory && File(it, "runtime").isDirectory }
            ?: start
            ?: File(System.getProperty("user.dir")).absoluteFile
    }

    val dataDir: File by lazy {
        File(appRoot, "data").apply { mkdirs() }
    }
}

class SharedPreferences internal constructor(private val file: File) {
    private val lock = Any()

    private fun load(): Properties = Properties().apply {
        if (file.isFile) file.inputStream().buffered().use(::load)
    }

    private fun persist(properties: Properties) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().buffered().use { properties.store(it, "Monitor de Noticias") }
        if (file.exists()) file.delete()
        tmp.renameTo(file)
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean = synchronized(lock) {
        load().getProperty(key)?.toBooleanStrictOrNull() ?: defaultValue
    }

    fun getInt(key: String, defaultValue: Int): Int = synchronized(lock) {
        load().getProperty(key)?.toIntOrNull() ?: defaultValue
    }

    fun getLong(key: String, defaultValue: Long): Long = synchronized(lock) {
        load().getProperty(key)?.toLongOrNull() ?: defaultValue
    }

    fun getString(key: String, defaultValue: String?): String? = synchronized(lock) {
        load().getProperty(key) ?: defaultValue
    }

    fun getStringSet(key: String, defaultValue: Set<String>?): Set<String>? = synchronized(lock) {
        val encoded = load().getProperty(key) ?: return@synchronized defaultValue
        if (encoded.isBlank()) return@synchronized emptySet()
        encoded.split("|").filter { it.isNotBlank() }.mapTo(linkedSetOf()) {
            String(Base64.getUrlDecoder().decode(it), StandardCharsets.UTF_8)
        }
    }

    fun edit(): Editor = Editor(this)

    class Editor internal constructor(private val prefs: SharedPreferences) {
        private val updates = linkedMapOf<String, String?>()

        fun putBoolean(key: String, value: Boolean) = apply { updates[key] = value.toString() }
        fun putInt(key: String, value: Int) = apply { updates[key] = value.toString() }
        fun putLong(key: String, value: Long) = apply { updates[key] = value.toString() }
        fun putString(key: String, value: String?) = apply { updates[key] = value }
        fun putStringSet(key: String, value: Set<String>?) = apply {
            updates[key] = value?.joinToString("|") {
                Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(it.toByteArray(StandardCharsets.UTF_8))
            }
        }
        fun remove(key: String) = apply { updates[key] = null }

        fun apply() = commit()

        fun commit(): Boolean = synchronized(prefs.lock) {
            val properties = prefs.load()
            updates.forEach { (key, value) ->
                if (value == null) properties.remove(key) else properties.setProperty(key, value)
            }
            prefs.persist(properties)
            true
        }
    }
}
