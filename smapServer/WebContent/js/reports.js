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

var gDelSig = false;

requirejs.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: '../../../../js/libs/jquery-2.1.1',
       	lang_location: '../'
    },
    shim: {
    	'app/common': ['jquery'],
    	'bootstrap.min': ['jquery'],
    	'icheck': ['jquery'],
       	'inspinia': ['jquery'],
    	'metismenu': ['jquery'],
    	'slimscroll': ['jquery']
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
         'pace',
         'icheck'
         ], function($, bootstrap, common, globals, localise, bsfi) {

	$(document).ready(function() {

		localise.setlang();		// Localise HTML

		// Get the user details
		globals.gIsAdministrator = false;
		getLoggedInUser(undefined, false, true, undefined);
		getReportsForUser();

		enableUserProfileBS();
	});


	function getReportsForUser() {

		url="/surveyKPI/reportgen";

		addHourglass();
		$.ajax({
			url: url,
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				completeReportList(data);
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
					  return;  // Not an error
				} else {
					console.log("Error: Failed to get list of reports: " + err);
				}
			}
		});
	}

	/*
	 * Fill in the report list
	 */
	function completeReportList(reportList) {

		var i,
			h = [],
			idx = -1,
			$reportList = $('#report_list');

		// Add the reports
		if(reportList) {
            for (i = 0; i < reportList.length; i++) {
				h[++idx] = '<a role="button" class="btn btn-primary btn-block btn-lg" href="/surveyKPI/reportgen/';
				h[++idx] = reportList[i].sId;
				h[++idx] = '/';
				h[++idx] = cleanFileName(reportList[i].name);
                h[++idx] = '">';
                h[++idx] = reportList[i].name;
                h[++idx] = '</a>';

            }
        }

		$reportList.html(h.join(''));

	}

});

