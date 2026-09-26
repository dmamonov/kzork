'use strict';
const $ = id => document.getElementById(id);
const token = document.querySelector('meta[name="game-token"]').content;
let cursor = 0, state = null, initial = true, pendingRequest = false;
const seen = new Set();
const audio = new Atmosphere();
async function api(action, body = {}) {
  const response = await fetch('/api/' + action, {method:'POST', headers:{'Content-Type':'application/json','X-Game-Token':token}, body:JSON.stringify(body)});
  const result = await response.json();
  if (!response.ok || !result.ok) throw new Error(result.error || 'The action could not be completed.');
  return result;
}
function problem(message) { $('connection-error').textContent = message; $('connection-error').hidden = !message; }
function closePanels() {
  for (const name of ['audio','agent']) { $(name+'-panel').hidden = true; $(name === 'audio' ? 'sound-button' : 'agent-button').setAttribute('aria-expanded','false'); }
}
$('sound-button').onclick = () => { const open = $('audio-panel').hidden; closePanels(); $('audio-panel').hidden = !open; $('sound-button').setAttribute('aria-expanded',String(open)); };
$('agent-button').onclick = () => { const open = $('agent-panel').hidden; closePanels(); $('agent-panel').hidden = !open; $('agent-button').setAttribute('aria-expanded',String(open)); };
document.querySelectorAll('.close-panel').forEach(button => button.onclick = closePanels);
document.addEventListener('keydown', event => { if (event.key === 'Escape') closePanels(); });
$('begin').onclick = async () => {
  await audio.unlock();
  try { await api('begin'); await poll(); $('command').focus(); } catch(error) { problem(error.message); }
};
async function submit(command) {
  if (!command.trim() || pendingRequest || state?.busy || state?.automatic) return;
  pendingRequest = true; audio.stopVoice(); problem('');
  try { await audio.unlock(); await api('command', {command}); $('command').value = ''; await poll(); }
  catch(error) { problem(error.message); }
  finally { pendingRequest = false; }
}
$('command-form').onsubmit = event => { event.preventDefault(); submit($('command').value); };
document.querySelectorAll('[data-command]').forEach(button => button.onclick = () => submit(button.dataset.command));
$('start-agent').onclick = async () => {
  closePanels(); await audio.unlock(); audio.stopVoice();
  try { await api('auto', {turns:Number($('turn-budget').value)}); await poll(); } catch(error) { problem(error.message); }
};
$('pause-agent').onclick = async () => { try { await api('pause'); await poll(); } catch(error) { problem(error.message); } };
$('retry').onclick = async () => { try { await api('retry'); await poll(); } catch(error) { problem(error.message); } };
$('jump').onclick = () => { window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'}); $('jump').hidden = true; };
window.addEventListener('scroll', () => { if (nearBottom()) $('jump').hidden = true; }, {passive:true});
function nearBottom() { return document.documentElement.scrollHeight - window.innerHeight - window.scrollY < 270; }
function node(tag, className, text) { const element = document.createElement(tag); if(className) element.className=className; if(text) element.textContent=text; return element; }
function renderEvent(event, quiet) {
  if (seen.has(event.id)) return; seen.add(event.id);
  const follow = nearBottom();
  const article = node('article','event ' + event.role); article.dataset.eventId = event.id;
  if (event.image) {
    const wrapper = node('div','cover-wrap'); const img=node('img','cover'); img.src=event.image; img.alt=event.title || 'The atmosphere of the adventure'; img.loading='lazy';
    img.onerror=()=>{ wrapper.hidden=true; }; wrapper.append(img); article.append(wrapper);
  }
  const label = node('div','event-label', ({player:'YOU',agent:'PLAYER AGENT',narrator:'THE STORY',location:'A NEW CHAMBER',opening:'YOUR ADVENTURE BEGINS',error:'A PAUSE IN THE STORY'})[event.role] || 'THE STORY');
  if (event.turn && event.role === 'narrator') label.append(node('span','',String(event.turn).padStart(2,'0')));
  if (['narrator','location','opening'].includes(event.role)) {
    const replay=node('button','replay','▷'); replay.setAttribute('aria-label','Replay narration'); replay.title='Replay narration'; replay.onclick=async()=>{ await audio.unlock(); audio.stopVoice(); audio.speak(event); }; label.append(replay);
  }
  article.append(label);
  if(event.title) article.append(node('h2','',event.title));
  for(const paragraph of event.text.split(/\n\s*\n/)) article.append(node('p','',paragraph.replace(/\n/g,' ')));
  if (event.scene) {
    const details=node('div','location-details'); details.append(node('span','','EXITS · ' + event.scene.exits.join(' / ').toUpperCase()));
    if(event.scene.actors.length) details.append(node('span','','HERE · ' + event.scene.actors.join(', ')));
    article.append(details);
  }
  $('transcript').append(article);
  if(!quiet) {
    if(['player','agent'].includes(event.role)) audio.stopVoice();
    if(event.effect) audio.effect(event.effect);
    if(['narrator','location','opening'].includes(event.role)) audio.speak(event);
    if(follow) requestAnimationFrame(()=>window.scrollTo({top:document.body.scrollHeight,behavior:'smooth'}));
    else $('jump').hidden=false;
  }
}
let polling=false;
async function poll() {
  if(polling) return; polling=true;
  try {
    const response=await fetch('/api/state?after='+cursor,{headers:{'X-Game-Token':token}});
    if(!response.ok) throw new Error('Cannot reach the adventure. Keep this page open; reconnecting…');
    const next=await response.json();
    $('welcome').hidden=next.started;
    next.events.forEach(event=>renderEvent(event,initial)); cursor+=next.events.length;
    state=next;
    $('mode').textContent=next.mode === 'demo' ? 'OFFLINE DEMO' : 'LIVE AI';
    $('agent-button').querySelector('span').textContent=next.mode === 'demo' ? 'Guide plays' : 'Agent plays';
    $('agent-description').textContent=next.mode === 'demo' ? 'Watch the offline guide explore this small dungeon. Live AI mode uses a player agent with the same public clues you see.' : 'A player agent explores using the same clues you see. This uses your configured AI service. Pause whenever you want to take control.';
    $('location-label').textContent=next.scene.title.toUpperCase();
    $('control-label').textContent=next.automatic ? 'WATCHING THE PLAYER AGENT' : 'YOU ARE THE HEARTH KEEPER';
    $('activity').hidden=!next.busy; $('activity-text').textContent=next.status;
    $('auto-status').hidden=!next.automatic && !(next.busy && next.status.startsWith('Pausing'));
    $('auto-text').textContent=next.automatic ? `Agent plays · ${next.remaining} turns left` : 'Finishing the current turn…';
    $('command').disabled=$('send').disabled=!next.started || next.busy || next.automatic || next.outcome==='won' || next.pending;
    $('command').placeholder=next.busy ? 'The story is unfolding…' : next.outcome==='won' ? 'Your adventure is complete.' : 'What will you do?';
    $('start-agent').disabled=next.busy || next.outcome==='won' || next.pending;
    $('retry').hidden=!next.pending || next.busy;
    $('win').hidden=next.outcome!=='won';
    document.querySelectorAll('[data-command]').forEach(button=>button.disabled=$('command').disabled);
    audio.location(next.scene);
    if(initial && next.started) window.scrollTo(0,document.body.scrollHeight);
    initial=false;
    if(!$('connection-error').hidden && $('connection-error').textContent.startsWith('Cannot reach')) problem('');
  } catch(error) { problem(error.message); }
  finally { polling=false; }
}
poll(); setInterval(poll,700);

function Atmosphere() {
  this.context=null; this.scene=null; this.voices=[]; this.queue=[]; this.speaking=false; this.muted=false;
  this.musicVolume=0.25; this.effectsVolume=0.35; this.voiceVolume=0.8; this.track=null; this.trackUrl=null; this.generation=0;
  this.unlock=async()=>{
    try { if(!this.context) { this.context=new AudioContext(); this.musicBus=this.context.createGain(); this.musicBus.gain.value=0; this.musicBus.connect(this.context.destination); }
      await this.context.resume(); this.startMusic();
    } catch (_) { $('audio-note').textContent='Ambient audio is unavailable in this browser. You can still play.'; }
  };
  this.location=scene=>{ if(this.scene?.id===scene.id && this.scene?.music===scene.music) return; this.scene=scene; if(this.context) this.startMusic(); };
  this.startMusic=()=>{
    if(!this.scene || !this.context) return;
    const musicKey=this.scene.music || this.scene.id;
    if(this.musicKey===musicKey){this.levels();return;}this.musicKey=musicKey;
    const t=this.context.currentTime; const level=this.muted?0:this.musicVolume*(this.speaking?0.3:1);
    this.musicBus.gain.setTargetAtTime(level*0.075,t,0.8);
    if(this.scene.music) {
      if(this.trackUrl!==this.scene.music) { this.track?.pause(); this.track=new Audio(this.scene.music); this.track.loop=true; this.trackUrl=this.scene.music; this.track.onerror=()=>{ this.trackUrl=null; this.scene={...this.scene,music:null}; this.musicKey=null; this.startMusic(); }; }
      this.track.volume=level; this.track.play().catch(()=>{});
      this.voices.forEach(v=>{try{v.osc.stop();}catch(_){}});this.voices=[]; return;
    }
    this.track?.pause();this.track=null;this.trackUrl=null;
    const old=this.voices; this.voices=[];
    const warm=this.scene.id==='cell-2-0'||this.scene.id==='cell-2-1';
    const hash=[...this.scene.id].reduce((n,c)=>n+c.charCodeAt(0),0);
    const base=warm?110:73.42+(hash%4)*3.5;
    [1,1.5,2.003].forEach((ratio,i)=>{
      const osc=this.context.createOscillator(), gain=this.context.createGain();osc.type='sine';osc.frequency.value=base*ratio;
      gain.gain.setValueAtTime(0,t);gain.gain.linearRampToValueAtTime(i?0.24:0.45,t+1.8);osc.connect(gain);gain.connect(this.musicBus);osc.start();this.voices.push({osc,gain});
    });
    old.forEach(({osc,gain})=>{gain.gain.setTargetAtTime(0,t,0.45);try{osc.stop(t+1.8);}catch(_){}});
  };
  this.levels=()=>{
    if(this.context) this.musicBus.gain.setTargetAtTime((this.muted?0:this.musicVolume)*(this.speaking?0.3:1)*0.075,this.context.currentTime,0.25);
    if(this.track) this.track.volume=(this.muted?0:this.musicVolume)*(this.speaking?0.3:1);
  };
  this.effect=kind=>{
    if(!this.context||this.muted||!this.effectsVolume)return;
    const time=this.context.currentTime;
    const notes=kind==='success'?[440,554.37,659.25,880]:kind==='bell'?[440,880]:[190,260,380];
    notes.forEach((frequency,i)=>{ const osc=this.context.createOscillator(), gain=this.context.createGain(); const start=time+i*0.14;osc.type=kind==='ice'?'triangle':'sine';osc.frequency.value=frequency;gain.gain.setValueAtTime(0,start);gain.gain.linearRampToValueAtTime(this.effectsVolume*0.12,start+0.01);gain.gain.exponentialRampToValueAtTime(0.001,start+1.1);osc.connect(gain);gain.connect(this.context.destination);osc.start(start);osc.stop(start+1.2); });
  };
  this.stopVoice=()=>{ this.generation++;this.queue=[];window.speechSynthesis?.cancel();this.voiceAudio?.pause();this.voiceAudio=null;this.speaking=false;this.levels(); };
  this.speak=event=>{ if(!this.context||this.muted||!$('narrate').checked||!this.voiceVolume)return;this.queue.push(event);this.nextVoice(); };
  this.nextVoice=()=>{
    if(this.speaking||!this.queue.length)return;const event=this.queue.shift(), generation=this.generation;
    this.speaking=true;this.levels();
    const done=()=>{if(generation!==this.generation)return;this.speaking=false;this.levels();this.nextVoice();};
    const fallback=()=>{
      if(!window.speechSynthesis){$('audio-note').textContent='Narration is unavailable. All story text is still shown.';done();return;}
      const utterance=new SpeechSynthesisUtterance((event.title?event.title+'. ':'')+event.text.replace(/\n/g,' '));utterance.rate=0.94;utterance.volume=this.voiceVolume;utterance.onend=done;utterance.onerror=done;window.speechSynthesis.speak(utterance);
    };
    if(event.voice) {this.voiceAudio=new Audio(event.voice);this.voiceAudio.volume=this.voiceVolume;this.voiceAudio.onended=done;this.voiceAudio.onerror=fallback;this.voiceAudio.play().catch(fallback);} else fallback();
  };
  for(const [id,key] of [['music-volume','musicVolume'],['effects-volume','effectsVolume'],['voice-volume','voiceVolume']]) {
    const saved=localStorage.getItem(id);if(saved!==null)$(id).value=saved;
    this[key]=Number($(id).value)/100;
    $(id).oninput=()=>{this[key]=Number($(id).value)/100;localStorage.setItem(id,$(id).value);this.levels();if(key==='voiceVolume'&&this.voiceAudio)this.voiceAudio.volume=this[key];};
  }
  $('narrate').checked=localStorage.getItem('narrate')!=='false';
  $('narrate').onchange=()=>{localStorage.setItem('narrate',String($('narrate').checked));if(!$('narrate').checked)this.stopVoice();};
  $('mute').onclick=()=>{this.muted=!this.muted;$('mute').textContent=this.muted?'Unmute all':'Mute all';if(this.muted)this.stopVoice();this.levels();};
  $('stop-voice').onclick=()=>this.stopVoice();
}
