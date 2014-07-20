({
    appDir: '../WebContent',
    baseUrl: 'js/libs',
    paths: {
    	jquery: 'jquery-1.8.3.min',
    	jquery_ui: 'jquery-ui-1.10.3.custom.min',
    	app: '../app',
    	lang_location: '../'
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
