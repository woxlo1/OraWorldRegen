package oraserver.oraworldregen.model

/**
 * ワールド再生成後に自動生成するMultiverse-Portalsゲートの設定。
 *
 * ## ポータルブロックについて
 * Multiverse-Portalsのポータルはフレーム内をどんなブロックでも「ゲート」にできる。
 * 標準的な選択肢:
 *   - WATER        : 水（見た目にわかりやすい）
 *   - LAVA         : 溶岩
 *   - NETHER_PORTAL: ネザーポータルブロック（音・エフェクトあり）
 *   - AIR          : 何も置かない（透明ゲート）
 *   - など任意のMaterial名
 *
 * ## location形式（portals.yml）
 * Multiverse-Portalsが読み込む portals.yml の location は
 *   "x1,y1,z1:x2,y2,z2"
 * という2点（選択範囲の対角2点）で表す。
 * ここでは「フレーム内側」の対角2点をconfigで指定する。
 */
data class GateConfig(
    /** ゲート名（/mvp list などに表示される名前） */
    val name: String,

    /** ゲートが存在するワールド名 */
    val worldName: String,

    // ── ゲート内側の対角2点（Multiverse-Portalsのlocation範囲） ──────────
    /** 内側範囲の第1点X */
    val x1: Int,
    /** 内側範囲の第1点Y */
    val y1: Int,
    /** 内側範囲の第1点Z */
    val z1: Int,
    /** 内側範囲の第2点X */
    val x2: Int,
    /** 内側範囲の第2点Y */
    val y2: Int,
    /** 内側範囲の第2点Z */
    val z2: Int,

    // ── ブロック設定 ──────────────────────────────────────────────────────
    /**
     * フレームブロックのMaterial名。
     * 例: "OBSIDIAN", "STONE", "OAK_PLANKS"
     * Multiverse-PortalsはフレームはANYTHINGでOKなので自由に設定可能。
     */
    val frameBlock: String = "OBSIDIAN",

    /**
     * ゲート内側に充填するブロックのMaterial名。
     * 例: "WATER", "LAVA", "NETHER_PORTAL", "AIR"
     * Multiverse-Portals的にはテレポートトリガーとして機能するブロック。
     */
    val portalBlock: String = "WATER",

    // ── テレポート先設定 ──────────────────────────────────────────────────
    /**
     * Multiverse destination文字列。
     * 例:
     *   "w:world"            → worldのスポーン
     *   "e:world:100,64,200" → 座標指定
     *   "e:world:100,64,200:0:90" → 座標+向き指定
     *   "p:otherPortal"      → 別ポータルへ
     */
    val destination: String,

    // ── オプション ────────────────────────────────────────────────────────
    /** このゲートを再生成後に生成するか */
    val enabled: Boolean = true,

    /** ゲートのオーナー（省略可） */
    val owner: String = "OraWorldRegen"
)
