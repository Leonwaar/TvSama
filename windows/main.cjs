const { app, BrowserWindow, session, ipcMain, dialog, protocol, net, Notification, shell, screen } = require('electron');
const path = require('node:path');
const fs=require('node:fs/promises');const {pathToFileURL}=require('node:url');
const {exportBackup}=require('./services/store.cjs');
const {Updates}=require('./services/updates.cjs');
const {UA}=require('./services/network.cjs');
const {desktopFetch}=require('./services/electron-fetch.cjs');
const {dnsOptions}=require('./services/dns.cjs');
const {SourceMaintenance}=require('./services/maintenance.cjs');
const {Store,importBackup}=require('./services/store.cjs');const {Network}=require('./services/network.cjs');const {Providers}=require('./services/legacy-providers.cjs');const {Media}=require('./services/media.cjs');const {Remote,parsePair}=require('./services/remote.cjs');const {Live}=require('./services/live.cjs');const {Cast}=require('./services/cast.cjs');const core=require('./core/library.js');
protocol.registerSchemesAsPrivileged([{scheme:'tvsama-media',privileges:{standard:true,secure:true,supportFetchAPI:true,stream:true,corsEnabled:true}}]);
let services;
app.whenReady().then(async () => {
  app.setAppUserModelId('fr.nekotv.desktop');
  session.defaultSession.setUserAgent(UA);
  const store=new Store(app.getPath('userData'));await store.load();if(!['system','cloudflare','google'].includes(store.state.settings.dns))store.state.settings.dns='cloudflare';app.configureHostResolver(dnsOptions(store.state.settings.dns));const network=new Network((url,options)=>desktopFetch(net,url,options));const providers=new Providers(network,store);const media=new Media((url,options)=>desktopFetch(net,url,options));const cast=new Cast();let window;const remote=new Remote(store,command=>window?.webContents.send('sama:command',command));const live=new Live(network,store,title=>{if(Notification.isSupported())new Notification({title:'TvSama · Dans 10 minutes',body:title}).show()});services={store,remote,live,cast};
  protocol.handle('tvsama-media',request=>media.handle(request).catch(error=>{window?.webContents.send('sama:media-error',{token:new URL(request.url).hostname,message:error.message});return new Response(error.message,{status:502})}));
  const jobs=new Map();const job=async(id,fn)=>{jobs.get(id)?.abort();const controller=new AbortController();jobs.set(id,controller);const timeout=setTimeout(()=>controller.abort(Error('Délai dépassé : réessayez ou choisissez une autre source.')),id==='catalog'?45000:90000);try{return await fn(controller.signal)}finally{clearTimeout(timeout);if(jobs.get(id)===controller)jobs.delete(id)}};
  const updates=new Updates(network,(url,options)=>desktopFetch(net,url,options));let allowClose=false,closeTimer;
  let accessWindow;
  const maintenance=new SourceMaintenance(providers,network,store,{onChange:names=>window?.webContents.send('sama:sources-updated',names)});
  providers.publish=(items,args,signal)=>{if(!signal?.aborted&&items.length)window?.webContents.send('sama:catalog',{items,requestToken:args.requestToken,page:args.page})};
  if(store.state.pairingToken)remote.start().catch(error=>{store.warning=`Télécommande indisponible : ${error.message}`});
  const handlers={
    bootstrap:()=>({state:store.state,version:app.getVersion(),warning:store.warning}),save:state=>store.save({...state,pairingToken:store.state.pairingToken,paired:store.state.paired,addresses:store.state.addresses,disabled:store.state.disabled,reminders:store.state.reminders,sourceMaintenance:store.state.sourceMaintenance}),
    dns:async value=>{const options=dnsOptions(value);app.configureHostResolver(options);await session.defaultSession.clearHostResolverCache();network.cache.clear();store.state.settings.dns=value;await store.save(store.state);return true},
    scale:value=>{if(![.75,1,1.25,1.5,1.75,2].includes(Number(value)))throw Error('Échelle invalide');window.webContents.setZoomFactor(Number(value));return true},
    licenses:async()=>Promise.all(['NOTICE.txt','streamflix-Apache-2.0.txt','DEPENDENCIES.txt'].map(async name=>({name,text:await fs.readFile(path.join(__dirname,'licenses',name),'utf8')}))),
    closeReady:async()=>{await store.queue;clearTimeout(closeTimer);allowClose=true;window.close();return true},
    sourceAccess:name=>{const source=providers.definitions().find(p=>p.name===name&&p.kind!=='m3u');if(!source)throw Error('Source inconnue');accessWindow?.close();const area=screen.getDisplayMatching(window.getBounds()).workAreaSize;accessWindow=new BrowserWindow({parent:window,width:Math.min(1000,Math.floor(area.width*.95)),height:Math.min(760,Math.floor(area.height*.9)),title:`Accès · ${name}`,autoHideMenuBar:true,webPreferences:{nodeIntegration:false,contextIsolation:true,sandbox:true}});accessWindow.webContents.setWindowOpenHandler(()=>({action:'deny'}));accessWindow.webContents.on('will-navigate',(event,address)=>{try{if(new URL(address).protocol!=='https:')event.preventDefault()}catch{event.preventDefault()}});accessWindow.on('closed',()=>network.cache.clear());accessWindow.loadURL(source.base);return true},
    catalog:(args,id='catalog')=>job(id,signal=>providers.catalog(args,signal)),cancel:id=>{jobs.get(id)?.abort();return true},
    details:item=>job('details',async signal=>{const detailed=await maintenance.details(item,signal);if(store.state.settings.tmdbToken){try{return await handlers.metadata(detailed,signal)}catch(e){if(signal.aborted)throw e}}return detailed}),resolve:(item,episode,options)=>job('resolve',signal=>maintenance.resolve(item,episode,signal,options)),sources:()=>providers.list(),
    sourceToggle:async(name,enabled)=>{if(!providers.list().some(p=>p.name===name&&p.ported))throw Error('Adaptateur non porté');store.state.disabled=[...new Set(enabled?store.state.disabled.filter(x=>x!==name):[...store.state.disabled,name])];await store.save(store.state);network.cache.clear();return providers.list()},
    directory:async()=>{const result=await maintenance.refresh({force:true,manual:true});if(result.error)throw Error(result.error);return result},
    media:async source=>media.register(await media.prepare(source)),releaseMedia:token=>media.remove(token),
    segments:async(imdb,season,episode,isMovie)=>{if(!/^tt\d{7,10}$/.test(imdb||''))return null;const query=new URLSearchParams({imdb_id:imdb,...(isMovie?{is_movie:'true'}:{season,episode})});try{const data=await network.request(`https://api.introdb.app/segments?${query}`,{json:true,ttl:3600000});return core.parseSegments(data,imdb,season,episode,isMovie)}catch{return null}},
    live:()=>job('live',signal=>live.events(signal)),reminder:event=>live.schedule(event),
    backupExport:async()=>{const result=await dialog.showSaveDialog(window,{defaultPath:'TvSama-Windows-sauvegarde.json',filters:[{name:'JSON',extensions:['json']}]});if(result.canceled)return false;await store.queue;await fs.writeFile(result.filePath,JSON.stringify(exportBackup(store.state),null,2),'utf8');return true},
    backupImport:async()=>{const result=await dialog.showOpenDialog(window,{properties:['openFile'],filters:[{name:'JSON',extensions:['json']}]});if(result.canceled)return null;const file=result.filePaths[0];if((await fs.stat(file)).size>4*1024*1024)throw Error('Sauvegarde trop volumineuse');const incoming=importBackup(JSON.parse(await fs.readFile(file,'utf8')));const choice=await dialog.showMessageBox(window,{type:'question',message:'Remplacer la bibliothèque locale par cette sauvegarde ?',buttons:['Annuler','Restaurer'],defaultId:0,cancelId:0});if(choice.response!==1)return null;incoming.pairingToken=store.state.pairingToken;incoming.paired=store.state.paired;incoming.settings.tmdbToken=store.state.settings.tmdbToken;await store.save(incoming);const dns=['system','cloudflare','google'].includes(store.state.settings.dns)?store.state.settings.dns:'cloudflare';await handlers.dns(dns);window.webContents.setZoomFactor([.75,1,1.25,1.5,1.75,2].includes(store.state.settings.viewScale)?store.state.settings.viewScale:1);return store.state},
    pairing:async()=>{await remote.start();const url=remote.url();return {url,qr:await require('qrcode').toDataURL(url)}},
    pair:async raw=>{const target=parsePair(raw);await remote.exchange(target,{action:'pair'});store.state.paired=target;await store.save(store.state);return true},
    remote:async command=>{if(!store.state.paired)throw Error('Aucun appareil associé');return remote.exchange(store.state.paired,command)},
    snapshot:state=>{remote.playback=state;return true},castDiscover:()=>cast.discover(),castLoad:(host,source,position)=>cast.connect(host,source,position),castCommand:(action,position)=>cast.command(action,position),castStop:()=>cast.closeClient(),
    checkUpdate:()=>updates.check(),
    downloadUpdate:async()=>{const info=await updates.check();const chosen=await dialog.showSaveDialog(window,{defaultPath:info.name||'TvSama-Windows-x64.exe',filters:[{name:'Application Windows',extensions:['exe']}]});if(chosen.canceled)return null;return updates.download(chosen.filePath)},
    openUpdate:async raw=>{const u=new URL(raw);if(u.protocol!=='https:'||u.hostname!=='github.com'||!u.pathname.startsWith('/Leonwaar/TvSama/releases/'))throw Error('Lien de mise à jour invalide');await shell.openExternal(u.href);return true},
    metadata:async(item,signal)=>{const token=store.state.settings.tmdbToken;if(!token)return item;const kind=item.tag==='Film'?'movie':'tv';const query=encodeURIComponent(item.title);const data=await network.request(`https://api.themoviedb.org/3/search/${kind}?query=${query}&language=fr-FR`,{signal,json:true,headers:{Authorization:`Bearer ${token}`}});const first=data.results?.find(x=>core.normalize(x.title||x.name)===core.normalize(item.title)&&(!item.year||String(x.release_date||x.first_air_date).startsWith(String(item.year))));return first?{...item,description:first.overview||item.description,poster:first.poster_path?`https://image.tmdb.org/t/p/w500${first.poster_path}`:item.poster,tmdbId:first.id}:item},
  };
  for(const [name,handler]of Object.entries(handlers))ipcMain.handle(`sama:${name}`,(event,...args)=>{if(event.sender!==window?.webContents||event.senderFrame?.url!==pathToFileURL(path.join(__dirname,'index.html')).href)throw Error('Émetteur IPC non autorisé');return handler(...args)});
  session.defaultSession.setPermissionRequestHandler((web, permission, callback) => callback(web===window?.webContents&&permission==='fullscreen'));
  const area=screen.getPrimaryDisplay().workAreaSize;
  window = new BrowserWindow({ width: Math.min(1280,Math.floor(area.width*.95)), height: Math.min(820,Math.floor(area.height*.9)), minWidth: Math.min(480,area.width), minHeight: Math.min(320,area.height),
    backgroundColor: '#141414', autoHideMenuBar: true,
    webPreferences: { preload:path.join(__dirname,'preload.cjs'),nodeIntegration: false, contextIsolation: true, sandbox: true } });
  window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  window.webContents.on('will-navigate', event => event.preventDefault());
  window.webContents.setZoomFactor([.75,1,1.25,1.5,1.75,2].includes(store.state.settings.viewScale)?store.state.settings.viewScale:1);
  window.loadFile(path.join(__dirname, 'index.html'));
  maintenance.refresh().catch(()=>{});const upkeep=setInterval(()=>maintenance.refresh().catch(()=>{}),1800000);upkeep.unref();app.once('before-quit',()=>clearInterval(upkeep));
  window.on('close',event=>{if(!allowClose&&!window.webContents.isDestroyed()&&!window.webContents.isCrashed()){event.preventDefault();if(!closeTimer){closeTimer=setTimeout(()=>{allowClose=true;window.close()},5000);window.webContents.send('sama:closing')}}});
  window.on('closed',()=>{clearTimeout(closeTimer);accessWindow?.close();for(const controller of jobs.values())controller.abort();for(const token of media.sessions.keys())media.remove(token)});
  window.webContents.on('before-input-event',(event,input)=>{if(input.type==='keyDown'&&input.key==='F11'){window.setFullScreen(!window.isFullScreen());event.preventDefault()}});
});
app.on('window-all-closed', () => app.quit());
app.on('before-quit',()=>{services?.live.close();services?.cast.close();services?.remote.close();});
