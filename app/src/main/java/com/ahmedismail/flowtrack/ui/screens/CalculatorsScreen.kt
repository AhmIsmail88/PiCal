package com.ahmedismail.flowtrack.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.util.toInputDoubleOrNull
import com.ahmedismail.flowtrack.util.toInputIntOrNull
import com.ahmedismail.flowtrack.util.MiterScopeException
import com.ahmedismail.flowtrack.util.MiterEquation
import com.ahmedismail.flowtrack.util.B311MiterPath
import com.ahmedismail.flowtrack.util.B311PipeScopeException
import com.ahmedismail.flowtrack.util.B311MiterSegmentCalculator
import com.ahmedismail.flowtrack.util.B311MiterSegmentInput
import com.ahmedismail.flowtrack.util.MiterSpacing
import com.ahmedismail.flowtrack.util.PipeThicknessResult
import com.ahmedismail.flowtrack.util.EngineeringEquations
import com.ahmedismail.flowtrack.pdf.MiterReportGenerator
import com.ahmedismail.flowtrack.data.reference.VerifiedAllowableStress
import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.reference.B311MaterialGroup
import com.ahmedismail.flowtrack.data.reference.B311YTable
import com.ahmedismail.flowtrack.data.reference.MaterialClass
import com.ahmedismail.flowtrack.data.reference.PipeMaterial
import com.ahmedismail.flowtrack.data.reference.PipeMaterialData
import com.ahmedismail.flowtrack.data.reference.PipeSchedule
import com.ahmedismail.flowtrack.data.reference.PipeScheduleData
import com.ahmedismail.flowtrack.pdf.BendReportGenerator
import com.ahmedismail.flowtrack.pdf.PipeThicknessReportGenerator
import com.ahmedismail.flowtrack.pdf.PipeWeightReportGenerator
import com.ahmedismail.flowtrack.pdf.ReportNotifier
import com.ahmedismail.flowtrack.pdf.VesselReportGenerator
import com.ahmedismail.flowtrack.ui.components.NumericReadout
import com.ahmedismail.flowtrack.ui.theme.*
import com.ahmedismail.flowtrack.util.B311PipeInput
import com.ahmedismail.flowtrack.util.B311PipeThicknessCalculator
import com.ahmedismail.flowtrack.util.B311MiterCalculator
import com.ahmedismail.flowtrack.util.B311MiterConditions
import com.ahmedismail.flowtrack.util.B313MiterCalculator
import com.ahmedismail.flowtrack.util.B313MiterInput
import com.ahmedismail.flowtrack.util.MiterGeometry
import com.ahmedismail.flowtrack.util.BendInput
import com.ahmedismail.flowtrack.util.BendThicknessCalculator
import com.ahmedismail.flowtrack.util.DisplayFormat
import com.ahmedismail.flowtrack.util.PipeThicknessCalculator
import com.ahmedismail.flowtrack.util.PipeThicknessInput
import com.ahmedismail.flowtrack.util.PipeWeightCalculator
import com.ahmedismail.flowtrack.util.PipeWeightInput
import com.ahmedismail.flowtrack.util.PipingCode
import com.ahmedismail.flowtrack.util.ThicknessScopeException
import com.ahmedismail.flowtrack.util.VesselInput
import com.ahmedismail.flowtrack.util.VesselPart
import com.ahmedismail.flowtrack.util.VesselScopeException
import com.ahmedismail.flowtrack.util.VesselThicknessCalculator
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private const val OTHER_MATERIAL = "__other__"

private enum class CalculatorTab { THICKNESS, WEIGHT, BEND, VESSEL, MITER }

/**
 * A standalone hub of ASME B31.3 piping calculators — deliberately NOT
 * tied to the Projects feature. Reached from the calculator icon in the
 * bottom navigation. It works with zero projects created. Project/Engineer
 * name are optional text fields, independent of the selected project.
 */
@Composable
fun CalculatorsScreen(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(CalculatorTab.THICKNESS) }

    val calculatorState = rememberSaveableStateHolder()
    Column(modifier = modifier.fillMaxSize().padding(14.dp)) {
        CalculatorTabRow(tab, onSelect = { tab = it })
        Spacer(Modifier.height(10.dp))
        calculatorState.SaveableStateProvider(tab.name) { when (tab) {
            CalculatorTab.THICKNESS -> PipeThicknessTab()
            CalculatorTab.WEIGHT -> PipeWeightTab()
            CalculatorTab.BEND -> PipeBendTab()
            CalculatorTab.VESSEL -> VesselTab()
            CalculatorTab.MITER -> MiterTab()
        } }
    }
}

@Composable
private fun CalculatorTabRow(selected: CalculatorTab, onSelect: (CalculatorTab) -> Unit) {
    // Four calculators no longer fit an evenly split row, so the tab strip scrolls
    // horizontally (Compose mirrors the scroll direction in RTL) instead of
    // squeezing the labels into unreadable chips.
    val scrollState = rememberScrollState()
    // Scroll the selected chip into view once the strip has been measured.
    LaunchedEffect(selected, scrollState.maxValue) {
        val last = CalculatorTab.values().lastIndex
        if (last > 0 && scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue * selected.ordinal / last)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 12.dp)
            .horizontalScroll(scrollState).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabChip(stringResource(R.string.tab_pipe_thickness), selected == CalculatorTab.THICKNESS) { onSelect(CalculatorTab.THICKNESS) }
        TabChip(stringResource(R.string.tab_pipe_weight), selected == CalculatorTab.WEIGHT) { onSelect(CalculatorTab.WEIGHT) }
        TabChip(stringResource(R.string.tab_pipe_bend), selected == CalculatorTab.BEND) { onSelect(CalculatorTab.BEND) }
        TabChip(stringResource(R.string.tab_pressure_vessel), selected == CalculatorTab.VESSEL) { onSelect(CalculatorTab.VESSEL) }
        TabChip(stringResource(R.string.tab_miter_bend), selected == CalculatorTab.MITER) { onSelect(CalculatorTab.MITER) }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Steel else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) CardWhite else Ink2, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

// ---------------------------------------------------------------------
// Pipe Thickness tab (ASME B31.3 §304.1.2)
// ---------------------------------------------------------------------

private const val CUSTOM_NPS = "__custom_nps__"
private const val MM_PER_INCH = 25.4

