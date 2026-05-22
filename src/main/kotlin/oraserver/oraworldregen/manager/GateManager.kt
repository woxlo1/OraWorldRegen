package oraserver.oraworldregen.manager

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.GateConfig
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * 再生成後のゲート自動生成マネージャー。
 *
 * ## 処理の流れ
 * 1. ワールドに「フレームブロック + ポータル充填ブロック」を配置
 * 2. Multiverse-Portals の portals.yml にエントリを書き込む
 * 3. `/mvp reload` を実行して反映させる
 *
 * ## portals.yml の形式（Multiverse-Portals 4.x）
 * ```yaml
 * portals:
 *   myGate:
 *     world: resources
 *     location: "10,64,10:12,67,10"    # 内側範囲の対角2点
 *     destination: "w:world"
 *     owner: "OraWorldRegen"
 *     entryfee:
 *       currency: -1
 *       amount: 0.0
 *     safeteleport: true
 *     teleportnonplayers: false
 * ```
 *
 * ## ブロックについて
 * Multiverse-Portalsのゲートは「どんなブロックでもフレームになれる」仕様。
 * 充填ブロック（portalBlock）は WATER, LAVA, NETHER_PORTAL, AIR などを config で指定可能。
 */
class GateManager(private val plugin: OraWorldRegen) {

    /**
     * 指定ワールドに登録されているゲートをすべて生成する。
     * RegenManagerの完了フェーズ（メインスレッド）から呼ぶこと。
     */
    fun buildGatesForWorld(worldName: String) {
        val gates = plugin.configManager.gateConfigs[worldName]
        if (gates.isNullOrEmpty()) return

        val enabledGates = gates.filter { it.enabled }
        if (enabledGates.isEmpty()) return

        plugin.logger.info("[Gate] ${worldName} のゲートを ${enabledGates.size} 件生成します...")

        enabledGates.forEach { gate ->
            try {
                placeBlocks(gate)
                writeToPortalsYml(gate)
                plugin.logger.info("[Gate] ゲート '${gate.name}' 生成完了 (frame=${gate.frameBlock}, portal=${gate.portalBlock}, dest=${gate.destination})")
            } catch (e: Exception) {
                plugin.logger.severe("[Gate] ゲート '${gate.name}' の生成エラー: ${e.message}")
                e.printStackTrace()
            }
        }

        // Multiverse-Portals を mvp reload で反映
        reloadMultiversePortals()
    }

    // =========================================================================
    // ブロック配置
    // =========================================================================

