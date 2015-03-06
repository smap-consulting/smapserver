var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 


require.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: '../../../../js/libs/jquery-1.8.3.min',
    	lang_location: '..'
    },
    shim: {
    	'bootstrap.min': ['jquery'],
    	'enketo-core.min': ['jquery']
    }
});

require(['jquery', 'bootstrap.min', 'app/localise', 'enketo-core.min'], function($, bootstrap, localise, enketo) {
	localise.setlang();
 });
