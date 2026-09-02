# HealthSync - Android Health Connect Exporter & Dashboard

Android の「ヘルスコネクト（Health Connect）」から多次元の健康データ（歩数、睡眠、体重、体脂肪、心拍数、消費カロリー、血圧、栄養摂取）を取得し、グラフ表示および Google スプレッドシート / 外部クラウド・Webhook への自動定期同期を行うアプリです。

## 主な機能
1. **多次元ヘルスデータの集約・表示**
   - **歩数**: 本日の総歩数・分刻みレコード
   - **睡眠**: 総睡眠時間・深い/浅い/レム/覚醒ステージの可視化バー
   - **体重 & 体脂肪率**: 最新値・推移
   - **心拍数**: 平均・最小・最大・安静時心拍数
   - **消費エネルギー**: 総カロリー・アクティブカロリー
   - **血圧**: 測定日時ごとの最高/最低血圧 (mmHg)
   - **栄養・食事摂取**: 朝食/昼食/夕食/間食ごとの摂取カロリー・PFCバランス (タンパク質/脂質/炭水化物) に加え、食物繊維、糖質、食塩相当量、ビタミン各種 (A, B群, C, D, E等)、主要ミネラル (Ca, Fe, Zn, Mg等)、脂質内訳 (飽和脂肪酸、コレステロール等)
2. **直近 7日・14日間の推移グラフ (Charts)**
   - Compose Canvas を用いた日別歩数バーチャート、睡眠時間チャート、体重推移ラインチャート
3. **Google スプレッドシート (GAS) / Webhook 自動エクスポート**
   - 汎用 JSON 形式（`HealthSyncPayload`）で GAS / Notion / Supabase / 自作サーバー等へ HTTP POST 送信
   - 認証ヘッダー（`Authorization: Bearer xxx` 等）の付与対応
   - テスト送信機能（疎通確認）
4. **WorkManager によるバックグラウンド定期自動同期**
   - 1時間 / 3時間 / 6時間 / 12時間 / 24時間間隔のバックグラウンド自動同期
   - ネットワーク接続時制約・低電力配慮
   - 直近の同期履歴ログ（成功/失敗ステータス、HTTPコード、日時）の保存と確認

---

## Google スプレッドシート連携（セットアップ手順）

本アプリから送信されたデータを Google スプレッドシートへ自動保存する手順です。
プロジェクト内の [`gas/Code.js.sample`](gas/Code.js.sample) を使用します。

