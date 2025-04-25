package com.deezer.caupain

/**
 * Base exception for exceptions thrown by Caupain.
 */
public abstract class CaupainException(message: String, cause: Throwable? = null) :
    Exception(message, cause)