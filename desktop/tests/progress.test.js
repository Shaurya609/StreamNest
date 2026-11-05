const {test}=require('node:test'),assert=require('node:assert/strict'),C=require('../overlay/butter-provider/streamnest-sync-crypto'),P=require('../overlay/butter-provider/streamnest-progress');
test('resume identities survive source/quality changes and separate movies and episodes',()=>{
 assert.equal(P.identity({imdb_id:'tt123',torrent:'1080p'}),P.identity({imdb_id:'tt123',torrent:'4k'}));
 assert.notEqual(P.identity({imdb_id:'tt123',season:1,episode:1}),P.identity({imdb_id:'tt123',season:1,episode:2}));
 assert.equal(P.resumable(900,1000),900);assert.equal(P.resumable(990,1000),0);
});
test('merging sync history respects intentional rewinds, completion and unrelated episodes',()=>{
 const a={'movie:tt123':{positionMs:90000,durationMs:1000000,updatedAt:1000}},b={'movie:tt123':{positionMs:30000,durationMs:1000000,updatedAt:2000},'episode:tt456:1:1':{positionMs:10000,durationMs:100000,updatedAt:2000}};
 assert.equal(C.merge(a,b)['movie:tt123'].positionMs,30000);assert(C.merge(a,b)['episode:tt456:1:1']);b['movie:tt123'].positionMs=0;assert.equal(C.merge(a,b)['movie:tt123'].positionMs,0);
});
