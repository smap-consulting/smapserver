requirejs.config({
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min'
    },
    shim: {
    	'foundation.min': ['jquery'],
    	'app/localise': ['jquery'],
    }
});

require(['jquery', 'foundation.min', 'app/localise'], function($, foundation, localise) {
	$(document).foundation();
	localise.setlang();
 });
