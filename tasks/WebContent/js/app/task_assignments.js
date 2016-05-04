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

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

define(['jquery','bootstrap', 'mapbox_app', 'common', 'localise', 
        'bootbox',
        'moment',
        'datetimepicker'
        ], 
		function($, bootstrap, mapbox_app, common, lang, bootbox, moment, datetimepicker) {

	
							// The following globals are only in this java script file
var gTasks,					// Object containing the task data retrieved from the database
	gTaskGroupIndex = -1,	// Currently selected task group
	gTaskParams = [],		// Parameters for a new task	
	gFilterqType,			// The type of the filter question select, select1, int, string
	gTaskGroupId = 0,		// Currently selected task group
	gCurrentTaskFeature,	// Currently edited task feature
	gClickOnMapEnabled = false,		// Listen to clicks on the map
	gCalendarInitialised = false,	// Set true when the calendar pane has been initialised
	gMapInitialised = false;		// Set true when the map pane has been initialised
	
$(document).ready(function() {
	
	var bs = isBusinessServer();
	
	window.moment = moment;		// Make moment global for use by common.js
	
	globals.gRegion = {};	// Initialise global values
	globals.gRegions = undefined;
	
	localise.setlang();		// Localise HTML
	if(typeof getVersion === "function") {
		getVersion();			// Update if the version on the server has changed
	}
	
	$("#side-menu").metisMenu();
	
	getLoggedInUser(projectChanged, false, true, undefined);
	
	// Set change function on projects
	$('#project_name').change(function() {
		projectChanged();
 	 });
	
	// Get locations
	getLocations(setLocationList);
	
	/*
	 * Change function on source project when tasks are being copied from an existing survey
	 * The source project is the project that data is being copied from, 
	 *  it can be different from the current project
	 */
	// 
	$('#project_select,#project_name').change(function() {
		var sourceProject = $('#project_select option:selected').val();
		loadSurveys(sourceProject, "#survey", false, false, surveyChanged);			// Get surveys
 	 });
	
	// Add a trigger to respond to the clicking of "filter tasks" 
	$('#filter_results_check').prop('checked', false).click(function() {
		$('#filter_results').toggle();
	});
	
	// Add a trigger to respond to the clicking of "empty_task_group" 
	$('#empty_task_group').prop('checked', false).click(function() {
		$('#add_task_from_existing').toggle();
	});
	
	// Add response to the filters being changed
	$('.task_filter').change(function() {
		refreshAssignmentData();
	});
	
	// Add response to a source survey being selected 
	$('#survey').change(function() {
		surveyChanged();
	});
	
	// Add response to a source survey being selected 
	$('#filter_language').change(function() {
		languageChanged();
	});
	
	// Add response to a filter question being selected 
	$('#filter_question').change(function() {
		questionChanged();
	});
	
	// Change Functions
	$('.users_select').change(function () {
		globals.gCurrentUserName = $('option:selected', $(this)).text();
		globals.gCurrentUserId = $('option:selected', $(this)).val();
	});

	/*
	 * Menus
	 */
	$('#zoomData').button().click(function () {	// Add zoom to data button
		zoomToFeatureLayer('map');
	});
	$('#m_export_pdf').click(function () {	// Export to PDF
		alert("Not implemented");
	});
	
	$('#m_export_xls').click(function () {	// Export to XLS
		var url = '/surveyKPI/tasks/xls/' + globals.gCurrentTaskGroup,
			name = $('#taskgroup option:selected').text();
		downloadFile(url, name + ".xlsx", 
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
	});
	
	$('#m_import_xls').click(function () {	// Import from XLS
		$('#import_taskgroup').modal("show");
	});
	$(('#importTaskGroupGo')).click(function(){
		importTaskGroup();
	});
    $('.file-inputs').bootstrapFileInput();
	
	// Add a trigger to open the modal that assigns a user to tasks
	$('#assignUser').button().click(function () {
		
		globals.gCurrentUserName = $('#users_select_user option:selected').text();
		globals.gCurrentUserId = $('#users_select_user option:selected').val();
		
		$('#assign_user').modal("show");
	});
	
	/*
	 * Save the assigned user
	 */
	$('#assignUserSave').off().click(function() {
		updatePendingAssignments("accepted", $('#users_select_user').val());
        saveData(globals.gPendingUpdates);
		refreshAssignmentData();
		globals.gCurrentUserId = undefined;
		globals.gCurrentUserName = undefined;
		globals.gPendingUpdates = [];
	});
	
	
	/*
	 * Update the properties of a task
	 */
	$('#taskPropertiesSave').off().click(function() {
		var url = "/surveyKPI/tasks/task/",
			taskFeature = {
				properties: {}
			};
		
		url += globals.gCurrentProject + "/" + globals.gCurrentTaskGroup;
		
		/*
		 * Set the properties of the taskFeature from the dialog
		 */
		taskFeature.properties["id"] = $('#task_properties_taskid').val();		// task id
		if(taskFeature.properties["id"] == "") {
			taskFeature.properties["id"] = 0;
		}
		taskFeature.properties["name"] = $('#task_properties_title').val();		// task name
		taskFeature.properties["form_id"] = $('#task_properties_sname').val();	// form id
		taskFeature.properties["repeat"] = $('#task_properties_repeat').prop('checked');
			
		taskFeature.properties["scheduleAt"] = $('#scheduleAtUTC').val(utcTime($('#scheduleAt').val()));
		taskFeature.properties["location_trigger"] = $('#nfc_select').val();
		
		/*
		 * Convert the geoJson geometry into a WKT location for update
		 */
		if(gCurrentTaskFeature.geometry) {
			taskFeature.properties.location = "POINT(" + gCurrentTaskFeature.geometry.coordinates.join(" ") + ")";
		}

		// TODO task update details (updating existing record)
		
		// Validations
		/*
		if(taskFeature.properties["repeat"]) || user.ident.length == 0) {
			alert("User ident must be specified and either be an email address or " +
					"only include lowercase characters from a-z and numbers.  No spaces.");
			$('#user_ident').focus();
			$('#userDetailsSave').prop("disabled", false);
			return false;
		}
		*/
		
		tfString = JSON.stringify(taskFeature);
		
		addHourglass();
		$.ajax({
			  type: "POST",
			  dataType: 'text',
			  contentType: "application/json",
			  url: url,
			  data: { task: tfString },
			  success: function(data, status) {
				  removeHourglass();
				  refreshAssignmentData();
			  },
			  error: function(xhr, textStatus, err) {
				 
				  removeHourglass(); 
				  alert(localise.set["msg_err_upd"] + xhr.responseText);
				 
			  }
		});
	})
		
	// Create new task group
	$('#addTaskGroup').button().click(function () {
		var taskSource = $('input[name=task_source]:checked', '#assign_survey_form').val(),
			s_id = $('#survey').val();
		
		/*
		 * Make sure we have the survey id
		 */
		if(typeof s_id === "undefined" || s_id === null) {
			alert(localise.set["msg_err_wait"]);
			return;
		}
		
		if(taskSource === "new") {
			globals.gCurrentUserName = $('#users_select_new_task option:selected').text();
			globals.gCurrentUserId = $('#users_select_new_task option:selected').val();
			registerForNewTasks();
		}
		
		// open the modal
		$('#addTask').modal("show");

	});
	
	/*
	 * Create a new group, 
	 *  optionally populated with tasks generated from existing survey results
	 *  or from an XLS file
	 */
	$('#addNewGroupSave').click(function () {
		var assignObj = {},
			assignString,
			url,
			filterObj = {},
			filterqId,
			filteroId,
			source_survey;
	
		if(validDates()) {

			updateTaskParams();
		
			assignObj["task_group_name"] = $('#task_group_name').val();	// The Name of the task group
			assignObj["project_name"] = $('#project_select option:selected').text();	// The name of the project that this survey is in
			
			if(!$('#empty_task_group').is(':checked')) {
				
				assignObj["survey_name"] = $('#survey_to_complete option:selected').text();	// The display name of the survey to complete
				assignObj["target_survey_id"] = $('#survey_to_complete option:selected').val(); 		// The form id is the survey id of the survey used to complete the task!
				assignObj["user_id"] = $('#users_task_group option:selected').val(); 		// User assigned to complete the task
				
				source_survey = $('#survey').val(); 						// The survey that provides the existing results	
				if(!source_survey) {
					source_survey = -1;
				}
				assignObj["source_survey_id"] = source_survey; 
				assignObj["address_columns"] = removeUnselected(gTaskParams);
				assignObj["source_survey_name"] = $('#survey option:selected').text();		// The display name of the survey that will provide the source locations and initial data
				assignObj["update_results"] = $('#update_results').is(':checked'); 			// Set to true if the survey is to be updated	
				
				// Add filter if filter checkbox has been checked
				if($('#filter_results_check').is(':checked')) {
					
					filterObj["qType"] = gFilterqType;
					filterObj["qId"] = $('#filter_question option:selected').val();
					filterObj["oValue"] = $('#filter_option option:selected').val();
					filterObj["qText"] = $('#filter_text').val();
					filterObj["qStartDate"] = getUtcDate($('#startDate'), true, false);		// Get start of day
					filterObj["qEndDate"] = getUtcDate($('#endDate'), false, true);			// Get end of day
					if(gFilterqType === "int") {
						filterObj["qInteger"] = $('#filter_integer').val();
					}
					filterObj["lang"] = $('#filter_language option:selected').val();
					assignObj["filter"] = filterObj;
		
				}
			}
	
	    	assignString = JSON.stringify(assignObj);
	    	globals.gCurrentUserId = undefined;
	    	globals.gCurrentUserName = undefined;
			
			addHourglass();
			$.ajax({
				  type: "POST",
				  url: "/surveyKPI/assignments/addSurvey/" + globals.gCurrentProject,
				  data: { settings: assignString },
				  dataType: 'json',
				  success: function(data, status) {
					  removeHourglass();
					  $('#addTask').modal("hide");
					  globals.gCurrentTaskGroup = data.tg_id;
					  refreshTaskGroupData();
					  refreshAssignmentData();
				  }, error: function(data, status) {
					  removeHourglass();
					  if(data.responseText.indexOf("<html>") !== 0) {
						  alert(localise.set["c_error"] + " : " + data.responseText); 
					  } else {
						  alert(localise.set["msg_err_upd"]);
					  }
	
				  }
			});
		}
		
	});
	
	/*
	 * Function to delete current task group
	 * Keep
	 */
	$('#deleteTaskGroup').button().click(function () {
		
		var tg_id = globals.gCurrentTaskGroup;
		
		bootbox.confirm(localise.set["msg_confirm_del"] + ' ' + localise.set["msg_confirm_tasks"] + ' (' + $('#taskgroup option:selected').text() + ')', function(result){
			if(result) {
				addHourglass();
				$.ajax({
					  type: "DELETE",
					  url: "/surveyKPI/assignments/" + tg_id,
					  success: function(data, status) {
						  removeHourglass();
						  refreshTaskGroupData();
					  }, error: function(data, status) {
						  removeHourglass();
						  console.log(data);
						  alert("Error: Failed to delete task group"); 
					  }
				});	
			}
		});
	
		
	});
	
	/*
	 * New Style add task function
	 */
	$('#addSingleTask').click(function () {
		var task = {},
			taskFeature = {
				geometry: {
					coordinates: []
				},	
				properties: {}
			};
		
		editTask(true, task, taskFeature);
	});
	
	/*
	 * Save new ad-hoc tasks in a group  - Deprecated
	 *
	$('#addNewTaskSave').click(function () {
		var error = false,
			assignObj = {},
			assignString,
			url,
			filterObj = {},
			filterqId,
			filteroId,
			source_survey;
	
		assignObj["task_group_name"] = $('#task_group_name').val();	// The Name of the task group
		assignObj["survey_name"] = $('#survey_to_complete_new_task option:selected').text();	// The display name of the survey to complete
		assignObj["project_name"] = $('#project_select option:selected').text();	// The name of the project that this survey is in
		assignObj["target_survey_id"] = $('#survey_to_complete_new_task option:selected').val(); 						// The form id is the survey id of the survey used to complete the task!
		assignObj["task_group_id"] = gTaskGroupId;
		assignObj["user_id"] = $('#users_select_new_task option:selected').val();
		   			
		assignObj["source_survey_id"] = -1;
		assignObj["new_tasks"] = $.parseJSON(getTasksAsGeoJSON());
	
		assignString = JSON.stringify(assignObj);
		globals.gCurrentUserId = undefined;
		globals.gCurrentUserName = undefined;
	
		addHourglass();
		$.ajax({
			  type: "POST",
			  url: "/surveyKPI/assignments/addSurvey/" + globals.gCurrentProject,
			  data: { settings: assignString },
			  success: function(data, status) {
				  removeHourglass();
				  refreshAssignmentData();
			  }, error: function(data, status) {
				  removeHourglass();
				  if(data.responseText.indexOf("<html>") !== 0) {
					  alert(localise.set["c_error"] + " : " + data.responseText); 
				  } else {
					  alert(localise.set["msg_err_upd"]);
				  }

			  }
		});
		
	});
	*/
	
	/*
	$('#addNewTask').on('hidden.bs.modal', function () {
		  $('#map_alert').hide();
		});
	*/
	
	// Delete Tasks button 
	$('#deleteTasks').button().click(function () {
		bootbox.confirm(localise.set["msg_confirm_del"] + globals.gPendingUpdates.length + ' ' + localise.set["m_assign"] + 
				'?', function(result){
			if(result) {
				deleteData(globals.gPendingUpdates); 
			}
		});
	});
	
	// Create trigger to open modal to edit task parameters
	$('#show_task_params').button().click(function () {
		gTaskGroupIndex = -1;
		$('#task_params').modal("show");
	});

	$('#taskParamsSave').click(function(){
		updateTaskParams();
	});
	
	// Respond to a new NFC being selected
	$('#nfc_select').change(function(){
		$('#task_properties_title').val($(this).find(':selected').text());
	});
	
	enableUserProfileBS();										// Enable user profile button	
	$('#m_refresh').click(function(e) {	// Add refresh action
		refreshAssignmentData();
	}); 
	
	$('#tasks_print').button();									// Add button styling
	
	// Set up the start and end dates with date picker
	$('#startDate').datetimepicker({
		useCurrent: false,
		locale: gUserLocale || 'en'
	}).data("DateTimePicker").date(moment());
	
	$('#endDate').datetimepicker({
		useCurrent: false,
		locale: gUserLocale || 'en'
	}).data("DateTimePicker").date(moment());
	
	$('#task_properties_scheduledDate').datetimepicker({
		useCurrent: false,
		locale: gUserLocale || 'en'
	});
	
	/* 
	 * Set focus to first element on opening modals
	 */
	$('.modal').on('shown.bs.modal', function() {
		var $selections = $(this).find('input[type=text],textarea,select').filter(':visible:first');
		$selections.focus();
	});
	
	
	/*
	 * Take action on tab change to initialise tab contents
	 * Refer: http://stackoverflow.com/questions/20705905/bootstrap-3-jquery-event-for-active-tab-change
	 */
	$('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
		  var target = $(e.target).attr("href") // activated tab
		  console.log("panel change: " + target);
		  if(target === '#cal-view') {
			  if(!gCalendarInitialised) {
				  gCalendarInitialised = true;
				  initialiseCalendar();
			  }
		  } else if(target === '#map-view') {
			  if(!gMapInitialised) {
				  gMapInitialised = true;
				  initializeMap('map', 1, true, undefined);
				  refreshMapAssignments('map', globals.gTaskList);
			  }
		  }
	});
});


/*
 * Remove unselected address parameters
 */
function removeUnselected(taskParams) {
	var i,
		minimalParams = [];
	if(taskParams) {
		for( i = taskParams.length - 1; i >= 0; i-- ) {
			if(taskParams[i].selected) {
				minimalParams.push(taskParams[i]);
			}
		}
	}
	
		
	return minimalParams;
}

/*
 * Assign the specified user to all the pending assignment changes
 * User is optional
 */
function updatePendingAssignments(status, user) {
	
	var userObj = {id: user},
		i;
	
	for(i = 0; i < globals.gPendingUpdates.length; i++) {
		if(user) {
			globals.gPendingUpdates[i].user = userObj;
		}
		
		globals.gPendingUpdates[i].assignment_status = status;
	}
}

/*
 * Function called when the current project is changed
 */
function projectChanged() {

	globals.gCurrentProject = $('#project_name option:selected').val();
	globals.gCurrentSurvey = -1;
	loadSurveys(globals.gCurrentProject, undefined, false, false, surveyChanged);			// Get surveys
	refreshTaskGroupData();		// Get the task groups from the server
	saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
	getUsers(globals.gCurrentProject);										// Get the users that have access to this project
	$('#project_select').val(globals.gCurrentProject);	// Set the source project equal to the current project
	
}

function surveyChanged() {
	var updateResults = $('#update_results').is(':checked'),
		sId = $('#survey').val();
	
	$('#filter_option').empty();
	//if(updateResults) {
	//	$('#survey_to_complete').val(sId);
	//} 
	getLanguageList(sId, questionChanged, false, '#filter_language', false);
	setAddressOptions();
}

function languageChanged() {
	
	var language = $('#filter_language option:selected').val(),
		sId = $('#survey').val(),
		qList;
	
	qList = globals.gSelector.getSurveyQuestions(sId, language);
	
	if(!qList) {
		getQuestionList(sId, language, "-1", "-1", questionChanged, false, undefined);
	} else {
		setSurveyViewQuestions(qList, qId);
	}
}

function questionChanged() {
	var qId = $('#filter_question option:selected').val(),
		sId = $('#survey').val(),
		language = $('#filter_language option:selected').val(),
		$filter_option = $('#filter_option'),
		$filter_option_only = $('.filter_option_only'),
		$filter_integer_only = $('.filter_integer_only'),
		$filter_text_only = $('.filter_text_only'),
		$filter_date_only = $('.filter_date_only'),
		question = globals.gSelector.getQuestionDetails(sId, qId, language);
	
	$filter_option.empty();

	if(question) {
		gFilterqType = question.type;
		if(question.type === "select1" || question.type === "select") {
			$filter_option_only.show();
			$filter_integer_only.hide();
			$filter_text_only.hide();
			$filter_date_only.hide();
			
			addHourglass();
			// Get the meta data for the question
			$.ajax({
				url: questionMetaURL(sId, language, qId),
				cache: false,
				success: function(data) {
					var i,
						option,
						h = [],
						idx = -1;
						
					removeHourglass();
					console.log(data);
					if(data && data.options) {
						for(i = 0; i < data.options.length; i++) {
							option = data.options[i];
							h[++idx] = '<option value="';
							h[++idx] = option.value;
							h[++idx] = '">';
							h[++idx] = option.label;
							h[++idx] = '</option>';
						}
						$filter_option.append(h.join(''));
					}
				},
				error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						alert("Error: Failed to get options for the question: " + err);
					}
				}
			});	
		} else if(question.type === "int") {
			$filter_option_only.hide();
			$filter_integer_only.show();
			$filter_text_only.hide();
			$filter_date_only.hide();
			
		} else if(question.type === "date" || question.type == "dateTime") {
			$filter_option_only.hide();
			$filter_integer_only.hide();
			$filter_text_only.hide();
			$filter_date_only.show();
			
		} else {	// Default to text (string)
			$filter_option_only.hide();
			$filter_integer_only.hide();
			$filter_date_only.hide();
			$filter_text_only.show();
		}
	}
	
}

