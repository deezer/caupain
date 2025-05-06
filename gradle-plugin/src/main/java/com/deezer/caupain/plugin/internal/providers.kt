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
import org.gradle.api.internal.provider.Providers
import org.gradle.api.internal.provider.ValueSupplier
import org.gradle.api.internal.provider.ValueSupplier.SideEffect
import org.gradle.api.internal.provider.ValueSupplier.Value
import org.gradle.api.internal.provider.ValueSupplier.ValueProducer
import org.gradle.api.provider.Provider

private class CombiningProvider<T, R>(
    providers: List<Provider<out T>>,
    private val transform: (List<T>) -> R
) : AbstractMinimalProvider<R>() {
    private val providers = providers.map { Providers.internal(it) }

    override fun toStringNoReentrance(): String {
        return providers.joinToString(prefix = "combine(", postfix = ")")
    }

    override fun calculatePresence(consumer: ValueSupplier.ValueConsumer): Boolean {
        openScope().use {
            if (providers.any { !it.calculatePresence(consumer) }) return false
        }
        // Purposefully only calculate full value if all are present, to save time
        return super.calculatePresence(consumer)
    }

    override fun calculateExecutionTimeValue(): ValueSupplier.ExecutionTimeValue<out R> {
        openScope().use {
            if (providers.any { it.calculateExecutionTimeValue().isChangingValue }) {
                return ValueSupplier.ExecutionTimeValue.changingValue(this)
            }
        }
        return super.calculateExecutionTimeValue()
    }

    override fun calculateOwnValue(consumer: ValueSupplier.ValueConsumer): Value<out R> {
        return openScope().use {
            val values = mutableListOf<Value<out T>>()
            for (provider in providers) {
                val value = provider.calculateValue(consumer)
                if (value.isMissing) return value.asType() else values.add(value)
            }
            val combinedUnpackedValue = transform(values.map { it.withoutSideEffect })
            Value
                .ofNullable(combinedUnpackedValue)
                .run {
                    var result = this
                    for (value in values) {
                        result = result.withSideEffect(SideEffect.fixedFrom(value))
                    }
                    result
                }
        }
    }

    override fun getProducer(): ValueProducer {
        return openScope().use {
            CombiningProducer(providers.map { it.producer })
        }
    }

    override fun getType(): Class<R>? = null
}

private class CombiningProducer(private val valueProducers: List<ValueProducer>) :
    ValueProducer {

    override fun isKnown(): Boolean = valueProducers.any { it.isKnown }

    override fun visitProducerTasks(visitor: Action<in Task>) {
        valueProducers.forEach { it.visitProducerTasks(visitor) }
    }
}

internal fun <T, R> combine(
    vararg providers: Provider<out T>,
    transform: (List<T>) -> R
): Provider<R> {
    return CombiningProvider(providers.asList(), transform)
}