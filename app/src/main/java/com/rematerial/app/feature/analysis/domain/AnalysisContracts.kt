package com.rematerial.app.feature.analysis.domain

import com.rematerial.app.core.model.AnalysisId
import com.rematerial.app.core.model.Availability
import com.rematerial.app.core.model.DomainFailure
import com.rematerial.app.core.model.FieldId
import com.rematerial.app.core.model.FindingId
import com.rematerial.app.core.model.InspectionValue
import com.rematerial.app.core.model.MaterialCategory
import com.rematerial.app.core.model.Observation
import com.rematerial.app.core.model.ProductOptionId
import com.rematerial.app.core.model.Result
import com.rematerial.app.core.model.SafetyOutcome
import com.rematerial.app.core.model.SourceId
import com.rematerial.app.core.model.UnitCode
import com.rematerial.app.core.model.ValueSource
import kotlinx.serialization.Serializable

@Serializable
data class PhotoReference(
    val mediaId: String,
    val privatePath: String,
    val contentType: String,
    val sizeBytes: Long,
) {
    init { require(mediaId.isNotBlank() && privatePath.isNotBlank() && sizeBytes > 0) }
}

@Serializable
data class InitialAnalysisRequest(
    val analysisId: AnalysisId,
    val photo: PhotoReference? = null,
    val manualCategory: MaterialCategory? = null,
)

@Serializable
data class CategoryPrediction(
    val category: MaterialCategory,
    val confidence: Double,
    val rankedCandidates: List<RankedCategoryPrediction> = listOf(RankedCategoryPrediction(category, confidence)),
) {
    init { require(confidence.isFinite() && confidence in 0.0..1.0) }
}

@Serializable
data class RankedCategoryPrediction(
    val category: MaterialCategory,
    val confidence: Double,
) {
    init { require(confidence.isFinite() && confidence in 0.0..1.0) }
}

@Serializable
enum class InspectionFieldType { TEXT, DECIMAL, WHOLE, BOOLEAN, CHOICE }

@Serializable
data class RequestedField(
    val id: FieldId,
    val label: String,
    val type: InspectionFieldType,
    val unit: UnitCode? = null,
    val required: Boolean,
    val choices: List<String> = emptyList(),
    val minimum: Double? = null,
    val maximum: Double? = null,
    val maximumLength: Int? = null,
    val allowUnavailable: Boolean = !required,
)

@Serializable
data class InitialAnalysisResponse(
    val analysisId: AnalysisId,
    val prediction: CategoryPrediction,
    val requestedFields: List<RequestedField>,
)

@Serializable
data class CompletedAnalysisRequest(
    val analysisId: AnalysisId,
    val category: MaterialCategory,
    val observations: List<Observation>,
)

@Serializable
data class ScienceFinding(
    val findingId: FindingId,
    val title: String,
    val observationRefs: List<FieldId>,
    val principle: String,
    val sourceRefs: List<SourceId>,
    val interpretation: String,
    val limitation: String,
    val recommendedVerification: String,
)

@Serializable
data class CalculationInput(
    val name: String,
    val value: Double,
    val unit: UnitCode,
    val observationRef: FieldId? = null,
    val evidenceSource: String? = null,
)

@Serializable
data class MathCalculation(
    val calculationId: String,
    val inputs: List<CalculationInput>,
    val formulaId: String,
    val formulaExpression: String,
    val result: Double,
    val unit: UnitCode,
    val limitations: String,
)

@Serializable
data class SafetyAssessment(
    val outcome: SafetyOutcome,
    val reasons: List<String>,
)

@Serializable
data class ScoreComponents(
    val propertyFit: Double,
    val materialSufficiency: Double,
    val economicPotential: Double,
    val residueReduction: Double,
)

@Serializable
data class ProductParameterRequirement(
    val fieldId: FieldId,
    val acceptedChoices: List<String> = emptyList(),
    val minimumNumeric: Double? = null,
    val maximumNumeric: Double? = null,
    val anyProvided: Boolean = false,
)

@Serializable
data class ProductOption(
    val optionId: ProductOptionId,
    val title: String,
    val explanation: String,
    val requiredMaterial: String,
    val requiredParameterIds: List<FieldId>,
    val parameterRequirements: List<ProductParameterRequirement>,
    val minimumQuantity: Double,
    val minimumUnit: UnitCode,
    val estimatedUsedQuantity: Double,
    val estimatedUsedUnit: UnitCode,
    val requiredToolIds: List<String>,
    val requiredSkillIds: List<String>,
    val prerequisiteFieldIds: List<FieldId> = emptyList(),
    val scoreFormulaId: String,
    val scoreFormulaExpression: String,
    val scoreEvidenceInputs: List<CalculationInput>,
    val scoreComponents: ScoreComponents,
    val provisionalProductScore: Double,
)

