package com.rematerial.app.feature.production.presentation

import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import com.rematerial.app.feature.production.data.MockProductionRepository
import com.rematerial.app.feature.production.domain.ProductDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import androidx.lifecycle.ViewModelStore

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionNavigationPolicyTest {
    @Test
    fun `production back follows the drill down parent`() {
        assertNull(productionBackTarget(ProductionPage.DISCOVERY))
        assertEquals(ProductionPage.DISCOVERY, productionBackTarget(ProductionPage.DETAIL))
        assertEquals(ProductionPage.DETAIL, productionBackTarget(ProductionPage.FORM))
        assertEquals(ProductionPage.DISCOVERY, productionBackTarget(ProductionPage.CONFIRMED))
        assertEquals(ProductionPage.DISCOVERY, productionBackTarget(ProductionPage.HISTORY))
        assertEquals(ProductionPage.HISTORY, productionBackTarget(ProductionPage.REQUEST))
    }

    @Test
    fun `form back is stored as detail in view model state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = ViewModelStore()
        try {
            val repository = MockProductionRepository()
            val draft = ProductDraft(ProductOptionId("lampu-kabel"), "Lampu", "Tembaga", "1 unit", "analysis-1", true)
            repository.saveDraft(draft)
            val artisan = (repository.searchArtisans("") as Result.Success).value.first()
            val viewModel = ProductionViewModel(repository)
            store.put("production", viewModel)

            viewModel.openDetail(artisan)
            viewModel.openForm()
            viewModel.back()

            assertEquals(ProductionPage.DETAIL, viewModel.state.value.page)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
