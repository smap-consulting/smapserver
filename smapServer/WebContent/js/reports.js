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
		getLoggedInUser(projectSet, false, true, undefined);


		enableUserProfileBS();
	});


	function getReportsForUser() {

		url="/surveyKPI/myassignments";

		addHourglass();
		$.ajax({
			url: url,
			dataType: 'json',
			cache: false,
			success: function(data) {
				var filterProject = projectId;
				removeHourglass();
				completeReportList(data, filterProject);
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
					  return;  // Not an error
				} else {
					console.log("Error: Failed to get list of surveys: " + err);
				}
			}
		});
	}

	/*
	 * Fill in the report list
	 */
	function completeReportList(surveyList, filterProjectId) {

		var i,
			h = [],
			idx = -1,
			$formList = $('#form_list'),
			formList = surveyList.forms,
			taskList = surveyList.data,
			params,
			repeat;

		// Add the forms
		if(formList) {
            for (i = 0; i < formList.length; i++) {
                if (!filterProjectId || filterProjectId == formList[i].pid) {
                    h[++idx] = '<a role="button" class="btn btn-primary btn-block btn-lg" target="_blank" href="/webForm/';
                    h[++idx] = formList[i].ident;
                    h[++idx] = '">';
                    h[++idx] = formList[i].name;
                    h[++idx] = '</a>';
                }
            }
        }

		$formList.html(h.join(''));
		$formList.find('.task').off().click(function(){
			var $this = $(this),
				repeat = $this.data("repeat");

			if(!repeat) {
				$this.removeClass('btn-warning').addClass('btn-success');		// Mark task as done
			}
		});
	}

});

