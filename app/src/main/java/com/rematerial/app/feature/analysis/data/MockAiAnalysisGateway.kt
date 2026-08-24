package com.rematerial.app.feature.analysis.data

import com.rematerial.app.core.model.AnalysisId
import com.rematerial.app.core.model.Availability
import com.rematerial.app.core.model.FieldId
import com.rematerial.app.core.model.InspectionValue
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.Observation
import com.rematerial.app.core.model.Result
import com.rematerial.app.core.model.SafetyOutcome
import com.rematerial.app.core.model.SourceId
import com.rematerial.app.core.model.UnitCode
import com.rematerial.app.core.model.ValueSource
import com.rematerial.app.feature.analysis.domain.AiAnalysisGateway
import com.rematerial.app.feature.analysis.domain.AnalysisValidator
import com.rematerial.app.feature.analysis.domain.CategoryPrediction
import com.rematerial.app.feature.analysis.domain.CalculationInput
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.InitialAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InspectionFieldType
import com.rematerial.app.feature.analysis.domain.MathCalculation
import com.rematerial.app.feature.analysis.domain.PhotoReference
import com.rematerial.app.feature.analysis.domain.ProductOption
import com.rematerial.app.feature.analysis.domain.RequestedField
import com.rematerial.app.feature.analysis.domain.SafetyAssessment
import com.rematerial.app.feature.analysis.domain.ScoreComponents
import com.rematerial.app.feature.analysis.domain.ScienceFinding
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.FindingId
import com.rematerial.app.core.model.ProductOptionId

data class AnalysisFixture(
    val category: MaterialCategory,
    val confidence: Double,
    val initial: InitialAnalysisResponse,
    val completed: CompletedAnalysisResponse,
)

object AnalysisFixtures {
    fun initialRequest(): InitialAnalysisRequest = InitialAnalysisRequest(
        analysisId = AnalysisId("analysis-demo"),
        photo = PhotoReference("materials/demo.jpg", "image/jpeg", 1_024),
    )

    fun allCompleted(): List<AnalysisFixture> = listOf(
        metalHigh(), cableMedium(), plasticLow(), woodHigh(), textileMedium(), electronicsHigh(),
    )

    fun metalHigh() = fixture(MaterialCategory.METAL, .87, SafetyOutcome.ALLOW, "sharp_edges", "quantity", "Metal organizer")
    fun cableMedium() = fixture(MaterialCategory.CABLE, .65, SafetyOutcome.ALLOW, "insulation", "quantity", "Cable lamp")
    fun plasticLow() = fixture(MaterialCategory.PLASTIC, .35, SafetyOutcome.ALLOW, "resin_code", "quantity", "Storage tray")
    fun woodHigh() = fixture(MaterialCategory.WOOD, .91, SafetyOutcome.CAUTION, "treatment", "quantity", "Wall shelf")
    fun textileMedium() = fixture(MaterialCategory.TEXTILE, .72, SafetyOutcome.CAUTION, "fiber_label", "quantity", "Utility pouch")
    fun electronicsHigh() = fixture(MaterialCategory.ELECTRONICS, .84, SafetyOutcome.BLOCK, "battery", "quantity", "Repair enclosure", includeOption = false)

