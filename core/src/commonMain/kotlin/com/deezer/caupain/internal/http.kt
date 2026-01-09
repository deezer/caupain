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

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.plugins.SendCountExceedException
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.io.IOException

internal expect fun HttpClientEngineConfig.configureKtorEngine()

// We do not use it as a scope, but HttpClient does implement scope
@Suppress("SuspendFunWithCoroutineScopeReceiver")
internal suspend inline fun <reified T, R> HttpClient.processRequest(
    default: R,
    transform: (T) -> R,
    onRecoverableError: (Throwable) -> Unit = {},
    executeRequest: suspend HttpClient.() -> HttpResponse
): R {
    return try {
        executeRequest()
            .takeIf { it.status.isSuccess() }
            ?.body<T>()
            ?.let(transform)
            ?: default
    } catch (ignored: IOException) {
        onRecoverableError(ignored)
        default
    } catch (ignored: SendCountExceedException) {
        onRecoverableError(ignored)
        default
    }
}

// We do not use it as a scope, but HttpClient does implement scope
@Suppress("SuspendFunWithCoroutineScopeReceiver")
internal suspend inline fun <reified R> HttpClient.processRequest(
    default: R,
    onRecoverableError: (Throwable) -> Unit = {},
    executeRequest: suspend HttpClient.() -> HttpResponse
): R {
    return processRequest<R, R>(
        default = default,
        transform = { it },
        onRecoverableError = onRecoverableError,
        executeRequest = executeRequest,
    )
}