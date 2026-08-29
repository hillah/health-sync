package com.example.healthsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthsync.ui.theme.HealthSyncTheme

class PrivacyPolicyActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HealthSyncTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("プライバシーポリシー") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "戻る"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "HealthSync プライバシーポリシー (個人利用版)",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "本アプリは個人利用（セルフホスト・サイドロード）を目的として開発されたデータエクスポートツールです。\n\n" +
                                    "1. データの収集と利用目的\n" +
                                    "本アプリは、Android Health Connect（ヘルスコネクト）から歩数・睡眠・心拍数等の健康データを読み取り、ユーザー自身が設定した外部エンドポイント（Webhook URLやスプレッドシート等）への転送、またはローカルでのJSONエクスポートのみに使用します。\n\n" +
                                    "2. 第三者へのデータ提供\n" +
                                    "本アプリの開発者や第三者のサーバーに対して、ユーザーの許可なく健康データが送信されることは一切ありません。\n\n" +
                                    "3. データの保持と削除\n" +
                                    "取得したデータは転送後にローカルから破棄され、アプリ内に長期間保存されることはありません。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