    private fun fixture(
        category: MaterialCategory,
        confidence: Double,
        safetyOutcome: SafetyOutcome,
        categoryField: String,
        quantityField: String,
        productTitle: String,
        includeOption: Boolean = true,
    ): AnalysisFixture {
        val id = AnalysisId("analysis-${category.name.lowercase()}")
        val fields = listOf(
            RequestedField(FieldId("source_location"), "Sumber atau lokasi", InspectionFieldType.TEXT, required = true),
            RequestedField(FieldId(quantityField), "Jumlah bahan", InspectionFieldType.DECIMAL, UnitCode.KG, required = true),
            RequestedField(FieldId("condition"), "Kondisi umum", InspectionFieldType.CHOICE, choices = listOf("good", "worn", "damaged", "unknown"), required = true),
            RequestedField(FieldId("contamination"), "Kontaminasi", InspectionFieldType.CHOICE, choices = listOf("none", "low", "unknown", "suspected_hazardous"), required = true),
            RequestedField(FieldId(categoryField), "Observasi ${category.displayName}", InspectionFieldType.TEXT, required = true),
        )
        val initial = InitialAnalysisResponse(id, CategoryPrediction(category, confidence), fields)
        val observations = listOf(
            Observation(FieldId("source_location"), InspectionValue.Text("Workshop demo")),
            Observation(FieldId(quantityField), InspectionValue.Decimal(2.5), UnitCode.KG),
            Observation(FieldId("condition"), InspectionValue.Choice("good")),
            Observation(FieldId("contamination"), InspectionValue.Choice("none")),
            Observation(FieldId(categoryField), InspectionValue.Text("Terlihat konsisten dengan ${category.displayName.lowercase()}")),
        )
        val science = ScienceFinding(
            FindingId("finding-${category.name.lowercase()}"),
            "Indikasi ${category.displayName}",
            listOf(FieldId(categoryField), FieldId("quantity")),
            "Sifat visual dan kondisi bahan dibandingkan dengan prosedur pemeriksaan ${category.displayName.lowercase()}.",
            listOf(SourceId("rematerial-material-procedure")),
            "Bukti yang tersedia mendukung kategori ${category.displayName.lowercase()} sebagai klasifikasi awal.",
            "Foto dan observasi pengguna tidak membuktikan komposisi kimia, kemurnian, atau keamanan struktural.",
            "Lakukan pemeriksaan fisik sesuai prosedur dan mintalah verifikasi praktisi bila diperlukan.",
        )
        val mathematics = MathCalculation(
            "mass-usable-${category.name.lowercase()}",
            listOf(CalculationInput("massa tercatat", 2.5, UnitCode.KG)),
            "usable_quantity_ratio", "massa tercatat × 0.90", .90, UnitCode.KG,
            "Koefisien adalah estimasi API; ukur ulang sebelum produksi.",
        )
        val optionSeeds = listOf(
            Triple(productTitle, "Bentuk paling mudah untuk memulai dari bahan yang tersedia.", 79.0),
            Triple(
                when (category) {
                    MaterialCategory.METAL -> "Rak dinding modular"
                    MaterialCategory.CABLE -> "Lampu gantung anyam"
                    MaterialCategory.PLASTIC -> "Organizer meja modular"
                    MaterialCategory.WOOD -> "Bangku samping ringan"
                    MaterialCategory.TEXTILE -> "Tas belanja berstruktur"
                    MaterialCategory.ELECTRONICS -> "Casing alat sederhana"
                },
                "Nilai tambah lebih tinggi dengan sedikit langkah perakitan tambahan.", 84.0,
            ),
            Triple(
                when (category) {
                    MaterialCategory.METAL -> "Nampan aksen industrial"
                    MaterialCategory.CABLE -> "Keranjang kabel rapi"
                    MaterialCategory.PLASTIC -> "Pot tanaman gantung"
                    MaterialCategory.WOOD -> "Papan pajangan minimal"
                    MaterialCategory.TEXTILE -> "Panel dekorasi tekstur"
                    MaterialCategory.ELECTRONICS -> "Jangan diproduksi dulu"
                },
                "Alternatif kreatif yang membantu mengurangi sisa material.", 73.0,
            ),
        )
        val categoryTools = when (category) {
            MaterialCategory.METAL -> listOf("hand-tools", "measuring-tools", "finishing-tools")
            MaterialCategory.CABLE -> listOf("hand-tools", "cutting-tools", "finishing-tools")
            MaterialCategory.PLASTIC -> listOf("cutting-tools", "measuring-tools", "finishing-tools")
            MaterialCategory.WOOD -> listOf("hand-tools", "measuring-tools", "finishing-tools")
            MaterialCategory.TEXTILE -> listOf("hand-tools", "cutting-tools", "finishing-tools")
            MaterialCategory.ELECTRONICS -> listOf("measuring-tools", "hand-tools", "finishing-tools")
        }
        val categorySkills = when (category) {
            MaterialCategory.METAL -> listOf("basic-making", "precision-making", "surface-finishing")
            MaterialCategory.CABLE -> listOf("basic-making", "precision-making", "surface-finishing")
            MaterialCategory.PLASTIC -> listOf("basic-making", "precision-making", "surface-finishing")
            MaterialCategory.WOOD -> listOf("basic-making", "precision-making", "surface-finishing")
            MaterialCategory.TEXTILE -> listOf("basic-making", "precision-making", "surface-finishing")
            MaterialCategory.ELECTRONICS -> listOf("precision-making", "basic-making", "surface-finishing")
        }
        val options = optionSeeds.mapIndexed { index, seed ->
            ProductOption(
                ProductOptionId("option-${category.name.lowercase()}-${index + 1}"), seed.first,
                seed.second, category.displayName,
                listOf(FieldId("quantity")), 1.0 + index * .5, UnitCode.KG,
                listOf(categoryTools[index]), listOf(categorySkills[index]),
                scoreComponents = ScoreComponents(78.0 + index * 4, 84.0 - index * 2, 65.0 + index * 7, 80.0 + index),
                provisionalProductScore = seed.third,
            )
        }
        val completed = CompletedAnalysisResponse(
            id, category, confidence, observations, listOf(science), listOf(mathematics),
            SafetyAssessment(safetyOutcome, if (safetyOutcome == SafetyOutcome.ALLOW) emptyList() else listOf("Data keselamatan perlu diperhatikan sebelum produksi.")),
            if (includeOption && safetyOutcome != SafetyOutcome.BLOCK) options else emptyList(),
        )
        check(AnalysisValidator.validate(completed) is Result.Success)
        return AnalysisFixture(category, confidence, initial, completed)
    }
}

