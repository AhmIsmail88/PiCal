"""Read-only checks for canonical acceptance PDFs; render separately for layout QA."""
from pathlib import Path
import logging
import sys
import unicodedata
import re
from pypdf import PdfReader
import pdfplumber

logging.getLogger("pdfminer").setLevel(logging.ERROR)
root = Path(sys.argv[1])
files = [p for p in root.rglob("*.pdf") if not p.name.startswith("PiCal_")]
assert len(files) == 18, f"Expected 18 report fixtures, found {len(files)}"
texts = {}
for path in files:
    reader = PdfReader(path)
    text = "\n".join(p.extract_text() or "" for p in reader.pages)
    assert "PiCal" in text and "FlowTrack" not in text, path
    texts[path.name] = re.sub(r"\s+", "", unicodedata.normalize("NFKC", text))
    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            assert all(c["x0"] >= -0.5 and c["x1"] <= 596 and c["top"] >= 0 and c["bottom"] <= 843 for c in page.chars), path
    print(f"PASS {path.name}: {len(reader.pages)} pages; brand and page bounds")

assert '16.0"' in texts['project.pdf'] and '4joints' in texts['project.pdf']
assert '0.0"' in texts['project-empty.pdf']
assert '32.0"' in texts['welding.pdf'] and '8joints' in texts['welding.pdf']
assert '120.0"' in texts['employee.pdf'] and '50%' in texts['employee.pdf'] and '101.6mm' in texts['employee.pdf']
assert 'Notrecorded' in texts['employee-empty.pdf']
assert '49.4608bar' in texts['miter313.pdf']
assert 'QUALIFICATIONREQUIRED' in texts['miter311.pdf'] and '6.25mm' in texts['miter311.pdf']
assert '96.45kg' in texts['weight.pdf'] and '145.73kg' in texts['weight.pdf']
for code in ['b31_1_power', 'b31_3_process']:
    assert 'MEETStheinternal-pressure' in texts[f'{code}-pass.pdf']
    assert 'BELOWtheinternal-pressure' in texts[f'{code}-fail.pdf']
    assert 'NOTPROVIDED' in texts[f'{code}-missing.pdf']
print('PASS report values, empty periods, attendance and calculator verdicts')