@Serializable
data class CompletedAnalysisResponse(
    val analysisId: AnalysisId,
    val category: MaterialCategory,
    val confidence: Double,
    val observations: List<Observation>,
    val science: List<ScienceFinding>,
    val mathematics: List<MathCalculation>,
    val safety: SafetyAssessment,
    val productOptions: List<ProductOption>,
) {
    init { require(confidence.isFinite() && confidence in 0.0..1.0) }
}

interface AiAnalysisGateway {
    suspend fun start(request: InitialAnalysisRequest): Result<InitialAnalysisResponse>
    suspend fun complete(
        request: CompletedAnalysisRequest,
    ): Result<CompletedAnalysisResponse>
}

enum class AnalysisConfirmation {
    CORRECTION_OPTIONAL,
    ALTERNATIVE_REQUIRED,
    MANUAL_REQUIRED;

    companion object {
        fun from(confidence: Double): AnalysisConfirmation = when {
            confidence >= 0.80 -> CORRECTION_OPTIONAL
            confidence >= 0.50 -> ALTERNATIVE_REQUIRED
            else -> MANUAL_REQUIRED
        }
    }
}

object AnalysisConfirmationPolicy {
    fun canContinue(schemaReady: Boolean, selected: MaterialCategory?, explicitlyConfirmed: Boolean): Boolean =
        schemaReady && selected != null && explicitlyConfirmed
}

@Serializable
sealed interface FieldAnswer {
    @Serializable
    data class Value(val raw: String) : FieldAnswer

    @Serializable
    data object Unavailable : FieldAnswer
}

data class InputValidationResult(val fieldErrors: Map<String, String>) {
    val isValid: Boolean get() = fieldErrors.isEmpty()
}

object AnalysisInputValidator {
    fun validate(fields: List<RequestedField>, answers: Map<String, FieldAnswer>): InputValidationResult {
        val errors = buildMap {
            fields.forEach { field ->
                when (val answer = answers[field.id.value]) {
                    null -> if (field.required) put(field.id.value, "Bagian ini wajib diisi.")
                    FieldAnswer.Unavailable -> if (!field.allowUnavailable) put(field.id.value, "Bagian ini perlu diisi agar analisis bisa dilanjutkan.")
                    is FieldAnswer.Value -> validateValue(field, answer.raw)?.let { put(field.id.value, it) }
                }
            }
        }
        return InputValidationResult(errors)
    }

    private fun validateValue(field: RequestedField, rawValue: String): String? {
        val raw = rawValue.trim()
        if (raw.isEmpty()) return if (field.required) "Bagian ini wajib diisi." else null
        if (field.maximumLength != null && raw.length > field.maximumLength) return "Maksimal ${field.maximumLength} karakter."
        return when (field.type) {
            InspectionFieldType.DECIMAL -> {
                val value = raw.replace(',', '.').toDoubleOrNull()
                when {
                    value == null || !value.isFinite() -> "Masukkan angka yang valid."
                    field.minimum != null && value < field.minimum || field.maximum != null && value > field.maximum ->
                        if (field.id.value == "quantity") "Masukkan nilai 0,01 sampai 100.000." else "Nilai berada di luar batas yang diperbolehkan."
                    else -> null
                }
            }
            InspectionFieldType.WHOLE -> {
                val value = raw.toIntOrNull()
                when {
                    value == null -> "Masukkan bilangan bulat."
                    field.minimum != null && value < field.minimum || field.maximum != null && value > field.maximum -> "Nilai berada di luar batas yang diperbolehkan."
                    else -> null
                }
            }
            InspectionFieldType.CHOICE -> if (raw !in field.choices) "Pilih salah satu jawaban yang tersedia." else null
            InspectionFieldType.BOOLEAN -> if (raw !in setOf("true", "false")) "Pilih Ya atau Tidak." else null
            InspectionFieldType.TEXT -> null
        }
    }
}

object AnalysisResponseValidator {
    fun initial(
        expectedAnalysisId: AnalysisId,
        requestedCategory: MaterialCategory?,
        response: InitialAnalysisResponse,
    ): Result<Unit> {
        if (response.analysisId != expectedAnalysisId) return Result.Failure(DomainFailure.MalformedResponse)
        if (requestedCategory != null && response.prediction.category != requestedCategory) return Result.Failure(DomainFailure.UnsupportedSchema)
        return AnalysisValidator.validate(response)
    }

    fun completed(
        expectedAnalysisId: AnalysisId,
        expectedCategory: MaterialCategory,
        response: CompletedAnalysisResponse,
        submittedObservations: List<Observation>? = null,
        requestedFields: List<RequestedField>? = null,
    ): Result<Unit> {
        if (response.analysisId != expectedAnalysisId) return Result.Failure(DomainFailure.MalformedResponse)
        if (response.category != expectedCategory) return Result.Failure(DomainFailure.UnsupportedSchema)
        if (submittedObservations != null && response.observations != submittedObservations) return Result.Failure(DomainFailure.MalformedResponse)
        if (requestedFields != null && response.observations.map { it.fieldId } != requestedFields.map { it.id }) return Result.Failure(DomainFailure.UnsupportedSchema)
        return AnalysisValidator.validate(response, requestedFields)
    }
}

