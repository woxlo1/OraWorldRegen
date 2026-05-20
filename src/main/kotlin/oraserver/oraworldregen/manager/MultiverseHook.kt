package oraserver.oraworldregen.manager

import com.onarandombox.MultiverseCore.MultiverseCore
import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.WorldRegenConfig
import org.bukkit.WorldType

class MultiverseHook(private val plugin: OraWorldRegen) {

    private val mv: MultiverseCore? by lazy {
        val p = plugin.server.pluginManager.getPlugin("Multiverse-Core")
        if (p is MultiverseCore && p.isEnabled) p else null
    }

    val isAvailable get() = mv != null

    fun unloadWorld(worldName: String): Boolean {
        val world = plugin.server.getWorld(worldName)
        if (world != null) plugin.server.unloadWorld(world, true)
        return mv?.mvWorldManager?.removeWorldFromConfig(worldName) ?: false
    }

    fun importWorld(config: WorldRegenConfig): Boolean {
        val gen  = config.generator.ifBlank { null }
        val seed = config.seed.ifBlank { null }
        return mv?.mvWorldManager?.addWorld(
            config.multiverseWorldName,
            config.environment,
            seed,
            WorldType.valueOf(config.worldType),
            true,
            gen
        ) ?: false
    }

    fun isRegistered(worldName: String) = mv?.mvWorldManager?.isMVWorld(worldName) ?: false
}
