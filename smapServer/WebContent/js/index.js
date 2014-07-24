require.config({
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min',
    	lang_location: '..'
    },
    shim: {
    	'foundation.min': ['jquery'],
       	'jquery_ui': ['jquery'],
    	'responsivemobilemenu': ['jquery'],
    	'app/common': ['jquery']
    }
});

require(['jquery', 'foundation.min', 'app/localise'], function($, foundation, localise) {
	$(document).foundation();
	localise.setlang();
 });
