/* STEM Battleship PWA — same local game as the Android APK.
 * Only cache same-origin static assets. Never cache signaling/data channels.
 * Network-first HTML ensures new code arrives on the next visit; offline fallback
 * permits same-device play with the 238 fixed multiple-choice questions,
 * generated multiple-choice Maths, the restored aiming slider, Auto/Manual movement, Pass to Play and Online Match alternating timed turns (30/45/60/75s), random Auto Roam speeds, online Aim Assist locked off, one-shot earned torpedoes, and 2-second weapon unlock alerts, standalone Workshop and Save button, Fit Screen, cinematic B-2 shadow, simultaneous two-player controls, smartboard Fit Screen, 50 HP battleships, optional aiming guides, rough ocean and improved shell collision, and original audio.
 */
const CACHE='steam-battleship-shell-v3.9.0';
const SHELL=['./','./index.html','./audio-engine.js','./question-ms.js','./naval-theme.css','./naval-scene.svg','./manifest.webmanifest','./icons/steam-battleship.svg','./icons/icon-192.png','./icons/icon-512.png'];
self.addEventListener('install',event=>{
  event.waitUntil(caches.open(CACHE).then(cache=>Promise.all(
    SHELL.map(path=>cache.add(path).catch(()=>undefined))
  )).then(()=>self.skipWaiting()));
});
self.addEventListener('activate',event=>{
  event.waitUntil(caches.keys().then(keys=>Promise.all(
    keys.filter(key=>key.startsWith('steam-battleship-shell-')&&key!==CACHE)
      .map(key=>caches.delete(key))
  )).then(()=>self.clients.claim()));
});
self.addEventListener('fetch',event=>{
  const request=event.request;
  if(request.method!=='GET'||new URL(request.url).origin!==self.location.origin)return;
  if(request.mode==='navigate'){
    event.respondWith(fetch(request).then(response=>{
      if(response.ok){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put('./index.html',copy));}
      return response;
    }).catch(async()=>{
      return (await caches.match('./index.html'))||
        new Response('Offline copy unavailable. Connect to the internet once before playing offline.',{status:503,headers:{'Content-Type':'text/plain'}});
    }));
    return;
  }
  event.respondWith(caches.match(request).then(cached=>
    cached||fetch(request).then(response=>{
      if(response.ok&&new URL(request.url).origin===self.location.origin){
        const copy=response.clone();
        caches.open(CACHE).then(cache=>cache.put(request,copy));
      }
      return response;
    })
  ));
});
