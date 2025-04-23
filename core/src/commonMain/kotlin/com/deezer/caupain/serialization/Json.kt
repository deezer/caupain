package com.deezer.caupain.serialization

import kotlinx.serialization.json.Json

internal val DefaultJson: Json =
    Json {
        encodeDefaults = true
        isLenient = true
        allowSpecialFloatingPointValues = true
        allowStructuredMapKeys = true
        prettyPrint = false
        useArrayPolymorphism = false
        ignoreUnknownKeys = true
    }