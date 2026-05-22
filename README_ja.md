# OraWorldRegen

**Paper/Spigot + Multiverse-Core 向け ワールド自動再生成プラグイン**

ワールドを定期的に自動で再生成し、スケジュール管理・バックアップ・GUI操作など再生成に必要な機能をすべて提供します。

---

## 目次

- [動作環境](#動作環境)
- [インストール](#インストール)
- [設定ファイル](#設定ファイル)
- [コマンド一覧](#コマンド一覧)
- [パーミッション](#パーミッション)
- [GUI操作](#gui操作)
- [スケジュール設定](#スケジュール設定)
- [ゲート自動生成](#ゲート自動生成)
- [再生成フロー](#再生成フロー)
- [ライセンス](#ライセンス)

---

## 動作環境

| 項目 | バージョン |
|------|-----------|
| Minecraft | 1.20.4 |
| サーバー | Paper / Spigot |
| Java | 17 以上 |
| 依存プラグイン | OraPluginAPI, Multiverse-Core |
| オプション依存 | Multiverse-Portals（ゲート自動生成を使う場合） |

---

## インストール

1. `OraWorldRegen-1.0.0.jar` を `plugins/` フォルダに配置する
2. `OraPluginAPI.jar` と `Multiverse-Core.jar` も同じフォルダに配置する
3. サーバーを起動すると `plugins/OraWorldRegen/config.yml` が自動生成される
4. `config.yml` を編集してワールドを登録し、`/owr reload` で反映する

---

## 設定ファイル

`plugins/OraWorldRegen/config.yml`

```yaml
worlds:
  resources:                           # 任意のワールド名（キー）
    multiverse-world-name: "resources" # Multiverse-Core に登録されているワールド名
    environment: NORMAL                # NORMAL / NETHER / THE_END
    world-type: NORMAL                 # NORMAL / FLAT / AMPLIFIED / LARGE_BIOMES
    seed: ""                           # 空欄でランダムシード
    generator: ""                      # 空欄でバニラ生成

    enabled: true
    countdown-seconds: 300             # 再生成前カウントダウン秒数（0で即実行）
    fallback-world: "world"            # プレイヤーの退避先ワールド

    schedules:
      - cron: "0 4 * * 0"             # 毎週日曜 AM4:00

    backup:
      enabled: false
      directory: "backups"            # プラグインフォルダからの相対パス（絶対パスも可）
      max-count: 5                    # 保持するバックアップの最大数

    world-border:
      enabled: false
      size: 2000.0                    # ボーダー直径（例: 2000 → 2000×2000）
      center-x: 0.0
      center-z: 0.0
      damage-amount: 0.2
      damage-buffer: 5.0
      warning-distance: 5
      warning-time: 15

    post-regen-commands:
      - "mv modify set gamemode survival {world}"  # {world} でワールド名に置換

    return-players:
      enabled: true                   # 再生成完了後にプレイヤーを元の場所へ戻す
      delay-seconds: 60               # 完了後何秒後に戻すか

settings:
  notify-at-seconds: [300, 120, 60, 30, 10, 5, 4, 3, 2, 1]
  timezone: "Asia/Tokyo"
  debug: false
```

### ゲート設定

Multiverse-Portals のゲートを再生成後に自動生成したい場合、`config.yml` に `gates` セクションを追加します。

```yaml
gates:
  myGate:
    world: "resources"           # ゲートを設置するワールド
    location:
      x1: 10
      y1: 64
      z1: 10
      x2: 12
      y2: 67
      z2: 10
    frame-block: "OBSIDIAN"      # フレームブロック
    portal-block: "WATER"        # 内側ブロック（WATER / LAVA / NETHER_PORTAL / AIR 等）
    destination: "w:world"       # Multiverse destination 文字列
    enabled: true
    owner: "OraWorldRegen"
```

---

## コマンド一覧

すべてのコマンドは `/owr` から始まります。

| コマンド | 説明 |
|---------|------|
| `/owr` | メインメニューGUIを開く |
| `/owr gui` | メインメニューGUIを開く |
| `/owr start <world>` | 指定ワールドの手動再生成を開始（プレイヤーはGUI確認あり） |
| `/owr cancel <world>` | カウントダウン中またはキュー待機中の再生成をキャンセル |
| `/owr queue` | 再生成キューの状態を表示 |
| `/owr status` | 全ワールドの現在の状態を表示 |
| `/owr list` | 登録されているワールドの一覧を表示 |
| `/owr history [world] [page]` | 再生成履歴を表示 |
| `/owr reload` | `config.yml` を再読み込み |
| `/owr help` | ヘルプを表示 |

---

## パーミッション

| パーミッション | 説明 | デフォルト |
|--------------|------|----------|
| `oraworldregen.admin` | すべての `/owr` コマンドを使用できる | OP のみ |

---

## GUI操作

### メインメニュー（`/owr`）

各ワールドがウール色で状態を表示します。

| ウール色 | 状態 |
|---------|------|
| 緑 | 待機中（再生成可能） |
| 黄 | カウントダウン中 |
| オレンジ | 再生成中 |
| シアン | キュー待機中 |
| 赤 | 無効 |

- **左クリック** → ワールド詳細GUIを開く
- **右クリック** → 即時再生成（確認ダイアログあり）/ キャンセル

### ワールド詳細GUI

設定情報・スケジュール・バックアップ・ボーダー・履歴などを確認できます。
有効/無効のトグルや今すぐ再生成ボタンもここにあります。

---

## スケジュール設定

2種類の形式を使用でき、1つのワールドに混在させることも可能です。

### human-readable 形式（推奨）

`time` キーが必須で、`dayofweek`・`day` は省略可能です。

```yaml
schedules:
  # 毎週月曜 18:30
  - time: "18:30"
    dayofweek: "MONDAY"

  # 毎月15日 12:00
  - time: "12:00"
    day: 15

  # 毎日 03:00（dayofweek / day を省略）
  - time: "03:00"

  # 毎月15日かつ水曜 09:00（AND条件）
  - time: "09:00"
    dayofweek: "WEDNESDAY"
    day: 15
```

| キー | 値 | 説明 |
|------|-----|------|
| `time` | `"HH:mm"` | 発動時刻（必須） |
| `dayofweek` | `MONDAY` 〜 `SUNDAY` | 曜日条件（省略すると毎日） |
| `day` | `1` 〜 `31` | 月内日付条件（省略すると毎日） |

`dayofweek` と `day` を両方指定した場合は **AND 条件**になります。

### cron 形式（後方互換）

既存の設定との互換性のために引き続き利用できます。

```yaml
schedules:
  - cron: "0 4 * * 0"   # 毎週日曜 04:00
```

```
分  時  日  月  曜日(0=日曜)
0   4   *   *   0    → 毎週日曜 04:00
0   0   *   *   *    → 毎日 00:00
```

> **注意:** CronParser は `*` と固定数値のみ対応しています。`*/n` や範囲指定（`1-5`）は未対応です。

---

## ゲート自動生成

再生成完了後、`gates` セクションに登録されたゲートをワールドに自動配置し、`plugins/Multiverse-Portals/portals.yml` に書き込んで `/mvp reload` を実行します。

**destination 書式の例:**

| 書式 | 意味 |
|-----|------|
| `w:world` | `world` のスポーン地点 |
| `e:world:100,64,200` | 座標指定 |
| `e:world:100,64,200:0:90` | 座標 + 向き指定 |
| `p:otherPortal` | 別ポータルへ |

---

## 再生成フロー

```
startRegen()
  │
  ├─ [countdown-seconds > 0]
  │     カウントダウン（通知 + タイトル表示）
  │
  ├─ プレイヤーを fallback-world へ転送
  ├─ [backup.enabled] バックアップ作成（非同期）
  ├─ Multiverse からワールドをアンロード
  ├─ ワールドフォルダを削除（非同期）
  ├─ Multiverse でワールドを再生成
  ├─ [world-border.enabled] ワールドボーダーを設定
  ├─ [gates] ゲートを配置 → portals.yml 書き込み → /mvp reload
  ├─ [post-regen-commands] コマンドを実行
  └─ [return-players.enabled] プレイヤーを元の場所へ戻す
```

複数ワールドの再生成が同時に要求された場合、先着のワールドが完了するまで後続はキューで待機します。

---

## データファイル

| ファイル | 内容 |
|---------|------|
| `plugins/OraWorldRegen/config.yml` | メイン設定 |
| `plugins/OraWorldRegen/history.yml` | 再生成履歴（最大200件） |
| `plugins/OraWorldRegen/regen.log` | プレーンテキストログ |
| `plugins/OraWorldRegen/backups/` | バックアップ zip ファイル（設定有効時） |

---

## ライセンス

Apache License 2.0 — 詳細は [LICENSE](LICENSE) を参照してください。