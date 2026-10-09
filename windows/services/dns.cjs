const SERVERS={cloudflare:'https://cloudflare-dns.com/dns-query',google:'https://dns.google/dns-query'};
function dnsOptions(value){
 if(value==='system')return {enableBuiltInResolver:false,secureDnsMode:'off',secureDnsServers:[]};
 if(!Object.hasOwn(SERVERS,value))throw Error('Résolveur DNS inconnu');
 return {enableBuiltInResolver:true,secureDnsMode:'automatic',secureDnsServers:[SERVERS[value]]};
}
module.exports={dnsOptions};