/*
 * Add the columns that the user can select to create address information
 */
function setAddressOptions() {
	
	var sId = $('#survey').val(),
		j,
		i;
	
	if(sId) {
		// Get the survey meta data
		addHourglass();
	 	$.ajax({
			url: "/surveyKPI/survey/" + sId + "/getMeta",
			dataType: 'json',
			success: function(data) {
				
				addHourglass();
				
				// Get the data for the top level table
				$.ajax({
					url: "/surveyKPI/table/" + data.top_table,
					dataType: 'json',
					cache: false,
					success: function(table) {
						var colname,
							coltype,
							sMedia,
							h = [],
							idx = -1,
							i,j;
						removeHourglass();
						
						gTaskParams = [];
						j = 0;
						for(i = 0; i < table.columns.length; i++) {
							colname = table.columns[i].name;
							coltype = table.columns[i].type;
							
							if(colname !== "prikey" && colname !== "parkey" && 
									colname !== "the_geom" &&
									colname !== "geo_type" &&
									colname.indexOf("_") !== 0) {
								
								if(coltype && (coltype === "image" || coltype === "audio" || coltype === "video")) {
									isMedia = true;
								} else {
									isMedia = false;
								}
								gTaskParams[j++] = {selected:false, name:colname, isBarcode:false, isMedia: isMedia}; 
								
							}
						}
	
						displayTaskParams();
	
					},
					error: function(xhr, textStatus, err) {
						removeHourglass();
						if(xhr.readyState == 0 || xhr.status == 0) {
				              return;  // Not an error
						} else {
							alert("Error: Failed to get table description: " + err);
						}
					}
				});	
				
				removeHourglass();
			},
			error: function(data) {
				removeHourglass();			
				bootbox.alert("Error failed to get data for survey:" + sId);
			}
		});
	}
 	
 	
}
/*
 * Get the list of users from the server
 */
