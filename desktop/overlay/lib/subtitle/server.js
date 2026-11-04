(function(App){
 'use strict';
 var server, paths={};
 App.SubtitlesServer={
  start:function(data,cb){
   paths={vtt:data.vtt,srt:data.srt};
   if(server&&server.listening){if(cb)cb();return;}
   server=require('http').createServer(function(req,res){
    var ext=require('path').extname(require('url').parse(req.url).pathname).slice(1),file=paths[ext];
    if(!file){res.writeHead(404);res.end();return;}
    require('fs').readFile(file,function(error,bytes){if(error){res.writeHead(404);res.end();return;}
     res.setHeader('Content-Type',(ext==='vtt'?'text/vtt':'application/x-subrip')+'; charset=utf-8');
     res.setHeader('Access-Control-Allow-Origin','*');res.end(bytes);
    });
   });
   server.on('error',function(error){win.error('Subtitle server failed',error);if(cb)cb(error);});
   server.listen(0,'127.0.0.1',function(){if(cb)cb();});
  },
  url:function(ext){var address=server&&server.address();return address?'http://127.0.0.1:'+address.port+'/data.'+ext:null;},
  stop:function(){if(server){server.close();server=null;}paths={};}
 };
})(window.App);