@Serializable
enum class AnalysisFlowPhase { SCAN, PREVIEW, CONFIRM, INPUTS, RESULT, IDEAS }

@Serializable
data class AnalysisSession(
    val analysisId: AnalysisId,
    val photo: PhotoReference? = null,
    val initial: InitialAnalysisResponse? = null,
    val selectedCategory: MaterialCategory? = null,
    val answers: Map<String, FieldAnswer> = emptyMap(),
    val result: CompletedAnalysisResponse? = null,
    val selectedOptionId: ProductOptionId? = null,
    val categoryConfirmed: Boolean = false,
    val isManual: Boolean = false,
    val phase: AnalysisFlowPhase = AnalysisFlowPhase.SCAN,
)

@Serializable
data class SavedAnalysisIdea(
    val analysisId: AnalysisId,
    val optionId: ProductOptionId,
    val result: CompletedAnalysisResponse,
    val photo: PhotoReference? = null,
)

data class AnalysisPersistenceSnapshot(
    val session: AnalysisSession?,
    val savedIdeas: List<SavedAnalysisIdea>,
    val committedMediaPaths: Set<String>,
)

interface AnalysisSessionRepository {
    suspend fun loadSnapshot(): Result<AnalysisPersistenceSnapshot>
    suspend fun loadSession(): Result<AnalysisSession?>
    suspend fun saveSession(session: AnalysisSession): Result<Unit>
    suspend fun clearSession(): Result<Unit>
    suspend fun saveIdea(idea: SavedAnalysisIdea): Result<Unit>
    suspend fun savedIdeas(): Result<List<SavedAnalysisIdea>>
}