function getUsers(projectId) {
	var $users = $('.users_select,#users_filter'),
	i, user,
	h = [],
	idx = -1;

	$users.empty();
	$('#users_filter').append('<option value="0">All Users</options>');
	$('#users_filter').append('<option value="-1">Unassigned Users</options>');

	$('#users_select_new_task').append('<option value="-1">Unassigned</options>');
	$('#users_task_group, #users_select_user').append('<option value="-1">Unassigned</options>');
	
	$.ajax({
		url: "/surveyKPI/userList",
		cache: false,
		success: function(data) {

			for(i = 0; i < data.length; i++) {
				user = data[i];
				// Check that this user has access to the project
				
				if(!projectId || userHasAccessToProject(user,projectId)) {
					h[++idx] = '<option value="';
					h[++idx] = user.id;
					h[++idx] = '">';
					h[++idx] = user.name;
					h[++idx] = '</option>';
				}
			}
			$users.append(h.join(''));
		},
		error: function(xhr, textStatus, err) {
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get list of users: " + err);
			}
		}
	});	
}

function userHasAccessToProject(user, projectId) {
	var i;
	for(i = 0; i < user.projects.length; i++) {
		if(user.projects[i].id == projectId) {
			return true;
		}
	}
	return false;
}

/*
 * Save any changes made to the selected tasks
 */
