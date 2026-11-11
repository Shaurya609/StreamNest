'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),A=require('../overlay/butter-provider/streamnest-artwork');
test('missing shared artwork has an HTTPS IMDb fallback without replacing a valid poster',()=>{
 assert.deepEqual(A.candidates({imdb:'tt11280740'}),['https://images.metahub.space/poster/medium/tt11280740/img']);
 assert.equal(A.candidates({imdb:'tt11280740',images:{poster:'https://static.tvmaze.com/a.jpg'}})[0],'https://static.tvmaze.com/a.jpg');
 assert.equal(A.candidates({imdb:'tt1',poster_big:'file:///secret'})[0],'https://images.metahub.space/poster/medium/tt1/img');
 assert.equal(A.url('http://image.tmdb.org/a.jpg'),'https://image.tmdb.org/a.jpg');assert.equal(A.url('https://name:password@example.com/a.jpg'),'');
});
test('artwork lookup rejects another title and deduplicates independent metadata requests',async()=>{
 assert.equal(A.tvmaze({externals:{imdb:'tt2'},image:{medium:'https://static.tvmaze.com/a.jpg'}},'tt1'),'');let calls=0;
 const get=async()=>{calls++;await new Promise(r=>setTimeout(r,5));return {externals:{imdb:'tt991234'},image:{medium:'https://static.tvmaze.com/a.jpg'}};};const t={imdb:'tt991234',kind:'shows'};
 assert.deepEqual(await Promise.all([A.lookup(t,get),A.lookup(t,get)]),['https://static.tvmaze.com/a.jpg','https://static.tvmaze.com/a.jpg']);assert.equal(calls,1);
});
test('image failure advances to the canonical poster, successful art is reused, cleanup cancels handlers',()=>{
 const t={imdb:'tt991235',poster_big:'https://example.com/broken.jpg',kind:'shows'},image={removeAttribute(){delete this.src;}},placeholder={hidden:true};const stop=A.attach(t,image,placeholder);
 assert.equal(image.src,t.poster_big);image.onerror();assert.equal(image.src,'https://images.metahub.space/poster/medium/tt991235/img');image.onload();assert.equal(placeholder.hidden,true);assert.equal(A.candidates(t)[0],image.src);stop();assert.equal(image.onerror,null);
});
test('resume fractions use timestamps, clamp invalid values and retain small positive positions',()=>{
 assert.equal(A.fraction({positionMs:71,durationMs:100}),.71);assert.equal(A.fraction({positionMs:38000,durationMs:2200000})>0,true);assert.equal(A.fraction({positionMs:5,durationMs:0}),0);assert.equal(A.fraction({positionMs:200,durationMs:100}),1);assert.equal(A.fraction({positionMs:NaN,durationMs:100}),0);
});

test('a hidden-window observer cannot prevent a missing-poster URL from being assigned',()=>{const image={ownerDocument:{defaultView:{IntersectionObserver:class{observe(){}disconnect(){}}}},parentElement:{},removeAttribute(){delete this.src;}},placeholder={};const stop=A.attach({imdb:'tt991236'},image,placeholder);assert.equal(image.src,'https://images.metahub.space/poster/medium/tt991236/img');stop();});
test('exhausted artwork shows a clean placeholder instead of a broken image',async()=>{const image={removeAttribute(){delete this.src;}},placeholder={hidden:true};const stop=A.attach({poster_big:'https://example.invalid/a.jpg',kind:'movies'},image,placeholder);image.onerror();await new Promise(r=>setImmediate(r));assert.equal(image.hidden,true);assert.equal(placeholder.hidden,false);stop();});