    /**
     * ゲートの設定に従いワールドにブロックを配置する。
     *
     * 配置ルール:
     *   - 内側範囲の対角2点 (x1,y1,z1)〜(x2,y2,z2) を計算
     *   - 範囲を1ブロック外側に拡張した部分 = フレーム
     *   - 内側 = ポータル充填ブロック
     */
    private fun placeBlocks(gate: GateConfig) {
        val world = Bukkit.getWorld(gate.worldName)
            ?: throw IllegalStateException("ワールドが見つかりません: ${gate.worldName}")

        val minChunkX = minOf(gate.x1, gate.x2) shr 4
        val maxChunkX = maxOf(gate.x1, gate.x2) shr 4
        val minChunkZ = minOf(gate.z1, gate.z2) shr 4
        val maxChunkZ = maxOf(gate.z1, gate.z2) shr 4

        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                val chunk = world.getChunkAt(cx, cz)

                if (!chunk.isLoaded) {
                    chunk.load(true)
                }
            }
        }

        val frameMat = parseMaterial(gate.frameBlock, Material.OBSIDIAN)
        val portalMat = parseMaterial(gate.portalBlock, Material.WATER)

        val minX = minOf(gate.x1, gate.x2)
        val minY = minOf(gate.y1, gate.y2)
        val minZ = minOf(gate.z1, gate.z2)
        val maxX = maxOf(gate.x1, gate.x2)
        val maxY = maxOf(gate.y1, gate.y2)
        val maxZ = maxOf(gate.z1, gate.z2)

        // ゲートが平面かどうか判定（X平面 or Z平面）
        val flatX = (minX == maxX) // Z方向に広がる（東西向き）
        val flatZ = (minZ == maxZ) // X方向に広がる（南北向き）

        // 内側をポータルブロックで埋める
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = world.getBlockAt(x, y, z)
                    if (portalMat == Material.AIR) {
                        block.type = Material.AIR
                    } else {
                        block.type = portalMat
                    }
                }
            }
        }

        // フレームを外側1ブロックに配置
        // XZ両方で広がるケース（3Dポータル）は全外周にフレームを置く
        // 平面ポータル（flatX or flatZ）は2D的に外枠を置く
        if (flatZ) {
            // Z固定 → X方向に広がるポータル（南北向きフレーム）
            val z = minZ
            for (x in (minX - 1)..(maxX + 1)) {
                world.getBlockAt(x, minY - 1, z).type = frameMat
                world.getBlockAt(x, maxY + 1, z).type = frameMat
            }
            for (y in minY..maxY) {
                world.getBlockAt(minX - 1, y, z).type = frameMat
                world.getBlockAt(maxX + 1, y, z).type = frameMat
            }
        } else if (flatX) {
            // X固定 → Z方向に広がるポータル（東西向きフレーム）
            val x = minX
            for (z in (minZ - 1)..(maxZ + 1)) {
                world.getBlockAt(x, minY - 1, z).type = frameMat
                world.getBlockAt(x, maxY + 1, z).type = frameMat
            }
            for (y in minY..maxY) {
                world.getBlockAt(x, y, minZ - 1).type = frameMat
                world.getBlockAt(x, y, maxZ + 1).type = frameMat
            }
        } else {
            // 3D範囲のゲート（水平ポータルなど）: 上下と四辺にフレーム
            for (x in (minX - 1)..(maxX + 1)) {
                for (z in (minZ - 1)..(maxZ + 1)) {
                    world.getBlockAt(x, minY - 1, z).type = frameMat
                    world.getBlockAt(x, maxY + 1, z).type = frameMat
                }
            }
            for (x in (minX - 1)..(maxX + 1)) {
                for (y in (minY - 1)..(maxY + 1)) {
                    world.getBlockAt(x, y, minZ - 1).type = frameMat
                    world.getBlockAt(x, y, maxZ + 1).type = frameMat
                }
            }
            for (z in (minZ - 1)..(maxZ + 1)) {
                for (y in (minY - 1)..(maxY + 1)) {
                    world.getBlockAt(minX - 1, y, z).type = frameMat
                    world.getBlockAt(maxX + 1, y, z).type = frameMat
                }
            }
        }
    }

    private fun parseMaterial(name: String, fallback: Material): Material {
        return try {
            Material.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("[Gate] 不明なMaterial '${name}', フォールバック: ${fallback.name}")
            fallback
        }
    }

    // =========================================================================
    // portals.yml への書き込み
    // =========================================================================

    /**
     * Multiverse-Portals の portals.yml にゲートエントリを書き込む。
     *
     * portals.yml の場所: plugins/Multiverse-Portals/portals.yml
     *
     * location形式: "x1,y1,z1:x2,y2,z2"
     * （Multiverse-Portalsはwandで選択した対角2点の内側を範囲として記録する）
     */
    private fun writeToPortalsYml(gate: GateConfig) {
        val portalsFile = File(plugin.server.pluginsFolder, "Multiverse-Portals/portals.yml")

        // ファイルが存在しなければ空で作成
        if (!portalsFile.exists()) {
            portalsFile.parentFile?.mkdirs()
            portalsFile.createNewFile()
            plugin.logger.info("[Gate] portals.yml を新規作成しました: ${portalsFile.path}")
        }

        val yaml = YamlConfiguration.loadConfiguration(portalsFile)

        val key = "portals.${gate.name}"

        // location = "x1,y1,z1:x2,y2,z2"
        val locationStr = "${gate.x1},${gate.y1},${gate.z1}:${gate.x2},${gate.y2},${gate.z2}"

        yaml.set("$key.world",                    gate.worldName)
        yaml.set("$key.location",                 locationStr)
        yaml.set("$key.destination",              gate.destination)
        yaml.set("$key.owner",                    gate.owner)
        yaml.set("$key.entryfee.currency",        -1)
        yaml.set("$key.entryfee.amount",          0.0)
        yaml.set("$key.safeteleport",             true)
        yaml.set("$key.teleportnonplayers",       false)

        yaml.save(portalsFile)
        plugin.logger.info("[Gate] portals.yml にゲート '${gate.name}' を書き込みました")
    }

    // =========================================================================
    // Multiverse-Portals リロード
    // =========================================================================

    private fun reloadMultiversePortals() {
        // 少し遅らせてから実行（ワールド生成が完全に終わるのを待つ）
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val mvPortals = plugin.server.pluginManager.getPlugin("Multiverse-Portals")
            if (mvPortals != null && mvPortals.isEnabled) {
                try {
                    plugin.server.dispatchCommand(plugin.server.consoleSender, "mvp reload")
                    plugin.logger.info("[Gate] Multiverse-Portals をリロードしました (/mvp reload)")
                } catch (e: Exception) {
                    plugin.logger.warning("[Gate] /mvp reload 失敗: ${e.message}")
                }
            } else {
                plugin.logger.warning(
                    "[Gate] Multiverse-Portals が見つかりません。portals.yml は書き込み済みですが、" +
                    "手動で /mvp reload を実行するか、サーバーを再起動してください。"
                )
            }
        }, 40L) // 2秒後（ワールド生成完了を確実に待つ）
    }

    // =========================================================================
    // ゲート削除（必要な場合）
    // =========================================================================

    /**
     * portals.yml からゲートエントリを削除する。
     * ワールド再生成では不要だが、コマンドからの手動削除などに使う。
     */
    fun removeGateFromPortalsYml(gateName: String) {
        val portalsFile = File(plugin.server.pluginsFolder, "Multiverse-Portals/portals.yml")
        if (!portalsFile.exists()) return

        val yaml = YamlConfiguration.loadConfiguration(portalsFile)
        yaml.set("portals.$gateName", null)
        yaml.save(portalsFile)
        plugin.logger.info("[Gate] portals.yml からゲート '${gateName}' を削除しました")
    }
}
