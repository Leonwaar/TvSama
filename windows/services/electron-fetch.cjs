const {Readable}=require('node:stream');
const {httpsURL}=require('./network.cjs');
// Backend HTTP client: keep renderer security enabled. Electron's fetch manual
// redirect mode cancels instead of returning a 3xx Response; expose it explicitly.
async function desktopFetch(net,raw,options={}){
 let url=httpsURL(raw).href,current={...options};
 for(let hop=0;hop<8;hop++){
  const response=await requestOnce(net,url,current);Object.defineProperty(response,'url',{value:url});
  if(![301,302,303,307,308].includes(response.status)||!response.headers.has('location'))return response;
  if(options.redirect==='manual')return response;
  if(options.redirect==='error')throw Error('Redirection refusée');
  const target=httpsURL(new URL(response.headers.get('location'),url));
  const headers=new Headers(current.headers);
  if(target.origin!==new URL(url).origin)for(const name of [...headers.keys()])if(!['user-agent','referer','origin','accept','accept-language','content-type'].includes(name))headers.delete(name);
  if(response.status===303||(response.status===301||response.status===302)&&current.method==='POST'){current={...current,method:'GET',body:undefined};headers.delete('content-type');headers.delete('content-length');}
  current={...current,headers:Object.fromEntries(headers)};url=target.href;
 }
 throw Error('Trop de redirections HTTP');
}
function requestOnce(net,url,options){
 const signal=options.signal;if(signal?.aborted)return Promise.reject(signal.reason);
 return new Promise((resolve,reject)=>{
  const request=net.request({url,method:options.method||'GET',redirect:'manual',credentials:'include',headers:{...options.headers,'Sec-Fetch-Mode':'no-cors'}});
  const abort=()=>{request.abort();reject(signal.reason);};
  const cleanup=()=>signal?.removeEventListener('abort',abort);
  signal?.addEventListener('abort',abort,{once:true});
  const headersOf=values=>{const headers=new Headers();for(const [name,value]of Object.entries(values||{}))for(const item of Array.isArray(value)?value:[value])headers.append(name,String(item));return headers;};
  request.on('redirect',(status,_method,address,values)=>{const headers=headersOf(values);headers.set('Location',address);cleanup();resolve(new Response(null,{status,headers}));request.abort();});
  request.on('response',message=>{try{const body=[204,205,304].includes(message.statusCode)||options.method==='HEAD'?null:Readable.toWeb(message);message.once('end',cleanup);message.once('close',()=>{cleanup();if(!message.readableEnded)request.abort()});if(!body)message.resume();resolve(new Response(body,{status:message.statusCode,statusText:message.statusMessage||'',headers:headersOf(message.headers)}));}catch(error){cleanup();request.abort();reject(error);}});
  request.on('error',error=>{cleanup();reject(error)});
  if(options.body!==undefined)request.end(options.body);else request.end();
 });
}
module.exports={desktopFetch};
