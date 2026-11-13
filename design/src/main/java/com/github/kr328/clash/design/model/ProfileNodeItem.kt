package com.github.kr328.clash.design.model

data class ProfileNodeItem(
    val key: String,
    val originalName: String,
    val displayName: String,
    val type: String,
    val groupId: String? = null,
    val groupName: String? = null,
    val delayText: String = "未测试",
    val delayState: String = "UNTESTED",
)

data class ProfileNodeGroupItem(
    val id: String,
    val name: String,
    val isPreset: Boolean,
    val enabled: Boolean,
    val sortOrder: Int,
)

data class ProfileNodeSettingsItem(
    val goodThresholdMs: Int? = null,
    val slowThresholdMs: Int? = null,
)