object AnalysisCatalog {
    private val commonSchema = listOf(
        RequestedField(FieldId("source_location"), "Asal atau lokasi bahan", InspectionFieldType.TEXT, required = true, maximumLength = 120),
        RequestedField(FieldId("condition"), "Kondisi umum", InspectionFieldType.CHOICE, required = true, choices = listOf("good", "worn", "damaged", "unknown")),
        RequestedField(FieldId("contamination"), "Adakah kotoran atau kontaminasi?", InspectionFieldType.CHOICE, required = true, choices = listOf("none", "low", "unknown", "suspected_hazardous")),
        RequestedField(FieldId("notes"), "Catatan tambahan", InspectionFieldType.TEXT, required = false, maximumLength = 500, allowUnavailable = true),
    )
    private fun f(id:String,label:String,type:InspectionFieldType,required:Boolean=false,unit:UnitCode?=null,choices:List<String> = emptyList(),min:Double?=null,max:Double?=null,maxLen:Int?=null)=RequestedField(FieldId(id),label,type,unit,required,choices,min,max,maxLen,!required)
    private val categorySchema = mapOf(
        MaterialCategory.METAL to listOf(f("piece_count","Jumlah potong",InspectionFieldType.WHOLE,unit=UnitCode.PCS,min=1.0,max=100000.0),f("dimensions_cm","Dimensi",InspectionFieldType.DECIMAL,unit=UnitCode.CM,min=0.0,max=100000.0),f("sharp_edges","Ada tepi tajam?",InspectionFieldType.CHOICE,true,choices=listOf("yes","no")),f("corrosion","Karat",InspectionFieldType.CHOICE,true,choices=listOf("none","light","heavy")),f("coating_oil","Pelapis atau minyak",InspectionFieldType.CHOICE,true,choices=listOf("none","present","unknown")),f("family","Keluarga logam",InspectionFieldType.TEXT,maxLen=120),f("thickness_mm","Ketebalan",InspectionFieldType.DECIMAL,unit=UnitCode.MM,min=0.0,max=100000.0)),
        MaterialCategory.CABLE to listOf(f("mass_kg","Berat",InspectionFieldType.DECIMAL,unit=UnitCode.KG,min=.01,max=100000.0),f("powered","Masih terhubung atau berdaya?",InspectionFieldType.CHOICE,true,choices=listOf("yes","no","unknown")),f("exposed_conductor","Konduktor terbuka?",InspectionFieldType.CHOICE,true,choices=listOf("yes","no")),f("insulation","Kondisi isolasi",InspectionFieldType.CHOICE,true,choices=listOf("intact","damaged","unknown")),f("conductor_count","Jumlah konduktor",InspectionFieldType.WHOLE,unit=UnitCode.PCS,min=1.0,max=100000.0),f("outer_diameter_mm","Diameter luar",InspectionFieldType.DECIMAL,unit=UnitCode.MM,min=0.0,max=100000.0)),
        MaterialCategory.PLASTIC to listOf(f("dimensions_cm","Dimensi",InspectionFieldType.DECIMAL,unit=UnitCode.CM,min=0.0,max=100000.0),f("resin_code","Kode resin",InspectionFieldType.CHOICE,true,choices=listOf("1","2","3","4","5","6","7","unknown")),f("chemical_contact","Kontak bahan kimia",InspectionFieldType.CHOICE,true,choices=listOf("no","yes","unknown")),f("melted_evidence","Bekas meleleh?",InspectionFieldType.CHOICE,true,choices=listOf("yes","no")),f("color","Warna",InspectionFieldType.TEXT,maxLen=80),f("flexibility","Kelenturan",InspectionFieldType.CHOICE,choices=listOf("rigid","flexible"))),
        MaterialCategory.WOOD to listOf(f("avg_length_cm","Panjang rata-rata",InspectionFieldType.DECIMAL,unit=UnitCode.CM,min=0.0,max=100000.0),f("avg_width_cm","Lebar rata-rata",InspectionFieldType.DECIMAL,unit=UnitCode.CM,min=0.0,max=100000.0),f("avg_thickness_cm","Tebal rata-rata",InspectionFieldType.DECIMAL,unit=UnitCode.CM,min=0.0,max=100000.0),f("mass_kg","Berat",InspectionFieldType.DECIMAL,unit=UnitCode.KG,min=.01,max=100000.0),f("treatment","Pernah diberi perlakuan?",InspectionFieldType.CHOICE,true,choices=listOf("no","yes","unknown")),f("rot_mold","Lapuk atau jamur",InspectionFieldType.CHOICE,true,choices=listOf("none","light","heavy")),f("embedded_metal","Ada logam tertanam?",InspectionFieldType.CHOICE,true,choices=listOf("yes","no")),f("moisture","Kelembapan",InspectionFieldType.DECIMAL,unit=UnitCode.PERCENT,min=0.0,max=100.0),f("crack_count","Jumlah retak",InspectionFieldType.WHOLE,unit=UnitCode.PCS,min=0.0,max=100000.0)),
        MaterialCategory.TEXTILE to listOf(f("width_m","Lebar",InspectionFieldType.DECIMAL,unit=UnitCode.M,min=0.0,max=100000.0),f("area_m2","Luas",InspectionFieldType.DECIMAL,unit=UnitCode.M2,min=.01,max=100000.0),f("piece_count","Jumlah lembar",InspectionFieldType.WHOLE,unit=UnitCode.PCS,min=1.0,max=100000.0),f("wet_mold","Lembap atau jamur",InspectionFieldType.CHOICE,true,choices=listOf("no","light","heavy")),f("oil_chemical","Minyak atau kimia",InspectionFieldType.CHOICE,true,choices=listOf("no","yes","unknown")),f("fiber_label","Label serat",InspectionFieldType.TEXT,maxLen=120),f("torn_area","Luas sobek",InspectionFieldType.DECIMAL,unit=UnitCode.M2,min=0.0,max=100000.0),f("color","Warna",InspectionFieldType.TEXT,maxLen=80)),
        MaterialCategory.ELECTRONICS to listOf(f("device_type","Jenis perangkat",InspectionFieldType.TEXT,true,maxLen=120),f("battery","Ada baterai?",InspectionFieldType.CHOICE,true,choices=listOf("no","yes","unknown")),f("battery_damage","Baterai rusak?",InspectionFieldType.CHOICE,true,choices=listOf("no","yes","unknown")),f("burn_marks","Ada bekas terbakar?",InspectionFieldType.CHOICE,true,choices=listOf("yes","no")),f("powered","Masih berdaya?",InspectionFieldType.CHOICE,true,choices=listOf("yes","no","unknown")),f("dismantled","Sudah dibongkar?",InspectionFieldType.CHOICE,choices=listOf("yes","no")),f("board_type","Jenis papan",InspectionFieldType.TEXT,maxLen=120)),
    )
    val commonFields = (commonSchema.map(RequestedField::id) + FieldId("quantity")).toSet()
    val categoryFields = categorySchema.mapValues { (_, fields) -> fields.mapTo(linkedSetOf(), RequestedField::id) }
    val sourceIds = setOf(
        SourceId("rematerial-material-procedure"), SourceId("school-material-safety"),
    )
    fun recognized(category: MaterialCategory): Set<FieldId> = commonFields + categoryFields.getValue(category)
    fun schemaFor(category: MaterialCategory, quantityUnit: UnitCode? = null): List<RequestedField> =
        listOf(commonSchema.first(), quantityField(category, quantityUnit)) + commonSchema.drop(1) + categorySchema.getValue(category)

    fun contractFor(category: MaterialCategory, id: FieldId): RequestedField? = schemaFor(category).firstOrNull { it.id == id }
    fun isCompatible(category: MaterialCategory, field: RequestedField): Boolean {
        if (field.id.value != "quantity") return field == contractFor(category, field.id)
        return runCatching { field == quantityField(category, field.unit) }.getOrDefault(false)
    }

