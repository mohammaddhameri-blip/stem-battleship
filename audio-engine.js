/*
 * STEAM BATTLESHIP — ORIGINAL PROCEDURAL SOUNDTRACK & EFFECTS
 * Written for this game: no external audio files, third-party recordings,
 * licensed samples, music services, attribution requirements or royalties.
 * 128 BPM, sixteen-bar minor-key orchestral/electronic battle loop.
 * Web Audio synthesis runs locally and offline after first user interaction.
 */
window.STEAMBattleAudio=(() => {
  'use strict';
  const STORE='steam-battleship-audio-v1';
  const defaults={music:true,sfx:true,musicVolume:.47,sfxVolume:.74};
  function readSettings(){
    try{
      const raw=JSON.parse(localStorage.getItem(STORE)||'null');
      if(raw&&typeof raw==='object')return {
        music:raw.music!==false,sfx:raw.sfx!==false,
        musicVolume:clamp(Number(raw.musicVolume),0,1,.47),
        sfxVolume:clamp(Number(raw.sfxVolume),0,1,.74)
      };
    }catch(_e){}
    return {...defaults};
  }
  function clamp(n,min,max,otherwise){return Number.isFinite(n)?Math.max(min,Math.min(max,n)):otherwise;}
  const settings=readSettings();
  let ctx=null,musicBus=null,sfxBus=null,master=null,noiseBuffer=null;
  let battle=false,interval=null,stepIndex=0,nextStepTime=0;
  const beat=60/128,stepTime=beat/4;
  const chordRoots=[110,87.307,98,82.407,110,87.307,73.416,98];
  const chords=[
    [220,261.626,329.628],[174.614,220,261.626],
    [196,246.942,293.665],[164.814,196,246.942],
    [220,261.626,329.628],[174.614,220,261.626],
    [146.832,174.614,220],[196,246.942,293.665]
  ];
  const melody=[
    [440,523.25,659.26,587.33],
    [523.25,440,392,349.23],
    [392,493.88,587.33,659.26],
    [493.88,392,329.63,493.88],
    [440,523.25,659.26,783.99],
    [698.46,659.26,523.25,440],
    [587.33,523.25,440,392],
    [493.88,587.33,659.26,440]
  ];
  function save(){try{localStorage.setItem(STORE,JSON.stringify(settings));}catch(_e){}}
  function ensure(){
    if(ctx)return true;
    const C=window.AudioContext||window.webkitAudioContext;
    if(!C)return false;
    try{
      ctx=new C();
      master=ctx.createGain();master.gain.value=.86;
      const limiter=ctx.createDynamicsCompressor();
      limiter.threshold.value=-15;limiter.knee.value=12;
      limiter.ratio.value=5;limiter.attack.value=.003;limiter.release.value=.25;
      master.connect(limiter);limiter.connect(ctx.destination);
      musicBus=ctx.createGain();sfxBus=ctx.createGain();
      musicBus.connect(master);sfxBus.connect(master);
      musicBus.gain.value=settings.music?settings.musicVolume:0;
      sfxBus.gain.value=settings.sfx?settings.sfxVolume:0;
      // Reusable white noise buffer (no external audio asset).
      noiseBuffer=ctx.createBuffer(1,Math.ceil(ctx.sampleRate*1.8),ctx.sampleRate);
      const samples=noiseBuffer.getChannelData(0);
      for(let i=0;i<samples.length;i++)samples[i]=(Math.random()*2-1);
      return true;
    }catch(_e){ctx=null;return false;}
  }
  function unlock(){
    if(!ensure())return false;
    if(ctx.state==='suspended')ctx.resume().catch(()=>{});
    if(battle&&settings.music&&!interval)startClock();
    return true;
  }
  function envelope(gain,t,duration,peak,attack=.01,release=.16){
    const safe=Math.max(.025,duration),atk=Math.min(attack,safe*.35),end=t+safe;
    const level=Math.max(.00001,peak);
    gain.setValueAtTime(.00001,t);
    gain.linearRampToValueAtTime(level,t+atk);
    gain.setValueAtTime(level,Math.max(t+atk,end-release));
    gain.exponentialRampToValueAtTime(.00001,end);
  }
  function voice(freq,time,duration,volume,bus,type='triangle',endFreq=null,cutoff=0,attack=.007){
    if(!ctx||!bus||!Number.isFinite(freq))return;
    const o=ctx.createOscillator(),g=ctx.createGain();
    o.type=type;o.frequency.setValueAtTime(Math.max(15,freq),time);
    if(endFreq!==null)o.frequency.exponentialRampToValueAtTime(Math.max(15,endFreq),time+Math.max(.03,duration));
    if(cutoff){
      const filter=ctx.createBiquadFilter();filter.type='lowpass';filter.frequency.value=cutoff;
      o.connect(filter);filter.connect(g);
    }else o.connect(g);
    g.connect(bus);envelope(g.gain,time,duration,volume,attack,Math.min(.18,duration*.45));
    o.start(time);o.stop(time+duration+.015);
  }
  function noise(time,duration,volume,bus,filterFrequency=1600,filterType='highpass'){
    if(!ctx||!noiseBuffer||!bus)return;
    const src=ctx.createBufferSource(),filter=ctx.createBiquadFilter(),g=ctx.createGain();
    src.buffer=noiseBuffer;filter.type=filterType;filter.frequency.value=filterFrequency;
    src.connect(filter);filter.connect(g);g.connect(bus);
    envelope(g.gain,time,duration,volume,.003,Math.min(.18,duration*.65));
    src.start(time);src.stop(time+duration+.012);
  }
  function kick(t,energy=.1){
    voice(138,t,.19,energy,musicBus,'sine',43,0,.002);
    noise(t,.055,.022,musicBus,1100,'lowpass');
  }
  function snare(t){noise(t,.125,.066,musicBus,1450);voice(205,t,.076,.021,musicBus,'triangle',110);}
  function hat(t,accent){noise(t,.047,accent?.019:.010,musicBus,6500);}
  function composeStep(time,index){
    const bar=Math.floor(index/16)%8,section=Math.floor(index/128)%2;
    const step=index%16,roots=chordRoots[bar],chord=chords[bar];
    if(step===0||step===8||step===10||(section===1&&(step===6||step===14)))kick(time,step===0?.12:.085);
    if(step===4||step===12)snare(time);
    if(section===1&&(step===3||step===11))hat(time,true);
    if(step%2===0)hat(time,step%4===0);
    if(step===0){
      for(const f of chord)voice(f,time,1.72,section===1?.022:.018,musicBus,'sawtooth',null,1250,.11);
      voice(roots/2,time,1.83,.043,musicBus,'sine',null,0,.09);
      if(section===1)voice(chord[0]*2,time,1.5,.012,musicBus,'triangle',null,0,.13);
    }
    if(step%4===0){
      const note=step===12?roots*1.5:roots;
      voice(note,time,.28,.095,musicBus,'sawtooth',null,560);
      voice(note/2,time,.30,.058,musicBus,'sine');
    }
    // A recurring battle ostinato above the drums, with a distinct 8-bar theme.
    if(step%2===0){
      const idx=(step/2+bar*2)%6;
      const pitch=chord[idx%3]*(idx>=3?2:1);
      voice(pitch,time,.14,.027,musicBus,'square',null,1850,.008);
    }
    if(step===0||step===5||step===9||step===13){
      const pos=step===0?0:step===5?1:step===9?2:3;
      const pitch=melody[bar][pos]*(section===1&&step===13?2:1);
      voice(pitch,time,.28,.041,musicBus,'sawtooth',null,2450,.025);
      voice(pitch/2,time,.23,.014,musicBus,'triangle');
    }
    if(step===15&&bar%2===1)noise(time,.20,.052,musicBus,4500);
    if(section===1&&step===15&&bar===7)noise(time,.45,.085,musicBus,5500);
  }
  function tickMusic(){
    if(!battle||!settings.music||!ctx||ctx.state!=='running')return;
    const now=ctx.currentTime;
    if(nextStepTime<now-.4)nextStepTime=now+.045;
    let count=0;
    while(nextStepTime<now+.32&&count++<8){
      composeStep(nextStepTime,stepIndex);
      nextStepTime+=stepTime;stepIndex=(stepIndex+1)%256;
    }
  }
  function startClock(){
    if(!ctx||interval||!settings.music||!battle)return;
    stepIndex=0;nextStepTime=ctx.currentTime+.065;
    tickMusic();
    interval=setInterval(tickMusic,85);
  }
  function stopClock(){if(interval!==null){clearInterval(interval);interval=null;}}
  function start(){
    battle=true;
    unlock(); // AudioContext is unlocked by a preceding click or tap.
    if(ctx&&settings.music){
      updateBus('music'); // Restore music after a previous match faded it out.
      startClock();
    }
  }
  function stop(){
    battle=false;stopClock();
    // Scheduled notes are quickly faded instead of hanging in the workshop.
    if(ctx&&musicBus){
      const t=ctx.currentTime;
      musicBus.gain.cancelScheduledValues(t);
      musicBus.gain.setValueAtTime(musicBus.gain.value,t);
      musicBus.gain.setTargetAtTime(0,t,.08);
    }
  }
  function updateBus(which){
    const bus=which==='music'?musicBus:sfxBus;
    if(!ctx||!bus)return;
    const t=ctx.currentTime,enabled=which==='music'?settings.music:settings.sfx,
      vol=which==='music'?settings.musicVolume:settings.sfxVolume;
    bus.gain.cancelScheduledValues(t);
    bus.gain.setTargetAtTime(enabled?vol:0,t,.035);
  }
  function setMusicEnabled(flag){settings.music=!!flag;save();if(settings.music&&battle){unlock();if(ctx){updateBus('music');startClock();}}else{stopClock();updateBus('music');}}
  function setSfxEnabled(flag){settings.sfx=!!flag;save();if(settings.sfx)unlock();updateBus('sfx');}
  function setMusicVolume(vol){settings.musicVolume=clamp(Number(vol),0,1,.47);save();updateBus('music');}
  function setSfxVolume(vol){settings.sfxVolume=clamp(Number(vol),0,1,.74);save();updateBus('sfx');}
  function sfx(kind){
    if(!settings.sfx||!ensure()||ctx.state!=='running')return;
    const t=ctx.currentTime+.012,b=sfxBus;
    switch(kind){
      case 'shell':
        voice(190,t,.33,.25,b,'sawtooth',47,850);
        noise(t,.27,.20,b,640,'lowpass');
        noise(t+.1,.36,.095,b,1100);break;
      case 'flak':
        for(let i=0;i<3;i++){
          noise(t+i*.09,.11,.15,b,2200);
          voice(360,t+i*.09,.075,.12,b,'square',100,1700);
        }break;
      case 'torpedo':
        for(let i=0;i<2;i++)voice(525,t+i*.20,.24,.105,b,'sine',265);
        noise(t+.28,.35,.12,b,900);break;
      case 'airstrike':
        // Jet engine pass, then falling smart missile from the B-2.
        voice(65,t,1.45,.15,b,'sawtooth',118,800,.03);
        voice(116,t+.08,1.25,.10,b,'triangle',180,1300,.03);
        noise(t+.15,1.24,.13,b,720,'lowpass');
        voice(1400,t+.92,.63,.095,b,'sawtooth',170,3000,.008);
        noise(t+1.02,.34,.09,b,2100);break;
      case 'hit':
        voice(95,t,.47,.24,b,'sawtooth',33,690);
        noise(t,.49,.23,b,560,'lowpass');
        noise(t+.09,.38,.16,b,1200);break;
      case 'destroy':
        voice(110,t,.95,.26,b,'sawtooth',24,620,.008);
        noise(t,.75,.24,b,510,'lowpass');
        noise(t+.25,.75,.17,b,1450);
        voice(45,t+.55,.87,.14,b,'sine',25);break;
      case 'repair':
        for(let i=0;i<7;i++){
          const note=[329.63,392,440,523.25,587.33,659.26,880][i];
          voice(note,t+i*.10,.27,.072,b,'sine',note*1.03);
          voice(note/2,t+i*.10,.22,.032,b,'triangle');
        }break;
      case 'radio':
        noise(t,.055,.07,b,2400);noise(t+.12,.05,.065,b,2400);
        for(let i=0;i<3;i++)voice([760,1060,900][i],t+.19+i*.095,.085,.075,b,'sine');break;
      case 'correct':
        voice(670,t,.095,.085,b,'sine');
        voice(920,t+.10,.12,.073,b,'triangle');break;
      case 'wrong':
        voice(260,t,.16,.07,b,'triangle',170);
        voice(170,t+.11,.19,.05,b,'triangle',100);break;
      case 'move':
        voice(390,t,.07,.045,b,'sine',260);break;
      case 'victory':
        for(let i=0;i<6;i++){
          const n=[261.63,329.63,392,523.25,659.26,783.99][i];
          voice(n,t+i*.18,.38,.105,b,'sawtooth',null,2400,.019);
          voice(n/2,t+i*.18,.34,.038,b,'triangle');
        }break;
      case 'click':
        voice(530,t,.055,.034,b,'sine',370);break;
    }
  }
  function legacyTone(freq=420,duration=.08,type='sine',volume=.045){
    if(!settings.sfx||!ensure()||ctx.state!=='running')return;
    voice(freq,ctx.currentTime+.008,duration,clamp(volume*1.6,.002,.18,.04),sfxBus,type);
  }
  return Object.freeze({
    unlock,start,stop,sfx,legacyTone,setMusicEnabled,setSfxEnabled,setMusicVolume,setSfxVolume,
    getSettings:()=>({...settings}),
    status:()=>({supported:!!(window.AudioContext||window.webkitAudioContext),running:!!interval,battle,unlocked:!!ctx&&ctx.state==='running'})
  });
})();