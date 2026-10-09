// Isolated profile, real Electron network, real renderer and media decoding.
module.exports=async function liveChecks(page,report){
 report.scope='Public source samples through Electron; decoded means video dimensions and advancing time';
 await page.evaluate(async()=>{await window.desktop.cancel('catalog');const sources=await window.desktop.sources();for(const source of sources.filter(x=>x.ported))await window.desktop.sourceToggle(source.name,false)});
 report.directory=await page.evaluate(()=>window.desktop.directory()).catch(e=>({error:e.message}));
 const sources=await page.evaluate(()=>window.desktop.sources());
 for(const source of sources.filter(s=>s.ported)){
  const row={name:source.name,catalogue:'non testé',detail:'non testé',resolution:'non testée',decodage:'non testé'};report.tests.push(row);
  try{
   await page.evaluate(name=>window.desktop.sourceToggle(name,true),source.name);
   const data=await page.evaluate(live=>window.desktop.catalog({query:'',category:live?'TV en direct':'Tous',page:1}),!!source.live);
   row.count=data.items.length;row.catalogue=data.items.length?'OK':data.sources.find(s=>s.name===source.name)?.status||'Vide';
   if(!data.items.length)continue;
   const item=await page.evaluate(item=>window.desktop.details(item),data.items[0]);row.detail='OK';row.episodes=item.episodes?.length||0;
   if(item.tag!=='Film'&&item.tag!=='Direct'&&!row.episodes)continue;
   const episode=item.episodes?.[0],streams=await page.evaluate(({item,episode})=>window.desktop.resolve(item,episode),{item,episode});
   row.resolution=streams.length+' URL';if(!streams.length)continue;
   await page.evaluate(({item,episode,streams})=>start(item,episode,streams),{item,episode,streams});
   try{await page.waitForFunction(()=>{const v=document.querySelector('video');return v&&v.videoWidth>0&&v.currentTime>2},undefined,{timeout:20000});row.decodage='OK';row.video=await page.locator('video').evaluate(v=>({width:v.videoWidth,height:v.videoHeight,time:v.currentTime,duration:Number.isFinite(v.duration)?v.duration:null}));}
   catch{row.decodage=await page.evaluate(()=>document.querySelector('.feedback')?.textContent||'Aucune image décodée dans le délai de 20 s');}
  }catch(error){const message=error.message.replace(/^.*Error invoking remote method[^:]*: Error: /,'');if(row.catalogue==='non testé')row.catalogue=message;else if(row.detail==='non testé')row.detail=message;else row.resolution=message;}
  finally{await page.evaluate(async name=>{await navigate('Sources');await window.desktop.sourceToggle(name,false)},source.name);console.log(JSON.stringify(row));}
 }
};
