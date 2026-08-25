package com.rematerial.app.feature.analysis.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rematerial.app.core.media.AnalysisMediaStore
import com.rematerial.app.core.model.AnalysisId
import com.rematerial.app.core.model.Availability
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.InspectionValue
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.Observation
import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import com.rematerial.app.core.model.ValueSource
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AnalysisConfirmation
import com.rematerial.app.feature.analysis.domain.AnalysisConfirmationPolicy
import com.rematerial.app.feature.analysis.domain.AnalysisFlowPhase
import com.rematerial.app.feature.analysis.domain.AnalysisInputValidator
import com.rematerial.app.feature.analysis.domain.AnalysisPersistenceSnapshot
import com.rematerial.app.feature.analysis.domain.AnalysisResponseValidator
import com.rematerial.app.feature.analysis.domain.AnalysisSession
import com.rematerial.app.feature.analysis.domain.AnalysisSessionRepository
import com.rematerial.app.feature.analysis.domain.CategoryPrediction
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisResponse
import com.rematerial.app.feature.analysis.domain.FieldAnswer
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.InitialAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InspectionFieldType
import com.rematerial.app.feature.analysis.domain.PhotoReference
import com.rematerial.app.feature.analysis.domain.RequestedField
import com.rematerial.app.feature.analysis.domain.SavedAnalysisIdea
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AnalysisStep { SCAN, PREVIEW, CONFIRM, INPUTS, RESULT, IDEAS }
enum class AnalysisMotionDirection { FORWARD, BACKWARD }

object AnalysisMotionPolicy {
    fun direction(from: AnalysisStep, to: AnalysisStep): AnalysisMotionDirection = AnalysisMotionDirection.FORWARD
    fun backDirection(from: AnalysisStep, to: AnalysisStep): AnalysisMotionDirection = AnalysisMotionDirection.BACKWARD
}

sealed interface AnalysisRetryAction {
    data class ImportGallery(val uri: String) : AnalysisRetryAction
    data class StartInitial(val manualCategory: MaterialCategory?) : AnalysisRetryAction
    data object Complete : AnalysisRetryAction
    data object SaveIdea : AnalysisRetryAction
    data object PersistPending : AnalysisRetryAction
    data object PersistCurrent : AnalysisRetryAction
}