    private fun quantityField(category: MaterialCategory, requestedUnit: UnitCode? = null): RequestedField {
        val defaultUnit = when (category) {
            MaterialCategory.CABLE -> UnitCode.M
            MaterialCategory.TEXTILE -> UnitCode.M
            MaterialCategory.ELECTRONICS -> UnitCode.PCS
            MaterialCategory.WOOD -> UnitCode.PCS
            else -> UnitCode.KG
        }
        val allowedUnits = when (category) {
            MaterialCategory.PLASTIC -> setOf(UnitCode.KG, UnitCode.PCS)
            MaterialCategory.TEXTILE -> setOf(UnitCode.M, UnitCode.M2)
            else -> setOf(defaultUnit)
        }
        val unit = requestedUnit ?: defaultUnit
        require(unit in allowedUnits)
        val label = when {
            category == MaterialCategory.PLASTIC && unit == UnitCode.PCS -> "Jumlah potong bahan"
            category == MaterialCategory.ELECTRONICS && unit == UnitCode.PCS -> "Jumlah unit perangkat"
            category == MaterialCategory.WOOD && unit == UnitCode.PCS -> "Jumlah potong bahan"
            unit == UnitCode.M -> "Perkiraan panjang bahan"
            unit == UnitCode.M2 -> "Perkiraan luas bahan"
            else -> "Perkiraan berat bahan"
        }
        return RequestedField(FieldId("quantity"), label, InspectionFieldType.DECIMAL, unit, true, minimum = .01, maximum = 100_000.0)
    }
}

object AnalysisValidator {
    fun validate(response: InitialAnalysisResponse): Result<Unit> {
        val candidates = response.prediction.rankedCandidates
        val validRanking = candidates.isNotEmpty() && candidates.size <= 3 &&
            (response.prediction.confidence !in .50..<.80 || candidates.size >= 2) &&
            candidates.first().category == response.prediction.category &&
            kotlin.math.abs(candidates.first().confidence - response.prediction.confidence) < .0001 &&
            candidates.map { it.category }.distinct().size == candidates.size &&
            candidates.zipWithNext().all { (left, right) -> left.confidence >= right.confidence }
        val fields = response.requestedFields
        val ids = fields.map { it.id }
        val requiredCommon = AnalysisCatalog.commonFields.filter { AnalysisCatalog.contractFor(response.prediction.category, it)?.required == true }
        val requiredCategory = AnalysisCatalog.schemaFor(response.prediction.category).filter { it.required && it.id in AnalysisCatalog.categoryFields.getValue(response.prediction.category) }.map { it.id }
        val contractsValid = ids.size == ids.distinct().size && requiredCommon.all { it in ids } &&
            requiredCategory.all { it in ids } && fields.all { AnalysisCatalog.isCompatible(response.prediction.category, it) }
        return if (!contractsValid || !validRanking) {
            Result.Failure(DomainFailure.UnsupportedSchema)
        } else Result.Success(Unit)
    }

