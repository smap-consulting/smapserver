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
    	jquery: '../../../../js/libs/jquery-2.1.1',
    	lang_location: '..'
    },
    shim: {
    	'bootstrap.min': ['jquery'],
    	'app/common': ['jquery']
    }
});

require(['jquery', 'bootstrap.min', 'app/localise', 'app/common'], function($, bootstrap, localise, common) {
	localise.setlang();
	if(typeof getVersion === "function") {
		getVersion();			// Update if the version on the server has changed
	}
	
	/*
	 * Enable self registration 
	 */
	if(isSelfRegistrationServer()) {
		$('#signup').show();
	}
 });
