package oraserver.oraworldregen.manager

import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * シンプルな cron パーサー (分 時 日 月 曜日, *のみ対応)
 */
class CronParser(val raw: String) {

    private val WILDCARD = -1
    private val parts: IntArray

    init {
        val tokens = raw.trim().split("\\s+".toRegex())
        require(tokens.size == 5) { "cron 書式が不正: \"$raw\"" }
        parts = IntArray(5) { i ->
            if (tokens[i] == "*") WILDCARD
            else tokens[i].toInt()
        }
    }

    val minute  get() = parts[0]
    val hour    get() = parts[1]
    val day     get() = parts[2]
    val month   get() = parts[3]
    val weekday get() = parts[4]

    fun matches(dt: ZonedDateTime): Boolean {
        if (minute  != WILDCARD && dt.minute                     != minute)  return false
        if (hour    != WILDCARD && dt.hour                       != hour)    return false
        if (day     != WILDCARD && dt.dayOfMonth                 != day)     return false
        if (month   != WILDCARD && dt.monthValue                 != month)   return false
        if (weekday != WILDCARD && (dt.dayOfWeek.value % 7)      != weekday) return false
        return true
    }

    fun matchesNow(zoneId: ZoneId): Boolean =
        matches(ZonedDateTime.now(zoneId).withSecond(0).withNano(0))

    fun toHumanReadable(): String {
        val wd = if (weekday == WILDCARD) "*"
                 else arrayOf("日","月","火","水","木","金","土")[weekday] + "曜"
        return "${if (hour == WILDCARD) "*" else hour}時${if (minute == WILDCARD) "*" else minute}分 曜:$wd"
    }

    override fun toString() = raw
}
