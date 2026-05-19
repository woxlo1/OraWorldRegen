package oraserver.oraworldregen.manager

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.RegenStatus
import oraserver.oraworldregen.model.RegenTask
import oraserver.oraworldregen.model.WorldRegenConfig
import oraserver.orapluginapi.scheduler.OraScheduler
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class RegenManager(private val plugin: OraWorldRegen) {

    // worldName -> 実行中タスク
    private val activeTasks     = HashMap<String, RegenTask>()
    // worldName -> カウントダウン BukkitTask
    private val countdownTasks  = HashMap<String, BukkitTask>()

    val tasks: Map<String, RegenTask> get() = activeTasks.toMap()

    fun isRegenerating(worldName: String) = activeTasks.containsKey(worldName)
    fun isAnyRegenerating()               = activeTasks.isNotEmpty()

    // ── 開始 ─────────────────────────────────────────

    fun startRegen(worldName: String) {
        val config = plugin.configManager.worldConfigs[worldName] ?: run {
            plugin.logger.warning("startRegen: 設定なし: $worldName")
            return
        }
        if (activeTasks.containsKey(worldName)) {
            plugin.logger.warning("$worldName は既に再生成中です")
            return
        }

        val task = RegenTask(worldName)
        activeTasks[worldName] = task

        if (config.countdownSeconds > 0) {
            runCountdown(worldName, config, task)
        } else {
            executeRegen(worldName, config, task)
        }
    }

    // ── キャンセル ────────────────────────────────────

    fun cancelRegen(worldName: String): Boolean {
        val task = activeTasks[worldName] ?: return false
        if (task.status != RegenStatus.COUNTDOWN) return false

        countdownTasks.remove(worldName)?.cancel()
        activeTasks.remove(worldName)
        broadcast("§e${worldName} §fの再生成をキャンセルしました。")
        return true
    }

    // ── カウントダウン ────────────────────────────────

    private fun runCountdown(worldName: String, config: WorldRegenConfig, task: RegenTask) {
        task.status = RegenStatus.COUNTDOWN
        val notifyAt = plugin.configManager.notifyAtSeconds
        var remaining = config.countdownSeconds

        val bt = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (remaining <= 0) {
                countdownTasks.remove(worldName)?.cancel()
                executeRegen(worldName, config, task)
                return@Runnable
            }
            if (notifyAt.contains(remaining)) {
                // チャット通知
                broadcast("§e${worldName} §fの再生成まで §c§l${remaining}秒！")

                // タイトル（残り10秒以下は大きく）
                val stay = if (remaining <= 10) 25 else 15
                Bukkit.getOnlinePlayers().forEach { p ->
                    p.sendTitle("§c§l${remaining}", "§e${worldName} のワールドが再生成されます", 5, stay, 5)
                }
            }
            remaining--
        }, 0L, 20L)

        countdownTasks[worldName] = bt
    }

    // ── 再生成本体 ────────────────────────────────────

    private fun executeRegen(worldName: String, config: WorldRegenConfig, task: RegenTask) {
        broadcast("§e${worldName} §fのワールド再生成を開始します...")
        Bukkit.getOnlinePlayers().forEach { p ->
            p.sendTitle("§6§l再生成開始！", "§e${worldName} を再生成しています...", 10, 40, 10)
        }

        // Step1: ホワイトリスト有効化
        plugin.whitelistManager.enableBlock()

        // Step2: プレイヤー転送
        task.status = RegenStatus.TELEPORTING
        broadcast("§fプレイヤーを §e${config.fallbackWorld} §fへ転送しています...")
        teleportAll(worldName, config.fallbackWorld)

        // Step3: 2tick後にアンロード
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            task.status = RegenStatus.UNLOADING
            plugin.logger.info("[$worldName] Multiverseアンロード中...")
            plugin.multiverseHook.unloadWorld(config.multiverseWorldName)

            // Step4: 非同期でフォルダ削除
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                task.status = RegenStatus.DELETING
                broadcast("§cワールドデータを削除中...")

                val worldFolder = File(Bukkit.getWorldContainer(), config.multiverseWorldName)
                try {
                    deleteDirectory(worldFolder.toPath())
                } catch (e: Exception) {
                    failRegen(worldName, task, "フォルダ削除失敗: ${e.message}")
                    return@Runnable
                }

                // Step5: メインスレッドでワールド再作成
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    task.status = RegenStatus.CREATING
                    broadcast("§aワールドを再生成中...")

                    val ok = plugin.multiverseHook.importWorld(config)
                    if (!ok) {
                        failRegen(worldName, task, "Multiverse によるワールド作成失敗")
                        return@Runnable
                    }
                    finishRegen(worldName, task)
                })
            })
        }, 2L)
    }

    // ── 完了・失敗 ────────────────────────────────────

    private fun finishRegen(worldName: String, task: RegenTask) {
        task.status = RegenStatus.COMPLETE
        val elapsed = task.elapsedSeconds

        plugin.whitelistManager.disableBlock()
        activeTasks.remove(worldName)

        broadcast("§a§l${worldName} §aの再生成が完了しました！ §7(${elapsed}秒)")
        Bukkit.getOnlinePlayers().forEach { p ->
            p.sendTitle("§a§l完了！", "§e${worldName} の再生成が終わりました", 10, 60, 20)
        }
        plugin.logger.info("[$worldName] 再生成完了（${elapsed}秒）")
    }

    private fun failRegen(worldName: String, task: RegenTask, reason: String) {
        task.status = RegenStatus.FAILED
        task.failReason = reason

        plugin.whitelistManager.disableBlock()
        activeTasks.remove(worldName)

        broadcast("§c${worldName} の再生成に失敗しました: §7${reason}")
        plugin.logger.severe("[$worldName] 再生成失敗: $reason")
    }

    // ── ヘルパー ──────────────────────────────────────

    private fun teleportAll(targetWorldName: String, fallbackWorldName: String) {
        val target   = plugin.server.getWorld(targetWorldName)
        val fallback = plugin.server.getWorld(fallbackWorldName)
            ?: plugin.server.worlds.first()
        val spawn    = fallback.spawnLocation

        Bukkit.getOnlinePlayers()
            .filter { target == null || it.world == target }
            .forEach { it.teleport(spawn) }
    }

    private fun broadcast(message: String) {
        Bukkit.broadcastMessage("${OraWorldRegen.PREFIX}${message}")
        plugin.logger.info(message.replace("§.", "").replace("§[a-fk-or0-9]".toRegex(), ""))
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
    }
}
