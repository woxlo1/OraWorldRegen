package oraserver.oraworldregen.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class RegenHistory(
    val worldName: String,
    val startTime: Instant,
    val endTime: Instant,
    val durationSeconds: Long,
    val success: Boolean,
    val failReason: String? = null,
    val triggeredBy: String = "schedule",   // "schedule" or "manual:<player>"
    val backupFile: String? = null
) {
    fun toConfigString(): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Tokyo"))
        val statusStr = if (success) "SUCCESS" else "FAILED(${failReason ?: "unknown"})"
        val backupStr = backupFile?.let { " backup=$it" } ?: ""
        return "[${fmt.format(startTime)}] $worldName | $statusStr | ${durationSeconds}秒 | by=$triggeredBy$backupStr"
    }

    /** YAMLマップとしてシリアライズ */
    fun toMap(): Map<String, Any> = mapOf(
        "world" to worldName,
        "start" to startTime.epochSecond,
        "end" to endTime.epochSecond,
        "duration" to durationSeconds,
        "success" to success,
        "failReason" to (failReason ?: ""),
        "triggeredBy" to triggeredBy,
        "backupFile" to (backupFile ?: "")
    )

    companion object {
        fun fromMap(map: Map<*, *>): RegenHistory? = runCatching {
            RegenHistory(
                worldName = map["world"].toString(),
                startTime = Instant.ofEpochSecond((map["start"] as Number).toLong()),
                endTime   = Instant.ofEpochSecond((map["end"]   as Number).toLong()),
                durationSeconds = (map["duration"] as Number).toLong(),
                success   = map["success"] as Boolean,
                failReason = map["failReason"]?.toString()?.ifBlank { null },
                triggeredBy = map["triggeredBy"]?.toString() ?: "unknown",
                backupFile  = map["backupFile"]?.toString()?.ifBlank { null }
            )
        }.getOrNull()
    }
}
