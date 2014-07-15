requirejs.config({
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min'
    },
    shim: {
        'mustache': {
            exports: 'Mustache'
        }
    }
});

