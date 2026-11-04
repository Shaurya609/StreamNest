'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto');
function removeOwned(root,entry) {
  root=path.resolve(root);entry=path.resolve(entry);
  if(path.basename(root)!=='StreamNestEpisodes'||path.dirname(entry)!==root)throw Error('Invalid episode cache path');
  if(!fs.existsSync(entry))return;
  if(fs.lstatSync(root).isSymbolicLink()||fs.lstatSync(entry).isSymbolicLink())throw Error('Linked cache folder is not owned');
  const marker=JSON.parse(fs.readFileSync(path.join(entry,'.streamnest-owned.json'),'utf8'));
  if(marker.application!=='StreamNest')throw Error('Unrecognized cache owner');
  function eraseOwned(target){
    const stat=fs.lstatSync(target);
    if(stat.isDirectory()&&!stat.isSymbolicLink()){
      for(const name of fs.readdirSync(target))eraseOwned(path.join(target,name));
      fs.rmdirSync(target);
    }else fs.unlinkSync(target);
  }
  eraseOwned(entry);
}
function prepare(base,identity,source,registry) {
  const root=path.join(path.resolve(base),'StreamNestEpisodes');fs.mkdirSync(root,{recursive:true});
  if(fs.lstatSync(root).isSymbolicLink())throw Error('Cache root cannot be a symbolic link');
  const key=crypto.createHash('sha256').update(identity+'|'+source).digest('hex').slice(0,32),folder=path.join(root,key);
  let roots=[];try{roots=JSON.parse(fs.readFileSync(registry,'utf8'));}catch(e){}
  roots=Array.from(new Set(roots.concat(root)));
  for(const ownedRoot of roots) {
    if(path.basename(ownedRoot)!=='StreamNestEpisodes'||!fs.existsSync(ownedRoot)||fs.lstatSync(ownedRoot).isSymbolicLink())continue;
    for(const name of fs.readdirSync(ownedRoot)) {
      const entry=path.join(ownedRoot,name);
      try {
        if(fs.lstatSync(entry).isSymbolicLink())continue;
        const marker=JSON.parse(fs.readFileSync(path.join(entry,'.streamnest-owned.json'),'utf8'));
        if(marker.application==='StreamNest'&&marker.identity!==identity)removeOwned(ownedRoot,entry);
      }catch(e){/* A busy or unowned file is preserved and retried next time. */}
    }
  }
  if(fs.existsSync(folder)){if(fs.lstatSync(folder).isSymbolicLink())throw Error('Linked episode folder');const marker=JSON.parse(fs.readFileSync(path.join(folder,'.streamnest-owned.json'),'utf8'));if(marker.application!=='StreamNest'||marker.identity!==identity)throw Error('Episode cache is not owned');}
  fs.mkdirSync(folder,{recursive:true});fs.mkdirSync(path.join(folder,'TorrentCache'),{recursive:true});
  fs.writeFileSync(path.join(folder,'.streamnest-owned.json'),JSON.stringify({application:'StreamNest',identity}));
  fs.writeFileSync(registry,JSON.stringify(roots));return folder;
}
module.exports={prepare,removeOwned};
