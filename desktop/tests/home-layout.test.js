'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
test('Home styles use the visible renderer even when a required module has another global document',()=>{
 const previous=global.document,styles=[];let backgroundReads=0;
 global.document={getElementById(){backgroundReads++;throw Error('Background document must not be used');}};
 const renderer={getElementById:id=>styles.find(s=>s.id===id),createElement:()=>({}),head:{appendChild:style=>styles.push(style)}};
 const App={View:{MovieBrowser:{extend:view=>view}}};const $=()=>[{ownerDocument:renderer}];
 try{require('../overlay/butter-provider/streamnest-home-ui').install(App,$);assert.equal(backgroundReads,0);assert.equal(styles.length,1);assert.equal(styles[0].id,'sn-home-style');assert.ok(styles[0].textContent.includes('.sn-home'));assert.equal(typeof App.View.HomeBrowser.onAttach,'function');}
 finally{if(previous===undefined)delete global.document;else global.document=previous;}
});
