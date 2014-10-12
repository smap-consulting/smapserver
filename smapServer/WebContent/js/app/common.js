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

var gWait = 0;		// This javascript file only

/*
 * ===============================================================
 * Project Functions
 * ===============================================================
 */

/*
 * Update the list of available projects
 */
function updateProjectList(addAll, projectId, callback) {

	var $projectSelect = $('.project_list'),
		i, 
		h = [],
		idx = -1,
		updateCurrentProject = true;
	
	if(addAll) {
		h[++idx] = '<option value="0">All</option>';
		updateCurrentProject = false;
	}
	for(i = 0; i < globals.gProjectList.length; i++) {
		h[++idx] = '<option value="';
		h[++idx] = globals.gProjectList[i].id;
		h[++idx] = '">';
		h[++idx] = globals.gProjectList[i].name;
		h[++idx] = '</option>';
		
		if(globals.gProjectList[i].id === projectId) {
			updateCurrentProject = false;
		}
	}
	$projectSelect.empty().append(h.join(''));

	// If for some reason the user's default project is no longer available then 
	//  set the default project to the first project in the list
	//  if the list is empty then set the default project to undefined
	if(updateCurrentProject && globals.gProjectList[0]) {	
		globals.gCurrentProject = globals.gProjectList[0].id;		// Update the current project id
		globals.gCurrentSurvey = -1;
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
	} else if(updateCurrentProject) {	
		globals.gCurrentProject = -1;		// Update the current project id
		globals.gCurrentSurvey = -1;
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
	}
	
	$projectSelect.val(globals.gCurrentProject);			// Set the initial project value
	$('#projectId').val(globals.gCurrentProject);			// Set the project value for the hidden field in template upload

	if(typeof callback !== "undefined") {
		callback(globals.gCurrentProject);				// Call the callback with the correct current project
	}
}

/*
 * Get the list of available projects from the server
 */
function getMyProjects(projectId, callback, getAll) {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/myProjectList",
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			globals.gProjectList = data;
			updateProjectList(getAll, projectId, callback);
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get list of projects: " + err);
			}
		}
	});	
}

/*
 * Save the current project id in the user defaults
 */
function saveCurrentProject(projectId, surveyId) {

	if(surveyId > 0 || projectId > 0) {
		
		var user = {
				current_project_id: projectId,
				current_survey_id: surveyId
				};
		var userString = JSON.stringify(user);
		
		addHourglass();
		$.ajax({
			  type: "POST",
			  contentType: "application/json",
			  dataType: "json",
			  url: "/surveyKPI/user",
			  data: { user: userString },
			  success: function(data, status) {
				  removeHourglass();
			  }, error: function(data, status) {
				  removeHourglass();
				  console.log("Error: Failed to save current project");
			  }
		});
	}
}


/*
 * ===============================================================
 * User Functions
 * ===============================================================
 */

/*
 * Update the user details on the page
 */
function updateUserDetails(data, getOrganisationsFn) {
	
	var groups = data.groups,
		i;
	
	if(data.language && data.language !== gUserLocale) {
		localStorage.setItem('user_locale', data.language);
		location.reload();
	}
	globals.gLoggedInUser = data;
	$('#username').html(data.name).button({ label: data.name + " @" + data.organisation_name, 
			icons: { primary: "ui-icon-person" }}).off().click(function(){
		$('#me_edit_form')[0].reset();
		
		$('#reset_me_password_fields').show();
		$('#password_me_fields').hide();
		addLanguageOptions($('#me_language'), data.language);
		$('#me_name').val(data.name);
		$('#me_email').val(data.email);
		
		$('#modify_me_popup').dialog("option", "title", data.name + "@" + data.organisation_name);
		$('#modify_me_popup').dialog("open");
	});
	
	/*
	 * Show administrator only functions
	 */
	if(groups) {
		for(i = 0; i < groups.length; i++) {
			if(groups[i].name === "admin") {
				globals.gIsAdministrator = true;
			}
			if(groups[i].name === "org admin") {
				globals.gIsOrgAdministrator = true;
			}
			if(groups[i].name === "analyst") {
				globals.gIsAnalyst = true;
			}
		}
	}
	if(globals.gIsAnalyst) {
		$('.analyst_only').show();
	} else {
		$('.analyst_only').hide();
	}
	if(globals.gIsAdministrator) {
		$('.super_user_only').show();
	} else {
		$('.super_user_only').hide();
	}
	if(globals.gIsOrgAdministrator) {
		$('.org_user_only').show();
		if(typeof getOrganisationsFn === "function") {
			getOrganisationsFn();
		}
	} else {
		$('.org_user_only').hide();
	}
}