### 1. スプレッドシートの新規作成
1. [Google スプレッドシート](https://sheets.new) を新規作成します（シート名は「健康ログ」など任意）。

### 2. Google Apps Script (GAS) の設定
1. スプレッドシート上部メニューの **「拡張機能」→「Apps Script」** をクリックします。
2. エディタに表示されているコードをすべて削除し、プロジェクト内の [`gas/Code.js.sample`](gas/Code.js.sample) の内容をすべてコピー＆ペーストします。
3. `Ctrl + S`（Macは `Cmd + S`）で保存します。

### 3. ウェブアプリとしてデプロイ
1. 画面右上の青い **「デプロイ」→「新しいデプロイ」** をクリックします。
2. 左側の歯車アイコン（種類の選択）から **「ウェブアプリ」** を選択します。
3. 各項目を以下のように設定します：
   - **説明**: `HealthSync Receiver`（任意）
   - **次のユーザーとして実行**: `自分 (your-email@gmail.com)`
   - **アクセスできるユーザー**: `全員 (Anyone)` **← ※必ず「全員」を選択してください**
4. **「デプロイ」** ボタンをクリックし、表示される画面でアクセスを承認（許可）します。
5. デプロイ完了画面に表示される **「ウェブアプリの URL」**（`https://script.google.com/macros/s/.../exec`）をコピーします。

### 4. HealthSync アプリへの URL 登録
1. Android アプリ「HealthSync」を起動し、下部タブの **「設定・同期」** を開きます。
2. **「Webhook エンドポイント URL」** にコピーした GAS の URL を貼り付けます。
3. **「URL設定を保存」** をタップした後、**「テスト送信」** をタップしてスナックバーで成功（HTTP 200）が表示されることを確認します。

### 5. 自動作成されるシート構成と更新・蓄積ルール
データが同期されると、スプレッドシート内に以下の 3 つのシートが自動生成・管理されます：

| シート名 | 記録粒度・更新ルール | 内容・挙動 |
| :--- | :--- | :--- |
| **`DailySummary`** | **1日1行 (Upsert)** | 日付ごとの総括データ（歩数、睡眠、体重、血圧、カロリー、PFCバランス等、計19列）。直近7日分のサマリーを受信し、過去日も含めて**同じ日付の行が最新値へ安全に上書き更新**されます。 |
| **`BloodPressureLog`** | **測定毎 (重複排除追記)** | 測定日時（秒単位）ごとに**1行ずつ時系列で追記・蓄積**。既に記録済みの測定データは自動スキップされるため、二重登録を防ぎつつ朝・晩の測定履歴が残ります。 |
| **`NutritionLog`** | **食事毎 (直近7日間の範囲洗い替え)** | 食事日時ごとに区分(朝食/昼食/夕食/間食)、品名、エネルギー、PFC、食物繊維、糖質、食塩相当量、各種ビタミン、主要ミネラル、脂質内訳(飽和脂肪酸、コレステロール等)、RecordIdを記録。直近7日間分を最新の Health Connect レコードで**一括置換（洗い替え）**するため、重複を完全に防ぎつつ、後から登録された過去の食事や修正も正確に反映されます。 |

> [!TIP]
> **既存シートをお使いの場合の自動マイグレーション**
> 最新の `gas/Code.js.sample` を GAS エディタへ上書きして新バージョンをデプロイするだけで、次回同期時に**DailySummary は19列へ自動縮小整理され、NutritionLog は詳細列および洗い替え処理へ安全に自動更新**されます。シートを削除して作り直す必要はありません。

#### 連携タイミングと取りこぼし防止の仕組み
1. **1日に複数回の定期同期時**:
   - `DailySummary` では、朝時点の歩数・カロリーから昼・夜にかけて常に最新の当日累計値へ更新され、過去数日分に後から入力された食事も更新されます。
   - `BloodPressureLog` では、朝測った血圧と夜測った血圧がそれぞれ独立した測定ログとして蓄積されます。
   - `NutritionLog` では、直近7日間の食事が常に最新のクリーンな状態で洗い替え更新され、重複なく同期されます。
2. **オフラインや数日間同期が空いた場合**:
   - アプリ側で直近7日分のサマリーおよび詳細レコード（食事・血圧・体重など）を保持して送信するため、次回の同期時に未送信だった過去の測定データが**自動的にスプレッドシートへ補完・洗い替え**されます。

---

## GitHub Actions による自動 APK ビルド & リリース

本リポジトリには、GitHub Actions を用いて自動で Release APK をビルド・署名・公開するワークフロー（[`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)）が設定されています。

### 1. 署名用キーストアの準備 & GitHub Secrets（環境変数）の設定

GitHub リポジトリに署名鍵（`.jks` / `.keystore`）を直接公開することなく、安全に自動署名を行うための手順です。

#### (1) 署名用キーストアの作成（初回のみ）
Android Studio のメニュー **「Build」→「Generate Signed Bundle / APK...」→「APK」→「Create new...」** からキーストアファイル（例: `my-release-key.jks`）を作成します。

#### (2) キーストアを Base64 文字列に変換
作成したキーストアファイルを Base64 形式のテキストに変換します。

* **Windows (PowerShell)**:
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("path\to\my-release-key.jks")) | Set-Clipboard
  ```
  *(クリップボードに Base64 文字列がコピーされます)*

* **macOS / Linux**:
  ```bash
  base64 -i path/to/my-release-key.jks | pbcopy
  ```

#### (3) GitHub リポジトリの Secrets に登録
GitHub のリポジトリページを開き、**「Settings」→「Secrets and variables」→「Actions」** の **「New repository secret」** から以下の 4 つの環境変数を登録します：

| Secret 名 | 内容 | 設定例 |
| :--- | :--- | :--- |
| **`KEYSTORE_BASE64`** | (2) で変換した Base64 テキスト | `MIIKvgIBAzCCCncGCSqGSIb...` |
| **`KEYSTORE_PASSWORD`** | キーストア作成時に設定したパスワード | `your_keystore_password` |
| **`KEY_ALIAS`** | 鍵のエイリアス名 | `healthsync_key` |
| **`KEY_PASSWORD`** | 鍵作成時に設定したパスワード | `your_key_password` |

---

### 2. リリースの実行方法

#### A. バージョンタグを打って自動公開（おすすめ）
Git で新しいバージョンのタグを作成して Push すると、Actions が起動して APK のビルド・署名を行い、**GitHub の「Releases」ページに APK が自動添付** されます。

```bash
# 例: v1.0.0 リリース
git tag v1.0.0
git push origin v1.0.0
```
> スマホのブラウザで GitHub の Releases ページを開き、`app-release.apk` をタップするだけで直接インストール・更新できます。

#### B. GitHub Web 画面からの手動ビルド
1. リポジトリの **「Actions」** タブを開きます。
2. 左側の **「Build & Release APK」** を選択し、**「Run workflow」** ボタンをクリックします。
3. ビルド完了後、実行詳細画面の **「Artifacts」** から APK をダウンロードできます。

---

## 技術スタック
- **UI**: Jetpack Compose + Material 3 + Navigation
- **SDK**: `androidx.health.connect:connect-client:1.1.0-alpha11`
- **非同期・定期実行**: Kotlin Coroutines + Flow + WorkManager (`2.10.0`)
- **設定保存**: Jetpack DataStore Preferences (`1.1.1`)
- **通信**: OkHttp (`4.12.0`) + Kotlinx Serialization JSON (`1.7.3`)
- **ターゲット**: Android 9.0+ (API 28+) / 推奨 Android 14+ (API 34+)

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
    "restingHeartRateBpm": 58,
    "latestSystolicMmHg": 118.0,
    "latestDiastolicMmHg": 76.0,
    "dietaryEnergyKcal": 2100.0,
    "breakfastCaloriesKcal": 450.0,
    "lunchCaloriesKcal": 800.0,
    "dinnerCaloriesKcal": 650.0,
    "snackCaloriesKcal": 200.0,
    "dietaryProteinGrams": 85.0,
    "dietaryFatGrams": 60.0,
    "dietaryCarbsGrams": 240.0,
    "dietaryFiberGrams": 22.5,
    "sugarGrams": 45.0,
    "saltGrams": 6.8,
    "sodiumMg": 2680.0,
    "calciumMg": 650.0,
    "ironMg": 8.5,
    "vitaminAMcg": 750.0,
    "vitaminCMg": 95.0,
    "vitaminDMcg": 8.0,
    "saturatedFatGrams": 18.0
  },
  "detailedRecords": {
    "stepItems": [ ... ],
    "sleepSessions": [ ... ],
    "weightRecords": [ ... ],
    "heartRateSamples": [ ... ],
    "bloodPressureRecords": [ ... ],
    "nutritionRecords": [
      {
        "name": "サラダチキンと玄米ご飯",
        "startTime": "2026-08-29T03:00:00Z",
        "endTime": "2026-08-29T03:30:00Z",
        "energyKcal": 480.0,
        "proteinGrams": 38.0,
        "fatGrams": 8.0,
        "carbsGrams": 62.0,
        "dietaryFiberGrams": 5.2,
        "sugarGrams": 1.5,
        "saltGrams": 1.8,
        "sodiumMg": 710.0,
        "calciumMg": 45.0,
        "ironMg": 2.1,
        "vitaminCMg": 12.0,
        "mealType": "MEAL_TYPE_LUNCH",
        "sourceApp": "jp.co.asken"
      }
    ]
  }
}
```
