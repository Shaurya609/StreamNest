'use strict';
const fs=require('fs'),path=require('path');
const root=path.resolve(process.argv[2]),stylus=require(path.join(root,'node_modules/stylus')),nib=require(path.join(root,'node_modules/nib'));
const input=path.join(root,'src/app/styl'),output=path.join(root,'src/app/themes');fs.mkdirSync(output,{recursive:true});
for(const name of fs.readdirSync(input).filter(n=>n.endsWith('.styl'))){
 const file=path.join(input,name);
 const css=stylus(fs.readFileSync(file,'utf8')).set('filename',file).set('paths',[input]).use(nib()).render();
 fs.writeFileSync(path.join(output,name.replace(/\.styl$/,'.css')),css);
}
process.stdout.write('Compiled standalone themes without invoking the legacy runtime downloader.\n');
