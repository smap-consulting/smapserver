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
	gTaskGroupId = 0;		// Currently selected task group
	
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

	// Initialise the map
	initializeMap();
	$('#layers').show();
	
	// Change Functions
	$('.users_select').change(function () {
		globals.gCurrentUserName = $('option:selected', $(this)).text();
		globals.gCurrentUserId = $('option:selected', $(this)).val();
	});

	// Add zoom to data button
	$('#zoomData').button().click(function () {
		zoomToFeatureLayer();
	});
	
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
		var url = "/surveyKPI/assignments/properties";
		
		$('#scheduleAtUTC').val(utcTime($('#scheduleAt').val()));	// Set the UTC date
		
		var f = document.forms.namedItem("taskProperties");
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
				  success: function(data, status) {
					  removeHourglass();
					  $('#addTask').modal("hide");
					  refreshAssignmentData();
					  clearNewTasks();
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
	 * Save new ad-hoc tasks in a group
	 */
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
				  clearNewTasks();
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
	
	$('#addNewTask').on('hidden.bs.modal', function () {
		  $('#map_alert').hide();
		})
	
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
	$('#refreshMenu').click(function(e) {	// Add refresh action
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
	 * Initialise the calendar
	 */
    var date = new Date();
    var d = date.getDate();
    var m = date.getMonth();
    var y = date.getFullYear();

    $('#calendar').fullCalendar({
        header: {
            left: 'prev,next today',
            center: 'title',
            right: 'month,agendaWeek,agendaDay'
        },
        editable: true,
        droppable: true, // this allows things to be dropped onto the calendar
        drop: function() {
            // is the "remove after drop" checkbox checked?
            if ($('#drop-remove').is(':checked')) {
                // if so, remove the element from the "Draggable Events" list
                $(this).remove();
            }
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
	
	if(typeof taskgroups != "undefined") {
		$('#taskgroup_table').empty().generateTaskGroupTable(
				{
					'data': taskgroups
				});
	}
	
	// Add events onto the task group table
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
	refreshAssignmentData(-1, true);
	
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
				globals.gTaskList = data;
				refreshMapAssignments();
				refreshTableAssignments();
				removeHourglass();
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

function refreshTableAssignments() {
	
	var tasks = globals.gTaskList;
	
	if(typeof tasks != "undefined") {
		$('#task_table_body').empty().generateTaskTable(
				{
					'data': globals.gTaskList,
					'showCompleted' : $('#filter_completed').prop('checked')
				});
		
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
			refreshMapAssignments();
		});
		
		// Respond to clicking on task edit button
		$(".task_edit", '#task_table_body').click(function() {
			var $this = $(this),
				idx = $this.val(),
				task = gTasks.features[idx].properties,
				scheduleDate;
		            	
			console.log(task);
		
			// open the properties dialog
			$('#task_properties_taskid').val(task.id);
			$('#task_properties_repeat').prop('checked', task.repeat);
			$('#task_properties_title').val(task.title);
			$('#task_properties_scheduledDate').data("DateTimePicker").date(localTime(task.scheduleAt));
			$('#nfc_select').val(task.location_trigger);
			$('#task_properties').modal("show");  

		});
		
		// Show barcodes
		$(".tasks").find('.barcode').each(function(index) {
			var $bcElement = $(this);
			$bcElement.barcode($bcElement.text(), "code11");
		});
		
		gTasks = tasks;
		
		// Add function to add tasks to group
		$('.add_new_task').button().click(function () {
			//var taskSource = $('input[name=task_source]:checked', '#assign_survey_form').val(),
			var s_id = $('#survey').val(),
				$this = $(this);
			
			/*
			 * Make sure we have the survey id
			 */
			if(typeof s_id === "undefined" || s_id === null) {
				alert("Either waiting for the server or there are no surveys in this project to assign to a user. " +
						"If the project does have surveys then try again in a few seconds");
				return;
			}

			// Enable selection of locations for new tasks
			globals.gCurrentUserName = $('#users_select_new_task option:selected').text();
			globals.gCurrentUserId = $('#users_select_new_task option:selected').val();
			registerForNewTasks();
			
			$('.save_new_task').hide();					// Close any other create new task buttons
			$('.add_new_task').show();
			
			$this.hide();								// Enable the button to save new tasks
			$this.siblings('.save_new_task').show();
			
			// Reset any pending tasks
			$('#new_task_count').html(0);
			$('#map_alert').show().text(localise.set["t_add_tasks"]);
			window.scrollTo(500, 0);
		});
		
		/*
		 * Function to save new ad hoc tasks
		 */
		$('.save_new_task').button().click(function () {
			//var taskSource = $('input[name=task_source]:checked', '#assign_survey_form').val(),
			var s_id = $('#survey').val(),
				$this = $(this),
				taskCount = $('#new_task_count').html(),
				taskCountInt = parseInt(taskCount);
			
			
			/*
			 * Make sure we have the survey id
			 */
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
		
		/*
		 * Function to delete a task group
		 */
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
		
	}
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

});