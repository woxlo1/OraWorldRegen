package oraserver.oraworldregen.model

import oraserver.oraworldregen.manager.CronParser
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * スケジュールエントリの共通インターフェース。
 *
 * ## 対応する2形式
 *
 * ### 1. cron形式（後方互換）
 * ```yaml
 * schedules:
 *   - cron: "0 4 * * 0"
 * ```
 *
 * ### 2. human-readable形式（新形式）
 * すべてのフィールドはオプションで、省略すると「毎〇〇」として扱われます。
 * ```yaml
 * schedules:
 *   - time: "18:30"            # 発動時刻 (HH:mm)    省略不可
 *     dayofweek: "MONDAY"      # 曜日指定 (MONDAY〜SUNDAY)
 *     day: 15                  # 月内日付 (1〜31)
 *     # dayofweek と day を両方書いた場合は両方の条件をANDで判定
 * ```
 *
 * #### 指定例
 * | time | dayofweek | day | 意味 |
 * |------|-----------|-----|------|
 * | "04:00" | - | - | 毎日 04:00 |
 * | "18:30" | "MONDAY" | - | 毎週月曜 18:30 |
 * | "12:00" | - | 1 | 毎月1日 12:00 |
 * | "09:00" | "WEDNESDAY" | 15 | 毎月15日かつ水曜 09:00 |
 */
sealed class ScheduleEntry {

    /** 指定した ZoneId で「今この瞬間」がスケジュールに一致するか判定する */
    abstract fun matchesNow(zoneId: ZoneId): Boolean

    /** 設定ファイルや表示に使う文字列表現 */
    abstract override fun toString(): String

    // =========================================================================
    // cron 形式
    // =========================================================================

    /**
     * 従来の5フィールド cron 式。
     * 例: `"0 4 * * 0"` → 毎週日曜 04:00
     */
    data class Cron(val parser: CronParser) : ScheduleEntry() {
        override fun matchesNow(zoneId: ZoneId) = parser.matchesNow(zoneId)
        override fun toString() = parser.raw
    }

    // =========================================================================
    // human-readable 形式
    // =========================================================================

    /**
     * わかりやすいキーワード形式。
     *
     * @param hour       発動時（0〜23）
     * @param minute     発動分（0〜59）
     * @param dayOfWeek  曜日条件（null = 毎日）
     * @param dayOfMonth 月内日付条件（null = 毎日, 1〜31）
     */
    data class Human(
        val hour: Int,
        val minute: Int,
        val dayOfWeek: DayOfWeek? = null,
        val dayOfMonth: Int? = null
    ) : ScheduleEntry() {

        init {
            require(hour in 0..23)   { "hour は 0〜23 で指定してください: $hour" }
            require(minute in 0..59) { "minute は 0〜59 で指定してください: $minute" }
            dayOfMonth?.let {
                require(it in 1..31) { "day は 1〜31 で指定してください: $it" }
            }
        }

        override fun matchesNow(zoneId: ZoneId): Boolean {
            val now = ZonedDateTime.now(zoneId).withSecond(0).withNano(0)
            if (now.hour != hour || now.minute != minute) return false
            if (dayOfWeek  != null && now.dayOfWeek   != dayOfWeek)  return false
            if (dayOfMonth != null && now.dayOfMonth  != dayOfMonth) return false
            return true
        }

        override fun toString(): String = buildString {
            append("%02d:%02d".format(hour, minute))
            dayOfWeek?.let  { append(" 毎週${it.toJapanese()}") }
            dayOfMonth?.let { append(" 毎月${it}日") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 拡張関数
// ─────────────────────────────────────────────────────────────────────────────

private fun DayOfWeek.toJapanese() = when (this) {
    DayOfWeek.MONDAY    -> "月曜"
    DayOfWeek.TUESDAY   -> "火曜"
    DayOfWeek.WEDNESDAY -> "水曜"
    DayOfWeek.THURSDAY  -> "木曜"
    DayOfWeek.FRIDAY    -> "金曜"
    DayOfWeek.SATURDAY  -> "土曜"
    DayOfWeek.SUNDAY    -> "日曜"
}