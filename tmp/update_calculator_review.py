from pathlib import Path

root = Path('app/src/main/java/com/ahmedismail/flowtrack')
p = root / 'ui/screens/CalculatorsScreen.kt'
s = p.read_text(encoding='utf-8-sig')
def replace(old, new):
    global s
    assert old in s, old[:100]
    s = s.replace(old, new, 1)

replace('import com.ahmedismail.flowtrack.util.QualityFactorPreset', 'import com.ahmedismail.flowtrack.util.ThicknessScopeException')
replace('    var temperatureText by rememberSaveable { mutableStateOf("20") }\n', '')
replace('    var allowableStressText by rememberSaveable { mutableStateOf(formatNum(PipeMaterialData.materials.first().allowableStressMPa)) }\n    var allowableStressManuallyEdited by rememberSaveable { mutableStateOf(false) }', '''    var temperatureText by rememberSaveable { mutableStateOf("20") }
    // A material or temperature change invalidates coefficients immediately in composition.
    val coefficientKey = listOf(selectedMaterialName, customMaterialName, customMaterialClass.name, temperatureText)
    var allowableStressText by rememberSaveable(coefficientKey) { mutableStateOf("") }
    var designReference by rememberSaveable(coefficientKey) { mutableStateOf("") }''')
start = s.index('    // NPS: either a standard size')
end = s.index('    var selectedNpsLabel', start)
s = s[:start] + '    // B36.10-2022 sizes; custom OD is independent of nominal pipe size.\n' + s[end:]
start = s.index('    // Standard ASTM/API seamless-pipe')
end = s.index('    var selectedSchedule', start)
s = s[:start] + '''    var erosionAllowanceText by rememberSaveable { mutableStateOf("0") }
    var mechanicalAllowanceText by rememberSaveable { mutableStateOf("0") }
    // Manufacturing tolerances depend on the product specification, not on B31.3 alone.
    var millToleranceText by rememberSaveable(selectedMaterialName, customMaterialName) { mutableStateOf("") }
    var qualityFactorText by rememberSaveable(coefficientKey) { mutableStateOf("") }
    var weldFactorText by rememberSaveable(coefficientKey) { mutableStateOf("") }
    var yCoefficientText by rememberSaveable(coefficientKey) { mutableStateOf("") }
    var yManuallyEdited by rememberSaveable(coefficientKey) { mutableStateOf(false) }
    val automaticY = temperatureText.toInputDoubleOrNull()?.let { temperature ->
        runCatching { PipeThicknessCalculator.defaultYCoefficient(materialClass, temperature) }.getOrNull()
    }

''' + s[end:]
start = s.index('    // Re-derive Y automatically')
end = s.index('    fun applyMaterial', start)
s = s[:start] + s[end:]
start = s.index('        // A fresh material pick')
end = s.index('\n    }', start)
s = s[:start] + s[end:]
replace('        Text(stringResource(R.string.schedule_basis_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)', '        Text(stringResource(R.string.thickness_scope_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)\n        Text(stringResource(R.string.schedule_basis_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)')
replace('                    allowableStressText = ""\n                    allowableStressManuallyEdited = true\n                    yManuallyEdited = false\n', '')
replace('        NumberField(stringResource(R.string.field_design_temperature), temperatureText) { temperatureText = it }\n', '')
replace('        Column(Modifier.padding(bottom = 10.dp)) {\n            OutlinedTextField(', '''        NumberField(stringResource(R.string.field_design_temperature), temperatureText) { temperatureText = it }
        Text(stringResource(R.string.stress_input_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        Column(Modifier.padding(bottom = 10.dp)) {
            OutlinedTextField(''')
replace('onValueChange = { allowableStressText = it; allowableStressManuallyEdited = true }', 'onValueChange = { allowableStressText = it }')
replace('        NumberField(stringResource(R.string.field_mill_tolerance), millToleranceText) { millToleranceText = it }', '''        NumberField(stringResource(R.string.field_erosion_allowance), erosionAllowanceText) { erosionAllowanceText = it }
        NumberField(stringResource(R.string.field_mechanical_allowance), mechanicalAllowanceText) { mechanicalAllowanceText = it }
        Text(stringResource(R.string.mechanical_allowance_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        NumberField(stringResource(R.string.field_mill_tolerance), millToleranceText) { millToleranceText = it }
        Text(stringResource(R.string.mill_tolerance_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)''')
