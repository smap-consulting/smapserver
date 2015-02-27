({
    appDir: '../WebContent',
    locale: "en",
    waitSeconds: 0,
    wrapShim: false,
    baseUrl: 'js/libs',
    optimize: 'none',
    paths: {
    	jquery: 'jquery-1.8.3.min',
    	jquery_ui: 'empty:',
    	app: '../app',
    	lang_location: '..',
	bootbox: 'bootbox.min'
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
            name: '../webForms',
        },
        {
            name: '../passwords',
        },
        {
            name: '../userTrail',
	    exclude: [ 'ol3/js/ol']
        }

    ]
})
