package oraserver.oraworldregen.manager

import oraserver.oraworldregen.OraWorldRegen
import oraserver.oraworldregen.model.WorldRegenConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ワールドフォルダを zip 圧縮してバックアップするマネージャー。
 * 処理は非同期スレッドで呼ぶこと（呼び出し側の責任）。
 */
class BackupManager(private val plugin: OraWorldRegen) {

    /**
     * [config] に従いワールドをバックアップする。
     * @return 作成した zip ファイルのパス文字列、失敗時は null
     */
    fun backup(config: WorldRegenConfig): String? {
        if (!config.backupEnabled) return null

        val worldFolder = File(plugin.server.worldContainer, config.multiverseWorldName)
        if (!worldFolder.exists()) {
            plugin.logger.warning("[Backup] ワールドフォルダが存在しません: ${worldFolder.absolutePath}")
            return null
        }

        val backupDir = resolveBackupDir(config)
        backupDir.mkdirs()

        val timestamp = LocalDateTime.now(ZoneId.of("Asia/Tokyo"))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val zipName = "${config.multiverseWorldName}_${timestamp}.zip"
        val zipFile = File(backupDir, zipName)

        return try {
            plugin.logger.info("[Backup] バックアップ開始: ${worldFolder.name} -> ${zipFile.name}")
            zipDirectory(worldFolder, zipFile)
            plugin.logger.info("[Backup] バックアップ完了: ${zipFile.name} (${zipFile.length() / 1024}KB)")

            pruneOldBackups(config, backupDir)

            zipFile.absolutePath
        } catch (e: Exception) {
            plugin.logger.severe("[Backup] バックアップ失敗: ${e.message}")
            zipFile.delete()
            null
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun resolveBackupDir(config: WorldRegenConfig): File {
        val path = config.backupDirectory
        return if (File(path).isAbsolute) File(path)
               else File(plugin.dataFolder, path)
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile).buffered(65536)).use { zos ->
            val basePath = sourceDir.parentFile.toPath()
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = basePath.relativize(file.toPath()).toString()
                    .replace('\\', '/')
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(file).buffered(65536).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    /** 古いバックアップを maxCount を超えた分だけ削除する */
    private fun pruneOldBackups(config: WorldRegenConfig, backupDir: File) {
        val prefix = "${config.multiverseWorldName}_"
        val zips = backupDir.listFiles { f ->
            f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".zip")
        }?.sortedBy { it.lastModified() } ?: return

        val excess = zips.size - config.backupMaxCount
        if (excess > 0) {
            zips.take(excess).forEach { old ->
                if (old.delete()) plugin.logger.info("[Backup] 古いバックアップを削除: ${old.name}")
            }
        }
    }
}
