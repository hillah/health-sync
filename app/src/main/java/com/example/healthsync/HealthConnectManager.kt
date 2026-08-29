package com.example.healthsync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.healthsync.data.model.*
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class HealthConnectManager(private val context: Context) {

    val healthConnectClient: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class)
    )

    fun getSdkStatus(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    suspend fun getGrantedPermissions(): Set<String> {
        val client = healthConnectClient ?: return emptySet()
        return client.permissionController.getGrantedPermissions()
    }

    /**
     * 指定日の HealthSummary を取得
     */
    suspend fun getHealthSummaryForDay(date: LocalDate): HealthSummary {
        val client = healthConnectClient ?: return HealthSummary(date.toString())
        val zone = ZoneId.systemDefault()
        val startTime = date.atStartOfDay(zone).toInstant()
        val endTime = date.plusDays(1).atStartOfDay(zone).toInstant()

        // 1. 歩数 & カロリー集計
        var steps = 0L
        var totalCalories = 0.0
        var activeCalories = 0.0
        var avgHeartRate: Long? = null
        var minHeartRate: Long? = null
        var maxHeartRate: Long? = null

        try {
            val aggregateResponse = client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        TotalCaloriesBurnedRecord.ENERGY_TOTAL,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.BPM_MIN,
                        HeartRateRecord.BPM_MAX
                    ),
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            steps = aggregateResponse[StepsRecord.COUNT_TOTAL] ?: 0L
            totalCalories = aggregateResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0
            activeCalories = aggregateResponse[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            avgHeartRate = aggregateResponse[HeartRateRecord.BPM_AVG]
            minHeartRate = aggregateResponse[HeartRateRecord.BPM_MIN]
            maxHeartRate = aggregateResponse[HeartRateRecord.BPM_MAX]
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error aggregating metrics for $date", e)
        }

        // 2. 睡眠時間集計
        var sleepMinutes = 0L
        var deepSleep = 0L
        var lightSleep = 0L
        var remSleep = 0L
        var awakeSleep = 0L

        try {
            val sleepSessions = getSleepSessions(startTime, endTime)
            sleepMinutes = sleepSessions.sumOf {
                Duration.between(it.startTime, it.endTime).toMinutes()
            }

            for (session in sleepSessions) {
                for (stage in session.stages) {
                    val stageMin = Duration.between(stage.startTime, stage.endTime).toMinutes()
                    when (stage.stage) {
                        SleepSessionRecord.STAGE_TYPE_DEEP -> deepSleep += stageMin
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> lightSleep += stageMin
                        SleepSessionRecord.STAGE_TYPE_REM -> remSleep += stageMin
                        SleepSessionRecord.STAGE_TYPE_AWAKE -> awakeSleep += stageMin
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error fetching sleep for $date", e)
        }

        // 3. 最新体重 & 体脂肪率
        var latestWeight: Double? = null
        var latestBodyFat: Double? = null
        try {
            val weights = getWeightRecords(startTime, endTime)
            latestWeight = weights.maxByOrNull { it.time }?.weight?.inKilograms

            val bodyFats = getBodyFatRecords(startTime, endTime)
            latestBodyFat = bodyFats.maxByOrNull { it.time }?.percentage?.value
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error fetching weight/fat for $date", e)
        }

        // 4. 安静時心拍数
        var restingHeartRate: Long? = null
        try {
            val resting = getRestingHeartRateRecords(startTime, endTime)
            restingHeartRate = resting.maxByOrNull { it.time }?.beatsPerMinute
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error fetching resting HR for $date", e)
        }

        // 5. 血圧 (最新測定値)
        var latestSystolic: Double? = null
        var latestDiastolic: Double? = null
        try {
            val bloodPressures = getBloodPressureRecords(startTime, endTime)
            val latestBP = bloodPressures.maxByOrNull { it.time }
            latestSystolic = latestBP?.systolic?.inMillimetersOfMercury
            latestDiastolic = latestBP?.diastolic?.inMillimetersOfMercury
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error fetching blood pressure for $date", e)
        }

        // 6. 栄養摂取 (合計値 & 食事区分別)
        var dietaryEnergy = 0.0
        var breakfastCal = 0.0
        var lunchCal = 0.0
        var dinnerCal = 0.0
        var snackCal = 0.0
        var dietaryProtein = 0.0
        var dietaryFat = 0.0
        var dietaryCarbs = 0.0
        try {
            val nutritionRecords = getNutritionRecords(startTime, endTime)
            for (nr in nutritionRecords) {
                val cal = nr.energy?.inKilocalories ?: 0.0
                dietaryEnergy += cal
                dietaryProtein += nr.protein?.inGrams ?: 0.0
                dietaryFat += nr.totalFat?.inGrams ?: 0.0
                dietaryCarbs += nr.totalCarbohydrate?.inGrams ?: 0.0

                when (nr.mealType) {
                    MealType.MEAL_TYPE_BREAKFAST, 1 -> breakfastCal += cal
                    MealType.MEAL_TYPE_LUNCH, 2 -> lunchCal += cal
                    MealType.MEAL_TYPE_DINNER, 3 -> dinnerCal += cal
                    MealType.MEAL_TYPE_SNACK, 4 -> snackCal += cal
                }
            }
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error fetching nutrition for $date", e)
        }

        return HealthSummary(
            date = date.toString(),
            steps = steps,
            activeCaloriesKcal = activeCalories,
            totalCaloriesKcal = totalCalories,
            sleepDurationMinutes = sleepMinutes,
            sleepStages = SleepStageSummary(
                deepMinutes = deepSleep,
                lightMinutes = lightSleep,
                remMinutes = remSleep,
                awakeMinutes = awakeSleep
            ),
            latestWeightKg = latestWeight,
            latestBodyFatPercent = latestBodyFat,
            avgHeartRateBpm = avgHeartRate,
            minHeartRateBpm = minHeartRate,
            maxHeartRateBpm = maxHeartRate,
            restingHeartRateBpm = restingHeartRate,
            latestSystolicMmHg = latestSystolic,
            latestDiastolicMmHg = latestDiastolic,
            dietaryEnergyKcal = dietaryEnergy,
            breakfastCaloriesKcal = breakfastCal,
            lunchCaloriesKcal = lunchCal,
            dinnerCaloriesKcal = dinnerCal,
            snackCaloriesKcal = snackCal,
            dietaryProteinGrams = dietaryProtein,
            dietaryFatGrams = dietaryFat,
            dietaryCarbsGrams = dietaryCarbs
        )
    }

    /**
     * 直近 N 日間（デフォルト7日間）の HealthSummary リストを取得（グラフ用）
     */
    suspend fun getDailyHealthSummaries(days: Int = 7): List<HealthSummary> {
        val today = LocalDate.now()
        val list = mutableListOf<HealthSummary>()
        for (i in (days - 1) downTo 0) {
            val date = today.minusDays(i.toLong())
            list.add(getHealthSummaryForDay(date))
        }
        return list
    }

    // -------------------------------------------------------------
    // 個別レコード取得メソッド群
    // -------------------------------------------------------------

    suspend fun getStepRecords(startTime: Instant, endTime: Instant): List<StepsRecord> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading steps", e)
            emptyList()
        }
    }

    suspend fun getSleepSessions(startTime: Instant, endTime: Instant): List<SleepSessionRecord> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading sleep", e)
            emptyList()
        }
    }

    suspend fun getWeightRecords(startTime: Instant, endTime: Instant): List<WeightRecord> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading weight", e)
            emptyList()
        }
    }

    suspend fun getBodyFatRecords(startTime: Instant, endTime: Instant): List<BodyFatRecord> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading body fat", e)
            emptyList()
        }
    }

    suspend fun getHeartRateRecords(startTime: Instant, endTime: Instant): List<HeartRateRecord> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading heart rate", e)
            emptyList()
        }
    }

    suspend fun getRestingHeartRateRecords(startTime: Instant, endTime: Instant): List<RestingHeartRateRecord> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading resting heart rate", e)
            emptyList()
        }
    }

    suspend fun getBloodPressureRecords(startTime: Instant, endTime: Instant): List<BloodPressureRecord> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = BloodPressureRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading blood pressure", e)
            emptyList()
        }
    }

    suspend fun getNutritionRecords(startTime: Instant, endTime: Instant): List<NutritionRecord> {
        val client = healthConnectClient ?: return emptyList()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = NutritionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Error reading nutrition", e)
            emptyList()
        }
    }

    /**
     * Webhook 送信用に本日分の詳細レコード一式を抽出・変換
     */
    suspend fun getTodayDetailedRecords(): DetailedRecords {
        val now = ZonedDateTime.now()
        val startOfDay = now.toLocalDate().atStartOfDay(now.zone).toInstant()
        val endOfDay = now.toInstant()
        val isoFormatter = DateTimeFormatter.ISO_INSTANT

        val steps = getStepRecords(startOfDay, endOfDay).map {
            StepItem(
                startTime = isoFormatter.format(it.startTime),
                endTime = isoFormatter.format(it.endTime),
                count = it.count,
                sourceApp = it.metadata.dataOrigin.packageName
            )
        }

        val sleep = getSleepSessions(startOfDay.minus(Duration.ofHours(12)), endOfDay).map {
            SleepSessionItem(
                title = it.title,
                startTime = isoFormatter.format(it.startTime),
                endTime = isoFormatter.format(it.endTime),
                durationMinutes = Duration.between(it.startTime, it.endTime).toMinutes(),
                sourceApp = it.metadata.dataOrigin.packageName
            )
        }

        val weight = getWeightRecords(startOfDay.minus(Duration.ofDays(7)), endOfDay).map {
            WeightRecordItem(
                time = isoFormatter.format(it.time),
                weightKg = it.weight.inKilograms,
                sourceApp = it.metadata.dataOrigin.packageName
            )
        }

        val hrList = mutableListOf<HeartRateSampleItem>()
        getHeartRateRecords(startOfDay, endOfDay).forEach { hrRecord ->
            hrRecord.samples.forEach { sample ->
                hrList.add(
                    HeartRateSampleItem(
                        time = isoFormatter.format(sample.time),
                        beatsPerMinute = sample.beatsPerMinute
                    )
                )
            }
        }

        val bpList = getBloodPressureRecords(startOfDay.minus(Duration.ofDays(7)), endOfDay).map {
            BloodPressureItem(
                time = isoFormatter.format(it.time),
                systolicMmHg = it.systolic.inMillimetersOfMercury,
                diastolicMmHg = it.diastolic.inMillimetersOfMercury,
                bodyPosition = it.bodyPosition.toString(),
                sourceApp = it.metadata.dataOrigin.packageName
            )
        }

        val nutritionList = getNutritionRecords(startOfDay, endOfDay).map {
            NutritionItem(
                name = it.name,
                startTime = isoFormatter.format(it.startTime),
                endTime = isoFormatter.format(it.endTime),
                energyKcal = it.energy?.inKilocalories ?: 0.0,
                proteinGrams = it.protein?.inGrams ?: 0.0,
                fatGrams = it.totalFat?.inGrams ?: 0.0,
                carbsGrams = it.totalCarbohydrate?.inGrams ?: 0.0,
                mealType = it.mealType.toString(),
                sourceApp = it.metadata.dataOrigin.packageName
            )
        }

        return DetailedRecords(
            stepItems = steps,
            sleepSessions = sleep,
            weightRecords = weight,
            heartRateSamples = hrList,
            bloodPressureRecords = bpList,
            nutritionRecords = nutritionList
        )
    }
}