    fun validate(response: CompletedAnalysisResponse, requestedFields: List<RequestedField>? = null): Result<Unit> {
        val violations = mutableListOf<String>()
        if (!response.confidence.isFinite() || response.confidence !in 0.0..1.0) violations += "Invalid confidence"
        if (response.science.isEmpty()) violations += "Science evidence missing"
        if (response.mathematics.isEmpty()) violations += "Calculation evidence missing"
        if (response.observations.map { it.fieldId }.distinct().size != response.observations.size) violations += "Duplicate observation"
        val observations = response.observations.associateBy { it.fieldId }
        requestedFields?.forEach { field ->
            val observation = observations[field.id]
            if (observation == null) violations += "Missing observation ${field.id.value}"
            else if (field.required && observation.availability != Availability.PROVIDED) violations += "Required observation unavailable ${field.id.value}"
            else if (!observation.matches(field)) violations += "Observation contract mismatch ${field.id.value}"
        }
        response.observations.forEach { observation ->
            if (observation.fieldId !in AnalysisCatalog.recognized(response.category)) violations += "Unknown field ${observation.fieldId.value}"
            if (observation.value is InspectionValue.Decimal && !observation.value.value.isFinite()) violations += "Non-finite observation"
            if (observation.availability == Availability.PROVIDED && observation.value == null) violations += "Provided observation has no value"
            if (observation.availability == Availability.NOT_AVAILABLE && observation.value != null) violations += "Unavailable observation cannot have a value"
            AnalysisCatalog.contractFor(response.category, observation.fieldId)?.let { if (!observation.matches(it.copy(unit = observation.unit))) violations += "Observation type mismatch ${observation.fieldId.value}" }
        }
        val quantity = observations[FieldId("quantity")]
        if (quantity?.availability == Availability.PROVIDED) {
            val value = (quantity.value as? InspectionValue.Decimal)?.value
            if (value == null || !value.isFinite() || value !in 0.01..100_000.0) violations += "Quantity must be between 0.01 and 100000"
            if (quantity.unit !in setOf(UnitCode.KG, UnitCode.G, UnitCode.M, UnitCode.M2, UnitCode.PCS)) violations += "Quantity unit invalid"
        }
        val location = observations[FieldId("source_location")]?.value as? InspectionValue.Text
        if (location != null && location.value.length !in 1..120) violations += "Source location length invalid"
        val notes = observations[FieldId("notes")]?.value as? InspectionValue.Text
        if (notes != null && notes.value.length > 500) violations += "Notes length invalid"
        response.science.forEach { finding ->
            if (finding.findingId.value.isBlank() || finding.title.isBlank()) violations += "Science identity missing"
            if (finding.observationRefs.isEmpty() || finding.observationRefs.any { it !in observations }) violations += "Science observation reference unresolved"
            if (finding.sourceRefs.isEmpty() || finding.sourceRefs.any { it !in AnalysisCatalog.sourceIds }) violations += "Science source reference unresolved"
            if (finding.principle.isBlank()) violations += "Science principle missing"
            if (finding.interpretation.isBlank()) violations += "Science interpretation missing"
            if (finding.limitation.isBlank()) violations += "Science limitation missing"
            if (finding.recommendedVerification.isBlank()) violations += "Science verification missing"
        }
        response.mathematics.forEach { calculation ->
            if (calculation.inputs.isEmpty() || calculation.inputs.any { !it.value.isFinite() }) violations += "Math input evidence missing"
            if (calculation.formulaId.isBlank() || calculation.formulaExpression.isBlank()) violations += "Math formula missing"
            if (!calculation.result.isFinite()) violations += "Math result invalid"
            if (calculation.unit == UnitCode.NONE) violations += "Math result unit missing"
            if (calculation.limitations.isBlank()) violations += "Math limitation missing"
            if (!CalculationPolicy.matches(calculation)) violations += "Calculation result does not match evidence"
            calculation.inputs.forEach { input ->
                if (input.observationRef != null) { if (!input.matchesObservation(observations[input.observationRef])) violations += "Calculation input evidence mismatch" }
                else if (input.evidenceSource.isNullOrBlank()) violations += "Calculation input source missing"
            }
        }
        if (response.safety.reasons.isEmpty() || response.safety.reasons.any(String::isBlank)) violations += "Safety reasons missing"
        if (response.safety.outcome != SafetyPolicy.assess(response.category, observations)) violations += "Safety does not match observations"
        if (response.safety.outcome == SafetyOutcome.BLOCK && response.productOptions.isNotEmpty()) violations += "Block cannot contain product options"
        response.productOptions.forEach { option ->
            if (option.requiredMaterial.isBlank()) violations += "Required material missing"
            if (option.requiredParameterIds.isEmpty()) violations += "Property-fit denominator is zero"
            if (option.requiredParameterIds.any { it !in observations }) violations += "Required product parameter unresolved"
            if (option.requiredParameterIds != option.parameterRequirements.map(ProductParameterRequirement::fieldId) ||
                option.parameterRequirements.map(ProductParameterRequirement::fieldId).distinct().size != option.parameterRequirements.size
            ) violations += "Product parameter requirements must match IDs one-to-one"
            option.parameterRequirements.forEach { requirement ->
                if (!requirement.isValidFor(response.category)) violations += "Product parameter requirement invalid ${requirement.fieldId.value}"
            }
            if (option.minimumQuantity <= 0.0 || !option.minimumQuantity.isFinite()) violations += "Minimum quantity invalid"
            if (option.minimumUnit != observations[FieldId("quantity")]?.unit) violations += "Minimum quantity unit incompatible"
            if (!option.estimatedUsedQuantity.isFinite() || option.estimatedUsedQuantity <= 0.0) violations += "Estimated used quantity invalid"
            if (option.estimatedUsedUnit != option.minimumUnit) violations += "Estimated used unit incompatible"
            if (option.requiredToolIds.any(String::isBlank) || option.requiredToolIds.isEmpty()) violations += "Required tools missing"
            if (option.requiredSkillIds.any(String::isBlank) || option.requiredSkillIds.isEmpty()) violations += "Required skills missing"
            if (option.explanation.isBlank()) violations += "Product explanation missing"
            val components = listOf(option.scoreComponents.propertyFit, option.scoreComponents.materialSufficiency, option.scoreComponents.economicPotential, option.scoreComponents.residueReduction)
            if (components.any { !it.isFinite() || it !in 0.0..100.0 }) violations += "Score component invalid"
            if (!option.provisionalProductScore.isFinite() || option.provisionalProductScore !in 0.0..100.0) violations += "Provisional score invalid"
            if (kotlin.math.abs(ProductScorePolicy.calculate(option.scoreComponents) - option.provisionalProductScore) > .01) violations += "Provisional score formula mismatch"
            if (option.scoreFormulaId != ProductScorePolicy.FORMULA_ID || option.scoreFormulaExpression != ProductScorePolicy.FORMULA_EXPRESSION) violations += "Score formula identity mismatch"
            val expectedEvidenceNames = setOf("propertyFit", "materialSufficiency", "economicPotential", "residueReduction")
            val evidenceByName = option.scoreEvidenceInputs.associateBy(CalculationInput::name)
            if (option.scoreEvidenceInputs.size != expectedEvidenceNames.size || evidenceByName.keys != expectedEvidenceNames) {
                violations += "Score evidence missing"
            } else {
                val componentValues = mapOf(
                    "propertyFit" to option.scoreComponents.propertyFit,
                    "materialSufficiency" to option.scoreComponents.materialSufficiency,
                    "economicPotential" to option.scoreComponents.economicPotential,
                    "residueReduction" to option.scoreComponents.residueReduction,
                )
                evidenceByName.forEach { (name, evidence) ->
                    if (evidence.unit != UnitCode.PERCENT || evidence.evidenceSource.isNullOrBlank() ||
                        kotlin.math.abs(evidence.value - componentValues.getValue(name)) > .01
                    ) violations += "Score evidence mismatch $name"
                }
                if (!evidenceByName.getValue("economicPotential").evidenceSource.orEmpty().startsWith("ai-api:")) {
                    violations += "Economic evidence must come from provider"
                }
            }
            val initialQuantity = observations[FieldId("quantity")]?.numericValue()
            val usableCalculation = response.mathematics.firstOrNull { it.formulaId == "usable_mass" }
            val usableQuantity = usableCalculation?.result
            if (initialQuantity == null || usableQuantity == null || usableCalculation.unit != observations[FieldId("quantity")]?.unit) {
                violations += "Usable quantity evidence missing"
            } else {
                if (option.estimatedUsedQuantity > usableQuantity) violations += "Estimated used quantity exceeds usable quantity"
                val satisfiedRequirements = option.parameterRequirements.count { requirement ->
                    requirement.isSatisfiedBy(observations[requirement.fieldId])
                }
                val expectedPropertyFit = if (option.parameterRequirements.isEmpty()) 0.0 else {
                    satisfiedRequirements.toDouble() / option.parameterRequirements.size.toDouble() * 100.0
                }
                val expectedSufficiency = (usableQuantity / option.minimumQuantity).coerceIn(0.0, 1.0) * 100.0
                val expectedResidueReduction = (option.estimatedUsedQuantity / initialQuantity).coerceIn(0.0, 1.0) * 100.0
                if (kotlin.math.abs(expectedPropertyFit - option.scoreComponents.propertyFit) > .01) violations += "Property fit mismatch"
                if (kotlin.math.abs(expectedSufficiency - option.scoreComponents.materialSufficiency) > .01) violations += "Material sufficiency mismatch"
                if (kotlin.math.abs(expectedResidueReduction - option.scoreComponents.residueReduction) > .01) violations += "Residue reduction mismatch"
            }
            if (response.safety.outcome == SafetyOutcome.CAUTION && option.prerequisiteFieldIds.any { observations[it]?.availability != Availability.PROVIDED }) violations += "Caution prerequisite unmet"
        }
        response.productOptions.firstOrNull()?.let { primaryOption ->
            val margin = response.mathematics.firstOrNull { it.formulaId == "margin_readiness" }
            val expectedInputs = listOf(
                primaryOption.scoreComponents.economicPotential,
                primaryOption.scoreComponents.materialSufficiency,
            )
            if (margin == null || margin.unit != UnitCode.PERCENT || margin.inputs.size != expectedInputs.size ||
                margin.inputs.zip(expectedInputs).any { (input, expected) ->
                    input.unit != UnitCode.PERCENT || kotlin.math.abs(input.value - expected) > .01
                }
            ) violations += "Margin readiness evidence mismatch"
        }
        return if (violations.isEmpty()) Result.Success(Unit) else Result.Failure(DomainFailure.Validation(violations))
    }

