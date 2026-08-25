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
import com.rematerial.app.feature.analysis.domain.AnalysisCatalog
import com.rematerial.app.feature.analysis.domain.CategoryPrediction
import com.rematerial.app.feature.analysis.domain.CalculationInput
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisRequest
import com.rematerial.app.feature.analysis.domain.CompletedAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InitialAnalysisRequest
import com.rematerial.app.feature.analysis.domain.InitialAnalysisResponse
import com.rematerial.app.feature.analysis.domain.InspectionFieldType
import com.rematerial.app.feature.analysis.domain.MathCalculation
import com.rematerial.app.feature.analysis.domain.ProductOption
import com.rematerial.app.feature.analysis.domain.ProductParameterRequirement
import com.rematerial.app.feature.analysis.domain.RequestedField
import com.rematerial.app.feature.analysis.domain.SafetyAssessment
import com.rematerial.app.feature.analysis.domain.ScoreComponents
import com.rematerial.app.feature.analysis.domain.ScienceFinding
import com.rematerial.app.feature.analysis.domain.ProductScorePolicy
import com.rematerial.app.feature.analysis.domain.RankedCategoryPrediction
import com.rematerial.app.feature.analysis.domain.SafetyPolicy
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.FindingId
import com.rematerial.app.core.model.ProductOptionId

data class AnalysisFixture(
    val category: MaterialCategory,
    val confidence: Double,
    val initial: InitialAnalysisResponse,
    val completed: CompletedAnalysisResponse,
    val availableOptions: List<ProductOption>,
)

