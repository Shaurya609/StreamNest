'use strict';
function assess(video,wanted='en'){
 if(!video||!video.videoWidth||video.readyState<2)return '';
 const tracks=Array.from(video.audioTracks||[]),bytes=video.webkitAudioDecodedByteCount;
 if(video.audioTracks&&tracks.length===0&&bytes===0)return 'missing';
 if(tracks.length&& !require('./streamnest-media-tracks').choose(tracks,wanted)&&tracks.some(t=>t.language||t.label))return 'language';
 return '';
}
function message(state,wanted='en'){const name=wanted==='hi'?'Hindi':'English';return state==='missing'?'This source has no audio this player can decode. Try another source or open it in an external player.':name+' audio is not available in this source. You can use the original audio with subtitles or try another source.';}
module.exports={assess,message};
