'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),S=require('../overlay/butter-provider/streamnest-home-search');
const t=(id,kind,title,aliases=[])=>({imdb:id,kind,title,aliases});
test('Home query searches all categories with normalized aliases and stable pages',()=>{
 const pool=[t('tt1','movies','Café Moon'),t('tt2','shows','Moon series'),t('tt3','animes','Tsuki',['Moon anime']),t('tt3','shows','Moon anime'),t('tt4','movies','Moon sequel'),t('tt5','movies','Other')];
 const first=S.select(pool,'moon',1,1);assert.deepEqual(first.rows.map(r=>r.cards.length),[1,1,1]);assert.equal(first.rows[2].cards[0].title.kind,'animes');assert.ok(first.hasMore);assert.equal(S.select(pool,'moon',2,1).rows[0].cards.length,1);assert.equal(S.select(pool,'cafe').rows[0].cards[0].title.imdb,'tt1');assert.equal(S.select(pool,'tt2').rows[1].cards[0].title.imdb,'tt2');assert.equal(S.select(pool,'absent').rows.flatMap(r=>r.cards).length,0);
});
test('Home discovery preserves independent successes when one service fails',async()=>{
 const results=[];await S.discover('moon',1,r=>results.push(r),async kind=>{if(kind==='shows')throw Error('offline');return {rows:[t(kind==='movies'?'tt1':'tt2',kind,'Moon')],hasMore:false};});assert.equal(results.length,3);assert.ok(results.find(r=>r.kind==='shows').failed);assert.equal(results.filter(r=>!r.failed).flatMap(r=>r.rows).length,2);
});
