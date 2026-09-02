package com.example.healthsync.data.model

import kotlinx.serialization.Serializable

/**
 * 送信用・UI表示用の健康データ総合サマリー
 */
@Serializable
data class HealthSummary(
    val date: String,                    // "yyyy-MM-dd"
    val steps: Long = 0L,               // 総歩数
    val activeCaloriesKcal: Double = 0.0,// アクティブ消費カロリー (kcal)
    val totalCaloriesKcal: Double = 0.0, // 総消費カロリー (kcal)
    val sleepDurationMinutes: Long = 0L,// 睡眠時間 (分)
    val sleepStages: SleepStageSummary? = null,
    val latestWeightKg: Double? = null, // 最新体重 (kg)
    val latestBodyFatPercent: Double? = null, // 最新体脂肪率 (%)
    val avgHeartRateBpm: Long? = null,  // 平均心拍数 (bpm)
    val minHeartRateBpm: Long? = null,  // 最小心拍数 (bpm)
    val maxHeartRateBpm: Long? = null,  // 最大心拍数 (bpm)
    val restingHeartRateBpm: Long? = null, // 安静時心拍数 (bpm)
    
    // 血圧 (Blood Pressure)
    val latestSystolicMmHg: Double? = null,  // 収縮期血圧 (最高血圧 mmHg)
    val latestDiastolicMmHg: Double? = null, // 拡張期血圧 (最低血圧 mmHg)

    // 栄養摂取 (Nutrition)
    val dietaryEnergyKcal: Double = 0.0,     // 摂取エネルギー合計 (kcal)
    val breakfastCaloriesKcal: Double = 0.0, // 朝食 (kcal)
    val lunchCaloriesKcal: Double = 0.0,     // 昼食 (kcal)
    val dinnerCaloriesKcal: Double = 0.0,    // 夕食 (kcal)
    val snackCaloriesKcal: Double = 0.0,     // 間食 (kcal)
    val dietaryProteinGrams: Double = 0.0,   // タンパク質 (g)
    val dietaryFatGrams: Double = 0.0,       // 脂質 (g)
    val dietaryCarbsGrams: Double = 0.0,     // 炭水化物 (g)
    val dietaryFiberGrams: Double = 0.0,     // 食物繊維 (g)
    val sugarGrams: Double = 0.0,            // 糖質 (g)
    val saturatedFatGrams: Double = 0.0,     // 飽和脂肪酸 (g)
    val transFatGrams: Double = 0.0,         // トランス脂肪酸 (g)
    val cholesterolMg: Double = 0.0,         // コレステロール (mg)
    val sodiumMg: Double = 0.0,              // ナトリウム (mg)
    val saltGrams: Double = 0.0,             // 食塩相当量 (g)
    val potassiumMg: Double = 0.0,           // カリウム (mg)
    val calciumMg: Double = 0.0,             // カルシウム (mg)
    val ironMg: Double = 0.0,                // 鉄分 (mg)
    val zincMg: Double = 0.0,                // 亜鉛 (mg)
    val magnesiumMg: Double = 0.0,           // マグネシウム (mg)
    val vitaminAMcg: Double = 0.0,           // ビタミンA (μg)
    val vitaminDMcg: Double = 0.0,           // ビタミンD (μg)
    val vitaminEMg: Double = 0.0,            // ビタミンE (mg)
    val vitaminKMcg: Double = 0.0,           // ビタミンK (μg)
    val vitaminB1Mg: Double = 0.0,           // ビタミンB1 / チアミン (mg)
    val vitaminB2Mg: Double = 0.0,           // ビタミンB2 / リボフラビン (mg)
    val vitaminB6Mg: Double = 0.0,           // ビタミンB6 (mg)
    val vitaminB12Mcg: Double = 0.0,         // ビタミンB12 (μg)
    val niacinMg: Double = 0.0,              // ナイアシン (mg)
    val folateMcg: Double = 0.0,             // 葉酸 (μg)
    val pantothenicAcidMg: Double = 0.0,     // パントテン酸 (mg)
    val biotinMcg: Double = 0.0,             // ビオチン (μg)
    val vitaminCMg: Double = 0.0,            // ビタミンC (mg)
    val caffeineMg: Double = 0.0,            // カフェイン (mg)
    val phosphorusMg: Double = 0.0,          // リン (mg)
    val copperMg: Double = 0.0,              // 銅 (mg)
    val manganeseMg: Double = 0.0,           // マンガン (mg)
    val seleniumMcg: Double = 0.0,           // セレン (μg)
    val iodineMcg: Double = 0.0,             // ヨウ素 (μg)
    val chromiumMcg: Double = 0.0,           // クロム (μg)
    val molybdenumMcg: Double = 0.0          // モリブデン (μg)
)

@Serializable
data class SleepStageSummary(
    val deepMinutes: Long = 0L,
    val lightMinutes: Long = 0L,
    val remMinutes: Long = 0L,
    val awakeMinutes: Long = 0L
)

