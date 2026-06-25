package com.babymomo.app.core.llm.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val parameters: JsonObject
)
