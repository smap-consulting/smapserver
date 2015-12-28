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
    	jquery: [
      	       '//ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min',
      	       '../../../../js/libs/jquery-1.8.3.min'
      	       ],
    	lang_location: '..'
    },
    shim: {
    	'bootstrap.min': ['jquery'],
    	'app/common': ['jquery']
    }
});

require(['jquery', 'bootstrap.min', 'app/localise', 'app/common'], function($, bootstrap, localise, common) {
	localise.setlang();
	
	/*
	 * Enable self registration 
	 */
	if(isSelfRegistrationServer()) {
		$('#signup').show();
	}
 });
