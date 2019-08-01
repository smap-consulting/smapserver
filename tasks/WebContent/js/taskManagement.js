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
 * Purpose: Manage the panels that display graphs, maps etc of results data
 */

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
}

requirejs.config({
    baseUrl: 'js/libs',
    locale: gUserLocale,
    waitSeconds: 0,
    paths: {
     	app: '../app',
     	i18n: '../../../../js/libs/i18n',
     	localise: '../../../../js/app/localise',
    	modernizr: '../../../../js/libs/modernizr',
    	moment: '../../../../js/libs/moment-with-locales.2.24.0',
    	datetimepicker: '../../../../js/libs/bootstrap-datetimepicker-4.17.47',
    	common: '../../../../js/app/common',
    	globals: '../../../../js/app/globals',
	    popper: '../../../../js/libs/popper.v1.12.9.min',
    	bootbox: '../../../../js/libs/bootbox.min',
    	crf: '../../../../js/libs/commonReportFunctions',
    	lang_location: '../../../../js',
    	mapbox_app: '../../../../js/app/mapbox_app',
    	
    	mapbox: '../../../../js/libs/mapbox/js/mapbox',
    	metismenu: '../../../../js/libs/wb/metisMenu/jquery.metisMenu',
    	pace: '../../../../js/libs/wb/pace/pace.min',
    	peity: '../../../../js/libs/wb/peity/jquery.peity.min',
    	icheck: '../../../../js/libs/wb/plugins/iCheck/icheck.min',
    	calendar: '../../../../js/libs/wb/plugins/fullcalendar/fullcalendar.min',
        es: '../../../../js/libs/wb/plugins/fullcalendar/es',
        multiselect: '../../../../js/libs/bootstrap-multiselect',
        knockout: '../../../../js/libs/knockout'
    },
    shim: {

    	'common': ['jquery'],
    	'datetimepicker': ['moment'],
    	'jquery-barcode': ['jquery'],
    	'app/plugins': ['jquery'],
    	'crf': ['jquery'],
    	'mapbox_app' : ['jquery', 'mapbox'],
    	'mapbox': {
            exports: 'L'
        },
    	'metismenu': ['jquery'],
    	'peity': ['jquery'],
    	'icheck': ['jquery'],
        'multiselect': ['jquery', 'knockout'],
        'es': ['calendar']
	
    	}
    });

