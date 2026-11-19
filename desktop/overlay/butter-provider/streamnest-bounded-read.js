'use strict';
const {Readable}=require('stream');
const BLOCK=256*1024;
// Preserve ImmediateStore above the disk cache: a verified piece may still be writing.
function partialReads(torrent){
 const cache=torrent.store?.store;
 if(!cache?.cache||cache.store?.constructor?.name!=='Storage')return false;
 if(cache.streamnestPartial)return true;
 const original=cache.get.bind(cache);
 cache.get=function(index,opts,cb){if(opts&&Number.isInteger(opts.length)&&opts.length>0&&opts.length<=BLOCK)return cache.store.get(index,opts,cb);return original(index,opts,cb);};
 cache.streamnestPartial=true;return true;
}
class BoundedStream extends Readable{
 constructor(file,opts={}){
  super({highWaterMark:BLOCK});this.file=file;this.torrent=file._torrent;
  this.at=opts.start??0;this.end=opts.end??file.length-1;this.pending=false;this.wanted=false;
  if(!Number.isSafeInteger(this.at)||!Number.isSafeInteger(this.end)||this.at<0||this.end>=file.length||this.end<this.at){queueMicrotask(()=>this.destroy(Error('Invalid media range')));return;}
  this.first=Math.floor((file.offset+this.at)/this.torrent.pieceLength);this.last=Math.floor((file.offset+this.end)/this.torrent.pieceLength);
  this.torrent.select(this.first,this.last,true,()=>this.pump());this.selected=true;
 }
 _read(){this.wanted=true;this.pump();}
 pump(){
  if(this.destroyed||this.pending||!this.wanted)return;
  if(this.torrent.destroyed)return this.destroy(Error('Torrent removed'));
  if(this.at>this.end){this.push(null);return;}
  const absolute=this.file.offset+this.at,piece=Math.floor(absolute/this.torrent.pieceLength),offset=absolute%this.torrent.pieceLength;
  if(!this.torrent.bitfield.get(piece)){this.torrent.critical(piece,piece);return;}
  const length=Math.min(BLOCK,this.end-this.at+1,this.torrent.pieceLength-offset);
  this.pending=true;
  this.torrent.store.get(piece,{offset,length},(error,bytes)=>{
   this.pending=false;if(this.destroyed)return;if(error)return this.destroy(error);
   if(!bytes||bytes.length!==length)return this.destroy(Error('Incomplete verified media read'));
   this.at+=length;this.wanted=this.push(bytes);if(this.at>this.end)this.push(null);else if(this.wanted)this.pump();
  });
 }
 _destroy(error,cb){if(this.selected&&!this.torrent.destroyed)this.torrent.deselect(this.first,this.last,true);cb(error);}
}
function install(file){
 if(file.streamnestBounded||!partialReads(file._torrent))return;
 const original=file.createReadStream.bind(file);
 file.createReadStream=function(opts){if(!this.length)return original(opts);const stream=new BoundedStream(this,opts);this._fileStreams.add(stream);stream.once('close',()=>this._fileStreams.delete(stream));return stream;};file.streamnestBounded=true;
}
module.exports={BLOCK,BoundedStream,partialReads,install};
