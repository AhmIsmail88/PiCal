from pathlib import Path
import re
from xml.sax.saxutils import escape
root=Path('app/src/main')
p=root/'java/com/ahmedismail/flowtrack/ui/screens/CalculatorsScreen.kt'
s=p.read_text(encoding='utf-8')
s=s.replace('import androidx.compose.foundation.background','import androidx.compose.material.icons.Icons\nimport androidx.compose.material.icons.outlined.HelpOutline\nimport androidx.compose.foundation.background',1)
s=s.replace('NumberField(', 'CalculatorNumberField(').replace('DropdownField(', 'CalculatorDropdownField(')
start=s.index('        Column(Modifier.padding(bottom = 10.dp)) {\n            OutlinedTextField(')
end=s.index('\n\n        CalculatorDropdownField(',start)
s=s[:start]+'''        CalculatorNumberField(stringResource(R.string.field_allowable_stress), allowableStressText) { allowableStressText = it }'''+s[end:]
s=s.replace('    fun applyMaterial(material: PipeMaterial) {','''    var measuredWallText by rememberSaveable(selectedMaterialName, customMaterialName, selectedNpsLabel, manualOdText, odUnit, selectedSchedule, manualWallText, manualWallUnit) { mutableStateOf("") }
    var measurementReference by rememberSaveable(selectedMaterialName, customMaterialName, selectedNpsLabel, manualOdText, odUnit, selectedSchedule, manualWallText, manualWallUnit) { mutableStateOf("") }

    fun applyMaterial(material: PipeMaterial) {''')
s=s.replace(' && tolerance != null && temperatureText', ' && temperatureText')
s=s.replace('''            ResultRow(stringResource(R.string.calculated_thickness_label),''','''            ResultRow(stringResource(R.string.finished_required_label), result?.let { "${formatThickness(it.minimumFinishedWallMm)} mm" } ?: "—", light = true)
            ResultRow(stringResource(R.string.calculated_thickness_label),''',1)
s=s.replace('"${formatNum(effectiveWall)} mm"','"${formatThickness(effectiveWall)} mm"')
anchor='''        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.calculation_report_details))'''
new='''        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.finished_section))
        CalculatorNumberField(stringResource(R.string.measured_wall_label), measuredWallText) { measuredWallText = it }
        Text(stringResource(R.string.measured_wall_help), style = MaterialTheme.typography.bodySmall, color = Ink2)
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
                Text(stringResource(if (finishedCheck.passes) R.string.finished_pass else R.string.finished_fail), color = if (finishedCheck.passes) androidx.compose.ui.graphics.Color(0xFF95E1BD) else androidx.compose.ui.graphics.Color(0xFFFFB4AB))
            }
        }
        if (measuredWallText.isNotBlank() && finishedCheck == null) Text(stringResource(R.string.measured_invalid), color = WarnBandText)
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.calculation_report_details))'''
assert anchor in s
s=s.replace(anchor,new,1)
s=s.replace('val canExport = result != null && requiredNominal != null && (scheduleWallMm == null || wallCheck != null)', 'val canExport = result != null && finishedCheck != null')
s=s.replace('millTolerancePercent = millToleranceText.toInputDoubleOrNull() ?: 0.0,','millTolerancePercent = tolerance,\n                        measuredMinimumWallMm = measuredWall,\n                        measurementReference = measurementReference.trim().ifBlank { "Not provided" },')
s=s.replace('''        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------
// Pipe Weight''','''        if (!canExport) Text(stringResource(R.string.finished_export_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        Spacer(Modifier.height(8.dp))
    }
}

// ---------------------------------------------------------------------
// Pipe Weight''',1)
s=s.replace('''        Text(label, style = MaterialTheme.typography.bodySmall, color = Ink2, modifier = Modifier.padding(bottom = 5.dp))
        OutlinedTextField''','''        CalculatorHelpLabel(label)
        OutlinedTextField''')
