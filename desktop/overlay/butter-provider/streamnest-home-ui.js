 'use strict';
function install(App,$){
 const H=require('./streamnest-home'),P=require('./streamnest-progress'),S=require('./streamnest-home-sync');
 const shared=(pool,history)=>{const code=require('./streamnest-sync').config().code;return S.apply(H.build(pool,history),pool,history,code?S.cached(code):null);};
 if(document.getElementById('sn-home-style'))return;
 const style=document.createElement('style');style.id='sn-home-style';style.textContent=`
 .sn-home{height:100%;overflow:auto;background:#14161c;color:#f4f5fa;padding:24px 32px 60px;box-sizing:border-box;}
 .sn-home h1{font-size:30px;font-weight:600;margin:12px 0 8px;}.sn-home .sn-home-note{color:#a3a7b3;margin:0 0 24px;}
 .sn-home h2{font-size:20px;margin:22px 0 12px;}.sn-home .sn-rail{display:flex;gap:14px;overflow-x:auto;padding:5px 4px 16px;}
 .sn-home .sn-card{flex:0 0 148px;width:148px;background:transparent;border:2px solid transparent;border-radius:7px;color:white;padding:3px;cursor:pointer;text-align:left;outline:none;}
 .sn-home .sn-card:focus,.sn-home .sn-card:hover{border-color:#48a9ff;background:#242b3b;}.sn-home .sn-card img{width:138px;height:207px;object-fit:cover;background:#252935;border-radius:4px;display:block;}
 .sn-home .sn-label{font:500 14px sans-serif;display:block;line-height:19px;height:38px;overflow:hidden;margin:8px 0 3px;}.sn-home .sn-meta{font:12px sans-serif;color:#b1b7c5;display:block;min-height:30px;line-height:15px;}
 .sn-home progress{width:100%;height:4px;display:block;accent-color:#3da8fa;}.sn-home button.sn-refresh{border:1px solid #596477;border-radius:4px;background:#232a36;color:white;padding:9px 14px;cursor:pointer;}
 `;document.head.appendChild(style);
 App.View.HomeBrowser=App.View.MovieBrowser.extend({
  onFilterChange(){},
  onAttach(){
   App.currentview='home';
   this.bar=new App.View.FilterBar({model:this.filter});this.showChildView('FilterBar',this.bar);
   const decorate=()=>{this.$('#nav-filters,.right .search').hide();this.$('.source').removeClass('active');this.$('#filterbar-home').addClass('active');};this.listenTo(this.bar,'render',decorate);decorate();
   this.host=$('<main class="sn-home" aria-label="Home"></main>');this.$('.list-region').empty().append(this.host);App.currentview='home';
   this.lastHomeSync=Date.now();this.refresh();this.timer=setInterval(()=>{if(['player','movie-detail','settings-container'].some(id=>{const node=document.getElementById(id);return node&&node.children.length;}))return;if(Date.now()-this.lastHomeSync>=60000){this.lastHomeSync=Date.now();require('./streamnest-sync').request(true).then(()=>{if(!this.isDestroyed()&&(JSON.stringify(P.records())!==this.signature||this.libraryRevision!==H.revision))this.refresh();});}const signature=JSON.stringify(P.records());if(signature!==this.signature||this.libraryRevision!==H.revision)this.refresh();},3000);
  },
  refresh(){
   const history=P.records(),pool=H.load();this.signature=JSON.stringify(history);this.libraryRevision=H.revision;this.paint(shared(pool,history));const generation=this.homeGeneration=(this.homeGeneration||0)+1;
   require('./streamnest-sync').request(true).then(()=>{if(!this.isDestroyed()&&(JSON.stringify(P.records())!==this.signature||this.libraryRevision!==H.revision))this.refresh();});
   const before=pool.slice();H.enrich(pool,history).then(enriched=>{if((enriched.length!==before.length||enriched.some((row,n)=>row!==before[n]))&&!this.isDestroyed()&&generation===this.homeGeneration){this.libraryRevision=H.revision;this.paint(shared(enriched,history));}});
  },
  paint(rows){
   const scroll=this.host.scrollTop();this.host.empty();$('<h1>').text('Something for your next watch').appendTo(this.host);$('<p class="sn-home-note">').text('Continue where you left off, or find your next favorite.').appendTo(this.host);
   for(const section of rows){if(!section.cards.length&&section.name!=='Continue Watching')continue;$('<h2>').text(section.name).appendTo(this.host);
    if(!section.cards.length){$('<p class="sn-home-note">').text('Start watching a title. Your place will appear here automatically.').appendTo(this.host);continue;}
    const rail=$('<div class="sn-rail">').appendTo(this.host);
    for(const entry of section.cards){const t=entry.title,button=$('<button type="button" class="sn-card">').appendTo(rail),image=$('<img loading="lazy" alt="">').appendTo(button);const poster=t.poster_big||t.large_cover_image||t.poster||t.images&&t.images.poster||'';if(/^https:\/\//i.test(poster))image.attr('src',poster);
     $('<span class="sn-label">').text(t.title).appendTo(button);let caption=t.kind==='movies'?'Movie':t.kind==='animes'?'Anime':'Series';
     if(entry.progress){const p=entry.progress,parts=entry.historyKey.split(':');if(parts.length===4)caption='S'+parts[2]+' · E'+parts[3];const seconds=Math.floor(p.positionMs/1000);if(p.nextEpisode)caption='Up next · '+caption;else caption+=' · Resume at '+Math.floor(seconds/60)+':'+String(seconds%60).padStart(2,'0');$('<progress max="1000">').attr('value',p.durationMs>0?Math.min(1000,p.positionMs*1000/p.durationMs):0).appendTo(button);}
     $('<span class="sn-meta">').text(caption).appendTo(button);button.attr('aria-label',t.title+' · '+caption);button.on('click',()=>this.open(t,button));
     button.on('keydown',e=>{if(e.key==='ArrowRight'||e.key==='ArrowLeft'){e.preventDefault();const next=e.key==='ArrowRight'?button.next():button.prev();if(next.length)next.focus();}});
    }
   }this.host.scrollTop(scroll);
  },
  async open(raw,button){
   if(this.opening)return;this.opening=true;button.attr('aria-busy','true');
   try{const show=raw.kind!=='movies',provider=App.Config.getProviderForType(show?(raw.kind==='animes'?'anime':'tvshow'):'movie')[0];if(!provider)throw Error('Provider unavailable');
    const attrs=Object.assign(require('./streamnest-api').movie({...raw,imdb:H.id(raw),poster_big:raw.poster_big||raw.large_cover_image||raw.poster||'',description:raw.description||raw.description_full||''},show,raw.language==='hi'),{freshMovie:!show,animeSeries:raw.kind==='animes',malId:raw.malId,providers:{torrent:provider}});
    const detail=await provider.detail(H.id(raw),attrs);if(this.isDestroyed())return;const model=new (show?App.Model.Show:App.Model.Movie)(Object.assign(attrs,detail));H.remember(show?{...raw,homeEpisodeOrder:H.episodeOrder(detail.episodes||[]),homeEpisodeOrderAt:Date.now()}:raw,raw.kind);App.vent.trigger(show?'show:showDetail':'movie:showDetail',model);
   }catch(e){const note=this.host.find('.sn-home-error');note.remove();$('<p class="sn-home-error" role="alert">').text('Could not open this title. '+e.message).prependTo(this.host);}
   finally{this.opening=false;button.removeAttr('aria-busy');}
  },
  onBeforeDestroy(){clearInterval(this.timer);this.homeGeneration=(this.homeGeneration||0)+1;}
 });
}
module.exports={install};