    private fun Observation.matches(field: RequestedField): Boolean {
        if (availability == Availability.NOT_AVAILABLE) return !field.required && value == null
        if (value == null) return false
        val typeMatches = when (field.type) {
            InspectionFieldType.TEXT -> value is InspectionValue.Text
            InspectionFieldType.DECIMAL -> value is InspectionValue.Decimal
            InspectionFieldType.WHOLE -> value is InspectionValue.Whole
            InspectionFieldType.BOOLEAN -> value is InspectionValue.BooleanValue
            InspectionFieldType.CHOICE -> value is InspectionValue.Choice && value.value in field.choices
        }
        return typeMatches && unit == field.unit
    }

    private fun CalculationInput.matchesObservation(observation: Observation?): Boolean {
        val numeric = when (val observed = observation?.value) {
            is InspectionValue.Decimal -> observed.value
            is InspectionValue.Whole -> observed.value.toDouble()
            else -> return false
        }
        return observation.unit == unit && kotlin.math.abs(numeric - value) <= .0001
    }

    private fun ProductParameterRequirement.isValidFor(category: MaterialCategory): Boolean {
        val contract = AnalysisCatalog.contractFor(category, fieldId) ?: return false
        val hasChoiceConstraint = acceptedChoices.isNotEmpty()
        val hasNumericConstraint = minimumNumeric != null || maximumNumeric != null
        val modeCount = listOf(anyProvided, hasChoiceConstraint, hasNumericConstraint).count { it }
        if (modeCount != 1) return false
        if (hasChoiceConstraint) {
            if (contract.type != InspectionFieldType.CHOICE || acceptedChoices.distinct().size != acceptedChoices.size ||
                acceptedChoices.any { it !in contract.choices }
            ) return false
        }
        if (hasNumericConstraint) {
            if (contract.type !in setOf(InspectionFieldType.DECIMAL, InspectionFieldType.WHOLE)) return false
            if (minimumNumeric?.isFinite() == false || maximumNumeric?.isFinite() == false) return false
            if (minimumNumeric != null && maximumNumeric != null && minimumNumeric > maximumNumeric) return false
        }
        return true
    }

