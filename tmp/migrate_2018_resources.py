from pathlib import Path
import re

common = {
    'miter_small_offset_note': 'B31.3-2018: offsets of 3° or less do not require miter design. Check the applicable straight-pipe and joint rules; no miter pressure verdict is issued here.',
    'code_b313_process': 'ASME B31.3-2018 — Process Piping',
    'code_b311_power': 'ASME B31.1-2022 — Power Piping',
    'b311_verify_note': 'B31.1-2022, 104.1.2(a), Eq. (7), verified against the supplied p.27: tm = P Do / [2(SEW + Py)] + A. Enter S before E/F; do not apply the quality factor twice to a combined SE/SF table value.',
    'b311_invalid': 'Use P ≥ 0, Do and S &gt; 0, E and W in (0,1], each allowance ≥ 0, and a valid temperature/y for the selected group.',
    'y_auto_hint': 'Automatic Y: B31.3-2018 Table 304.1.1, SI temperature points. Full interpolation precision is used.',
    'b311_y_auto_hint': 'Automatic y: B31.1-2022 Table 104.1.2-1. The calculated result applies note (b) when required.',
    'y_above_table_warning': 'B31.3-2018 Table 304.1.1 has a 677°C and above column. Steel Y remains 0.7; this does not establish material temperature suitability. Confirm S and W at the design temperature.',
    'help_e': 'Quality factor E: B31.3-2018 Table A-1A/A-1B, or the applicable B31.1-2022 product-form quality factor. Enter allowable stress S before E/F; do not multiply an already combined SE/SF value by E again.',
    'miter_scope_hint': 'B31.3-2018 304.2.3: multiple miters use (4a)/(4b); a single joint uses (4a) up to 22.5° and (4c) above it. Verify radius (5) and wall extension M. B31.1-2022 uses the 10/100 psi paths and qualification under 104.7. θ is half the deflection per joint.',
    'miter_closely_spaced': 'Multiple miter joints',
    'miter_widely_spaced': 'Single miter joint',
    'miter_spacing_class_label': 'Miter arrangement',
    'miter_equation_multiple': 'Equations 4a and 4b — multiple miters',
    'miter_equation_single_low': 'Equation 4a — single miter, θ ≤ 22.5°',
    'miter_equation_single_high': 'Equation 4c — single miter, θ &gt; 22.5°',
    'miter_outside_scope': 'No pressure/thickness verdict: multiple-miter θ must not exceed 22.5°, and R1 must meet B31.3-2018 Eq. (5).',
    'miter_invalid': 'B31.3 needs valid D, T, c, S, E, W, P, angle, joint count and effective radius R1. B31.1 needs valid pressure, angle, joint count, T, B and declared conditions.',
    'miter_segments_note': 'Segment thickness under B31.1-2022 104.2.3(c)(3) can be calculated below; satisfying it does not replace qualification under 104.7.',
    'field_miter_radius': 'Effective bend radius R1 / R (mm)',
    'miter_minimum_radius': 'Minimum R1 — Eq. (5)',
    'miter_extension': 'Minimum wall extension M — 304.2.3(c)',
    'miter_segment_section': 'B31.1 segment thickness — 104.2.3(c)(3)',
    'miter_spacing_basis': 'Declare close/wide spacing and r/R from actual geometry using ASME B31J. The app does not infer this classification.',
    'b311_miter_close': 'Closely spaced',
    'b311_miter_wide': 'Widely spaced',
    'miter_segment_tm': 'Straight-pipe minimum tm — 104.1 (mm)',
    'miter_segment_r': 'Pipe mean radius r — B31J (mm)',
    'miter_segment_ts': 'Required segment thickness ts (mm)',
    'miter_segment_invalid': 'Use positive tm and r, 0 &lt; θ &lt; 90°, and R &gt; r for close spacing.',
}
arabic = {
    'miter_small_offset_note': 'B31.3-2018: الانحراف عند الوصلة حتى 3° لا يحتاج تصميم مايل. راجع قواعد الماسورة المستقيمة والوصلة المنطبقة؛ لا يصدر هنا حكم لضغط المايل.',
    'code_b313_process': 'ASME B31.3-2018 — الأنابيب الصناعية',
    'code_b311_power': 'ASME B31.1-2022 — أنابيب القدرة',
    'b311_verify_note': 'B31.1-2022 الفقرة 104.1.2(a)، المعادلة (7)، مؤكدة من الصفحة 27 المرفقة: tm = P Do / [2(SEW + Py)] + A. أدخل S قبل E/F؛ لا تضرب الإجهاد المدمج SE/SF في معامل الجودة مرة ثانية.',
    'b311_invalid': 'أدخل P ≥ 0 وDo وS &gt; 0 وE وW بين صفر و1، وكل سماحية ≥ 0، وحرارة ومعامل y صالحين للمجموعة المختارة.',
    'y_auto_hint': 'Y تلقائي من جدول 304.1.1 بإصدار B31.3-2018، باستخدام درجات الحرارة المئوية والاستيفاء بالدقة الكاملة.',
    'b311_y_auto_hint': 'y تلقائي من جدول 104.1.2-1 بإصدار B31.1-2022. النتيجة المحسوبة تطبق الملاحظة (b) عند انطباقها.',
    'y_above_table_warning': 'جدول 304.1.1 بإصدار B31.3-2018 يحتوي عمود 677°م وما فوق. تبقى Y للفولاذ 0.7؛ وهذا لا يثبت صلاحية المادة للحرارة. تحقق من S وW عند حرارة التصميم.',
    'help_e': 'معامل الجودة E من جدولي A-1A/A-1B في B31.3-2018، أو معامل شكل المنتج المنطبق في B31.1-2022. أدخل الإجهاد S قبل E/F؛ لا تضرب قيمة SE/SF المدمجة في E مرة ثانية.',
    'miter_scope_hint': 'B31.3-2018 الفقرة 304.2.3: المتعدد يستخدم (4a)/(4b)، والمفرد يستخدم (4a) حتى 22.5° و(4c) فوقها. تحقق من نصف القطر (5) وامتداد السمك M. يستخدم B31.1-2022 مساري 10/100 psi والتأهيل وفق 104.7. θ نصف الانحراف عند الوصلة.',
    'miter_closely_spaced': 'وصلات مايلة متعددة',
    'miter_widely_spaced': 'وصلة مايلة مفردة',
    'miter_spacing_class_label': 'ترتيب الوصلات',
    'miter_equation_multiple': 'المعادلتان 4a و4b — وصلات متعددة',
    'miter_equation_single_low': 'المعادلة 4a — مفرد، θ ≤ 22.5°',
    'miter_equation_single_high': 'المعادلة 4c — مفرد، θ &gt; 22.5°',
    'miter_outside_scope': 'لا يتوفر حكم للضغط أو السمك: زاوية θ للمايل المتعدد لا تتجاوز 22.5°، ويجب أن يحقق R1 المعادلة (5) في B31.3-2018.',
    'miter_invalid': 'يحتاج B31.3 قيمًا صحيحة لـ D وT وc وS وE وW والضغط والزاوية وعدد الوصلات ونصف القطر R1. يحتاج B31.1 ضغطًا وزاوية وعدد وصلات وT وB صحيحة وشروطًا معلنة.',
    'miter_segments_note': 'يمكن حساب سماكة القطعة وفق B31.1-2022 الفقرة 104.2.3(c)(3) أدناه؛ تحقيقها لا يعوض التأهيل وفق 104.7.',
    'field_miter_radius': 'نصف قطر الانحناء الفعّال R1 / R (مم)',
    'miter_minimum_radius': 'أقل R1 — المعادلة (5)',
    'miter_extension': 'أقل امتداد للسمك M — 304.2.3(c)',
    'miter_segment_section': 'سماكة قطعة B31.1 — 104.2.3(c)(3)',
    'miter_spacing_basis': 'حدد التباعد المتقارب أو المتباعد وr/R من الهندسة الفعلية وفق ASME B31J. التطبيق لا يستنتج هذا التصنيف.',
    'b311_miter_close': 'متقاربة',
    'b311_miter_wide': 'متباعدة',
    'miter_segment_tm': 'أقل سمك للماسورة المستقيمة tm — 104.1 (مم)',
    'miter_segment_r': 'متوسط نصف قطر الماسورة r — B31J (مم)',
    'miter_segment_ts': 'سمك القطعة المطلوب ts (مم)',
    'miter_segment_invalid': 'أدخل tm وr موجبين، و0 &lt; θ &lt; 90°، وR &gt; r للقطع المتقاربة.',
}
for folder, mapping in [('values', common), ('values-ar', arabic)]:
    p = Path('app/src/main/res') / folder / 'strings.xml'
    s = p.read_text(encoding='utf-8').replace('304.1.1-1', '304.1.1')
    for key, value in mapping.items():
        pattern = r'<string name="' + re.escape(key) + r'">.*?</string>'
        replacement = f'<string name="{key}">{value}</string>'
        if re.search(pattern, s):
            s = re.sub(pattern, lambda _: replacement, s, count=1)
        else:
            s = s.replace('</resources>', '    ' + replacement + '\n</resources>')
    for key in ['field_b311_x', 'b311_report_note']:
        s = re.sub(r'    <string name="'+key+r'">.*?</string>\n', '', s)
    p.write_text(s, encoding='utf-8')
for p in Path('app/src').rglob('*.kt'):
    s = p.read_text(encoding='utf-8')
    if '304.1.1-1' in s:
        p.write_text(s.replace('304.1.1-1', '304.1.1'), encoding='utf-8')
