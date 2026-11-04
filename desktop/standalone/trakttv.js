(function(App) {
  'use strict';
  // No upstream application's shared API credentials are shipped or reused.
  function LocalMetadata() {
    this.authenticated = false;
    this.client = {
      images:{get:()=>Promise.resolve({})},
      matcher:{match:()=>Promise.reject(new Error('Automatic file identification is not configured'))}
    };
  }
  LocalMetadata.prototype = {
    config:{name:'Trakttv'},
    reAuthenticate:()=>Promise.resolve(false), disconnect:()=>Promise.resolve(),
    scrobble:()=>Promise.resolve(), getPlayback:()=>Promise.resolve(0),
    syncAll:()=>Promise.resolve(), getImages:attrs=>Promise.resolve(attrs),
    getMetadata:()=>Promise.reject(new Error('Additional metadata integration is not configured')),
    authenticate:function() {
      App.vent.trigger('notification:show', new App.Model.Notification({title:'Trakt is not configured', body:'This preview uses catalog metadata and local watch history. Trakt account sync will need a StreamNest application registration.', autoclose:true, showClose:true, type:'info'}));
      return Promise.resolve(false);
    }
  };
  App.Providers.Trakttv = LocalMetadata;
  App.Providers.install(LocalMetadata);
})(window.App);
