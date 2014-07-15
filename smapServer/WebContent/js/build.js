({
    appDir: '../',
    baseUrl: 'js/libs',
    paths: {
    	jquery: 'jquery-1.8.3.min',
    	jquery_ui: 'jquery-ui-1.10.3.custom.min',
    	app: '../app',
    	main: '..'
    },
    dir: '../../smapServer',
    modules: [
        //First set up the common build layer.
        {
            //module names are relative to baseUrl/paths config
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
