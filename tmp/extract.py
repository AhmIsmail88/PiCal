import sys, re
from pypdf import PdfReader
src, dst = sys.argv[1], sys.argv[2]
r = PdfReader(src)
out = []
for i, page in enumerate(r.pages, 1):
    t = page.extract_text() or ""
    out.append(f"\n===== PAGE {i} =====\n{t}")
text = "".join(out)
open(dst, "w", encoding="utf-8").write(text)
print(f"{src} -> {dst}  pages={len(r.pages)} chars={len(text)}")
