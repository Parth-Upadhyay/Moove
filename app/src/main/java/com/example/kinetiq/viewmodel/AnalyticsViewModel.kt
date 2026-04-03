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
        if (sessions.isEmpty()) return OverallSummary()

        val sortedSessions = sessions.sortedBy { it.timestamp_end }
        val groupedByExercise = sessions.groupBy { it.exercise }
        
        var totalOptImprovement = 0.0
        var totalPainReduction = 0.0
        var exercisesWithData = 0
        
        var latestOptimalitySum = 0.0
        var latestPainSum = 0.0
        var activeExercises = 0

        groupedByExercise.forEach { (_, exerciseSessions) ->
            val sorted = exerciseSessions.sortedBy { it.timestamp_end }
            val last = sorted.last()
            
            latestOptimalitySum += last.results.peak_rom_degrees
            val lastPain = last.pain_log.mapNotNull { it.level.toDoubleOrNull() }.average().takeIf { !it.isNaN() } ?: last.pre_session.pain_score.toDouble()
            latestPainSum += lastPain
            activeExercises++

            if (exerciseSessions.size >= 2) {
                val first = sorted.first()
                totalOptImprovement += (last.results.peak_rom_degrees - first.results.peak_rom_degrees)
                
                val firstPain = first.pain_log.mapNotNull { it.level.toDoubleOrNull() }.average().takeIf { !it.isNaN() } ?: first.pre_session.pain_score.toDouble()
                totalPainReduction += (firstPain - lastPain)
                exercisesWithData++
            }
        }

        val avgOptImp = if (exercisesWithData > 0) totalOptImprovement / exercisesWithData else 0.0
        val avgPainRed = if (exercisesWithData > 0) totalPainReduction / exercisesWithData else 0.0
        
        val weightedScore = if (activeExercises > 0) {
            val avgLatestOpt = latestOptimalitySum / activeExercises
            val avgLatestPain = latestPainSum / activeExercises
            val painScore = ((10.0 - avgLatestPain).coerceIn(0.0, 10.0) / 10.0) * 100.0
            (avgLatestOpt * 0.6) + (painScore * 0.4)
        } else 0.0

        val lastDate = try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(sortedSessions.last().timestamp_end)
        } catch (e: Exception) { null }

        val adherence = if (patientData?.clinicalPrescription != null) {
            val prescribedCount = patientData.clinicalPrescription!!.exercises.count { it.isActive }
            if (prescribedCount > 0) {
                val last7Days = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
                val recentSessions = sessions.filter { 
                    try {
                        val d = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(it.timestamp_end)
                        d?.after(last7Days) == true
                    } catch (e: Exception) { false }
                }
                val completedPrescribed = patientData.clinicalPrescription!!.exercises
                    .filter { it.isActive }
                    .count { p -> recentSessions.any { s -> s.exercise == p.exerciseId } }
                completedPrescribed.toFloat() / prescribedCount.toFloat()
            } else 1.0f
        } else (sessions.lastOrNull()?.adherence?.adherence_rate_30d ?: 0f)

        return OverallSummary(
            weightedRecoveryPercentage = weightedScore,
            avgOptimalityImprovement = avgOptImp,
            avgPainReduction = avgPainRed,
            totalSessions = sessions.size,
            adherenceRate = adherence,
            lastSessionDate = lastDate
        )
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
