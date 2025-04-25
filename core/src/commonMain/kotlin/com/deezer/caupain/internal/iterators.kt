package com.deezer.caupain.internal

import kotlin.reflect.KClass

internal class CatchingIterator<T>(
    private val delegate: Iterator<T>,
    private val ignoredExceptionClass: KClass<out Throwable>
) : Iterator<T> {

    private var nextState = -1
    private var nextItem: T? = null

    @Suppress("TooGenericExceptionCaught") // Needed because we wanna catch the right one
    private fun calcNext() {
        while (delegate.hasNext()) {
            try {
                val item = delegate.next()
                nextItem = item
                nextState = 1
                return
            } catch (e: Throwable) {
                if (!ignoredExceptionClass.isInstance(e)) throw e
            }
        }
        nextState = 0
    }

    override fun hasNext(): Boolean {
        if (nextState == -1) calcNext()
        return nextState == 1
    }

    override fun next(): T {
        if (nextState == -1) calcNext()
        if (nextState == 0) throw NoSuchElementException()
        val result = nextItem
        nextItem = null
        nextState = -1
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

internal inline fun <T, reified E : Throwable> Iterable<T>.catch(): Iterable<T> =
    object : Iterable<T> {
        override fun iterator(): Iterator<T> = CatchingIterator(this@catch.iterator(), E::class)
    }