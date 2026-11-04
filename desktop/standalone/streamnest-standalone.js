'use strict';
const fs = require('fs'), path = require('path'), os = require('os');
const project = 'https://github.com/Shaurya609/StreamNest';
function profileRoot() {
  const root = path.join(process.env.LOCALAPPDATA || path.join(os.homedir(), '.local', 'share'), 'StreamNest', 'Profile');
  fs.mkdirSync(path.join(root, 'data'), {recursive:true});
  return root;
}
function configure(settings) {
  const cache = path.join(path.dirname(profileRoot()), 'Cache');
  fs.mkdirSync(cache, {recursive:true});
  Object.assign(settings, {
    projectName:'StreamNest', projectUrl:project + '/releases', sourceUrl:project + '/',
    issuesUrl:project + '/issues', changelogUrl:project + '/releases', commitUrl:project + '/commit',
    projectBlog:'', projectForum:'', projectForum2:'', statusUrl:'', dht:'', dhtInfo:'',
    dhtEnable:false, updateNotification:true, tmpLocation:cache, downloadsLocation:cache,
    databaseLocation:path.join(profileRoot(), 'data'), httpApiEnabled:false,
    httpApiUsername:'', httpApiPassword:'', updateEndpoint:{url:'', proxies:[]},
    language:'en', contentLanguage:'en', contentLangOnly:false, subtitle_language:'en',
    activateVpn:false, traktStatus:false, traktSyncOnStart:false, traktPlayback:false,
    trakttv:{client_id:'', client_secret:''}, fanart:{api_key:''}, tvdb:{api_key:''}, tmdb:{api_key:''}
  });
  settings.providers.movie.uri = ['MovieApi?apiURL=https://api.apiabcd.com/'];
  settings.providers.tvshow.uri = ['TVApi?apiURL=https://api.apiabcd.com/'];
  settings.providers.anime.uri = ['AnimeApi?apiURL=https://api.apiabcd.com/'];
  settings.customServers = {movie:['https://api.apiabcd.com/'], tvshow:['https://api.apiabcd.com/'], anime:['https://api.apiabcd.com/']};
  // A smoke run uses an isolated throwaway profile and has no first-run interaction.
  if (typeof nw !== 'undefined' && !!process.env.STREAMNEST_SMOKE_ROOT) settings.disclaimerAccepted = 1;
  return settings;
}
module.exports = {profileRoot, configure, project};
