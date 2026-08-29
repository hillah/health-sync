# HealthSync - Android Health Connect Exporter

Android の「ヘルスコネクト（Health Connect）」から健康データ（歩数、睡眠、体重、心拍数など）を取得し、外部へエクスポートするための個人用Androidアプリです。

## 技術スタック
- **UI**: Jetpack Compose + Material 3
- **SDK**: `androidx.health.connect:connect-client:1.1.0-alpha11`
- **非同期**: Kotlin Coroutines + Flow + ViewModel
- **ターゲット**: Android 9.0+ (API 28+) / 推奨 Android 14+ (API 34+)

## プロジェクト構成
```
health-sync/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml              # 権限・Rationale・プライバシーポリシー設定
│   │   ├── java/com/example/healthsync/
│   │   │   ├── MainActivity.kt              # Compose メイン画面
│   │   │   ├── HealthConnectManager.kt      # Health Connect API ラッパー
│   │   │   ├── HealthSyncViewModel.kt       # 状態管理・データ取得ロジック
│   │   │   ├── PrivacyPolicyActivity.kt     # Health Connect要件用画面
│   │   │   └── ui/theme/                    # Composeテーマ・カラー
│   │   └── res/                             # リソース
│   └── build.gradle.kts                     # アプリ依存関係
├── gradle/libs.versions.toml                # バージョンカタログ
├── build.gradle.kts
└── settings.gradle.kts
```

## 動作確認・ビルド手順

### 1. Android Studio で開く
1. Android Studio を起動し、「Open」から本フォルダ (`health-sync`) を選択します。
2. Gradle Sync が完了するのを確認します。

### 2. 実機デバッグと Health Connect の事前準備
1. Android 実機の「開発者向けオプション」→「USBデバッグ」を有効にしてPCに接続します。
2. **Android 13 以前の端末の場合**:
   - Google Play ストアから **「Health Connect (ヘルスコネクト)」** アプリをインストールしてください。
3. **Android 14 以降の端末の場合**:
   - 設定アプリ内に Health Connect が標準統合されています。
4. **データの準備**:
   - Google Fit、Galaxy Health、Fitbit 等の対応アプリから Health Connect に歩数データが同期されていることを確認してください。

### 3. アプリの実行
1. Android Studio から `app` を実行します。
2. 起動後、画面に表示される **「権限をリクエストする」** ボタンをタップし、歩数読み取り権限を許可します。
3. 本日の歩数と、時間帯ごとのレコード一覧が表示されます。
