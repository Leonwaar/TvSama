const assert=require('node:assert/strict'),fs=require('node:fs/promises'),path=require('node:path');
module.exports=async function endurance(page,context,report,duration){
 await page.getByRole('button',{name:'Ma liste',exact:true}).click();
 await page.locator('.card').filter({hasText:'Fixture MP4'}).click();await page.getByRole('button',{name:'Lire / Reprendre',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('video')?.currentTime>1);
 await page.locator('video').evaluate(v=>v.loop=true);
 const cdp=await context.newCDPSession(page);await cdp.send('Performance.enable');
 const started=Date.now(),samples=[];let lastFrames=0;
 report.endurance={requestedMs:duration,samples};
 while(Date.now()-started<duration){
  await new Promise(resolve=>setTimeout(resolve,Math.min(60000,duration-(Date.now()-started))));
  const video=await page.locator('video').evaluate(v=>({frames:v.getVideoPlaybackQuality().totalVideoFrames,paused:v.paused,error:v.error?.message||null,time:v.currentTime}));
  const metrics=await cdp.send('Performance.getMetrics'),dom=await cdp.send('Memory.getDOMCounters');
  const heap=metrics.metrics.find(x=>x.name==='JSHeapUsedSize')?.value;
  const sample={elapsedMs:Date.now()-started,...video,heapBytes:heap,nodes:dom.nodes};samples.push(sample);
  assert.equal(video.paused,false);assert.equal(video.error,null);assert.ok(video.frames>lastFrames,'Lecture figée');assert.ok(heap<128*1024*1024,'Croissance excessive du tas JS');assert.ok(dom.nodes<5000,'Croissance excessive du DOM');lastFrames=video.frames;
  await fs.writeFile(path.join(__dirname,'verification',`endurance-${report.runtime.includes('Wine')?'wine':process.platform}.json`),JSON.stringify(report.endurance,null,2));console.log(JSON.stringify({endurance:sample}));
 }
 report.endurance.completedMs=Date.now()-started;
 assert.deepEqual(report.errors,[]);
};
