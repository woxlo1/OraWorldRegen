package oraserver.oraworldregen.manager

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.GateConfig
import oraserver.oraworldregen.model.GateFacing
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * 再生成後のゲート自動生成マネージャー。
 *
 * ゲートはワールドのスポーン地点 + offset を原点として、
 * size（width × height）と facing から座標を自動計算して配置します。
 *
 * Multiverse-Portals が存在しない環境向けに /mv reload でリロードします。
 */
class GateManager(private val plugin: OraWorldRegen) {

    fun buildGatesForWorld(worldName: String) {
        val gates = plugin.configManager.gateConfigs[worldName]
        if (gates.isNullOrEmpty()) return

        val enabledGates = gates.filter { it.enabled }
        if (enabledGates.isEmpty()) return

        val world = Bukkit.getWorld(worldName) ?: run {
            plugin.logger.warning("[Gate] ワールドが見つかりません（ゲート生成スキップ）: $worldName")
            return
        }

        val spawn = world.spawnLocation
        plugin.logger.info(
            "[Gate] ${worldName} スポーン: ${spawn.blockX}, ${spawn.blockY}, ${spawn.blockZ}"
        )
        plugin.logger.info("[Gate] ${worldName} のゲートを ${enabledGates.size} 件生成します...")

        enabledGates.forEach { gate ->
            try {
                val origin = calcOrigin(spawn, gate)
                placeBlocks(gate, world, origin)
                writeToPortalsYml(gate, world, origin)
                plugin.logger.info(
                    "[Gate] '${gate.name}' 生成完了 " +
                    "(facing=${gate.facing}, size=${gate.width}x${gate.height}, " +
                    "origin=${origin.blockX},${origin.blockY},${origin.blockZ}, " +
                    "dest=${gate.destination})"
                )
            } catch (e: Exception) {
                plugin.logger.severe("[Gate] '${gate.name}' の生成エラー: ${e.message}")
                e.printStackTrace()
            }
        }

        reloadPortals()
    }

    // =========================================================================
    // 座標計算
    // =========================================================================

    private fun calcOrigin(spawn: Location, gate: GateConfig): Location {
        return Location(
            spawn.world,
            (spawn.blockX + gate.offsetX).toDouble(),
            (spawn.blockY + gate.offsetY).toDouble(),
            (spawn.blockZ + gate.offsetZ).toDouble()
        )
    }

    /**
     * facing と size から内側範囲の2点（min/max）を返す。
     * - NORTH/SOUTH: X方向に width-1 だけ広がる（Z固定）
     * - EAST/WEST  : Z方向に width-1 だけ広がる（X固定）
     * - 高さは常にY方向
     */
    private fun calcInnerBounds(origin: Location, gate: GateConfig): Pair<Location, Location> {
        val ox = origin.blockX; val oy = origin.blockY; val oz = origin.blockZ
        val w  = gate.width - 1
        val h  = gate.height - 1

        return if (gate.facing.expandsAlongX) {
            Location(origin.world, ox.toDouble(), oy.toDouble(), oz.toDouble()) to
            Location(origin.world, (ox + w).toDouble(), (oy + h).toDouble(), oz.toDouble())
        } else {
            Location(origin.world, ox.toDouble(), oy.toDouble(), oz.toDouble()) to
            Location(origin.world, ox.toDouble(), (oy + h).toDouble(), (oz + w).toDouble())
        }
    }

    // =========================================================================
    // ブロック配置
    // =========================================================================