@Composable
private fun PipeThicknessTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var projectNameText by rememberSaveable { mutableStateOf("") }
    var engineerNameText by rememberSaveable { mutableStateOf("") }
    var exportFailed by remember { mutableStateOf(false) }

    // Which code governs: B31.3 Process or B31.1 Power. Equations and data never mix.
    var code by rememberSaveable { mutableStateOf(PipingCode.B31_3_PROCESS) }
    var b311Group by rememberSaveable { mutableStateOf(B311MaterialGroup.FERRITIC) }

    var selectedMaterialName by rememberSaveable { mutableStateOf(PipeMaterialData.materials.first().displayName) }
    var customMaterialName by rememberSaveable { mutableStateOf("") }
    var customMaterialClass by rememberSaveable { mutableStateOf(MaterialClass.FERRITIC) }
    var temperatureText by rememberSaveable { mutableStateOf("20") }
    // A material or temperature change invalidates coefficients immediately in composition.
    val coefficientKey = listOf(code.name, selectedMaterialName, customMaterialName, customMaterialClass.name, b311Group.name, temperatureText)
    val stressDefault = VerifiedAllowableStress.lookup(code, selectedMaterialName, temperatureText.toInputDoubleOrNull())
    var allowableStressText by rememberSaveable(coefficientKey) { mutableStateOf(stressDefault?.let { DisplayFormat.number(it.stressMPa, 6) }.orEmpty()) }
    var designReference by rememberSaveable(coefficientKey) { mutableStateOf("") }

    val isOtherMaterial = selectedMaterialName == OTHER_MATERIAL
    val materialClass = if (isOtherMaterial) customMaterialClass else PipeMaterialData.materials.find { it.displayName == selectedMaterialName }?.materialClass ?: MaterialClass.FERRITIC

    // B36.10-2022 sizes; custom OD is independent of nominal pipe size.
    var selectedNpsLabel by rememberSaveable { mutableStateOf(PipeScheduleData.sizes.first().label) }
    val isCustomNps = selectedNpsLabel == CUSTOM_NPS
    var odUnit by rememberSaveable { mutableStateOf(DiameterUnit.MM) }
    var manualOdText by rememberSaveable { mutableStateOf("") }

    var pressureText by rememberSaveable { mutableStateOf("") }
    var corrosionAllowanceText by rememberSaveable { mutableStateOf("0") }
    var erosionAllowanceText by rememberSaveable { mutableStateOf("0") }
    var mechanicalAllowanceText by rememberSaveable { mutableStateOf("0") }
    // Manufacturing tolerances depend on the product specification, not on B31.3 alone.
    var millToleranceText by rememberSaveable(selectedMaterialName, customMaterialName) { mutableStateOf("") }
    var qualityFactorText by rememberSaveable(coefficientKey) { mutableStateOf(stressDefault?.let { DisplayFormat.number(it.qualityFactorE, 2) }.orEmpty()) }
    var weldFactorText by rememberSaveable(coefficientKey) { mutableStateOf("") }
    var yCoefficientText by rememberSaveable(coefficientKey) { mutableStateOf("") }
    var yManuallyEdited by rememberSaveable(coefficientKey) { mutableStateOf(false) }
    val automaticY = temperatureText.toInputDoubleOrNull()?.let { temperature ->
        runCatching {
            if (code == PipingCode.B31_1_POWER) B311YTable.coefficient(b311Group, temperature)
            else PipeThicknessCalculator.defaultYCoefficient(materialClass, temperature)
        }.getOrNull()
    }

    var selectedSchedule by rememberSaveable { mutableStateOf<PipeSchedule?>(null) }
    // Fallback manual wall entry — shown whenever there's no schedule table
    // to pick from (Custom NPS, or a standard size where none is defined).
    var manualWallUnit by rememberSaveable { mutableStateOf(DiameterUnit.MM) }
    var manualWallText by rememberSaveable { mutableStateOf("") }

    var measuredWallText by rememberSaveable(selectedMaterialName, customMaterialName, selectedNpsLabel, manualOdText, odUnit, selectedSchedule, manualWallText, manualWallUnit) { mutableStateOf("") }
    var measurementReference by rememberSaveable(selectedMaterialName, customMaterialName, selectedNpsLabel, manualOdText, odUnit, selectedSchedule, manualWallText, manualWallUnit) { mutableStateOf("") }

    fun applyMaterial(material: PipeMaterial) {
        selectedMaterialName = material.displayName

    }

    val otherMaterialLabel = stringResource(R.string.material_other)
    val ferriticLabel = stringResource(R.string.material_class_ferritic)
    val austeniticLabel = stringResource(R.string.material_class_austenitic)
    val b311GroupLabels = mapOf(
        B311MaterialGroup.FERRITIC to stringResource(R.string.b311_group_ferritic),
        B311MaterialGroup.AUSTENITIC to stringResource(R.string.b311_group_austenitic),
        B311MaterialGroup.NICKEL_N06690 to stringResource(R.string.b311_group_n06690),
        B311MaterialGroup.NICKEL_ALLOY to stringResource(R.string.b311_group_nickel),
        B311MaterialGroup.CAST_IRON to stringResource(R.string.b311_group_cast_iron),
        B311MaterialGroup.OTHER to stringResource(R.string.b311_group_other)
    )
    val codeLabels = mapOf(
        PipingCode.B31_3_PROCESS to stringResource(R.string.code_b313_process),
        PipingCode.B31_1_POWER to stringResource(R.string.code_b311_power)
    )
    val customNpsLabel = stringResource(R.string.nps_custom)
    val manualEntryLabel = stringResource(R.string.manual_wall_entry_label)
    val inchLabel = stringResource(R.string.unit_inch)
    val mmLabel = stringResource(R.string.unit_mm)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompositionLocalProvider(LocalCalculatorBasis provides (if (code == PipingCode.B31_1_POWER) CalculatorBasis.B311 else CalculatorBasis.B313)) {
        SectionLabel(stringResource(R.string.section_inputs))
        CalculatorDropdownField(
            label = stringResource(R.string.field_design_code),
            options = listOf(PipingCode.B31_3_PROCESS, PipingCode.B31_1_POWER),
            selected = code,
            display = { codeLabels.getValue(it) },
            onSelect = { code = it }
        )
        EquationCard(EngineeringEquations.pipe(code))
        Text(stringResource(if (code == PipingCode.B31_1_POWER) R.string.b311_verify_note else R.string.thickness_scope_hint), style = MaterialTheme.typography.bodySmall, color = if (code == PipingCode.B31_1_POWER) WarnBandText else Ink2)
        Text(stringResource(R.string.schedule_basis_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)


        CalculatorDropdownField(
            label = stringResource(R.string.field_material),
            options = PipeMaterialData.materials.map { it.displayName } + OTHER_MATERIAL,
            selected = selectedMaterialName,
            display = { if (it == OTHER_MATERIAL) otherMaterialLabel else it },
            onSelect = { name ->
                if (name == OTHER_MATERIAL) {
                    selectedMaterialName = OTHER_MATERIAL
                } else {
                    PipeMaterialData.materials.find { it.displayName == name }?.let { applyMaterial(it) }
                }
            }
        )

        if (isOtherMaterial) {
            NumberFieldText(stringResource(R.string.field_material_name), customMaterialName) { customMaterialName = it }
            CalculatorDropdownField(
                label = stringResource(R.string.field_material_class),
                options = listOf(MaterialClass.FERRITIC, MaterialClass.AUSTENITIC),
                selected = customMaterialClass,
                display = { if (it == MaterialClass.FERRITIC) ferriticLabel else austeniticLabel },
                onSelect = { customMaterialClass = it }
            )
        }

        if (code == PipingCode.B31_1_POWER) {
            CalculatorDropdownField(
                label = stringResource(R.string.field_b311_material_group),
                options = B311MaterialGroup.values().toList(),
                selected = b311Group,
                display = { b311GroupLabels.getValue(it) },
                onSelect = { b311Group = it }
            )
        }

        CalculatorNumberField(stringResource(R.string.field_design_temperature), temperatureText) { temperatureText = it }
        CalculatorNumberField(stringResource(R.string.field_allowable_stress), allowableStressText) { allowableStressText = it }
        StressSourceHint(code, selectedMaterialName, temperatureText, allowableStressText) { value -> allowableStressText = value }

        CalculatorDropdownField(
            label = stringResource(R.string.field_nominal_size),
            options = PipeScheduleData.sizes.map { it.label } + CUSTOM_NPS,
            selected = selectedNpsLabel,
            display = { if (it == CUSTOM_NPS) customNpsLabel else it },
            onSelect = { label -> selectedNpsLabel = label; selectedSchedule = null }
        )
        if (isCustomNps) {
            CalculatorDropdownField(
                label = stringResource(R.string.field_od_unit),
                options = listOf(DiameterUnit.INCH, DiameterUnit.MM),
                selected = odUnit,
                display = { if (it == DiameterUnit.INCH) inchLabel else mmLabel },
                onSelect = { odUnit = it }
            )
            CalculatorNumberField(stringResource(R.string.field_manual_od), manualOdText) { manualOdText = it }
        }
        CalculatorNumberField(stringResource(R.string.field_design_pressure), pressureText) { pressureText = it }
        CalculatorNumberField(stringResource(R.string.field_corrosion_allowance), corrosionAllowanceText) { corrosionAllowanceText = it }
        CalculatorNumberField(stringResource(R.string.field_erosion_allowance), erosionAllowanceText) { erosionAllowanceText = it }
        CalculatorNumberField(stringResource(R.string.field_mechanical_allowance), mechanicalAllowanceText) { mechanicalAllowanceText = it }

        SectionLabel(stringResource(if (code == PipingCode.B31_3_PROCESS) R.string.b313_coefficients_title else R.string.b311_coefficients_title))
        CalculatorNumberField(stringResource(R.string.field_quality_factor), qualityFactorText) { qualityFactorText = it }
        CalculatorNumberField(stringResource(R.string.field_weld_factor), weldFactorText) { weldFactorText = it }
        CalculatorNumberField(stringResource(R.string.field_y_coefficient),
            if (yManuallyEdited) yCoefficientText else automaticY?.let { DisplayFormat.number(it, 4) }.orEmpty()
        ) { yCoefficientText = it; yManuallyEdited = true }
        Text(stringResource(if (yManuallyEdited) R.string.y_manual_hint else if (code == PipingCode.B31_1_POWER) R.string.b311_y_auto_hint else R.string.y_auto_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        if (code == PipingCode.B31_3_PROCESS && temperatureText.toInputDoubleOrNull()?.let { it > PipeThicknessCalculator.Y_TABLE_MAX_TEMPERATURE_C } == true) {
            Text(stringResource(R.string.y_above_table_warning), style = MaterialTheme.typography.bodySmall, color = WarnBandText)
        }
        if (yManuallyEdited) TextButton(onClick = { yManuallyEdited = false }) { Text(stringResource(R.string.y_use_table)) }
        NumberFieldText(stringResource(R.string.design_reference_label), designReference) { designReference = it }

        val od: Double? = if (isCustomNps) {
            manualOdText.toInputDoubleOrNull()?.let { if (odUnit == DiameterUnit.INCH) it * MM_PER_INCH else it }
        } else {
            PipeScheduleData.outsideDiameterMm(selectedNpsLabel)
        }
        val pressure = pressureText.toInputDoubleOrNull()
        val allowableStress = allowableStressText.toInputDoubleOrNull()
        val e = qualityFactorText.toInputDoubleOrNull()
        val w = weldFactorText.toInputDoubleOrNull()
        val y = if (yManuallyEdited) yCoefficientText.toInputDoubleOrNull() else automaticY
        val corrosion = corrosionAllowanceText.toInputDoubleOrNull()
        val erosion = erosionAllowanceText.toInputDoubleOrNull()
        val mechanical = mechanicalAllowanceText.toInputDoubleOrNull()
        val tolerance = millToleranceText.toInputDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it < 100 }

        val temperature = temperatureText.toInputDoubleOrNull()?.takeIf { it > -273.15 }
        val calculation = if (code == PipingCode.B31_3_PROCESS && od != null && pressure != null && allowableStress != null && e != null && w != null && y != null && corrosion != null && erosion != null && mechanical != null && temperature != null) {
            runCatching { PipeThicknessCalculator.calculate(
                PipeThicknessInput(
                    designPressureBar = pressure, outsideDiameterMm = od, allowableStressMPa = allowableStress,
                    qualityFactorE = e, weldStrengthReductionW = w, yCoefficient = y, corrosionAllowanceMm = corrosion,
                    erosionAllowanceMm = erosion, mechanicalAllowanceMm = mechanical
                )
            ) }
        } else null
        val b313Result = calculation?.getOrNull()

        // ASME B31.1 (Power Piping) runs its own equation and its own y table; the two
        // code paths never share a result.
        val b311Calculation = if (code == PipingCode.B31_1_POWER && od != null && pressure != null &&
            allowableStress != null && e != null && w != null && y != null && corrosion != null &&
            erosion != null && mechanical != null && temperature != null &&
            corrosion >= 0.0 && erosion >= 0.0 && mechanical >= 0.0) {
            runCatching {
                B311PipeThicknessCalculator.calculate(
                    B311PipeInput(
                        designPressureBar = pressure, outsideDiameterMm = od, allowableStressMPa = allowableStress,
                        qualityFactorE = e, weldStrengthReductionW = w,
                        additionalThicknessAMm = corrosion + erosion + mechanical,
                        materialGroup = b311Group, designTemperatureC = temperature,
                        yOverride = if (yManuallyEdited) y else null
                    )
                )
            }
        } else null
        val b311Result = b311Calculation?.getOrNull()
        // Common comparison data, after each code has calculated its own result.
        val result = if (code == PipingCode.B31_3_PROCESS) b313Result else b311Result?.let {
            PipeThicknessResult(it.pressureThicknessMm, it.minimumRequiredThicknessMm,
                it.pressureThicknessMm + (corrosion ?: 0.0) + (erosion ?: 0.0))
        }
        val requiredNominal = if (result != null && tolerance != null) runCatching {
            PipeThicknessCalculator.requiredNominalThicknessMm(result, tolerance)
        }.getOrNull() else null
        val b311Additional = if (corrosion != null && erosion != null && mechanical != null) {
            corrosion + erosion + mechanical
        } else null

        if (code == PipingCode.B31_1_POWER) {
            Spacer(Modifier.height(10.dp))
            SectionLabel(stringResource(R.string.section_outputs))
            if (b311Result == null) {
                Text(stringResource(if (b311Calculation?.exceptionOrNull() is B311PipeScopeException) R.string.b311_outside_scope else R.string.b311_invalid), color = WarnBandText, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.fillMaxWidth().glassPanelDark(cornerRadius = 10.dp).padding(14.dp, 12.dp)) {
                ResultRow(stringResource(R.string.b311_thickness_label), b311Result?.let { "${formatThickness(it.pressureThicknessMm)} mm" } ?: "—", light = true)
                ResultRow(stringResource(R.string.b311_min_required_label), b311Result?.let { "${formatThickness(it.minimumRequiredThicknessMm)} mm" } ?: "—", light = true)
                ResultRow(stringResource(R.string.b311_y_used_label), b311Result?.let { formatThickness(it.yCoefficient) } ?: "—", light = true)
                ResultRow(stringResource(R.string.b311_a_label), b311Additional?.let { "${formatThickness(it)} mm" } ?: "—", light = true)
            }
            if (b311Result?.thinWallRuleApplied == true) {
                Text(stringResource(R.string.b311_thin_wall_note), style = MaterialTheme.typography.bodySmall, color = WarnBandText)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.finished_section))
        CalculatorNumberField(stringResource(R.string.measured_wall_label), measuredWallText) { measuredWallText = it }
        Text(stringResource(R.string.measurement_basis_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        NumberFieldText(stringResource(R.string.measurement_reference_label), measurementReference) { measurementReference = it }
        val measuredWall = measuredWallText.toInputDoubleOrNull()
        val finishedCheck = if (result != null && od != null && measuredWall != null) runCatching {
            PipeThicknessCalculator.checkFinishedWall(result, od, measuredWall)
        }.getOrNull() else null
        Column(Modifier.fillMaxWidth().glassPanelDark(cornerRadius = 10.dp).padding(14.dp)) {
            ResultRow(stringResource(R.string.measured_wall_label), finishedCheck?.let { "${formatThickness(it.measuredMinimumMm)} mm" } ?: stringResource(R.string.measurement_not_provided), light = true)
            ResultRow(stringResource(R.string.finished_required_label), result?.let { "${formatThickness(it.minimumFinishedWallMm)} mm" } ?: "—", light = true)
            if (finishedCheck != null) {
                ResultRow(stringResource(R.string.finished_margin_label), "${formatThickness(finishedCheck.marginMm)} mm", light = true)
                Text(stringResource(if (finishedCheck.passes) R.string.finished_pass else R.string.finished_fail), color = if (finishedCheck.passes) PassOnNavy else FailOnNavy)
            }
        }
        if (measuredWallText.isNotBlank() && finishedCheck == null) Text(stringResource(R.string.measured_invalid), color = WarnBandText)

        if (code == PipingCode.B31_3_PROCESS) {
        Spacer(Modifier.height(10.dp))
        SectionLabel(stringResource(R.string.section_outputs))
        if (result == null) Text(stringResource(if (calculation?.exceptionOrNull() is ThicknessScopeException) R.string.thickness_outside_scope else R.string.calculation_invalid), color = WarnBandText, style = MaterialTheme.typography.bodySmall)
        Column(Modifier.fillMaxWidth().glassPanelDark(cornerRadius = 10.dp).padding(14.dp, 12.dp)) {
            Text(stringResource(R.string.section_calculated_results), color = CardWhite, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            ResultRow(stringResource(R.string.calculated_thickness_label), result?.let { "${formatThickness(it.calculatedThicknessMm)} mm" } ?: "—", light = true)
            ResultRow(stringResource(R.string.minimum_required_thickness_label), result?.let { "${formatThickness(it.minimumRequiredThicknessMm)} mm" } ?: "—", light = true)
            ResultRow(stringResource(R.string.required_nominal_thickness), requiredNominal?.let { "${formatThickness(it)} mm" } ?: "—", light = true)
        }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel(stringResource(R.string.section_schedule_check))
        CalculatorNumberField(stringResource(R.string.field_mill_tolerance), millToleranceText) { millToleranceText = it }
        val availableSchedules = if (isCustomNps) emptyList() else PipeScheduleData.availableSchedules(selectedNpsLabel)
        val scheduleWallMm: Double?
        if (availableSchedules.isEmpty()) {
            // No table to pick from (Custom NPS, or a standard size with no
            // schedule data) — fall back to a manual, unit-aware wall entry
            // so the PASS/FAIL check still works for a real, specific pipe.
            Text(stringResource(R.string.schedule_not_available), style = MaterialTheme.typography.bodySmall, color = Ink2, modifier = Modifier.padding(bottom = 8.dp))
            CalculatorDropdownField(
                label = stringResource(R.string.field_wall_unit),
                options = listOf(DiameterUnit.INCH, DiameterUnit.MM),
                selected = manualWallUnit,
                display = { if (it == DiameterUnit.INCH) inchLabel else mmLabel },
                onSelect = { manualWallUnit = it }
            )
            CalculatorNumberField(stringResource(R.string.wall_thickness_label), manualWallText) { manualWallText = it }
            scheduleWallMm = manualWallText.toInputDoubleOrNull()?.let { if (manualWallUnit == DiameterUnit.INCH) it * MM_PER_INCH else it }
        } else {
            CalculatorDropdownField(
                label = stringResource(R.string.field_schedule),
                options = availableSchedules,
                selected = selectedSchedule,
                display = { it.label },
                onSelect = { selectedSchedule = it }
            )
            scheduleWallMm = selectedSchedule?.let { sch -> PipeScheduleData.wallThicknessMm(selectedNpsLabel, sch) }
        }

        val wallCheck = if (result != null && od != null && scheduleWallMm != null && tolerance != null) runCatching {
            PipeThicknessCalculator.checkNominalWall(result, od, scheduleWallMm, tolerance)
        }.getOrNull() else null
        if (wallCheck != null && od != null && scheduleWallMm != null) {
            val id = od - 2 * scheduleWallMm
            val effectiveWall = wallCheck.effectiveWallMm
            val passes = wallCheck.passes
            val accentColor = if (passes) Green else Orange
            Column(Modifier.fillMaxWidth().glassPanel()) {
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(accentColor))
                Column(Modifier.padding(12.dp)) {
                    ResultRow(stringResource(R.string.outside_diameter_label), "${formatNum(od)} mm")
                    ResultRow(stringResource(R.string.wall_thickness_label), "${formatNum(scheduleWallMm)} mm")
                    ResultRow(stringResource(R.string.effective_wall_thickness_label), "${formatThickness(effectiveWall)} mm")
                    ResultRow(stringResource(R.string.inside_diameter_label), "${formatNum(id)} mm")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (passes) stringResource(R.string.schedule_pass) else stringResource(R.string.schedule_fail),
                        color = if (passes) Green else Orange,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.calculation_report_details))
        NumberFieldText(stringResource(R.string.field_project_name_optional), projectNameText) { projectNameText = it }
        NumberFieldText(stringResource(R.string.field_engineer_name_optional), engineerNameText) { engineerNameText = it }
        val validNominalEntry = scheduleWallMm?.let { it.isFinite() && it > 0 && od != null && 2 * it < od } ?: (availableSchedules.isNotEmpty() || manualWallText.isBlank())
        val validToleranceEntry = millToleranceText.isBlank() || tolerance != null
        val canExport = result != null && finishedCheck != null && validNominalEntry && validToleranceEntry
        if (!validNominalEntry || !validToleranceEntry) Text(stringResource(R.string.nominal_input_invalid), color = WarnBandText)
        Button(
            onClick = {
                scope.launch {
                    exportFailed = false
                    val materialLabel = if (isOtherMaterial) customMaterialName.ifBlank { "Custom material" } else selectedMaterialName
                    val npsLabel = if (isCustomNps) "Custom (${formatNum(od ?: 0.0)} mm OD)" else selectedNpsLabel
                    val outputDir = File(context.getExternalFilesDir(null), "reports")
                    val file = runCatching { PipeThicknessReportGenerator.generate(
                        projectName = projectNameText.ifBlank { null },
                        engineerName = engineerNameText.ifBlank { null },
                        materialName = materialLabel,
                        nps = npsLabel,
                        outsideDiameterMm = od ?: 0.0,
                        designPressureBar = pressure ?: 0.0,
                        designTemperatureC = temperatureText.toInputDoubleOrNull() ?: 0.0,
                        allowableStressMPa = allowableStress ?: 0.0,
                        qualityFactorE = e ?: 0.0,
                        weldFactorW = w ?: 0.0,
                        yCoefficient = y ?: 0.0,
                        corrosionAllowanceMm = corrosion ?: 0.0,
                        erosionAllowanceMm = erosion ?: 0.0,
                        mechanicalAllowanceMm = mechanical ?: 0.0,
                        designReference = listOfNotNull(designReference.trim().ifBlank { null },
                            stressDefault?.takeIf { allowableStress?.let { s -> kotlin.math.abs(s - it.stressMPa) <= 1e-6 } == true }?.reference)
                            .joinToString(" | ").ifBlank { "Not provided" },
                        millTolerancePercent = tolerance,
                        measuredMinimumWallMm = measuredWall,
                        measurementReference = measurementReference.trim().ifBlank { "Not provided" },
                        selectedSchedule = selectedSchedule?.label ?: scheduleWallMm?.let { manualEntryLabel },
                        scheduleWallThicknessMm = scheduleWallMm,
                        pipingCode = code,
                        b311MaterialGroup = b311Group,
                        b311ManualY = yManuallyEdited,
                        outputDir = outputDir
                    ) }.getOrElse { exportFailed = true; return@launch }
                    ReportNotifier.notify(context, file)
                    context.startActivity(ReportNotifier.shareIntent(context, file))
                }
            },
            enabled = canExport,
            colors = ButtonDefaults.buttonColors(containerColor = Steel, contentColor = CardWhite),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(stringResource(R.string.export_pipe_report), style = MaterialTheme.typography.titleSmall)
        }
        if (!canExport) Text(stringResource(R.string.finished_export_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        if (exportFailed) Text(stringResource(R.string.report_export_failed), color = WarnBandText)
        Spacer(Modifier.height(8.dp))
    
        }
    }
}

// ---------------------------------------------------------------------
// Pipe Weight tab
// ---------------------------------------------------------------------

@Composable
private fun PipeWeightTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var projectNameText by rememberSaveable { mutableStateOf("") }
    var engineerNameText by rememberSaveable { mutableStateOf("") }

    var selectedMaterialName by rememberSaveable { mutableStateOf(PipeMaterialData.materials.first().displayName) }
    var customMaterialName by rememberSaveable { mutableStateOf("") }
    var densityText by rememberSaveable { mutableStateOf(formatNum(PipeMaterialData.materials.first().densityKgM3)) }

    val isOtherMaterial = selectedMaterialName == OTHER_MATERIAL
    val otherMaterialLabel = stringResource(R.string.material_other)
    val customNpsLabel = stringResource(R.string.nps_custom)
    val inchLabel = stringResource(R.string.unit_inch)
    val mmLabel = stringResource(R.string.unit_mm)

    // Same Custom/manual-entry NPS pattern as the Thickness tab — the
    // schedule table only covers NPS ½"–10", so anything outside that (or
    // any non-standard pipe) needs a direct, unit-aware OD + wall entry.
    var selectedNpsLabel by rememberSaveable { mutableStateOf(PipeScheduleData.sizes.first().label) }
    val isCustomNps = selectedNpsLabel == CUSTOM_NPS
    var odUnit by rememberSaveable { mutableStateOf(DiameterUnit.MM) }
    var manualOdText by rememberSaveable { mutableStateOf("") }
    var wallUnit by rememberSaveable { mutableStateOf(DiameterUnit.MM) }
    var manualWallText by rememberSaveable { mutableStateOf("") }

    var selectedSchedule by rememberSaveable { mutableStateOf<PipeSchedule?>(PipeScheduleData.availableSchedules(PipeScheduleData.sizes.first().label).firstOrNull()) }
    var lengthText by rememberSaveable { mutableStateOf("1") }
    var includeWaterFill by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompositionLocalProvider(LocalCalculatorBasis provides (CalculatorBasis.WEIGHT)) {
        EquationCard(EngineeringEquations.WEIGHT)
        SectionLabel(stringResource(R.string.section_inputs))
        Text(stringResource(R.string.schedule_basis_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)


        CalculatorDropdownField(
            label = stringResource(R.string.field_material),
            options = PipeMaterialData.materials.map { it.displayName } + OTHER_MATERIAL,
            selected = selectedMaterialName,
            display = { if (it == OTHER_MATERIAL) otherMaterialLabel else it },
            onSelect = { name ->
                selectedMaterialName = name
                if (name != OTHER_MATERIAL) {
                    PipeMaterialData.materials.find { it.displayName == name }?.let { densityText = formatNum(it.densityKgM3) }
                }
            }
        )
        if (isOtherMaterial) {
            NumberFieldText(stringResource(R.string.field_material_name), customMaterialName) { customMaterialName = it }
        }
        CalculatorNumberField(stringResource(R.string.field_density), densityText) { densityText = it }

        CalculatorDropdownField(
            label = stringResource(R.string.field_nominal_size),
            options = PipeScheduleData.sizes.map { it.label } + CUSTOM_NPS,
            selected = selectedNpsLabel,
            display = { if (it == CUSTOM_NPS) customNpsLabel else it },
            onSelect = { label ->
                selectedNpsLabel = label
                selectedSchedule = if (label == CUSTOM_NPS) null else PipeScheduleData.availableSchedules(label).firstOrNull()
            }
        )

        val od: Double?
        val wall: Double?
        if (isCustomNps) {
            CalculatorDropdownField(
                label = stringResource(R.string.field_od_unit),
                options = listOf(DiameterUnit.INCH, DiameterUnit.MM),
                selected = odUnit,
                display = { if (it == DiameterUnit.INCH) inchLabel else mmLabel },
                onSelect = { odUnit = it }
            )
            CalculatorNumberField(stringResource(R.string.field_manual_od), manualOdText) { manualOdText = it }
            CalculatorDropdownField(
                label = stringResource(R.string.field_wall_unit),
                options = listOf(DiameterUnit.INCH, DiameterUnit.MM),
                selected = wallUnit,
                display = { if (it == DiameterUnit.INCH) inchLabel else mmLabel },
                onSelect = { wallUnit = it }
            )
            CalculatorNumberField(stringResource(R.string.wall_thickness_label), manualWallText) { manualWallText = it }
            od = manualOdText.toInputDoubleOrNull()?.let { if (odUnit == DiameterUnit.INCH) it * MM_PER_INCH else it }
            wall = manualWallText.toInputDoubleOrNull()?.let { if (wallUnit == DiameterUnit.INCH) it * MM_PER_INCH else it }
        } else {
            val availableSchedules = PipeScheduleData.availableSchedules(selectedNpsLabel)
            if (availableSchedules.isEmpty()) {
                Text(stringResource(R.string.schedule_not_available), style = MaterialTheme.typography.bodySmall, color = Ink2)
            } else {
                CalculatorDropdownField(
                    label = stringResource(R.string.field_schedule),
                    options = availableSchedules,
                    selected = selectedSchedule,
                    display = { it.label },
                    onSelect = { selectedSchedule = it }
                )
            }
            od = PipeScheduleData.outsideDiameterMm(selectedNpsLabel)
            wall = selectedSchedule?.let { sch -> PipeScheduleData.wallThicknessMm(selectedNpsLabel, sch) }
        }

        CalculatorNumberField(stringResource(R.string.field_pipe_length), lengthText) { lengthText = it }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) { CalculatorHelpLabel(stringResource(R.string.include_water_fill)) }
            Switch(checked = includeWaterFill, onCheckedChange = { includeWaterFill = it })
        }

        val density = densityText.toInputDoubleOrNull()
        val length = lengthText.toInputDoubleOrNull()

        val result = if (od != null && wall != null && density != null && length != null) {
            runCatching { PipeWeightCalculator.calculate(
                PipeWeightInput(outsideDiameterMm = od, wallThicknessMm = wall, densityKgM3 = density, lengthM = length, includeWaterFill = includeWaterFill)
            ) }.getOrNull()
        } else null

        Spacer(Modifier.height(10.dp))
        SectionLabel(stringResource(R.string.section_outputs))
        Column(Modifier.fillMaxWidth().glassPanelDark(cornerRadius = 10.dp).padding(14.dp, 12.dp)) {
            ResultRow(stringResource(R.string.unit_weight_label), result?.let { "${formatNum(it.unitWeightKgM)} kg/m" } ?: "—", light = true)
            ResultRow(stringResource(R.string.total_pipe_weight_label), result?.let { "${formatNum(it.totalPipeWeightKg)} kg" } ?: "—", light = true)
            if (includeWaterFill) {
                ResultRow(stringResource(R.string.water_fill_weight_label), result?.waterFillWeightKg?.let { "${formatNum(it)} kg" } ?: "—", light = true)
                ResultRow(stringResource(R.string.total_weight_with_water_label), result?.let { "${formatNum(it.totalWithWaterKg)} kg" } ?: "—", light = true)
            }
        }

        if (result == null) Text(stringResource(R.string.weight_invalid), color = WarnBandText, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.calculation_report_details))
        NumberFieldText(stringResource(R.string.field_project_name_optional), projectNameText) { projectNameText = it }
        NumberFieldText(stringResource(R.string.field_engineer_name_optional), engineerNameText) { engineerNameText = it }
        Button(
            onClick = {
                scope.launch {
                    val r = result ?: return@launch
                    val materialLabel = if (isOtherMaterial) customMaterialName.ifBlank { "Custom material" } else selectedMaterialName
                    val npsLabel = if (isCustomNps) null else selectedNpsLabel
                    val outputDir = File(context.getExternalFilesDir(null), "reports")
                    val file = PipeWeightReportGenerator.generate(
                        projectName = projectNameText.ifBlank { null },
                        engineerName = engineerNameText.ifBlank { null },
                        materialName = materialLabel,
                        nps = npsLabel,
                        outsideDiameterMm = od ?: 0.0,
                        wallThicknessMm = wall ?: 0.0,
                        densityKgM3 = density ?: 0.0,
                        lengthM = length ?: 0.0,
                        result = r,
                        outputDir = outputDir
                    )
                    ReportNotifier.notify(context, file)
                    context.startActivity(ReportNotifier.shareIntent(context, file))
                }
            },
            enabled = result != null,
            colors = ButtonDefaults.buttonColors(containerColor = Steel, contentColor = CardWhite),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(stringResource(R.string.export_weight_report), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
    
        }
    }
}

// ---------------------------------------------------------------------
// Pipe bend tab (ASME B31.3 §304.2.1)
// ---------------------------------------------------------------------

@Composable
private fun PipeBendTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pressureText by rememberSaveable { mutableStateOf("") }
    var odText by rememberSaveable { mutableStateOf("") }
    var radiusText by rememberSaveable { mutableStateOf("") }
    var materialName by rememberSaveable { mutableStateOf(PipeMaterialData.materials.first().displayName) }
    var customMaterialName by rememberSaveable { mutableStateOf("") }
    var temperatureText by rememberSaveable { mutableStateOf("20") }
    var stressText by rememberSaveable(materialName, customMaterialName, temperatureText) { mutableStateOf("") }
    var eText by rememberSaveable(materialName, customMaterialName, temperatureText) { mutableStateOf("") }
    var wText by rememberSaveable(materialName, customMaterialName, temperatureText) { mutableStateOf("") }
    var yText by rememberSaveable(materialName, customMaterialName, temperatureText) { mutableStateOf(
        PipeMaterialData.materials.find { it.displayName == materialName }?.let { material ->
            temperatureText.toInputDoubleOrNull()?.let { temp ->
                runCatching { DisplayFormat.number(PipeThicknessCalculator.defaultYCoefficient(material.materialClass, temp), 6) }.getOrNull()
            }
        }.orEmpty()
    ) }
    var corrosionText by rememberSaveable { mutableStateOf("0") }
    var erosionText by rememberSaveable { mutableStateOf("0") }
    var mechanicalText by rememberSaveable { mutableStateOf("0") }
    var measuredText by rememberSaveable { mutableStateOf("") }
    var projectNameText by rememberSaveable { mutableStateOf("") }
    var engineerNameText by rememberSaveable { mutableStateOf("") }
    var referenceText by rememberSaveable { mutableStateOf("") }
    var measurementReferenceText by rememberSaveable { mutableStateOf("") }
    var exportFailed by remember { mutableStateOf(false) }

    val pressure = pressureText.toInputDoubleOrNull()
    val od = odText.toInputDoubleOrNull()
    val radius = radiusText.toInputDoubleOrNull()
    val temperature = temperatureText.toInputDoubleOrNull()?.takeIf { it > -273.15 }
    val stress = stressText.toInputDoubleOrNull()
    val e = eText.toInputDoubleOrNull()
    val w = wText.toInputDoubleOrNull()
    val y = yText.toInputDoubleOrNull()
    val corrosion = corrosionText.toInputDoubleOrNull()
    val erosion = erosionText.toInputDoubleOrNull()
    val mechanical = mechanicalText.toInputDoubleOrNull()

    val calculation = if (listOf(temperature, pressure, od, radius, stress, e, w, y, corrosion, erosion, mechanical).all { it != null }) {
        runCatching {
            BendThicknessCalculator.calculate(
                BendInput(
                    designPressureBar = pressure!!, outsideDiameterMm = od!!, bendRadiusMm = radius!!,
                    allowableStressMPa = stress!!, qualityFactorE = e!!, weldStrengthReductionW = w!!,
                    yCoefficient = y!!, corrosionAllowanceMm = corrosion!!, erosionAllowanceMm = erosion!!,
                    mechanicalAllowanceMm = mechanical!!
                )
            )
        }
    } else null
    val result = calculation?.getOrNull()
    val measured = measuredText.toInputDoubleOrNull()
    val measuredCheck = if (result != null && od != null && measured != null) {
        runCatching { BendThicknessCalculator.checkIntradosWall(result, od, measured) }.getOrNull()
    } else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompositionLocalProvider(LocalCalculatorBasis provides (CalculatorBasis.B313)) {
        EquationCard(EngineeringEquations.BEND)
        SectionLabel(stringResource(R.string.section_inputs))
        Text(stringResource(R.string.bend_scope_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)

        CalculatorNumberField(stringResource(R.string.field_design_pressure), pressureText) { pressureText = it }
        CalculatorNumberField(stringResource(R.string.field_bend_od), odText) { odText = it }
        CalculatorNumberField(stringResource(R.string.field_bend_radius), radiusText) { radiusText = it }
        MaterialTemperatureFields(materialName, { materialName = it }, customMaterialName, { customMaterialName = it }, temperatureText, { temperatureText = it })
        CalculatorNumberField(stringResource(R.string.field_allowable_stress), stressText) { stressText = it }
        CalculatorNumberField(stringResource(R.string.field_quality_factor), eText) { eText = it }
        CalculatorNumberField(stringResource(R.string.field_weld_factor), wText) { wText = it }
        CalculatorNumberField(stringResource(R.string.field_y_coefficient), yText) { yText = it }
        CalculatorNumberField(stringResource(R.string.field_corrosion_allowance), corrosionText) { corrosionText = it }
        CalculatorNumberField(stringResource(R.string.field_erosion_allowance), erosionText) { erosionText = it }
        CalculatorNumberField(stringResource(R.string.field_mechanical_allowance), mechanicalText) { mechanicalText = it }

        Spacer(Modifier.height(10.dp))
        SectionLabel(stringResource(R.string.section_outputs))
        if (result == null) {
            Text(
                stringResource(if (calculation?.exceptionOrNull() is ThicknessScopeException) R.string.thickness_outside_scope else R.string.bend_invalid),
                color = WarnBandText, style = MaterialTheme.typography.bodySmall
            )
        }
        Column(Modifier.fillMaxWidth().glassPanelDark(cornerRadius = 10.dp).padding(14.dp, 12.dp)) {
            ResultRow(stringResource(R.string.bend_intrados_thickness), result?.let { "${formatThickness(it.intradosThicknessMm)} mm" } ?: "—", light = true)
            ResultRow(stringResource(R.string.bend_intrados_required), result?.let { "${formatThickness(it.intradosMinimumRequiredThicknessMm)} mm" } ?: "—", light = true)
            ResultRow(stringResource(R.string.bend_straight_thickness), result?.let { "${formatThickness(it.straightThicknessMm)} mm" } ?: "—", light = true)
            ResultRow(stringResource(R.string.bend_straight_required), result?.let { "${formatThickness(it.straightMinimumRequiredThicknessMm)} mm" } ?: "—", light = true)
            ResultRow(stringResource(R.string.bend_extrados_thickness), result?.let { "${formatThickness(it.extradosThicknessMm)} mm" } ?: "—", light = true)
            ResultRow(stringResource(R.string.bend_intrados_factor), result?.let { formatThickness(it.intradosFactorI) } ?: "—", light = true)
            ResultRow(stringResource(R.string.bend_extrados_factor), result?.let { formatThickness(it.extradosFactorI) } ?: "—", light = true)
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel(stringResource(R.string.finished_section))
        CalculatorNumberField(stringResource(R.string.measured_intrados_wall_label), measuredText) { measuredText = it }
        Text(stringResource(R.string.bend_measured_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        Column(Modifier.fillMaxWidth().glassPanelDark(cornerRadius = 10.dp).padding(14.dp)) {
            ResultRow(stringResource(R.string.measured_wall_label), measuredCheck?.let { "${formatThickness(it.measuredMinimumMm)} mm" } ?: stringResource(R.string.measurement_not_provided), light = true)
            ResultRow(stringResource(R.string.bend_intrados_required_short), result?.let { "${formatThickness(it.intradosMinimumFinishedWallMm)} mm" } ?: "—", light = true)
            if (measuredCheck != null) {
                ResultRow(stringResource(R.string.finished_margin_label), "${formatThickness(measuredCheck.marginMm)} mm", light = true)
                Text(
                    stringResource(if (measuredCheck.passes) R.string.finished_pass else R.string.finished_fail),
                    color = if (measuredCheck.passes) PassOnNavy else FailOnNavy
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.calculation_report_details))
        NumberFieldText(stringResource(R.string.field_project_name_optional), projectNameText) { projectNameText = it }
        NumberFieldText(stringResource(R.string.field_engineer_name_optional), engineerNameText) { engineerNameText = it }
        NumberFieldText(stringResource(R.string.field_reference_optional), referenceText) { referenceText = it }
        NumberFieldText(stringResource(R.string.measurement_reference_label), measurementReferenceText) { measurementReferenceText = it }
        Button(
            onClick = {
                scope.launch {
                    exportFailed = false
                    val outputDir = File(context.getExternalFilesDir(null), "reports")
                    val file = runCatching {
                        BendReportGenerator.generate(
                            projectName = projectNameText.ifBlank { null },
                            engineerName = engineerNameText.ifBlank { null },
                            materialName = if (materialName == OTHER_MATERIAL) customMaterialName else materialName,
                            designTemperatureC = temperature ?: 20.0,
                            designReference = referenceText.trim().ifBlank { null },
                            outsideDiameterMm = od ?: 0.0,
                            bendRadiusMm = radius ?: 0.0,
                            designPressureBar = pressure ?: 0.0,
                            allowableStressMPa = stress ?: 0.0,
                            qualityFactorE = e ?: 0.0,
                            weldStrengthReductionW = w ?: 0.0,
                            yCoefficient = y ?: 0.0,
                            corrosionAllowanceMm = corrosion ?: 0.0,
                            erosionAllowanceMm = erosion ?: 0.0,
                            mechanicalAllowanceMm = mechanical ?: 0.0,
                            measuredMinimumMm = measured,
                            measurementReference = measurementReferenceText.trim().ifBlank { null },
                            outputDir = outputDir
                        )
                    }.getOrElse { exportFailed = true; return@launch }
                    ReportNotifier.notify(context, file)
                    context.startActivity(ReportNotifier.shareIntent(context, file))
                }
            },
            enabled = result != null,
            colors = ButtonDefaults.buttonColors(containerColor = Steel, contentColor = CardWhite),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(stringResource(R.string.export_bend_report), style = MaterialTheme.typography.titleSmall)
        }
        if (result == null) Text(stringResource(R.string.bend_report_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        if (exportFailed) Text(stringResource(R.string.report_export_failed), color = WarnBandText)
        Spacer(Modifier.height(8.dp))
    
        }
    }
}

// ---------------------------------------------------------------------
// Pressure vessel tab (ASME BPVC Section VIII Division 1)
// ---------------------------------------------------------------------

@Composable
private fun VesselTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var part by rememberSaveable { mutableStateOf(VesselPart.CYLINDRICAL_SHELL) }
    var pressureText by rememberSaveable { mutableStateOf("") }
    var radiusText by rememberSaveable { mutableStateOf("") }
    var materialName by rememberSaveable { mutableStateOf("SA-516 Gr.70 (plate)") }
    var customMaterialName by rememberSaveable { mutableStateOf("") }
    var temperatureText by rememberSaveable { mutableStateOf("20") }
    var stressText by rememberSaveable(materialName, customMaterialName, temperatureText) { mutableStateOf("") }
    var eText by rememberSaveable(materialName, customMaterialName, temperatureText) { mutableStateOf("") }
    var corrosionText by rememberSaveable { mutableStateOf("0") }
    var crownText by rememberSaveable { mutableStateOf("") }
    var knuckleText by rememberSaveable { mutableStateOf("") }
    var halfApexText by rememberSaveable { mutableStateOf("30") }
    var ellipsoidalRatioText by rememberSaveable { mutableStateOf("2") }
    var nominalText by rememberSaveable { mutableStateOf("") }
    var projectNameText by rememberSaveable { mutableStateOf("") }
    var engineerNameText by rememberSaveable { mutableStateOf("") }
    var referenceText by rememberSaveable { mutableStateOf("") }
    var exportFailed by remember { mutableStateOf(false) }

    val partLabels = listOf(
        VesselPart.CYLINDRICAL_SHELL to stringResource(R.string.vessel_part_shell),
        VesselPart.ELLIPSOIDAL_HEAD_2_1 to stringResource(R.string.vessel_part_ellipsoidal),
        VesselPart.TORISPHERICAL_HEAD to stringResource(R.string.vessel_part_torispherical),
        VesselPart.HEMISPHERICAL_HEAD to stringResource(R.string.vessel_part_hemispherical),
        VesselPart.CONICAL_HEAD to stringResource(R.string.vessel_part_conical)
    )
    val partLabelByValue = partLabels.toMap()

    val pressure = pressureText.toInputDoubleOrNull()
    val radius = radiusText.toInputDoubleOrNull()
    val temperature = temperatureText.toInputDoubleOrNull()?.takeIf { it > -273.15 }
    val stress = stressText.toInputDoubleOrNull()
    val e = eText.toInputDoubleOrNull()
    val corrosion = corrosionText.toInputDoubleOrNull()
    val crown = crownText.toInputDoubleOrNull()
    val knuckle = knuckleText.toInputDoubleOrNull()
    val halfApex = halfApexText.toInputDoubleOrNull()
    val ellipsoidalRatio = ellipsoidalRatioText.toInputDoubleOrNull()

    val validShapeInputs = when (part) {
        VesselPart.CONICAL_HEAD -> halfApex != null
        VesselPart.ELLIPSOIDAL_HEAD_2_1 -> ellipsoidalRatio != null
        VesselPart.TORISPHERICAL_HEAD -> (crownText.isBlank() || crown != null) && (knuckleText.isBlank() || knuckle != null)
        else -> true
    }
    val input = if (pressure != null && radius != null && stress != null && e != null && corrosion != null && temperature != null && validShapeInputs) {
        VesselInput(
            designPressureBar = pressure, insideRadiusMm = radius, allowableStressMPa = stress,
            jointEfficiency = e, corrosionAllowanceMm = corrosion,
            crownRadiusMm = if (part == VesselPart.TORISPHERICAL_HEAD) crown else null,
            knuckleRadiusMm = if (part == VesselPart.TORISPHERICAL_HEAD) knuckle else null,
            halfApexAngleDeg = halfApex ?: 30.0,
            ellipsoidalRatio = ellipsoidalRatio ?: 2.0
        )
    } else null
    val calculation = input?.let { runCatching { VesselThicknessCalculator.calculate(part, it) } }
    val result = calculation?.getOrNull()
    val nominal = nominalText.toInputDoubleOrNull()
    val mawp = if (result != null && nominal != null) {
        runCatching { VesselThicknessCalculator.maximumAllowablePressureBar(part, input, nominal) }.getOrNull()
    } else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompositionLocalProvider(LocalCalculatorBasis provides (CalculatorBasis.VESSEL)) {
        SectionLabel(stringResource(R.string.section_inputs))
        Text(stringResource(R.string.vessel_scope_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)

        CalculatorDropdownField(
            label = stringResource(R.string.field_vessel_part),
            options = partLabels.map { it.first },
            selected = part,
            display = { partLabelByValue.getValue(it) },
            onSelect = { part = it }
        )
        EquationCard(EngineeringEquations.vessel(part))
        CalculatorNumberField(stringResource(R.string.field_design_pressure), pressureText) { pressureText = it }
        CalculatorNumberField(stringResource(R.string.field_vessel_inside_radius), radiusText) { radiusText = it }
        MaterialTemperatureFields(materialName, { materialName = it }, customMaterialName, { customMaterialName = it }, temperatureText, { temperatureText = it })
        CalculatorNumberField(stringResource(R.string.field_allowable_stress), stressText) { stressText = it }
        CalculatorNumberField(stringResource(R.string.field_joint_efficiency), eText) { eText = it }
        CalculatorNumberField(stringResource(R.string.field_corrosion_allowance), corrosionText) { corrosionText = it }
        if (part == VesselPart.TORISPHERICAL_HEAD) {
            CalculatorNumberField(stringResource(R.string.field_vessel_crown_radius), crownText) { crownText = it }
            CalculatorNumberField(stringResource(R.string.field_vessel_knuckle_radius), knuckleText) { knuckleText = it }
            Text(stringResource(R.string.vessel_blank_default), style = MaterialTheme.typography.bodySmall, color = Ink2)
        }
        if (part == VesselPart.CONICAL_HEAD) {
            CalculatorNumberField(stringResource(R.string.field_vessel_half_apex), halfApexText) { halfApexText = it }
        }
        if (part == VesselPart.ELLIPSOIDAL_HEAD_2_1) {
            CalculatorNumberField(stringResource(R.string.field_vessel_ellipsoidal_ratio), ellipsoidalRatioText) { ellipsoidalRatioText = it }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel(stringResource(R.string.section_outputs))
        if (result == null) {
            Text(
                stringResource(if (calculation?.exceptionOrNull() is VesselScopeException) R.string.vessel_outside_scope else R.string.vessel_invalid),
                color = WarnBandText, style = MaterialTheme.typography.bodySmall
            )
        }
        Column(Modifier.fillMaxWidth().glassPanelDark(cornerRadius = 10.dp).padding(14.dp, 12.dp)) {
            if (part == VesselPart.CYLINDRICAL_SHELL) {
                ResultRow(stringResource(R.string.vessel_circumferential_thickness), result?.circumferentialThicknessMm?.let { "${formatThickness(it)} mm" } ?: "—", light = true)
                ResultRow(stringResource(R.string.vessel_longitudinal_thickness), result?.longitudinalThicknessMm?.let { "${formatThickness(it)} mm" } ?: "—", light = true)
            }
            ResultRow(stringResource(R.string.vessel_pressure_thickness), result?.let { "${formatThickness(it.pressureThicknessMm)} mm" } ?: "—", light = true)
            ResultRow(stringResource(R.string.vessel_order_thickness), result?.let { "${formatThickness(it.minimumOrderThicknessMm)} mm" } ?: "—", light = true)
        }
        Text(stringResource(R.string.vessel_ug16_note), style = MaterialTheme.typography.bodySmall, color = Ink2)

        Spacer(Modifier.height(10.dp))
        SectionLabel(stringResource(R.string.section_vessel_mawp_check))
        CalculatorNumberField(stringResource(R.string.field_nominal_vessel_thickness), nominalText) { nominalText = it }
        Column(Modifier.fillMaxWidth().glassPanelDark(cornerRadius = 10.dp).padding(14.dp, 12.dp)) {
            ResultRow(stringResource(R.string.vessel_mawp_label), mawp?.let { "${formatNum(it)} bar" } ?: "—", light = true)
        }
        if (nominalText.isNotBlank() && mawp == null) {
            Text(stringResource(R.string.vessel_mawp_unavailable), style = MaterialTheme.typography.bodySmall, color = WarnBandText)
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.calculation_report_details))
        NumberFieldText(stringResource(R.string.field_project_name_optional), projectNameText) { projectNameText = it }
        NumberFieldText(stringResource(R.string.field_engineer_name_optional), engineerNameText) { engineerNameText = it }
        NumberFieldText(stringResource(R.string.field_reference_optional), referenceText) { referenceText = it }
        Button(
            onClick = {
                scope.launch {
                    exportFailed = false
                    val vesselInput = input ?: return@launch
                    val outputDir = File(context.getExternalFilesDir(null), "reports")
                    val file = runCatching {
                        VesselReportGenerator.generate(
                            projectName = projectNameText.ifBlank { null },
                            engineerName = engineerNameText.ifBlank { null },
                            designReference = referenceText.trim().ifBlank { null },
                            materialName = if (materialName == OTHER_MATERIAL) customMaterialName else materialName,
                            designTemperatureC = temperature ?: 20.0,
                            part = part,
                            input = vesselInput,
                            nominalThicknessMm = nominal,
                            outputDir = outputDir
                        )
                    }.getOrElse { exportFailed = true; return@launch }
                    ReportNotifier.notify(context, file)
                    context.startActivity(ReportNotifier.shareIntent(context, file))
                }
            },
            enabled = result != null && (nominalText.isBlank() || mawp != null),
            colors = ButtonDefaults.buttonColors(containerColor = Steel, contentColor = CardWhite),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(stringResource(R.string.export_vessel_report), style = MaterialTheme.typography.titleSmall)
        }
        if (result == null) Text(stringResource(R.string.vessel_report_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        if (exportFailed) Text(stringResource(R.string.report_export_failed), color = WarnBandText)
        Spacer(Modifier.height(8.dp))
    
        }
    }
}

// ---------------------------------------------------------------------
// Miter bend tab (B31.3 §304.2.3 gives Pm; B31.1 §104.2.3 is a decision tree)
// ---------------------------------------------------------------------

@Composable
private fun MiterTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var projectNameText by rememberSaveable { mutableStateOf("") }
    var engineerNameText by rememberSaveable { mutableStateOf("") }
    var referenceText by rememberSaveable { mutableStateOf("") }
    var exportFailed by remember { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf(PipingCode.B31_3_PROCESS) }
    var odText by rememberSaveable { mutableStateOf("") }
    var thicknessText by rememberSaveable { mutableStateOf("") }
    var allowancesText by rememberSaveable { mutableStateOf("0") }
    var materialName by rememberSaveable { mutableStateOf(PipeMaterialData.materials.first().displayName) }
    var customMaterialName by rememberSaveable { mutableStateOf("") }
    var temperatureText by rememberSaveable { mutableStateOf("20") }
    var stressText by rememberSaveable(materialName, customMaterialName, temperatureText) { mutableStateOf("") }
    var eText by rememberSaveable(materialName, customMaterialName, temperatureText) { mutableStateOf("") }
    var wText by rememberSaveable(materialName, customMaterialName, temperatureText) { mutableStateOf("") }
    var pressureText by rememberSaveable { mutableStateOf("") }
    var totalAngleText by rememberSaveable { mutableStateOf("90") }
    var jointsText by rememberSaveable { mutableStateOf("1") }
    var miterRadiusText by rememberSaveable { mutableStateOf("") }
    var b311Spacing by rememberSaveable { mutableStateOf(MiterSpacing.CLOSE) }
    var segmentTmText by rememberSaveable { mutableStateOf("") }
    var segmentMeanRadiusText by rememberSaveable { mutableStateOf("") }
    var crotchText by rememberSaveable { mutableStateOf("") }
    var segmentsOk by rememberSaveable { mutableStateOf(false) }
    var fluidOk by rememberSaveable { mutableStateOf(false) }
    var cyclesOk by rememberSaveable { mutableStateOf(false) }
    var fullPenOk by rememberSaveable { mutableStateOf(false) }

    val codeLabels = mapOf(
        PipingCode.B31_3_PROCESS to stringResource(R.string.code_b313_process),
        PipingCode.B31_1_POWER to stringResource(R.string.code_b311_power)
    )

    val od = odText.toInputDoubleOrNull()
    val spacingLabels = mapOf(MiterSpacing.CLOSE to stringResource(R.string.b311_miter_close),
        MiterSpacing.WIDE to stringResource(R.string.b311_miter_wide))
    val thickness = thicknessText.toInputDoubleOrNull()
    val allowances = allowancesText.toInputDoubleOrNull()
    val temperature = temperatureText.toInputDoubleOrNull()?.takeIf { it > -273.15 }
    val stress = stressText.toInputDoubleOrNull()
    val e = eText.toInputDoubleOrNull()
    val w = wText.toInputDoubleOrNull()
    val pressure = pressureText.toInputDoubleOrNull()
    val totalAngle = totalAngleText.toInputDoubleOrNull()
    val joints = jointsText.toInputIntOrNull()
    val miterRadius = miterRadiusText.toInputDoubleOrNull()
    val crotch = crotchText.toInputDoubleOrNull()
    val theta = if (totalAngle != null && joints != null && joints >= 1) totalAngle / joints / 2.0 else null

    val geometry = if (od != null && thickness != null && totalAngle != null && joints != null && miterRadius != null) {
        runCatching { MiterGeometry(totalAngle, joints, od, thickness, miterRadius) }.getOrNull()
    } else null
    val b313Input = if (geometry != null && temperature != null && pressure != null && stress != null && e != null && w != null && allowances != null) {
        B313MiterInput(pressure, stress, e, w, allowances, geometry)
    } else null
    val b313Calculation = if (code == PipingCode.B31_3_PROCESS) {
        b313Input?.let { runCatching { B313MiterCalculator.calculate(it) } }
    } else null
    val b313Result = b313Calculation?.getOrNull()
    val requiredThickness = if (b313Result != null) b313Input?.let {
        runCatching { B313MiterCalculator.requiredThicknessMm(it) }.getOrNull()
    } else null
    val b311Calculation = if (code == PipingCode.B31_1_POWER && pressure != null && theta != null &&
        crotch != null && thickness != null) {
        runCatching {
            B311MiterCalculator.calculate(
                pressure, theta,
                B311MiterConditions(crotch, thickness, segmentsOk, fluidOk, cyclesOk, fullPenOk)
            )
        }
    } else null
    val b311Result = b311Calculation?.getOrNull()
    val segmentTm = segmentTmText.toInputDoubleOrNull()
    val segmentMeanRadius = segmentMeanRadiusText.toInputDoubleOrNull()
    val segmentInput = if (code == PipingCode.B31_1_POWER && segmentTm != null && segmentMeanRadius != null && theta != null)
        B311MiterSegmentInput(segmentTm, segmentMeanRadius, theta, b311Spacing, miterRadius) else null
    val segmentCalculation = segmentInput?.let { runCatching { B311MiterSegmentCalculator.requiredThicknessMm(it) } }
    val segmentRequested = segmentTmText.isNotBlank() || segmentMeanRadiusText.isNotBlank()
    val canExport = if (code == PipingCode.B31_3_PROCESS) b313Result != null
        else b311Result != null && (!segmentRequested || segmentCalculation?.isSuccess == true)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CompositionLocalProvider(LocalCalculatorBasis provides (if (code == PipingCode.B31_1_POWER) CalculatorBasis.B311 else CalculatorBasis.B313)) {
        SectionLabel(stringResource(R.string.section_inputs))
        CalculatorDropdownField(
            label = stringResource(R.string.field_design_code),
            options = listOf(PipingCode.B31_3_PROCESS, PipingCode.B31_1_POWER),
            selected = code,
            display = { codeLabels.getValue(it) },
            onSelect = { code = it }
        )
        EquationCard(if (code == PipingCode.B31_3_PROCESS) EngineeringEquations.MITER_313 else EngineeringEquations.MITER_311)
        Text(stringResource(R.string.miter_scope_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)

        CalculatorNumberField(stringResource(R.string.field_bend_od), odText) { odText = it }
        CalculatorNumberField(stringResource(if (code == PipingCode.B31_1_POWER) R.string.field_miter_nominal_tn else R.string.field_miter_thickness), thicknessText) { thicknessText = it }
        if (code == PipingCode.B31_3_PROCESS) {
            CalculatorNumberField(stringResource(R.string.field_miter_allowances), allowancesText) { allowancesText = it }
            MaterialTemperatureFields(materialName, { materialName = it }, customMaterialName, { customMaterialName = it }, temperatureText, { temperatureText = it })
        CalculatorNumberField(stringResource(R.string.field_allowable_stress), stressText) { stressText = it }
            CalculatorNumberField(stringResource(R.string.field_quality_factor), eText) { eText = it }
            CalculatorNumberField(stringResource(R.string.field_weld_factor), wText) { wText = it }
        }
        CalculatorNumberField(stringResource(R.string.field_design_pressure), pressureText) { pressureText = it }
        CalculatorNumberField(stringResource(R.string.field_miter_total_angle), totalAngleText) { totalAngleText = it }
        CalculatorNumberField(stringResource(R.string.field_miter_joints), jointsText) { jointsText = it }
        if (code == PipingCode.B31_3_PROCESS) {
            CalculatorNumberField(stringResource(R.string.field_miter_radius), miterRadiusText) { miterRadiusText = it }
        } else {
            CalculatorNumberField(stringResource(R.string.field_miter_crotch_b), crotchText) { crotchText = it }
        }
        theta?.let {
            NumericReadout(text = "${stringResource(R.string.miter_theta_label)} = ${formatThickness(it)}°", fontSize = 13.sp, color = Steel)
        }

        if (code == PipingCode.B31_1_POWER) {
            SwitchRow(stringResource(R.string.switch_segments_1041), segmentsOk) { segmentsOk = it }
            SwitchRow(stringResource(R.string.switch_fluid), fluidOk) { fluidOk = it }
            SwitchRow(stringResource(R.string.switch_cycles), cyclesOk) { cyclesOk = it }
            SwitchRow(stringResource(R.string.switch_full_pen), fullPenOk) { fullPenOk = it }
        }

        Spacer(Modifier.height(10.dp))
        SectionLabel(stringResource(R.string.section_outputs))
        val invalid = if (code == PipingCode.B31_3_PROCESS) b313Result == null else b311Result == null
        if (invalid) {
            val message = when {
                code == PipingCode.B31_3_PROCESS && geometry?.deflectionPerJointDeg?.let { it <= 3.0 } == true -> R.string.miter_small_offset_note
                b313Calculation?.exceptionOrNull() is MiterScopeException -> R.string.miter_outside_scope
                else -> R.string.miter_invalid
            }
            Text(stringResource(message), color = WarnBandText, style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.fillMaxWidth().glassPanelDark(cornerRadius = 10.dp).padding(14.dp, 12.dp)) {
            ResultRow(stringResource(R.string.miter_theta_label), theta?.let { "${formatThickness(it)}°" } ?: "—", light = true)
            if (code == PipingCode.B31_3_PROCESS) {
                ResultRow(stringResource(R.string.miter_spacing_class_label), b313Result?.let {
                    stringResource(if (it.multipleMiter) R.string.miter_closely_spaced else R.string.miter_widely_spaced)
                } ?: "—", light = true)
                ResultRow(stringResource(R.string.miter_equation_label), b313Result?.let {
                    stringResource(when (it.equation) {
                        MiterEquation.MULTIPLE_4A_4B -> R.string.miter_equation_multiple
                        MiterEquation.SINGLE_4A -> R.string.miter_equation_single_low
                        MiterEquation.SINGLE_4C -> R.string.miter_equation_single_high
                    })
                } ?: "—", light = true)
                ResultRow(stringResource(R.string.miter_pm_label), b313Result?.let { "${formatNum(it.maxAllowablePressureBar)} bar" } ?: "—", light = true)
                b313Result?.pmEqA?.let { ResultRow(stringResource(R.string.miter_pm4a_label), "${formatNum(it)} bar", light = true) }
                b313Result?.pmEqB?.let { ResultRow(stringResource(R.string.miter_pm4b_label), "${formatNum(it)} bar", light = true) }
                ResultRow(stringResource(R.string.miter_margin_label), b313Result?.let { "${formatNum(it.marginBar)} bar" } ?: "—", light = true)
                ResultRow(stringResource(R.string.miter_required_t_label), requiredThickness?.let { "${formatThickness(it)} mm" } ?: "—", light = true)
                ResultRow(stringResource(R.string.miter_minimum_radius), b313Result?.let { "${formatThickness(it.minimumEffectiveRadiusMm)} mm" } ?: "—", light = true)
                ResultRow(stringResource(R.string.miter_extension), b313Result?.let { "${formatThickness(it.minimumWallExtensionMm)} mm" } ?: "—", light = true)
            } else {
                ResultRow(stringResource(R.string.miter_path_label), b311Result?.let {
                    stringResource(when (it.path) {
                        B311MiterPath.TEN_PSI -> R.string.miter_path_ten
                        B311MiterPath.HUNDRED_PSI -> R.string.miter_path_hundred
                        B311MiterPath.QUALIFICATION -> R.string.miter_path_qualification
                    })
                } ?: "—", light = true)
                ResultRow(stringResource(R.string.miter_limit_label), b311Result?.pressureLimitBar?.let { "${formatNum(it)} bar" } ?: "—", light = true)
            }
        }
        if (code == PipingCode.B31_3_PROCESS) {
            b313Result?.let {
                Text(
                    stringResource(if (it.passes) R.string.miter_pass else R.string.miter_fail),
                    color = if (it.passes) Green else Orange,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            b311Result?.let { result ->
                if (result.qualificationRequired) {
                    Text(stringResource(R.string.miter_qualification_required), color = WarnBandText, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        stringResource(if (result.passes == true) R.string.miter_b311_pass else R.string.miter_fail),
                        color = if (result.passes == true) Green else Orange,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.miter_conditions_title), style = MaterialTheme.typography.labelLarge, color = Steel, fontWeight = FontWeight.SemiBold)
                result.conditions.forEach { check ->
                    val description = stringResource(when (check.id) {
                        "(a)(1)" -> R.string.miter_condition_steep
                        "(b)(1)-(2)" -> R.string.miter_condition_shallow
                        "(a)(2)" -> R.string.switch_segments_1041
                        "(a)(3)" -> R.string.switch_fluid
                        "(a)(4)" -> R.string.switch_cycles
                        else -> R.string.switch_full_pen
                    })
                    Text(
                        "$description — ${stringResource(if (check.satisfied) R.string.miter_condition_met else R.string.miter_condition_not_met)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (check.satisfied) Green else Orange
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.miter_segments_note), style = MaterialTheme.typography.bodySmall, color = WarnBandText)
            }
            SectionLabel(stringResource(R.string.miter_segment_section))
            Text(stringResource(R.string.miter_spacing_basis), style = MaterialTheme.typography.bodySmall, color = Ink2)
            CalculatorDropdownField(stringResource(R.string.miter_spacing_class_label), MiterSpacing.values().toList(), b311Spacing,
                display = { spacingLabels.getValue(it) }, onSelect = { b311Spacing = it })
            CalculatorNumberField(stringResource(R.string.miter_segment_tm), segmentTmText) { segmentTmText = it }
            CalculatorNumberField(stringResource(R.string.miter_segment_r), segmentMeanRadiusText) { segmentMeanRadiusText = it }
            if (b311Spacing == MiterSpacing.CLOSE) CalculatorNumberField(stringResource(R.string.field_miter_radius), miterRadiusText) { miterRadiusText = it }
            ResultRow(stringResource(R.string.miter_segment_ts), segmentCalculation?.getOrNull()?.let { "${formatThickness(it)} mm" } ?: "—")
            if (segmentCalculation?.isFailure == true) Text(stringResource(R.string.miter_segment_invalid), color = WarnBandText)
        }
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.calculation_report_details))
        NumberFieldText(stringResource(R.string.field_project_name_optional), projectNameText) { projectNameText = it }
        NumberFieldText(stringResource(R.string.field_engineer_name_optional), engineerNameText) { engineerNameText = it }
        NumberFieldText(stringResource(R.string.field_reference_optional), referenceText) { referenceText = it }
        Button(onClick = {
            scope.launch {
                exportFailed = false
                val file = runCatching {
                    MiterReportGenerator.generate(code, projectNameText.ifBlank { null }, engineerNameText.ifBlank { null },
                        referenceText.ifBlank { null }, if (materialName == OTHER_MATERIAL) customMaterialName else materialName,
                        temperature, b313Input.takeIf { code == PipingCode.B31_3_PROCESS }, requireNotNull(pressure), requireNotNull(theta),
                        if (crotch != null && thickness != null) B311MiterConditions(crotch, thickness, segmentsOk, fluidOk, cyclesOk, fullPenOk) else null,
                        segmentInput, File(context.getExternalFilesDir(null), "reports"))
                }.getOrElse { exportFailed = true; return@launch }
                ReportNotifier.notify(context, file)
                context.startActivity(ReportNotifier.shareIntent(context, file))
            }
        }, enabled = canExport, colors = ButtonDefaults.buttonColors(containerColor = Steel, contentColor = CardWhite),
            shape = RoundedCornerShape(9.dp), modifier = Modifier.fillMaxWidth().height(46.dp)) {
            Text(stringResource(R.string.export_miter_report), style = MaterialTheme.typography.titleSmall)
        }
        if (exportFailed) Text(stringResource(R.string.report_export_failed), color = WarnBandText)
        Spacer(Modifier.height(8.dp))

        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) { CalculatorHelpLabel(label) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---------------------------------------------------------------------
// Shared bits
// ---------------------------------------------------------------------

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Steel,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 5.dp, top = 6.dp)
    )
}

@Composable
private fun ResultRow(label: String, value: String, light: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodySmall, color = if (light) NavyMuted else Ink2)
        NumericReadout(text = value, modifier = Modifier.weight(1f), fontSize = 13.sp, weight = FontWeight.SemiBold, color = if (light) Amber else Ink)
    }
}

@Composable
private fun NumberFieldText(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(bottom = 10.dp)) {
        CalculatorHelpLabel(label)
        OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

private fun formatNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.2f", value)





private fun formatThickness(value: Double): String = DisplayFormat.millimetres(value)

@Composable
private fun CalculatorNumberField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(bottom = 8.dp)) {
        CalculatorHelpLabel(label)
        OutlinedTextField(value = value, onValueChange = onChange, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily, textDirection = androidx.compose.ui.text.style.TextDirection.Ltr))
    }
}

@Composable
private fun <T> CalculatorDropdownField(label: String, options: List<T>, selected: T?, display: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        CalculatorHelpLabel(label)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let(display) ?: "—", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(display(option)) }, onClick = { onSelect(option); expanded = false }) }
            }
        }
    }
}

@Composable
private fun CalculatorHelpLabel(label: String) {
    var showHelp by remember { mutableStateOf(false) }
    val help = calculatorHelp(label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Ink2)
        IconButton(onClick = { showHelp = true }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = stringResource(R.string.field_help_description, label), tint = Steel, modifier = Modifier.size(20.dp))
        }
    }
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text(label) },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(help) } }, confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.help_close)) } })
}

