const {test}=require('node:test'),assert=require('node:assert/strict');
const {installing}=require('../standalone/launch-guard');
const missing=()=>{throw Object.assign(Error(),{code:'ENOENT'});};
test('launch waits for active installer; completed and crashed installers do not block',()=>{
 const marker={readFileSync:()=>String(process.pid)};
 assert.equal(installing('marker',marker,process),true);
 assert.equal(installing('marker',{readFileSync:missing},process),false);
 assert.equal(installing('marker',marker,{kill:()=>{throw Object.assign(Error(),{code:'ESRCH'});}}),false);
 assert.equal(installing('marker',{readFileSync:()=>''},process),true);
 assert.equal(installing('marker',marker,{kill:()=>{throw Object.assign(Error(),{code:'EPERM'});}}),true);
});
