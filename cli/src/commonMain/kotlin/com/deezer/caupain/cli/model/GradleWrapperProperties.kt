package com.deezer.caupain.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class GradleWrapperProperties(val distributionUrl: String? = null)