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
    	'bootstrap.min': ['jquery'],
    	'inspinia': ['jquery'],
    	'metismenu': ['jquery'],
    	'icheck': ['jquery'],
    	'slimscroll': ['jquery'],
    }
});

require([
         'jquery', 
         'bootstrap.min',
         'app/common', 
         'app/globals',
         'app/localise',
         'bootstrapfileinput',
         'inspinia',
         'metismenu',
         'slimscroll',
         'icheck'
         ], function($, bootstrap, common, globals, localise, bsfi) {
	

	$(document).ready(function() {

		localise.setlang();		// Localise HTML
		
		globals.gIsAdministrator = false;
		getLoggedInUser(getAlerts, false, true, undefined, false, false);
		
		enableUserProfileBS();
		
		$('#show_alerts').click(function(){
			if(!globals.gAlertSeen) {
				globals.gAlertSeen = true;
				$('.alert_icon').removeClass("text-danger");
				saveLastAlert(globals.gLastAlertTime, true);
			}
		});
	});
	
	/*
	 * Get the alerts
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
			if(alerts[0].updatedTime != globals.gLastAlertTime) {
				$('.alert_icon').addClass("text-danger");
				$('#chime')[0].play();
				
				globals.gLastAlertTime = alerts[0].updatedTime;
				globals.gAlertSeen = false;
				saveLastAlert(globals.gLastAlertTime, false);
			}
			
		}
		/*
		 * Drop down messages have x parts
		 * At left icon
		 * Main message
		 * Small message to right of main message
		 * Footer message (Required)
		 */
		for(i = 0; i < alerts.length; i++) {
			a = alerts[i];
			h[++idx] = '<li>';
			h[++idx] = '<div class="dropdown-messages-box">';
			if(a.link) {
				h[++idx] = '<a href="';
				h[++idx] = a.link;
				h[++idx] = '" class="pull-left">';
			} 
			h[++idx] = '<i class="fa ';
			if(a.status == "complete") {
				h[++idx] = "green-bg "
			} else if(a.priority == 1) {
				h[++idx] = "red-bg "
			} else if(a.priority == 2) {
				h[++idx] = "orange-bg "
			} else {
				h[++idx] = "blue-bg "
			}
        	h[++idx] = 'fa-edit';
        	h[++idx] = ' fa-2x"></i>';
        	if(a.link) {
             	h[++idx] = '</a>';
            }

            h[++idx] = '<div class="media-body">';
            h[++idx] = '<small class="pull-right text-navy">'; 
            h[++idx] = getInterval(a.since);
            h[++idx] = '</small>';
            h[++idx] = '<strong>';
            h[++idx] = a.message;
            h[++idx] = '</strong><br>';
            h[++idx] =  '<small class="text-muted">Yesterday 1:21 pm - 11.06.2014</small>';
            h[++idx] = '</div>';
            h[++idx] = '</div>';
           
            h[++idx] = '</li>';
            h[++idx] = '<li class="divider"></li>';
		}
		$('.dropdown-messages').html(h.join(''));
	}
	
	function getInterval(seconds) {
		if(seconds < 2) {
			return seconds + ' ' + localise.set["i_sec"];
		} else if(seconds < 60) {
			return seconds + ' ' + localise.set["i_secs"];
		} else if(seconds < 120) {
			return Math.floor(seconds/ 60) + ' ' + localise.set["i_min"];
		} else if(seconds < 3600) {
			return Math.floor(seconds/ 60) + ' ' + localise.set["i_mins"];
		} else if(seconds < (3600 * 2)) {
			return Math.floor(seconds/ (60 * 60)) + ' ' + localise.set["i_hour"];
		} else if(seconds < (3600 * 24)) {
			return Math.floor(seconds/ (60 * 60)) + ' ' + localise.set["i_hours"];
		} else if(seconds < (3600 * 24 * 2)) {
			return Math.floor(seconds/ (60 * 60 * 24)) + ' ' + localise.set["i_day"];
		} else if(seconds < (3600 * 24)) {
			return Math.floor(seconds/ (60 * 60 * 24)) + ' ' + localise.set["i_days"];
		}
	}
	
	$('input', '#tab-settings-content').iCheck({
		 checkboxClass: 'icheckbox_square-green',
		 radioClass: 'iradio_square-green'
	 });
	
});



