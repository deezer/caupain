package com.deezer.dependencies.cli

import com.github.ajalt.clikt.command.main
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    runBlocking {
        DependencyUpdateCheckerCli().main(args)
    }
}