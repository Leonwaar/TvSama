const fs = require('node:fs/promises');
const path = require('node:path');
const core = require('../core/library.js');
class Store {
  constructor(directory) { this.file = path.join(directory, 'library-v2.json'); this.state = core.fresh(); this.queue = Promise.resolve(); }
  async load() { try { this.state = core.migrate(JSON.parse(await fs.readFile(this.file,'utf8'))); } catch(e) { if(e.code !== 'ENOENT') { await fs.copyFile(this.file,`${this.file}.corrupt-${Date.now()}`).catch(()=>{}); this.warning = 'Bibliothèque invalide conservée dans une copie de récupération.'; } } return this.state; }
  save(state) { const data=JSON.stringify(core.migrate(state),(key,value)=>key==='raw'?undefined:value);if(Buffer.byteLength(data)>8*1024*1024)throw Error('Bibliothèque trop volumineuse');
    this.state=JSON.parse(data);this.queue=this.queue.catch(()=>{}).then(async()=>{await fs.mkdir(path.dirname(this.file),{recursive:true});const temporary=`${this.file}.tmp`;await fs.writeFile(temporary,data,{mode:0o600});await fs.rename(temporary,this.file)});return this.queue;
  }
}
function importBackup(data) {
  if(data?.schema===2||Array.isArray(data))return core.migrate(data);
  if(data?.app!=='TvSama'||data.version!==1||!data.preferences)throw Error('Sauvegarde TvSama non reconnue');
  if(data.desktop)return core.migrate(data.desktop);
  const state=core.fresh(), p=data.preferences, lib=p.tvsama_library||{}, settings=p.tvsama_settings||{};
  const decode = (entry, fallback) => entry ? JSON.parse(entry.value) : fallback;
  state.items=decode(lib.favorites,[]).map(x=>({...x,favorite:true}));state.history=decode(lib.history,[]).map(e=>({...e,episodeId:e.episode,anime:e.anime}));
  for(const item of state.items) { const cached=lib[`episodes|${core.key(item)}`]||lib[`episodes|${core.key(item).replace(/\|\|/,'|null|')}`];if(cached)item.episodes=decode(cached,[]); }
  for(const [name,target] of Object.entries({language:'language',autoplay:'autoplay',subtitles:'subtitles',pause_dimming:'pauseDimming',density:'density',startup:'startup',oled:'oled',motion:'motion'})) {
    const entry=lib[name]||settings[name];if(entry)state.settings[target]=entry.value;
  }
  state.hidden=Object.fromEntries(Object.entries(decode(lib.hidden_resume_times,{})).map(([key,time])=>[key.replace('|null|','||'),time]));state.searches=decode(lib.searches,[]).map(x=>x.query);state.disabled=settings.disabled_providers?.value||[];
  state.settings.sleepMinutes=settings.sleep_minutes?.value||0;
  for(const line of (p.sources?.items?.value||'').split('\n')){const split=line.indexOf('|');if(split<1)continue;const title=line.slice(0,split),url=line.slice(split+1);let parsed;try{parsed=new URL(url)}catch{throw Error('Flux personnel Android invalide')}if(parsed.protocol!=='https:'||parsed.username||parsed.password)throw Error('Flux personnel Android non autorisé');state.items.push({id:url,url,title,provider:'Personnel',tag:'Film',episodes:[],favorite:false});}
  return core.migrate(state);
}
function exportBackup(input){const state=core.migrate(input),clean=JSON.parse(JSON.stringify(state,(key,value)=>['raw','pairingToken','paired','tmdbToken'].includes(key)?undefined:value));const entry=(type,value)=>({type,value}),json=value=>entry('string',JSON.stringify(value));const androidAnime=item=>({...item,episodes:(item.episodes||[]).map(e=>({...e,season:e.season||e.seasonNumber||1}))});const lib={favorites:json(state.items.filter(x=>x.favorite).map(androidAnime)),history:json(state.history.map(e=>({...e,anime:androidAnime(e.anime),episode:e.episodeId||''}))),hidden_resume_times:json(Object.fromEntries(Object.entries(state.hidden).map(([key,time])=>[key.replace(/\|\|/,'|null|'),time]))),searches:json(state.searches.map(query=>({query,updatedAt:Date.now()}))),language:entry('string',state.settings.language),autoplay:entry('boolean',state.settings.autoplay)};const settings={disabled_providers:entry('strings',state.disabled),sleep_minutes:entry('int',state.settings.sleepMinutes)};for(const [key,target]of Object.entries({subtitles:'subtitles',pause_dimming:'pauseDimming',density:'density',startup:'startup',oled:'oled',motion:'motion'}))settings[key]=entry(typeof state.settings[target]==='boolean'?'boolean':'string',state.settings[target]);return {app:'TvSama',version:1,preferences:{sources:{items:entry('string',state.items.filter(x=>x.provider==='Personnel').map(x=>`${x.title.replace(/[|\n\r]/g,' ')}|${x.url}`).join('\n'))},tvsama_library:lib,tvsama_settings:settings},desktop:clean};}
module.exports={Store,importBackup,exportBackup};
