package oraserver.oraworldregen.model

/**
 * ワールド再生成後に自動生成するMultiverse-Portalsゲートの設定。
 *
 * ゲートはワールドのスポーン地点を基準に配置されます。
 *
 * ## config.yml の書き方
 * ```yaml
 * gates:
 *   myGate:
 *     world: "resources"
 *     facing: "south"       # ゲートの向き: north / south / east / west
 *     size:
 *       width: 2            # ゲート内側の幅（ブロック数）
 *       height: 3           # ゲート内側の高さ（ブロック数）
 *     offset:               # スポーンからのオフセット（省略可、デフォルト0）
 *       x: 0
 *       y: 0
 *       z: 3
 *     frame-block: "OBSIDIAN"
 *     portal-block: "WATER"
 *     destination: "w:world"
 *     enabled: true
 *     owner: "OraWorldRegen"
 * ```
 *
 * ## facing について
 * | 値 | ゲートが広がる軸 | 意味 |
 * |----|-----------------|------|
 * | north / south | X軸方向 | 東西に広がる（南北向きに立つ） |
 * | east / west | Z軸方向 | 南北に広がる（東西向きに立つ） |
 */
data class GateConfig(
    /** ゲート名 */
    val name: String,

    /** ゲートが存在するワールド名 */
    val worldName: String,

    /** ゲートの向き */
    val facing: GateFacing = GateFacing.SOUTH,

    /** ゲート内側の幅（ブロック数） */
    val width: Int = 2,

    /** ゲート内側の高さ（ブロック数） */
    val height: Int = 3,

    /** スポーン地点からのX方向オフセット */
    val offsetX: Int = 0,

    /** スポーン地点からのY方向オフセット（0 = 地面の上） */
    val offsetY: Int = 0,

    /** スポーン地点からのZ方向オフセット */
    val offsetZ: Int = 0,

    /** フレームブロックのMaterial名 */
    val frameBlock: String = "OBSIDIAN",

    /** ゲート内側に充填するブロックのMaterial名 */
    val portalBlock: String = "WATER",

    /** Multiverse destination文字列 */
    val destination: String,

    val enabled: Boolean = true,
    val owner: String = "OraWorldRegen"
)

enum class GateFacing {
    /** 東西に広がるゲート（ゲートの「面」が南北を向く） */
    NORTH,
    SOUTH,
    /** 南北に広がるゲート（ゲートの「面」が東西を向く） */
    EAST,
    WEST;

    /** X軸方向にゲートが広がるか（NORTH/SOUTH） */
    val expandsAlongX: Boolean get() = this == NORTH || this == SOUTH

    companion object {
        fun parse(s: String?): GateFacing = when (s?.uppercase()) {
            "NORTH" -> NORTH
            "EAST"  -> EAST
            "WEST"  -> WEST
            else    -> SOUTH  // デフォルト
        }
    }
}
