const { spawnSync } = require('node:child_process');
const fs=require('node:fs');const path=require('node:path');
const manifest=require('./package.json'),lock=require('./package-lock.json'),android=fs.readFileSync(path.join(__dirname,'../app/build.gradle.kts'),'utf8');
if(!android.includes(`versionName = "v${manifest.version}"`)||lock.version!==manifest.version)throw Error('Versions Android, Windows et lockfile désynchronisées');
const licenses=path.join(__dirname,'licenses');fs.mkdirSync(licenses,{recursive:true});
for(const file of ['NOTICE.txt','streamflix-Apache-2.0.txt'])fs.copyFileSync(path.join(__dirname,'../app/src/main/assets/licenses',file),path.join(licenses,file));
const notices=[];for(const [directory,metadata]of Object.entries(lock.packages)){if(!directory||metadata.dev)continue;const root=path.join(__dirname,directory);const pkg=JSON.parse(fs.readFileSync(path.join(root,'package.json'),'utf8'));notices.push(`\n${pkg.name} ${pkg.version} · ${pkg.license||'Voir texte'}\n`);for(const name of fs.readdirSync(root).filter(name=>/^(licen[sc]e|notice|copying)(\.|$)/i.test(name))){const file=path.join(root,name);if(fs.statSync(file).isFile())notices.push(fs.readFileSync(file,'utf8'));}}
fs.writeFileSync(path.join(licenses,'DEPENDENCIES.txt'),notices.join('\n'),'utf8');
// Bound memory and build time; the default 7z ultra mode is costly on CI.
const result = spawnSync(process.execPath, [require.resolve('electron-builder/cli.js'), '--win', 'portable', '--x64', '--publish', 'never'], {
  stdio: 'inherit',
  env: { ...process.env, ELECTRON_BUILDER_COMPRESSION_LEVEL: '3' }
});
if (result.error) throw result.error;
process.exit(result.status ?? 1);
