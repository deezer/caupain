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

package com.deezer.caupain.plugin.internal

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.provider.AbstractMinimalProvider
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.provider.Provider
import java.util.Optional
import java.util.function.Function

internal fun <T : Any> Provider<T>.asOptional(): Provider<Optional<T>> {
    return map { Optional.of(it) }.orElse(Optional.empty())
}

internal inline fun <T : Any, reified R : Any> combine(
    vararg providers: Provider<out T>,
    crossinline combiner: (List<T>) -> R?,
): Provider<R> = combine(providers = providers.toList(), combiner = combiner)

internal inline fun <T : Any, reified R : Any> combine(
    providers: List<Provider<out T>>,
    crossinline combiner: (List<T>) -> R?,
): Provider<R> {
    return CombinedProvider(
        providers = providers,
        combiner = { providers -> combiner(providers) },
        type = R::class.java
    )
}

@Suppress("UNCHECKED_CAST")
internal inline fun <T1 : Any, T2 : Any, reified R : Any> combine(
    provider1: Provider<T1>,
    provider2: Provider<T2>,
    crossinline combiner: (T1, T2) -> R?,
): Provider<R> = combine(listOf(provider1, provider2)) { values ->
    combiner(values[0] as T1, values[1] as T2)
}

internal class CombinedProvider<T : Any, R : Any>(
    providers: List<Provider<out T>>,
    private val combiner: Function<List<T>, R?>,
    private val type: Class<R>,
) : AbstractMinimalProvider<R>() {

    private val providers: List<ProviderInternal<out T>> =
        providers.map { Providers.internal(it) }

    override fun toStringNoReentrance(): String {
        return providers.joinToString(prefix = "combined(", postfix = ")", separator = ", ")
    }

    override fun calculatePresence(consumer: ValueSupplier.ValueConsumer): Boolean {
        openScope().use {
            if (providers.any { !it.calculatePresence(consumer) }) return false
        }
        return super.calculatePresence(consumer)
    }

    override fun calculateExecutionTimeValue(): ValueSupplier.ExecutionTimeValue<out R> {
        openScope().use {
            if (providers.any { it.isChangingValue }) {
                return ValueSupplier.ExecutionTimeValue.changingValue(this)
            }
        }
        return super.calculateExecutionTimeValue()
    }

    override fun calculateOwnValue(consumer: ValueSupplier.ValueConsumer): ValueSupplier.Value<out R> {
        openScope().use {
            val values = providers.map { provider ->
                provider.calculateValue(consumer).let { value ->
                    if (value.isMissing) return value.asType() else value
                }
            }
            val combinedUnpackedValue = combiner.apply(values.map { it.withoutSideEffect })
            return ValueSupplier
                .Value
                .ofNullable(combinedUnpackedValue)
                .run {
                    var currentValue = this
                    for (value in values) {
                        currentValue = currentValue.withSideEffect(
                            ValueSupplier.SideEffect.fixedFrom(value)
                        )
                    }
                    currentValue
                }
        }
    }

    override fun getType(): Class<R> = type

    override fun getProducer(): ValueSupplier.ValueProducer {
        openScope().use {
            return CombinedProducer(providers.map { it.producer })
        }
    }

    companion object {
        private val ProviderInternal<*>.isChangingValue: Boolean
            get() = calculateExecutionTimeValue().isChangingValue
    }
}

private class CombinedProducer(
    private val producers: List<ValueSupplier.ValueProducer>
) : ValueSupplier.ValueProducer {

    override fun isKnown(): Boolean = producers.any { it.isKnown }

    override fun visitProducerTasks(visitor: Action<in Task>) {
        for (producer in producers) producer.visitProducerTasks(visitor)
    }
}
