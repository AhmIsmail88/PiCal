from pathlib import Path
p = Path('app/src/main/java/com/ahmedismail/flowtrack/util/PipeThicknessCalculator.kt')
s = p.read_text(encoding='utf-8')
start = s.index('        require(listOf', s.index('    fun calculate('))
end = s.index('        val pMPa =', start)
checks = s[start:end]
s = s[:start] + '        validateInputs(input)\n' + s[end:]
s = s.replace('        calculate(input) // Shared input validation and equation-scope limits.', '        validateInputs(input)')
s = s.replace('        if (t >= input.outsideDiameterMm / 6.0) throw ThicknessScopeException()',
              '        if (t >= input.outsideDiameterMm / 6.0 || pMpa / (input.allowableStressMPa * input.qualityFactorE) > 0.385) throw ThicknessScopeException()\n'
              '        require(t + allowances < input.outsideDiameterMm / 2.0) { "Required thickness leaves no bore." }')
anchor = '    fun requiredNominalThicknessMm('
s = s.replace(anchor, '    private fun validateInputs(input: PipeThicknessInput) {\n' + checks + '    }\n\n' + anchor, 1)
p.write_text(s, encoding='utf-8')
