"""Validate local README links, screenshot assets, headings and fenced blocks."""
from pathlib import Path
import re
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
failures = []
for filename in ("README.md", "README.ar.md"):
    path = ROOT / filename
    text = path.read_text(encoding="utf-8")
    if text.count("```") % 2:
        failures.append(f"{filename}: unclosed fenced block")
    if text.count("<details>") != text.count("</details>"):
        failures.append(f"{filename}: unclosed details section")
    targets = re.findall(r'\]\(([^)]+)\)', text) + re.findall(r'<img[^>]+src="([^"]+)"', text)
    for target in targets:
        if target.startswith(("https://", "http://", "#")):
            continue
        local = unquote(target.split("#", 1)[0])
        if not (path.parent / local).exists():
            failures.append(f"{filename}: missing {local}")
    headings = re.findall(r'^#{1,6} (.+)$', text, re.M)
    slugs = {re.sub(r'[^\w\- ]', '', h.lower()).replace(' ', '-') for h in headings}
    for fragment in re.findall(r'\]\(#([^\)]+)\)', text):
        if fragment not in slugs:
            failures.append(f"{filename}: missing anchor {fragment}")
    print(f"{filename}: {len(targets)} links/images; {len(headings)} headings checked")

if failures:
    raise SystemExit("\n".join(failures))
print("README asset and structure checks passed")
