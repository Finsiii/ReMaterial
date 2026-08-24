package com.rematerial.app.feature.analysis.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rematerial.app.core.model.AnalysisId
import com.rematerial.app.core.model.Availability
import com.rematerial.app.core.model.FieldId
import com.rematerial.app.core.model.InspectionValue
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.Observation
import com.rematerial.app.core.model.Result
import com.rematerial.app.core.model.UnitCode
import com.rematerial.app.core.model.ValueSource
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.CategoryPrediction
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.InitialAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InspectionFieldType
import com.rematerial.app.feature.analysis.domain.PhotoReference
import com.rematerial.app.feature.analysis.domain.RequestedField
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AnalysisStep { SCAN, PREVIEW, CONFIRM, INPUTS, RESULT }

data class AnalysisUiState(
    val step: AnalysisStep = AnalysisStep.SCAN,
    val analysisId: AnalysisId = AnalysisId("analysis-${UUID.randomUUID()}"),
    val photoUri: String? = null,
    val isManual: Boolean = false,
    val initial: InitialAnalysisResponse? = null,
    val prediction: CategoryPrediction? = null,
    val selectedCategory: MaterialCategory? = null,
    val values: Map<String, String> = emptyMap(),
    val result: CompletedAnalysisResponse? = null,
    val selectedOptionId: String? = null,
    val saved: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val gateway: AiAnalysisGateway,
) : ViewModel() {
    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    fun attachPhoto(uri: String) {
        _state.value = _state.value.copy(step = AnalysisStep.PREVIEW, photoUri = uri, isManual = false, error = null)
    }

    fun chooseManual(category: MaterialCategory) {
        _state.value = _state.value.copy(step = AnalysisStep.CONFIRM, selectedCategory = category, isManual = true, error = null)
        startInitial(category)
    }

    fun startPhotoAnalysis() = startInitial(null)

    private fun startInitial(manualCategory: MaterialCategory?) {
        if (_state.value.loading) return
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val current = _state.value
            val result = gateway.start(
                InitialAnalysisRequest(
                    analysisId = current.analysisId,
                    photo = current.photoUri?.let { PhotoReference(it, "image/jpeg", 1_024) },
                    manualCategory = manualCategory,
                ),
            )
            when (result) {
                is Result.Success -> {
                    val response = result.value
                    _state.value = _state.value.copy(
                        step = AnalysisStep.CONFIRM,
                        initial = response,
                        prediction = response.prediction,
                        selectedCategory = manualCategory ?: response.prediction.category,
                        loading = false,
                    )
                }
                is Result.Failure -> _state.value = _state.value.copy(loading = false, error = "Analisis belum tersedia. Coba lagi sebentar.")
            }
        }
    }

    fun setCategory(category: MaterialCategory) {
        _state.value = _state.value.copy(selectedCategory = category, error = null)
    }

    fun continueToInputs() {
        if (_state.value.selectedCategory == null) return
        val fields = _state.value.initial?.requestedFields.orEmpty()
        _state.value = _state.value.copy(
            step = AnalysisStep.INPUTS,
            values = _state.value.values + fields.associate { it.id.value to "" },
            error = null,
        )
    }

    fun updateValue(id: String, value: String) {
        _state.value = _state.value.copy(values = _state.value.values + (id to value), error = null)
    }

    fun submitInputs() {
        val current = _state.value
        val response = current.initial ?: return
        val category = current.selectedCategory ?: return
        val missing = response.requestedFields.filter { it.required && current.values[it.id.value].orEmpty().isBlank() }
        if (missing.isNotEmpty()) {
            _state.value = current.copy(error = "Lengkapi: ${missing.joinToString { it.label }}")
            return
        }
        val observations = response.requestedFields.map { field -> field.toObservation(current.values[field.id.value].orEmpty()) }
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = gateway.complete(CompletedAnalysisRequest(current.analysisId, category, observations))) {
                is Result.Success -> _state.value = _state.value.copy(
                    step = AnalysisStep.RESULT,
                    result = result.value,
                    selectedOptionId = result.value.productOptions.firstOrNull()?.optionId?.value,
                    loading = false,
                )
                is Result.Failure -> _state.value = _state.value.copy(loading = false, error = "Hasil belum bisa dibuat. Coba lagi.")
            }
        }
    }

    fun selectOption(id: String) { _state.value = _state.value.copy(selectedOptionId = id) }
    fun saveForMaking() { _state.value = _state.value.copy(saved = true) }
    fun reset() { _state.value = AnalysisUiState() }

    private fun RequestedField.toObservation(raw: String): Observation {
        if (raw.isBlank()) return Observation(id, availability = Availability.NOT_AVAILABLE, source = ValueSource.USER)
        val value = when (type) {
            InspectionFieldType.DECIMAL -> InspectionValue.Decimal(raw.replace(',', '.').toDoubleOrNull() ?: 0.0)
            InspectionFieldType.WHOLE -> InspectionValue.Whole(raw.toIntOrNull() ?: 0)
            InspectionFieldType.BOOLEAN -> InspectionValue.BooleanValue(raw == "true")
            InspectionFieldType.CHOICE -> InspectionValue.Choice(raw)
            InspectionFieldType.TEXT -> InspectionValue.Text(raw)
        }
        return Observation(id, value, unit, ValueSource.USER, Availability.PROVIDED)
    }
}
