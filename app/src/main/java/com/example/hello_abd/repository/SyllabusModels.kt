package com.example.hello_abd.repository

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import java.util.UUID

@Keep
@Serializable
data class SyllabusTopic(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    var isCompleted: Boolean = false
)

@Keep
@Serializable
data class SyllabusUnit(
    val id: String = UUID.randomUUID().toString(),
    val unit_title: String = "",
    val topics: List<SyllabusTopic> = emptyList()
)

@Keep
@Serializable
data class SyllabusData(
    val id: String = UUID.randomUUID().toString(),
    val subject: String = "",
    val units: List<SyllabusUnit> = emptyList()
)

// Intermediate models for AI parsing
@Keep
@Serializable
data class RawSyllabusUnit(
    val unit_title: String = "",
    val topics: List<String> = emptyList()
)

@Keep
@Serializable
data class RawSyllabusData(
    val subject: String = "",
    val units: List<RawSyllabusUnit> = emptyList()
)
