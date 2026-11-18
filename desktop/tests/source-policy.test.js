'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const P=require('../overlay/butter-provider/streamnest-source-policy'),E=require('../overlay/butter-provider/streamnest-episodes'),A=require('../overlay/butter-provider/streamnest-audio-check'),X=require('../overlay/butter-provider/streamnest-external-audio');
test('provider index cannot override requested episode identity',()=>{
 const files=[{name:'American.Dad.S11E08.mkv',path:'pack/American.Dad.S11E08.mkv',length:100}];
 assert.throws(()=>E.selectFile(files,{fileIndex:0,file_name:'American.Dad.S11E08.mkv',season:2,episode:1}));
 assert.equal(P.validSource({file:'Show.S02E01.mkv'},{season:2,episode:1}),true);
 assert.equal(P.validSource({file:'Show.S11E08.mkv'},{season:2,episode:1}),false);
 assert.equal(P.episodeName('Show.S01E01-E02.mkv',{season:1,episode:1}),false);
 assert.equal(P.episodeName('[Group] Anime - 108.mkv',{season:2,episode:6,absolute:108}),true);
});
test('Auto deprioritizes unsupported English audio without treating subtitle language as dub',()=>{
 const sources=[{url:'bad',quality:'1080p',seed:7,title:'Show DDP5.1 ENG'},{url:'good',quality:'1080p',seed:2,title:'Show AAC ENG'}];
 assert.equal(E.rank(sources)[0].url,'good');
 assert.equal(P.audio({title:'AAC ITA DDP 5.1 ENG Sub.ENG'}).unsupported,true);
 assert.equal(P.audio({title:'AAC ITA Sub.ENG'}).english,false);
 assert.equal(P.audio({title:'[Erai] Anime [Multiple Subtitle] [ENG][ITA]'}).english,false);
 assert.equal(P.audio({title:'DDPA5.1 H.264'}).unsupported,true);
 assert.equal(P.audio({title:'DD5.1 H.264'}).unsupported,true);
 assert.equal(P.audio({title:'DD+ H.264'}).unsupported,true);
 assert.equal(E.rank(sources,'720p').length,0);
});
test('audio detection distinguishes unsupported output from usable preferred audio',()=>{
 assert.equal(A.assess({videoWidth:1920,readyState:4,audioTracks:[],webkitAudioDecodedByteCount:0}),'missing');
 assert.equal(A.assess({videoWidth:1920,readyState:4,audioTracks:[{language:'ita'}],webkitAudioDecodedByteCount:200}),'language');
 assert.equal(A.assess({videoWidth:1920,readyState:4,audioTracks:[{language:'eng'}],webkitAudioDecodedByteCount:200}),'');
 assert.equal(A.assess({videoWidth:0,readyState:1,audioTracks:[],webkitAudioDecodedByteCount:0}),'');
});
test('external audio handoff uses separate arguments and only local media',()=>{
 const d={id:'VLC',get:()=> require('path').join('Program Files','VideoLAN','VLC','vlc.exe')};let called;
 X.launch(d,'http://127.0.0.1:1234/video',45,null,(...args)=>{called=args;return {}});
 assert.deepEqual(called[1],['--no-video-title-show','--start-time=45','http://127.0.0.1:1234/video']);
 assert.throws(()=>X.launch(d,'https://example.com/video',0));
 assert.throws(()=>X.launch(d,'http://user:pass@localhost/video',0));
 assert.throws(()=>X.launch({id:'VLC',get:()=> 'cmd.exe'},'http://localhost/video',0));
});
