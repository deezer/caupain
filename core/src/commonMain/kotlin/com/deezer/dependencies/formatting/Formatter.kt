package com.deezer.dependencies.formatting

import com.deezer.dependencies.model.UpdateInfo

public fun interface Formatter {
    public suspend fun format(updates: Map<UpdateInfo.Type, List<UpdateInfo>>)
}