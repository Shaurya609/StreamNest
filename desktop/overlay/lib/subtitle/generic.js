(function (App) {
    'use strict';

    var srt2vtt = require('srt-to-vtt');

    var self;

    var findSrt = function (input) {
        var files = fs.readdirSync(input);
        for (var f in files) {
            var stats = fs.lstatSync(path.join(input, files[f]));
            if (path.extname(files[f]) === '.srt' && stats.isFile()) {
                return path.join(input, files[f]);
            }
            if (stats.isDirectory()) {
                var found = findSrt(path.join(input, files[f]));
                if (found) {
                    return found;
                }
            }
        }
    };

    var downloadFromUrl = async function (data) {
        const ext = path.extname(data.path);
        const target = path.join(path.dirname(data.path),path.basename(data.path,ext)+'.'+data.lang.substr(0,2)+'.srt');
        const bytes = await require('./butter-provider/streamnest-subtitles').read(data.url);
        await fs.promises.writeFile(target,bytes);
        return target;
    };

    var Subtitles = Backbone.Model.extend({
        defaults: {
            id: 'generic',
            name: 'Generic'
        },
        initialize: function () {
            App.vent.on('subtitle:download', this.download);
            App.vent.on('subtitle:convert', this.convert);
            self = this;
        },
        download: function (data) {
            if (data.path && data.url) {
                var fileFolder = path.dirname(data.path);

                try {
                    mkdirp.sync(fileFolder);
                } catch (e) {
                    // Ignore EEXIST
                }

                downloadFromUrl(data).then(function (spath) {
                    App.vent.trigger('subtitle:downloaded', spath);
                }).catch(function (error) {
                    win.error('Subtitle download error:', error);
                    App.vent.trigger('subtitle:downloaded', null);
                });
            } else {
                if (Settings.subtitle_language !== 'none') {
                    win.info('No subtitles downloaded. None picked or language not available');
                    App.vent.trigger('notification:show', new App.Model.Notification({
                        title: i18n.__('No subtitles found'),
                        body: i18n.__('Try again later or drop a subtitle in the player'),
                        showRestart: false,
                        type: 'warning',
                        autoclose: true
                    }));
                }
                App.vent.trigger('subtitle:downloaded', null);
            }
        },
        convert: function (data, cb) { // Converts .srt's to .vtt's
            try {
                const srtPath = data.path;
                const vttPath = srtPath.replace('.srt', '.vtt');
                const source = fs.createReadStream(srtPath), converter = srt2vtt(), output = fs.createWriteStream(vttPath);
                let completed = false;
                function done(error) { if(completed)return;completed=true;cb(error,error?null:{vtt:vttPath,srt:srtPath,encoding:'utf8'}); }
                source.on('error',done);converter.on('error',done);output.on('error',done);
                output.on('finish',function(){done(null);});
                source.pipe(converter).pipe(output);
            } catch (e) {
                cb(e, null);
            }
        },
        // Handles charset encoding
        decode: function (dataBuff, language, callback) {
            var targetEncodingCharset = 'utf8';

            var charset = charsetDetect.detect(dataBuff);
            var detectedEncoding = charset.encoding;
            // Do we need decoding?
            if (detectedEncoding.toLowerCase().replace('-', '') === targetEncodingCharset) {
                callback(dataBuff.toString('utf8'));
                // We do
            } else {
                var langInfo = App.Localization.langcodes[language] || {};
                if (langInfo.encoding !== undefined && langInfo.encoding.indexOf(detectedEncoding) < 0) {
                    // The detected encoding was unexepected to the language, so we'll use the most common
                    // encoding for that language instead.
                    detectedEncoding = langInfo.encoding[0];
                }
                dataBuff = iconv.encode(iconv.decode(dataBuff, detectedEncoding), targetEncodingCharset);
                callback(dataBuff.toString('utf8'));
            }
        }

    });

    App.Subtitles = {
        Generic: new Subtitles()
    };
})(window.App);
