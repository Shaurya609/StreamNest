 'use strict';
// Only an active installer blocks launch. A crashed install must not leave a permanent lock.
function installing(marker,fs,process){
 let text;
 try{text=fs.readFileSync(marker,'utf8').trim();}catch(e){if(e.code==='ENOENT')return false;return true;}
 if(!/^[1-9][0-9]{0,9}$/.test(text))return true;
 try{process.kill(Number(text),0);return true;}catch(e){return e.code!=='ESRCH';}
}
module.exports={installing};