function saveData(data) {
	
	var assignString = JSON.stringify(data);

	$.ajax({
		  type: "POST",
		  url: "/surveyKPI/assignments",
		  data: { settings: assignString },
		  success: function(data, status) {
			  refreshAssignmentData();
		  }, error: function(data, status) {
			  console.log(data);
			  alert("Error: Failed to update tasks"); 
		  }
	});
}

function deleteData(data) {
	
	var deleteString = JSON.stringify(data);

	addHourglass();
	$.ajax({
		  type: "DELETE",
		  url: "/surveyKPI/assignments",
		  data: { settings: deleteString },
		  success: function(data, status) {
			  removeHourglass();
			  refreshAssignmentData();
			  globals.gPendingUpdates = [];
		  }, error: function(data, status) {
			  console.log(data);
			  removeHourglass();
			  alert("Error: Failed to delete tasks"); 
		  }
	});
}

/*
 * Get the task groups from the server
 */
function refreshTaskGroupData() {

	if(typeof globals.gCurrentProject !== "undefined" && globals.gCurrentProject != -1) {
		addHourglass();
		$.ajax({
			url: "/surveyKPI/tasks/taskgroups/" + globals.gCurrentProject,
			cache: false,
			dataType: 'json',
			success: function(data) {
				refreshTableTaskGroups(data);
				removeHourglass();
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Failed to get task group data");
				}
			}
		});
	}
}

