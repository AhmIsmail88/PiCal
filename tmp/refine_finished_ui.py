from pathlib import Path
import re
p=Path('app/src/main/java/com/ahmedismail/flowtrack/ui/screens/CalculatorsScreen.kt')
s=p.read_text(encoding='utf-8')
start=s.index('        Spacer(Modifier.height(16.dp))\n        SectionLabel(stringResource(R.string.finished_section))')
end=s.index('        Spacer(Modifier.height(16.dp))\n        SectionLabel(stringResource(R.string.calculation_report_details))',start)
block=s[start:end]
s=s[:start]+s[end:]
pos=s.index('        Spacer(Modifier.height(10.dp))\n        SectionLabel(stringResource(R.string.section_outputs))')
s=s[:pos]+block+'\n'+s[pos:]
for key in ['stress_input_hint','mechanical_allowance_hint','mill_tolerance_hint','factors_input_hint']:
    s=re.sub(r'        Text\(stringResource\(R.string.'+key+r'\), style = MaterialTheme.typography.bodySmall, color = Ink2\)\n','',s,count=1)
s=s.replace('Text(stringResource(R.string.measured_wall_help), style', 'Text(stringResource(R.string.measurement_basis_hint), style')
line='        CalculatorNumberField(stringResource(R.string.field_mill_tolerance), millToleranceText) { millToleranceText = it }\n'
assert line in s
s=s.replace(line,'',1)
anchor='        SectionLabel(stringResource(R.string.section_schedule_check))\n'
s=s.replace(anchor,anchor+line,1)
p.write_text(s,encoding='utf-8')
for lang,label,hint in [
('values','OPTIONAL NOMINAL PIPE ORDERING CHECK','Enter the inspection minimum after all machining, including groove roots. The report uses this measured value. Tap ? for the measurement basis.'),
('values-ar','فحص اختياري لسمك الماسورة الاسمي عند الشراء','أدخل أقل سمك معتمد بالفحص بعد كل عمليات التشغيل، بما فيها جذور المجاري. التقرير يستخدم هذا القياس. اضغط ؟ لتفاصيل أساس القياس.')]:
    p=Path('app/src/main/res')/lang/'strings.xml'; s=p.read_text(encoding='utf-8')
    s=re.sub(r'<string name="section_schedule_check">.*?</string>',f'<string name="section_schedule_check">{label}</string>',s)
    s=s.replace('</resources>',f'    <string name="measurement_basis_hint">{hint}</string>\n</resources>')
    p.write_text(s,encoding='utf-8')
