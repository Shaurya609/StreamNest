const fs=require('fs'),path=require('path'),{Resvg}=require('@resvg/resvg-js');
const [svgPath,out]=process.argv.slice(2);const svg=fs.readFileSync(svgPath,'utf8');fs.mkdirSync(out,{recursive:true});
// The traced source includes generous transparent margins; fill the Windows icon tile.
const iconSvg=svg.replace('viewBox="0 0 384 384"','viewBox="40 40 304 304"');
const sizes=[16,20,24,32,40,48,64,96,128,256],images=sizes.map(size=>new Resvg(iconSvg,{fitTo:{mode:'width',value:size}}).render().asPng());
const header=Buffer.alloc(6+16*sizes.length);header.writeUInt16LE(1,2);header.writeUInt16LE(sizes.length,4);let offset=header.length;
for(let i=0;i<sizes.length;i++){const pos=6+16*i;header[pos]=sizes[i]===256?0:sizes[i];header[pos+1]=header[pos];header.writeUInt16LE(1,pos+4);header.writeUInt16LE(32,pos+6);header.writeUInt32LE(images[i].length,pos+8);header.writeUInt32LE(offset,pos+12);offset+=images[i].length;}
fs.writeFileSync(path.join(out,'streamnest.ico'),Buffer.concat([header,...images]));fs.writeFileSync(path.join(out,'icon.png'),images[images.length-1]);fs.copyFileSync(svgPath,path.join(out,'streamnest.svg'));
