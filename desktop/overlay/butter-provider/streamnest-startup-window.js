'use strict';
function ranges(length,offset,pieceLength,fraction=0){
 if(!(length>0)||!(offset>=0)||!(pieceLength>0))return [];
 const out=[],add=(position,bytes,priority)=>{const first=Math.floor((offset+position)/pieceLength),last=Math.floor((offset+Math.min(length-1,position+bytes-1))/pieceLength);if(!out.some(r=>r.first===first&&r.last===last))out.push({first,last,priority});};
 add(0,Math.min(length,1048576),10);add(Math.max(0,length-1048576),1048576,10);
 if(Number.isFinite(fraction)&&fraction>0&&fraction<0.98)add(Math.floor(length*fraction),4194304,5);
 return out;
}
function apply(torrent,index,fraction=0){const f=torrent.files[index];if(!f)return;const selected=ranges(f.length,f.offset,torrent.pieceLength,fraction);torrent.streamnestStartupHint=selected.find(r=>r.priority===5);for(const r of selected)torrent.select(r.first,r.last,r.priority);}
function preferExact(torrent,range){if(/^bytes=[1-9][0-9]*-/i.test(String(range||''))&&torrent.streamnestStartupHint){const r=torrent.streamnestStartupHint;torrent.streamnestStartupHint=null;torrent.deselect(r.first,r.last,r.priority);}}
module.exports={ranges,apply,preferExact};
