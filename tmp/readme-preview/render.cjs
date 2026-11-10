const fs=require('fs');
const {pathToFileURL}=require('url');
const pkg='C:/Users/PC/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/';
const {chromium}=require(pkg+'playwright');
(async()=>{
 const {marked}=await import(pathToFileURL(pkg+'marked/lib/marked.esm.js').href);
 const root=process.cwd();
 const css=`body{font-family:Segoe UI,Tahoma,Arial,sans-serif;color:#223344;background:#fff;max-width:980px;margin:40px auto;padding:0 28px;line-height:1.65}img{max-width:100%;height:auto}h1,h2,h3{color:#14263d;line-height:1.3}h1{font-size:38px}h2{font-size:26px;border-bottom:1px solid #dce4e9;padding-bottom:10px;margin-top:42px}p{font-size:16px}table{border-collapse:collapse;width:100%;margin:20px 0}td,th{border:1px solid #dce4e9;padding:12px;font-size:14px;vertical-align:top}th{background:#f2f7fa;text-align:start}td img{display:block;margin:auto}pre{padding:18px;background:#f5f7f9;border-radius:8px;overflow:auto;direction:ltr;text-align:left;font-size:13px}code{font-family:Consolas,monospace}a{color:#126b80}details{margin:20px 0}summary{color:#126b80;cursor:pointer}body[dir=rtl]{text-align:right}body[dir=rtl] table{direction:rtl}`;
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const page=await browser.newPage({viewport:{width:1120,height:1000}});
 await page.route(/^https?:\/\//,route=>route.abort());
 for(const [file,key,dir] of [['README.md','en','ltr'],['README.ar.md','ar','rtl']]){
  let content=marked.parse(fs.readFileSync(file,'utf8'));
  const out='tmp/readme-preview/'+key+'.html';
  fs.writeFileSync(out,`<!doctype html><html lang="${key}"><head><meta charset="utf-8"><base href="${pathToFileURL(root+'/').href}"><style>${css}</style></head><body dir="${dir}">${content}</body></html>`);
  await page.goto(pathToFileURL(root+'/'+out).href);
  await page.evaluate(()=>document.fonts.ready);
  const bad=await page.locator('img').evaluateAll(items=>items.filter(x=>!x.complete||x.naturalWidth===0).map(x=>x.src));
  if(bad.length)throw new Error('Missing images: '+bad.join(','));
  await page.screenshot({path:'tmp/readme-preview/'+key+'-full.png',fullPage:true});
  await page.screenshot({path:'tmp/readme-preview/'+key+'-top.png'});
  const screens=page.locator('h2').filter({hasText:key==='en'?'Screenshots':'صور التطبيق'});await screens.scrollIntoViewIfNeeded();
  await page.screenshot({path:'tmp/readme-preview/'+key+'-gallery.png'});
  const architecture=page.locator('h2').filter({hasText:key==='en'?'Architecture':'معمارية التطبيق'});await architecture.scrollIntoViewIfNeeded();
  await page.screenshot({path:'tmp/readme-preview/'+key+'-architecture.png'});
  console.log(file+' rendered; every image loaded');
 }
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});