    private fun ProductParameterRequirement.isSatisfiedBy(observation: Observation?): Boolean {
        if (observation?.availability != Availability.PROVIDED || observation.value == null) return false
        if (anyProvided) return true
        if (acceptedChoices.isNotEmpty()) {
            return (observation.value as? InspectionValue.Choice)?.value in acceptedChoices
        }
        val numeric = observation.numericValue() ?: return false
        return (minimumNumeric == null || numeric >= minimumNumeric) &&
            (maximumNumeric == null || numeric <= maximumNumeric)
    }
    private fun Observation.numericValue(): Double? = when (val v = value) { is InspectionValue.Decimal -> v.value; is InspectionValue.Whole -> v.value.toDouble(); else -> null }
}

object ProductScorePolicy {
    const val FORMULA_ID = "rematerial_product_score_v1"
    const val FORMULA_EXPRESSION = "(0.30×property + 0.25×sufficiency + 0.15×economic + 0.10×residue) ÷ 0.80"
    fun calculate(components: ScoreComponents): Double =
        (components.propertyFit * .30 + components.materialSufficiency * .25 +
            components.economicPotential * .15 + components.residueReduction * .10) / .80
}

object CalculationPolicy {
    fun matches(calculation: MathCalculation): Boolean {
        val values = calculation.inputs.map { it.value }
        val expected = when (calculation.formulaId) {
            "usable_mass" -> values.getOrNull(0)?.times((values.getOrNull(1) ?: return false) / 100.0)
            "product_yield" -> values.getOrNull(0)?.div(values.getOrNull(1)?.takeIf { it > 0.0 } ?: return false)?.let { kotlin.math.floor(it) }
            "cost_efficiency" -> values.getOrNull(0)?.div(values.getOrNull(1)?.takeIf { it > 0.0 } ?: return false)?.times(100.0)
            "margin_readiness" -> if (values.size >= 2) (values[0] + values[1]) / 2.0 else null
            else -> null
        } ?: return false
        return kotlin.math.abs(expected - calculation.result) <= .01
    }
}

object SafetyPolicy {
    fun assess(category: MaterialCategory, observations: Map<FieldId, Observation>): SafetyOutcome {
        fun choice(id: String) = (observations[FieldId(id)]?.value as? InspectionValue.Choice)?.value
        if (choice("contamination") == "suspected_hazardous") return SafetyOutcome.BLOCK
        if (choice("contamination") == "unknown" || choice("condition") == "unknown") return SafetyOutcome.CAUTION
        return when (category) {
            MaterialCategory.ELECTRONICS -> when {
                choice("battery_damage") in setOf("yes", "unknown") || choice("burn_marks") == "yes" || choice("powered") == "yes" -> SafetyOutcome.BLOCK
                choice("battery") in setOf("yes","unknown") || choice("powered") == "unknown" -> SafetyOutcome.BLOCK
                else -> SafetyOutcome.ALLOW
            }
            MaterialCategory.CABLE -> when { choice("powered") == "yes" -> SafetyOutcome.BLOCK; choice("powered") == "unknown" || choice("exposed_conductor") == "yes" || choice("insulation") in setOf("damaged","unknown") -> SafetyOutcome.CAUTION; else -> SafetyOutcome.ALLOW }
            MaterialCategory.METAL -> if (choice("sharp_edges") == "yes" || choice("corrosion") == "heavy" || choice("coating_oil") == "unknown") SafetyOutcome.CAUTION else SafetyOutcome.ALLOW
            MaterialCategory.PLASTIC -> when { choice("chemical_contact") == "yes" || choice("melted_evidence") == "yes" -> SafetyOutcome.BLOCK; choice("chemical_contact") == "unknown" || choice("resin_code") == "unknown" -> SafetyOutcome.CAUTION; else -> SafetyOutcome.ALLOW }
            MaterialCategory.WOOD -> when { choice("rot_mold") == "heavy" -> SafetyOutcome.BLOCK; choice("treatment") in setOf("yes","unknown") || choice("rot_mold") == "light" || choice("embedded_metal") == "yes" -> SafetyOutcome.CAUTION; else -> SafetyOutcome.ALLOW }
            MaterialCategory.TEXTILE -> when { choice("wet_mold") == "heavy" || choice("oil_chemical") == "yes" -> SafetyOutcome.BLOCK; choice("oil_chemical") == "unknown" || choice("wet_mold") == "light" -> SafetyOutcome.CAUTION; else -> SafetyOutcome.ALLOW }
        }
    }
}