require([
         'jquery',
         'popper',
         'common', 
         'localise', 
         'globals',
         'bootbox',
         'app/task_plugins',
         'jquery-barcode',
         'crf',
         'moment',
         'mapbox',
         'datetimepicker',
         'mapbox_app',
         'metismenu',
         'pace',
         'peity',
         'icheck',
         'calendar',
	     'multiselect'
         
         ], function($,
        		 Popper,
        		 common, 
        		 localise, 
        		 globals, 
        		 bootbox,
        		 task_plugins,
        		 barcode, 
        		 crf, 
        		 moment,
        		 mapbox) {


	// The following globals are only in this java script file
	var gTaskGroupIndex = -1,	// Currently selected task group
		gUpdateTaskGroupIndex,  // A task group being edited
		gTaskGroups,            // Current list of task groups
		gTaskParams = [],		// Parameters for a new task
		gFilterqType,			// The type of the filter question select, select1, int, string
		gCalendarInitialised = false,	// Set true when the calendar pane has been initialised
		gMapInitialised = false,		// Set true when the map pane has been initialised
		gModalMapInitialised = false,	// Set true then the modal map has been initialised
		gIdx = 0,						// Idx set when external task dropped on calendar
		gSelectedCount = 0,
		gUnsentEmailCount = 0,
		MIN_DOWNLOAD_RANGE = 100,
		MIN_SHOW_RANGE = 10;

	var gCurrentGroup,
		gCurrentLocation = '-1',
		gTags;

	$(document).ready(function () {

		setCustomAssignments();			// Apply custom javascript

		window.moment = moment;		// Make moment global for use by common.js

		window.gCurrentTaskFeature; // Currently edited task feature, hack to support shared functions with console
		window.gSaveType = '';

		globals.gRegion = {};	// Initialise global values
		globals.gRegions = undefined;

		setupUserProfile(true);
		localise.setlang();		// Localise HTML
		$('#email_subject').prop('placeholder', localise.set['n_esc']);
		$('#tp_assign_emails, #assign_emails').prop('placeholder', localise.set['n_cs_e']);

		$("#side-menu").metisMenu();

		getLoggedInUser(projectChanged, false, true, undefined);

		// Set change function on projects
		$('#project_name').change(function () {
			projectChanged();
		});

		// Get locations
		getLocations(processLocationList);

		/*
		 * Change function on source project when tasks are being copied from an existing survey
		 * The source project is the project that data is being copied from,
		 *  it can be different from the current project
		 */
		//
		$('#project_select,#project_name').change(function () {
			var sourceProject = $('#project_select option:selected').val();
			loadSurveys(sourceProject, "#survey", false, false, surveyChanged);			// Get surveys
		});

		// Add a trigger to respond to the clicking of "filter tasks"
		$('#filter_results').change(function () {
			if($('#filter_results').prop('checked')) {
				$('#filter_results_advanced').prop('checked', false);
				$('.simple_filter').show();
				$('.advanced_filter').hide();
			} else {
				$('.simple_filter').hide();
			}
		});

		// Add a trigger to respond to the clicking of "advanced filter tasks"
		$('#filter_results_advanced').change(function () {
			if($('#filter_results_advanced').prop('checked')) {
				$('#filter_results').prop('checked', false);
				$('.simple_filter').hide();
				$('.advanced_filter').show();
			} else {
				$('.advanced_filter').hide();
			}
		});


		// Add a trigger to respond to the clicking of "add_from_survey"
		$('#add_from_survey').prop('checked', false).click(function () {
			$('#add_task_from_existing').toggle();
		});

		// Add response to the filters being changed
		$('.task_filter').change(function () {
			refreshAssignmentData();
		});

		// Add response to a source survey being selected
		$('#survey').change(function () {
			surveyChanged("-1");
		});

		// Add response to a source survey being selected
		$('#filter_language').change(function () {
			languageChanged();
		});

		// Add response to a filter question being selected
		$('#filter_question').change(function () {
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
			var url = '/surveyKPI/tasks/pdf/' + globals.gCurrentTaskGroup,
				name = $('#taskgroup option:selected').text();

			if (globals.gCurrentTaskGroup) {
				downloadFile(url);
				//downloadFile(url, name + ".pdf", "application/pdf");
			} else {
				alert(localise.set["msg_tg_ns"]);
			}
		});

		$('#m_export_xls').click(function () {	// Export to XLS
			var tz = Intl.DateTimeFormat().resolvedOptions().timeZone,
				tzParam = "",
				url = '/surveyKPI/tasks/xls/' + globals.gCurrentTaskGroup,
				hasParam = false,
				statusFilterArray = $('#status_filter').val();

			if (globals.gCurrentTaskGroup) {

				// Add parameters
				if (tz) {
					url += (hasParam ? '&' : '?') + "tz=" + encodeURIComponent(tz);
					hasParam = true;
				}
				if(statusFilterArray) {
					url += (hasParam ? '&' : '?') + 'inc_status=' + statusFilterArray.join(',');
				}

				downloadFile(url);

			} else {
				alert(localise.set["msg_tg_ns"]);
			}
		});

		$('#m_import_xls').click(function () {	// Import from XLS
			if (globals.gCurrentTaskGroup) {
				$('#import_taskgroup').modal("show");
			} else {
				alert(localise.set["msg_tg_ns"]);
			}
		});
		$(('#importTaskGroupGo')).click(function () {
			importTaskGroup();
		});

		// Respond to selection of a file for upload
		$('.custom-file-label').attr('data-browse', localise.set["c_browse"]);
		$('.custom-file-input').on('change',function(){
			var fileName = $(this).val();
			var endPath = fileName.lastIndexOf("\\");
			if(endPath > 0) {
				fileName = fileName.substring(endPath + 1);
			}
			$(this).next('.custom-file-label').html(fileName);
		})

		$('#users_task_group, #roles_task_group').change(function() {
			if($(this).val() == -2) {
				$('.assign_data').show();
				$('#assign_data').prop('placeholder', "");
			} else {
				$('.assign_data').hide();
			}
		});

		getRoles();
		$('.assign_user').show();
		$('.assign_role, .assign_email, .assign_data').hide();
		setupTaskDialog();
		$('#taskPropertiesSave').off().click(function () {
			saveTask(false, gCurrentTaskFeature, gSaveType, undefined, doneTaskSave);
		});


		// Add a trigger to open the modal that bulk assigns a user to tasks
		$('#assignUser').click(function () {

			globals.gCurrentUserName = $('#users_select_user option:selected').text();
			globals.gCurrentUserId = $('#users_select_user option:selected').val();

			$('#assign_user').modal("show");
		});

		// Add a trigger to set a task as assigned
		$('#setStatus').click(function () {
			var bulkAction = {
					action: "status",
					tasks: getSelectedTaskIds()
				},
				baString = JSON.stringify(bulkAction),
				url;

			url = "/surveyKPI/tasks/bulk/";
			url += globals.gCurrentProject + "/" + globals.gCurrentTaskGroup;

			addHourglass();
			$.ajax({
				type: "POST",
				dataType: 'text',
				cache: false,
				contentType: "application/json",
				url: url,
				data: {tasks: baString},
				success: function (data, status) {
					removeHourglass();
					refreshAssignmentData();
				}, error: function (data, status) {
					removeHourglass();
					alert(localise.set["c_error"] +": " + data.responseText);
				}
			});


		});

		/*
		 * Save the assigned user
		 */
		$('#assignUserSave').off().click(function () {

			var bulkAction = {
					action: "assign",
					userId: $('#users_select_user').val(),
					tasks: getSelectedTaskIds()
				},
				baString = JSON.stringify(bulkAction),
				url;

			url = "/surveyKPI/tasks/bulk/";
			url += globals.gCurrentProject + "/" + globals.gCurrentTaskGroup;

			addHourglass();
			$.ajax({
				type: "POST",
				dataType: 'text',
				cache: false,
				contentType: "application/json",
				url: url,
				data: {tasks: baString},
				success: function (data, status) {
					removeHourglass();
					refreshAssignmentData();
				}, error: function (data, status) {
					removeHourglass();
					alert(localise.set["c_error"] +": " + data.responseText);
				}
			});

		});


		/*
		 * Set up filters
		 */
		$('#status_filter').multiselect({
			onChange: function(option, checked, select) {
				refreshTableAssignments();
				refreshMainMap();
				updateCalendar();
			}
		});
		$('#status_filter').multiselect('selectAll', false)
			.multiselect('deselect', 'deleted')
			.multiselect('deselect', 'cancelled')
			.multiselect('updateButtonText');


		$('#editTaskGroup').click(function () {
			var s_id = $('#survey').val();

			/*
			 * Make sure we have a current task group to edit
			 * The option should be greyed out if this is not the case
			 */
			if (!gTaskGroups || gTaskGroups.length == 0) {
				alert("No task group to edit");
				return;
			}

			// Clear form
			$('#assign_survey_form')[0].reset();
			gUpdateTaskGroupIndex = gTaskGroupIndex;

			var tg = gTaskGroups[gTaskGroupIndex];
			var tgRule = gTaskGroups[gTaskGroupIndex].rule;
			if(typeof tgRule === "undefined") {
				alert(localise.set["msg_tg_rd"]);
				return;
			}


			$('#task_group_name').val(tgRule.task_group_name);
			$('#t_dl_dist').val(tgRule.dl_dist);
			$('#t_show_dist').val(tgRule.show_dist);

			// If added from a survey
			var filterQuestion = "-1";
			if(tg.source_s_id) {
				$('#add_from_survey').prop('checked', true);
				$('#add_task_from_existing').show();

				$('#survey_to_complete').val(tg.target_s_id);
				$('#users_task_group').val(tgRule.user_id);
				$('#roles_task_group').val(tgRule.role_id);
				$('#assign_data').val(tgRule.assign_data);
				$('#fixed_role').val(tgRule.fixed_role_id);
				$('#assign_emails').val(tgRule.emails);
				$('#survey').val(tg.source_s_id);
				if(tgRule.update_results) {
					$('#id_update_results').prop('checked', true);
				} else if(tgRule.prepopulate) {
					$('#id_prepopulate').prop('checked', true);
				} else  {
					$('#id_blank').prop('checked', true);
				}
				$('#add_current').prop('checked', tgRule.add_current);
				$('#add_future').prop('checked', tgRule.add_future);

				setupAssignType(tgRule.user_id, tgRule.role_id, tgRule.emails);    // Set up assign type

				if(tgRule.user_id == -2 || tgRule.role_id == -2 ||
					(tgRule.user_id == 0 && tgRule.role_id == 0)) {
					$('.assign_data').show();
				} else {
					$('.assign_data').hide();
				}
				// Add Question Filter
				$('.simple_filter').hide();
				$('.advanced_filter').hide();
				if(typeof tgRule.filter !== "undefined") {
					if (typeof tgRule.filter.qId !== "undefined" && tgRule.filter.qId.length > 0) {

						$('#filter_language').val(tgRule.lang_val);
						$('#project_select').val(tgRule.filter.existing_proj);
						filterQuestion = tgRule.filter.qId;
						if(tgRule.filter.qType === "string") {
							$('#filter_text').val(tgRule.filter.qText);
						} else if(tgRule.filter.qType === "int") {
							$('#filter_integer').val(tgRule.filter.qInteger);
						}

						if(tgRule.filter.qStartDate) {
							$('#startDate').datetimepicker({
								useCurrent: false,
								locale: gUserLocale || 'en'
							}).data("DateTimePicker").date(moment.utc(tgRule.filter.qStartDate));
						}

						if(tgRule.filter.qEndDate) {
							$('#endDate').datetimepicker({
								useCurrent: false,
								locale: gUserLocale || 'en'
							}).data("DateTimePicker").date(moment.utc(tgRule.filter.qEndDate));
						}

						$('#filter_results').prop('checked', true);
						$('.simple_filter').show();
					} else if (typeof tgRule.filter.advanced !== "undefined" && tgRule.filter.advanced.trim().length > 0) {
						$('#tg_ad_filter').val(tgRule.filter.advanced);

						$('#filter_results_advanced').prop('checked', true);
						$('.advanced_filter').show(tgRule.filter.advanced);
					}
				}

				if(tgRule.taskStart) {
					$('#task_start').val(tgRule.taskStart);		// Get start of task
					$('#task_after').val(tgRule.taskAfter);
					$('#task_units').val(tgRule.taskUnits);
					$('#task_duration').val(tgRule.taskDuration);
					$('#duration_units').val(tgRule.durationUnits);
				}
			} else {
				$('#add_task_from_existing').hide();
			}

			// Set email details
			var emaildetails = tg.emaildetails;
			if(emaildetails) {
				$('#email_subject').val(emaildetails.subject);
				$('#email_content').val(emaildetails.content);
			}


			surveyChanged(filterQuestion, tgRule.address_columns);    // Set survey related parameters

			// open the modal for update
			$('#add_current').prop('disabled', true);
			$('#addTaskLabel').text(localise.set["t_edit_group"]);
			$('#tg_id').html(tg.tg_id);


			$('.tg_edit_only').show();
			$('#addTask').modal("show");

		});

		// Add new Task Group
		$('#addTaskGroup').click(function () {
			var s_id = $('#survey').val();

			/*
			 * Make sure we have the survey id
			 */
			if (typeof s_id === "undefined" || s_id === null) {
				alert(localise.set["msg_err_wait"]);
				return;
			}

			// Clear form
			$('#assign_survey_form')[0].reset();
			gUpdateTaskGroupIndex = -1;

			$('#assign_user_type').prop('checked', true);
			$('#assign_role_type, #assign_email_type').prop('checked', false);
			$('#assign_user_type').closest('label').addClass('active');
			$('#assign_role_type').closest('label').removeClass('active');
			$('.assign_user').show();
			$('.assign_role').hide();

			surveyChanged("-1");
			$('#add_task_from_existing').hide();
			$('.simple_filter').hide();
			$('.advanced_filter').hide();

			// open the modal
			$('#addTask').find('input,select, #addNewGroupSave').prop('disabled', false);
			$('#addTaskLabel').text(localise.set["t_add_group"]);
			$('.tg_edit_only').hide();
			$('#addTask').modal("show");

		});


		/*
		 * Create a new group or save the edited task group,
		 *  optionally populated with tasks generated from existing survey results
		 *  or from an XLS file
		 */
		$('#addNewGroupSave').click(function () {
			var assignObj = {},
				assignString,
				url,
				filterObj = {},
				source_survey,
				taskGroup,
				tgId = -1;

			if(gUpdateTaskGroupIndex > -1) {
				tgId = gTaskGroups[gTaskGroupIndex].tg_id;
			}

			// validation
			if (!validDates()) {
				return;
			}

			taskGroup = $('#task_group_name').val();
			if (!taskGroup || taskGroup.trim() === "") {
				alert(localise.set["msg_val_nm"]);
				$('#task_group_name').focus();
				return;
			}

			var dl_dist = $('#t_dl_dist').val();
			if(typeof dl_dist === "undefined") {
				dl_dist = 0;
			} else {
				dl_dist = +dl_dist;
			}
			if (dl_dist && dl_dist < MIN_DOWNLOAD_RANGE) {
				alert(localise.set["msg_val_dl_dist"]);
				$('#t_dl_dist').focus();
				return;
			}

			var show_dist = $('#t_show_dist').val();
			if(typeof show_dist === "undefined") {
				show_dist = 0;
			} else {
				show_dist = +show_dist;
			}
			if (show_dist && show_dist < MIN_SHOW_RANGE) {
				alert(localise.set["msg_val_show_dist"]);
				$('#t_show_dist').focus();
				return;
			}

			updateTaskParams();

			assignObj["task_group_name"] = taskGroup;	// The Name of the task group
			assignObj["dl_dist"] = dl_dist;	            // Download distance
			assignObj["show_dist"] = show_dist;	        // Show distance
			assignObj["project_name"] = $('#project_select option:selected').text();	// The name of the project that this survey is in

			if ($('#add_from_survey').is(':checked')) {

				assignObj["survey_name"] = $('#survey_to_complete option:selected').text();	// The display name of the survey to complete
				assignObj["target_survey_id"] = $('#survey_to_complete option:selected').val(); 		// The form id is the survey id of the survey used to complete the task!

				var assignType = $("button.assign_type.active", "#addTask").attr("id");
				if(assignType == 'assign_user_type') {
					assignObj["user_id"] = $('#users_task_group option:selected').val(); 		// User assigned to complete the task
					assignObj["role_id"] = 0;
					assignObj["fixed_role_id"] = 0;
				} else if(assignType == 'assign_role_type') {
					assignObj["user_id"] = 0;
					assignObj["role_id"] = $('#roles_task_group option:selected').val();
					assignObj["fixed_role_id"] = $('#fixed_role option:selected').val();

					// validate - The fixed role id should only be set if the role id is also set
					if (assignObj["fixed_role_id"] > 0 &&  assignObj["role_id"] == 0) {
						alert(localise.set["msg_val_ad2"]);
						return;
					}
				} else if(assignType == 'assign_email_type') {
					assignObj["user_id"] = 0;
					assignObj["role_id"] = 0;
					assignObj["fixed_role_id"] = 0;
					assignObj["emails"] = $('#assign_emails').val();

					// Text email must be valid email addresses
					var emails = assignObj["emails"];
					if(emails && emails.trim().length > 0) {
						var emailArray = emails.split(",");
						for (var i = 0; i < emailArray.length; i++) {
							if (!validateEmails(emailArray[i])) {
								alert(localise.set["msg_inv_email"]);
								break;
							}
						}
					}
				}
				if(assignObj["user_id"] == -2 || assignObj["role_id"] == -2 || assignType == 'assign_email_type') {
					assignObj["assign_data"] = $('#assign_data').val();

					// validate
					if (typeof assignObj["assign_data"] === "undefined" && assignObj["assign_data"].trim().length == 0) {
						alert(localise.set["msg_val_ad"]);
						return;
					}
				}

				source_survey = $('#survey').val(); 						// The survey that provides the existing results
				if (!source_survey) {
					source_survey = -1;
				}
				assignObj["source_survey_id"] = source_survey;
				assignObj["address_columns"] = removeUnselected(gTaskParams);
				assignObj["source_survey_name"] = $('#survey option:selected').text();		// The display name of the survey that will provide the source locations and initial data

				var initial_data = $("input[name='initial_data']:checked"). val();
				assignObj["prepopulate"] = false;
				assignObj["update_results"] = false;
				assignObj["blank"] = false;
				if(initial_data === "prepopulate") {
					assignObj["prepopulate"] = true;
				}  if(initial_data === "update_results") {
					assignObj["update_results"] = true;
				} else {
					assignObj["blank"] = true;
				}

				//assignObj["update_results"] = $('#update_results').is(':checked'); 			// Set to true if the survey is to be updated

				// Add filter if filter checkbox has been checked
				if ($('#filter_results').is(':checked')) {

					filterObj["qType"] = gFilterqType;
					filterObj["lang_val"] = $('#filter_language option:selected').val();
					filterObj["existing_proj"] = $('#project_select option:selected').val();
					filterObj["qId"] = $('#filter_question option:selected').val();
					filterObj["oValue"] = $('#filter_option option:selected').val();
					filterObj["qText"] = $('#filter_text').val();
					filterObj["qStartDate"] = getUtcDate($('#startDate'), true, false);		// Get start of day
					filterObj["qEndDate"] = getUtcDate($('#endDate'), false, true);			// Get end of day
					if (gFilterqType === "int") {
						filterObj["qInteger"] = $('#filter_integer').val();
					}
					filterObj["lang"] = $('#filter_language option:selected').val();
					assignObj["filter"] = filterObj;

				} else if ($('#filter_results_advanced').is(':checked')) {
					filterObj["advanced"] = $('#tg_ad_filter').val();
					assignObj["filter"] = filterObj;
				}

				// Set flag to indicate if tasks should be created from data submitted before the task group creation, after or both
				assignObj["add_current"] = $('#add_current').is(':checked');
				assignObj["add_future"] = $('#add_future').is(':checked');

				if (!assignObj["add_current"] && !assignObj["add_future"]) {
					alert(localise.set["msg_ab_ns"]);
					return;
				}

				// Start and durations
				assignObj["taskStart"] = $('#task_start').val();		// Get start of task
				assignObj["taskAfter"] = $('#task_after').val();
				assignObj["taskUnits"] = $('#task_units').val();
				assignObj["taskDuration"] = $('#task_duration').val();
				assignObj["durationUnits"] = $('#duration_units').val();

			}

			// Add email details
			assignObj["emailDetails"] = {
				subject: $('#email_subject').val(),
				content: $('#email_content').val()
			}

			assignString = JSON.stringify(assignObj);
			globals.gCurrentUserId = undefined;
			globals.gCurrentUserName = undefined;

			if(gUpdateTaskGroupIndex < 0) {
				// Add new task group
				addHourglass();
				$.ajax({
					type: "POST",
					url: "/surveyKPI/assignments/addSurvey/" + globals.gCurrentProject,
					cache: false,
					data: {settings: assignString},
					dataType: 'json',
					success: function (data, status) {
						removeHourglass();
						$('#addTask').modal("hide");
						globals.gCurrentTaskGroup = data.tg_id;
						refreshTaskGroupData();
					}, error: function (data, status) {
						removeHourglass();
						if (data.responseText.indexOf("<html>") !== 0) {
							alert(localise.set["c_error"] + " : " + data.responseText);
						} else {
							alert(localise.set["msg_err_upd"]);
						}

					}
				});
			} else {
				// update task group
				addHourglass();
				$.ajax({
					type: "POST",
					url: "/surveyKPI/assignments/updatetaskgroup/" + globals.gCurrentProject + "/" + tgId,
					cache: false,
					data: {settings: assignString},
					dataType: 'json',
					success: function (data, status) {
						removeHourglass();
						$('#addTask').modal("hide");
						globals.gCurrentTaskGroup = data.tg_id;
						refreshTaskGroupData();
					}, error: function (data, status) {
						removeHourglass();
						if (data.responseText.indexOf("<html>") !== 0) {
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
		 */
		$('#deleteTaskGroup').click(function () {

			var tg_id = globals.gCurrentTaskGroup;

			bootbox.confirm(localise.set["msg_confirm_del"] + ' ' + localise.set["msg_confirm_tasks"] + ' (' + $('#taskgroup option:selected').text() + ')', function (result) {
				if (result) {
					addHourglass();
					$.ajax({
						type: "DELETE",
						url: "/surveyKPI/assignments/" + tg_id,
						success: function (data, status) {
							removeHourglass();
							refreshTaskGroupData();
						}, error: function (data, status) {
							removeHourglass();
							alert(localise.set["c_error"] +": " + data.responseText);
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


		// Delete Tasks button
		$('#deleteTasks').click(function () {

			var bulkAction = {
				action: "delete",
				tasks: getSelectedTaskIds()
			};

			bootbox.confirm(localise.set["msg_confirm_del"] + ' ' + bulkAction.tasks.length + ' ' + localise.set["m_assign"] +
				'?', function (result) {
				if (result) {
					var baString = JSON.stringify(bulkAction),
						url = "/surveyKPI/tasks/bulk/";

					url += globals.gCurrentProject + "/" + globals.gCurrentTaskGroup;
					addHourglass();
					$.ajax({
						type: "POST",
						dataType: 'text',
						cache: false,
						contentType: "application/json",
						url: url,
						data: {tasks: baString},
						success: function (data, status) {
							removeHourglass();
							refreshAssignmentData();
						}, error: function (data, status) {
							console.log(data);
							removeHourglass();
							alert(localise.set["c_error"] +": " + data.responseText);
						}
					});
				}
			});
		});


		$('#taskParamsSave').click(function () {
			updateTaskParams();
		});

		$('#m_refresh').click(function (e) {	// Add refresh action
			refreshAssignmentData();
		});

		$('#m_email_unsent').click(function (e) {	// Add email unsent action
			emailUnsent();
		});

		$('#tasks_print').button();									// Add button styling

		// Set up the start and end dates with date picker
		$('#startDate').datetimepicker({					// Selecting start end times for tasks generated from survey
			useCurrent: false,
			locale: gUserLocale || 'en'
		}).data("DateTimePicker").date(moment());

		$('#endDate').datetimepicker({
			useCurrent: false,
			locale: gUserLocale || 'en'
		}).data("DateTimePicker").date(moment());

		/*
		 * Set focus to first element on opening modals
		 */
		$('.modal').on('shown.bs.modal', function () {
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
			if (target === '#cal-view') {
				if (!gCalendarInitialised) {
					gCalendarInitialised = true;
					setTimeout(function () {
						initialiseCalendar();
						$('#calendar').fullCalendar('option', 'locale', gUserLocale);
					}, 500);
				} else {
					$('#calendar').fullCalendar('render');
				}
			} else if (target === '#map-view') {
				if (!gMapInitialised) {
					gMapInitialised = true;
					initialiseMap('map', 1, true, undefined, refreshMainMap);
				}
			}
		});

		/*
		 * Reports
		 */
		$('#m_all_tasks_report').click(function(){
			var tz = Intl.DateTimeFormat().resolvedOptions().timeZone,
				tzParam = "",
				url = '/surveyKPI/tasks/xls/0',
				hasParam = false,
				statusFilterArray = $('#status_filter').val(),
				period_filter = $('#period').val();

			// Add parameters
			if (tz) {
				url += (hasParam ? '&' : '?') + "tz=" + encodeURIComponent(tz);
				hasParam = true;
			}
			if(statusFilterArray) {
				url += (hasParam ? '&' : '?') + 'inc_status=' + statusFilterArray.join(',');
				hasParam = true;
			}
			if(period_filter) {
				url += (hasParam ? '&' : '?') + 'period=' + period_filter;
				hasParam = true;
			}

			downloadFile(url);

		});
	});

	/*
	 * Get an array of taskIds that have been selected
	 */
	function getSelectedTaskIds() {

		var tasks = [],
			idx;

		$('input[type=checkbox]:checked', '#task_table').each(function () {
			idx = $(this).val();
			tasks.push({
				taskId: globals.gTaskList.features[idx].properties.id,
				assignmentId: globals.gTaskList.features[idx].properties.a_id
			});

		});

		return tasks;
	}

	/*
	 * Get an array of taskIds that are displayed and an email has not been sent
	 */
	function getUnsentTaskIds() {

		var tasks = [],
			idx;
		for(idx = 0; idx < globals.gTaskList.features.length; idx++) {
			if (globals.gTaskList.features[idx].properties.status === "unsent" || globals.gTaskList.features[idx].properties.status === "blocked") {
				tasks.push({
					taskId: globals.gTaskList.features[idx].properties.id,
					assignmentId: globals.gTaskList.features[idx].properties.a_id
				});
			}
		}

		return tasks;
	}

	/*
	 * Refresh the main map
	 */
	function refreshMainMap() {
		refreshMapAssignments('map', globals.gTaskList);
	}

	/*
	 * Remove unselected address parameters
	 */
	function removeUnselected(taskParams) {
		var i,
			minimalParams = [];
		if (taskParams) {
			for (i = taskParams.length - 1; i >= 0; i--) {
				if (taskParams[i].selected) {
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

		for (i = 0; i < globals.gPendingUpdates.length; i++) {
			if (user) {
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
		globals.gCurrentTaskGroup = undefined;

		loadSurveys(globals.gCurrentProject, undefined, false, false, surveyChanged);			// Get surveys
		refreshTaskGroupData();		// Get the task groups from the server

		saveCurrentProject(globals.gCurrentProject,
			globals.gCurrentSurvey,
			globals.gCurrentTaskGroup);

		getTaskUsers(globals.gCurrentProject);										// Get the users that have access to this project
		$('#project_select').val(globals.gCurrentProject);	// Set the source project equal to the current project

	}

	function surveyChanged(filterQuestion, address_columns) {
		var sId = $('#survey').val();

		if(typeof filterQuestion === "undefined") {
			filterQuestion = "-1";
		}
		$('#filter_option').empty();
		getLanguageList(sId, questionChanged, false, '#filter_language', false, filterQuestion);
		setAddressOptions(address_columns);
	}

	function languageChanged() {

		var language = $('#filter_language option:selected').val(),
			sId = $('#survey').val(),
			dateqId = $('#task_start').val();
		qList;

		qList = globals.gSelector.getSurveyQuestions(sId, language);

		if (!qList) {
			getQuestionList(sId, language, "-1", "-1", questionChanged, false, undefined, dateqId, undefined);
		} else {
			setSurveyViewQuestions(qList, qId, undefined, dateqId);
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

		if (question) {
			gFilterqType = question.type;
			if (question.type === "select1" || question.type === "select") {
				$filter_option_only.show();
				$filter_integer_only.hide();
				$filter_text_only.hide();
				$filter_date_only.hide();

				addHourglass();
				// Get the meta data for the question
				$.ajax({
					url: questionMetaURL(sId, language, qId),
					cache: false,
					success: function (data) {
						var i,
							option,
							h = [],
							idx = -1;

						removeHourglass();
						console.log(data);
						if (data && data.options) {
							for (i = 0; i < data.options.length; i++) {
								option = data.options[i];
								h[++idx] = '<option value="';
								h[++idx] = option.value;
								h[++idx] = '">';
								h[++idx] = option.label;
								h[++idx] = '</option>';
							}
							$filter_option.append(h.join(''));

							var tgRule = JSON.parse(gTaskGroups[gTaskGroupIndex].rule);
							if(tgRule && tgRule.filter) {
								$filter_option.val(tgRule.filter.oValue);
							}
						}
					},
					error: function (xhr, textStatus, err) {
						removeHourglass();
						if (xhr.readyState == 0 || xhr.status == 0) {
							return;  // Not an error
						} else {
							alert(localise.set["c_error"] +": " + err);
						}
					}
				});
			} else if (question.type === "int") {
				$filter_option_only.hide();
				$filter_integer_only.show();
				$filter_text_only.hide();
				$filter_date_only.hide();

			} else if (question.type === "date" || question.type == "dateTime") {
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
	function setAddressOptions(address_columns) {

		var sId = $('#survey').val(),
			j,
			i;

		if (sId) {
			// Get the survey meta data
			addHourglass();
			$.ajax({
				url: "/surveyKPI/survey/" + sId + "/getMeta",
				dataType: 'json',
				cache: false,
				success: function (data) {
					removeHourglass();

					// Get the data for the top level table
					addHourglass();
					$.ajax({
						url: "/surveyKPI/table/" + data.top_table,
						dataType: 'json',
						cache: false,
						success: function (table) {
							var colname,
								coltype,
								sMedia,
								h = [],
								idx = -1,
								i, j,k,
								selected,
								isBarcode;
							removeHourglass();

							gTaskParams = [];
							j = 0;
							for (i = 0; i < table.columns.length; i++) {
								colname = table.columns[i].name;
								coltype = table.columns[i].type;

								if (colname !== "prikey" && colname !== "parkey" &&
									colname !== "the_geom" &&
									colname !== "geo_type" &&
									colname.indexOf("_") !== 0) {

									if (coltype && (coltype === "image" || coltype === "audio" || coltype === "video")) {
										isMedia = true;
									} else {
										isMedia = false;
									}

									selected = false;
									isBarcode = false;
									if(address_columns) {
										for(k = 0; k < address_columns.length; k++) {
											if(address_columns[k].name === colname) {
												selected = address_columns[k].selected;
												isBarcode = address_columns[k].isBarcode;
												break;
											}
										}
									}
									gTaskParams[j++] = {
										selected: selected,
										name: colname,
										isBarcode: isBarcode,
										isMedia: isMedia
									};

								}
							}

							displayTaskParams();

						},
						error: function (xhr, textStatus, err) {
							removeHourglass();
							if (xhr.readyState == 0 || xhr.status == 0) {
								return;  // Not an error
							} else {
								alert(localise.set["c_error"] + ": " + err);
							}
						}
					});

				},
				error: function (data) {
					removeHourglass();
					bootbox.alert("Error failed to get data for survey:" + sId);
				}
			});
		}


	}

	/*
	 * Get the list of roles from the server
	 */
	function getRoles() {
		var $roles = $('.roles_select'),
			$fixed = $('#fixed_role'),
			i,
			role,
			h = [],
			idx = -1;

		$roles.empty();

		$roles.append('<option value="-1">' + localise.set["t_u"] + '</options>');
		$roles.append('<option value="-2">' + localise.set["t_ad"] + '</options>');
		$fixed.append('<option value="-1">' + localise.set["none"] + '</options>');
		$.ajax({
			url: "/surveyKPI/role/roles/names",
			cache: false,
			success: function (data) {

				for (i = 0; i < data.length; i++) {
					role = data[i];

					h[++idx] = '<option value="';
					h[++idx] = role.id;
					h[++idx] = '">';
					h[++idx] = role.name;
					h[++idx] = '</option>';

				}
				$roles.append(h.join(''));
				$fixed.append(h.join(''));
			},
			error: function (xhr, textStatus, err) {
				if (xhr.readyState == 0 || xhr.status == 0) {
					return;  // Not an error
				} else {
					alert(localise.set["c_error"] + err);
				}
			}
		});
	}

	/*
	 * Get the task groups from the server
	 */
	function refreshTaskGroupData() {

		if (typeof globals.gCurrentProject !== "undefined" && globals.gCurrentProject != -1) {
			addHourglass();
			$.ajax({
				url: "/surveyKPI/tasks/taskgroups/" + globals.gCurrentProject,
				cache: false,
				dataType: 'json',
				success: function (data) {
					removeHourglass();
					refreshTaskGroupList(data);
				},
				error: function (xhr, textStatus, err) {
					removeHourglass();
					if (xhr.readyState == 0 || xhr.status == 0) {
						return;  // Not an error
					} else {
						alert("Failed to get task group data");
					}
				}
			});
		} else {
			$('.for_selected, .for_is_tg').addClass('disabled');		// Disable task buttons
		}
	}

	/*
	 * Update the table view of task groups
	 */
	function refreshTaskGroupList(taskgroups) {

		var h = [],
			idx = -1,
			i,
			grp,
			firstTg,
			hasCurrentTg = false;

		gTaskGroups = taskgroups;   // Keep the task group list
		gTaskGroupIndex = 0;

		if (!taskgroups || taskgroups.length == 0) {
			$('#tasks_row').hide();
		} else {
			$('#tasks_row').show();
		}

		$('.for_selected, .for_is_tg').addClass('disabled');		// Disable task buttons

		if (typeof taskgroups != "undefined" && taskgroups.length > 0) {

			$('.for_is_tg').removeClass('disabled');				// Enable adding of task to the task group

			for (i = 0; i < taskgroups.length; i++) {
				grp = taskgroups[i];
				h[++idx] = '<option value="';
				h[++idx] = i;
				h[++idx] = '">';
				h[++idx] = grp.name;
				h[++idx] = '</option>';

				if (i == 0) {
					firstTg = grp.tg_id
				}
				if (grp.tg_id == globals.gCurrentTaskGroup) {
					hasCurrentTg = true;
					gTaskGroupIndex = i;
				}
			}
		}
		$('#taskgroup').html(h.join(''));

		// Set current value for the task group
		if (!hasCurrentTg) {
			globals.gCurrentTaskGroup = firstTg;
		}
		$('#taskgroup').val(gTaskGroupIndex);


		$('#taskgroup').change(function () {
			gTaskGroupIndex = $(this).val();
			globals.gCurrentTaskGroup = gTaskGroups[gTaskGroupIndex].tg_id;
			saveCurrentProject(undefined, undefined, globals.gCurrentTaskGroup);
			refreshAssignmentData();
		})
		refreshAssignmentData();

	}

	/*
	 * Email unsent tasks
	 */
	function emailUnsent() {

		var bulkAction = {
			action: "email_unsent",
			tasks: getUnsentTaskIds()
		};
		var baString = JSON.stringify(bulkAction);
		var url = "/surveyKPI/tasks/bulk/" + globals.gCurrentProject
			+ "/" + globals.gCurrentTaskGroup;

		$('.for_unsent_email').addClass('disabled');    // Disable send button

		addHourglass();
		$.ajax({
			type: "POST",
			dataType: 'text',
			cache: false,
			contentType: "application/json",
			url: url,
			data: {tasks: baString},
			success: function (data, status) {
				removeHourglass();
				refreshAssignmentData();
			}, error: function (data, status) {
				removeHourglass();
				refreshAssignmentData();
				alert(localise.set["c_error"] +": " + data.responseText);
			}
		});

	}

	/*
	 * Get the assignments from the server
	 */
	function refreshAssignmentData() {

		var user_filter = $('#users_filter').val(),
			completed = $('#filter_completed').is(':checked'),
			period_filter = $('#period').val();

		if (typeof globals.gCurrentTaskGroup !== "undefined" && globals.gCurrentTaskGroup != -1) {
			addHourglass();
			$.ajax({
				url: "/surveyKPI/tasks/assignments/" +
					globals.gCurrentTaskGroup +
					"?user=" + user_filter + "&period=" + period_filter,
				cache: false,
				dataType: 'json',
				success: function (data) {
					removeHourglass();
					globals.gTaskList = data;
					if (gMapInitialised) {
						refreshMapAssignments('map', globals.gTaskList);
					}
					refreshTableAssignments();
					if (gCalendarInitialised) {
						updateCalendar();
					}
				},
				error: function (xhr, textStatus, err) {
					removeHourglass();
					if (xhr.readyState == 0 || xhr.status == 0) {
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

		gSelectedCount = 0;

		if (typeof tasks != "undefined") {

			$('#task_table_body').empty().html(getTableBody(tasks));

			if(gUnsentEmailCount > 0) {
				$('.for_unsent_email').removeClass('disabled');
			} else {
				$('.for_unsent_email').addClass('disabled');
			}


			// Respond to selection of a task
			$('input', '#task_table_body').change(function (event) {
				var $this = $(this),
					idx = $this.val(),
					selected = $this.is(':checked');

				if (selected) {
					gSelectedCount++;
				} else {
					gSelectedCount--;
				}

				if (gSelectedCount > 0) {
					$('.for_selected').removeClass('disabled');
				} else {
					$('.for_selected').addClass('disabled');
				}

				globals.gTaskList.features[idx].properties.selected = selected;
				if (gMapInitialised) {
					refreshMapAssignments('map', globals.gTaskList);
				}
			});

			// Respond to clicking on task edit button
			$(".task_edit", '#task_table_body').click(function () {
				var $this = $(this),
					idx = $this.val(),
					taskFeature = globals.gTaskList.features[idx],
					task = taskFeature.properties;

				editTask(false, task, taskFeature);
			});

			// Show barcodes
			$(".tasks").find('.barcode').each(function (index) {
				var $bcElement = $(this);
				$bcElement.barcode($bcElement.text(), "code11");
			});


		}
	}

	/*
	 * Edit an existing task or create a new one
	 */
	function editTask(isNew, task, taskFeature) {
		var scheduleDate,
			splitDate = [];

		console.log("open edit task: " + task.from);

		gCurrentTaskFeature = taskFeature;

		$('form[name="taskProperties"]')[0].reset();
		clearDraggableMarker('mapModal');

		if (isNew) {
			$('#taskPropLabel').html(localise.set["t_add_task"]);
		} else {
			$('#taskPropLabel').html(localise.set["t_edit_task"]);
		}

		/*
		 * Set up data
		 */
		$('#tp_repeat').prop('checked', task.repeat);
		$('#tp_name').val(task.name);		// name
		if(isNew) {
			$('#tp_form_name').val($('#tp_form_name option:first').val());
		} else {
			$('#tp_form_name').val(taskFeature.properties.form_id);	// form id
		}
		setupAssignType(taskFeature.properties.assignee, 0, taskFeature.properties.emails);
		$('#tp_user').val(taskFeature.properties.assignee);	// assignee
		$('#tp_assign_emails').val(taskFeature.properties.emails);
		$('#tp_repeat').prop('checked', taskFeature.properties.repeat);

		// Set end date first as otherwise since it will be null, it will be defaulted when from date set
		if (task.to) {
			$('#tp_to').data("DateTimePicker").date(localTime(task.to));
		}
		if (task.from) {
			$('#tp_from').data("DateTimePicker").date(localTime(task.from));
		}

		$('#nfc_uid').val(task.location_trigger);
		gCurrentGroup = task.location_group;
		gCurrentLocation = getLocationIndex(task.location_name, gTags);
		if(gCurrentGroup && gCurrentGroup != '') {
			$('.location_group_list_sel').text(gCurrentGroup);
			setLocationList(gTags, gCurrentLocation, gCurrentGroup);
		}

		if(task.guidance) {
			$('#tp_guidance').val(task.guidance);
		} else {
			$('#tp_guidance').val(task.address);    // Initialise with address data
		}
		if (task.update_id && task.update_id.length > 0) {
			$('#initial_data').html(getInitialDataLink(taskFeature));
		}
		$('#tp_show_dist').val(task.show_dist);

		$('#location_save_panel').hide();
		$('#task_properties').modal("show");

		if (!gModalMapInitialised) {
			setTimeout(function () {
				initialiseMap('mapModal', 14,
					!gCurrentTaskFeature.geometry.coordinates[0] && !gCurrentTaskFeature.geometry.coordinates[1], 		// Show user location if there is no task location
					clickOnMap, modalMapReady);
			}, 500);
			gModalMapInitialised = true;
		} else {
			gClickOnMapenabled = false;
			modalMapReady();
		}

	}

	/*
	 * Called when the modal map is ready to accept features
	 */
	function modalMapReady() {
		if (gCurrentTaskFeature.geometry.coordinates[0] || gCurrentTaskFeature.geometry.coordinates[1]) {
			addDraggableMarker('mapModal',
				new L.LatLng(gCurrentTaskFeature.geometry.coordinates[1], gCurrentTaskFeature.geometry.coordinates[0]),
				onDragEnd);
		}
	}

	/*
	 * Respond to a click on the modal map
	 */
	function clickOnMap(latlng) {
		var x = 1,
			coords = [];

		coords[0] = latlng.lng;
		coords[1] = latlng.lat;

		gCurrentTaskFeature.geometry.coordinates = coords;
		addDraggableMarker('mapModal', latlng, onDragEnd);
		setupSaveLocation();
		$('#location_save_panel').show();

	}

	/*
	 * Respond to a drag of the task location on the modal map
	 */
	function onDragEnd(latlng) {
		var x = 1,
			coords = [];

		coords[0] = latlng.lng;
		coords[1] = latlng.lat;

		gCurrentTaskFeature.geometry.coordinates = coords;
		setupSaveLocation();
		$('#location_save_panel').show();
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
		h[++idx] = '<th>';
		h[++idx] = localise.set["c_select"];
		h[++idx] = '</th>';
		h[++idx] = '<th>';
		h[++idx] = localise.set["c_question"];
		h[++idx] = '</th>';
		// remove setting of barcode checkbox.  This feeature probably needs to be added back into PDF export
		//h[++idx] = '<th>';
		//h[++idx] = localise.set["ed_bc"];
		//h[++idx] = '</th>';
		h[++idx] = '</tr>';
		h[++idx] = '</thead>';
		h[++idx] = '<tbody>';


		if (gTaskParams) {
			for (i = 0; i < gTaskParams.length; i++) {
				h[++idx] = '<tr>';
				h[++idx] = '<td class="task_selected"><input type="checkbox" name="isSelected" value="';
				h[++idx] = gTaskParams[i].name;
				h[++idx] = '" ';
				if (gTaskParams[i].selected !== false) {
					h[++idx] = 'checked="checked"';
				}
				h[++idx] = '></td>';
				h[++idx] = '<td class="task_name">' + gTaskParams[i].name + '</td>';
				//h[++idx] = '<td class="task_isBarcode"><input type="checkbox" name="isBarcode" value="';
				//h[++idx] = gTaskParams[i].name;
				//h[++idx] = '" ';
				//if (gTaskParams[i].isBarcode !== false) {
				//	h[++idx] = 'checked="checked"';
				//}
				//h[++idx] = '></td>';
				h[++idx] = '</tr>';
			}

		}

		h[++idx] = '</tbody>';
		h[++idx] = '</table>';

		$('.task_params_table').empty().append(h.join(''));

	}

	/*
	 * Get the updated parameters from the modal and save back into the global parameters object
	 */
	function updateTaskParams() {

		var name,
			selected,
			isBarcode,
			isMedia,
			updatedTaskParams = [],
			$this;


		$('#task_params_table_main').find('tbody tr').each(function (index) {
			$this = $(this);
			name = $this.find('td.task_name').text();
			selected = $this.find('td.task_selected input').prop('checked');
			isBarcode = $this.find('td.task_isBarcode input').prop('checked');
			isMedia = gTaskParams[index].isMedia;
			updatedTaskParams[index] = {selected: selected, name: name, isBarcode: isBarcode, isMedia: isMedia};
		});
		gTaskParams = updatedTaskParams;

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

		gUnsentEmailCount = 0;

		// Filter on status
		var statusFilterArray = $('#status_filter').val();
		var statusFilter = statusFilterArray ? statusFilterArray.join('') : "";
		var statusLookup;

		for (i = 0; i < tasks.length; i++) {
			var task = tasks[i];

			if(task.properties.status === "unsent" || task.properties.status === "blocked") {
				gUnsentEmailCount++;
			}

			if(statusFilter.indexOf(task.properties.status) >= 0) {
				tab[++idx] = '<tr>';
				tab[++idx] = addSelectCheckBox(false, i, false);

				tab[++idx] = '<td>';
				tab[++idx] = task.properties.id;
				tab[++idx] = '</td>';

				tab[++idx] = '<td>';
				tab[++idx] = task.properties.form_name;
				tab[++idx] = '</td>';

				tab[++idx] = '<td>';			// Task name
				tab[++idx] = task.properties.name;
				tab[++idx] = '</td>';

				tab[++idx] = '<td class="' + getStatusClass(task.properties.status) + '">';	// status
				statusLookup = task.properties.status;
				if(statusLookup === "error" || statusLookup === "pending" || statusLookup === "blocked") {
					statusLookup = "c_" + statusLookup;
				}
				tab[++idx] = localise.set[statusLookup];
				tab[++idx] = '</td>';

				tab[++idx] = '<td>';		// Assignee
				tab[++idx] = task.properties.assignee_name;
				tab[++idx] = '</td>';

				tab[++idx] = '<td>';		// Comment
				tab[++idx] = task.properties.comment;
				tab[++idx] = '</td>';

				tab[++idx] = '<td>';			// NFC
				if (task.properties.location_trigger && task.properties.location_trigger.length > 0) {
					if (task.properties.location_trigger.indexOf('{') == 0) {
						tab[++idx] = '<i class="fa fa-crosshairs"></i>';	// Geo fence
					} else {
						tab[++idx] = '<i class="fa fa-wifi"></i>';			// NFC
					}
				}
				tab[++idx] = '</td>';

				tab[++idx] = '<td>';			// Existing data
				if (task.properties.update_id && task.properties.update_id.length > 0) {
					tab[++idx] = getInitialDataLink(task);
				}
				tab[++idx] = '</td>';

				tab[++idx] = '<td>';			// Blocked
				if (task.properties.blocked) {
					tab[++idx] = '<i class="fa fa-ban has_tt" title="Survey Blocked"></i>';	// Survey Blocked
				}
				tab[++idx] = '</td>';

				tab[++idx] = '<td>';			// Repeat Count
				if (task.properties.repeat) {
					tab[++idx] = task.properties.repeat_count;
				}
				tab[++idx] = '</td>';

				tab[++idx] = '<td>';		// scheduled
				tab[++idx] = localTime(task.properties.from);
				tab[++idx] = '<td>';			// edit
				tab[++idx] = '<button class="btn btn-default task_edit" value="';
				tab[++idx] = i;
				tab[++idx] = '" type="button"><i class="fa fa-edit"></i></button>';
				tab[++idx] = '</td>';


				tab[++idx] = '</tr>';
			}
		}
		return tab.join('');

	}

	function getInitialDataLink(task) {
		var tab = [];
		idx = -1;

		tab[++idx] = '<a href="';
		tab[++idx] = getWebFormUrl(task.properties.form_ident,
			task.properties.update_id,
			task.properties.initial_data_source,
			task.properties.id,
			task.properties.a_id);
		tab[++idx] = '" target="_blank">'
		tab[++idx] = '<i class="fa fa-file-text"></i>';	// Edit existing data
		tab[++idx] = '</a>';

		return tab.join('');
	}

	function getWebFormUrl(form_ident, update_id, initial_data_source, taskId, assignmentId) {
		var url,
			hasParams = false;

		initial_data_souce = initial_data_source || 'none';

		url = "/webForm/" + form_ident;

		if (update_id && initial_data_source === 'survey') {
			url += "?datakey=instanceid&datakeyvalue=" + update_id;
			url += "&viewOnly=true"
			hasParams = true;
		} else {
			url += '?taskkey=';
			url += taskId;
			hasParams = true;
		}
		url += (hasParams ? '&' : '?');
		url += 'assignment_id=';
		url += assignmentId;

		return url;
	}

	/*
	 * Process a list of locations
	 */
	function processLocationList(tags) {
		gTags = tags;
		refreshLocationGroups(tags, true);
		setLocationList(tags, gCurrentLocation, gCurrentGroup);

		// Respond to a location group being selected
		$('.dropdown-item', '#location_group').click(function () {
			gCurrentGroup = $(this).text();
			gCurrentLocation = '-1';
			$('.location_group_list_sel').text(gCurrentGroup);
			setLocationList(gTags, gCurrentLocation, gCurrentGroup);
		});
	}

	/*
	 * Respond to a location being selected
	 */
	$('#location_select').change(function () {
		var idx = $(this).val();

		// Clear old values
		clearDraggableMarker('mapModal');
		$('#nfc_uid').val("");
		$('#location_save_panel').hide();
		gSaveType = '';

		if(idx != -1) {
			$('#nfc_uid').val(gTags[idx].uid);
			var lat = gTags[idx].lat;
			var lon = gTags[idx].lon;
			if (lon || lat) {
				//clearDraggableMarker('mapModal');
				addDraggableMarker('mapModal', new L.LatLng(lat, lon), onDragEnd);
			}
			gCurrentTaskFeature.geometry.coordinates[0] = lon;
			gCurrentTaskFeature.geometry.coordinates[1] = lat;
		}
		zoomToFeatureLayer('mapModal');
	});

	/*
	 * respond to change in the nfc uid
	 */
	$('#nfc_uid').keyup(function(){
		setupSaveLocation();
		$('#location_save_panel').show();
	});

	/*
	 * respond to selection of a save type
	 */
	$("input[name='location_save']", '#location_save_panel').change(function() {
		setupSaveLocation();
	});

	/*
	 * Import a task group from a spreadsheet
	 */
	function importTaskGroup() {
		var url = '/surveyKPI/tasks/xls/' + globals.gCurrentProject;

		$('#tg_to_import').val(globals.gCurrentTaskGroup);
		var f = document.forms.namedItem("loadtasks");
		var formData = new FormData(f);

		$('#load_tasks_alert').hide();

		addHourglass();
		$.ajax({
			type: "POST",
			data: formData,
			cache: false,
			contentType: false,
			processData: false,
			url: url,
			success: function (data, status) {
				removeHourglass();
				$('#import_taskgroup').modal("hide");
				$('#load_tasks_alert').show().removeClass('alert-danger').addClass('alert-success').empty("");
				refreshAssignmentData();
				getLocations(processLocationList);	// Refresh the location data since new locations may have been loaded
			},
			error: function (xhr, textStatus, err) {
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

		var events = getEvents();

		$('#calendar').fullCalendar({
			header: {
				left: 'prev,next today',
				center: 'title',
				right: 'month,agendaWeek,agendaDay'
			},
			contentHeight: 300,
			aspectRatio: 1.35,
			editable: true,
			droppable: true,
			drop: function () {
				$(this).remove();
			},
			dayClick: function (date, jsEvent, view) {

				console.log('Clicked on: ' + date.format());
				console.log('Coordinates: ' + jsEvent.pageX + ',' + jsEvent.pageY);
				console.log('Current view: ' + view.name);

				// change the day's background color
				//$(this).css('background-color', 'red');
				$('#calendar').fullCalendar('gotoDate', date);
				$('#calendar').fullCalendar('changeView', 'agendaDay');

			},
			eventDrop: function (event, delta, revertFunc) {
				var feature = {
					properties: {
						id: globals.gTaskList.features[event.taskIdx].properties.id,
						from: utcTime(event.start.format("YYYY-MM-DD HH:mm:ss")),
						to: utcTime(event.end.format("YYYY-MM-DD HH:mm:ss"))
					}
				};
				updateWhen(feature, revertFunc, event.taskIdx);
			},
			eventResize: function (event, delta, revertFunc) {
				var feature = {
					properties: {
						id: globals.gTaskList.features[event.taskIdx].properties.id,
						from: utcTime(event.start.format("YYYY-MM-DD HH:mm:ss")),
						to: utcTime(event.end.format("YYYY-MM-DD HH:mm:ss"))
					}
				};
				updateWhen(feature, revertFunc, event.taskIdx);
			},
			eventClick: function (event) {
				var taskFeature = globals.gTaskList.features[event.taskIdx],
					task = taskFeature.properties;

				editTask(false, task, taskFeature);
			},
			eventReceive: function (event) {
				event.taskIdx = gIdx;
				var feature = {
					properties: {
						id: globals.gTaskList.features[event.taskIdx].properties.id,
						from: utcTime(event.start.format("YYYY-MM-DD HH:mm")),
						to: utcTime(event.end.format("YYYY-MM-DD HH:mm"))
					}
				};

				globals.gTaskList.features[event.taskIdx].from = utcTime(event.start.format("YYYY-MM-DD HH:mm:ss"));
				globals.gTaskList.features[event.taskIdx].to = utcTime(event.end.format("YYYY-MM-DD HH:mm:ss"));
				updateWhen(feature, undefined, event.taskIdx);
			},
			drop: function () {
				gIdx = $(this).data("idx");
				$(this).remove();
			},
			events: events

		});

		$('#external-events div.external-event').each(function () {

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

	/*
	 * Update a single property
	 * No need to refresh assignments after the update as the change has already been applied to the local model
	 */
	function updateWhen(taskFeature, revertFunc, idx) {

		var url = "/surveyKPI/tasks/when/" + globals.gCurrentProject + "/" + globals.gCurrentTaskGroup,
			tfString = JSON.stringify(taskFeature),
			tasks = globals.gTaskList.features,
			i;

		addHourglass();
		$.ajax({
			type: "POST",
			dataType: 'text',
			cache: false,
			contentType: "application/json",
			url: url,
			data: {task: tfString},
			success: function (data, status) {
				removeHourglass();

				tasks[idx].properties.from = taskFeature.properties.from;
				tasks[idx].properties.to = taskFeature.properties.to;

				refreshTableAssignments();
			},
			error: function (xhr, textStatus, err) {

				removeHourglass();
				revertFunc();
				alert(localise.set["msg_err_upd"] + xhr.responseText);

			}
		});
	}

	function updateCalendar() {

		var events = getEvents();

		$('#calendar').fullCalendar('removeEvents');
		$('#calendar').fullCalendar('addEventSource', events)
	}

	/*
	 * Convert the current task list into events
	 */
	function getEvents() {
		var tasks = tasks = globals.gTaskList.features,
			events = [],
			event = {},
			h = [],
			idx = -1,
			task,
			i;

		// Filter on status
		var statusFilterArray = $('#status_filter').val();
		var statusFilter = statusFilterArray ? statusFilterArray.join('') : "";
		var statusLookup;

		for (i = 0; i < tasks.length; i++) {
			task = tasks[i].properties;
			if(statusFilter.indexOf(task.status) >= 0) {
				if (task.from) {
					event = {
						title: task.name,
						start: localTimeAsDate(task.from),
						allDay: false,
						taskIdx: i
					};
					if (task.to) {
						event.end = localTimeAsDate(task.to)
					}
					events.push(event);
				} else {
					h[++idx] = '<div class="external-event navy-bg" data-idx="';
					h[++idx] = i;
					h[++idx] = '" data-start="09:00" data-duration = "01:00"';
					h[++idx] = '>';
					h[++idx] = task.name;
					h[++idx] = '</div>';
				}
			}
		}

		$('#dragTask').html(h.join(''));

		return events;
	}

	/*
	 * Setup controls in save location panel
	 */
	function setupSaveLocation() {
		var val = $("input[name='location_save']:checked", '#location_save_panel').val();
		var locationIdx = $('#location_select').val();
		var groupSave = $('#locationGroupSave').val();

		if(!val || val === '') {
			$('#location_save_al').prop('checked', true);      // default
		}
		gSaveType = val;

		if (locationIdx >= 0) {
			$('#location_save_ul').prop('disabled', false);
		} else {
			$('#location_save_ul').prop('disabled', true);
		}

		if(val == 'nl') {
			if(!groupSave || groupSave.trim().length === 0) {
				$('#locationGroupSave').val($('.location_group_list_sel').text());
			}
			$('.update_only').show();
		} else {
			$('.update_only').hide();
			$('#locationGroupSave').val('');
		}
	}

	/*
	 * Callback after saving a task
	 */
	function doneTaskSave() {
		refreshAssignmentData();
		getLocations(processLocationList);
	}

});

