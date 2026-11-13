package com.github.kr328.clash.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ProfileProxy(
    val key: String,
    val name: String,
    val type: String,
)

@Serializable
data class ProfileProxyResult(
    val nodes: List<ProfileProxy> = emptyList(),
    val error: String? = null,
)
