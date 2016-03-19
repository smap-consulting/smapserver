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
         'bootstrapfileinput'
         ], function($, bootstrap, common, globals, localise, bsfi) {

$(document).ready(function() {
	
	if(typeof getVersion === "function") {
		getVersion();			// Update if the version on the server has changed
	}
	
	/*
	 * Apply customised javascript
	 */
	if(typeof setCustom === "function") {
		setCustom();			// Apply custom javascript
	}
	
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
function saveUserDetails(formData, key) {

	var url = "/surveyKPI/user/details";
	
	// If this is an iphone we might need to do the upload with a unique key
	if(key) {
		url = url + "/key/" + key;
	}
	
	$('#up_alert').hide();
	addHourglass();
	$.ajax({
		  type: "POST",
		  data: formData,
		  cache: false,
          contentType: false,
          processData:false,
		  url: url,
		  success: function(data, status) {
			  removeHourglass();
			  $('#up_alert').show().removeClass('alert-danger').addClass('alert-success').html("User details saved");
			  // Update the signature value
			  var user = JSON.parse(data);
			  $('#my_signature').attr("src", user.signature);
			  // updateUserDetails(data, undefined);
		  },
		  error: function(xhr, textStatus, err) {
			  var originalFormData = formData,
			  	  originalKey = key;
			  
			  removeHourglass(); 
			  
			  /*
			   * If there is an error retry after gettign an authentication key
			   * Safari on ios seems to return a status of 0 for a 401 error
			   */
			  if(!originalKey) {
				  getKey(originalFormData);
			  } else {
				 $('#up_alert').show().removeClass('alert-success').addClass('alert-danger').html("Error profile not saved" + xhr.responseText);
			 }
		  }
	});
	
};
	
function getKey(formData) {
	
	addHourglass();
	$.ajax({
		url: '/surveyKPI/login/key?form=user',
		dataType: 'json',
		cache: false,
		success: function(data) {
			var passedFormData = formData;
			removeHourglass();
			saveUserDetails(passedFormData, data.key);
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

function projectSet() {
	getSurveysForList(globals.gCurrentProject);			// Get surveys
}


function getSurveysForList(projectId) {

	//var url="/surveyKPI/surveys?projectId=" + projectId + "&blocked=false&deleted=false";
	url="/surveyKPI/myassignments";
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			var filterProject = projectId;
			removeHourglass();
			completeSurveyList(data, filterProject);
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
 * XXXX
 */
function completeSurveyList(surveyList, filterProjectId) {
	
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
		for(i = 0; i < formList.length; i++) {
			if(!filterProjectId || filterProjectId == formList[i].pid) {
				h[++idx] = '<a role="button" class="btn btn-primary btn-block btn-lg" target="_blank" href="/webForm/';
				h[++idx] = formList[i].ident;
				h[++idx] = '">';
				h[++idx] = formList[i].name;
				h[++idx] = '</a>';	
			} 
		}
	}
	
	// Add the tasks
	if(taskList) {
		for(i = 0; i < taskList.length; i++) {
			if(!filterProjectId || filterProjectId == taskList[i].task.pid) {
				repeat = taskList[i].task.repeat;	// Can complete the task multiple times
				h[++idx] = '<a role="button" class="task btn btn-warning btn-block btn-lg" target="_blank" data-repeat="';
				if(repeat) {
					h[++idx] = 'true';
				} else {
					h[++idx] = 'false';
				}
				h[++idx] = '" href="/webForm/';
				h[++idx] = taskList[i].task.form_id;
				if(taskList[i].task.initial_data) {
					// Add the initial data parameters
					params = taskList[i].task.initial_data;
					params = params.substring(params.indexOf('?'));
					// TODO Hack to fix inconistency in parameter names between webforms and fieldTask
					params = params.replace("key=", "datakey=");
					params = params.replace("keyval=", "datakeyvalue=");
					h[++idx] = params;
					// Add the assignment id
					h[++idx] = '&assignment_id=';
				} else {	// Launch the form without data
					// Add the assignment id
					h[++idx] = '?assignment_id=';
				}
				h[++idx] = taskList[i].assignment.assignment_id; 
				h[++idx] = '">';
				h[++idx] = taskList[i].task.title + " (task id: " + taskList[i].task.id + ")";
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

