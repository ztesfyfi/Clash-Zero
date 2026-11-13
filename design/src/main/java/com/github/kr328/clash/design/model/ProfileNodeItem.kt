package com.github.kr328.clash.design.model

data class ProfileNodeItem(
    val key: String,
    val originalName: String,
    val displayName: String,
    val type: String,
    val groupId: String? = null,
    val groupName: String? = null,
)

data class ProfileNodeGroupItem(
    val id: String,
    val name: String,
    val isPreset: Boolean,
    val enabled: Boolean,
    val sortOrder: Int,
)
