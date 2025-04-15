package com.deezer.dependencies.formatting

import com.deezer.dependencies.model.UpdateInfo

public fun interface Formatter {
    public fun format(updates: List<UpdateInfo>)
}