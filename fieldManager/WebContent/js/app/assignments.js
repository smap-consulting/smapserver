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

define(['jquery','bootstrap', 'app/map-ol-mgmt', 'common', 'localise', 'bootbox'], 
		function($, bootstrap, ol_mgmt, common, lang, bootbox) {
	
							// The following globals are only in this java script file
var gTasks,					// Object containing the task data retrieved from the database
	gTaskGroupIndex = -1,	// Currently selected task group
	gTaskParams = [],		// Parameters for a new task	
	gFilterqType,			// The type of the filter question select, select1, int, string
	gUserFilter = "0";		// Default to all users
	
$(document).ready(function() {
	
	globals.gRegion = {};	// Initialise global values
	globals.gRegions = undefined;
	
	localise.setlang();		// Localise HTML
	
	getLoggedInUser(projectChanged, false, true, undefined);
	
	// Set change function on projects
	$('#project_name').change(function() {
		projectChanged();
 	 });
	
	/*
	 * Change function on source project when tasks are being copied from an existing survey
	 * The source project is the project that data is being copied from, 
	 *  it can be different from the current project
	 */
	// 
	$('#project_select').change(function() {
		var sourceProject = $('#project_select option:selected').val();
		loadSurveys(sourceProject, "#survey", false, false, surveyChanged);			// Get surveys
		
		// Disable the update results checkbox if the source project and current project are different
		if(sourceProject === globals.gCurrentProject) {
			$('#update_results').attr("disabled", false);
		} else {
			$('#update_results').attr("disabled", true);
			$('#update_results').attr("checked", false);
		}
 	 });
	
	// Add a trigger to respond to the clicking of "filter tasks" 
	$('#filter_results_check').attr('checked', false).click(function() {
		$('#filter_results').toggle();
	});
	
	// Add a trigger to respond to the clicking of "update results" 
	$('#update_results').click(function() {
		var updateResults = $('#update_results').is(':checked');
		if(updateResults) {
			$('#survey_to_complete').val($('#survey').val()).attr('disabled', 'disabled');
		} else {
			$('#survey_to_complete').removeAttr('disabled');
		}
	});
	
	// Add response to the filters being changed
	$('.task_filter').change(function() {
		gUserFilter = $('#users_filter').val();
		refreshAssignmentData(gUserFilter);
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
	
	// Add response to the source of task assignment being changed
	/*
	$('input[name=task_source]', '#assign_survey_form').change(function() {
		var taskSource = $('input[name=task_source]:checked', '#assign_survey_form').val();

		if(taskSource === "new") {
			$('#task_source_new_hide').show();
			$('#task_source_survey_hide').hide();
			registerForNewTasks();
		} else {
			$('#task_source_new_hide').hide();
			$('#task_source_survey_hide').show();
			clearNewTasks();
		}
	});
	*/

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
		zoomTo("assignments");
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
		updatePendingAssignments($("accepted", '#users_select_user').val());
        saveData(globals.gPendingUpdates);
		refreshAssignmentData(gUserFilter);
		globals.gCurrentUserId = undefined;
		globals.gCurrentUserName = undefined;
		globals.gPendingUpdates = [];
	})
	
		// Create new task group
		$('#addTaskGroup').button().click(function () {
			var taskSource = $('input[name=task_source]:checked', '#assign_survey_form').val(),
				s_id = $('#survey').val();
			
			/*
			 * Make sure we have the survey id
			 */
			if(typeof s_id === "undefined" || s_id === null) {
				alert("Either waiting for the server or there are no surveys in this project to assign to a user. " +
						"If the project does have surveys then try again in a few seconds");
				return;
			}
			
			if(taskSource === "new") {
				globals.gCurrentUserName = $('#users_select_new_task option:selected').text();
				globals.gCurrentUserId = $('#users_select_new_task option:selected').val();
				registerForNewTasks();
			}
			// open the dialog
			$('#addTask').modal("show");

		});
	
	/*
	$('#assign_user').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:false,
			title: "Assign Users",
			position: { my: "left top", at: "left top", of:'#aside'},
			width: 250,
			show:"drop",
			buttons: [
		        {
		        	text: "Cancel",
		        	click: function() {
		        		refreshAssignmentData(gUserFilter);
		        		globals.gCurrentUserId = undefined;
		        		globals.gCurrentUserName = undefined;
		        		globals.gPendingUpdates = [];
		        		$(this).dialog("close");
		        	}
		        },
		        {
		        	text: "Save",
		        	click: function() {
		        		saveData(globals.gPendingUpdates);
		        		refreshAssignmentData(gUserFilter);
		        		globals.gCurrentUserId = undefined;
		        		globals.gCurrentUserName = undefined;
		        		globals.gPendingUpdates = [];
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	*/
	
	/*
	$('#assign_survey').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:false,
			title: "Create new tasks",
			show:"drop",
			position: { my: "left top", at: "left top", of:'#aside'},
			buttons: [
		        {
		        	text: "Add to Tasks",
		        	click: function() {
		        		var error = false,
		        			taskSource = $('input[name=task_source]:checked', '#assign_survey_form').val(),
		        			assignObj = {},
		        			assignString,
		        			url,
		        			filterObj = {},
		        			filterqId,
		        			filteroId,
		        			source_survey;
		        		
	        			assignObj["task_group_name"] = $('#task_group_name').val();	// The Name of the task group
		        		assignObj["survey_name"] = $('#survey_to_complete option:selected').text();	// The display name of the survey to complete
		        		assignObj["project_name"] = $('#project_select option:selected').text();	// The name of the project that this survey is in
		        		assignObj["form_id"] = $('#survey_to_complete option:selected').val(); 						// The form id is the survey id of the survey used to complete the task!
		        		
		        		if(taskSource === "new") {
		        		       			
		        			assignObj["source_survey_id"] = -1;
			        		assignObj["new_tasks"] = $.parseJSON(getTasksAsGeoJSON());
		        			
		        		} else {
		        			source_survey = $('#survey').val(); 						// The survey that provides the existing results	
		        			if(!source_survey) {
		        				source_survey = -1;
		        			}
			        		assignObj["source_survey_id"] = source_survey; 
			        		assignObj["address_columns"] = gTaskParams;
			        		assignObj["source_survey_name"] = $('#survey option:selected').text();		// The display name of the survey that will provide the source locations and initial data
			        		assignObj["update_results"] = $('#update_results').is(':checked'); 			// Set to true if the existing survey is to be updated	
			        		
			        		// Add filter if filter checkbox has been checked
			        		if($('#filter_results_check').attr('checked')) {
			        			

		        				filterObj["qType"] = gFilterqType;
			        			filterObj["qId"] = $('#filter_question option:selected').val();
			        			filterObj["oValue"] = $('#filter_option option:selected').val();
			        			filterObj["qText"] = $('#filter_text').val();
			        			if(gFilterqType === "int") {
			        				filterObj["qInteger"] = $('#filter_integer').val();
			        			}
			        			filterObj["lang"] = $('#filter_language option:selected').val();
			        			assignObj["filter"] = filterObj;

			        		}
		        		}
		        		if(!error) {
			            	assignString = JSON.stringify(assignObj);
			            	globals.gCurrentUserId = undefined;
			            	globals.gCurrentUserName = undefined;
		        			
		        			addHourglass();
		            		$.ajax({
		            			  type: "POST",
		            			  contentType: "application/json",
		            			  dataType: "json",
		            			  url: "/surveyKPI/assignments/addSurvey/" + globals.gCurrentProject,
		            			  data: { settings: assignString },
		            			  success: function(data, status) {
		            				  removeHourglass();
		            				  refreshAssignmentData(gUserFilter);
		            				  clearNewTasks();
		            			  }, error: function(data, status) {
		            				  removeHourglass();
		            				  if(data.responseText.indexOf("<html>") !== 0) {
		            					  alert("Error: " + data.responseText); 
		            				  } else {
		            					  alert("Error adding tasks");
		            				  }

		            			  }
		            		});
		            		
			        		$(this).dialog("close");
		        		} 
		        	},
		        }, 
		        {
		        	text: "Close",
		        	click: function() {
		    			clearNewTasks();
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	*/
	
	// Add new group save
	$('#addNewGroupSave').click(function () {
		var error = false,
		//taskSource = $('input[name=task_source]:checked', '#assign_survey_form').val(),
		assignObj = {},
		assignString,
		url,
		filterObj = {},
		filterqId,
		filteroId,
		source_survey;
	
	assignObj["task_group_name"] = $('#task_group_name').val();	// The Name of the task group
	assignObj["survey_name"] = $('#survey_to_complete option:selected').text();	// The display name of the survey to complete
	assignObj["project_name"] = $('#project_select option:selected').text();	// The name of the project that this survey is in
	assignObj["form_id"] = $('#survey_to_complete option:selected').val(); 						// The form id is the survey id of the survey used to complete the task!
	
	/*
	if(taskSource === "new") {
	       			
		assignObj["source_survey_id"] = -1;
		assignObj["new_tasks"] = $.parseJSON(getTasksAsGeoJSON());
		
	} else {
	*/
		source_survey = $('#survey').val(); 						// The survey that provides the existing results	
		if(!source_survey) {
			source_survey = -1;
		}
		assignObj["source_survey_id"] = source_survey; 
		assignObj["address_columns"] = gTaskParams;
		assignObj["source_survey_name"] = $('#survey option:selected').text();		// The display name of the survey that will provide the source locations and initial data
		assignObj["update_results"] = $('#update_results').is(':checked'); 			// Set to true if the existing survey is to be updated	
		
		// Add filter if filter checkbox has been checked
		if($('#filter_results_check').attr('checked')) {
			

			filterObj["qType"] = gFilterqType;
			filterObj["qId"] = $('#filter_question option:selected').val();
			filterObj["oValue"] = $('#filter_option option:selected').val();
			filterObj["qText"] = $('#filter_text').val();
			if(gFilterqType === "int") {
				filterObj["qInteger"] = $('#filter_integer').val();
			}
			filterObj["lang"] = $('#filter_language option:selected').val();
			assignObj["filter"] = filterObj;

		}
	//}
	if(!error) {
    	assignString = JSON.stringify(assignObj);
    	globals.gCurrentUserId = undefined;
    	globals.gCurrentUserName = undefined;
		
		addHourglass();
		$.ajax({
			  type: "POST",
			  contentType: "application/json",
			  dataType: "json",
			  url: "/surveyKPI/assignments/addSurvey/" + globals.gCurrentProject,
			  data: { settings: assignString },
			  success: function(data, status) {
				  removeHourglass();
				  refreshAssignmentData(gUserFilter);
				  clearNewTasks();
			  }, error: function(data, status) {
				  removeHourglass();
				  if(data.responseText.indexOf("<html>") !== 0) {
					  alert("Error: " + data.responseText); 
				  } else {
					  alert("Error adding tasks");
				  }

			  }
		});
		
	} 
	});
	
	// Delete Tasks button and dialog
	$('#deleteTasks').button().click(function () {
		//globals.gDeleteSelected = true;
		//$('#assignments_delete').dialog("open");
		bootbox.confirm('Are you sure you want to delete these tasks?', function(result){
			if(result) {
				//updatePendingAssignments("deleted", undefined);
				deleteData(globals.gPendingUpdates); 
			}
		});
	});
	
	/*
	$('#assignments_delete').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:false,
			position: { my: "left top", at: "left top", of:'#aside'},
			title: "Delete Tasks",
			show:"drop",
			buttons: [
		        {
		        	text: "Cancel",
		        	click: function() {
		        		//globals.gDeleteSelected = false;
		        		refreshAssignmentData(gUserFilter);
		        		$(this).dialog("close");
		        	}
		        },
		        {
		        	text: "Save",
		        	click: function() {
		        		if(confirm("Are you sure you want to delete these tasks")) {		        		
		        			//globals.gDeleteSelected = false;
		        			deleteData(globals.gPendingUpdates);       		
		        			$(this).dialog("close");
		        		}
			        	
		        	}
		        }
			]
		}
	);
	
	$('#deleteAllTasks').button().click(function () {
		
		if(typeof globals.gAssignmentsLayer != "undefined") {
			features = globals.gAssignmentsLayer.features;
			for(i = 0; i < features.length; i++) {
				if(features[i].attributes.assignment_status !== "cancelled"
						&& features[i].attributes.assignment_status !== "submitted"
						&& features[i].attributes.assignment_status !== "submitted") {
					deleteFeature(features[i]);
				}
			}
		}
		globals.gAssignmentsLayer.redraw();

	});
	*/
	
	// Clear cancelled tasks buton
	/*
	$('#clearCancelledTasks').button().click(function () {
		
		if(confirm("Are you sure you want to clear all cancelled tasks.  A user that has " +
				"downloaded these tasks to their phones may not be notified that the task has " +
				"been cancelled..")) {	
			addHourglass();
			$.ajax({
				  type: "DELETE",
				  contentType: "application/json",
				  dataType: "json",
				  url: "/surveyKPI/assignments/cancelled/" + globals.gCurrentProject, 
				  success: function(data, status) {
					  refreshAssignmentData(gUserFilter);
					  removeHourglass();
				  }, error: function(data, status) {
					  console.log(data);
					  removeHourglass();
					  alert("Error: Failed to clear cancelled tasks"); 
				  }
			});
		}

	});
	*/
	
	// Create trigger to open dialog to edit task parameters

	$('#show_task_params').button().click(function () {
		gTaskGroupIndex = -1;
		$('#task_params').dialog("open");
	});

	
	$('#task_params').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			title: "Edit Address Parameters",
			width: 500,
			show:"drop",
			buttons: [
		        {
		        	text: "Cancel",
		        	click: function() {
		        		$(this).dialog("close");
		        	}
		        },
		        {
		        	text: "Save Changes",
		        	click: function() {
		        		updateTaskParams();
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	
	enableUserProfile();										// Enable user profile button	
	$('.rmm').delegate('#refreshMenu', 'click', function(e) {	// Add refresh action
		refreshAssignmentData(gUserFilter);
	}); 	
	$('#tasks_print').button();									// Add button styling
	
	// Hack on screen refresh default radio button is not always being set (not sure why)
	$('#tableradio').prop('checked',true);
});

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
	refreshAssignmentData(gUserFilter);		// Get the assignments from the server
	saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
	getUsers(globals.gCurrentProject);										// Get the users that have access to this project
	$('#project_select').val(globals.gCurrentProject);	// Set the source project equal to the current project
	
}

function surveyChanged() {
	var updateResults = $('#update_results').is(':checked'),
		sId = $('#survey').val();
	
	$('#filter_option').empty();
	if(updateResults) {
		$('#survey_to_complete').val(sId);
	} 
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
		question = globals.gSelector.getQuestionDetails(sId, qId, language);
	
	$filter_option.empty();

	if(question) {
		gFilterqType = question.type;
		if(question.type === "select1" || question.type === "select") {
			$filter_option_only.show();
			$filter_integer_only.hide();
			$filter_text_only.hide();
			
			addHourglass();
			// Get the meta data for the question
			$.ajax({
				url: questionMetaURL(sId, language, qId),
				dataType: 'json',
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
		} else {	// Default to text (string)
			$filter_option_only.hide();
			$filter_integer_only.hide();
			$filter_text_only.show();
		}
	}
	
}

/*
 * Add the columns that the user can select to create address information
 */
function setAddressOptions() {
	
	var sId = $('#survey').val();
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
					h = [],
					idx = -1,
					i,j;
					removeHourglass();
					
					gTaskParams = [];
					j = 0;
					for(i = 0; i < table.columns.length; i++) {
						colname = table.columns[i].name;
						
						if(colname !== "prikey" && colname !== "parkey" && 
								colname !== "the_geom" &&
								colname.indexOf("_") !== 0) {
							gTaskParams[j++] = {selected:false, name:colname, isBarcode:false}; 
							
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
			$('#status_msg_msg').empty().text("Error failed to get data for survey:" + sId);
			$("#status_msg").dialog("open");
			//refreshSurveyData(panelView, undefined, dataView);
			removeHourglass();
		}
	});
 	
 	
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
	
	$.ajax({
		url: "/surveyKPI/userList",
		dataType: 'json',
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

function saveData(data) {
	
	var assignString = JSON.stringify(data);

	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  dataType: "json",
		  url: "/surveyKPI/assignments",
		  data: { settings: assignString },
		  success: function(data, status) {
			  refreshAssignmentData(gUserFilter);
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
		  contentType: "application/json",
		  dataType: "json",
		  url: "/surveyKPI/assignments",
		  data: { settings: deleteString },
		  success: function(data, status) {
			  removeHourglass();
			  refreshAssignmentData(gUserFilter);
		  }, error: function(data, status) {
			  console.log(data);
			  removeHourglass();
			  alert("Error: Failed to delete tasks"); 
		  }
	});
}

/*
 * Get the assignments from the server
 */
function refreshAssignmentData(user_filter) {

	if(typeof globals.gCurrentProject !== "undefined" && globals.gCurrentProject != -1) {
		addHourglass();
		$.ajax({
			url: "/surveyKPI/assignments/" + globals.gCurrentProject + "?user=" + user_filter,
			cache: false,
			dataType: 'json',
			success: function(data) {
				refreshMapAssignments(data);
				refreshTableAssignments(data);
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

/*
function clearDialogs() {
	globals.gCurrentUserId = undefined;
	globals.gPendingUpdates = [];
	//globals.gDeleteSelected = false;	
	$(".ui-dialog-content").modal("hide");
}
*/

function refreshTableAssignments(tasks) {
	
	if(typeof tasks != "undefined") {
		$('#tasks_table').empty().generateTaskTable(
				{
					'data': tasks,
					'maxAddCols' : 10,
					'showCompleted' : $('#filter_completed').prop('checked')
				});
		
		// Respond to selection of a task
		$(".tasks").find(".select_row").change(function() {
			var $this = $(this);
			
			console.log("Row selected:" + $this.data("taskid") + " : " + $this.data("assid") );
			if($this.is(':checked')) {
				$this.closest('tr').addClass("info");
				addPendingTask($this.data("taskid"), $this.data("assid"), $this.data("status"), "table");
			} else {
				$this.closest('tr').removeClass("info");
				removePendingTask($this.data("assid"), "table");
			}
		});
		
		// Show barcodes
		$(".tasks").find('.barcode').each(function(index) {
			var $bcElement = $(this);
			$bcElement.barcode($bcElement.text(), "code11");
		});
		
		// Create trigger to open dialog to edit task parameters
		
		gTasks = tasks;
		
		// Add function to add tasks to group
		$('.add_new_task').button().click(function () {
			//var taskSource = $('input[name=task_source]:checked', '#assign_survey_form').val(),
			var s_id = $('#survey').val();
			
			/*
			 * Make sure we have the survey id
			 */
			if(typeof s_id === "undefined" || s_id === null) {
				alert("Either waiting for the server or there are no surveys in this project to assign to a user. " +
						"If the project does have surveys then try again in a few seconds");
				return;
			}
			
			//if(taskSource === "new") {
				globals.gCurrentUserName = $('#users_select_new_task option:selected').text();
				globals.gCurrentUserId = $('#users_select_new_task option:selected').val();
				registerForNewTasks();
			//}
			
			// open the dialog
			$('#addNewTask').modal("show");
		});
		
		// Add function to delete the group
		$('.delete_task_group').button().click(function () {

			/*
			var tg_id = $(this).val();
			
			$('#tasktable' + tg_id).find('.control_td > input').each(function(){
				var $this = $(this);
				
				if(!$this.is(':checked')) {
					addPendingTask($this.data("taskid"), $this.data("assid"), $this.data("status"), "table");
				} 
				$this.prop("checked", true).closest('tr').addClass("info");

			});
			*/
			
			var tg_id = $(this).val();
			
			bootbox.confirm('Are you sure you want to delete this group and all of its tasks?', function(result){
				if(result) {
					addHourglass();
					$.ajax({
						  type: "DELETE",
						  contentType: "application/json",
						  dataType: "json",
						  url: "/surveyKPI/assignments/" + tg_id,
						  success: function(data, status) {
							  removeHourglass();
							  refreshAssignmentData(gUserFilter);
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
 * Show the task parameters in the dialog
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
 * Get the updated parameters from the dialog and save back into the global parameters object
 */
function updateTaskParams() {
	
	var name,
		selected,
		isBarcode;
	
	gTaskParams = [];
	$('#task_params_table').find('tbody tr').each(function(index){
		name = $(this).find('td.task_name').text();
		selected = $(this).find('td.task_selected input').is(':checked');
		isBarcode = $(this).find('td.task_isBarcode input').is(':checked');
		gTaskParams[index] = {selected: selected, name: name, isBarcode: isBarcode};
	});
	
	if(gTaskGroupIndex !== -1) {	// An existing set of task parameters was being edited
		// Update the array of task params
		gTasks.task_groups[gTaskGroupIndex].tg_address_params = JSON.stringify(gTaskParams);
		// Update the task params in the database TODO
		
		refreshAssignmentData(gUserFilter);
		//refreshTableAssignments(gTasks);	// Refresh the table view
	}
}

});