/*
 * Update the table view of task groups
 */
function refreshTableTaskGroups(taskgroups) {
	
	var h =[],
		idx = -1,
		i,
		grp,
		firstTg,
		hasCurrentTg = false;
		
	//if(typeof taskgroups != "undefined") {
	//	$('#taskgroup_table').empty().generateTaskGroupTable(
	//			{
	//				'data': taskgroups
	//			});
	//}
	if(typeof taskgroups != "undefined") {
		for(i = 0; i < taskgroups.length; i++) {
			grp = taskgroups[i];
			h[++idx] = '<option value="';
			h[++idx] = grp.tg_id;
			h[++idx] = '">';
			h[++idx] = grp.name;
			h[++idx] = '</option>';
			
			if(i==0) {
				firstTg = grp.tg_id
			}
			if(grp.tg_id == globals.gCurrentTaskGroup) {
				hasCurrentTg = true;
			}
		}
	}
	$('#taskgroup').html(h.join(''));
	
	// Set current value for the task group
	if(!hasCurrentTg) {
		globals.gCurrentTaskGroup = firstTg;
	}
	$('#taskgroup').val(globals.gCurrentTaskGroup);
	
	
	
	
	// Add events onto the task group table
	/*
	$("span.pie", '#taskgroup_table').peity("pie", {
		  fill: ["green", "red"]
	});
	
	$('input', '#taskgroup_table').iCheck({
	    checkboxClass: 'icheckbox_square-green',
	    radioClass: 'iradio_square-green'
	});
	
	$('.taskgroup', '#taskgroup_table').on('ifChecked', function(event){
		globals.gCurrentTaskGroup = $(this).val();
		refreshAssignmentData(-1);
	});
	*/
	$('#taskgroup').change(function() {
		globals.gCurrentTaskGroup = $(this).val();
		saveCurrentProject(undefined, undefined, globals.gCurrentTaskGroup);
		refreshAssignmentData();
	})
	refreshAssignmentData();
	
}

/*
 * Get the assignments from the server
 */
