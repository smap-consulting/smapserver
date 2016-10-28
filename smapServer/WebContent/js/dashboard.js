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
         'd3',
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
		
		$('input', '#tab-settings-content').iCheck({
			 checkboxClass: 'icheckbox_square-green',
			 radioClass: 'iradio_square-green'
		});
		
		$('#d_rep_def_freq').click(function(){
			getReportData(1490, "main");
		});
	});
	
	/*
	 * Get the data for a form
	 */
	function getReportData(sId, form) {
		$.ajax({
			url: "/api/v1/data/" + sId,
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				processReportResults(data);
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Error: Failed to get data: " + err);
				}
			}
		});	
	}
	
	/*
	 * Get the data for a form
	 */
	function processReportResults(results) {
		console.log(results);
		var genderByRegion = d3.nest()
		  .key(function(d) { return d.region; })
		  .entries(results);
		console.log(genderByRegion);
	}
	
});
	


