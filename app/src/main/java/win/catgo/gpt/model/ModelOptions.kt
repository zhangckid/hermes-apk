package win.catgo.gpt.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelOptions(
    val providers: List<ModelProvider> = emptyList(),
    val model: String = "",
    val provider: String = "",
)

@Serializable
data class ModelProvider(
    val slug: String,
    val name: String = "",
    val models: List<String> = emptyList(),
    val authenticated: Boolean = false,
)

object ReasoningOptions {
    val values = listOf("off", "minimal", "low", "medium", "high", "xhigh", "max", "ultra")
}
