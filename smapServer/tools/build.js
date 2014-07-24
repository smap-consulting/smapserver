({
    appDir: '../WebContent',
    mainConfigFile: '../WebContent/js/index.js',
    wrapShim: false,
    baseUrl: 'js/libs',
    optimize: 'none',
    paths: {
    	jquery: 'jquery-1.8.3.min',
    	jquery_ui: 'jquery-ui-1.10.3.custom.min',
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
