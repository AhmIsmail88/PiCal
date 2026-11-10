from pathlib import Path
import pdfplumber
files=[Path('output/pdf/finished-pipe-wall-example.pdf'),*Path('tmp/pdfs/finished-wall').glob('*.pdf')]
for path in files:
    with pdfplumber.open(path) as pdf:
        texts=[]
        for i,page in enumerate(pdf.pages):
            text=page.extract_text() or ''; texts.append(text)
            page.to_image(resolution=120).save(f'tmp/pdfs/finished-wall/{path.stem}-{i+1}.png')
            assert all(c['x0']>=0 and c['x1']<=596 and c['top']>=0 and c['bottom']<=843 for c in page.chars)
        combined='\n'.join(texts)
        if path.stem=='finished-pipe-wall-example':
            assert '3.5 mm' in texts[0] and 'MEETS the internal-pressure' in texts[0]
        if path.stem=='fail': assert 'BELOW the internal-pressure' in texts[0]
        if path.stem=='missing': assert 'NOT PROVIDED' in texts[0] and 'NOT EVALUATED' in texts[0]
        if path.stem=='long': assert 'BELOW the internal-pressure' in texts[0]
        assert '3.478654592' in combined and '4.478654592' in combined
        assert '06  LIMITS' in combined or '06 LIMITS' in combined
        print(path.name, 'pages=',len(pdf.pages),'text checks passed')
        Path(f'tmp/pdfs/finished-wall/{path.stem}.txt').write_text(combined,encoding='utf-8')
