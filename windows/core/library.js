(function (root, factory) {
  const api = factory(); if (typeof module === 'object') module.exports = api; else root.SamaCore = api;
})(typeof globalThis === 'object' ? globalThis : this, function () {
  const normalize = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/\b(vostfr|truefrench|french|vfq|vff|vf)\b/g, '').replace(/[^a-z0-9]/g, '');
  const key = item => `${normalize(item.title)}|${item.year || ''}|${item.tag || 'Film'}`;
  function same(a, b) { return a.tag === b.tag && ((a.imdbId && b.imdbId && a.imdbId === b.imdbId) || (a.provider === b.provider && a.id === b.id) || key(a) === key(b)); }
  const fresh = () => ({ schema: 2, items: [], history: [], hidden: {}, searches: [], settings: { viewScale: 1, dns: 'cloudflare', language: 'VF', autoplay: true, subtitles: true, pauseDimming: true, sleepMinutes: 0, density: 'Confort', startup: 'Accueil', oled: false, motion: true }, disabled: [], addresses: {} });
  function migrate(input) {
    if (Array.isArray(input)) return { ...fresh(), items: input.map(item => ({ ...item, provider: item.provider || 'Personnel', tag: item.tag || 'Film', episodes: item.episodes || [] })) };
    if (!input || input.schema !== 2 || !Array.isArray(input.items) || !Array.isArray(input.history)) throw Error('Format de bibliothèque invalide');
    if(input.items.some(x=>!x||typeof x.id!=='string'||typeof x.title!=='string'||x.episodes&&!Array.isArray(x.episodes))||input.history.some(x=>!x?.anime||typeof x.anime.id!=='string'||!Number.isFinite(x.position)||!Number.isFinite(x.duration)||!Number.isFinite(x.updatedAt)))throw Error('Entrées de bibliothèque invalides');
    for(const name of ['hidden','addresses','settings','reminders'])if(input[name]!==undefined&&(!input[name]||typeof input[name]!=='object'||Array.isArray(input[name])))throw Error(`Préférence ${name} invalide`);
    for(const name of ['searches','disabled'])if(input[name]!==undefined&&(!Array.isArray(input[name])||input[name].some(x=>typeof x!=='string')))throw Error(`Préférence ${name} invalide`);
    return { ...fresh(), ...input, settings: { ...fresh().settings, ...input.settings } };
  }
  const finished = entry => entry.duration > 0 && entry.duration - entry.position <= 30000;
  function saveProgress(state, anime, episode, position, duration, now = Date.now()) {
    if (!Number.isFinite(position) || !Number.isFinite(duration) || position < 1000 || duration <= 0) return;
    const entry = { anime, episodeId: episode?.id || '', episodeTitle: episode?.title || 'Film', season: episode?.season || episode?.seasonNumber || 0, number: episode?.number || 0, position: Math.min(position, duration), duration, updatedAt: Math.max(now, (state.hidden[key(anime)] || 0) + 1) };
    state.history = [entry, ...state.history.filter(e => !(same(e.anime, anime) && (anime.tag === 'Film' || e.episodeId === entry.episodeId || e.season === entry.season && e.number === entry.number)))].slice(0, 1000);
  }
  function resumeEpisode(state, anime) {
    const episodes = anime.episodes || [], last = state.history.find(e => same(e.anime, anime));
    if (!last) return episodes[0];
    const index = episodes.findIndex(e => e.id === last.episodeId || (e.season || e.seasonNumber) === last.season && e.number === last.number);
    return episodes[index + (finished(last) ? 1 : 0)] || episodes[Math.max(0, index)];
  }
  function continuing(state) {
    const seen = new Set(); return state.history.filter(e => { const id = key(e.anime); if (seen.has(id)) return false; seen.add(id); return (state.hidden[id] || 0) < e.updatedAt; });
  }
  function mergeHistory(state, incoming) {
    const seen = new Set(); state.history = [...incoming, ...state.history].filter(e => e?.anime?.id && Number.isFinite(e.position) && e.position >= 0 && Number.isFinite(e.duration) && e.duration > 0 && Number.isFinite(e.updatedAt)).sort((a,b) => b.updatedAt-a.updatedAt).filter(e => {const id=`${key(e.anime)}|${e.season}|${e.number}|${e.episodeId}`;if(seen.has(id))return false;seen.add(id);return true}).slice(0,1000);
  }
  function mergeCatalog(items) {
    const merged = new Map(); for (const item of items) { const id = key(item), current = merged.get(id); const ref = { provider: item.provider, id: item.id, tag: item.tag };
      if (!current) merged.set(id, { ...item, references: item.references?.length ? item.references : [ref] });
      else current.references = [...current.references, ...(item.references || [ref])].filter((r,i,a) => a.findIndex(x=>x.id===r.id&&x.provider===r.provider)===i);
    } return [...merged.values()];
  }
  function parseSegments(data, imdb, season, episode, isMovie = false) {
    if (!/^tt\d{7,10}$/.test(imdb || '') || data?.imdb_id !== imdb || (!isMovie && (data.season !== season || data.episode !== episode)) || (isMovie && data.media_type !== 'movie' && data.is_movie !== true)) return null;
    const segment = value => { if(!value)return null; const start=Number(value.start_ms ?? Number(value.start_sec)*1000), end=Number(value.end_ms ?? Number(value.end_sec)*1000);return Number.isFinite(start)&&Number.isFinite(end)&&start>=0&&end>start?{start,end}:null; };
    const result = { intro: isMovie?null:segment(data.intro), outro: segment(data.outro) }; return result.intro||result.outro?result:null;
  }
  const seekIncrement = held => held < 3000 ? 1000 : held < 6000 ? 10000 : held < 10000 ? 30000 : 60000;
  return { normalize, key, same, fresh, migrate, finished, saveProgress, resumeEpisode, continuing, mergeHistory, mergeCatalog, parseSegments, seekIncrement };
});