private enum class CalculatorBasis { B313, B311, VESSEL, WEIGHT }
private val LocalCalculatorBasis = staticCompositionLocalOf { CalculatorBasis.B313 }

@Composable
private fun EquationCard(formula: String) {
    Column(Modifier.fillMaxWidth().glassPanel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.equation_heading), style = MaterialTheme.typography.labelLarge, color = Steel)
        Text(formula, style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily,
            textDirection = androidx.compose.ui.text.style.TextDirection.Ltr), color = Ink)
        Text(stringResource(if (LocalCalculatorBasis.current == CalculatorBasis.WEIGHT) R.string.weight_equation_units else R.string.pressure_equation_units),
            style = MaterialTheme.typography.bodySmall, color = Ink2)
    }
}

@Composable
private fun MaterialTemperatureFields(material: String, onMaterial: (String) -> Unit,
    custom: String, onCustom: (String) -> Unit, temperature: String, onTemperature: (String) -> Unit) {
    val options = if (LocalCalculatorBasis.current == CalculatorBasis.VESSEL)
        listOf("SA-516 Gr.70 (plate)", "SA-516 Gr.60 (plate)", "SA-106 Gr.B (pipe)", "SA-240 Type 304 (plate)", "SA-240 Type 316 (plate)")
    else PipeMaterialData.materials.map { it.displayName }
    val otherLabel = stringResource(R.string.material_other)
    CalculatorDropdownField(stringResource(R.string.field_material), options + OTHER_MATERIAL, material,
        display = { if (it == OTHER_MATERIAL) otherLabel else it }, onSelect = onMaterial)
    if (material == OTHER_MATERIAL) NumberFieldText(stringResource(R.string.field_material_name), custom, onCustom)
    CalculatorNumberField(stringResource(R.string.field_design_temperature), temperature, onTemperature)
    Text(stringResource(R.string.material_manual_stress_note), style = MaterialTheme.typography.bodySmall, color = Ink2)
}

