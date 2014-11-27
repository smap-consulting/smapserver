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
    	jquery: [
      	       '//ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min',
      	       '../../../../js/libs/jquery-1.8.3.min'
      	       ],
      	jquery_ui: [
      	            '//ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min',
      	            '../../../../js/libs/jquery-ui-1.10.3.custom.min'
      	       ],
       	lang_location: '../'
    },
    shim: {
    	'jquery_ui': ['jquery'],
    	'responsivemobilemenu': ['jquery'],
    	'app/common': ['jquery']
    }
});

require([
         'jquery', 
         'app/common', 
         'jquery_ui', 
         'responsivemobilemenu', 
         'app/globals',
         'app/localise'
         ], function($, common, jquery_ui, responsivemobilemenu, globals, localise) {

$(document).ready(function() {
	
	// Get the user details
	globals.gIsAdministrator = false;
	getLoggedInUser(projectSet, false, true, undefined);
	
	// Set change function on projects
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;
		//$('#projectId').val(globals.gCurrentProject);		// Set the project value for the hidden field in template upload
		getSurveysForList(globals.gCurrentProject);			// Get surveys
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);		// Save the current project id
 	 });
	
	enableUserProfile();
});

function projectSet() {
	getSurveysForList(globals.gCurrentProject);			// Get surveys
}



function getSurveysForList(projectId) {

	var url="/surveyKPI/surveys?projectId=" + projectId + "&blocked=false&deleted=false";
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			completeSurveyList(data);
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
 * Fill in the survey list
 */
function completeSurveyList(surveyList) {
	
	$('#forms').empty();

	for(i = 0; i < surveyList.length; i++) {

		var $link = $('<a />').attr("class", "formLink")
        	.text(surveyList[i].displayName);
     
		$link.attr("href", "/webforms/formXML.php"+"?key=" + surveyList[i].ident)
			.attr("target", "_blank")
			.addClass("online");
		
		$link.button();
		$('#forms').append($link).append('<br/>');	


	
	}	
}

});