class MockAiAnalysisGateway(
    private val scenario: Scenario = Scenario.METAL_HIGH,
) : AiAnalysisGateway {
    enum class Scenario { METAL_HIGH, CABLE_MEDIUM, PLASTIC_LOW, WOOD_HIGH, TEXTILE_MEDIUM, ELECTRONICS_BLOCK, TIMEOUT, OFFLINE, MALFORMED, UNSUPPORTED_IMAGE, UNAVAILABLE }

    override suspend fun start(request: InitialAnalysisRequest): Result<InitialAnalysisResponse> = when (scenario) {
        Scenario.TIMEOUT -> Result.Failure(DomainFailure.Timeout)
        Scenario.OFFLINE -> Result.Failure(DomainFailure.Offline)
        Scenario.MALFORMED -> Result.Failure(DomainFailure.MalformedResponse)
        Scenario.UNSUPPORTED_IMAGE -> Result.Failure(DomainFailure.UnsupportedImage)
        Scenario.UNAVAILABLE -> Result.Failure(DomainFailure.Unavailable)
        else -> Result.Success(fixture().initial.copy(analysisId = request.analysisId))
    }

    override suspend fun complete(request: CompletedAnalysisRequest): Result<CompletedAnalysisResponse> = when (scenario) {
        Scenario.TIMEOUT -> Result.Failure(DomainFailure.Timeout)
        Scenario.OFFLINE -> Result.Failure(DomainFailure.Offline)
        Scenario.MALFORMED -> Result.Failure(DomainFailure.MalformedResponse)
        Scenario.UNSUPPORTED_IMAGE -> Result.Failure(DomainFailure.UnsupportedImage)
        Scenario.UNAVAILABLE -> Result.Failure(DomainFailure.Unavailable)
        else -> Result.Success(fixture().completed.copy(analysisId = request.analysisId, observations = request.observations))
    }

    private fun fixture(): AnalysisFixture = when (scenario) {
        Scenario.METAL_HIGH -> AnalysisFixtures.metalHigh()
        Scenario.CABLE_MEDIUM -> AnalysisFixtures.cableMedium()
        Scenario.PLASTIC_LOW -> AnalysisFixtures.plasticLow()
        Scenario.WOOD_HIGH -> AnalysisFixtures.woodHigh()
        Scenario.TEXTILE_MEDIUM -> AnalysisFixtures.textileMedium()
        Scenario.ELECTRONICS_BLOCK -> AnalysisFixtures.electronicsHigh()
        else -> AnalysisFixtures.metalHigh()
    }
}
