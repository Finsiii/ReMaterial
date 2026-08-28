package com.rematerial.app.feature.analysis

import android.net.Uri
import com.rematerial.app.core.media.AnalysisMediaStore
import com.rematerial.app.core.model.AnalysisId
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.analysis.data.AnalysisFixtures
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AnalysisFlowPhase
import com.rematerial.app.feature.analysis.domain.AnalysisPersistenceSnapshot
import com.rematerial.app.feature.analysis.domain.AnalysisSession
import com.rematerial.app.feature.analysis.domain.AnalysisSessionRepository
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisResponse
import com.rematerial.app.feature.analysis.domain.FieldAnswer
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.InitialAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InspectionFieldType
import com.rematerial.app.feature.analysis.domain.PhotoReference
import com.rematerial.app.feature.analysis.domain.SavedAnalysisIdea
import com.rematerial.app.feature.analysis.presentation.AnalysisStep
import com.rematerial.app.feature.analysis.presentation.AnalysisRetryAction
import com.rematerial.app.feature.analysis.presentation.AnalysisViewModel
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModelConcurrencyTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun delayedHydrationBlocksImportAndCleansOnlyCommittedReferences() = runTest(dispatcher) {
        val repository = FakeRepository(snapshotInitiallyReady = false)
        val media = FakeMediaStore()
        val viewModel = AnalysisViewModel(DelayedGateway(), media, repository)
        runCurrent()

        assertTrue(viewModel.state.value.hydrating)
        viewModel.importPhoto("content://gallery/new")
        runCurrent()
        assertEquals(0, media.importCalls)
        assertTrue(media.cleanedReferences.isEmpty())

        repository.snapshot.complete(
            Result.Success(AnalysisPersistenceSnapshot(null, emptyList(), setOf("/committed/photo.jpg"))),
        )
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hydrating)
        assertEquals(setOf("/committed/photo.jpg"), media.cleanedReferences.single())
    }

    @Test fun hydrationRemainsBlockedUntilOrphanCleanupCompletes() = runTest(dispatcher) {
        val cleanupGate = CompletableDeferred<Unit>()
        val media = FakeMediaStore().apply { this.cleanupGate = cleanupGate }
        val viewModel = AnalysisViewModel(DelayedGateway(), media, FakeRepository())
        runCurrent()

        assertTrue(viewModel.state.value.hydrating)
        viewModel.importCapture("/capture/during-cleanup.jpg")
        runCurrent()
        assertEquals(0, media.importCalls)

        cleanupGate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hydrating)
        viewModel.importCapture("/capture/after-cleanup.jpg")
        advanceUntilIdle()
        assertEquals(1, media.importCalls)
        assertTrue("/owned/capture.jpg" !in media.deletedPaths)
    }

    @Test fun successfulInitialWaitsForCommitAndPersistenceRetryPublishesIt() = runTest(dispatcher) {
        val repository = FakeRepository()
        val firstCommit = CompletableDeferred<Result<Unit>>()
        val resultCommit = CompletableDeferred<Result<Unit>>()
        val retryCommit = CompletableDeferred<Result<Unit>>()
        repository.saveSessionGates += listOf(firstCommit, resultCommit, retryCommit)
        val gateway = DelayedGateway()
        val viewModel = AnalysisViewModel(gateway, FakeMediaStore(), repository)
        advanceUntilIdle()

        viewModel.chooseManual(MaterialCategory.WOOD)
        runCurrent()
        firstCommit.complete(Result.Success(Unit))
        runCurrent()
        val call = gateway.starts.single()
        call.response.complete(Result.Success(initialFor(call.request)))
        runCurrent()
        assertTrue(viewModel.state.value.loading)
        assertEquals(null, viewModel.state.value.initial)

        resultCommit.complete(Result.Failure(com.rematerial.app.core.model.DomainFailure.Unavailable))
        runCurrent()
        assertFalse(viewModel.state.value.busy)
        assertEquals(null, viewModel.state.value.initial)
        assertEquals(AnalysisRetryAction.PersistPending, viewModel.state.value.retryAction)

        viewModel.retry()
        runCurrent()
        retryCommit.complete(Result.Success(Unit))
        advanceUntilIdle()
        assertEquals(MaterialCategory.WOOD, viewModel.state.value.initial?.prediction?.category)
        assertEquals(AnalysisFlowPhase.CONFIRM, repository.currentSession?.phase)
    }

    @Test fun delayedInitialResponseAfterBackCannotReopenConfirmation() = runTest(dispatcher) {
        val gateway = DelayedGateway()
        val viewModel = AnalysisViewModel(gateway, FakeMediaStore(), FakeRepository())
        advanceUntilIdle()
        viewModel.chooseManual(MaterialCategory.METAL)
        runCurrent()
        assertEquals(1, gateway.starts.size)

        assertTrue(viewModel.navigateBack())
        gateway.starts.single().response.complete(Result.Success(initialFor(gateway.starts.single().request)))
        advanceUntilIdle()
        assertEquals(AnalysisStep.SCAN, viewModel.state.value.step)
        assertFalse(viewModel.state.value.loading)
    }

    @Test fun newerCategoryRequestWinsOverOlderResponse() = runTest(dispatcher) {
        val gateway = DelayedGateway()
        val viewModel = AnalysisViewModel(gateway, FakeMediaStore(), FakeRepository())
        advanceUntilIdle()
        viewModel.chooseManual(MaterialCategory.METAL)
        runCurrent()
        viewModel.setCategory(MaterialCategory.WOOD)
        runCurrent()
        assertEquals(2, gateway.starts.size)

        gateway.starts[1].response.complete(Result.Success(initialFor(gateway.starts[1].request)))
        runCurrent()
        gateway.starts[0].response.complete(Result.Success(initialFor(gateway.starts[0].request)))
        advanceUntilIdle()
        assertEquals(MaterialCategory.WOOD, viewModel.state.value.selectedCategory)
        assertEquals(MaterialCategory.WOOD, viewModel.state.value.initial?.prediction?.category)
    }

    @Test fun retryInitialKeepsTheExactCorrectedCategory() = runTest(dispatcher) {
        val gateway = DelayedGateway()
        val viewModel = AnalysisViewModel(gateway, FakeMediaStore(), FakeRepository())
        advanceUntilIdle()
        viewModel.chooseManual(MaterialCategory.METAL)
        runCurrent()
        gateway.starts[0].response.complete(Result.Success(initialFor(gateway.starts[0].request)))
        advanceUntilIdle()

        viewModel.setCategory(MaterialCategory.WOOD)
        runCurrent()
        gateway.starts[1].response.complete(Result.Failure(com.rematerial.app.core.model.DomainFailure.Timeout))
        advanceUntilIdle()
        assertEquals(AnalysisRetryAction.StartInitial(MaterialCategory.WOOD), viewModel.state.value.retryAction)

        viewModel.retry()
        runCurrent()
        assertEquals(MaterialCategory.WOOD, gateway.starts[2].request.manualCategory)
    }

    @Test fun duplicateSubmitStartsOnlyOneCompletion() = runTest(dispatcher) {
        val fixture = AnalysisFixtures.metalHigh()
        val session = AnalysisSession(
            analysisId = fixture.completed.analysisId,
            initial = fixture.initial,
            selectedCategory = MaterialCategory.METAL,
            answers = validAnswers(fixture.initial),
            categoryConfirmed = true,
            isManual = true,
            phase = AnalysisFlowPhase.INPUTS,
        )
        val gateway = DelayedGateway()
        val viewModel = AnalysisViewModel(gateway, FakeMediaStore(), FakeRepository(session = session))
        advanceUntilIdle()

        viewModel.submitInputs()
        viewModel.submitInputs()
        runCurrent()
        assertEquals(1, gateway.completions.size)
    }

    @Test fun savingOwnsTheExactOptionAndSelectionCannotChangeMidSave() = runTest(dispatcher) {
        val fixture = AnalysisFixtures.metalHigh().completed
        val first = fixture.productOptions[0].optionId
        val second = fixture.productOptions[1].optionId
        val session = AnalysisSession(
            analysisId = fixture.analysisId,
            selectedCategory = fixture.category,
            result = fixture,
            selectedOptionId = first,
            categoryConfirmed = true,
            phase = AnalysisFlowPhase.RESULT,
        )
        val repository = FakeRepository(session = session).apply {
            saveIdeaGate = CompletableDeferred()
        }
        val viewModel = AnalysisViewModel(DelayedGateway(), FakeMediaStore(), repository)
        advanceUntilIdle()

        viewModel.saveForMaking()
        runCurrent()
        viewModel.selectOption(second.value)
        assertEquals(first.value, viewModel.state.value.selectedOptionId)
        repository.saveIdeaGate!!.complete(Result.Success(Unit))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.saved)
        assertEquals(first.value, viewModel.state.value.selectedOptionId)
        assertEquals(first, repository.savedIdea?.optionId)
    }

    @Test fun backFromResultPersistsInputsPhaseAcrossRecreation() = runTest(dispatcher) {
        val fixture = AnalysisFixtures.metalHigh()
        val repository = FakeRepository(
            session = AnalysisSession(
                analysisId = fixture.completed.analysisId,
                initial = fixture.initial,
                selectedCategory = fixture.category,
                answers = validAnswers(fixture.initial),
                result = fixture.completed,
                categoryConfirmed = true,
                phase = AnalysisFlowPhase.RESULT,
            ),
        )
        val first = AnalysisViewModel(DelayedGateway(), FakeMediaStore(), repository)
        advanceUntilIdle()
        assertTrue(first.navigateBack())
        advanceUntilIdle()
        assertEquals(AnalysisFlowPhase.INPUTS, repository.currentSession?.phase)

        val recreated = AnalysisViewModel(DelayedGateway(), FakeMediaStore(), repository)
        advanceUntilIdle()
        assertEquals(AnalysisStep.INPUTS, recreated.state.value.step)
    }

    @Test fun enteringAnalysisAfterCompletedResultStartsFreshSession() = runTest(dispatcher) {
        val fixture = AnalysisFixtures.metalHigh()
        val repository = FakeRepository(
            session = AnalysisSession(
                analysisId = fixture.completed.analysisId,
                initial = fixture.initial,
                selectedCategory = fixture.category,
                answers = validAnswers(fixture.initial),
                result = fixture.completed,
                categoryConfirmed = true,
                phase = AnalysisFlowPhase.RESULT,
            ),
        )
        val viewModel = AnalysisViewModel(DelayedGateway(), FakeMediaStore(), repository)
        advanceUntilIdle()

        viewModel.prepareForEntry()
        advanceUntilIdle()

        assertEquals(AnalysisStep.SCAN, viewModel.state.value.step)
        assertTrue(viewModel.state.value.result == null)
        assertTrue(repository.currentSession == null)
    }

    @Test fun enteringAnalysisWhileInProgressResumesExistingSession() = runTest(dispatcher) {
        val fixture = AnalysisFixtures.metalHigh()
        val repository = FakeRepository(
            session = AnalysisSession(
                analysisId = fixture.completed.analysisId,
                initial = fixture.initial,
                selectedCategory = fixture.category,
                answers = validAnswers(fixture.initial),
                categoryConfirmed = true,
                phase = AnalysisFlowPhase.INPUTS,
            ),
        )
        val viewModel = AnalysisViewModel(DelayedGateway(), FakeMediaStore(), repository)
        advanceUntilIdle()

        viewModel.prepareForEntry()
        advanceUntilIdle()

        assertEquals(AnalysisStep.INPUTS, viewModel.state.value.step)
        assertEquals(fixture.completed.analysisId, viewModel.state.value.analysisId)
        assertEquals(AnalysisFlowPhase.INPUTS, repository.currentSession?.phase)
    }

    private fun initialFor(request: InitialAnalysisRequest): InitialAnalysisResponse {
        val category = request.manualCategory ?: MaterialCategory.METAL
        return AnalysisFixtures.forCategory(category).initial.copy(analysisId = request.analysisId)
    }

    private fun validAnswers(initial: InitialAnalysisResponse): Map<String, FieldAnswer> =
        initial.requestedFields.associate { field ->
            field.id.value to when {
                field.id.value == "quantity" -> FieldAnswer.Value("2.5")
                field.id.value == "source_location" -> FieldAnswer.Value("Workshop sekolah")
                !field.required -> FieldAnswer.Unavailable
                field.type == InspectionFieldType.CHOICE -> FieldAnswer.Value(field.choices.first())
                field.type == InspectionFieldType.DECIMAL -> FieldAnswer.Value((field.minimum ?: 1.0).toString())
                field.type == InspectionFieldType.WHOLE -> FieldAnswer.Value((field.minimum ?: 1.0).toInt().toString())
                field.type == InspectionFieldType.BOOLEAN -> FieldAnswer.Value("false")
                else -> FieldAnswer.Value("Terlihat sesuai")
            }
        }

    private class DelayedGateway : AiAnalysisGateway {
        data class StartCall(
            val request: InitialAnalysisRequest,
            val response: CompletableDeferred<Result<InitialAnalysisResponse>> = CompletableDeferred(),
        )
        data class CompleteCall(
            val request: CompletedAnalysisRequest,
            val response: CompletableDeferred<Result<CompletedAnalysisResponse>> = CompletableDeferred(),
        )
        val starts = mutableListOf<StartCall>()
        val completions = mutableListOf<CompleteCall>()
        override suspend fun start(request: InitialAnalysisRequest, onProgress: suspend (com.rematerial.app.feature.analysis.domain.AnalysisProgress) -> Unit): Result<InitialAnalysisResponse> =
            StartCall(request).also(starts::add).response.await()
        override suspend fun complete(request: CompletedAnalysisRequest, onProgress: suspend (com.rematerial.app.feature.analysis.domain.AnalysisProgress) -> Unit): Result<CompletedAnalysisResponse> =
            CompleteCall(request).also(completions::add).response.await()
    }

    private class FakeRepository(
        session: AnalysisSession? = null,
        snapshotInitiallyReady: Boolean = true,
    ) : AnalysisSessionRepository {
        val snapshot = CompletableDeferred<Result<AnalysisPersistenceSnapshot>>()
        private val delayedSnapshot = !snapshotInitiallyReady
        var currentSession = session
        var saveIdeaGate: CompletableDeferred<Result<Unit>>? = null
        val saveSessionGates = ArrayDeque<CompletableDeferred<Result<Unit>>>()
        var savedIdea: SavedAnalysisIdea? = null
        override suspend fun loadSnapshot(): Result<AnalysisPersistenceSnapshot> = if (delayedSnapshot) snapshot.await() else {
            Result.Success(
                AnalysisPersistenceSnapshot(
                    currentSession,
                    listOfNotNull(savedIdea),
                    buildSet {
                        currentSession?.photo?.privatePath?.let(::add)
                        savedIdea?.photo?.privatePath?.let(::add)
                    },
                ),
            )
        }
        override suspend fun loadSession(): Result<AnalysisSession?> = Result.Success(currentSession)
        override suspend fun saveSession(session: AnalysisSession): Result<Unit> {
            val result = saveSessionGates.removeFirstOrNull()?.await() ?: Result.Success(Unit)
            if (result is Result.Success) currentSession = session
            return result
        }
        override suspend fun clearSession(): Result<Unit> { currentSession = null; return Result.Success(Unit) }
        override suspend fun saveIdea(idea: SavedAnalysisIdea): Result<Unit> {
            savedIdea = idea
            return saveIdeaGate?.await() ?: Result.Success(Unit)
        }
        override suspend fun savedIdeas(): Result<List<SavedAnalysisIdea>> = Result.Success(listOfNotNull(savedIdea))
    }

    private class FakeMediaStore : AnalysisMediaStore {
        var importCalls = 0
        var cleanupGate: CompletableDeferred<Unit>? = null
        val cleanedReferences = mutableListOf<Set<String>>()
        val deletedPaths = mutableListOf<String>()
        override suspend fun importUri(uri: Uri): Result<PhotoReference> {
            importCalls++
            return Result.Success(PhotoReference("new", "/owned/new.jpg", "image/jpeg", 10))
        }
        override suspend fun adoptCapture(file: File): Result<PhotoReference> {
            importCalls++
            return Result.Success(PhotoReference("capture", "/owned/capture.jpg", "image/jpeg", 10))
        }
        override suspend fun delete(photo: PhotoReference) { deletedPaths += photo.privatePath }
        override suspend fun cleanupAbandoned() = Unit
        override suspend fun cleanupOrphans(referencedPaths: Set<String>) {
            cleanedReferences += referencedPaths
            cleanupGate?.await()
        }
        override suspend fun isValidOwned(photo: PhotoReference): Boolean = true
    }
}
