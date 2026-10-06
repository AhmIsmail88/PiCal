from pathlib import Path

p = Path('app/src/main/java/com/ahmedismail/flowtrack/ui/screens/CalculatorsScreen.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('import com.ahmedismail.flowtrack.util.PipeThicknessResult',
              'import com.ahmedismail.flowtrack.util.PipeThicknessResult\nimport com.ahmedismail.flowtrack.util.EngineeringEquations\nimport com.ahmedismail.flowtrack.data.reference.VerifiedAllowableStress')
s = s.replace('    var allowableStressText by rememberSaveable(coefficientKey) { mutableStateOf("") }',
              '    val stressDefault = VerifiedAllowableStress.lookup(code, selectedMaterialName, temperatureText.toInputDoubleOrNull())\n'
              '    var allowableStressText by rememberSaveable(coefficientKey) { mutableStateOf(stressDefault?.let { DisplayFormat.number(it.stressMPa, 6) }.orEmpty()) }')
s = s.replace('    var qualityFactorText by rememberSaveable(coefficientKey) { mutableStateOf("") }',
              '    var qualityFactorText by rememberSaveable(coefficientKey) { mutableStateOf(stressDefault?.let { DisplayFormat.number(it.qualityFactorE, 2) }.orEmpty()) }')

def function_end(text, start):
    opening = text.index('{', start)
    depth = 0
    for i in range(opening, len(text)):
        if text[i] == '{': depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0: return i
    raise RuntimeError('Unbalanced function')

for name,basis in [('PipeThicknessTab','if (code == PipingCode.B31_1_POWER) CalculatorBasis.B311 else CalculatorBasis.B313'),
                   ('PipeWeightTab','CalculatorBasis.WEIGHT'), ('PipeBendTab','CalculatorBasis.B313'),
                   ('VesselTab','CalculatorBasis.VESSEL'), ('MiterTab','if (code == PipingCode.B31_1_POWER) CalculatorBasis.B311 else CalculatorBasis.B313')]:
    a = s.index('private fun '+name+'(')
    b = function_end(s,a)+1
    block = s[a:b]
    col = block.index('\n    Column(')
    opening = block.index('{',col)
    # Last top-level column is the last statement in each tab.
    closing = block.rfind('}',0,block.rfind('}'))
    block = block[:opening+1] + f'\n        CompositionLocalProvider(LocalCalculatorBasis provides ({basis})) {{' + block[opening+1:closing] + '\n        }\n    ' + block[closing:]

    if name in ('PipeThicknessTab','MiterTab'):
        anchor = '            onSelect = { code = it }\n        )'
        formula = 'EngineeringEquations.pipe(code)' if name=='PipeThicknessTab' else 'if (code == PipingCode.B31_3_PROCESS) EngineeringEquations.MITER_313 else EngineeringEquations.MITER_311'
        block = block.replace(anchor,anchor+'\n        EquationCard('+formula+')',1)
    elif name == 'VesselTab':
        anchor = '            onSelect = { part = it }\n        )'
        block = block.replace(anchor,anchor+'\n        EquationCard(EngineeringEquations.vessel(part))',1)
    else:
        formula = 'EngineeringEquations.WEIGHT' if name=='PipeWeightTab' else 'EngineeringEquations.BEND'
        block = block.replace('        SectionLabel(stringResource(R.string.section_inputs))',
                              '        EquationCard('+formula+')\n        SectionLabel(stringResource(R.string.section_inputs))',1)

    if name in ('PipeBendTab','VesselTab','MiterTab'):
        material = '"SA-516 Gr.70 (plate)"' if name=='VesselTab' else 'PipeMaterialData.materials.first().displayName'
        anchor = '    var stressText by rememberSaveable { mutableStateOf("") }'
        setup = f'    var materialName by rememberSaveable {{ mutableStateOf({material}) }}\n    var customMaterialName by rememberSaveable {{ mutableStateOf("") }}\n    var temperatureText by rememberSaveable {{ mutableStateOf("20") }}\n    var stressText by rememberSaveable(materialName, temperatureText) {{ mutableStateOf("") }}'
        block = block.replace(anchor,setup,1)
        anchor = '        CalculatorNumberField(stringResource(R.string.field_allowable_stress), stressText) { stressText = it }'
        fields = '        MaterialTemperatureFields(materialName, { materialName = it }, customMaterialName, { customMaterialName = it }, temperatureText, { temperatureText = it })\n'+anchor
        block = block.replace(anchor,fields,1)
        # Material/temperature changes invalidate coefficients that depend on them.
        block = block.replace('by rememberSaveable { mutableStateOf("") }\n    var wText', 'by rememberSaveable(materialName, temperatureText) { mutableStateOf("") }\n    var wText',1)
        block = block.replace('var wText by rememberSaveable {', 'var wText by rememberSaveable(materialName, temperatureText) {',1)
        block = block.replace('    val stress = stressText.toInputDoubleOrNull()',
                              '    val temperature = temperatureText.toInputDoubleOrNull()?.takeIf { it > -273.15 }\n    val stress = stressText.toInputDoubleOrNull()',1)
        if name == 'PipeBendTab':
            block = block.replace('listOf(pressure, od, radius, stress,', 'listOf(temperature, pressure, od, radius, stress,',1)
            block = block.replace('                            materialName = null,', '                            materialName = if (materialName == OTHER_MATERIAL) customMaterialName else materialName,\n                            designTemperatureC = temperature ?: 20.0,',1)
        elif name == 'VesselTab':
            block = block.replace('corrosion != null && validShapeInputs)', 'corrosion != null && temperature != null && validShapeInputs)',1)
            block = block.replace('                            part = part,', '                            materialName = if (materialName == OTHER_MATERIAL) customMaterialName else materialName,\n                            designTemperatureC = temperature ?: 20.0,\n                            part = part,',1)
        else:
            block = block.replace('geometry != null && pressure != null && stress != null', 'geometry != null && temperature != null && pressure != null && stress != null',1)
    else:
        if name == 'PipeThicknessTab':
            anchor = '        CalculatorNumberField(stringResource(R.string.field_allowable_stress), allowableStressText) { allowableStressText = it }'
            block = block.replace(anchor,anchor+'\n        StressSourceHint(code, selectedMaterialName, temperatureText, allowableStressText) { value -> allowableStressText = value }',1)
    s = s[:a] + block + s[b:]

# Long explanations remain readable in dialogs at large font sizes.
s = s.replace('text = { Text(help) }, confirmButton', 'text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) { Text(help) } }, confirmButton')
p.write_text(s,encoding='utf-8')
