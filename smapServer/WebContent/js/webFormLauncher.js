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
         'bootstrap.file-input'
         ], function($, bootstrap, common, globals, localise, bsfi) {

$(document).ready(function() {
	
	localise.setlang();		// Localise HTML
	
	// Get the user details
	globals.gIsAdministrator = false;
	getLoggedInUser(projectSet, false, true, undefined);
	
	// Set change function on projects
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;
		getSurveysForList(globals.gCurrentProject);			// Get surveys
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);		// Save the current project id
 	 });
	
	// Set up the tabs
    $('#userTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		
		$('#formsPanel').hide();
		$('#userPanel').show();
    })
    $('#formsTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		  	  
		$('#formsPanel').show();
		$('#userPanel').hide();
    })
	
    /*
	 * Save the user details
	 * There is some overlap between this and saving the user profile
	 */
	$('#userDetailsSave').off().click(function() {
		var user = globals.gLoggedInUser,
			userDetails = {},
			userList = [],
			error = false,
			userList;
		
		user.name = $('#my_name').val();
		
		userDetails.title = $('#my_title').val();
		userDetails.license = $('#my_license').val();
		user.settings = JSON.stringify(userDetails);
			
		user.current_project_id = 0;	// Tell service to ignore project id and update other details
		user.current_survey_id = 0;
		
		var settings = JSON.stringify(user);
		$('#userSettings').val(settings);
    	var f = document.forms.namedItem("signaturesave");
    	var formData = new FormData(f);
    	
		saveUserDetails(formData);			// Save the updated user details to disk	 
	});
    
    $('.file-inputs').bootstrapFileInput();
    
	enableUserProfileBS();
});

/*
 * Save the currently logged on user's details
 */
function saveUserDetails(formData) {

	$('#up_alert').hide();
	addHourglass();
	$.ajax({
		  type: "POST",
		  data: formData,
		  cache: false,
          contentType: false,
          processData:false,
		  url: "/surveyKPI/user/details",
		  success: function(data, status) {
			  removeHourglass();
			  $('#up_alert').show().removeClass('alert-danger').addClass('alert-success').html("User details saved");
			  // Update the signature value
			  var user = JSON.parse(data);
			  $('#my_signature').attr("src", user.signature);
			  // updateUserDetails(data, undefined);
		  },
		  error: function(xhr, textStatus, err) {
			  removeHourglass();  	
			  if(xhr.readyState == 0 || xhr.status == 0) {
			      return;  // Not an error
			 } else {
				 $('#up_alert').show().removeClass('alert-success').addClass('alert-danger').html("Error profile not saved" + xhr.responseText);
			 }
		  }
	});
	
};
	

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
	
	var i,
		h = [],
		idx = -1,
		$formList = $('#form_list');

	for(i = 0; i < surveyList.length; i++) {
		
		h[++idx] = '<a role="button" class="btn btn-primary btn-block btn-lg" target="_blank" href="/webForm/';
		h[++idx] = surveyList[i].ident;
		h[++idx] = '">';
		h[++idx] = surveyList[i].displayName;
		h[++idx] = '</a>';
		
	}
	
	$formList.html(h.join(''));
}

});

