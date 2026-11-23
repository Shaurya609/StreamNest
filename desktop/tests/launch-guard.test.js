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

test('normal entry requests the shell before importing the main page; smoke stays hidden',()=>{
 const vm=require('vm'),fs=require('fs'),path=require('path');
 const script=fs.readFileSync(path.join(__dirname,'../standalone/launch.html'),'utf8').split('<script>')[1].split('</script>')[0];
 for(const smoke of [false,true]){
  const events=[],nodes={heading:{},status:{}},timers=[];
  vm.runInNewContext(script,{sessionStorage:{setItem(){}},document:{getElementById:id=>nodes[id]},require:name=>name==='./launch-guard'?{installing:()=>false}:require(name),process:{execPath:process.execPath,env:smoke?{STREAMNEST_SMOKE_ROOT:'isolated'}:{}},nw:{Window:{get:()=>({show:()=>events.push('show')})}},setTimeout:fn=>timers.push(fn),location:{replace:()=>events.push('index')}});
  assert.equal(nodes.heading.textContent,'Starting StreamNest');assert.deepEqual(events,smoke?[]:['show']);
  timers[0]();assert.deepEqual(events,smoke?['index']:['show','index']);
 }
});
