import java.io.InputStream
import java.nio.charset.Charset

fun Any.readFile(path: String) = javaClass.getResourceAsStream(path)!!.readTextAndClose()

private fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
    return this.bufferedReader(charset).use { it.readText() }
}