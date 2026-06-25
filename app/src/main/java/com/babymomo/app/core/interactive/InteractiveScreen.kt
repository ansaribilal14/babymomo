package com.babymomo.app.core.interactive

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

@Serializable
data class ScreenDescriptor(
    val mode: String = "inline",  // inline | fullscreen
    val title: String = "",
    val widgets: List<WidgetDescriptor> = emptyList(),
    val actions: Map<String, String> = emptyMap()
)

@Serializable
sealed class WidgetDescriptor {
    @Serializable
    data class BabyText(
        val text: String,
        val style: String = "body"  // title | body | caption
    ) : WidgetDescriptor()

    @Serializable
    data class BabyButton(
        val label: String,
        val actionId: String
    ) : WidgetDescriptor()

    @Serializable
    data class BabyList(
        val items: List<BabyListItem>
    ) : WidgetDescriptor()

    @Serializable
    data class BabyListItem(
        val text: String,
        val actionId: String? = null
    )

    @Serializable
    data class BabyInput(
        val hint: String,
        val inputId: String
    ) : WidgetDescriptor()

    @Serializable
    data class BabyCard(
        val title: String,
        val body: String,
        val children: List<WidgetDescriptor> = emptyList()
    ) : WidgetDescriptor()

    @Serializable
    data class BabyGrid(
        val columns: Int = 2,
        val children: List<WidgetDescriptor> = emptyList()
    ) : WidgetDescriptor()

    @Serializable
    data class BabyProgress(
        val value: Int,
        val max: Int,
        val label: String = ""
    ) : WidgetDescriptor()

    @Serializable
    data object BabyDivider : WidgetDescriptor()
}

class InteractiveScreenParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(jsonString: String): ScreenDescriptor? {
        return try {
            val cleaned = jsonString.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            json.decodeFromString<ScreenDescriptor>(cleaned)
        } catch (_: Exception) {
            null
        }
    }
}
