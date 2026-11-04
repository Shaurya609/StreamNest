'use strict';

const TVApi = require('./tv');
const sanitize = require('butter-sanitize');
const i18n = require('i18n');

class AnimeApi extends TVApi {

  extractIds(items) { return items.results.map(item=>item.imdb_id); }

  fetch(filters) {
    if(filters.language==='latest')return require('./streamnest-releases').list(filters).then(result=>({results:result.rows.map(o=>Object.assign(require('./streamnest-api').movie(o,false,false),{animeRelease:true,animeMovie:true})),hasMore:result.hasMore}));
    if(filters.language==='films'&&!['All','1080p','2160p'].includes(filters.type))filters=Object.assign({},filters,{type:'All'});
    if(filters.language==='films')return require('./streamnest-paged').list(filters,'all',true).then(result=>({results:result.rows.map(o=>Object.assign(require('./streamnest-api').movie(o,false,false),{freshMovie:true,animeMovie:true,contextLocale:'ja',exist_translations:['ja']})),hasMore:result.hasMore}));
    return require('./streamnest-anime').list(filters);
  }

  detail(id,old) { if(old.animeRelease)return Promise.resolve(old);return require('./streamnest-api').detail(id,old,!old.animeMovie); }


  formatFiltersFromServer(sorters, data)
  {
    let filters = {
      genres: {},
      sorters: {},
    };
    for (const genre of sorters) {
      filters.sorters[genre] = i18n.__(genre.capitalizeEach());
    }

    filters.genres = {
      'All': 'Anime',
    };

    return filters;
  }

  filters() {
    return require('./streamnest-api').filters(true).then(f=>Object.assign(f,{languages:{all:'Anime series',films:'Anime films',latest:'Latest episodes'}}));
  }
}

AnimeApi.prototype.config = {
  name: 'AnimeApi',
  uniqueId: 'tvdb_id',
  tabName: 'Anime',
  type: 'anime',
  metadata: 'trakttv:show-metadata'
};

module.exports = AnimeApi;
