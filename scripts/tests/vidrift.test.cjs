const {test} = require('node:test');
const assert = require('node:assert/strict');
const {readFileSync} = require('node:fs');
const vm = require('node:vm');
const script = readFileSync('app/src/main/assets/vidrift.js', 'utf8');
function setup(language = 'VF', originalLanguage = 'en', mediaType = 'movie', mediaId = '27205', episode = 0) {
  let now = 1000;
  const context = {Date:{now:()=>now}};
  vm.runInNewContext(script, context);
  const sent = [], events = [];
  const bridge = context.VidriftBridge.create({tmdbId:mediaId, aniListId:mediaId, mediaType, season:0, episode, language, originalLanguage, resumeAt:12},
    value=>sent.push(JSON.parse(JSON.stringify(value))), value=>events.push(JSON.parse(JSON.stringify(value))));
  const state = overrides => ({type:'vidrift:state',tmdbId:mediaId,mediaType,season:0,episode,
    ...(mediaType==='anilist'?{anilist:{id:Number(mediaId),episode}}:{}),
    server:'vela',source:'0',audio:'0',audios:[],sources:[{value:'0',label:'English'}],subtitles:[{value:'off',label:'Off'}],subtitle:'off',
    servers:[{value:'vela',label:'Vela',state:'ready'}],duration:1200,currentTime:0,paused:true,...overrides});
  return {bridge,sent,events,state,setTime:value=>{now=value},language:context.VidriftBridge.audioLanguage};
}
test('VO is not an offered Vidrift mode',()=>{
  const s=setup('VO');s.bridge.receive(s.state());
  assert.ok(s.events.some(e=>e.type==='error'));assert.ok(!s.events.some(e=>e.type==='ready'));
});
test('French subtitles and unidentified streams do not count as French audio',()=>{
  for(const label of ['VOSTFR','French subtitles','Vela 1','Hindi','Tamil']){
    const s=setup('VF');s.bridge.receive(s.state({sources:[{value:'0',label}]}));
    assert.ok(s.events.some(e=>e.type==='error'),label);assert.ok(!s.events.some(e=>e.type==='ready'),label);
  }
});
test('a real French audio track is selected before playback',()=>{
  const s=setup('VF');const audios=[{value:'0',label:'English'},{value:'1',label:'Français'}];
  s.bridge.receive(s.state({audios}));
  assert.ok(s.sent.some(c=>c.type==='vidrift:set'&&c.audio==='1'));
  assert.ok(!s.sent.some(c=>c.type==='vidrift:play'));
  s.bridge.receive(s.state({audios,audio:'1'}));assert.ok(s.events.some(e=>e.type==='ready'));
});
test('VOSTFR requires original audio and a declared French subtitle track',()=>{
  const s=setup('VOSTFR','ja');
  const tracks={sources:[{value:'0',label:'Japanese'}],subtitles:[{value:'off',label:'Off'},{value:'fr',label:'Français'}]};
  s.bridge.receive(s.state(tracks));
  assert.ok(s.sent.some(c=>c.type==='vidrift:set'&&c.subtitle==='fr'));
  assert.ok(!s.events.some(e=>e.type==='ready'));
  s.bridge.receive(s.state({...tracks,subtitle:'fr'}));
  assert.ok(s.events.some(e=>e.type==='ready'));
  assert.ok(s.sent.some(c=>c.type==='vidrift:set'&&c.subtitle==='fr'));
});
test('VOSTFR rejects original audio when French subtitles are absent',()=>{
  const s=setup('VOSTFR','ja');s.bridge.receive(s.state({sources:[{value:'0',label:'Japanese'}]}));
  assert.ok(s.events.some(e=>e.type==='error'));assert.ok(!s.events.some(e=>e.type==='ready'));
});
test('unknown server falls back and accepts only the original Japanese track',()=>{
  const s=setup('VOSTFR','ja'),servers=[{value:'vela',label:'Vela'},{value:'lyra',label:'Lyra'}];
  s.bridge.receive(s.state({sources:[{value:'0',label:'Vela 1'}],servers}));
  assert.ok(s.sent.some(c=>c.server==='lyra'));
  s.bridge.receive(s.state({server:'lyra',duration:0,servers}));
  s.bridge.receive(s.state({server:'lyra',sources:[{value:'0',label:'Japanese'}],subtitles:[{value:'off',label:'Off'},{value:'fr',label:'French'}],subtitle:'fr',servers}));
  assert.ok(s.events.some(e=>e.type==='ready'));
});
test('wrong title events cannot mark progress or enable playback',()=>{
  const s=setup();s.bridge.receive(s.state({tmdbId:'123'}));assert.ok(s.events.some(e=>e.type==='error'));assert.ok(!s.sent.some(c=>c.type==='vidrift:play'));
});
test('a short promo is rejected and waiting never interrupts an approved paused stream',()=>{
  const short=setup();short.bridge.receive(short.state({duration:30}));assert.ok(short.events.some(e=>e.type==='error'));
  const valid=setup();valid.bridge.receive(valid.state({sources:[{value:'0',label:'French'}]}));valid.bridge.tick(300000);
  assert.ok(!valid.events.some(e=>e.type==='error'));
});
test('changing to a foreign dub revokes approval and blocks progress',()=>{
  const s=setup();s.bridge.receive(s.state({sources:[{value:'0',label:'French'}]}));
  s.bridge.receive(s.state({sources:[{value:'0',label:'Hindi'}]}));
  s.bridge.receive({type:'vidrift:progress',tmdbId:'27205',mediaType:'movie',currentTime:20,duration:1200});
  assert.ok(s.events.some(e=>e.type==='error'));assert.ok(!s.events.some(e=>e.type==='progress'));
});
test('AniList embed validates both the AniList title and absolute episode before playback',()=>{
  const s=setup('VOSTFR','ja','anilist','98659',13);
  const valid=s.state({sources:[{value:'0',label:'Japanese'}],subtitles:[{value:'off',label:'Off'},{value:'fr',label:'French'}],subtitle:'fr'});
  s.bridge.receive(valid);
  assert.ok(s.events.some(e=>e.type==='ready'));
  const wrong=setup('VOSTFR','ja','anilist','98659',13);
  wrong.bridge.receive(wrong.state({anilist:{id:145545,episode:13}}));
  assert.ok(wrong.events.some(e=>e.type==='error'));
  assert.ok(!wrong.sent.some(c=>c.type==='vidrift:play'));
});
test('a seek cannot create ninety seconds of confirmed playback',()=>{
  const s=setup();s.bridge.receive(s.state({sources:[{value:'0',label:'French'}]}));
  s.bridge.receive({type:'vidrift:progress',tmdbId:'27205',mediaType:'movie',currentTime:10,duration:1200});
  s.setTime(6000);s.bridge.receive({type:'vidrift:progress',tmdbId:'27205',mediaType:'movie',currentTime:500,duration:1200});
  assert.equal(s.events.filter(e=>e.type==='progress').at(-1).played,0);
});
