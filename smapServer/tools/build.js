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
    	wfapp: '../app/webform',
    	lang_location: '..',
	bootbox: 'bootbox.min',
	moment: 'moment-with-locales.min',
	d3: 'd3.v4.min',
	toggle: 'bootstrap-toggle.min',
	bootstrap: 'bootstrap.min',
        bootstrapfileinput: 'bootstrap.file-input',
    	bootstrapcolorpicker: '../../../../smapServer/WebContent/js/libs/bootstrap-colorpicker.min',
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
	    exclude: ['jquery', 'bootstrap']
        },
        {
            name: '../reports',
        },
        {
            name: '../passwords',
        },
        {
            name: '../resources',
        },
        {
            name: '../queries',
        },
        {
            name: '../subscriptions',
        },
        {
            name: '../userTrail',
	    exclude: [ 'ol3/ol']
        }

    ]
})
