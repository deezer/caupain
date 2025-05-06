/*
 * MIT License
 *
 * Copyright (c) 2025 Deezer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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