start = s.index('        SectionLabel("ASME B31.3 — E / W / Y")')
end = s.index('        val od: Double?', start)
s = s[:start] + '''        SectionLabel("ASME B31.3-2024 — E / W / Y")
        Text(stringResource(R.string.factors_input_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        NumberField(stringResource(R.string.field_quality_factor), qualityFactorText) { qualityFactorText = it }
        NumberField(stringResource(R.string.field_weld_factor), weldFactorText) { weldFactorText = it }
        NumberField(stringResource(R.string.field_y_coefficient),
            if (yManuallyEdited) yCoefficientText else automaticY?.let { String.format(Locale.US, "%.6f", it) }.orEmpty()
        ) { yCoefficientText = it; yManuallyEdited = true }
        Text(stringResource(if (yManuallyEdited) R.string.y_manual_hint else R.string.y_auto_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        if (yManuallyEdited) TextButton(onClick = { yManuallyEdited = false }) { Text(stringResource(R.string.y_use_table)) }
        NumberFieldText(stringResource(R.string.design_reference_label), designReference) { designReference = it }

''' + s[end:]
replace('        val y = yCoefficientText.toInputDoubleOrNull()', '        val y = if (yManuallyEdited) yCoefficientText.toInputDoubleOrNull() else automaticY')
replace('        val corrosion = corrosionAllowanceText.toInputDoubleOrNull()', '''        val corrosion = corrosionAllowanceText.toInputDoubleOrNull()
        val erosion = erosionAllowanceText.toInputDoubleOrNull()
        val mechanical = mechanicalAllowanceText.toInputDoubleOrNull()''')
replace('val result = if (od != null && pressure != null && allowableStress != null && e != null && w != null && y != null && corrosion != null && tolerance != null && temperatureText.toInputDoubleOrNull()?.isFinite() == true)', 'val calculation = if (od != null && pressure != null && allowableStress != null && e != null && w != null && y != null && corrosion != null && erosion != null && mechanical != null && tolerance != null && temperatureText.toInputDoubleOrNull()?.let { it > -273.15 } == true)')
replace('qualityFactorE = e, weldStrengthReductionW = w, yCoefficient = y, corrosionAllowanceMm = corrosion', 'qualityFactorE = e, weldStrengthReductionW = w, yCoefficient = y, corrosionAllowanceMm = corrosion,\n                    erosionAllowanceMm = erosion, mechanicalAllowanceMm = mechanical')
replace('            ) }.getOrNull()\n        } else null', '''            ) }
        } else null
        val result = calculation?.getOrNull()
        val requiredNominal = if (result != null && tolerance != null) runCatching {
            PipeThicknessCalculator.requiredNominalThicknessMm(result, tolerance)
        }.getOrNull() else null''')
replace('if (result == null) Text(stringResource(R.string.calculation_invalid)', 'if (result == null) Text(stringResource(if (calculation?.exceptionOrNull() is ThicknessScopeException) R.string.thickness_outside_scope else R.string.calculation_invalid)')
replace('result?.let { "${formatNum(it.calculatedThicknessMm)} mm" }', 'result?.let { "${formatThickness(it.calculatedThicknessMm)} mm" }')
replace('result?.let { "${formatNum(it.minimumRequiredThicknessMm)} mm" }', 'result?.let { "${formatThickness(it.minimumRequiredThicknessMm)} mm" }')
replace('            ResultRow(stringResource(R.string.minimum_required_thickness_label), result?.let { "${formatThickness(it.minimumRequiredThicknessMm)} mm" } ?: "—", light = true)', '''            ResultRow(stringResource(R.string.minimum_required_thickness_label), result?.let { "${formatThickness(it.minimumRequiredThicknessMm)} mm" } ?: "—", light = true)
            ResultRow(stringResource(R.string.required_nominal_thickness), requiredNominal?.let { "${formatThickness(it)} mm" } ?: "—", light = true)''')
start = s.index('        if (od != null && od.isFinite() && scheduleWallMm')
end = s.index('            val accentColor', start)
s = s[:start] + '''        val wallCheck = if (result != null && od != null && scheduleWallMm != null && tolerance != null) runCatching {
            PipeThicknessCalculator.checkNominalWall(result, od, scheduleWallMm, tolerance)
        }.getOrNull() else null
        if (wallCheck != null && od != null && scheduleWallMm != null) {
            val id = od - 2 * scheduleWallMm
            val effectiveWall = wallCheck.effectiveWallMm
            val passes = wallCheck.passes
''' + s[end:]
replace('val accentColor = when (passes) { true -> Green; false -> Orange; null -> Line }', 'val accentColor = if (passes) Green else Orange')
replace('        val canExport = result != null', '        val canExport = result != null && requiredNominal != null && (scheduleWallMm == null || wallCheck != null)')
replace('                        corrosionAllowanceMm = corrosion ?: 0.0,', '''                        corrosionAllowanceMm = corrosion ?: 0.0,
                        erosionAllowanceMm = erosion ?: 0.0,
                        mechanicalAllowanceMm = mechanical ?: 0.0,
                        designReference = designReference.trim().ifBlank { "Not provided" },''')
s += '\nprivate fun formatThickness(value: Double): String = if (value > 0 && value < 0.0001) String.format(Locale.US, "%.4e", value) else String.format(Locale.US, "%.4f", value)\n'
p.write_text(s, encoding='utf-8')
