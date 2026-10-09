const {load}=require('cheerio');
const {httpsURL}=require('./network.cjs');
// Flight payloads are JSON data. Never execute scripts supplied by a source.
function fluneoServers(text,episode){
  const $=load(text),chunks=[];
  $('script').each((_i,n)=>{const script=$(n).html()?.trim()||'';const match=/^self\.__next_f\.push\(([\s\S]*)\);?$/.exec(script);if(!match)return;try{const payload=JSON.parse(match[1]);if(payload[0]===1&&typeof payload[1]==='string')chunks.push(payload[1]);}catch{}});
  const found=[];
  const visit=(value,depth=0)=>{if(!value||typeof value!=='object'||depth>40)return;const item=value.episode;if(item&&typeof item==='object'&&(!episode?.number||Number(item.episode_number)===episode.number)&&(!episode?.season||Number(item.season_number)===episode.season)){
    if(item.url)found.push({url:item.url,name:'MyFluneo'});
    try{for(const [name,url]of Object.entries(JSON.parse(item.embeds_json||'{}')))if(typeof url==='string')found.push({name,url});}catch{}
  }for(const child of Object.values(value))visit(child,depth+1);};
  for(const record of chunks.join('').split('\n')){const colon=record.indexOf(':');if(colon<0)continue;try{visit(JSON.parse(record.slice(colon+1)));}catch{}}
  return [...new Map(found.filter(entry=>{try{httpsURL(entry.url);return true}catch{return false}}).map(entry=>[entry.url,entry])).values()];
}
module.exports={fluneoServers};
