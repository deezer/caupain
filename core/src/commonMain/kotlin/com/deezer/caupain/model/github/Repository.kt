package com.deezer.caupain.model.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class Repository(
    val id: String,
    @SerialName("default_branch") val defaultBranch: String? = null,
)
