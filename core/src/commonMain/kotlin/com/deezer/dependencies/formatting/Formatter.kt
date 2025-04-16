package com.deezer.dependencies.formatting

import com.deezer.dependencies.model.UpdateInfo

public fun interface Formatter {
    public suspend fun format(updates: List<UpdateInfo>)
}