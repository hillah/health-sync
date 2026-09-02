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

    val basePermissions = setOf(
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

    @OptIn(androidx.health.connect.client.feature.ExperimentalFeatureAvailabilityApi::class)
    val permissions: Set<String>
        get() {
            val client = healthConnectClient ?: return basePermissions
            val perms = basePermissions.toMutableSet()
            try {
                if (client.features.getFeatureStatus(androidx.health.connect.client.HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) == androidx.health.connect.client.HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
                    perms.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
                }
            } catch (e: Exception) {
                Log.w("HealthConnectManager", "Error checking FEATURE_READ_HEALTH_DATA_IN_BACKGROUND", e)
            }
            try {
                if (client.features.getFeatureStatus(androidx.health.connect.client.HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) == androidx.health.connect.client.HealthConnectFeatures.FEATURE_STATUS_AVAILABLE) {
                    perms.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
                }
            } catch (e: Exception) {
                Log.w("HealthConnectManager", "Error checking FEATURE_READ_HEALTH_DATA_HISTORY", e)
            }
            return perms
        }

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

        // 6. 栄養摂取 (合計値 & 食事区分別 & 詳細栄養素)
        var dietaryEnergy = 0.0
        var breakfastCal = 0.0
        var lunchCal = 0.0
        var dinnerCal = 0.0
        var snackCal = 0.0
        var dietaryProtein = 0.0
        var dietaryFat = 0.0
        var dietaryCarbs = 0.0
        var dietaryFiber = 0.0
        var sugar = 0.0
        var saturatedFat = 0.0
        var transFat = 0.0
        var cholesterol = 0.0
        var sodium = 0.0
        var potassium = 0.0
        var calcium = 0.0
        var iron = 0.0
        var zinc = 0.0
        var magnesium = 0.0
        var vitaminA = 0.0
        var vitaminD = 0.0
        var vitaminE = 0.0
        var vitaminK = 0.0
        var vitaminB1 = 0.0
        var vitaminB2 = 0.0
        var vitaminB6 = 0.0
        var vitaminB12 = 0.0
        var niacin = 0.0
        var folate = 0.0
        var pantothenicAcid = 0.0
        var biotin = 0.0
        var vitaminC = 0.0
        var caffeine = 0.0
        var phosphorus = 0.0
        var copper = 0.0
        var manganese = 0.0
        var selenium = 0.0
        var iodine = 0.0
        var chromium = 0.0
        var molybdenum = 0.0

        try {
            val nutritionRecords = getNutritionRecords(startTime, endTime)
            for (nr in nutritionRecords) {
                val cal = nr.energy?.inKilocalories ?: 0.0
                dietaryEnergy += cal
                dietaryProtein += nr.protein?.inGrams ?: 0.0
                dietaryFat += nr.totalFat?.inGrams ?: 0.0
                dietaryCarbs += nr.totalCarbohydrate?.inGrams ?: 0.0
                dietaryFiber += nr.dietaryFiber?.inGrams ?: 0.0
                sugar += nr.sugar?.inGrams ?: 0.0
                saturatedFat += nr.saturatedFat?.inGrams ?: 0.0
                transFat += nr.transFat?.inGrams ?: 0.0
                cholesterol += nr.cholesterol?.inMilligrams ?: 0.0
                sodium += nr.sodium?.inMilligrams ?: 0.0
                potassium += nr.potassium?.inMilligrams ?: 0.0
                calcium += nr.calcium?.inMilligrams ?: 0.0
                iron += nr.iron?.inMilligrams ?: 0.0
                zinc += nr.zinc?.inMilligrams ?: 0.0
                magnesium += nr.magnesium?.inMilligrams ?: 0.0
                vitaminA += nr.vitaminA?.inMicrograms ?: 0.0
                vitaminD += nr.vitaminD?.inMicrograms ?: 0.0
                vitaminE += nr.vitaminE?.inMilligrams ?: 0.0
                vitaminK += nr.vitaminK?.inMicrograms ?: 0.0
                vitaminB1 += nr.thiamin?.inMilligrams ?: 0.0
                vitaminB2 += nr.riboflavin?.inMilligrams ?: 0.0
                vitaminB6 += nr.vitaminB6?.inMilligrams ?: 0.0
                vitaminB12 += nr.vitaminB12?.inMicrograms ?: 0.0
                niacin += nr.niacin?.inMilligrams ?: 0.0
                folate += nr.folate?.inMicrograms ?: 0.0
                pantothenicAcid += nr.pantothenicAcid?.inMilligrams ?: 0.0
                biotin += nr.biotin?.inMicrograms ?: 0.0
                vitaminC += nr.vitaminC?.inMilligrams ?: 0.0
                caffeine += nr.caffeine?.inMilligrams ?: 0.0
                phosphorus += nr.phosphorus?.inMilligrams ?: 0.0
                copper += nr.copper?.inMilligrams ?: 0.0
                manganese += nr.manganese?.inMilligrams ?: 0.0
                selenium += nr.selenium?.inMicrograms ?: 0.0
                iodine += nr.iodine?.inMicrograms ?: 0.0
                chromium += nr.chromium?.inMicrograms ?: 0.0
                molybdenum += nr.molybdenum?.inMicrograms ?: 0.0

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

        val saltGrams = sodium * 2.54 / 1000.0

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
            dietaryCarbsGrams = dietaryCarbs,
            dietaryFiberGrams = dietaryFiber,
            sugarGrams = sugar,
            saturatedFatGrams = saturatedFat,
            transFatGrams = transFat,
            cholesterolMg = cholesterol,
            sodiumMg = sodium,
            saltGrams = saltGrams,
            potassiumMg = potassium,
            calciumMg = calcium,
            ironMg = iron,
            zincMg = zinc,
            magnesiumMg = magnesium,
            vitaminAMcg = vitaminA,
            vitaminDMcg = vitaminD,
            vitaminEMg = vitaminE,
            vitaminKMcg = vitaminK,
            vitaminB1Mg = vitaminB1,
            vitaminB2Mg = vitaminB2,
            vitaminB6Mg = vitaminB6,
            vitaminB12Mcg = vitaminB12,
            niacinMg = niacin,
            folateMcg = folate,
            pantothenicAcidMg = pantothenicAcid,
            biotinMcg = biotin,
            vitaminCMg = vitaminC,
            caffeineMg = caffeine,
            phosphorusMg = phosphorus,
            copperMg = copper,
            manganeseMg = manganese,
            seleniumMcg = selenium,
            iodineMcg = iodine,
            chromiumMcg = chromium,
            molybdenumMcg = molybdenum
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

        val nutritionList = getNutritionRecords(startOfDay.minus(Duration.ofDays(7)), endOfDay)
            .distinctBy { it.metadata.id }
            .map {
                val sodiumMg = it.sodium?.inMilligrams ?: 0.0
                val saltGrams = sodiumMg * 2.54 / 1000.0

                NutritionItem(
                    recordId = it.metadata.id,
                    name = it.name,
                    startTime = isoFormatter.format(it.startTime),
                    endTime = isoFormatter.format(it.endTime),
                energyKcal = it.energy?.inKilocalories ?: 0.0,
                proteinGrams = it.protein?.inGrams ?: 0.0,
                fatGrams = it.totalFat?.inGrams ?: 0.0,
                carbsGrams = it.totalCarbohydrate?.inGrams ?: 0.0,
                mealType = it.mealType.toString(),
                sourceApp = it.metadata.dataOrigin.packageName,
                dietaryFiberGrams = it.dietaryFiber?.inGrams ?: 0.0,
                sugarGrams = it.sugar?.inGrams ?: 0.0,
                saturatedFatGrams = it.saturatedFat?.inGrams ?: 0.0,
                transFatGrams = it.transFat?.inGrams ?: 0.0,
                cholesterolMg = it.cholesterol?.inMilligrams ?: 0.0,
                sodiumMg = sodiumMg,
                saltGrams = saltGrams,
                potassiumMg = it.potassium?.inMilligrams ?: 0.0,
                calciumMg = it.calcium?.inMilligrams ?: 0.0,
                ironMg = it.iron?.inMilligrams ?: 0.0,
                zincMg = it.zinc?.inMilligrams ?: 0.0,
                magnesiumMg = it.magnesium?.inMilligrams ?: 0.0,
                vitaminAMcg = it.vitaminA?.inMicrograms ?: 0.0,
                vitaminDMcg = it.vitaminD?.inMicrograms ?: 0.0,
                vitaminEMg = it.vitaminE?.inMilligrams ?: 0.0,
                vitaminKMcg = it.vitaminK?.inMicrograms ?: 0.0,
                vitaminB1Mg = it.thiamin?.inMilligrams ?: 0.0,
                vitaminB2Mg = it.riboflavin?.inMilligrams ?: 0.0,
                vitaminB6Mg = it.vitaminB6?.inMilligrams ?: 0.0,
                vitaminB12Mcg = it.vitaminB12?.inMicrograms ?: 0.0,
                niacinMg = it.niacin?.inMilligrams ?: 0.0,
                folateMcg = it.folate?.inMicrograms ?: 0.0,
                pantothenicAcidMg = it.pantothenicAcid?.inMilligrams ?: 0.0,
                biotinMcg = it.biotin?.inMicrograms ?: 0.0,
                vitaminCMg = it.vitaminC?.inMilligrams ?: 0.0,
                caffeineMg = it.caffeine?.inMilligrams ?: 0.0,
                phosphorusMg = it.phosphorus?.inMilligrams ?: 0.0,
                copperMg = it.copper?.inMilligrams ?: 0.0,
                manganeseMg = it.manganese?.inMilligrams ?: 0.0,
                seleniumMcg = it.selenium?.inMicrograms ?: 0.0,
                iodineMcg = it.iodine?.inMicrograms ?: 0.0,
                chromiumMcg = it.chromium?.inMicrograms ?: 0.0,
                molybdenumMcg = it.molybdenum?.inMicrograms ?: 0.0
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