data class AnalysisUiState(
    val step: AnalysisStep = AnalysisStep.SCAN,
    val analysisId: AnalysisId = AnalysisId("analysis-${UUID.randomUUID()}"),
    val photo: PhotoReference? = null,
    val isManual: Boolean = false,
    val initial: InitialAnalysisResponse? = null,
    val prediction: CategoryPrediction? = null,
    val confirmation: AnalysisConfirmation? = null,
    val categoryConfirmed: Boolean = false,
    val selectedCategory: MaterialCategory? = null,
    val answers: Map<String, FieldAnswer> = emptyMap(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val result: CompletedAnalysisResponse? = null,
    val selectedOptionId: String? = null,
    val saved: Boolean = false,
    val saving: Boolean = false,
    val loading: Boolean = false,
    val hydrating: Boolean = true,
    val error: String? = null,
    val retryAction: AnalysisRetryAction? = null,
    val savedIdeas: List<SavedAnalysisIdea> = emptyList(),
    val motionDirection: AnalysisMotionDirection = AnalysisMotionDirection.FORWARD,
) {
    val photoUri: String? get() = photo?.privatePath?.let { Uri.fromFile(File(it)).toString() }
    val photoSizeBytes: Long get() = photo?.sizeBytes ?: 0L
    val schemaReady: Boolean get() = initial != null && selectedCategory == initial.prediction.category
    val busy: Boolean get() = hydrating || loading || saving
    val canContinue: Boolean get() = AnalysisConfirmationPolicy.canContinue(schemaReady, selectedCategory, categoryConfirmed) && !busy
    val values: Map<String, String> get() = answers.mapValues { (_, answer) -> (answer as? FieldAnswer.Value)?.raw.orEmpty() }
}

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val gateway: AiAnalysisGateway,
    private val mediaStore: AnalysisMediaStore,
    private val sessions: AnalysisSessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()
    private val persistenceMutex = Mutex()
    private var persistenceRevision = 0L
    private var activeJob: Job? = null
    private var operationGeneration = 0L
    private var pendingCommit: PendingCommit? = null

    private data class PendingCommit(
        val next: AnalysisUiState,
        val expectedAnalysisId: AnalysisId,
        val expectedStep: AnalysisStep,
        val expectedCategory: MaterialCategory?,
        val expectedOptionId: String?,
        val retryAction: AnalysisRetryAction,
        val failureMessage: String,
    )

    init { viewModelScope.launch { hydrate() } }

    fun importPhoto(uri: String) = importMedia(uri, false)
    fun importCapture(path: String) = importMedia(path, true)

    private fun importMedia(value: String, isCapture: Boolean) {
        if (!_state.value.acceptsIntent()) return
        val previous = _state.value
        val token = beginOperation { it.copy(loading = true, error = null, retryAction = null) }
        activeJob = viewModelScope.launch {
            val imported = if (isCapture) {
                mediaStore.adoptCapture(File(value.removePrefix("file://")))
            } else {
                mediaStore.importUri(Uri.parse(value))
            }
            when (imported) {
                is Result.Failure -> failIfCurrent(
                    token,
                    if (isCapture) "Foto hasil kamera tidak dapat dipakai. Ambil ulang foto." else imported.error.userMessage(),
                    if (isCapture) null else AnalysisRetryAction.ImportGallery(value),
                )
                is Result.Success -> {
                    if (!isCurrent(token)) { mediaStore.delete(imported.value); return@launch }
                    val proposed = freshForPhoto(imported.value)
                    when (val committed = commitSession(proposed.toSession())) {
                        is Result.Failure -> {
                            mediaStore.delete(imported.value)
                            failIfCurrent(
                                token,
                                if (isCapture) "Foto belum bisa disimpan. Ambil ulang foto." else committed.error.userMessage(),
                                null,
                            )
                        }
                        is Result.Success -> if (isCurrent(token)) {
                            _state.value = proposed
                            clearActive(token)
                            previous.photo?.let { deleteIfUnreferenced(it) }
                        }
                    }
                }
            }
        }
    }

    fun chooseManual(category: MaterialCategory) {
        if (!_state.value.acceptsIntent()) return
        val previous = _state.value
        val proposed = AnalysisUiState(
            analysisId = AnalysisId("analysis-${UUID.randomUUID()}"), step = AnalysisStep.CONFIRM,
            selectedCategory = category, isManual = true, loading = true, hydrating = false,
        )
        val token = beginOperation { proposed }
        activeJob = viewModelScope.launch {
            val safeBoundary = proposed.copy(step = AnalysisStep.SCAN, loading = false)
            when (val saved = commitSession(safeBoundary.toSession())) {
                is Result.Failure -> failIfCurrent(token, saved.error.userMessage(), null)
                is Result.Success -> { previous.photo?.let { deleteIfUnreferenced(it) }; performInitial(token, category) }
            }
        }
    }

    fun startPhotoAnalysis() {
        if (!_state.value.acceptsIntent() || _state.value.photo == null) return
        startInitial(null)
    }

    private fun startInitial(manualCategory: MaterialCategory?) {
        if (_state.value.hydrating || _state.value.saving) return
        val token = beginOperation {
            it.copy(loading = true, error = null, retryAction = null, initial = null, answers = emptyMap(), fieldErrors = emptyMap(), result = null, saved = false)
        }
        activeJob = viewModelScope.launch { performInitial(token, manualCategory) }
    }

    private suspend fun performInitial(token: Long, manualCategory: MaterialCategory?) {
        val current = _state.value
        val request = InitialAnalysisRequest(current.analysisId, current.photo, manualCategory)
        when (val result = gateway.start(request)) {
            is Result.Failure -> failIfCurrent(token, result.error.userMessage(), AnalysisRetryAction.StartInitial(manualCategory))
            is Result.Success -> acceptInitial(token, request, result.value)
        }
    }

    private suspend fun acceptInitial(token: Long, request: InitialAnalysisRequest, response: InitialAnalysisResponse) {
        if (!isCurrent(token) || _state.value.analysisId != request.analysisId) return
        when (val validation = AnalysisResponseValidator.initial(request.analysisId, request.manualCategory, response)) {
            is Result.Failure -> failIfCurrent(token, validation.error.userMessage(), AnalysisRetryAction.StartInitial(request.manualCategory))
            is Result.Success -> {
                val confirmation = AnalysisConfirmation.from(response.prediction.confidence)
                val explicit = request.manualCategory != null
                val next = _state.value.copy(
                    step = AnalysisStep.CONFIRM, initial = response, prediction = response.prediction, confirmation = confirmation,
                    selectedCategory = when { explicit -> request.manualCategory; confirmation == AnalysisConfirmation.MANUAL_REQUIRED -> null; else -> response.prediction.category },
                    categoryConfirmed = explicit, loading = false, error = null, retryAction = null,
                    motionDirection = AnalysisMotionDirection.FORWARD,
                )
                commitThenPublish(token, next, "Hasil kategori sudah diterima, tetapi belum dapat disimpan.")
            }
        }
    }

    fun setCategory(category: MaterialCategory) {
        if (_state.value.hydrating || _state.value.saving) return
        invalidateOperation()
        val ready = _state.value.initial?.prediction?.category == category
        _state.value = _state.value.copy(
            selectedCategory = category, categoryConfirmed = ready, answers = emptyMap(), fieldErrors = emptyMap(),
            result = null, loading = false, error = null, retryAction = null,
        )
        if (ready) schedulePersist() else startInitial(category)
    }

    fun continueToInputs() {
        val current = _state.value
        if (!current.acceptsIntent() || !current.canContinue) return
        val answers = current.initial?.requestedFields.orEmpty().associate { it.id.value to (current.answers[it.id.value] ?: FieldAnswer.Value("")) }
        _state.value = current.copy(step = AnalysisStep.INPUTS, answers = answers, fieldErrors = emptyMap(), error = null, retryAction = null, motionDirection = AnalysisMotionDirection.FORWARD)
        schedulePersist()
    }

    fun updateValue(id: String, value: String) {
        if (!_state.value.acceptsIntent()) return
        _state.value = _state.value.copy(answers = _state.value.answers + (id to FieldAnswer.Value(value)), fieldErrors = _state.value.fieldErrors - id, error = null, retryAction = null)
        schedulePersist()
    }

    fun markUnavailable(id: String) {
        if (!_state.value.acceptsIntent()) return
        _state.value = _state.value.copy(answers = _state.value.answers + (id to FieldAnswer.Unavailable), fieldErrors = _state.value.fieldErrors - id, error = null, retryAction = null)
        schedulePersist()
    }

    fun submitInputs() {
        val current = _state.value
        if (!current.acceptsIntent() || current.step != AnalysisStep.INPUTS) return
        val response = current.initial ?: return
        val category = current.selectedCategory ?: return
        val validation = AnalysisInputValidator.validate(response.requestedFields, current.answers)
        if (!validation.isValid) { _state.value = current.copy(fieldErrors = validation.fieldErrors, error = "Periksa kembali jawaban yang ditandai.", retryAction = null); return }
        val request = CompletedAnalysisRequest(current.analysisId, category, response.requestedFields.map { it.toObservation(current.answers[it.id.value]) })
        val token = beginOperation { it.copy(loading = true, fieldErrors = emptyMap(), error = null, retryAction = null) }
        activeJob = viewModelScope.launch {
            when (val result = gateway.complete(request)) {
                is Result.Failure -> failIfCurrent(token, result.error.userMessage(), AnalysisRetryAction.Complete)
                is Result.Success -> {
                    if (!isCurrent(token) || _state.value.analysisId != request.analysisId || _state.value.selectedCategory != category || _state.value.step != AnalysisStep.INPUTS) return@launch
                    when (val checked = AnalysisResponseValidator.completed(current.analysisId, category, result.value, request.observations, response.requestedFields)) {
                        is Result.Failure -> failIfCurrent(token, checked.error.userMessage(), AnalysisRetryAction.Complete)
                        is Result.Success -> {
                            val next = _state.value.copy(step = AnalysisStep.RESULT, result = result.value, selectedOptionId = null, saved = false, loading = false, error = null, retryAction = null, motionDirection = AnalysisMotionDirection.FORWARD)
                            commitThenPublish(token, next, "Hasil analisis sudah diterima, tetapi belum dapat disimpan.")
                        }
                    }
                }
            }
        }
    }

    fun selectOption(id: String) {
        if (!_state.value.acceptsIntent() || _state.value.step != AnalysisStep.RESULT) return
        if (_state.value.result?.productOptions?.none { it.optionId.value == id } != false) return
        _state.value = _state.value.copy(selectedOptionId = id, saved = false, error = null, retryAction = null)
        schedulePersist()
    }

    fun saveForMaking() {
        val current = _state.value
        if (!current.acceptsIntent() || current.step != AnalysisStep.RESULT) return
        val result = current.result ?: return
        val optionId = current.selectedOptionId?.let(::ProductOptionId) ?: return
        val token = beginOperation { it.copy(saving = true, error = null, retryAction = null) }
        activeJob = viewModelScope.launch {
            when (val saved = sessions.saveIdea(SavedAnalysisIdea(current.analysisId, optionId, result, current.photo))) {
                is Result.Failure -> failIfCurrent(token, saved.error.userMessage(), AnalysisRetryAction.SaveIdea, true)
                is Result.Success -> {
                    if (!isCurrent(token) || _state.value.analysisId != current.analysisId || _state.value.selectedOptionId != optionId.value || _state.value.step != AnalysisStep.RESULT) return@launch
                    val next = _state.value.copy(saved = true, saving = false, error = null, retryAction = null)
                    commitThenPublish(token, next, "Ide sudah tersimpan, tetapi status layar belum dapat disimpan.")
                }
            }
        }
    }

    fun retry() {
        val retry = _state.value.retryAction ?: return
        _state.value = _state.value.copy(error = null, retryAction = null)
        when (retry) {
            is AnalysisRetryAction.ImportGallery -> importMedia(retry.uri, false)
            is AnalysisRetryAction.StartInitial -> startInitial(retry.manualCategory)
            AnalysisRetryAction.Complete -> submitInputs()
            AnalysisRetryAction.SaveIdea -> saveForMaking()
            AnalysisRetryAction.PersistPending, AnalysisRetryAction.PersistCurrent -> retryPendingCommit()
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null, retryAction = null) }

    fun openSavedIdeas() {
        if (!_state.value.acceptsIntent()) return
        val token = beginOperation { it.copy(loading = true, error = null, retryAction = null) }
        activeJob = viewModelScope.launch {
            when (val snapshot = persistenceMutex.withLock { sessions.loadSnapshot() }) {
                is Result.Failure -> failIfCurrent(token, snapshot.error.userMessage(), null)
                is Result.Success -> if (isCurrent(token)) {
                    val next = _state.value.copy(step = AnalysisStep.IDEAS, savedIdeas = snapshot.value.savedIdeas, loading = false, error = null, retryAction = null, motionDirection = AnalysisMotionDirection.FORWARD)
                    commitThenPublish(token, next, "Daftar ide sudah dimuat, tetapi posisi layar belum dapat disimpan.")
                }
            }
        }
    }

    fun openSavedIdea(idea: SavedAnalysisIdea) {
        if (!_state.value.acceptsIntent()) return
        _state.value = AnalysisUiState(
            step = AnalysisStep.RESULT, analysisId = idea.analysisId, photo = idea.photo, selectedCategory = idea.result.category,
            categoryConfirmed = true, result = idea.result, selectedOptionId = idea.optionId.value, saved = true,
            hydrating = false, savedIdeas = _state.value.savedIdeas, motionDirection = AnalysisMotionDirection.FORWARD,
        )
        schedulePersist()
    }

    fun navigateBack(): Boolean {
        val current = _state.value
        if (current.hydrating) return true
        invalidateOperation()
        val target = when (current.step) {
            AnalysisStep.PREVIEW -> AnalysisStep.SCAN
            AnalysisStep.CONFIRM -> if (current.photo != null) AnalysisStep.PREVIEW else AnalysisStep.SCAN
            AnalysisStep.INPUTS -> AnalysisStep.CONFIRM
            AnalysisStep.RESULT -> if (current.initial == null) AnalysisStep.IDEAS else AnalysisStep.INPUTS
            AnalysisStep.IDEAS -> AnalysisStep.SCAN
            AnalysisStep.SCAN -> return false
        }
        _state.value = current.copy(step = target, loading = false, saving = false, error = null, retryAction = null, motionDirection = AnalysisMotionDirection.BACKWARD)
        schedulePersist()
        return true
    }

    fun reset() {
        if (_state.value.hydrating) return
        val previous = _state.value
        val token = beginOperation { it.copy(loading = true, error = null, retryAction = null) }
        activeJob = viewModelScope.launch {
            when (val cleared = persistenceMutex.withLock { sessions.clearSession() }) {
                is Result.Failure -> failIfCurrent(token, cleared.error.userMessage(), null)
                is Result.Success -> if (isCurrent(token)) {
                    _state.value = AnalysisUiState(hydrating = false)
                    clearActive(token)
                    previous.photo?.let { deleteIfUnreferenced(it) }
                }
            }
        }
    }

    private suspend fun hydrate() {
        persistenceMutex.withLock {
            when (val snapshot = sessions.loadSnapshot()) {
                is Result.Failure -> _state.value = AnalysisUiState(hydrating = false, error = snapshot.error.userMessage(), retryAction = null)
                is Result.Success -> restore(snapshot.value)
            }
        }
    }

    private suspend fun restore(snapshot: AnalysisPersistenceSnapshot) {
        mediaStore.cleanupAbandoned()
        val session = snapshot.session
        val validPhoto = session?.photo?.takeIf { mediaStore.isValidOwned(it) }
        val validPhase = session?.phase?.takeIf { it.isValidFor(session, validPhoto != null) }
        val restored = when {
            session == null -> AnalysisUiState(hydrating = true, savedIdeas = snapshot.savedIdeas)
            validPhase == null -> AnalysisUiState(hydrating = true, savedIdeas = snapshot.savedIdeas, error = "Sesi analisis sebelumnya tidak lengkap. Mulai analisis baru.", retryAction = null)
            else -> AnalysisUiState(
                step = validPhase.toStep(), analysisId = session.analysisId, photo = validPhoto, initial = session.initial,
                prediction = session.initial?.prediction, confirmation = session.initial?.prediction?.confidence?.let(AnalysisConfirmation::from),
                categoryConfirmed = session.categoryConfirmed, isManual = session.isManual, selectedCategory = session.selectedCategory,
                answers = session.answers, result = session.result, selectedOptionId = session.selectedOptionId?.value,
                saved = session.selectedOptionId?.let { selected -> snapshot.savedIdeas.any { it.analysisId == session.analysisId && it.optionId == selected } } == true,
                hydrating = true, savedIdeas = snapshot.savedIdeas,
            )
        }
        mediaStore.cleanupOrphans(snapshot.committedMediaPaths)
        _state.value = restored.copy(hydrating = false)
    }

    private fun schedulePersist() {
        val revision = ++persistenceRevision
        val session = _state.value.toSession()
        viewModelScope.launch {
            persistenceMutex.withLock {
                if (revision != persistenceRevision) return@withLock
                when (val saved = sessions.saveSession(session)) {
                    is Result.Failure -> {
                        val current = _state.value
                        pendingCommit = current.pending(
                            next = current.copy(error = null, retryAction = null),
                            retryAction = AnalysisRetryAction.PersistCurrent,
                            failureMessage = "Perubahan belum dapat disimpan. Coba simpan ulang.",
                        )
                        _state.value = current.copy(error = saved.error.userMessage(), retryAction = AnalysisRetryAction.PersistCurrent)
                    }
                    is Result.Success -> Unit
                }
            }
        }
    }

    private suspend fun commitSession(session: AnalysisSession): Result<Unit> = persistenceMutex.withLock { sessions.saveSession(session) }

    private suspend fun commitThenPublish(token: Long, next: AnalysisUiState, failureMessage: String) {
        val current = _state.value
        when (val committed = commitSession(next.toSession())) {
            is Result.Success -> if (isCurrent(token) && current.contextMatches(_state.value)) {
                pendingCommit = null
                _state.value = next
                clearActive(token)
            }
            is Result.Failure -> if (isCurrent(token) && current.contextMatches(_state.value)) {
                pendingCommit = current.pending(next, AnalysisRetryAction.PersistPending, failureMessage)
                _state.value = current.copy(
                    loading = false,
                    saving = false,
                    error = failureMessage,
                    retryAction = AnalysisRetryAction.PersistPending,
                )
                clearActive(token)
            }
        }
    }

    private fun retryPendingCommit() {
        val pending = pendingCommit ?: return
        if (!pending.matches(_state.value) || !_state.value.acceptsIntent()) {
            pendingCommit = null
            _state.value = _state.value.copy(error = null, retryAction = null)
            return
        }
        val token = beginOperation(
            transform = { it.copy(loading = true, error = null, retryAction = null) },
            preservePending = true,
        )
        activeJob = viewModelScope.launch {
            when (commitSession(pending.next.toSession())) {
                is Result.Success -> if (isCurrent(token) && pending.matches(_state.value)) {
                    pendingCommit = null
                    _state.value = pending.next.copy(loading = false, saving = false, error = null, retryAction = null)
                    clearActive(token)
                }
                is Result.Failure -> if (isCurrent(token)) {
                    _state.value = _state.value.copy(loading = false, saving = false, error = pending.failureMessage, retryAction = pending.retryAction)
                    clearActive(token)
                }
            }
        }
    }

    private suspend fun deleteIfUnreferenced(photo: PhotoReference) {
        persistenceMutex.withLock {
            when (val snapshot = sessions.loadSnapshot()) {
                is Result.Success -> if (photo.privatePath !in snapshot.value.committedMediaPaths) mediaStore.delete(photo)
                is Result.Failure -> Unit
            }
        }
    }

    private fun beginOperation(
        preservePending: Boolean = false,
        transform: (AnalysisUiState) -> AnalysisUiState,
    ): Long {
        invalidateOperation()
        if (!preservePending) pendingCommit = null
        val token = operationGeneration
        _state.value = transform(_state.value)
        return token
    }

    private fun invalidateOperation() { operationGeneration += 1; activeJob?.cancel(); activeJob = null }
    private fun isCurrent(token: Long): Boolean = token == operationGeneration
    private fun clearActive(token: Long) { if (isCurrent(token)) activeJob = null }

    private fun failIfCurrent(token: Long, message: String, retry: AnalysisRetryAction?, wasSaving: Boolean = false) {
        if (!isCurrent(token)) return
        _state.value = _state.value.copy(loading = false, saving = if (wasSaving) false else _state.value.saving, error = message, retryAction = retry)
        clearActive(token)
    }

    private fun AnalysisUiState.acceptsIntent(): Boolean = !hydrating && !loading && !saving

    private fun AnalysisUiState.pending(
        next: AnalysisUiState,
        retryAction: AnalysisRetryAction,
        failureMessage: String,
    ) = PendingCommit(next, analysisId, step, selectedCategory, selectedOptionId, retryAction, failureMessage)

    private fun PendingCommit.matches(state: AnalysisUiState): Boolean =
        state.analysisId == expectedAnalysisId && state.step == expectedStep &&
            state.selectedCategory == expectedCategory && state.selectedOptionId == expectedOptionId

    private fun AnalysisUiState.contextMatches(other: AnalysisUiState): Boolean =
        analysisId == other.analysisId && step == other.step && selectedCategory == other.selectedCategory &&
            selectedOptionId == other.selectedOptionId

    private fun AnalysisUiState.toSession() = AnalysisSession(
        analysisId = analysisId, photo = photo, initial = initial, selectedCategory = selectedCategory, answers = answers,
        result = result, selectedOptionId = selectedOptionId?.let(::ProductOptionId), categoryConfirmed = categoryConfirmed,
        isManual = isManual, phase = step.toPhase(),
    )

    private fun freshForPhoto(photo: PhotoReference) = AnalysisUiState(step = AnalysisStep.PREVIEW, analysisId = AnalysisId("analysis-${UUID.randomUUID()}"), photo = photo, hydrating = false)

    private fun RequestedField.toObservation(answer: FieldAnswer?): Observation {
        if (answer !is FieldAnswer.Value || answer.raw.isBlank()) return Observation(id, unit = unit, availability = Availability.NOT_AVAILABLE, source = ValueSource.USER)
        val raw = answer.raw.trim()
        val value = when (type) {
            InspectionFieldType.DECIMAL -> InspectionValue.Decimal(raw.replace(',', '.').toDouble())
            InspectionFieldType.WHOLE -> InspectionValue.Whole(raw.toInt())
            InspectionFieldType.BOOLEAN -> InspectionValue.BooleanValue(raw == "true")
            InspectionFieldType.CHOICE -> InspectionValue.Choice(raw)
            InspectionFieldType.TEXT -> InspectionValue.Text(raw)
        }
        return Observation(id, value, unit, ValueSource.USER, Availability.PROVIDED)
    }

    private fun AnalysisStep.toPhase(): AnalysisFlowPhase = AnalysisFlowPhase.valueOf(name)
    private fun AnalysisFlowPhase.toStep(): AnalysisStep = AnalysisStep.valueOf(name)
    private fun AnalysisFlowPhase.isValidFor(session: AnalysisSession, validPhoto: Boolean): Boolean = when (this) {
        AnalysisFlowPhase.SCAN -> true
        AnalysisFlowPhase.PREVIEW -> validPhoto
        AnalysisFlowPhase.CONFIRM -> session.initial != null
        AnalysisFlowPhase.INPUTS -> session.initial != null && session.selectedCategory != null && session.categoryConfirmed
        AnalysisFlowPhase.RESULT -> session.result != null && session.selectedCategory == session.result.category
        AnalysisFlowPhase.IDEAS -> true
    }

    private fun DomainFailure.userMessage(): String = when (this) {
        DomainFailure.Offline -> "Tidak ada koneksi. Periksa internet lalu coba lagi."
        DomainFailure.Timeout -> "Analisis memerlukan waktu terlalu lama. Coba lagi."
        DomainFailure.Unauthorized -> "Sesi akses berakhir. Masuk kembali untuk melanjutkan."
        DomainFailure.MalformedResponse -> "Jawaban layanan tidak sesuai. Coba ulang analisis."
        DomainFailure.UnsupportedSchema -> "Form analisis belum didukung aplikasi ini."
        DomainFailure.UnsupportedImage -> "Foto tidak didukung, kosong, atau lebih dari 15 MB."
        DomainFailure.PermissionDenied -> "Foto tidak bisa dibaca. Pilih ulang dari galeri."
        DomainFailure.Unavailable -> "Layanan belum tersedia. Coba lagi sebentar."
        is DomainFailure.Validation -> violations.firstOrNull() ?: "Data belum valid."
    }
}
