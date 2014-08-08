({
    appDir: '../WebContent',
//    mainConfigFile: '../WebContent/js/index.js',
    locale: "en",
    wrapShim: false,
    baseUrl: 'js/libs',
//    optimize: 'none',
    paths: {
    	jquery: 'jquery-1.8.3.min',
    	jquery_ui: 'empty:',
    	app: '../app',
    	lang_location: '..'
    },
    dir: '../smapServer',
    modules: [
        {
            name: '../index'
        },
        {
            name: '../edit'
        },
        {
            name: '../webForms'
        }


    ]
})
