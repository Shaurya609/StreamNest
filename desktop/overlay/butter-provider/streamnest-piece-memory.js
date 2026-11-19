'use strict';
// Pinned torrent-piece retains stable block views until flush. When those views
// already cover one contiguous backing buffer, reuse it instead of copying a
// second whole piece. Fragmented blocks keep the upstream concat/hash path.
function contiguous(piece){
 if(piece.length<1024*1024)return null;
 const blocks=piece._buffer;if(!blocks||piece._buffered!==piece._chunks||!blocks.length)return null;
 const first=blocks[0];if(!Buffer.isBuffer(first)||first.buffer.byteLength>piece.length+65536)return null;
 let offset=first.byteOffset,total=0;
 for(const block of blocks){if(!Buffer.isBuffer(block)||block.buffer!==first.buffer||block.byteOffset!==offset)return null;total+=block.length;offset+=block.length;}
 return total===piece.length?Buffer.from(first.buffer,first.byteOffset,total):null;
}
function install(Piece){
 if(!Piece?.prototype||typeof Piece.prototype.flush!=='function')return;
 if(Piece.prototype.streamnestZeroCopy)return;
 const flush=Piece.prototype.flush;
 Piece.prototype.flush=function(){const bytes=contiguous(this);if(!bytes)return flush.call(this);this._buffer=null;this._cancellations=null;this.sources=null;this._flushed=true;return bytes;};
 Piece.prototype.streamnestZeroCopy=true;
}
module.exports={install,contiguous};
