const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),os=require('os'),path=require('path'),vm=require('vm');
test('standalone defaults isolate profile/cache and replace legacy endpoint/key defaults',()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'streamnest-standalone-test-')),old=process.env.LOCALAPPDATA;
 try{process.env.LOCALAPPDATA=root;const C=require('../standalone/streamnest-standalone');const settings={providers:{movie:{},tvshow:{},anime:{}},trakttv:{client_id:'upstream',client_secret:'upstream'}};C.configure(settings);
 assert.equal(settings.projectName,'StreamNest');assert(settings.tmpLocation.startsWith(root));assert(settings.databaseLocation.startsWith(root));assert(!settings.tmpLocation.includes('Popcorn'));assert.equal(settings.dhtEnable,false);assert.equal(settings.httpApiEnabled,false);assert.equal(settings.trakttv.client_secret,'');assert(settings.providers.movie.uri[0].includes('https://api.apiabcd.com/'));assert.equal(settings.sourceUrl,'https://github.com/Shaurya609/StreamNest/');
 }finally{if(old===undefined)delete process.env.LOCALAPPDATA;else process.env.LOCALAPPDATA=old;assert(root.startsWith(os.tmpdir()));fs.rmSync(root,{recursive:true,force:true});}
});
test('standalone updater keeps legacy DHT channels disabled',async()=>{
 const App={};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../standalone/updater.js'),'utf8'),{
 window:{App},process:{env:{STREAMNEST_SMOKE_ROOT:'test'}},require:name=>name.includes('streamnest-standalone')?{profileRoot:()=>os.tmpdir()}:name==='./update-policy'?require('../standalone/update-policy'):name==='./update-download'?{}:require(name)
 });await App.Updater.updateDHT();await App.Updater.updateDHTOld();await App.Updater.updateDHTSettings();
 assert.equal(typeof App.Updater.check,'function');
});

test('fresh standalone filter models define language before their first render',async()=>{
 const App={Model:{},vent:{trigger:()=>{}}};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../standalone/lib/models/filter.js'),'utf8'),{window:{App},Backbone:{Model:{extend:value=>value}}});
 const definition=App.Model.Filter,data={provider:{filters:()=>Promise.resolve({languages:{all:'All',hi:'Hindi'},sorters:{trending:'Trending'}})}};
 const model={get:key=>data[key],set:(key,value)=>{data[key]=value;},init:definition.init};definition.initialize.call(model);
 assert.equal(data.language,'all');await Promise.resolve();assert.equal(data.languages.hi,'Hindi');assert.equal(data.load,true);
});