function addLanguageOptions($elem, current) {
	
	var h = [],
		idx = -1,
		i,
		languages = [
		             {
		            	locale: "en",
		            	name: "English"
		             },
		             {
			            locale: "fr",
			            name: "French"
			         },
			         {
				         locale: "kh",
				         name: "Khmer"
			         },
			         {
			         	locale: "pt",
			            name: "Portugese"
					 }
		];
	
	for(i = 0; i < languages.length; i++) {
		h[++idx] = '<option value="';
		h[++idx] = languages[i].locale;
		h[++idx] = '">';
		h[++idx] = languages[i].name;
		h[++idx] = '</option>';
	}
	$elem.html(h.join(''));
	$elem.val(current);
}

/*
 * Enable the user profile button
 */
function enableUserProfile () {
	 // Initialse the dialog for the user to edit their own account details
	 $('#modify_me_popup').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			title:"User Profile",
			show:"drop",
			width:350,
			height:350,
			zIndex: 2000,
			buttons: [
		        {
		        	text: "Cancel",
		        	click: function() {
		        		
		        		$(this).dialog("close");
		        	}
		        }, {
		        	text: "Save",
		        	click: function() {

		        		var user = globals.gLoggedInUser,
		        			userList = [],
		        			error = false,
		        			userList;
		        		
		        		user.name = $('#me_name').val();
		        		user.language = $('#me_language').val();
		        		user.email = $('#me_email').val();
		        		if($('#me_password').is(':visible')) {
		        			user.password = $('#me_password').val();
		        			if($('#me_password_confirm').val() !== user.password) {
		        				error = true;
		        				user.password = undefined;
		        				alert("Passwords do not match");
		        				$('#me_password').focus();
		        				return false;
		        			}
		        		} else {
		        			user.password = undefined;
		        		}
		        		
		        		user.current_project_id = 0;	// Tell service to ignore project id and update other details
		        		user.current_survey_id = 0;
		        		saveCurrentUser(user);			// Save the updated user details to disk
		        		$(this).dialog("close");
		        	}, 
		        }, {
		        	text: "Logout",
		        	click: function() {
		        		jQuery.ajax({
		        		    type: "GET",
		        			cache: false,
		        		    url: "/fieldManager/templateManagement.html",
		        		    username: "shkdhasfkhd",
		        		    password: "sieinkdnfkdf",
		        		    error: function(data, status) {
		        				  window.location.href="/";
		        			},
		        			success: function(data,status) {
		        				window.location.href="/";
		        			}
		        		});
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	 );
	 

	 // Initialise the reset password checkbox
	 $('#reset_me_password').click(function () {
		 if($(this).is(':checked')) {
			 $('#password_me_fields').show();
		 } else {
			 $('#password_me_fields').hide();
		 }
	 });
}

/*
 * Save the currently logged on user's details
 */
function saveCurrentUser(user) {

	var userString = JSON.stringify(user);
	addHourglass();
	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  dataType: "json",
		  url: "/surveyKPI/user",
		  data: { user: userString },
		  success: function(data, status) {
			  removeHourglass();
			  updateUserDetails(user, undefined);
		  }, error: function(data, status) {
			  removeHourglass();
			  alert("Error profile not saved"); 
		  }
	});
}


function getLoggedInUser(callback, getAll, getProjects, getOrganisationsFn, hideUserDetails, dontGetCurrentSurvey) {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/user",
		cache: false,
		dataType: 'json',
		success: function(data) {
			removeHourglass();
			if(!hideUserDetails) {
				updateUserDetails(data, getOrganisationsFn);
			}
			
			globals.gEmailEnabled = data.allow_email;
			globals.gFacebookEnabled = data.allow_facebook;
			globals.gTwitterEnabled = data.allow_twitter;
			globals.gCanEdit = data.can_edit;
			
			if(getProjects) {
				globals.gCurrentProject = data.current_project_id;
				if(!dontGetCurrentSurvey) {	// Horrible hack, on edit screen current survey is set as parameter not from the user's defaults
					globals.gCurrentSurvey = data.current_survey_id;
				}
				$('#projectId').val(globals.gCurrentProject);		// Set the project value for the hidden field in template upload
				getMyProjects(globals.gCurrentProject, callback, getAll);	// Get projects and call getSurveys when the current project is known
			}

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get user details: " + err);
				alert("Error: Failed to get user details: " + err);
			}
		}
	});	
}

/*
 * ===============================================================
 * Hourglass Functions
 * ===============================================================
 */

function addHourglass() {

	if(gWait === 0) {

		$("#hour_glass").show();
	}
	++gWait;
}

function removeHourglass() {

	--gWait;
	if(gWait === 0) {

		$("#hour_glass").hide();
	}

}

/*
 * ===============================================================
 * Survey Functions
 * ===============================================================
 */

/*
 * Load the surveys from the server
 */