    private fun placeBlocks(gate: GateConfig, world: World, origin: Location) {
        val frameMat  = parseMaterial(gate.frameBlock,  Material.OBSIDIAN)
        val portalMat = parseMaterial(gate.portalBlock, Material.WATER)

        val (min, max) = calcInnerBounds(origin, gate)
        val minX = min.blockX; val minY = min.blockY; val minZ = min.blockZ
        val maxX = max.blockX; val maxY = max.blockY; val maxZ = max.blockZ

        // 内側をポータルブロックで埋める
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    world.getBlockAt(x, y, z).type = portalMat
                }
            }
        }

        // フレーム（外側1ブロック）
        if (gate.facing.expandsAlongX) {
            // Z固定 → X方向に広がるゲート
            val z = minZ
            for (x in (minX - 1)..(maxX + 1)) {
                world.getBlockAt(x, minY - 1, z).type = frameMat
                world.getBlockAt(x, maxY + 1, z).type = frameMat
            }
            for (y in minY..maxY) {
                world.getBlockAt(minX - 1, y, z).type = frameMat
                world.getBlockAt(maxX + 1, y, z).type = frameMat
            }
        } else {
            // X固定 → Z方向に広がるゲート
            val x = minX
            for (z in (minZ - 1)..(maxZ + 1)) {
                world.getBlockAt(x, minY - 1, z).type = frameMat
                world.getBlockAt(x, maxY + 1, z).type = frameMat
            }
            for (y in minY..maxY) {
                world.getBlockAt(x, y, minZ - 1).type = frameMat
                world.getBlockAt(x, y, maxZ + 1).type = frameMat
            }
        }

        plugin.logger.info(
            "[Gate] '${gate.name}' ブロック配置: " +
            "inner=($minX,$minY,$minZ)-($maxX,$maxY,$maxZ), facing=${gate.facing}"
        )
    }

    private fun parseMaterial(name: String, fallback: Material): Material =
        runCatching { Material.valueOf(name.uppercase()) }.getOrElse {
            plugin.logger.warning("[Gate] 不明なMaterial '${name}', フォールバック: ${fallback.name}")
            fallback
        }

    // =========================================================================
    // portals.yml への書き込み
    // =========================================================================

    private fun writeToPortalsYml(gate: GateConfig, world: World, origin: Location) {
        val portalsFile = File(plugin.server.pluginsFolder, "Multiverse-Portals/portals.yml")

        if (!portalsFile.exists()) {
            portalsFile.parentFile?.mkdirs()
            portalsFile.createNewFile()
            plugin.logger.info("[Gate] portals.yml を新規作成: ${portalsFile.path}")
        }

        val yaml = YamlConfiguration.loadConfiguration(portalsFile)
        val key  = "portals.${gate.name}"

        val (min, max) = calcInnerBounds(origin, gate)
        val locationStr = "${min.blockX},${min.blockY},${min.blockZ}:" +
                          "${max.blockX},${max.blockY},${max.blockZ}"

        yaml.set("$key.world",              gate.worldName)
        yaml.set("$key.location",           locationStr)
        yaml.set("$key.destination",        gate.destination)
        yaml.set("$key.owner",              gate.owner)
        yaml.set("$key.entryfee.currency",  -1)
        yaml.set("$key.entryfee.amount",    0.0)
        yaml.set("$key.safeteleport",       true)
        yaml.set("$key.teleportnonplayers", false)

        yaml.save(portalsFile)
        plugin.logger.info("[Gate] portals.yml に '${gate.name}' を書き込み (location: $locationStr)")
    }

    // =========================================================================
    // リロード（/mv reload）
    // =========================================================================

    private fun reloadPortals() {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            try {
                plugin.server.dispatchCommand(plugin.server.consoleSender, "mv reload")
                plugin.logger.info("[Gate] Multiverse をリロードしました (/mv reload)")
            } catch (e: Exception) {
                plugin.logger.warning("[Gate] /mv reload 失敗: ${e.message}")
            }
        }, 40L)
    }

    // =========================================================================
    // ゲート削除
    // =========================================================================

    fun removeGateFromPortalsYml(gateName: String) {
        val portalsFile = File(plugin.server.pluginsFolder, "Multiverse-Portals/portals.yml")
        if (!portalsFile.exists()) return
        val yaml = YamlConfiguration.loadConfiguration(portalsFile)
        yaml.set("portals.$gateName", null)
        yaml.save(portalsFile)
        plugin.logger.info("[Gate] portals.yml から '${gateName}' を削除")
    }
}