@Composable
private fun StressSourceHint(code: PipingCode, material: String, temperature: String, value: String, onUse: (String) -> Unit) {
    val suggestion = VerifiedAllowableStress.lookup(code, material, temperature.toInputDoubleOrNull())
    if (suggestion == null) {
        Text(stringResource(R.string.stress_manual_source_note), style = MaterialTheme.typography.bodySmall, color = Ink2)
    } else {
        val referenceValue = DisplayFormat.number(suggestion.stressMPa, 6)
        Text(stringResource(R.string.stress_verified_source, referenceValue, suggestion.columnF.toInt(), suggestion.stressKsi.toString()),
            style = MaterialTheme.typography.bodySmall, color = Ink2)
        if (value != referenceValue) TextButton(onClick = { onUse(referenceValue) }) {
            Text(stringResource(R.string.use_verified_stress))
        }
    }
}

@Composable
private fun calculatorHelp(label: String): String {
    val basis = LocalCalculatorBasis.current
    if (listOf(R.string.switch_segments_1041, R.string.switch_fluid, R.string.switch_cycles, R.string.switch_full_pen)
            .any { label == stringResource(it) }) return stringResource(R.string.guide_b311_conditions)
    if (label == stringResource(R.string.field_allowable_stress)) return stringResource(when (basis) {
        CalculatorBasis.B311 -> R.string.guide_stress_b311
        CalculatorBasis.VESSEL -> R.string.guide_stress_vessel
        else -> R.string.guide_stress_b313
    })
    if (label == stringResource(R.string.field_quality_factor)) return stringResource(if (basis == CalculatorBasis.B311) R.string.guide_e_b311 else R.string.help_e)
    if (label == stringResource(R.string.field_weld_factor)) return stringResource(if (basis == CalculatorBasis.B311) R.string.guide_w_b311 else R.string.help_w)
    if (label == stringResource(R.string.field_y_coefficient)) return stringResource(if (basis == CalculatorBasis.B311) R.string.guide_y_b311 else R.string.help_y)
    if (label == stringResource(R.string.field_miter_radius)) return stringResource(if (basis == CalculatorBasis.B311) R.string.guide_radius_b311 else R.string.guide_radius_b313)
    if (label == stringResource(R.string.field_miter_nominal_tn)) return stringResource(R.string.guide_miter_t_b311)
    if (label == stringResource(R.string.field_material) && basis == CalculatorBasis.VESSEL) return stringResource(R.string.guide_material_vessel)
    val pairs = listOf(
        R.string.field_design_code to R.string.guide_code,
        R.string.field_b311_material_group to R.string.guide_b311_group,
        R.string.field_miter_thickness to R.string.guide_miter_t,
        R.string.field_miter_allowances to R.string.guide_miter_c,
        R.string.field_miter_total_angle to R.string.guide_miter_angle,
        R.string.field_miter_joints to R.string.guide_miter_joints,
        R.string.field_miter_crotch_b to R.string.guide_miter_b,
        R.string.miter_spacing_class_label to R.string.guide_miter_spacing,
        R.string.miter_segment_tm to R.string.guide_miter_tm,
        R.string.miter_segment_r to R.string.guide_miter_r,
        R.string.field_reference_optional to R.string.help_reference,
        R.string.field_material to R.string.help_material,
        R.string.field_material_name to R.string.help_material_name,
        R.string.field_material_class to R.string.help_material_class,
        R.string.field_yield_strength to R.string.help_yield,
        R.string.field_design_temperature to R.string.help_temperature,
        R.string.field_allowable_stress to R.string.stress_input_hint,
        R.string.field_nominal_size to R.string.help_nps,
        R.string.field_manual_od to R.string.help_od,
        R.string.field_od_unit to R.string.help_unit,
        R.string.field_wall_unit to R.string.help_unit,
        R.string.field_design_pressure to R.string.help_pressure,
        R.string.field_corrosion_allowance to R.string.help_corrosion,
        R.string.field_erosion_allowance to R.string.help_erosion,
        R.string.field_mechanical_allowance to R.string.mechanical_allowance_hint,
        R.string.field_mill_tolerance to R.string.mill_tolerance_hint,
        R.string.field_quality_factor to R.string.help_e,
        R.string.field_weld_factor to R.string.help_w,
        R.string.field_y_coefficient to R.string.help_y,
        R.string.design_reference_label to R.string.help_reference,
        R.string.field_schedule to R.string.help_schedule,
        R.string.wall_thickness_label to R.string.help_nominal,
        R.string.measured_wall_label to R.string.measured_wall_help,
        R.string.measurement_reference_label to R.string.help_measurement_reference,
        R.string.field_project_name_optional to R.string.help_project,
        R.string.field_engineer_name_optional to R.string.help_engineer,
        R.string.field_density to R.string.help_density,
        R.string.include_water_fill to R.string.guide_water_fill,
        R.string.field_pipe_length to R.string.help_length,
        R.string.field_bend_od to R.string.help_od,
        R.string.field_bend_radius to R.string.help_bend_radius,
        R.string.measured_intrados_wall_label to R.string.measured_wall_help,
        R.string.field_vessel_part to R.string.help_vessel_part,
        R.string.field_vessel_inside_radius to R.string.help_vessel_radius,
        R.string.field_vessel_crown_radius to R.string.help_vessel_crown,
        R.string.field_vessel_knuckle_radius to R.string.help_vessel_knuckle,
        R.string.field_vessel_half_apex to R.string.help_vessel_half_apex,
        R.string.field_vessel_ellipsoidal_ratio to R.string.help_vessel_ellipsoidal_ratio,
        R.string.field_joint_efficiency to R.string.help_joint_efficiency,
        R.string.field_nominal_vessel_thickness to R.string.help_nominal_vessel_thickness)
    for ((key, help) in pairs) if (label == stringResource(key)) return stringResource(help)
    return stringResource(R.string.guide_generic)
}
