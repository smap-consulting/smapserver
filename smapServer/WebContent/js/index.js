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

require(['jquery', 'bootstrap.min', 'app/localise', 'app/common','app/globals'], 
		function($, bootstrap, localise, common, globals) {
	
	var params,
		pArray = [],
		param = [],
		i,
		loggedin=false,
		androidVersion;
	
	/*
	 * If the user is logged in then get their details
	 */
	params = location.search.substr(location.search.indexOf("?") + 1)
	pArray = params.split("&");
	for (i = 0; i < pArray.length; i++) {
		param = pArray[i].split("=");
		if(param.length > 1) {
			if ( param[0] === "loggedin" && param[1] === "yes" ) {
				getLoggedInUser(undefined, false, false, undefined, false, false);
				loggedin = true;
			} 
		}
	}
	
	/*
	 * If the user is not logged in then enable the login button and disable other menus
	 * which depend on their authorisation level
	 */
	if(loggedin) {
		setupUserProfile();
		localise.setlang();
		$('.loggedin').show();
		$('.notloggedin').hide();
	} else {
		setCustomMainLogo();
		$('.restrict_role').hide();
		$('.notloggedin').show();
		$('.loggedin').hide();
	}
	
	/*
	 * Enable self registration 
	 */
	if(isSelfRegistrationServer() && !loggedin) {
		$('#signup').show();
	} else {
		$('#signup').hide();
	}
	
	/*
	 * Add logout function
	 */
	$('#logout').click(function(){
		logout();
	});

	/*
	 * Add links to download fieldTask
	 */
	androidVersion = parseFloat(getAndroidVersion());
	if(androidVersion == 0 || androidVersion >= 4.1) {		// Default to downloading the new APK
		$('#ft').attr("href", "fieldTask.apk");
	} else {
		$('#ft').attr("href", "fieldTaskPreJellyBean.apk");
	}

 });

/*
 * Get the android version - return 0.0 if it cannot be determined
 */
function getAndroidVersion() {
    var ua = (navigator.userAgent).toLowerCase();
    var match = ua.match(/android\s([0-9\.]*)/);
    return match ? match[1] : 0;
};
