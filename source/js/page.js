(function(){
if(window.__kjtApp)return;window.__kjtApp=1;var K=window.KJTApp;if(!K)return;var T='@TOKEN@';var A={save:function(b,n,m){K.save(T,b,n,m);},share:function(a,b,c){K.share(T,a,b,c);},copy:function(t){K.copy(T,t);},media:function(s,t){K.media(T,s,t);},vibrate:function(p){K.vibrate(T,String(p));}};
document.documentElement.classList.add('kjt-native');
function save(href,name){
 if(/^data:/.test(href)){var m=/^data:([^;,]*)(;base64)?,(.*)$/.exec(href);if(!m)return;var b=m[2]?m[3]:btoa(unescape(encodeURIComponent(decodeURIComponent(m[3]))));A.save(b,name||'bible-corner',m[1]||'application/octet-stream');return;}
 fetch(href).then(function(r){return r.blob();}).then(function(bl){var fr=new FileReader();fr.onload=function(){var s=String(fr.result);A.save(s.slice(s.indexOf(',')+1),name||'bible-corner',bl.type||'application/octet-stream');};fr.readAsDataURL(bl);});
}
function patch(w){
 try{
  if(!w||w.__kjtPatched)return;w.__kjtPatched=1;
  var P=w.HTMLAnchorElement.prototype,oc=P.click;
  P.click=function(){if(this.hasAttribute('download')&&/^(blob|data):/.test(this.href)){save(this.href,this.getAttribute('download'));return;}return oc.apply(this,arguments);};
  w.document.addEventListener('click',function(e){var a=e.target&&e.target.closest&&e.target.closest('a[download]');if(a&&/^(blob|data):/.test(a.href)){e.preventDefault();e.stopPropagation();save(a.href,a.getAttribute('download'));}},true);
  w.navigator.share=function(d){d=d||{};A.share(String(d.title||''),String(d.text||''),String(d.url||''));return Promise.resolve();};
  w.navigator.canShare=function(){return true;};
  w.navigator.vibrate=function(p){try{A.vibrate(Array.isArray(p)?p.join(','):(p||0));}catch(x){}return true;};
  try{if(!w.navigator.clipboard)Object.defineProperty(w.navigator,'clipboard',{value:{}});w.navigator.clipboard.writeText=function(t){A.copy(String(t));return Promise.resolve();};}catch(x){}
 }catch(e){}
}
patch(window);
document.addEventListener('load',function(e){var f=e.target;if(f&&f.tagName==='IFRAME'){try{patch(f.contentWindow);}catch(x){}}},true);
[].forEach.call(document.querySelectorAll('iframe'),function(f){try{patch(f.contentWindow);}catch(x){}});
/* the page's language and Bible (notifications come in the same language) */
function tellLang(){try{K.lang(T,(document.documentElement.lang||'en').slice(0,2),(window.KJTB_UI&&KJTB_UI.bible)||'');}catch(x){}}
tellLang();setTimeout(tellLang,3000);
/* what the app's own pages may ask the phone */
/* 2.0: the phone's dark mode */
window.__kjtDark=function(d){document.documentElement.classList.toggle('kjt-dark',!!d);try{window.dispatchEvent(new Event('kjt-dark'));}catch(x){}};
try{window.__kjtDark(K.dark());}catch(x){}
/* 2.0: problems on this page, kept for "Report a problem" (only sent if the person sends them) */
var ERRS=[];function err(m){try{ERRS.push(String(m).slice(0,300));if(ERRS.length>20)ERRS.shift();sessionStorage.setItem('kjt_errs',JSON.stringify(ERRS));}catch(x){}}
try{ERRS=JSON.parse(sessionStorage.getItem('kjt_errs')||'[]')||[];}catch(x){}
window.addEventListener('error',function(e){err((e.message||'error')+(e.filename?' @ '+String(e.filename).replace(/^.*\/wp-content\//,'')+':'+(e.lineno||0):''));});
window.addEventListener('unhandledrejection',function(e){err('promise: '+((e.reason&&(e.reason.message||e.reason))||''));});
window.__kjtUpd=function(p){try{window.dispatchEvent(new CustomEvent('kjt-upd',{detail:p}));}catch(x){}};
window.KJTNative={version:K.version(),dark:function(){try{return K.dark();}catch(x){return false;}},errors:function(){return ERRS.slice();},
reminder:function(){try{return JSON.parse(K.reminder(T));}catch(x){return {};}},setReminder:function(on,h,m,t,x){K.setReminder(T,!!on,h|0,m|0,String(t||''),String(x||''));},
addEvent:K.addEvent?function(t,x,y,m,d,n){K.addEvent(T,String(t||''),String(x||''),y|0,m|0,d|0,n|0);}:null,verseTime:K.verseTime?function(){try{return JSON.parse(K.verseTime(T));}catch(x){return {};}}:null,setVerseTime:K.setVerseTime?function(h,m){K.setVerseTime(T,h|0,m|0);}:null,postReminder:K.postReminder?function(){try{return JSON.parse(K.postReminder(T));}catch(x){return {};}}:null,setPostReminder:K.setPostReminder?function(on,h,m,t,x){K.setPostReminder(T,!!on,h|0,m|0,String(t||''),String(x||''));}:null,
verse:function(r,t,u){try{K.verse(T,String(r||''),String(t||''),String(u||''));}catch(x){}},install:function(u){K.install(T,String(u));},
shareImage:function(b,n,t){K.shareImage(T,String(b),String(n||''),String(t||''));},notes:function(){try{return JSON.parse(K.notes(T));}catch(x){return {};}},setNotes:function(n,v){K.setNotes(T,!!n,!!v);},askNotes:function(){K.askNotes(T);},testNote:function(t,x){K.testNote(T,String(t),String(x));},games:K.games?function(j){K.games(T,String(j||'[]'));}:null,gameNote:K.gameNote?function(t,x,u){K.gameNote(T,String(t||''),String(x||''),String(u||''));}:null,settings:function(){location.href='app-settings:';}};
try{window.dispatchEvent(new Event('kjt-native'));}catch(x){}
/* the recording on the lock screen */
var cur=null;
function title(){var t=document.querySelector('.kjtb-passbar__t'),tr=document.querySelector('.kjtb-passbar__tr');var s=t?t.textContent.trim():document.title;if(tr&&tr.textContent.trim())s+=' · '+tr.textContent.trim();return s;}
document.addEventListener('play',function(e){var m=e.target;if(!m||!/^(AUDIO|VIDEO)$/.test(m.tagName))return;cur=m;A.media('play',title());},true);
document.addEventListener('pause',function(e){if(e.target===cur&&!cur.ended)A.media('pause',title());},true);
document.addEventListener('ended',function(e){if(e.target!==cur)return;setTimeout(function(){if(cur&&cur.paused)A.media('stop','');},4000);},true);
window.__kjtMedia=function(a){
 var m=cur;
 if(a==='play'){if(m)m.play();return;}
 if(a==='pause'){if(m)m.pause();return;}
 if(a==='next'||a==='prev'){var s=a==='next'?1:-1;var b=document.querySelector('.kjtb-audio-nav [data-kjtb-step="'+s+'"]')||document.querySelector('.kjtb-mini [data-kjtb-step="'+s+'"]')||document.querySelector('.kjtb-verse-nav [data-kjtb-step="'+s+'"]');if(b)b.click();}
};
})();