s += '''
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
                Text(selected?.let(display) ?: "—", modifier = Modifier.weight(1f))
                Text("▾")
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
        IconButton(onClick = { showHelp = true }) {
            Icon(Icons.Outlined.HelpOutline, contentDescription = stringResource(R.string.field_help_description, label), tint = Steel)
        }
    }
    if (showHelp) AlertDialog(onDismissRequest = { showHelp = false }, title = { Text(label) },
        text = { Text(help) }, confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.help_close)) } })
}

@Composable
private fun calculatorHelp(label: String): String {
    val pairs = listOf(
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
        R.string.field_pipe_length to R.string.help_length)
    for ((key, help) in pairs) if (label == stringResource(key)) return stringResource(help)
    return label
}
'''
p.write_text(s,encoding='utf-8')
texts={
'effective_wall_thickness_label':('Lower wall bound from mill tolerance (not measured)','الحد الأدنى حسب تفاوت المصنع (ليس قياسًا)'),
'minimum_required_thickness_label':('Required wall before machining, tm','السمك المطلوب قبل التشغيل وإزالة المعدن، tm'),
'required_nominal_thickness':('Minimum nominal pipe wall to order','الحد الأدنى للسمك الاسمي المطلوب للشراء'),
'wall_thickness_label':('Nominal pipe wall thickness','سمك جدار الماسورة الاسمي'),
'inside_diameter_label':('Nominal inside diameter','القطر الداخلي الاسمي'),
'finished_required_label':('Required remaining wall after fabrication','الحد الأدنى المطلوب للجدار المتبقي بعد التصنيع'),
'finished_section':('FINAL PIPE WALL - MEASURED AFTER FABRICATION','جدار الماسورة النهائي - القياس بعد التصنيع'),
'measured_wall_label':('Minimum measured finished wall (mm)','أقل سمك فعلي مقاس بعد التصنيع (مم)'),
'measured_wall_help':('Enter the minimum remaining metal measured on the finished straight pipe, in mm, including the thinnest groove/thread root after all machining. Use a validated inspection minimum accounting for measurement uncertainty; not an average, nominal schedule wall, coating or weld reinforcement. No mill tolerance or removed-metal allowance is deducted again. For a new pipe before service; this is not a remaining-life assessment.','أدخل أقل سمك معدن متبقٍ مقاس بالملليمتر على الماسورة المستقيمة بعد اكتمال التصنيع، بما فيه أرق موضع عند جذور القلاوظ أو المجاري بعد التشغيل. استخدم أقل قيمة معتمدة بالفحص مع مراعاة عدم يقين القياس، وليس المتوسط أو سمك Schedule أو الطلاء أو بروز اللحام. لا يُخصم تفاوت المصنع أو المعدن المزال مرة ثانية. هذا الفحص لماسورة جديدة قبل الخدمة، وليس لتقييم العمر المتبقي.'),
'measurement_reference_label':('Inspection / pipe reference (optional)','مرجع الفحص / رقم الماسورة (اختياري)'),
'measurement_not_provided':('No valid measured value','لا توجد قيمة قياس صالحة'),
'finished_margin_label':('Measured wall minus required wall','السمك المقاس ناقص السمك المطلوب'),
'finished_pass':('Measured wall meets the internal-pressure thickness requirement.','السمك المقاس يحقق متطلب السمك للضغط الداخلي.'),
'finished_fail':('Measured wall is BELOW the internal-pressure thickness requirement.','السمك المقاس أقل من متطلب السمك للضغط الداخلي.'),
'measured_invalid':('Complete the design inputs and enter a positive measured wall less than half the OD.','أكمل بيانات التصميم وأدخل سمكًا مقاسًا موجبًا أقل من نصف القطر الخارجي.'),
'finished_export_hint':('A finished-wall report needs valid design inputs and a measured minimum wall.','تقرير السمك النهائي يتطلب بيانات تصميم صالحة وأقل سمك مقاس فعليًا.'),
'field_help_description':('Explain: %1$s','شرح: %1$s'),
'help_close':('Understood','فهمت'),
'help_material':('Select the exact material grade and product specification. S, E and W must be verified for the design temperature and product.','اختر درجة الخامة ومواصفة المنتج بدقة. يجب مراجعة S وE وW حسب حرارة التصميم ونوع المنتج.'),
'help_material_name':('Enter the material specification, grade and product form shown on the material certificate.','اكتب مواصفة الخامة ودرجتها وشكل المنتج كما تظهر في شهادة الخامة.'),
'help_material_class':('This selection controls automatic Y only. Choose the applicable ferritic or austenitic steel class; it does not determine S.','هذا الاختيار يحدد Y التلقائي فقط. اختر فئة الصلب الفريتي أو الأوستنيتي المناسبة؛ لا يحدد الإجهاد S.'),
'help_yield':('Optional reference yield strength in MPa. It is not the allowable design stress S and is not used in this calculation.','إجهاد الخضوع بالميجاباسكال كمعلومة مرجعية اختيارية. ليس الإجهاد المسموح S ولا يدخل في هذه المعادلة.'),
'help_temperature':('Design metal temperature in degrees Celsius for the governing coincident pressure-temperature case. Changing it clears S, E and W for review.','درجة حرارة معدن الماسورة التصميمية بالمئوية لحالة الضغط والحرارة المتزامنين الحاكمة. تغييرها يمسح S وE وW لإعادة مراجعتها.'),
'help_nps':('Select nominal pipe size, not a measured diameter. The matching actual OD is loaded from B36.10-2022. Choose Custom for other dimensions.','اختر المقاس الاسمي NPS وليس قطرًا مقاسًا. يُستخدم القطر الخارجي المقابل من B36.10-2022. اختر مخصص للأبعاد الأخرى.'),
'help_od':('Enter the outside diameter in the selected unit, excluding coatings. Do not enter inside diameter or the NPS number.','أدخل القطر الخارجي بوحدة القياس المختارة دون الطلاء. لا تدخل القطر الداخلي أو رقم NPS.'),
'help_unit':('Select the unit of the number you enter: mm or inch. One inch equals 25.4 mm. Changing the unit reinterprets the entered number.','اختر وحدة الرقم الذي ستدخله: مم أو بوصة. البوصة تساوي 25.4 مم. تغيير الوحدة يعيد تفسير الرقم المدخل.'),
'help_pressure':('Internal design gauge pressure in bar(g), not absolute pressure, test pressure or MPa. Example: 20 bar = 2 MPa. Use the governing design case.','ضغط التصميم الداخلي المقاس بالنسبة للجو بوحدة bar(g)، وليس الضغط المطلق أو ضغط الاختبار أو قيمة بالميجاباسكال. مثال: 20 بار = 2 ميجاباسكال. استخدم حالة التصميم الحاكمة.'),
'help_corrosion':('Metal allowance in mm for expected corrosion over the design life. Enter the project value, or 0 only when justified.','سماحية فقد المعدن بالتآكل الكيميائي بالملليمتر خلال العمر التصميمي. أدخل قيمة المشروع، أو صفرًا إذا كان مبررًا.'),
'help_erosion':('Additional metal allowance in mm for erosion by flowing fluid or particles. Do not duplicate any erosion already included in corrosion allowance.','سماحية إضافية بالملليمتر للنحر بسبب المائع أو الجسيمات. لا تكرر سماحية نحر محسوبة بالفعل ضمن سماحية التآكل.'),
'help_e':('Dimensionless quality factor E from B31.3-2024 Table A-2 or A-3 and applicable notes for the product/weld examination. Valid range: greater than 0 up to 1.','معامل الجودة E دون وحدة من جدول A-2 أو A-3 في B31.3-2024 والملاحظات المناسبة للمنتج وفحص اللحام. أكبر من صفر وحتى 1.'),
'help_w':('Dimensionless weld strength reduction W at design temperature, per 302.3.5(e) / Table 302.3.5-1. Valid range: greater than 0 up to 1. Do not assume 1 without checking applicability.','معامل تخفيض مقاومة اللحام W دون وحدة عند حرارة التصميم وفق 302.3.5(e) وجدول 302.3.5-1. أكبر من صفر وحتى 1. لا تفترض 1 قبل مراجعة انطباقه.'),
'help_y':('Dimensionless Y from Table 304.1.1-1. Automatic interpolation uses the selected steel class and temperature. Editing overrides the table; use the reset button to restore it.','معامل Y دون وحدة من جدول 304.1.1-1. الاستيفاء التلقائي يستخدم فئة الصلب والحرارة المختارتين. التعديل اليدوي يتجاوز الجدول؛ استخدم زر العودة لقيم الجدول لاستعادتها.'),
'help_reference':('Record code edition, material table row/notes and product specification used for S, E, W and mill tolerance. Included in the report for traceability.','اكتب إصدار الكود وصف جدول المادة والملاحظات ومواصفة المنتج المستخدمة في S وE وW وتفاوت التصنيع، لتوثيقها في التقرير.'),
'help_schedule':('Select a nominal pipe wall from B36.10-2022. This is an ordering dimension, not the measured finished wall. The table is a limited subset, not all available schedules.','اختر سمكًا اسميًا للماسورة من B36.10-2022. هذا بُعد للشراء وليس السمك الفعلي المقاس. الجدول يضم مجموعة محدودة من الجداول المتاحة.'),
'help_nominal':('Enter nominal pipe wall in the selected unit for the ordering check. Enter the actual minimum measured after fabrication in its separate field.','أدخل سمك الماسورة الاسمي بوحدة القياس المختارة لفحص الشراء. أدخل أقل سمك فعلي بعد التصنيع في خانته المنفصلة.'),
'help_measurement_reference':('Record pipe/spool ID, inspection report, date and measurement method. The application stores your value and does not perform the physical measurement.','اكتب رقم الماسورة أو الإسبول ورقم تقرير الفحص وتاريخه وطريقة القياس. التطبيق يسجل قيمتك ولا يجري القياس الميداني.'),
'help_project':('Optional project name printed in the report; it does not change the calculation.','اسم المشروع لطباعته في التقرير، اختياري ولا يؤثر على الحساب.'),
'help_engineer':('Optional preparer name printed in the report; it is not an approval or inspection signature.','اسم مُعد التقرير، اختياري ولا يمثل توقيع اعتماد أو شهادة فحص.'),
'help_density':('Material density in kg/m³. Example: carbon steel approximately 7850. This is mass density, not specific gravity.','كثافة المادة بالكيلوجرام لكل متر مكعب؛ مثال تقريبي للصلب الكربوني 7850. ليست الكثافة النسبية.'),
'help_length':('Total pipe length in metres used to calculate mass. Example: 6 for a six-metre pipe.','إجمالي طول الماسورة بالمتر لحساب الوزن. مثال: 6 لماسورة طولها ستة أمتار.'),
'schedule_basis_hint':('Dimensions: ASME B36.10-2022 Table 2-1 (SI). Use custom entry for other dimensions; STD/XS and stainless S schedules are separate designations.','الأبعاد من جدول 2-1 وفق ASME B36.10-2022 بالنظام المتري. استخدم الإدخال المخصص للأبعاد الأخرى؛ STD/XS وجداول الستانلس S تسميات مستقلة.'),
'pipe_report_disclaimer':('Straight-pipe internal-pressure wall check, B31.3-2024, with user-supplied coefficients and measurements. Not overall code certification.','فحص سمك الماسورة المستقيمة للضغط الداخلي وفق B31.3-2024 بمعاملات وقياسات يدخلها المستخدم. ليس شهادة مطابقة التصميم الكامل.'),
}
for i,lang in enumerate(['values','values-ar']):
    p=root/'res'/lang/'strings.xml'; s=p.read_text(encoding='utf-8')
    for key,values in texts.items():
        line=f'    <string name="{key}">{escape(values[i])}</string>'
        pattern=rf'    <string name="{key}">.*?</string>'
        if re.search(pattern,s): s=re.sub(pattern,lambda _:line,s)
        else: s=s.replace('</resources>',line+'\n</resources>')
    p.write_text(s,encoding='utf-8')
