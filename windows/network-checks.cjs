const assert=require('node:assert/strict');
module.exports=async function networkChecks(page,report,port,setOffline){
 const url=`https://127.0.0.1:${port}/headers.mp4`,item={id:'headers-test',title:'En-têtes vidéo',provider:'Personnel',tag:'Film',url,episodes:[]};
 const source={url,name:'En-têtes requis',headers:{Referer:'https://fixture.example/',Origin:'https://fixture.example'}};
 await page.evaluate(({item,source})=>start(item,undefined,[source]),{item,source});
 await page.waitForFunction(()=>document.querySelector('video')?.currentTime>1,undefined,{timeout:15000});
 assert.ok(await page.locator('video').evaluate(v=>v.videoWidth>0));report.tests.push({label:'En-têtes Referer/Origin transmis au serveur vidéo réel'});
 const plain={...source,url:`https://127.0.0.1:${port}/plain.m3u8`,format:'hls'};await page.evaluate(({item,source})=>start(item,undefined,[source]),{item:{...item,url:plain.url},source:plain});await page.waitForFunction(()=>document.querySelector('video')?.currentTime>1,undefined,{timeout:15000});report.tests.push({label:'HLS servi en text/plain avec Origin et Referer'});
 const redirected={...source,url:`https://127.0.0.1:${port}/redirect.mp4`,headers:{Authorization:'fixture-only'}};
 await page.evaluate(({item,source})=>start(item,undefined,[source]),{item:{...item,url:redirected.url},source:redirected});
 await page.waitForFunction(()=>document.querySelector('video')?.currentTime>1,undefined,{timeout:15000});report.tests.push({label:'Redirection vidéo réelle, en-tête privé retiré au changement d’origine'});
 const recover={...item,url:`https://127.0.0.1:${port}/recovery.mp4`};setOffline(true);
 await page.evaluate(item=>start(item),recover);
 await page.getByRole('button',{name:'Renouveler les serveurs',exact:true}).waitFor({state:'visible'});
 setOffline(false);await page.getByRole('button',{name:'Renouveler les serveurs',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('video')?.currentTime>1,undefined,{timeout:15000});report.tests.push({label:'Erreur réseau HTTP 503 puis reprise effective sans redémarrer l’application'});
};
