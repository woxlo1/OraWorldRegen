package oraserver.oraworldregen.model

import org.bukkit.World

data class WorldRegenConfig(
    val worldName: String,
    val multiverseWorldName: String,
    val environment: World.Environment,
    val worldType: String,
    val seed: String,
    val generator: String,
    val cronSchedules: List<String>,
    val countdownSeconds: Int,
    val fallbackWorld: String,
    var enabled: Boolean
)
