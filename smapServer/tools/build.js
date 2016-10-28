({
    appDir: '../WebContent',
    locale: "en",
    waitSeconds: 0,
    wrapShim: false,
    baseUrl: 'js/libs',
    optimize: 'none',
    paths: {
    	jquery: 'jquery-2.1.1',
    	jquery_ui: 'empty:',
    	app: '../app',
    	lang_location: '..',
	bootbox: 'bootbox.min',
	moment: 'moment-with-locales.min',
	d3: 'd3.v4.min',
	toggle: 'bootstrap-toggle.min',
	bootstrap: 'bootstrap.min',
        bootstrapfileinput: 'bootstrap.file-input',
        inspinia: 'wb/inspinia',
	metismenu: 'wb/plugins/metisMenu/jquery.metisMenu',
	slimscroll: 'wb/plugins/slimscroll/jquery.slimscroll.min',
        pace: 'wb/plugins/pace/pace.min',
	icheck: 'wb/plugins/iCheck/icheck.min'
    },
    dir: '../smapServer',
    modules: [
        {
            name: '../index',
        },
        {
            name: '../edit',
        },
        {
            name: '../webFormLauncher',
        },
        {
            name: '../passwords',
        },
        {
            name: '../resources',
        },
        {
            name: '../dashboard',
        },
        {
            name: '../userTrail',
	    exclude: [ 'ol3/js/ol']
        }

    ]
})
