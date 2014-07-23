requirejs.config({
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min',
    	lang_location: '..'
    },
    shim: {
    	'foundation.min': ['jquery']
    }
});

require(['jquery', 'foundation.min', 'app/localise'], function($, foundation, localise) {
	$(document).foundation();
	localise.setlang();
 });
