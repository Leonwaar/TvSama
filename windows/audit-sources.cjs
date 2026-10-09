// Public samples; URL resolution is never proof of video decoding.
const fs=require('node:fs/promises'),path=require('node:path'),core=require('./core/library.js');
const {Network}=require('./services/network.cjs'),{Providers}=require('./services/legacy-providers.cjs'),{SourceMaintenance}=require('./services/maintenance.cjs');
async function audit(){
 const network=new Network(),store={state:core.fresh(),save:async()=>{}},providers=new Providers(network,store);
 const directory=await new SourceMaintenance(providers,network,store).refresh(),definitions=providers.definitions();
 const report={at:new Date().toISOString(),platform:process.platform,scope:'One catalogue, search, detail and resolution per source; no remote video decoded',directory,sources:[]};let cursor=0;
 async function worker(){while(cursor<definitions.length){const source=definitions[cursor++],state=structuredClone(store.state);state.disabled=definitions.filter(x=>x.name!==source.name).map(x=>x.name);const p=new Providers(network,{state}),row={name:source.name,catalogue:'non testé',recherche:'non testée',detail:'non testé',episodes:0,resolution:'non testée',decodage:'non testé'};
 try{const args={query:'',category:source.live?'TV en direct':'Tous',page:1},data=await p.catalog(args,AbortSignal.timeout(45000));row.count=data.items.length;row.catalogue=data.items.length?'OK':p.status[source.name]||'vide';if(data.items.length){
 try{const search=await p.catalog({...args,query:data.items[0].title},AbortSignal.timeout(30000));row.recherche=search.items.length?'OK ('+search.items.length+' résultats)':p.status[source.name]||'aucun résultat';}catch(e){row.recherche=e.message}
 const item=await p.details(data.items[0],AbortSignal.timeout(45000));row.detail='OK';row.episodes=item.episodes?.length||0;
 if(item.tag==='Film'||item.tag==='Direct'||row.episodes){try{const streams=await p.resolve(item,item.episodes?.[0],AbortSignal.timeout(45000));row.resolution=streams.length?'OK ('+streams.length+' URL, décodage non testé)':'aucune URL';}catch(e){row.resolution=e.message}}
 }}catch(e){if(row.catalogue==='non testé')row.catalogue=e.message;else row.detail=e.message}
 report.sources.push(row);console.log(row.name+': catalogue='+row.catalogue+'; recherche='+row.recherche+'; résolution='+row.resolution);
 }}
 await Promise.all([worker(),worker(),worker()]);report.sources.sort((a,b)=>a.name.localeCompare(b.name));
 await fs.mkdir(path.join(__dirname,'verification'),{recursive:true});await fs.writeFile(path.join(__dirname,'verification','sources-linux.json'),JSON.stringify(report,null,2));console.log('Audit terminé : '+report.sources.length+' sources.');
}
audit().catch(e=>{console.error(e);process.exitCode=1});