@Serializable
data class StepItem(
    val startTime: String,
    val endTime: String,
    val count: Long,
    val sourceApp: String
)

@Serializable
data class HeartRateSampleItem(
    val time: String,
    val beatsPerMinute: Long
)

@Serializable
data class SleepSessionItem(
    val title: String? = null,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Long,
    val sourceApp: String
)

@Serializable
data class WeightRecordItem(
    val time: String,
    val weightKg: Double,
    val sourceApp: String
)

@Serializable
data class BloodPressureItem(
    val time: String,
    val systolicMmHg: Double,
    val diastolicMmHg: Double,
    val bodyPosition: String? = null,
    val sourceApp: String
)

@Serializable
data class NutritionItem(
    val recordId: String? = null,
    val name: String? = null,
    val startTime: String,
    val endTime: String,
    val energyKcal: Double = 0.0,
    val proteinGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val carbsGrams: Double = 0.0,
    val mealType: String? = null,
    val sourceApp: String,
    // 詳細栄養素
    val dietaryFiberGrams: Double = 0.0,     // 食物繊維 (g)
    val sugarGrams: Double = 0.0,            // 糖質 (g)
    val saturatedFatGrams: Double = 0.0,     // 飽和脂肪酸 (g)
    val transFatGrams: Double = 0.0,         // トランス脂肪酸 (g)
    val cholesterolMg: Double = 0.0,         // コレステロール (mg)
    val sodiumMg: Double = 0.0,              // ナトリウム (mg)
    val saltGrams: Double = 0.0,             // 食塩相当量 (g)
    val potassiumMg: Double = 0.0,           // カリウム (mg)
    val calciumMg: Double = 0.0,             // カルシウム (mg)
    val ironMg: Double = 0.0,                // 鉄分 (mg)
    val zincMg: Double = 0.0,                // 亜鉛 (mg)
    val magnesiumMg: Double = 0.0,           // マグネシウム (mg)
    val vitaminAMcg: Double = 0.0,           // ビタミンA (μg)
    val vitaminDMcg: Double = 0.0,           // ビタミンD (μg)
    val vitaminEMg: Double = 0.0,            // ビタミンE (mg)
    val vitaminKMcg: Double = 0.0,           // ビタミンK (μg)
    val vitaminB1Mg: Double = 0.0,           // ビタミンB1 / チアミン (mg)
    val vitaminB2Mg: Double = 0.0,           // ビタミンB2 / リボフラビン (mg)
    val vitaminB6Mg: Double = 0.0,           // ビタミンB6 (mg)
    val vitaminB12Mcg: Double = 0.0,         // ビタミンB12 (μg)
    val niacinMg: Double = 0.0,              // ナイアシン (mg)
    val folateMcg: Double = 0.0,             // 葉酸 (μg)
    val pantothenicAcidMg: Double = 0.0,     // パントテン酸 (mg)
    val biotinMcg: Double = 0.0,             // ビオチン (μg)
    val vitaminCMg: Double = 0.0,            // ビタミンC (mg)
    val caffeineMg: Double = 0.0,            // カフェイン (mg)
    val phosphorusMg: Double = 0.0,          // リン (mg)
    val copperMg: Double = 0.0,              // 銅 (mg)
    val manganeseMg: Double = 0.0,           // マンガン (mg)
    val seleniumMcg: Double = 0.0,           // セレン (μg)
    val iodineMcg: Double = 0.0,             // ヨウ素 (μg)
    val chromiumMcg: Double = 0.0,           // クロム (μg)
    val molybdenumMcg: Double = 0.0          // モリブデン (μg)
)

/**
 * Webhook エクスポート用 JSON ペイロード
 */
@Serializable
data class HealthSyncPayload(
    val timestamp: String,              // ISO-8601 送信時刻
    val deviceId: String? = null,
    val syncType: String,               // "MANUAL" or "BACKGROUND_WORKER"
    val summary: HealthSummary,
    val recentSummaries: List<HealthSummary> = emptyList(), // 直近過去分サマリー（最大7日）
    val detailedRecords: DetailedRecords? = null
)

@Serializable
data class DetailedRecords(
    val stepItems: List<StepItem> = emptyList(),
    val sleepSessions: List<SleepSessionItem> = emptyList(),
    val weightRecords: List<WeightRecordItem> = emptyList(),
    val heartRateSamples: List<HeartRateSampleItem> = emptyList(),
    val bloodPressureRecords: List<BloodPressureItem> = emptyList(),
    val nutritionRecords: List<NutritionItem> = emptyList()
)

/**
 * 同期履歴ログアイテム
 */
@Serializable
data class SyncLogItem(
    val id: String,
    val timestamp: String,
    val isSuccess: Boolean,
    val syncType: String,
    val statusCode: Int? = null,
    val message: String,
    val recordsCountSummary: String
)
