package oraserver.oraworldregen.manager

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.RegenHistory
import oraserver.oraworldregen.model.RegenStatus
import oraserver.oraworldregen.model.RegenTask
import oraserver.oraworldregen.model.WorldRegenConfig
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.WorldBorder
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.UUID

class RegenManager(private val plugin: OraWorldRegen) {

    /** worldName -> 実行中タスク */
    private val activeTasks    = HashMap<String, RegenTask>()
    /** worldName -> カウントダウン BukkitTask */
    private val countdownTasks = HashMap<String, BukkitTask>()
    /** 直列実行キュー（worldName） */
    private val regenQueue     = ArrayDeque<Pair<String, String>>()
    /** 現在キュー処理中かどうか */
    private var isProcessingQueue = false

    val tasks: Map<String, RegenTask> get() = activeTasks.toMap()

    fun isRegenerating(worldName: String) = activeTasks.containsKey(worldName)
    fun isAnyRegenerating()               = activeTasks.isNotEmpty()

    fun getQueue(): List<String> = regenQueue.map { it.first }

    // =========================================================================
    // 開始 / キュー登録
    // =========================================================================

    fun startRegen(worldName: String, triggeredBy: String = "schedule") {
        val config = plugin.configManager.worldConfigs[worldName] ?: run {
            plugin.logger.warning("startRegen: 設定なし: $worldName")
            return
        }
        if (!config.enabled) {
            plugin.logger.info("startRegen: 無効化されています: $worldName")
            return
        }
        if (activeTasks.containsKey(worldName)) {
            plugin.logger.warning("$worldName は既に再生成中です")
            return
        }
        if (regenQueue.any { it.first == worldName }) {
            plugin.logger.warning("$worldName は既にキューに入っています")
            return
        }

        if (isProcessingQueue || activeTasks.isNotEmpty()) {
            regenQueue.addLast(worldName to triggeredBy)
            broadcast("§e${worldName} §fをキューに追加しました。§7(キュー: ${regenQueue.size}件)")
            activeTasks[worldName] = RegenTask(worldName).also { it.status = RegenStatus.QUEUED }
            return
        }

        isProcessingQueue = true
        executeRegenPipeline(worldName, triggeredBy)
    }

    // =========================================================================
    // キャンセル
    // =========================================================================

    fun cancelRegen(worldName: String): Boolean {
        val task = activeTasks[worldName] ?: return false

        if (task.status == RegenStatus.QUEUED) {
            regenQueue.removeAll { it.first == worldName }
            activeTasks.remove(worldName)
            broadcast("§e${worldName} §fのキュー待機をキャンセルしました。")
            return true
        }

        if (task.status != RegenStatus.COUNTDOWN) return false

        countdownTasks.remove(worldName)?.cancel()
        activeTasks.remove(worldName)
        broadcast("§e${worldName} §fの再生成をキャンセルしました。")

        isProcessingQueue = false
        processNextQueue()
        return true
    }

    // =========================================================================
    // パイプライン
    // =========================================================================

    private fun executeRegenPipeline(worldName: String, triggeredBy: String) {
        val config = plugin.configManager.worldConfigs[worldName] ?: run {
            isProcessingQueue = false
            processNextQueue()
            return
        }

        val task = RegenTask(worldName)
        activeTasks[worldName] = task

        if (config.countdownSeconds > 0) {
            runCountdown(worldName, config, task, triggeredBy)
        } else {
            executeRegen(worldName, config, task, triggeredBy)
        }
    }

    // =========================================================================
    // カウントダウン
    // =========================================================================

