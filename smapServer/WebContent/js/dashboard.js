/*
This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

SMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

*/

/*
 * Purpose: Allow the user to select a web form in order to complete a survey
 */
var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

var gInterval;

requirejs.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    paths: {
    	app: '../app',
    	jquery: '../../../../js/libs/jquery-2.1.1',
       	lang_location: '../'
    },
    shim: {
    	'app/common': ['jquery'],
    	'bootstrap.min': ['jquery']
    }
});

require([
         'jquery', 
         'bootstrap.min',
         'app/common', 
         'app/globals',
         'app/localise',
         'bootstrapfileinput'
         ], function($, bootstrap, common, globals, localise, bsfi) {
	

	$(document).ready(function() {

		globals.gIsAdministrator = false;
		getLoggedInUser(undefined, false, true, undefined, false, false);
		
		getAlerts();
		enableUserProfileBS();
	});
	
	/*
	 * Get 
	 */
	function getAlerts() {
		addHourglass();
		$.ajax({
			url: "/surveyKPI/user/alerts",
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				showAlerts(data);
				console.log("There are: " + data.length + " alerts");
				setTimeout(getAlerts, 60000);
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				clearInterval(gInterval);
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert(localise.set["msg_err_get_a"] + " " + err);
				}
			}
		});	
	}

	function showAlerts() {
		
	}
	
});



