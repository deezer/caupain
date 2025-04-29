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