function refreshAssignmentData() {
	
	var user_filter = $('#users_filter').val(),
		completed = $('#filter_completed').is(':checked');
	
	if(typeof globals.gCurrentTaskGroup !== "undefined" && globals.gCurrentTaskGroup != -1) {
		addHourglass();
		$.ajax({
			url: "/surveyKPI/tasks/assignments/" + 
					globals.gCurrentTaskGroup +
					"?user=" + user_filter +
					"&completed=" + completed,
			cache: false,
			dataType: 'json',
			success: function(data) {
				removeHourglass();
				globals.gTaskList = data;
				refreshMapAssignments('map', globals.gTaskList);
				refreshTableAssignments();
				initialiseCalendar();
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Failed to get table data");
				}
			}
		});
	}
}

/*
 * Update the table view of task data
 */
function refreshTableAssignments() {
	
	var tasks = globals.gTaskList.features,
		h = [],
		idx = -1,
		i,
		item;
	
	if(typeof tasks != "undefined") {
		
		$('#task_table_body').empty().html(getTableBody(tasks));
	
		$('input', '#task_table_body').iCheck({
		    checkboxClass: 'icheckbox_square-green',
		    radioClass: 'iradio_square-green'
		});
		
		
		// Respond to selection of a task
		$('input', '#task_table_body').on('ifChanged', function(event){
			var $this = $(this),
				idx = $this.val(),
				selected = $this.is(':checked');
			

			globals.gTaskList.features[idx].properties.selected = selected;
			refreshMapAssignments('map', globals.gTaskList);
		});
		
		// Respond to clicking on task edit button
		$(".task_edit", '#task_table_body').click(function() {
			var $this = $(this),
				idx = $this.val(),
				task = gTasks[idx].properties,
				taskFeature = gTasks[idx];
			
			editTask(false, task, taskFeature);
		});
		
		// Show barcodes
		$(".tasks").find('.barcode').each(function(index) {
			var $bcElement = $(this);
			$bcElement.barcode($bcElement.text(), "code11");
		});
		
		gTasks = tasks;
		
		/*
		 * Function to save new ad hoc tasks
		 * Deprecate
		 *
		$('.save_new_task').button().click(function () {
			//var taskSource = $('input[name=task_source]:checked', '#assign_survey_form').val(),
			var s_id = $('#survey').val(),
				$this = $(this),
				taskCount = $('#new_task_count').html(),
				taskCountInt = parseInt(taskCount);
			
		
			if(typeof s_id === "undefined" || s_id === null) {
				alert(localise.set["msg_err_wait"]);
				return;
			}
			
			globals.gCurrentUserName = $('#users_select_new_task option:selected').text();
			globals.gCurrentUserId = $('#users_select_new_task option:selected').val();
			registerForNewTasks();
			
			// Reset buttons
			$this.hide();
			$this.siblings('.add_new_task').show();
			
			// Notify the user if we are adding a task without location
			$('#ts_alert').hide().text("");
			if(taskCountInt == 0) {
				$('#new_task_count').html(1);
				$('#ts_alert').show().text("Adding task without location");
			}

			gTaskGroupId = $this.val();		
			$('#addNewTask').modal("show");  // open the modal
		});
		*/
		
		/*
		 * Function to delete a task group
		 * Deprecate
		 *
		$('.delete_task_group').button().click(function () {
			
			var tg_id = $(this).val();
			
			bootbox.confirm(localise.set["msg_confirm_del"] + ' ' + localise.set["msg_confirm_tasks"], function(result){
				if(result) {
					addHourglass();
					$.ajax({
						  type: "DELETE",
						  url: "/surveyKPI/assignments/" + tg_id + "?completed=true",
						  success: function(data, status) {
							  removeHourglass();
							  refreshAssignmentData();
						  }, error: function(data, status) {
							  removeHourglass();
							  console.log(data);
							  alert("Error: Failed to delete task group"); 
						  }
					});	
				}
			});

		});
		*/
		
	}
}

/*
 * Edit an existing task or create a new one
 */
function editTask(isNew, task, taskFeature) {
	var scheduleDate;

	gCurrentTaskFeature = taskFeature;
	
	if(isNew) {
		$('#taskPropLabel').html(localise.set["t_add_task"]);
	} else {
		$('#taskPropLabel').html(localise.set["t_edit_task"]);
	}
	
	/*
	 * Set up data
	 */
	$('#task_properties_taskid').val(task.id);
	$('#task_properties_repeat').prop('checked', task.repeat);
	$('#task_properties_title').val(task.name);
	if(task.scheduleAt) {
		$('#task_properties_scheduledDate').data("DateTimePicker").date(localTime(task.scheduleAt));
	} 

	$('#nfc_select').val(task.location_trigger);
	if(task.update_id && task.update_id.length > 0) {
		$('#initial_data').html(getInitialDataLink(task.form_id, task.update_id) + 
				' ' + getInitialDataUrl(task.form_id, task.update_id));
	}
	
	$('#task_properties').modal("show"); 
	
	if(!mapData['mapModal']) {
		setTimeout(function() {
				initializeMap('mapModal', 14, false, clickOnMap);
				if(gCurrentTaskFeature.geometry.coordinates[0] || gCurrentTaskFeature.geometry.coordinates[1]) {
					addDraggableMarker('mapModal', 
							new L.LatLng(gCurrentTaskFeature.geometry.coordinates[0], gCurrentTaskFeature.geometry.coordinates[0]),
							onDragEnd);
				} else {
					gClickOnMapEnabled = true;
				}
			}, 500);
	}
	
}

/*
 * Respond to a click on the map
 */
