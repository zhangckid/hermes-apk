package win.catgo.gpt.data

/** Production persists with Android Keystore; integration tests use isolated memory. */
interface SessionStorage {
    fun read(): String?
    fun write(value: String)
    fun clear()
}