    private fun runCountdown(
        worldName: String, config: WorldRegenConfig,
        task: RegenTask, triggeredBy: String
    ) {
        task.status = RegenStatus.COUNTDOWN
        val notifyAt = plugin.configManager.notifyAtSeconds
        var remaining = config.countdownSeconds

        val bt = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (remaining <= 0) {
                countdownTasks.remove(worldName)?.cancel()
                executeRegen(worldName, config, task, triggeredBy)
                return@Runnable
            }
            if (notifyAt.contains(remaining)) {
                broadcast("§e${worldName} §fの再生成まで §c§l${remaining}秒！")
                val stay = if (remaining <= 10) 25 else 15
                Bukkit.getOnlinePlayers().forEach { p ->
                    p.sendTitle("§c§l${remaining}", "§e${worldName} のワールドが再生成されます", 5, stay, 5)
                }
            }
            remaining--
        }, 0L, 20L)

        countdownTasks[worldName] = bt
    }

    // =========================================================================
    // 再生成本体
    // =========================================================================

    private fun executeRegen(
        worldName: String, config: WorldRegenConfig,
        task: RegenTask, triggeredBy: String
    ) {
        // ── 二重実行ガード ──────────────────────────────────────────────────
        // cancelRegen などで既に activeTasks から消えていたら何もしない
        if (!activeTasks.containsKey(worldName)) {
            plugin.logger.warning("[$worldName] executeRegen: タスクが存在しません。スキップします。")
            isProcessingQueue = false
            processNextQueue()
            return
        }

        val startTime = Instant.now()
        broadcast("§e${worldName} §fのワールド再生成を開始します...")
        Bukkit.getOnlinePlayers().forEach { p ->
            p.sendTitle("§6§l再生成開始！", "§e${worldName} を再生成しています...", 10, 40, 10)
        }

        plugin.whitelistManager.enableBlock()

        task.status = RegenStatus.TELEPORTING
        broadcast("§fプレイヤーを §e${config.fallbackWorld} §fへ転送しています...")
        teleportAll(worldName, config, task)

        // プレイヤー転送完了を待って次フェーズへ（2tick後）
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            doBackupPhase(worldName, config, task, triggeredBy, startTime)
        }, 2L)
    }

    // ── フェーズ分割: バックアップ ──────────────────────────────────────────

    private fun doBackupPhase(
        worldName: String, config: WorldRegenConfig,
        task: RegenTask, triggeredBy: String, startTime: Instant
    ) {
        if (config.backupEnabled) {
            task.status = RegenStatus.BACKING_UP
            broadcast("§eバックアップを作成しています...")

            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                val backupPath = plugin.backupManager.backup(config)

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (backupPath != null) {
                        broadcast("§aバックアップ完了: §7${File(backupPath).name}")
                    } else {
                        broadcast("§cバックアップに失敗しましたが、再生成を続行します。")
                    }
                    doUnloadAndDeletePhase(worldName, config, task, triggeredBy, startTime, backupPath)
                })
            })
        } else {
            // バックアップ不要の場合は直接次フェーズへ
            doUnloadAndDeletePhase(worldName, config, task, triggeredBy, startTime, null)
        }
    }

    // ── フェーズ分割: アンロード → 削除 ────────────────────────────────────

    private fun doUnloadAndDeletePhase(
        worldName: String, config: WorldRegenConfig,
        task: RegenTask, triggeredBy: String,
        startTime: Instant, backupPath: String?
    ) {
        // アンロード（メインスレッド）
        task.status = RegenStatus.UNLOADING
        plugin.logger.info("[$worldName] Multiverse アンロード中...")
        plugin.multiverseHook.unloadWorld(config.multiverseWorldName)

        // 削除（非同期）
        task.status = RegenStatus.DELETING
        broadcast("§cワールドデータを削除中...")

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val worldFolder = File(Bukkit.getWorldContainer(), config.multiverseWorldName)
            try {
                deleteDirectory(worldFolder.toPath())
            } catch (e: Exception) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    failRegen(worldName, task, triggeredBy, startTime, backupPath, "フォルダ削除失敗: ${e.message}")
                })
                return@Runnable
            }

            // 削除完了 → メインスレッドで再生成フェーズへ
            Bukkit.getScheduler().runTask(plugin, Runnable {
                doCreatePhase(worldName, config, task, triggeredBy, startTime, backupPath)
            })
        })
    }

    // ── フェーズ分割: ワールド再生成 ────────────────────────────────────────

    private fun doCreatePhase(
        worldName: String, config: WorldRegenConfig,
        task: RegenTask, triggeredBy: String,
        startTime: Instant, backupPath: String?
    ) {
        task.status = RegenStatus.CREATING
        broadcast("§aワールドを再生成中...")

        val ok = plugin.multiverseHook.importWorld(config)
        if (!ok) {
            failRegen(worldName, task, triggeredBy, startTime, backupPath, "Multiverse によるワールド作成失敗")
            return
        }

        // ワールドボーダー設定
        if (config.borderEnabled) {
            task.status = RegenStatus.SETTING_BORDER
            applyWorldBorder(config)
        }

        // ゲート生成（スポーン地点基準）
        plugin.gateManager.buildGatesForWorld(worldName)

        // 完了後コマンド
        if (config.postRegenCommands.isNotEmpty()) {
            task.status = RegenStatus.POST_COMMANDS
            executePostCommands(config)
        }

        // プレイヤー帰還
        if (config.returnPlayersAfterRegen && task.playerReturnLocations.isNotEmpty()) {
            task.status = RegenStatus.RETURNING
            returnPlayers(config, task)
        } else {
            finishRegen(worldName, task, triggeredBy, startTime, backupPath)
        }
    }

    // =========================================================================
    // ワールドボーダー設定
    // =========================================================================

    private fun applyWorldBorder(config: WorldRegenConfig) {
        val world = Bukkit.getWorld(config.multiverseWorldName) ?: run {
            plugin.logger.warning("[Border] ワールドが見つかりません: ${config.multiverseWorldName}")
            return
        }
        val border: WorldBorder = world.worldBorder
        border.center = world.getBlockAt(
            config.borderCenterX.toInt(), 64, config.borderCenterZ.toInt()
        ).location.also { it.x = config.borderCenterX; it.z = config.borderCenterZ }
        border.size            = config.borderSize
        border.damageAmount    = config.borderDamageAmount
        border.damageBuffer    = config.borderDamageBuffer
        border.warningDistance = config.borderWarningDistance
        border.warningTime     = config.borderWarningTime

        plugin.logger.info("[Border] ${config.multiverseWorldName} にボーダーを設定: ${config.borderSize} x ${config.borderSize}")
        broadcast("§aワールドボーダーを設定しました: §f${config.borderSize.toInt()}×${config.borderSize.toInt()}")
    }

    // =========================================================================
    // 完了後コマンド
    // =========================================================================

    private fun executePostCommands(config: WorldRegenConfig) {
        val server = plugin.server
        config.postRegenCommands.forEach { cmd ->
            val resolved = cmd
                .replace("{world}", config.multiverseWorldName)
                .replace("{mv_world}", config.multiverseWorldName)
            try {
                server.dispatchCommand(server.consoleSender, resolved)
                plugin.logger.info("[PostCmd] 実行: $resolved")
            } catch (e: Exception) {
                plugin.logger.warning("[PostCmd] 実行失敗 '$resolved': ${e.message}")
            }
        }
    }

    // =========================================================================
    // プレイヤー帰還
    // =========================================================================

    private fun returnPlayers(config: WorldRegenConfig, task: RegenTask) {
        val worldName  = config.worldName
        val delayTicks = config.returnDelay * 20L

        broadcast("§f${config.returnDelay}秒後にプレイヤーを元の場所へ戻します...")

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val regenWorld = Bukkit.getWorld(config.multiverseWorldName)
            if (regenWorld == null) {
                plugin.logger.warning("[Return] ワールドが見つかりません: ${config.multiverseWorldName}")
                finishRegen(worldName, task, "unknown", task.startTime, null)
                return@Runnable
            }

            var returnCount = 0
            task.playerReturnLocations.forEach { (uuid, oldLoc) ->
                val player = Bukkit.getPlayer(uuid) ?: return@forEach
                val targetLoc = if (oldLoc.world?.name == config.multiverseWorldName) {
                    regenWorld.spawnLocation
                } else {
                    oldLoc
                }
                player.teleport(targetLoc)
                player.sendMessage("${OraWorldRegen.PREFIX}§fワールド §e${config.multiverseWorldName} §fの再生成が完了しました。")
                returnCount++
            }
            task.playerReturnLocations.clear()

            if (returnCount > 0) broadcast("§a${returnCount}人のプレイヤーを元の場所へ戻しました。")
            finishRegen(worldName, task, "unknown", task.startTime, null)
        }, delayTicks)
    }

    // =========================================================================
    // 完了・失敗
    // =========================================================================

    private fun finishRegen(
        worldName: String, task: RegenTask,
        triggeredBy: String, startTime: Instant, backupPath: String?
    ) {
        task.status = RegenStatus.COMPLETE
        val endTime = Instant.now()
        val elapsed = endTime.epochSecond - startTime.epochSecond

        plugin.whitelistManager.disableBlock()
        activeTasks.remove(worldName)

        plugin.historyManager.add(
            RegenHistory(
                worldName       = worldName,
                startTime       = startTime,
                endTime         = endTime,
                durationSeconds = elapsed,
                success         = true,
                triggeredBy     = triggeredBy,
                backupFile      = backupPath?.let { File(it).name }
            )
        )

        broadcast("§a§l${worldName} §aの再生成が完了しました！ §7(${elapsed}秒)")
        Bukkit.getOnlinePlayers().forEach { p ->
            p.sendTitle("§a§l完了！", "§e${worldName} の再生成が終わりました", 10, 60, 20)
        }
        plugin.logger.info("[$worldName] 再生成完了（${elapsed}秒）")

        isProcessingQueue = false
        processNextQueue()
    }

    private fun failRegen(
        worldName: String, task: RegenTask,
        triggeredBy: String, startTime: Instant, backupPath: String?,
        reason: String
    ) {
        task.status     = RegenStatus.FAILED
        task.failReason = reason
        val endTime     = Instant.now()
        val elapsed     = endTime.epochSecond - startTime.epochSecond

        plugin.whitelistManager.disableBlock()
        activeTasks.remove(worldName)

        plugin.historyManager.add(
            RegenHistory(
                worldName       = worldName,
                startTime       = startTime,
                endTime         = endTime,
                durationSeconds = elapsed,
                success         = false,
                failReason      = reason,
                triggeredBy     = triggeredBy,
                backupFile      = backupPath?.let { File(it).name }
            )
        )

        broadcast("§c${worldName} の再生成に失敗しました: §7${reason}")
        plugin.logger.severe("[$worldName] 再生成失敗: $reason")

        isProcessingQueue = false
        processNextQueue()
    }

    // =========================================================================
    // キュー処理
    // =========================================================================

    private fun processNextQueue() {
        if (isProcessingQueue || regenQueue.isEmpty()) return

        val (nextWorld, nextTrigger) = regenQueue.removeFirst()
        activeTasks.remove(nextWorld)

        isProcessingQueue = true
        broadcast("§f次のキュー: §e${nextWorld} §fの再生成を開始します...")
        executeRegenPipeline(nextWorld, nextTrigger)
    }

    // =========================================================================
    // ヘルパー
    // =========================================================================

    private fun teleportAll(worldName: String, config: WorldRegenConfig, task: RegenTask) {
        val target   = plugin.server.getWorld(worldName)
        val fallback = plugin.server.getWorld(config.fallbackWorld)
            ?: plugin.server.worlds.first()
        val spawn    = fallback.spawnLocation

        Bukkit.getOnlinePlayers()
            .filter { target == null || it.world == target }
            .forEach { p ->
                if (config.returnPlayersAfterRegen) {
                    task.playerReturnLocations[p.uniqueId] = p.location.clone()
                }
                p.teleport(spawn)
            }
    }

    private fun broadcast(message: String) {
        Bukkit.broadcastMessage("${OraWorldRegen.PREFIX}${message}")
        plugin.logger.info(message.replace("§[0-9a-fk-or]".toRegex(), ""))
    }

    private fun deleteDirectory(path: Path) {
        if (!Files.exists(path)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) throw exc
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    fun shutdown() {
        countdownTasks.values.forEach { it.cancel() }
        countdownTasks.clear()
        activeTasks.clear()
        regenQueue.clear()
        isProcessingQueue = false
    }
}