function loadSurveys(projectId, selector, getDeleted, addAll, callback) {
	
	var url="/surveyKPI/surveys?projectId=" + projectId + "&blocked=true",
		$elem;
	
	if(selector === undefined) {
		selector = ".survey_select";	// Update the entire class of survey select controls
	}
	$elem = $(selector);
	
	if(typeof projectId !== "undefined" && projectId != -1 && projectId != 0) {
		
		if(getDeleted) {
			url+="&deleted=true";
		}
		addHourglass();

		$.ajax({
			url: url,
			dataType: 'json',
			cache: false,
			success: function(data) {
				
				removeHourglass();
				$elem.empty();
				if(addAll) {
					$elem.append('<option value="_all">All Surveys</option>');	
				}
				$.each(data, function(j, item) {
					$elem.append('<option value="' + item.id + '">' + item.displayName + '</option>');
				});

				//globals.gCurrentSurvey = $elem.val();   // TODO set to current global survey
				if(globals.gCurrentSurvey > 0) {
					$elem.val(globals.gCurrentSurvey);
				}
				
				if(typeof callback == "function") {
					callback();
				}
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
	} else {
		$elem.empty();
		if(addAll) {
			$elem.append('<option value="_all">All Surveys</option>');	
		}
		
		if(callback) {
			callback();
		}

	}
}

// Common Function to get the language and question list (for the default language)
function getLanguageList(sId, callback, addNone, selector, setGroupList) {
	
	if(typeof sId === "undefined") {
		sId = globals.gCurrentSurvey;
	}
	
	function getAsyncLanguageList(sId, theCallback, selector) {
		addHourglass();
		$.ajax({
			url: languageListUrl(sId),
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				globals.gSelector.setSurveyLanguages(sId, data);
				if(selector) {
					setSurveyViewLanguages(data, undefined, selector, addNone);
				} else {
					setSurveyViewLanguages(data, undefined, '#settings_language', false);	
					setSurveyViewLanguages(data, undefined, '#export_language', true);
					setSurveyViewLanguages(data, undefined, '#language_name', false);
				}
				
				if(data[0]) {
					getQuestionList(sId, data[0].name, "-1", "-1", theCallback, setGroupList, undefined);	// Default language to the first in the list
				} else {
					if(typeof theCallback === "function") {
						theCallback();
					}
				}
				
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Error: Failed to get list of languages: " + err);
				}
			}
		});	
	}
	
	getAsyncLanguageList(sId, callback, selector);
}

//Function to get the question list
function getQuestionList(sId, language, qId, groupId, callback, setGroupList, view) {

	function getAsyncQuestionList(sId, language, theCallback, groupId, qId, view) {
	
		addHourglass();
		$.ajax({
			url: questionListUrl(sId, language, true),
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				globals.gSelector.setSurveyQuestions(sId, language, data);
				setSurveyViewQuestions(data, qId, view);
	
				if(setGroupList && typeof setSurveyViewQuestionGroups === "function") {
					setSurveyViewQuestionGroups(data, groupId);
				}
				if(typeof theCallback === "function") {
					theCallback();
				}
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Error: Failed to get list of questions: " + err);
				}
			}
		});	
	}
	
	getAsyncQuestionList(sId, language, callback, groupId, qId, view);
}

//Set the language list in the survey view control
function setSurveyViewLanguages(list, language,elem, addNone) {

	var $languageSelect = $(elem);
	$languageSelect.empty();
	if(addNone) {
		$languageSelect.append('<option value="none">None</option>');
	}
	$.each(list, function(j, item) {
		$languageSelect.append('<option value="' + item.name + '">' + item.name + '</option>');
	});
	if(language) {
		$languageSelect.val(language);
	}
}

// Set the question list in the survey view control
function setSurveyViewQuestions(list, qId, view) {
	
	var $questionSelect = $('.selected_question'),
		label;
	
	$questionSelect.empty();
	$questionSelect.append('<option value="-1">None</option>');

	if(list) {
		$.each(list, function(j, item) {
			if(typeof item.q === "undefined") {
				label = "";
			} else {
				label = item.q;
			}
			if(item.is_ssc) {
				$questionSelect.append('<option value="' + item.id + '">ssc : ' + item.name + " : " + item.fn + '</option>');
			} else {
				$questionSelect.append('<option value="' + item.id + '">' + item.name + " : " + label + '</option>');
			}
		});
	}
	if(!qId) {
		qId = "-1";
	}
	$questionSelect.val(qId);
	if(view) {
		setFilterFromView(view);	// Set the filter dialog settings
	}

}

/*
 * ------------------------------------------------------------
 * Web service Functions
 */
function languageListUrl (sId) {

	var url = "/surveyKPI/languages/";
	url += sId;
	return url;
}

/*
 * Web service handler for retrieving available "count" questions for graph
 *  @param {string} survey
 */
function questionListUrl (sId, language, exc_read_only) {

	var url = "/surveyKPI/questionList/", 
		ro_text;
	
	if(exc_read_only) {
		ro_text = "true";
	} else {
		ro_text = "false";
	}
	
	url += sId;
	url += "/" + language;
	url += "?exc_read_only=" + ro_text;
	return url;
}

/**
 * Web service handler for question Meta Data
 * @param {string} survey id
 * @param {string} question id
 */
function questionMetaURL (sId, lang, qId) {

	var url = "/surveyKPI/question/";
	url += sId;
	url += "/" + lang;
	url += "/" + qId;
	url += "/getMeta";
	return url;
}