function clickOnMap(latlng) {
	var x = 1,
		coords = [];
	
	if(gClickOnMapEnabled) {
		
		gClickOnMapEnabled = false;
		
		coords[0] = latlng.lng;
		coords[1] = latlng.lat;
		
		gCurrentTaskFeature.geometry.coordinates = coords;
		addDraggableMarker('mapModal', latlng,	onDragEnd);

	}
}

/*
 * Respond to a drag of the task location
 */
function onDragEnd(latlng) {
	var x = 1,
		coords = [];
	
	coords[0] = latlng.lng;
	coords[1] = latlng.lat;
	
	gCurrentTaskFeature.geometry.coordinates = coords;
}

/*
 * Show the task parameters in the modal
 */
function displayTaskParams() {

	var h = [],
	idx = -1,
	addressObj = null,
	i;
	
	h[++idx] = '<table class="table table-striped">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th>Selected</th>';
	h[++idx] = '<th>Parameter Name</th>';
	h[++idx] = '<th>Barcode</th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody>';



	if(gTaskParams) {
		for(i = 0; i < gTaskParams.length; i++) {
			h[++idx] = '<tr>';
			h[++idx] = '<td class="task_selected"><input type="checkbox" name="isSelected" value="';
			h[++idx] = gTaskParams[i].name;
			h[++idx] = '" ';
			if(gTaskParams[i].selected !== false) {
				h[++idx] = 'checked="checked"';
			} 
			h[++idx] = '></td>';
			h[++idx] = '<td class="task_name">' + gTaskParams[i].name + '</td>';
			h[++idx] = '<td class="task_isBarcode"><input type="checkbox" name="isBarcode" value="';
			h[++idx] = gTaskParams[i].name;
			h[++idx] = '" ';
			if(gTaskParams[i].isBarcode !== false) {
				h[++idx] = 'checked="checked"';
			} 
			h[++idx] = '></td>';
			h[++idx] = '</tr>';
		}

	}

	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
	
	$('#task_params_table').empty().append(h.join(''));
	
}

/*
 * Get the updated parameters from the modal and save back into the global parameters object
 */
function updateTaskParams() {
	
	var name,
		selected,
		isBarcode,
		isMedia,
		updatedTaskParams = [];
	
	
	$('#task_params_table').find('tbody tr').each(function(index){
		name = $(this).find('td.task_name').text();
		selected = $(this).find('td.task_selected input').is(':checked');
		isBarcode = $(this).find('td.task_isBarcode input').is(':checked');
		isMedia = gTaskParams[index].isMedia;
		updatedTaskParams[index] = {selected: selected, name: name, isBarcode: isBarcode, isMedia: isMedia};
	});
	gTaskParams = updatedTaskParams;
	
	if(gTaskGroupIndex !== -1) {	// An existing set of task parameters was being edited
		// Update the array of task params
		gTasks.task_groups[gTaskGroupIndex].tg_address_params = JSON.stringify(gTaskParams);
		// Update the task params in the database TODO
		
		refreshAssignmentData();
		//refreshTableAssignments(gTasks);	// Refresh the table view
	}
}

/*
 * Create HTML for task table
 * New
 */
function getTableBody(tasks) {
	var surveyName,
		tab = [],
		idx = -1,
		i;

	for(i = 0; i < tasks.length; i++) {
		task = tasks[i];
		tab[++idx] = '<tr>';
			tab[++idx] = addSelectCheckBox(false, i, false);
			
			tab[++idx] = '<td>';
				tab[++idx] = task.properties.form_name;	
			tab[++idx] = '</td>';		
	
			tab[++idx] = '<td>';			// Task name
				tab[++idx] = task.properties.name;		
			tab[++idx] = '</td>';
		
			tab[++idx] = '<td class="' + getStatusClass(task.properties.status) + '">';	// status
				tab[++idx] = task.properties.status;
			tab[++idx] = '</td>';
			
			tab[++idx] = '<td>';		// Assignee	
				tab[++idx] = task.properties.assignee_name;
			tab[++idx] = '</td>';
			
			tab[++idx] = '<td>';			// NFC
				if(task.properties.location_trigger && task.properties.location_trigger.length > 0) {
					if(task.properties.location_trigger.indexOf('{') == 0) {
						tab[++idx] = '<i class="fa fa-crosshairs"></i>';	// Geo fence
					} else {
						tab[++idx] = '<i class="fa fa-wifi"></i>';			// NFC
					}
				}		
			tab[++idx] = '</td>';
			
			tab[++idx] = '<td>';			// Existing data
			if(task.properties.update_id && task.properties.update_id.length > 0) {
				tab[++idx] = getInitialDataLink(task.properties.form_id, task.properties.update_id);
			}		
			tab[++idx] = '</td>';
			
			tab[++idx] = '<td>';			// Blocked
			if(task.properties.blocked) {
				tab[++idx] = '<i class="fa fa-ban has_tt" title="Survey Blocked"></i>';	// Survey Blocked
			} 
			tab[++idx] = '</td>';
			
			tab[++idx] = '<td>';		// scheduled
				tab[++idx] = task.properties.scheduled_at;
			tab[++idx] = '<td>';			// edit
			tab[++idx] ='<button class="btn btn-default task_edit" value="';
			tab[++idx] = i;
			tab[++idx] = '" type="button"><i class="fa fa-edit"></i></button>';
			tab[++idx] = '</td>';		
			
		
		tab[++idx] = '</tr>';
	}
	return tab.join('');
	
}

