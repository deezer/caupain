import com.deezer.dependencies.cli.DependencyUpdateCheckerCli
import com.github.ajalt.clikt.command.main
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    runBlocking {
        DependencyUpdateCheckerCli().main(args)
    }
}