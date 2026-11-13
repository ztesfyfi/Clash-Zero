package com.github.kr328.clash.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ProxyTestResult(
    val delay: Int = 0,
    val error: String? = null,
)
