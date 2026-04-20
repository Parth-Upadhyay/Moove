package com.example.kinetiq.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kinetiq.models.Patient
import com.example.kinetiq.models.SessionResult
import com.example.kinetiq.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

enum class TimeFilter {
    LAST_3_DAYS, LAST_WEEK, LAST_MONTH, LAST_3_MONTHS, ALL
}

data class ExerciseTrendPoint(
    val date: Date,
    val optimality: Double,
    val pain: Double
)

data class OverallSummary(
    val weightedRecoveryPercentage: Double = 0.0,
    val avgOptimalityImprovement: Double = 0.0,
    val avgPainReduction: Double = 0.0,
    val totalSessions: Int = 0,
    val adherenceRate: Float = 0f,
    val adherenceBreakdown: Map<String, Pair<Int, Int>> = emptyMap(), // exerciseId -> Pair(setsCompleted, setsPrescribed)
    val exercisesLeftToday: Int = 0,
    val lastSessionDate: Date? = null
)

data class AnalyticsUiState(
    val allTrendData: Map<String, List<ExerciseTrendPoint>> = emptyMap(),
    val filteredTrendData: Map<String, List<ExerciseTrendPoint>> = emptyMap(),
    val dailySessions: List<SessionResult> = emptyList(),
    val overallSummary: OverallSummary = OverallSummary(),
    val loadingExercises: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val comparisons: Map<String, String> = emptyMap(),
    val selectedFilter: TimeFilter = TimeFilter.ALL
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val repository: AnalyticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState = _uiState.asStateFlow()

    fun loadExerciseProgress(patientId: String, exerciseType: String) {
        repository.getExerciseProgress(patientId, exerciseType)
            .onStart { _uiState.update { it.copy(loadingExercises = it.loadingExercises + exerciseType) } }
            .onEach { result ->
                result.onSuccess { data ->
                    val comparison = calculate3DayComparison(data)
                    _uiState.update { 
                        it.copy(
                            allTrendData = it.allTrendData + (exerciseType to data),
                            filteredTrendData = it.filteredTrendData + (exerciseType to filterData(data, it.selectedFilter)),
                            loadingExercises = it.loadingExercises - exerciseType,
                            comparisons = it.comparisons + (exerciseType to comparison)
                        ) 
                    }
                }.onFailure { err ->
                    _uiState.update { it.copy(error = err.message, loadingExercises = it.loadingExercises - exerciseType) }
                }
            }.launchIn(viewModelScope)
    }

    fun setTimeFilter(filter: TimeFilter) {
        _uiState.update { state ->
            val newFilteredData = state.allTrendData.mapValues { (_, data) ->
                filterData(data, filter)
            }
            state.copy(
                selectedFilter = filter,
                filteredTrendData = newFilteredData
            )
        }
    }

    private fun filterData(data: List<ExerciseTrendPoint>, filter: TimeFilter): List<ExerciseTrendPoint> {
        if (filter == TimeFilter.ALL) return data
        
        val calendar = Calendar.getInstance()
        when (filter) {
            TimeFilter.LAST_3_DAYS -> calendar.add(Calendar.DAY_OF_YEAR, -3)
            TimeFilter.LAST_WEEK -> calendar.add(Calendar.WEEK_OF_YEAR, -1)
            TimeFilter.LAST_MONTH -> calendar.add(Calendar.MONTH, -1)
            TimeFilter.LAST_3_MONTHS -> calendar.add(Calendar.MONTH, -3)
            else -> return data
        }
        val cutoffDate = calendar.time
        return data.filter { it.date.after(cutoffDate) }
    }

    fun loadOverallSummary(patientId: String, patientData: Patient? = null) {
        repository.getAllSessions(patientId)
            .onStart { _uiState.update { it.copy(isLoading = true) } }
            .onEach { result ->
                result.onSuccess { sessions ->
                    val summary = calculateOverallSummary(sessions, patientData)
                    _uiState.update { it.copy(overallSummary = summary, isLoading = false) }
                }.onFailure { err ->
                    _uiState.update { it.copy(error = err.message, isLoading = false) }
                }
            }.launchIn(viewModelScope)
    }

    private fun calculateOverallSummary(sessions: List<SessionResult>, patientData: Patient?): OverallSummary {
        if (sessions.isEmpty() && patientData?.clinicalPrescription == null) return OverallSummary()

        val groupedByExercise = sessions.groupBy { it.exercise }
        
        var totalOptImprovement = 0.0
        var totalPainReduction = 0.0
        var exercisesWithTrendData = 0
        
        var latestOptimalitySum = 0.0
        var latestPainSum = 0.0
        var activeExercisesCount = 0

        groupedByExercise.forEach { (_, exerciseSessions) ->
            val sorted = exerciseSessions.sortedBy { it.timestamp_end }
            val last = sorted.last()
            
            latestOptimalitySum += last.results.peak_rom_degrees
            val lastPain = last.pain_log.mapNotNull { it.level.toDoubleOrNull() }.average().takeIf { !it.isNaN() } ?: last.pre_session.pain_score.toDouble()
            latestPainSum += lastPain
            activeExercisesCount++

            if (exerciseSessions.size >= 2) {
                val first = sorted.first()
                totalOptImprovement += (last.results.peak_rom_degrees - first.results.peak_rom_degrees)
                
                val firstPain = first.pain_log.mapNotNull { it.level.toDoubleOrNull() }.average().takeIf { !it.isNaN() } ?: first.pre_session.pain_score.toDouble()
                totalPainReduction += (firstPain - lastPain)
                exercisesWithTrendData++
            }
        }

        val avgOptImp = if (exercisesWithTrendData > 0) totalOptImprovement / exercisesWithTrendData else 0.0
        val avgPainRed = if (exercisesWithTrendData > 0) totalPainReduction / exercisesWithTrendData else 0.0
        
        val weightedScore = if (activeExercisesCount > 0) {
            val avgLatestOpt = latestOptimalitySum / activeExercisesCount
            val avgLatestPain = latestPainSum / activeExercisesCount
            val painScore = ((10.0 - avgLatestPain).coerceIn(0.0, 10.0) / 10.0) * 100.0
            (avgLatestOpt * 0.6) + (painScore * 0.4)
        } else 0.0

        // Weekly Adherence Logic - Last 7 Days
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val oneWeekAgo = calendar.time
        
        val recentSessions = sessions.filter { 
            parseIsoDate(it.timestamp_end)?.after(oneWeekAgo) == true 
        }

        val adherenceBreakdown = mutableMapOf<String, Pair<Int, Int>>()
        var totalCompletedSets = 0
        var totalPrescribedSets = 0

        val prescription = patientData?.clinicalPrescription
        val frequency = (prescription?.frequencyPerWeek ?: 7).coerceAtLeast(1)

        // Adherence is calculated against CURRENT prescription to show immediate goal progress
        // We remove the date filter that was blocking sessions done before a prescription update
        prescription?.exercises?.filter { it.isActive }?.forEach { prescribed ->
            val exerciseSessions = recentSessions.filter { it.exercise == prescribed.exerciseId }
            val completedSets = exerciseSessions.sumOf { it.results.sets_completed }
            
            // Weekly target = sets per day * frequency per week
            val targetSetsPerDay = prescribed.sets.coerceAtLeast(1)
            val weeklyTarget = targetSetsPerDay * frequency
            
            adherenceBreakdown[prescribed.exerciseId] = Pair(completedSets, weeklyTarget)
            totalCompletedSets += completedSets
            totalPrescribedSets += weeklyTarget
        }

        val adherenceRate = if (totalPrescribedSets > 0) {
            (totalCompletedSets.toFloat() / totalPrescribedSets.toFloat()).coerceIn(0f, 1f)
        } else 0f

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val sessionsToday = sessions.filter { 
            parseIsoDate(it.timestamp_end)?.after(today) == true 
        }.map { it.exercise }.toSet()
        
        val prescribedExercises = prescription?.exercises?.filter { it.isActive }?.map { it.exerciseId } ?: emptyList()
        val leftToday = prescribedExercises.count { it !in sessionsToday }

        val lastDate = if (sessions.isNotEmpty()) {
            parseIsoDate(sessions.sortedBy { it.timestamp_end }.last().timestamp_end)
        } else null

        return OverallSummary(
            weightedRecoveryPercentage = weightedScore,
            avgOptimalityImprovement = avgOptImp,
            avgPainReduction = avgPainRed,
            totalSessions = sessions.size,
            adherenceRate = adherenceRate,
            adherenceBreakdown = adherenceBreakdown,
            exercisesLeftToday = leftToday,
            lastSessionDate = lastDate
        )
    }

    private fun parseIsoDate(isoString: String): Date? {
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(isoString)
        } catch (e: Exception) {
            try {
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(isoString)
            } catch (e2: Exception) { null }
        }
    }

    private fun calculate3DayComparison(data: List<ExerciseTrendPoint>): String {
        if (data.size < 2) return "Insufficient data"
        
        val lastSession = data.last()
        val calendar = Calendar.getInstance()
        calendar.time = lastSession.date
        calendar.add(Calendar.DAY_OF_YEAR, -3)
        val threeDaysAgo = calendar.time
        
        val previousPoint = data.filter { it.date.before(threeDaysAgo) }.lastOrNull() 
            ?: data.first()
            
        val optDiff = lastSession.optimality - previousPoint.optimality
        val painDiff = lastSession.pain - previousPoint.pain
        
        val optStatus = if (optDiff >= 0) "improved by ${optDiff.toInt()}%" else "decreased by ${abs(optDiff).toInt()}%"
        val painStatus = if (painDiff < 0) "pain reduced" else if (painDiff > 0) "pain increased" else "stable pain"
        
        return "Vs last 3 days: Optimality $optStatus, $painStatus"
    }

    fun loadSessionsForDate(patientId: String, date: Date) {
        repository.getSessionsByDate(patientId, date)
            .onStart { _uiState.update { it.copy(isLoading = true, dailySessions = emptyList()) } }
            .onEach { result ->
                result.onSuccess { sessions ->
                    _uiState.update { it.copy(dailySessions = sessions, isLoading = false) }
                }.onFailure { err ->
                    _uiState.update { it.copy(error = err.message, isLoading = false) }
                }
            }.launchIn(viewModelScope)
    }
}
