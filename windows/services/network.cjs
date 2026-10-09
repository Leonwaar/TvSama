const UA=`Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${process.versions.chrome||'131.0.0.0'} Safari/537.36`;
function httpsURL(raw) { const url=new URL(raw);if(url.protocol!=='https:'||url.username||url.password)throw Error('URL HTTPS requise');return url; }
class Network {
  constructor(fetcher=fetch,limit=8) {this.fetcher=fetcher;this.limit=limit;this.active=0;this.waiters=[];this.cache=new Map();}
  async slot(signal) {if(signal?.aborted)throw signal.reason;if(this.active<this.limit){this.active++;return}await new Promise((resolve,reject)=>{const waiter={resolve,reject,signal};waiter.abort=()=>{this.waiters=this.waiters.filter(w=>w!==waiter);reject(signal.reason)};signal?.addEventListener('abort',waiter.abort,{once:true});this.waiters.push(waiter)});}
  release(){const waiter=this.waiters.shift();if(waiter){waiter.signal?.removeEventListener('abort',waiter.abort);waiter.resolve()}else this.active--;}
  async request(raw,{signal,ttl=60000,headers={},json=false,method='GET',body,maxBytes=8*1024*1024,mediaAware=false}={}) {
    const url=httpsURL(raw).href, cacheKey=`${method}|${body||''}|${json}|${mediaAware}|${url}|${JSON.stringify(headers)}`, cached=this.cache.get(cacheKey);if(cached&&cached.until>Date.now())return cached.value;
    await this.slot(signal);try{const timeout=AbortSignal.timeout(12000);const combined=signal?AbortSignal.any([signal,timeout]):timeout;
      const response=await this.fetcher(url,{signal:combined,redirect:'follow',method,body,headers:{'User-Agent':UA,'Accept-Language':'fr-FR,fr;q=0.9',...headers}});httpsURL(response.url||url);
      if(!response.ok)throw Error(`HTTP ${response.status}`);if(mediaAware){const type=response.headers.get('content-type')||'',format=/mpegurl/i.test(type)?'hls':/dash\+xml/i.test(type)?'dash':/video\/mp4/i.test(type)?'mp4':'';if(format){await response.body?.cancel();return {text:'',url:response.url||url,mediaFormat:format};}}let text='',size=0;const decoder=new TextDecoder();for await(const part of response.body){if(mediaAware&&size===0){const head=new TextDecoder().decode(part.subarray(0,4096)),format=/^\s*#EXTM3U/.test(head)?'hls':/<MPD[\s>]/.test(head)?'dash':head.slice(4,8)==='ftyp'?'mp4':'';if(format)return {text:'',url:response.url||url,mediaFormat:format};}size+=part.byteLength;if(size>maxBytes)throw Error('Réponse trop volumineuse');text+=decoder.decode(part,{stream:true})}text+=decoder.decode();
      const value=json?JSON.parse(text):{text,url:response.url||url};if(ttl>0&&size<=32*1024*1024){this.cache.delete(cacheKey);for(const[key,entry]of this.cache)if(entry.until<=Date.now())this.cache.delete(key);let bytes=[...this.cache.values()].reduce((sum,e)=>sum+(e.bytes||0),0);while(this.cache.size&&(this.cache.size>=150||bytes+size>64*1024*1024)){const oldest=this.cache.keys().next().value;bytes-=this.cache.get(oldest).bytes||0;this.cache.delete(oldest)}this.cache.set(cacheKey,{until:Date.now()+ttl,value,bytes:size})}return value;
    }finally{this.release()}
  }
}
module.exports={Network,httpsURL,UA};
