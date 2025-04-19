package com.deezer.dependencies.formatting.console

/**
 * ConsolePrinter is an interface for printing messages to the console.
 */
public interface ConsolePrinter {

    /**
     * Prints a message to the console.
     *
     * @param message The message to print.
     */
    public fun print(message: String)

    /**
     * Prints an error message to the console.
     *
     * @param message The error message to print.
     */
    public fun printError(message: String)
}