object AnalysisFixtures {
    fun initialRequest(): InitialAnalysisRequest = InitialAnalysisRequest(
        analysisId = AnalysisId("analysis-demo"),
        manualCategory = MaterialCategory.METAL,
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

    fun forCategory(category: MaterialCategory): AnalysisFixture = when (category) {
        MaterialCategory.METAL -> metalHigh()
        MaterialCategory.CABLE -> cableMedium()
        MaterialCategory.PLASTIC -> plasticLow()
        MaterialCategory.WOOD -> woodHigh()
        MaterialCategory.TEXTILE -> textileMedium()
        MaterialCategory.ELECTRONICS -> electronicsHigh()
    }

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
        val fields = AnalysisCatalog.schemaFor(category)
        val initial = InitialAnalysisResponse(
            id,
            CategoryPrediction(category, confidence, candidates(category, confidence)),
            fields.take(2),
            mapOf("quantity" to "5", "condition" to "good", "contamination" to "none"),
        )
        val observations = fields.map { defaultObservation(category, it, safetyOutcome) }
        val quantityObservation = observations.first { it.fieldId.value == "quantity" }
        val quantity = (quantityObservation.value as InspectionValue.Decimal).value
        val quantityUnit = quantityObservation.unit ?: UnitCode.KG
        val science = ScienceFinding(
            FindingId("finding-${category.name.lowercase()}"),
            "Indikasi ${category.displayName}",
            listOf(FieldId("condition"), FieldId("quantity")),
            principle(category),
            listOf(SourceId("rematerial-material-procedure"), SourceId("school-material-safety")),
            interpretation(category),
            "Foto dan jawaban pengguna belum membuktikan komposisi kimia, kemurnian, atau kekuatan struktural.",
            verification(category),
        )
        /* provider returns the score; Android only verifies the locked formula and evidence. */
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
            val requirements = productRequirements(category)
            val requiredParameters = requirements.map(ProductParameterRequirement::fieldId)
            val minimum = quantity * (.75 + index * .20)
            val estimatedUsed = usableQuantity(quantity) * (.55 + index * .15)
            val components = scoreComponents(
                requirements = requirements,
                observations = observations,
                usableQuantity = usableQuantity(quantity),
                initialQuantity = quantity,
                minimumQuantity = minimum,
                estimatedUsedQuantity = estimatedUsed,
                economicPotential = 65.0 + index * 7,
            )
            ProductOption(
                ProductOptionId("option-${category.name.lowercase()}-${index + 1}"), seed.first,
                seed.second, category.displayName,
                requiredParameters, requirements, minimum.coerceAtLeast(.01), quantityUnit,
                estimatedUsed, quantityUnit,
                listOf(categoryTools[index]), listOf(categorySkills[index]),
                scoreFormulaId = ProductScorePolicy.FORMULA_ID,
                scoreFormulaExpression = ProductScorePolicy.FORMULA_EXPRESSION,
                scoreEvidenceInputs = scoreEvidence(components),
                scoreComponents = components,
                provisionalProductScore = ProductScorePolicy.calculate(components),
            )
        }
        val mathematics = calculations(
            quantity = quantity,
            unit = quantityUnit,
            economicPotential = options.firstOrNull()?.scoreComponents?.economicPotential ?: 65.0,
            materialSufficiency = options.firstOrNull()?.scoreComponents?.materialSufficiency ?: 100.0,
        )
        val derivedSafety = SafetyPolicy.assess(category, observations.associateBy(Observation::fieldId))
        check(derivedSafety == safetyOutcome) {
            "Fixture $category requested $safetyOutcome but its observations derive $derivedSafety"
        }
        val completed = CompletedAnalysisResponse(
            id, category, confidence, observations, listOf(science), mathematics,
            SafetyAssessment(derivedSafety, safetyReasons(category, derivedSafety)),
            if (includeOption && derivedSafety != SafetyOutcome.BLOCK) options else emptyList(),
        )
        val validation = AnalysisValidator.validate(completed, fields)
        check(validation is Result.Success) { validation.toString() }
        return AnalysisFixture(category, confidence, initial, completed, options)
    }

    fun completedFor(request: CompletedAnalysisRequest): CompletedAnalysisResponse {
        val fixture = forCategory(request.category)
        val quantityObservation = request.observations.first { it.fieldId.value == "quantity" }
        val quantity = (quantityObservation.value as? InspectionValue.Decimal)?.value ?: .01
        val unit = quantityObservation.unit ?: AnalysisCatalog.contractFor(request.category, FieldId("quantity"))?.unit ?: UnitCode.KG
        val safety = SafetyPolicy.assess(request.category, request.observations.associateBy(Observation::fieldId))
        val options = if (safety == SafetyOutcome.BLOCK) emptyList() else fixture.availableOptions.mapIndexed { index, option ->
            val minimum = if (unit == UnitCode.PCS) (index + 1).toDouble() else (quantity * (.2 + index * .1)).coerceAtLeast(.01)
            val estimatedUsed = (usableQuantity(quantity) * (.45 + index * .15)).coerceAtMost(quantity)
            val components = scoreComponents(
                requirements = option.parameterRequirements,
                observations = request.observations,
                usableQuantity = usableQuantity(quantity),
                initialQuantity = quantity,
                minimumQuantity = minimum,
                estimatedUsedQuantity = estimatedUsed,
                economicPotential = option.scoreComponents.economicPotential,
            )
            option.copy(
                minimumUnit = unit, minimumQuantity = minimum,
                estimatedUsedQuantity = estimatedUsed, estimatedUsedUnit = unit,
                scoreComponents = components,
                provisionalProductScore = ProductScorePolicy.calculate(components),
                scoreEvidenceInputs = scoreEvidence(components),
            )
        }
        val referenceComponents = options.firstOrNull()?.scoreComponents
        return fixture.completed.copy(
            analysisId = request.analysisId,
            observations = request.observations,
            science = fixture.completed.science.map { finding ->
                finding.copy(
                    observationRefs = listOf(FieldId("condition"), FieldId("quantity")),
                    interpretation = "Kesimpulan awal ini memakai foto dan jawaban penting pengguna untuk ${request.category.displayName.lowercase()}.",
                )
            },
            mathematics = calculations(
                quantity,
                unit,
                referenceComponents?.economicPotential ?: 65.0,
                referenceComponents?.materialSufficiency ?: 100.0,
            ),
            safety = SafetyAssessment(safety, safetyReasons(request.category, safety)),
            productOptions = options,
        )
    }

    private fun calculations(
        quantity: Double,
        unit: UnitCode,
        economicPotential: Double,
        materialSufficiency: Double,
    ): List<MathCalculation> {
        val usable = usableQuantity(quantity)
        val perProduct = (usable / 3.0).coerceAtLeast(.01)
        val marginReadiness = (economicPotential + materialSufficiency) / 2.0
        return listOf(
            MathCalculation("usable", listOf(CalculationInput("jumlah awal", quantity, unit, FieldId("quantity")), CalculationInput("efisiensi", 90.0, UnitCode.PERCENT, evidenceSource="ai-api:yield-model")), "usable_mass", "jumlah awal × efisiensi", usable, unit, "Efisiensi adalah perkiraan; ukur ulang sebelum produksi."),
            MathCalculation("yield", listOf(CalculationInput("bahan yang bisa dipakai", usable, unit, evidenceSource="derived:usable_mass"), CalculationInput("kebutuhan per produk", perProduct, unit, evidenceSource="ai-api:product-requirement")), "product_yield", "bahan bisa dipakai ÷ kebutuhan", 3.0, UnitCode.PCS, "Hasil dibulatkan ke bawah."),
            MathCalculation("cost", listOf(CalculationInput("bahan yang bisa dipakai", usable, unit, evidenceSource="derived:usable_mass"), CalculationInput("bahan awal", quantity, unit, FieldId("quantity"))), "cost_efficiency", "bahan bisa dipakai ÷ bahan awal × 100", 90.0, UnitCode.PERCENT, "Belum memasukkan alat dan tenaga."),
            MathCalculation("margin", listOf(CalculationInput("potensi ekonomi", economicPotential, UnitCode.PERCENT, evidenceSource="ai-api:economic-model"), CalculationInput("kecukupan bahan", materialSufficiency, UnitCode.PERCENT, evidenceSource="derived:usable-minimum")), "margin_readiness", "(potensi + kecukupan) ÷ 2", marginReadiness, UnitCode.PERCENT, "Indikator, bukan keuntungan final."),
        )
    }

    private fun usableQuantity(quantity: Double): Double = quantity * .90

    private fun scoreComponents(
        requirements: List<ProductParameterRequirement>,
        observations: List<Observation>,
        usableQuantity: Double,
        initialQuantity: Double,
        minimumQuantity: Double,
        estimatedUsedQuantity: Double,
        economicPotential: Double,
    ): ScoreComponents {
        val byId = observations.associateBy(Observation::fieldId)
        val satisfied = requirements.count { requirement -> requirement.isSatisfiedBy(byId[requirement.fieldId]) }
        val propertyFit = satisfied.toDouble() / requirements.size.toDouble() * 100.0
        val materialSufficiency = (usableQuantity / minimumQuantity).coerceIn(0.0, 1.0) * 100.0
        val residueReduction = (estimatedUsedQuantity / initialQuantity).coerceIn(0.0, 1.0) * 100.0
        return ScoreComponents(propertyFit, materialSufficiency, economicPotential, residueReduction)
    }

    private fun productRequirements(category: MaterialCategory): List<ProductParameterRequirement> = listOf(
        ProductParameterRequirement(FieldId("quantity"), minimumNumeric = .01),
        ProductParameterRequirement(FieldId("condition"), acceptedChoices = listOf("good", "worn")),
        ProductParameterRequirement(FieldId("contamination"), acceptedChoices = listOf("none", "low")),
    )

    private fun ProductParameterRequirement.isSatisfiedBy(observation: Observation?): Boolean {
        if (observation?.availability != Availability.PROVIDED || observation.value == null) return false
        if (anyProvided) return true
        if (acceptedChoices.isNotEmpty()) return (observation.value as? InspectionValue.Choice)?.value in acceptedChoices
        val numeric = when (val value = observation.value) {
            is InspectionValue.Decimal -> value.value
            is InspectionValue.Whole -> value.value.toDouble()
            else -> return false
        }
        return (minimumNumeric == null || numeric >= minimumNumeric) &&
            (maximumNumeric == null || numeric <= maximumNumeric)
    }

    private fun scoreEvidence(components: ScoreComponents): List<CalculationInput> = listOf(
        CalculationInput("propertyFit", components.propertyFit, UnitCode.PERCENT, evidenceSource = "derived:required-parameters"),
        CalculationInput("materialSufficiency", components.materialSufficiency, UnitCode.PERCENT, evidenceSource = "derived:usable-minimum"),
        CalculationInput("economicPotential", components.economicPotential, UnitCode.PERCENT, evidenceSource = "ai-api:economic-model"),
        CalculationInput("residueReduction", components.residueReduction, UnitCode.PERCENT, evidenceSource = "derived:usable-initial"),
    )

    private fun candidates(category: MaterialCategory, confidence: Double): List<RankedCategoryPrediction> =
        if (confidence >= .80) listOf(RankedCategoryPrediction(category, confidence))
        else listOf(
            RankedCategoryPrediction(category, confidence),
            RankedCategoryPrediction(MaterialCategory.entries.first { it != category }, (confidence - .13).coerceAtLeast(.10)),
            RankedCategoryPrediction(MaterialCategory.entries.last { it != category }, (confidence - .27).coerceAtLeast(.05)),
        )

    private fun defaultObservation(
        category: MaterialCategory,
        field: RequestedField,
        desiredSafety: SafetyOutcome,
    ): Observation {
        if (!field.required) {
            return Observation(field.id, unit = field.unit, availability = Availability.NOT_AVAILABLE)
        }
        val value: InspectionValue = when (field.type) {
            InspectionFieldType.TEXT -> InspectionValue.Text(if (field.id.value == "source_location") "Workshop sekolah" else "Terlihat sesuai")
            InspectionFieldType.DECIMAL -> InspectionValue.Decimal(
                when (field.id.value) { "quantity" -> 2.5; "length" -> 4.0; "area" -> 3.0; else -> field.minimum ?: 1.0 },
            )
            InspectionFieldType.WHOLE -> InspectionValue.Whole((field.minimum ?: 1.0).toInt())
            InspectionFieldType.BOOLEAN -> InspectionValue.BooleanValue(false)
            InspectionFieldType.CHOICE -> InspectionValue.Choice(
                when (field.id.value) {
                    "condition" -> if (desiredSafety == SafetyOutcome.CAUTION) "unknown" else "good"
                    "contamination" -> if (desiredSafety == SafetyOutcome.BLOCK) "suspected_hazardous" else "none"
                    "corrosion" -> "none"
                    "rot_mold" -> "none"
                    "wet_mold" -> if (category == MaterialCategory.TEXTILE && desiredSafety == SafetyOutcome.CAUTION) "light" else "no"
                    "oil_chemical" -> "no"
                    "treatment" -> if (category == MaterialCategory.WOOD && desiredSafety == SafetyOutcome.CAUTION) "unknown" else "no"
                    "coating_oil" -> "none"
                    "insulation" -> "intact"
                    "chemical_contact" -> "no"
                    "battery" -> if (category == MaterialCategory.ELECTRONICS && desiredSafety == SafetyOutcome.BLOCK) "yes" else "no"
                    "battery_damage", "burn_marks", "powered", "sharp_edges", "exposed_conductor", "melted_evidence", "embedded_metal" -> "no"
                    else -> field.choices.first()
                },
            )
        }
        return Observation(field.id, value, field.unit, ValueSource.USER, Availability.PROVIDED)
    }

    private fun categoryPrompt(category: MaterialCategory): String = when (category) {
        MaterialCategory.METAL -> "Adakah karat, lapisan minyak, atau tepi tajam?"
        MaterialCategory.CABLE -> "Bagaimana kondisi isolasi dan konduktor kabel?"
        MaterialCategory.PLASTIC -> "Adakah kode resin, bekas meleleh, atau paparan bahan kimia?"
        MaterialCategory.WOOD -> "Adakah jamur, pelapukan, atau lapisan pengawet?"
        MaterialCategory.TEXTILE -> "Adakah label serat, jamur, minyak, atau bahan kimia?"
        MaterialCategory.ELECTRONICS -> "Adakah baterai, bekas terbakar, atau komponen yang masih berdaya?"
    }

    private fun principle(category: MaterialCategory): String = when (category) {
        MaterialCategory.METAL -> "Korosi, lapisan permukaan, dan tepi tajam memengaruhi proses potong, sambung, serta finishing logam."
        MaterialCategory.CABLE -> "Keutuhan isolasi dan kondisi konduktor menentukan apakah kabel layak dijadikan material non-listrik."
        MaterialCategory.PLASTIC -> "Kode resin dan riwayat panas membantu menentukan teknik potong atau pembentukan yang lebih rendah risiko."
        MaterialCategory.WOOD -> "Kelembapan, pelapukan, dan perlakuan kimia memengaruhi kekuatan sambungan dan keamanan pengerjaan kayu."
        MaterialCategory.TEXTILE -> "Jenis serat, kelembapan, dan kontaminasi memengaruhi pencucian, jahitan, serta ketahanan produk."
        MaterialCategory.ELECTRONICS -> "Baterai dan bekas panas memerlukan isolasi serta penanganan limbah elektronik sebelum pembongkaran."
    }

    private fun interpretation(category: MaterialCategory): String =
        "Ciri yang dilaporkan konsisten dengan ${category.displayName.lowercase()} dan cukup untuk menyusun rekomendasi awal bersyarat."

    private fun verification(category: MaterialCategory): String = when (category) {
        MaterialCategory.ELECTRONICS -> "Jangan menyalakan perangkat; minta teknisi memeriksa baterai dan sisa tegangan sebelum dibongkar."
        MaterialCategory.CABLE -> "Pastikan kabel tidak terhubung listrik dan minta pengrajin memeriksa isolasinya sebelum dipotong."
        else -> "Periksa permukaan secara langsung, ukur ulang jumlahnya, lalu minta pengrajin mengonfirmasi teknik pengerjaan."
    }

    private fun safetyReasons(category: MaterialCategory, outcome: SafetyOutcome): List<String> = when (outcome) {
        SafetyOutcome.ALLOW -> listOf("Gunakan pelindung yang sesuai dan minta pengrajin memeriksa material sebelum proses produksi.")
        SafetyOutcome.CAUTION -> listOf("Kondisi ${category.displayName.lowercase()} perlu diverifikasi langsung sebelum dipotong atau dibentuk.")
        SafetyOutcome.BLOCK -> listOf("Baterai atau bekas panas harus ditangani teknisi; rekomendasi produk ditahan sampai pemeriksaan selesai.")
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
        else -> {
            val selected = request.manualCategory?.let(AnalysisFixtures::forCategory) ?: fixture()
            Result.Success(selected.initial.copy(analysisId = request.analysisId))
        }
    }

    override suspend fun complete(request: CompletedAnalysisRequest): Result<CompletedAnalysisResponse> = when (scenario) {
        Scenario.TIMEOUT -> Result.Failure(DomainFailure.Timeout)
        Scenario.OFFLINE -> Result.Failure(DomainFailure.Offline)
        Scenario.MALFORMED -> Result.Failure(DomainFailure.MalformedResponse)
        Scenario.UNSUPPORTED_IMAGE -> Result.Failure(DomainFailure.UnsupportedImage)
        Scenario.UNAVAILABLE -> Result.Failure(DomainFailure.Unavailable)
        else -> Result.Success(AnalysisFixtures.completedFor(request))
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

/** Release-safe fallback until the deployment injects its HTTPS API configuration. */
class UnconfiguredAiAnalysisGateway : AiAnalysisGateway {
    override suspend fun start(request: InitialAnalysisRequest): Result<InitialAnalysisResponse> =
        Result.Failure(DomainFailure.Unavailable)

    override suspend fun complete(request: CompletedAnalysisRequest): Result<CompletedAnalysisResponse> =
        Result.Failure(DomainFailure.Unavailable)
}
