// A dead first host must not prevent trying the remaining published servers.
async function resolveServers(entries,extract,signal,{concurrency=3,budget=25000}={}){
 if(signal?.aborted)throw signal.reason;
 const deadline=AbortSignal.timeout(budget),combined=signal?AbortSignal.any([signal,deadline]):deadline;
 const results=new Array(Math.min(entries.length,16)),failures=[];let cursor=0;
 const run=entry=>new Promise((resolve,reject)=>{const abort=()=>reject(combined.reason);combined.addEventListener('abort',abort,{once:true});Promise.resolve().then(()=>{combined.throwIfAborted();return extract(entry,combined)}).then(resolve,reject).finally(()=>combined.removeEventListener('abort',abort));});
 async function worker(){while(cursor<results.length&&!combined.aborted){const index=cursor++,entry=entries[index];try{results[index]=await run(entry);}catch(error){failures.push({name:entry.name||'Serveur',message:error.message});}}}
 await Promise.all(Array.from({length:Math.min(concurrency,results.length)},worker));
 if(signal?.aborted)throw signal.reason;
 const videos=[...new Map(results.flat().filter(v=>v?.url).map(v=>[v.url,v])).values()];
 if(videos.length)return videos;
 const causes=[...new Set(failures.map(f=>`${f.name} : ${f.message}`))].slice(0,4).join(' · ');
 throw Error(combined.aborted?'Délai des serveurs dépassé. Réessayez ou choisissez une autre source.':causes?`Lecteurs indisponibles — ${causes}`:'Aucun lecteur publié pour cet épisode.');
}
module.exports={resolveServers};
