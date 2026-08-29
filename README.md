# HealthSync - Android Health Connect Exporter & Dashboard

Android の「ヘルスコネクト（Health Connect）」から多次元の健康データ（歩数、睡眠、体重、体脂肪、心拍数、消費カロリー）を取得し、グラフ表示および外部クラウド・Webhook への自動定期同期を行うアプリです。

## 主な機能
1. **多次元ヘルスデータの集約・表示**
   - 歩数（本日の総歩数・分刻みレコード）
   - 睡眠（総睡眠時間・深い/浅い/レム/覚醒ステージの可視化バー）
   - 体重 & 体脂肪率
   - 心拍数（平均・最小・最大・安静時心拍数）
   - 消費エネルギー（総カロリー・アクティブカロリー）
2. **直近 7日・14日間の推移グラフ (Charts)**
   - Compose Canvas を用いた日別歩数バーチャート、睡眠時間チャート、体重推移ラインチャート
3. **Webhook / クラウド自動エクスポート**
   - 汎用 JSON 形式（`HealthSyncPayload`）で GAS / Notion / Supabase / 自作サーバー等へ HTTP POST 送信
   - 認証ヘッダー（`Authorization: Bearer xxx` 等）の付与対応
   - テスト送信機能（疎通確認）
4. **WorkManager によるバックグラウンド定期自動同期**
   - 1時間 / 3時間 / 6時間 / 12時間 / 24時間間隔のバックグラウンド自動同期
   - ネットワーク接続時制約・低電力配慮
   - 直近の同期履歴ログ（成功/失敗ステータス、HTTPコード、日時）の保存と確認

---

## 技術スタック
- **UI**: Jetpack Compose + Material 3 + Navigation
- **SDK**: `androidx.health.connect:connect-client:1.1.0-alpha11`
- **非同期・定期実行**: Kotlin Coroutines + Flow + WorkManager (`2.10.0`)
- **設定保存**: Jetpack DataStore Preferences (`1.1.1`)
- **通信**: OkHttp (`4.12.0`) + Kotlinx Serialization JSON (`1.7.3`)
- **ターゲット**: Android 9.0+ (API 28+) / 推奨 Android 14+ (API 34+)

---

## プロジェクト構成
```
health-sync/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml              # 権限・Activity・Rationale設定
│   │   ├── java/com/example/healthsync/
│   │   │   ├── MainActivity.kt              # Bottom Navigation & 画面ホスト
│   │   │   ├── HealthConnectManager.kt      # Health Connect 各種データ集計
│   │   │   ├── HealthSyncViewModel.kt       # 状態管理・同期オーケストレーション
│   │   │   ├── PrivacyPolicyActivity.kt     # Health Connect 要件用画面
│   │   │   ├── data/
│   │   │   │   ├── model/HealthModels.kt    # サマリー・明細・ペイロード・ログ
│   │   │   │   ├── repository/SettingsRepository.kt # DataStore 設定管理
│   │   │   │   └── network/WebhookClient.kt # OkHttp による JSON 送信
│   │   │   ├── worker/
│   │   │   │   ├── HealthSyncWorker.kt      # WorkManager バックグラウンド同期
│   │   │   │   └── WorkManagerHelper.kt     # スケジューラ
│   │   │   └── ui/
│   │   │       ├── components/              # メトリックカード・推移グラフ
│   │   │       ├── screens/                 # Dashboard, Charts, Settings
│   │   │       └── theme/                   # Material 3 テーマ
│   │   └── res/                             # アイコン・リソース
│   └── build.gradle.kts                     # アプリ依存関係
├── gradle/libs.versions.toml                # バージョンカタログ
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Webhook 送信ペイロードの形式例

```json
{
  "timestamp": "2026-08-29T08:30:00Z",
  "deviceId": "Pixel 8",
  "syncType": "BACKGROUND_WORKER",
  "summary": {
    "date": "2026-08-29",
    "steps": 8420,
    "activeCaloriesKcal": 380.5,
    "totalCaloriesKcal": 1950.0,
    "sleepDurationMinutes": 450,
    "sleepStages": {
      "deepMinutes": 90,
      "lightMinutes": 240,
      "remMinutes": 100,
      "awakeMinutes": 20
    },
    "latestWeightKg": 65.4,
    "latestBodyFatPercent": 17.8,
    "avgHeartRateBpm": 68,
    "minHeartRateBpm": 52,
    "maxHeartRateBpm": 135,
    "restingHeartRateBpm": 58
  },
  "detailedRecords": {
    "stepItems": [ ... ],
    "sleepSessions": [ ... ],
    "weightRecords": [ ... ],
    "heartRateSamples": [ ... ]
  }
}
```