function getInitialDataLink(form_id, update_id) {
	var tab = [];
		idx = -1;
		
	tab[++idx] = '<a href="';
	tab[++idx] = getWebFormUrl(task.properties.form_id, task.properties.update_id);
	tab[++idx] = '" target="_blank">'
	tab[++idx] = '<i class="fa fa-file-text"></i>';	// Edit existing data
	tab[++idx] = '</a>';
	
	return tab.join('');
}

function getWebFormUrl(form_id, update_id) {
	var url;
	
	url = "/webForm/" + form_id;
	if(update_id) {
		url += "?datakey=instanceid&datakeyvalue=" + update_id;
	}
	
	return url;
}

function getStatusClass(status) {
	
	var statusClass = "";
	
	if(status === "new") {
		statusClass = "bg-danger";
	} else if(status === "submitted") {
		statusClass = "bg-success";
	} else if(status === "accepted") {
		statusClass = "bg-warning";
	}
	return statusClass;
}

/*
 * Import a task group from a spreadsheet
 */
function importTaskGroup() {
	var url = '/surveyKPI/tasks/xls/' + globals.gCurrentProject,
		name = $('#taskgroup option:selected').text();

	$('#tg_to_import').val(globals.gCurrentTaskGroup);
	var f = document.forms.namedItem("loadtasks");
	var formData = new FormData(f);
	
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
			  $('#import_taskgroup').modal("hide");
			  $('#load_tasks_alert').show().removeClass('alert-danger').addClass('alert-success').html();
			  refreshAssignmentData();
		  },
		  error: function(xhr, textStatus, err) {
			  removeHourglass(); 
			  var msg = xhr.responseText;
			  $('#load_tasks_alert').show().removeClass('alert-success').addClass('alert-danger').html(msg);
			 
		  }
	});
}



/*
 * Initialise the calendar
 */
function initialiseCalendar() {
	var date = new Date();
	var d = date.getDate();
	var m = date.getMonth();
	var y = date.getFullYear();
	var events =  [
	                                   {
	                                       title: 'All Day Event',
	                                       start: new Date(y, m, 1)
	                                   },
	                                   {
	                                       title: 'Long Event',
	                                       start: new Date(y, m, d-5),
	                                       end: new Date(y, m, d-2)
	                                   },
	                                   {
	                                       id: 999,
	                                       title: 'Repeating Event',
	                                       start: new Date(y, m, d-3, 16, 0),
	                                       allDay: false
	                                   },
	                                   {
	                                       id: 999,
	                                       title: 'Repeating Event',
	                                       start: new Date(y, m, d+4, 16, 0),
	                                       allDay: false
	                                   },
	                                   {
	                                       title: 'Meeting',
	                                       start: new Date(y, m, d, 10, 30),
	                                       allDay: false
	                                   },
	                                   {
	                                       title: 'Lunch',
	                                       start: new Date(y, m, d, 12, 0),
	                                       end: new Date(y, m, d, 14, 0),
	                                       allDay: false
	                                   },
	                                   {
	                                       title: 'Birthday Party',
	                                       start: new Date(y, m, d+1, 19, 0),
	                                       end: new Date(y, m, d+1, 22, 30),
	                                       allDay: false
	                                   },
	                                   {
	                                       title: 'Click for Google',
	                                       start: new Date(y, m, 28),
	                                       end: new Date(y, m, 29),
	                                       url: 'http://google.com/'
	                                   }
	                               ];

	
	$('#calendar').fullCalendar({
	    header: {
	        left: 'prev,next today',
	        center: 'title',
	        right: 'month,agendaWeek,agendaDay'
	    },
	    editable: true,
	    droppable: true, 
	    drop: function() {
	    	$(this).remove();
	    },
	    events: [
                 {
                     title: 'All Day Event',
                     start: new Date(y, m, 1)
                 },
                 {
                     title: 'Long Event',
                     start: new Date(y, m, d-5),
                     end: new Date(y, m, d-2)
                 },
                 {
                     id: 999,
                     title: 'Repeating Event',
                     start: new Date(y, m, d-3, 16, 0),
                     allDay: false
                 },
                 {
                     id: 999,
                     title: 'Repeating Event',
                     start: new Date(y, m, d+4, 16, 0),
                     allDay: false
                 },
                 {
                     title: 'Meeting',
                     start: new Date(y, m, d, 10, 30),
                     allDay: false
                 },
                 {
                     title: 'Lunch',
                     start: new Date(y, m, d, 12, 0),
                     end: new Date(y, m, d, 14, 0),
                     allDay: false
                 },
                 {
                     title: 'Birthday Party',
                     start: new Date(y, m, d+1, 19, 0),
                     end: new Date(y, m, d+1, 22, 30),
                     allDay: false
                 },
                 {
                     title: 'Click for Google',
                     start: new Date(y, m, 28),
                     end: new Date(y, m, 29),
                     url: 'http://google.com/'
                 }
             ]
	});
	
	 $('#external-events div.external-event').each(function() {

         // store data so the calendar knows to render an event upon drop
         $(this).data('event', {
             title: $.trim($(this).text()), // use the element's text as the event title
             stick: true // maintain when user navigates (see docs on the renderEvent method)
         });

         // make the event draggable using jQuery UI
         $(this).draggable({
             zIndex: 1111999,
             revert: true,      // will cause the event to go back to its
             revertDuration: 0  //  original position after the drag
         });

     });

	
}


});