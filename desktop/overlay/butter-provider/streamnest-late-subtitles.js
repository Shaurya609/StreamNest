'use strict';
// A slow provider can finish after the startup wait has released the player.
// Populate that existing player without reopening the video or overriding a choice.
function apply(player,subtitles,preferred,vjs,manual=false){
 const tracks=player.textTracks(),known=new Set(tracks.map(t=>t.language()));
 const button=player.controlBar.children().find(c=>c.name?.()==='subtitlesButton');
 let chosen;
 for(const [language,url] of Object.entries(subtitles||{})){
  if(known.has(language)||!/^https?:\/\//i.test(url))continue;
  const track=player.addTextTrack('subtitles',language,language,{src:url,srclang:language});known.add(language);
  if(button?.menu&&vjs?.TextTrackMenuItem)button.menu.addItem(new vjs.TextTrackMenuItem(player,{track}));
  if(language===preferred)chosen=track;
 }
 button?.show();
 if(chosen&&!manual&&preferred!=='none'&&!tracks.some(t=>t.mode()===2))player.showTextTrack(chosen.id_,chosen.kind());
}
module.exports={apply};
