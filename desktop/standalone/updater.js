(function(App) {
  'use strict';
  // App updates are manual, from this project's standalone releases. Catalog updates
  // use the separate signed snapshot channel; legacy DHT configuration is disabled.
  const releases = 'https://github.com/Shaurya609/StreamNest/releases';
  App.Updater = {
    onlyNotification: function(event) {
      if (event) nw.Shell.openExternal(releases);
      return Promise.resolve();
    },
    updateDHT: function() { return Promise.resolve(); },
    updateDHTOld: function() { return Promise.resolve(); },
    updateDHTSettings: function() { return Promise.resolve(); }
  };
})(window.App);
