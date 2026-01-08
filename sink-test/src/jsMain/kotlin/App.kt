import com.deezer.caupain.internal.systemSink
import okio.buffer
import okio.use

fun main() {
    systemSink().buffer().use { sink ->
        sink.writeUtf8("test\ttest")
        sink.flush()
    }
}