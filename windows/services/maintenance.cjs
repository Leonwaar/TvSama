const core=require('../core/library.js');const {httpsURL}=require('./network.cjs');
const DAY=24*3600000,HOUR=3600000;
function waitFor(task,signal){if(!signal)return task;if(signal.aborted)return Promise.reject(signal.reason);return new Promise((resolve,reject)=>{const abort=()=>{signal.removeEventListener('abort',abort);reject(signal.reason)};signal.addEventListener('abort',abort,{once:true});task.then(value=>{signal.removeEventListener('abort',abort);resolve(value)},error=>{signal.removeEventListener('abort',abort);reject(error)});});}
class SourceMaintenance {
  constructor(providers,network,store,{now=Date.now,onChange=()=>{}}={}){this.providers=providers;this.network=network;this.store=store;this.now=now;this.onChange=onChange;this.pending=null;this.lastError='';}
  async refresh({force=false,manual=false}={}){
    if(this.pending)return this.pending;const memo=this.store.state.sourceMaintenance||{},now=this.now();
    if((!force&&memo.checkedAt&&now-memo.checkedAt<DAY)||(!manual&&memo.attemptedAt&&now-memo.attemptedAt<HOUR))return {changed:[],cached:true};
    this.store.state.sourceMaintenance={...memo,attemptedAt:now};
    const task=(async()=>{await this.store.save(this.store.state);try{const entries=await this.providers.directory(AbortSignal.timeout(45000)),changed=[];const histories={...(this.store.state.sourceMaintenance?.histories||{})};
      for(const source of this.providers.definitions()){if(source.kind==='m3u')continue;const entry=entries.find(e=>core.normalize(e.name)===core.normalize(source.name)&&e.status==='active');if(!entry)continue;let url;try{url=httpsURL(entry.url)}catch{continue;}
        // Anime-Ultime's v5 JSON contract differs from its legacy public portal.
        if(source.kind==='ultime'&&!url.hostname.startsWith('v5.'))continue;
        const address=`${url.origin}/`;if(new URL(source.base).origin===url.origin)continue;histories[source.name]=[...new Set([new URL(source.base).origin,...(histories[source.name]||[])])].slice(0,4);this.store.state.addresses[source.name]=address;changed.push(source.name);
      }
      this.store.state.sourceMaintenance={...this.store.state.sourceMaintenance,checkedAt:this.now(),histories,error:''};await this.store.save(this.store.state);this.lastError='';if(changed.length){this.network.cache.clear();this.onChange(changed)}return {changed,cached:false};
    }catch(error){this.lastError=error.message;this.store.state.sourceMaintenance={...this.store.state.sourceMaintenance,error:error.message};await this.store.save(this.store.state);return {changed:[],error:error.message};}})();
    this.pending=task;try{return await task}finally{if(this.pending===task)this.pending=null;}
  }
  translate(item){const source=this.providers.definitions().find(p=>p.name===item.provider);if(!source||source.kind==='m3u')return item;const origins=[...(this.store.state.sourceMaintenance?.histories?.[source.name]||[])];if(source.defaultBase)origins.push(new URL(source.defaultBase).origin);const rebase=raw=>{try{const url=new URL(raw);return origins.includes(url.origin)?new URL(url.pathname+url.search+url.hash,source.base).href:raw}catch{return raw}};return {...item,id:rebase(item.id),episodes:item.episodes?.map(e=>({...e,id:rebase(e.id)})),references:item.references?.map(r=>r.provider===item.provider?{...r,id:rebase(r.id)}:r)};}
  async details(item,signal){let translated=this.translate(item);try{return await this.providers.details(translated,signal)}catch(error){if(signal?.aborted)throw error;const repaired=await waitFor(this.refresh({force:true}),signal);if(signal?.aborted)throw signal.reason;if(!repaired.changed.includes(item.provider))throw error;translated=this.translate(item);return this.providers.details(translated,signal);}}
  async resolve(item,episode,signal,{refresh=false}={}){if(refresh)this.network.cache.clear();let translated=this.translate(item),target=episode;if(refresh&&!item.url){translated=await this.details(translated,signal);target=this.matchEpisode(translated,episode)}try{return await this.providers.resolve(translated,target,signal)}catch(error){if(signal?.aborted||item.url)throw error;await waitFor(this.refresh({force:true}),signal);if(signal?.aborted)throw signal.reason;this.network.cache.clear();translated=await this.details(this.translate(item),signal);target=this.matchEpisode(translated,episode);return this.providers.resolve(translated,target,signal);}}
  matchEpisode(item,episode){if(!episode)return undefined;return item.episodes?.find(e=>e.id===episode.id)||item.episodes?.find(e=>(e.season||e.seasonNumber)===(episode.season||episode.seasonNumber)&&e.number===episode.number&&(!episode.language||e.language===episode.language))||episode;}
}
module.exports={SourceMaintenance,DAY,HOUR};
