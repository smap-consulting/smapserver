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

var gLastAlertTime;

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
				$('.alert_count').html(data.length);
				setTimeout(getAlerts, 60000);
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert(localise.set["msg_err_get_a"] + " " + err);
				}
			}
		});	
	}

	function showAlerts(alerts) {
		var h = [],
			idx = -1,
			i,
			a;
	
		/*
		 * Check for a change in the alert list
		 */
		if(alerts.length > 0) {
			if(alerts[0].updatedTime != gLastAlertTime) {
				$('.alert_icon').addClass("text-danger");
				$('#chime')[0].play();
			}
			gLastAlertTime = alerts[0].updatedTime;
			// TODO save in user settings
		}
		for(i = 0; i < alerts.length; i++) {
			a = alerts[i];
			h[++idx] = '<li>';
			if(alert.link) {
				h[++idx] = '<a href="';
				h[++idx] = a.link;
				h[++idx] = '">';
			}    
            h[++idx] = '<div>';
            	h[++idx] = '<i class="fa ';
            	h[++idx] = 'fa-envelope';
            	h[++idx] = ' fa-fw"></i>';
            	h[++idx] = a.message;
                h[++idx] =  '<span class="pull-right text-muted small">';                 
                h[++idx] = '4 minutes ago';
                h[++idx] = '</span>';
            h[++idx] = '<div>';
            if(a.link) {
            	h[++idx] = '</a>';
            }
            h[++idx] = '</li>';
		}
		$('.dropdown-alerts').html(h.join(''));
	}
	
});



