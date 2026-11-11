 'use strict';
function install(App,$){
 // Required Node modules can see NW's background document. Use the renderer owning jQuery nodes.
 const document=$('<div>')[0].ownerDocument;
 const H=require('./streamnest-home'),P=require('./streamnest-progress'),S=require('./streamnest-home-sync'),Art=require('./streamnest-artwork'),Search=require('./streamnest-home-search');
 const shared=(pool,history)=>{const code=require('./streamnest-sync').config().code;return S.apply(H.build(pool,history),pool,history,code?S.cached(code):null);};
 if(document.getElementById('sn-home-style'))return;
 const style=document.createElement('style');style.id='sn-home-style';style.textContent=`
 .sn-home{position:fixed;top:68px;right:0;bottom:0;left:0;overflow:auto;overflow-x:hidden;background:#14161c;color:#f4f5fa;padding:24px 32px 60px;box-sizing:border-box;}
 #main-window .sn-home .sn-home-search{display:flex;gap:10px;max-width:760px;margin:0 0 18px;align-items:center;}#main-window .sn-home .sn-home-search input{flex:1;min-width:0;height:44px;padding:10px 12px;background:#202735;color:#fff;border:1px solid #69788f;border-radius:6px;font:16px sans-serif;}#main-window .sn-home .sn-home-search button,#main-window .sn-home .sn-search-pages button{height:44px;min-width:70px;background:#303f56;color:#fff;border:1px solid #69788f;border-radius:6px;padding:8px 14px;}#main-window .sn-home .sn-home-search :focus,#main-window .sn-home .sn-search-pages button:focus{outline:2px solid #48a9ff;outline-offset:2px;}.sn-home .sn-search-pages{display:flex;gap:12px;margin:16px 0;}.sn-home .sn-search-status{color:#b1b7c5;}
 .sn-home h1{display:block;color:#f4f5fa;font-size:30px;font-weight:600;margin:12px 0 8px;}.sn-home .sn-home-note{color:#a3a7b3;margin:0 0 24px;}
 .sn-home::-webkit-scrollbar{width:8px;}.sn-home .sn-rail::-webkit-scrollbar{height:6px;}.sn-home::-webkit-scrollbar-track,.sn-home .sn-rail::-webkit-scrollbar-track{background:transparent;}.sn-home::-webkit-scrollbar-thumb,.sn-home .sn-rail::-webkit-scrollbar-thumb{background:#485268;border-radius:6px;}
 .sn-home h2{display:block;color:#f4f5fa;font-size:20px;margin:22px 0 12px;}.sn-home .sn-rail{display:flex;gap:14px;overflow-x:auto;padding:5px 4px 16px;}
 #main-window .sn-home .sn-card{appearance:none;display:block;height:auto;min-height:0;margin:0;box-shadow:none;white-space:normal;line-height:normal;flex:0 0 148px;width:148px;background:transparent;border:2px solid transparent;border-radius:7px;color:white;padding:3px;cursor:pointer;text-align:left;outline:none;}
 #main-window .sn-home .sn-card:focus,#main-window .sn-home .sn-card:hover{border-color:#48a9ff;background:#242b3b;}.sn-home .sn-card img{width:138px;height:207px;object-fit:cover;background:#252935;border-radius:4px;display:block;}
 .sn-home .sn-label{font:500 14px sans-serif;display:block;line-height:19px;height:38px;overflow:hidden;margin:8px 0 3px;}.sn-home .sn-meta{font:12px sans-serif;color:#b1b7c5;display:block;min-height:30px;line-height:15px;}
 .sn-home .sn-poster{position:relative;width:138px;height:207px;display:block;background:#252935;border-radius:4px;overflow:hidden;}.sn-home .sn-art-missing{width:100%;height:100%;display:flex;align-items:center;justify-content:center;text-align:center;padding:12px;box-sizing:border-box;font:12px/18px sans-serif;color:#aeb8ca;}.sn-home [hidden]{display:none!important;}.sn-home .sn-resume-track{display:block;width:100%;height:5px;background:#3b465b;border:0;border-radius:3px;overflow:hidden;margin:5px 0 7px;}.sn-home .sn-resume-fill{display:block;height:100%;background:#3da8fa;border-radius:3px;}.sn-home button.sn-refresh{border:1px solid #596477;border-radius:4px;background:#232a36;color:white;padding:9px 14px;cursor:pointer;}
 `;document.head.appendChild(style);
 App.View.HomeBrowser=App.View.MovieBrowser.extend({
  onFilterChange(){},
  onAttach(){
   App.currentview='home';
   this.bar=new App.View.FilterBar({model:this.filter});this.showChildView('FilterBar',this.bar);
   const decorate=()=>{this.$('#nav-filters,.right .search').hide();this.$('.source').removeClass('active');this.$('#filterbar-home').addClass('active');};this.listenTo(this.bar,'render',decorate);decorate();
   this.host=$('<main class="sn-home" aria-label="Home"></main>');this.$('.list-region').empty().append(this.host);App.currentview='home';
   this.searchForm=$('<form class="sn-home-search" role="search">').appendTo(this.host);this.searchInput=$('<input type="search" maxlength="200" aria-label="Search movies, series and anime" placeholder="Search movies, series and anime">').appendTo(this.searchForm);$('<button type="submit" aria-label="Search all titles">').text('Search').appendTo(this.searchForm);$('<button type="button">').text('Clear').on('click',()=>this.clearSearch()).appendTo(this.searchForm);this.results=$('<section class="sn-home-results">').appendTo(this.host);this.searchForm.on('submit',e=>{e.preventDefault();this.beginSearch(this.searchInput.val(),1);});this.searchInput.on('keydown',e=>{if(e.key==='Escape'){e.preventDefault();e.stopPropagation();this.clearSearch();}});

   this.layoutHome=()=>this.host.css('top',Math.max(0,this.bar.el.getBoundingClientRect().bottom)+'px');this.layoutHome();document.defaultView.addEventListener('resize',this.layoutHome);if(document.defaultView.ResizeObserver){this.homeResize=new document.defaultView.ResizeObserver(this.layoutHome);this.homeResize.observe(this.bar.el);}
   this.lastHomeSync=Date.now();this.refresh();this.timer=setInterval(()=>{if(this.searchQuery)return;if(['player','movie-detail','settings-container'].some(id=>{const node=document.getElementById(id);return node&&node.children.length;}))return;if(Date.now()-this.lastHomeSync>=60000){this.lastHomeSync=Date.now();require('./streamnest-sync').request(true).then(()=>{if(!this.isDestroyed()&&(JSON.stringify(P.records())!==this.signature||this.libraryRevision!==H.revision))this.refresh();});}const signature=JSON.stringify(P.records());if(signature!==this.signature||this.libraryRevision!==H.revision)this.refresh();},3000);
  },
  refresh(){
   if(this.searchQuery)return;
   const history=P.records(),pool=H.load();this.signature=JSON.stringify(history);this.libraryRevision=H.revision;this.paint(shared(pool,history));const generation=this.homeGeneration=(this.homeGeneration||0)+1;
   require('./streamnest-sync').request(true).then(()=>{if(!this.isDestroyed()&&(JSON.stringify(P.records())!==this.signature||this.libraryRevision!==H.revision))this.refresh();});
   const before=pool.slice();H.enrich(pool,history).then(enriched=>{if((enriched.length!==before.length||enriched.some((row,n)=>row!==before[n]))&&!this.isDestroyed()&&generation===this.homeGeneration){this.libraryRevision=H.revision;this.paint(shared(enriched,history));}});
  },
  paint(rows){
   if(this.artworkCleanup)this.artworkCleanup.forEach(stop=>stop());this.artworkCleanup=[];const scroll=this.host.scrollTop(),focused=this.host[0].contains(document.activeElement)?document.activeElement:null,focusId=focused&&focused.getAttribute('data-home-key'),offsets=new Map([...this.host[0].querySelectorAll('.sn-rail')].map(r=>[r.getAttribute('data-home-row'),r.scrollLeft]));this.results.empty();$('<h1>').text(this.searchQuery?'Search results for “'+this.searchQuery+'”':'Something for your next watch').appendTo(this.results);$('<p class="sn-home-note">').text(this.searchQuery?'Movies, series and anime':'Continue where you left off, or find your next favorite.').appendTo(this.results);
   for(const section of rows){if(!section.cards.length&&section.name!=='Continue Watching')continue;$('<h2>').text(section.name).appendTo(this.results);
    if(!section.cards.length){$('<p class="sn-home-note">').text('Start watching a title. Your place will appear here automatically.').appendTo(this.results);continue;}
    const rail=$('<div class="sn-rail">').attr('data-home-row',section.name).appendTo(this.results);
    for(const entry of section.cards){const t=entry.title,button=$('<button type="button" class="sn-card">').appendTo(rail),art=$('<span class="sn-poster">').appendTo(button),image=$('<img loading="lazy" alt="">').appendTo(art),placeholder=$('<span class="sn-art-missing" hidden>').text('Artwork unavailable').appendTo(art);this.artworkCleanup.push(Art.attach(t,image[0],placeholder[0]));
     $('<span class="sn-label">').text(t.title).appendTo(button);let caption=t.kind==='movies'?'Movie':t.kind==='animes'?'Anime':'Series';
     if(entry.progress){const p=entry.progress,parts=entry.historyKey.split(':');if(parts.length===4)caption='S'+parts[2]+' · E'+parts[3];const seconds=Math.floor(p.positionMs/1000);if(p.nextEpisode)caption='Up next · '+caption;else caption+=' · Resume at '+Math.floor(seconds/60)+':'+String(seconds%60).padStart(2,'0');if(!p.nextEpisode){const ratio=Art.fraction(p),track=$('<span class="sn-resume-track" role="progressbar" aria-valuemin="0" aria-valuemax="100">').attr('aria-valuenow',ratio*100).attr('aria-label','Watched progress').appendTo(button);$('<span class="sn-resume-fill" aria-hidden="true">').css({width:(ratio*100)+'%',minWidth:ratio>0?'2px':'0'}).appendTo(track);}}
     $('<span class="sn-meta">').text(caption).appendTo(button);button.attr('data-home-key',section.name+':'+H.id(t));button.attr('aria-label',t.title+' · '+caption);button.on('click',()=>this.open(t,button));
     button.on('keydown',e=>{let next;if(e.key==='ArrowRight'||e.key==='ArrowLeft')next=e.key==='ArrowRight'?button.next():button.prev();else if(e.key==='ArrowDown'||e.key==='ArrowUp'){const rails=this.host.find('.sn-rail'),at=rails.index(rail)+(e.key==='ArrowDown'?1:-1);if(at>=0&&at<rails.length){const cards=rails.eq(at).find('.sn-card');next=cards.eq(Math.min(button.index(),cards.length-1));}}if(next){e.preventDefault();e.stopPropagation();if(next.length){next[0].focus({preventScroll:true});next[0].scrollIntoView({block:'nearest',inline:'nearest'});}}});
    }
   }for(const rail of this.host[0].querySelectorAll('.sn-rail'))rail.scrollLeft=offsets.get(rail.getAttribute('data-home-row'))||0;this.host.scrollTop(scroll);if(focusId){const target=[...this.host[0].querySelectorAll('.sn-card')].find(b=>b.getAttribute('data-home-key')===focusId);if(target)target.focus({preventScroll:true});}
  },
  clearSearch(){this.searchGeneration=(this.searchGeneration||0)+1;this.searchQuery='';this.searchInput.val('');this.refresh();this.searchInput.focus();},
  beginSearch(raw,page=1){
   const q=String(raw||'').trim().slice(0,200);if(!q)return this.clearSearch();const same=q===this.searchQuery;this.homeGeneration=(this.homeGeneration||0)+1;const generation=this.searchGeneration=(this.searchGeneration||0)+1;this.searchQuery=q;this.searchPage=page;this.searchInput.val(q);if(!same){try{this.searchCatalog=H.load();}catch(e){this.searchCatalog=[];}}this.searchPending=3;this.searchFailures=0;this.searchMore={};this.drawSearch();
   Search.discover(q,page,result=>{if(this.isDestroyed()||generation!==this.searchGeneration)return;this.searchCatalog=this.searchCatalog.concat(result.rows||[]);this.searchPending--;if(result.failed)this.searchFailures++;this.searchMore[result.kind]=result.hasMore;this.drawSearch();});
  },
  drawSearch(){
   const result=Search.select(this.searchCatalog,this.searchQuery,this.searchPage);this.paint(result.rows);const count=result.rows.reduce((n,r)=>n+r.cards.length,0);$('<p class="sn-search-status" role="status">').text(this.searchPending?'Searching movies, series and anime…':this.searchFailures?'Some catalog services are unavailable. Showing available matches.':count?'Search results':'No matching titles found.').appendTo(this.results);const pages=$('<div class="sn-search-pages">').appendTo(this.results);if(this.searchPage>1)$('<button type="button">').text('Previous results').prop('disabled',this.searchPending>0).on('click',()=>this.beginSearch(this.searchQuery,this.searchPage-1)).appendTo(pages);if(result.hasMore||Object.values(this.searchMore).some(Boolean))$('<button type="button">').text('Next results').prop('disabled',this.searchPending>0).on('click',()=>this.beginSearch(this.searchQuery,this.searchPage+1)).appendTo(pages);
  },
  async open(raw,button){
   if(this.opening)return;this.opening=true;button.attr('aria-busy','true');
   try{const show=raw.kind!=='movies',provider=App.Config.getProviderForType(show?(raw.kind==='animes'?'anime':'tvshow'):'movie')[0];if(!provider)throw Error('Provider unavailable');
    const attrs=Object.assign(require('./streamnest-api').movie({...raw,imdb:H.id(raw),poster_big:raw.poster_big||raw.large_cover_image||raw.poster||'',description:raw.description||raw.description_full||''},show,raw.language==='hi'),{freshMovie:!show,animeSeries:raw.kind==='animes',malId:raw.malId,providers:{torrent:provider}});
    const detail=await provider.detail(H.id(raw),attrs);if(this.isDestroyed())return;const model=new (show?App.Model.Show:App.Model.Movie)(Object.assign(attrs,detail));H.remember(show?{...raw,homeEpisodeOrder:H.episodeOrder(detail.episodes||[]),homeEpisodeOrderAt:Date.now()}:raw,raw.kind);App.vent.trigger(show?'show:showDetail':'movie:showDetail',model);
   }catch(e){const note=this.host.find('.sn-home-error');note.remove();$('<p class="sn-home-error" role="alert">').text('Could not open this title. '+e.message).prependTo(this.host);}
   finally{this.opening=false;button.removeAttr('aria-busy');}
  },
  onBeforeDestroy(){this.searchGeneration=(this.searchGeneration||0)+1;if(this.artworkCleanup)this.artworkCleanup.forEach(stop=>stop());clearInterval(this.timer);document.defaultView.removeEventListener('resize',this.layoutHome);if(this.homeResize)this.homeResize.disconnect();this.homeGeneration=(this.homeGeneration||0)+1;}
 });
}
module.exports={install};
