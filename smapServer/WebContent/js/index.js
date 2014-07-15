requirejs.config({
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min'
    }
});

require(['jquery', 'foundation.min'], function($, foundation) {
	$(document).foundation();
 });
