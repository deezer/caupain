package com.deezer.caupain.model.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class Tree(
    val sha: String,
    @SerialName("tree") val content: List<TreeContent>
)

@Serializable
internal data class TreeContent(
    val path: String,
    val type: String,
)
