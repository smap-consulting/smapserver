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
var gCache = {};


/*
 * Convert a choice list name into a valid jquery class name
 */
function jq(choiceList) {

	var c;

	c = choiceList.replace( /(:|\.|\[|\]|,)/g, "\\$1" );
	return c;
}

/* 
 * ==============================================================
 * Task Functions
 * ==============================================================
 */

function addPendingTask(taskId, assignmentId, status, source) {
	var i,
		duplicate = false,
		assignment;

	assignment = {
		assignment_id: assignmentId,
		assignment_status: status,
		task_id: taskId
	};
	globals.gPendingUpdates.push(assignment);

	if(source === "table") {
		updateMapTaskSelections(taskId, true);
	} else if(source === "map") {
		$('#tasks_table').find('[data-taskid=' + taskId + ']').prop("checked", true).closest('tr').addClass("info");
	}
}

function removePendingTask(taskId, source) {
	var i;
	for (i = 0; i < globals.gPendingUpdates.length; i++) {
		if(globals.gPendingUpdates[i].task_id === taskId) {
			globals.gPendingUpdates.splice(i,1);
			break;
		}
	}
	if(source === "table") {
		updateMapTaskSelections(taskId, false);
	} else if(source === "map") {
		$('#tasks_table').find('[data-taskid=' + taskId + ']').prop("checked", false).closest('tr').removeClass("info");
	}
}

/*
 * ===============================================================
 * Project Functions
 * ===============================================================
 */

/*
 * Update the list of available projects
 * Note when addAll is set to true the list is not used to change the default project
 *   In this case the value of the list should not be set to the default project
 */
function updateProjectList(addAll, projectId, callback) {

	var $projectSelect = $('.project_list'),
		i,
		h = [],
		idx = -1,
		updateCurrentProject = true;

	if(addAll) {
		h[++idx] = '<option value="0">' + localise.set["c_all"] + '</option>';
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
		globals.gCurrentTaskGroup = undefined;
	} else if(updateCurrentProject) {
		globals.gCurrentProject = -1;		// Update the current project id
		globals.gCurrentSurvey = -1;
		globals.gCurrentTaskGroup = undefined;
	}

	saveCurrentProject(globals.gCurrentProject,
		globals.gCurrentSurvey,
		globals.gCurrentTaskGroup);

	if(!addAll) {
		$projectSelect.val(globals.gCurrentProject);			// Set the initial project value
		$('#projectId').val(globals.gCurrentProject);			// Set the project value for the hidden field in template upload
	}

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
 * Save the time of the last alert for the user
 */
function saveLastAlert(lastAlert, seen) {

	var alertStatus = {
		lastalert: lastAlert,
		seen: seen
	}

	$.ajax({
		type: "POST",
		contentType: "application/json",
		url: "/surveyKPI/user/alertstatus",
		cache: false,
		data: {
			alertstatus: JSON.stringify(alertStatus)
		}
	});
}

/*
 * Save the current project id in the user defaults
 */
function saveCurrentProject(projectId, surveyId, taskGroupId) {

	if(surveyId > 0 || projectId > 0 || taskGroupId > 0) {

		var user = {
			current_project_id: projectId,
			current_survey_id: surveyId,
			current_task_group_id: taskGroupId
		};

		var userString = JSON.stringify(user);

		addHourglass();
		$.ajax({
			type: "POST",
			contentType: "application/json",
			url: "/surveyKPI/user/currentproject",
			cache: false,
			data: { user: userString },
			success: function(data, status) {
				removeHourglass();
			}, error: function(data, status) {
				removeHourglass();
			}
		});
	}
}

/*
 * Save the current relationship between survey and surveyGroup
 */
function saveCurrentGroupSurvey(surveyId, groupSurvey) {

	if (surveyId > 0) {

		var groupSurvey = {
			sId: surveyId,
			groupIdent: groupSurvey
		};

		var groupString = JSON.stringify(groupSurvey);

		addHourglass();
		$.ajax({
			type: "POST",
			contentType: "application/json",
			url: "/surveyKPI/user/groupsurvey",
			cache: false,
			data: {groupSurvey: groupString},
			success: function (data, status) {
				removeHourglass();
			}, error: function (data, status) {
				removeHourglass();
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
 * Add user details popup to the page
 */
function addUserDetailsPopup() {
	var bootstrap_enabled = (typeof $().modal == 'function'),
		h =[],
		idx = -1;

	if(bootstrap_enabled) {

		h[++idx] = '<div id="modify_me_popup" class="modal fade" tabindex="-1" role="dialog" aria-labelledby="modifyMeLabel" aria-hidden="true">';
			h[++idx] = '<div class="modal-dialog">';
				h[++idx] = '<div class="modal-content">';
					h[++idx] = '<div class="modal-header">';
						h[++idx] = '<button type="button" class="close" data-dismiss="modal"><span aria-hidden="true">&times;</span><span class="sr-only">Close</span></button>';
						h[++idx] = '<h4 class="modal-title" id="modifyMeLabel"></h4>';
					h[++idx] = '</div>';    // modal-headers

					h[++idx] = '<div class="modal-body">';
						h[++idx] = '<form role="form" class="form-horizontal" id="me_edit_form">';
							h[++idx] = '<div class="form-group">';
								h[++idx] = '<label for="me_name" class="col-sm-2 control-label">';
									h[++idx] = localise.set["c_name"];
								h[++idx] = '</label>';
								h[++idx] = '<div class="col-sm-10">';
									h[++idx] = '<input type="text" id="me_name" required class="form-control">';
								h[++idx] = '</div>';
							h[++idx] = '</div>';

							h[++idx] = '<div class="form-group">';
								h[++idx] = '<label for="me_language" class="col-sm-2 control-label">';
									h[++idx] = localise.set["c_lang"];
								h[++idx] = '</label>';
								h[++idx] = '<div class="col-sm-10">';
									h[++idx] = '<select id="me_language" class="language_select form-control"></select>';
								h[++idx] = '</div>';
							h[++idx] = '</div>';

							h[++idx] = '<div class="form-group">';
								h[++idx] = '<label for="me_email" class="col-sm-2 control-label">';
									h[++idx] = localise.set["c_email"];
								h[++idx] = '</label>';
								h[++idx] = '<div class="col-sm-10">';
									h[++idx] = '<input type="email" class="form-control" id="me_email"';
									h[++idx] = ' placeholder="Enter email"';
									h[++idx] = ' pattern="^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$">';
								h[++idx] = '</div>';
							h[++idx] = '</div>';

							h[++idx] = '<div class="form-group">';
								h[++idx] = '<label for="me_organisation" class="col-sm-2 control-label">';
									h[++idx] = localise.set["c_org"];
								h[++idx] = '</label>';
								h[++idx] = '<div class="col-sm-10">';
									h[++idx] = '<select id="me_organisation" class="organisation_select form-control"></select>';
								h[++idx] = '</div>';
							h[++idx] = '</div>';

							h[++idx] = '<div class="form-group">';
								h[++idx] = '<label for="me_enterprise" class="col-sm-2 control-label">';
									h[++idx] = localise.set["c_ent"];
								h[++idx] = '</label>';
								h[++idx] = '<div class="col-sm-10">';
									h[++idx] = '<div id="me_enterprise" class="form-control"></div>';
								h[++idx] = '</div>';
							h[++idx] = '</div>';

							h[++idx] = '<div class="form-group">';
								h[++idx] = '<label for="u_tz" class="col-sm-2 control-label">';
									h[++idx] = localise.set["c_tz"];
								h[++idx] = '</label>';
								h[++idx] = '<div class="col-sm-10">';
									h[++idx] = '<select class="form-control timezone_select" id="u_tz"></select>';
								h[++idx] = '</div>';
							h[++idx] = '</div>';

							h[++idx] = '<div id="reset_me_password_fields" style="display:none;">';
								h[++idx] = '<div class="form-group">';
									h[++idx] = '<div class="col-sm-offset-2 col-sm-10">';
										h[++idx] = '<div class="checkbox">';
											h[++idx] = '<label>';
												h[++idx] = '<input type="checkbox" id="reset_me_password">';
												h[++idx] = localise.set["c_r_p"];
											h[++idx] = '</label>';
										h[++idx] = '</div>';
									h[++idx] = '</div>';
								h[++idx] = '</div>';
							h[++idx] = '</div>';

							h[++idx] = '<div id="password_me_fields" style="display:none;">';
								h[++idx] = '<div class="form-group">';
									h[++idx] = '<label for="me_password" class="col-sm-2 control-label">';
										h[++idx] = localise.set["c_password"];
									h[++idx] = '</label>';
									h[++idx] = '<div class="col-sm-10">';
										h[++idx] = '<input id="me_password" type="password" placeholder="Password" class="form-control">';
									h[++idx] = '</div>';
								h[++idx] = '</div>';

								h[++idx] = '<div class="form-group">';
									h[++idx] = '<label for="me_password_confirm" class="col-sm-2 control-label">';
										h[++idx] = localise.set["c_c_p"];
									h[++idx] = '</label>';
									h[++idx] = '<div class="col-sm-10">';
										h[++idx] = '<input id="me_password_confirm" type="password" placeholder="Password" class="form-control">';
									h[++idx] = '</div>';
								h[++idx] = '</div>';
							h[++idx] = '</div>';
						h[++idx] = '</form>';
					h[++idx] = '</div>';    // modal body

					h[++idx] = '<div class="modal-footer">';
						h[++idx] = '<button type="button" class="btn btn-default" data-dismiss="modal">';
							h[++idx] = localise.set["c_close"];
						h[++idx] = '</button>';

						h[++idx] = '<button id="userProfileLogout"type="button" class="btn btn-default" data-dismiss="modal">';
							h[++idx] = localise.set["c_logout"];
						h[++idx] = '</button>';

						h[++idx] = '<button id="userProfileSave" type="button" class="btn btn-primary" data-dismiss="modal">';
							h[++idx] = localise.set["c_save"];
						h[++idx] = '</button>';
					h[++idx] = '</div>';    // modal - footer
				h[++idx] = '</div>';        // modal - content
			h[++idx] = '</div>';            // modal - dialog
		h[++idx] = '</div>';                // popup

	} else {
		h[++idx] = '<div id="modify_me_popup" style="display:none;">';
			h[++idx] = '<div class="left_panel">';
				h[++idx] = '<form id="me_edit_form">';
					h[++idx] = '<label for="me_name">';
						h[++idx] = localise.set["c_name"];
					h[++idx] = '</label>';
					h[++idx] = '<input type="text" id="me_name" required><br/>';

					h[++idx] = '<label for="me_language">';
						h[++idx] = localise.set["c_lang"];
					h[++idx] = '</label>';
					h[++idx] = '<select class="language_select" id="me_language"></select><br/>';

					h[++idx] = '<label for="me_email">';
						h[++idx] = localise.set["c_email"];
					h[++idx] = '</label>';
					h[++idx] = '<input type="text" id="me_email" pattern="^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$"><br/>';

					h[++idx] = '<label for="me_organisation">';
						h[++idx] = localise.set["c_org"];
					h[++idx] = '</label>';
					h[++idx] = '<select class="organisation_select" id="me_organisation"></select><br/>';

					h[++idx] = '<label for="me_enterprise">';
						h[++idx] = localise.set["c_ent"];
					h[++idx] = '</label>';
					h[++idx] = '<div id="me_enterprise"></div><br/>';

					h[++idx] = '<label for="u_tz">';
						h[++idx] = localise.set["c_tz"];
					h[++idx] = '</label>';
					h[++idx] = '<select class="timezone_select" id="u_tz"></select>';

					h[++idx] = '<span id="reset_me_password_fields" style="display:none;">';
						h[++idx] = '<label for="reset_me_password">';
							h[++idx] = localise.set["c_r_p"];
						h[++idx] = '</label>';
						h[++idx] = '<input type="checkbox" id="reset_me_password"/><br/>';
					h[++idx] = '</span>';

					h[++idx] =	'<span id="password_me_fields" style="display:none;">';
						h[++idx] = '<label for="me_password" data-lang="c_password" class="lang">';
							h[++idx] = localise.set["c_password"];
						h[++idx] = '</label>';
						h[++idx] = '<input id="me_password" type="password"><br/>';

						h[++idx] = '<label for="me_password_confirm" data-lang="c_c_p" class="lang">';
							h[++idx] = localise.set["c_password"];
						h[++idx] = '</label>';
						h[++idx] = '<input id="me_password_confirm" type="password"><br/>';
					h[++idx] = '</span>';
				h[++idx] = '</form>';
			h[++idx] = '</div>';
		h[++idx] = '</div>';
	}
	$(document.body).append(h.join(''));
	if(bootstrap_enabled) {
		enableUserProfileBS();
	}
}

/*
 * Add user details popup to the page
 */
function addUserDetailsPopupBootstrap4() {
	var	h =[],
		idx = -1;

	h[++idx] = '<div id="modify_me_popup" class="modal fade" tabindex="-1" role="dialog" aria-labelledby="modifyMeLabel" aria-hidden="true">';
	h[++idx] = '<div class="modal-dialog modal-lg">';
	h[++idx] = '<div class="modal-content">';
	h[++idx] = '<div class="modal-header">';
	h[++idx] = '<h4 class="modal-title" id="modifyMeLabel"></h4>';
	h[++idx] = '<button type="button" class="close" data-dismiss="modal" aria-label="Close"><span aria-hidden="true">&times;</span></button>';
	h[++idx] = '</div>';    // modal-headers

	h[++idx] = '<div class="modal-body">';
	h[++idx] = '<form role="form" id="me_edit_form">';
	h[++idx] = '<div class="form-group row">';
	h[++idx] = '<label for="me_name" class="col-sm-2 control-label">';
	h[++idx] = localise.set["c_name"];
	h[++idx] = '</label>';
	h[++idx] = '<div class="col-sm-10">';
	h[++idx] = '<input type="text" id="me_name" required class="form-control">';
	h[++idx] = '</div>';
	h[++idx] = '</div>';

	h[++idx] = '<div class="form-group row">';
	h[++idx] = '<label for="me_language" class="col-sm-2 control-label">';
	h[++idx] = localise.set["c_lang"];
	h[++idx] = '</label>';
	h[++idx] = '<div class="col-sm-10">';
	h[++idx] = '<select id="me_language" class="language_select form-control"></select>';
	h[++idx] = '</div>';
	h[++idx] = '</div>';

	h[++idx] = '<div class="form-group row">';
	h[++idx] = '<label for="me_email" class="col-sm-2 control-label">';
	h[++idx] = localise.set["c_email"];
	h[++idx] = '</label>';
	h[++idx] = '<div class="col-sm-10">';
	h[++idx] = '<input type="email" class="form-control" id="me_email"';
	h[++idx] = ' placeholder="Enter email"';
	h[++idx] = ' pattern="^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$">';
	h[++idx] = '</div>';
	h[++idx] = '</div>';

	h[++idx] = '<div class="form-group row">';
	h[++idx] = '<label for="me_organisation" class="col-sm-2 control-label">';
	h[++idx] = localise.set["c_org"];
	h[++idx] = '</label>';
	h[++idx] = '<div class="col-sm-10">';
	h[++idx] = '<select id="me_organisation" class="organisation_select form-control"></select>';
	h[++idx] = '</div>';
	h[++idx] = '</div>';

	h[++idx] = '<div class="form-group row">';
	h[++idx] = '<label for="me_enterprise" class="col-sm-2 control-label">';
	h[++idx] = localise.set["c_ent"];
	h[++idx] = '</label>';
	h[++idx] = '<div class="col-sm-10">';
	h[++idx] = '<div id="me_enterprise" class="form-control"></div>';
	h[++idx] = '</div>';
	h[++idx] = '</div>';

	h[++idx] = '<div class="form-group row">';
	h[++idx] = '<label for="u_tz" class="col-sm-2 control-label">';
	h[++idx] = localise.set["c_tz"];
	h[++idx] = '</label>';
	h[++idx] = '<div class="col-sm-10">';
	h[++idx] = '<select class="form-control timezone_select" id="u_tz"></select>';
	h[++idx] = '</div>';
	h[++idx] = '</div>';

	h[++idx] = '<div id="reset_me_password_fields" style="display:none;">';
	h[++idx] = '<div class="form-group row">';
	h[++idx] = '<label class="col-form-label col-sm-2">';
	h[++idx] = localise.set["c_r_p"];
	h[++idx] = '</label>';
	h[++idx] = '<div class="col-sm-10">';
	h[++idx] = '<div class="checkbox">';
	h[++idx] = '<input type="checkbox" id="reset_me_password">';
	h[++idx] = '</div>';        // checkbox
	h[++idx] = '</div>';        // col-sm-10
	h[++idx] = '</div>';        // form-group
	h[++idx] = '</div>';        // reset password fields

	h[++idx] = '<div id="password_me_fields" style="display:none;">';
	h[++idx] = '<div class="form-group row">';
	h[++idx] = '<label for="me_password" class="col-sm-2 control-label">';
	h[++idx] = localise.set["c_password"];
	h[++idx] = '</label>';
	h[++idx] = '<div class="col-sm-10">';
	h[++idx] = '<input id="me_password" type="password" placeholder="Password" class="form-control">';
	h[++idx] = '</div>';
	h[++idx] = '</div>';

	h[++idx] = '<div class="form-group row">';
	h[++idx] = '<label for="me_password_confirm" class="col-sm-2 control-label">';
	h[++idx] = localise.set["c_c_p"];
	h[++idx] = '</label>';
	h[++idx] = '<div class="col-sm-10">';
	h[++idx] = '<input id="me_password_confirm" type="password" placeholder="Password" class="form-control">';
	h[++idx] = '</div>';
	h[++idx] = '</div>';
	h[++idx] = '</div>';
	h[++idx] = '</form>';
	h[++idx] = '</div>';    // modal body

	h[++idx] = '<div class="modal-footer">';
	h[++idx] = '<button type="button" class="btn btn-default" data-dismiss="modal">';
	h[++idx] = localise.set["c_close"];
	h[++idx] = '</button>';

	h[++idx] = '<button id="userProfileLogout"type="button" class="btn btn-default" data-dismiss="modal">';
	h[++idx] = localise.set["c_logout"];
	h[++idx] = '</button>';

	h[++idx] = '<button id="userProfileSave" type="button" class="btn btn-primary" data-dismiss="modal">';
	h[++idx] = localise.set["c_save"];
	h[++idx] = '</button>';
	h[++idx] = '</div>';    // modal - footer
	h[++idx] = '</div>';        // modal - content
	h[++idx] = '</div>';            // modal - dialog
	h[++idx] = '</div>';                // popup

	$(document.body).append(h.join(''));

	enableUserProfileBS();
}

/*
 * Update the user details on the page
 */
function updateUserDetails(data, getOrganisationsFn, getEnterprisesFn, getServerDetailsFn) {

	var groups = data.groups,
		i,
		bootstrap_enabled = (typeof $().modal == 'function');

	if(data.language && data.language !== gUserLocale) {
		try {
			localStorage.setItem('user_locale', data.language);  // Write to storage may be disabled
			location.reload();
		} catch (e) {

		}

	} else if(data.o_id != globals.gOrgId) {
		location.reload();
	}

	globals.gLoggedInUser = data;
	globals.gOrgId = data.o_id;

	if(bootstrap_enabled) {

		$('#modify_me_popup').on('show.bs.modal', function (event) {
			var $this = $(this)
			$this.find('.modal-title').text(data.ident + "@" + data.organisation_name)

			$('#me_edit_form')[0].reset();
			$('#reset_me_password_fields').show();
			$('#password_me_fields').hide();
			addLanguageOptions($('.language_select'), data.language);
			addOrganisationOptions($('.organisation_select'), data.o_id, data.orgs);
			$('#me_name').val(data.name);
			$('#me_email').val(data.email);
			$('#me_enterprise').text(globals.gEnterpriseName);
			$('#u_tz').val(globals.gTimezone);

			$(".navbar-collapse").removeClass("in").addClass("collapse");	// Remove drop down menu
		});


	} else {
		$('#username').html(data.name).button({ label: data.name,
			icons: { primary: "ui-icon-person" }}).off().click(function(){
			$('#me_edit_form')[0].reset();

			$('#reset_me_password_fields').show();
			$('#password_me_fields').hide();
			addLanguageOptions($('.language_select'), data.language);
			addOrganisationOptions($('.organisation_select'), data.o_id, data.orgs);
			$('#me_name').val(data.name);
			$('#me_email').val(data.email);
			$('#me_enterprise').text(globals.gEnterpriseName);
			$('#u_tz').val(globals.gTimezone);

			$('#modify_me_popup').dialog("option", "title", data.name + "@" + data.organisation_name);
			$('#modify_me_popup').dialog("open");
		});
	}

	/*
	 * Show restricted functions
	 */
	if(groups) {
		for(i = 0; i < groups.length; i++) {
			if(groups[i].id === globals.GROUP_ADMIN) {
				globals.gIsAdministrator = true;

                if(data.billing_enabled) {
                    globals.gOrgBillingData = true;
                }

			} else if(groups[i].id === globals.GROUP_ORG_ADMIN) {
				globals.gIsOrgAdministrator = true;
				globals.gBillingData = true;

			} else if(groups[i].id === globals.GROUP_SECURITY) {
				globals.gIsSecurityAdministrator = true;

			} else if(groups[i].id === globals.GROUP_ENTERPRISE) {
                globals.gIsEnterpriseAdministrator = true;
				globals.gBillingData = true;

            } else if(groups[i].id === globals.GROUP_ANALYST) {
				globals.gIsAnalyst = true;

			} else if(groups[i].id === globals.GROUP_MANAGE) {
				globals.gIsManage = true;

			} else if(groups[i].id === globals.GROUP_ENUM) {
				globals.gIsEnum = true;

			} else if(groups[i].id === globals.GROUP_VIEW_DATA) {
                globals.gViewData = true;

            } else if(groups[i].id === globals.GROUP_OWNER) {
                globals.gIsServerOwner = true;
            }
		}
	}

	// Only show items relevant to a user
	$('.restrict_role').hide();
	if(globals.gIsEnum) {
		$('.enum_role').show();
	}
	if(globals.gIsAnalyst) {
		$('.analyst_role').show();
	}
	if(globals.gViewData) {
		$('.data_role').show();
	}
	if(globals.gIsAdministrator) {
		$('.admin_role').show();
	}
	if(globals.gIsManage) {
		$('.manage_role').show();
	}
	if(globals.gIsSecurityAdministrator) {
		$('.security_role').show();
	}
	if(globals.gIsOrgAdministrator) {
		$('.org_role').show();
		if(typeof getOrganisationsFn === "function") {
			getOrganisationsFn();
		}
	}
	if(globals.gIsEnterpriseAdministrator) {
		$('.enterprise_role').show();
		if(typeof getEnterprisesFn === "function") {
			getEnterprisesFn();
		}
	}
	if(globals.gIsServerOwner) {
		$('.owner_role').show();
		if(typeof getServerDetailsFn === "function") {
			getServerDetailsFn();
		}
	}

	//TODO set logic for enabling disabling billing
	if(isBusinessServer() && (globals.gBillingData || globals.gOrgBillingData)) {
		$('.billing_role').show();
	}

	// Other conditional elements
	if(globals.gSendTrail === 'off') {
		$('.user_trail').hide();
	}

	// 	Customer configurable details - the configurable part is TODO
	$('#my_name').val(data.name);			// Add the name to the configurable list

	if(data.settings) {
		var userDetails = JSON.parse(data.settings);
		$('#my_title').val(userDetails.title);
		$('#my_license').val(userDetails.license);
		$('#my_signature').attr("src", "/surveyKPI/file/" + data.signature + "/users?type=sig");
	}

	// Hide any menus that have been disabled by custom java scripts
	$('.perm_dis_menu').hide();
}

function addLanguageOptions($elem, current) {

	var h = [],
		idx = -1,
		i,
		languages = [
			{
				locale: "ar",
				name: "Arabic"
			},
			{
				locale: "en",
				name: "English"
			},
			{
				locale: "fr",
				name: "French"
			},
			{
				locale: "hi",
				name: "Hindi"
			},
			{
				locale: "pt",
				name: "Portugese"
			},
			{
				locale: "es",
				name: "Spanish"
			}
		];

	for(i = 0; i < languages.length; i++) {
		h[++idx] = '<option value="';
		h[++idx] = languages[i].locale;
		h[++idx] = '">';
		h[++idx] = localise.set[languages[i].locale];
		h[++idx] = '</option>';
	}
	$elem.html(h.join(''));
	if(current) {
		$elem.val(current);
	} else {
		$elem.val("en");
	}
}

function addOrganisationOptions($elem, current, orgs) {

	var h = [],
		idx = -1,
		i;

	for(i = 0; i < orgs.length; i++) {
		h[++idx] = '<option value="';
		h[++idx] = orgs[i].id;
		h[++idx] = '">';
		h[++idx] = orgs[i].name;
		h[++idx] = '</option>';
	}
	$elem.html(h.join(''));
	if(current) {
		$elem.val(current);
	}
}

/*
 * Enable the user profile button
 */
function enableUserProfile () {
	// Initialise the dialog for the user to edit their own account details
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
						user.current_task_group_id = 0;

						user.timezone = $('#u_tz').val();
						globals.gTimezone = user.timezone;

						user.o_id = $('#me_organisation').val();
						if(user.o_id == globals.gOrgId) {
							user.o_id = 0;	// No change
						}

						saveCurrentUser(user);			// Save the updated user details to disk
						$(this).dialog("close");
					},
				}, {
					text: "Logout",
					click: function() {
						logout();
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
 * Logout function
 */
function logout() {
	localStorage.setItem('navbar_color', undefined);
	localStorage.setItem('main_logo', undefined);
	jQuery.ajax({
		type: "GET",
		cache: false,
		url: "/surveyKPI/logout",
		beforeSend: function(xhr){xhr.setRequestHeader("Authorization","Basic YXNkc2E6");},
		username: "shkdhasfkhd",
		password: "sieinkdnfkdf",
		error: function(data, status) {
			window.location.href="/logout.html";
		},
		success: function(data,status) {
			window.location.href="/logout.html";
		}
	});
}

/*
 * Enable the user profile button
 */
function enableUserProfileBS () {


	/*
	 * User logout
	 */
	$('#userProfileLogout').click(function() {
		logout();
	});

	/*
	 * Save the user profile
	 */
	$('#userProfileSave').click(function() {
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

		user.o_id = $('#me_organisation').val();
		if(user.o_id == globals.gOrgId) {
			user.o_id = 0;	// No change
		}

		globals.gTimezone = $('#u_tz').val();
		user.timezone = globals.gTimezone;

		user.current_project_id = 0;	// Tell service to ignore project id and update other details
		user.current_survey_id = 0;
		user.current_task_group_id = 0;

		saveCurrentUser(user);			// Save the updated user details to disk
	});


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
		cache: false,
		contentType: "application/json",
		dataType: 'json',
		url: "/surveyKPI/user",
		data: { user: userString },
		success: function(data) {
			removeHourglass();
			updateUserDetails(data, undefined);
		}, error: function(data, status) {
			removeHourglass();
			alert(localise.set["c_error"]);
		}
	});
}

function getAvailableTimeZones(callback) {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/utility/timezones",
		contentType: "application/json",
		cache: true,
		success: function(data) {
			removeHourglass();

			if(typeof callback == "function") {
				callback(data);
			}

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				alert(localise.set["c_error"] + ": " + err);
			}
		}
	});
}

function showTimeZones(timeZones) {
	var h =[],
		idx = -1,
		i,
		tz;

	for (i = 0; i < timeZones.length; i++) {
		tz = timeZones[i];
		h[++idx] = '<option value="';
		h[++idx] = tz.id;
		h[++idx] = '">';
		h[++idx] = tz.name;
		h[++idx] = '</option>';
	}
	$('.timezone_select').empty().html(h.join(''));
	if(!globals.gTimezone) {
		globals.gTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone;      // Browser timezone
	}
	$('#u_tz').val(globals.gTimezone);   // Set time zone in user profile
	$('#timezone').html(localise.set["c_tz"] + ": " + globals.gTimezone);   // Show timezone where this is enabled
}

function addTimeZoneToUrl(url) {
	if(url) {
		if(url.indexOf("?") > 0) {
			url += "&";
		} else {
			url += "?";
		}
		url += "tz=";
		url += encodeURIComponent(globals.gTimezone);
	}
	return url;
}

/*
 * Create the user profile dialog and get any data it needs
 */
function setupUserProfile(bs4) {
	if(bs4) {
		addUserDetailsPopupBootstrap4();
	} else {
		addUserDetailsPopup();
	}
	getAvailableTimeZones(showTimeZones);
}

function getLoggedInUser(callback, getAll, getProjects, getOrganisationsFn, hideUserDetails,
                         dontGetCurrentSurvey, getEnterprisesFn, getServerDetailsFn) {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/user",
		contentType: "application/json",
		cache: false,
		success: function(data) {
			removeHourglass();

			var i;

			globals.gServerCanSendEmail = data.sendEmail;

			globals.gEmailEnabled = data.allow_email;
			globals.gFacebookEnabled = data.allow_facebook;
			globals.gTwitterEnabled = data.allow_twitter;
			globals.gCanEdit = data.can_edit;
			globals.gSendTrail = data.ft_send_location;
			globals.gAlertSeen = data.seen;		// Alerts have been acknowledged
			globals.gLastAlertTime = data.lastalert;
			globals.gOrgId = data.o_id;
			globals.gEntId = data.e_id;
			globals.gEnterpriseName = data.enterprise_name;
			globals.gSetAsTheme = data.set_as_theme;
			globals.gNavbarColor = data.navbar_color;

			if(data.timezone) {
				globals.gTimezone = data.timezone;
			} else {
				globals.gTimezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
			}
			$('#u_tz').val(globals.gTimezone);

			if(!hideUserDetails) {
				updateUserDetails(data, getOrganisationsFn, getEnterprisesFn, getServerDetailsFn);
			}

			if(!dontGetCurrentSurvey) {	// Hack, on edit screen current survey is set as parameter not from the user's defaults
				globals.gCurrentSurvey = data.current_survey_id;
			}
			globals.gCurrentProject = data.current_project_id;
			globals.gCurrentTaskGroup = data.current_task_group_id;
			$('#projectId').val(globals.gCurrentProject);		// Set the project value for the hidden field in template upload
			if(data.groupSurveys) {
				for(i = 0; i < data.groupSurveys.length; i++) {
					globals.gGroupSurveys[data.groupSurveys[i].sId] = data.groupSurveys[i].groupIdent;
				}
			}

			setOrganisationTheme();

			if(getProjects) {
				getMyProjects(globals.gCurrentProject, callback, getAll);	// Get projects
			} else {
				if(typeof callback !== "undefined") {
					callback(globals.gCurrentSurvey);				// Call the callback with the correct current project
				}
			}

			// Add hack to show beta functions. There should be a user setting of beta tester
			if(data.name &&(data.name.indexOf("Penman") >= 0 || data.name.indexOf("Torrado") >= 0)) {
				$('.beta').show();
			}

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				console.log("Error: Failed to get user details: " + err);

				var msg = localise.set["c_error"] + ": ";
				if(err && err.indexOf('Unauthorized') >= 0) {
					msg += localise.set["c_auth"];
				} else {
					msg += err;
				}
				alert(msg);
			}
		}
	});
}

/*
 * Get the users queries
 */
function getQueries(published) {

	var url="/surveyKPI/query" + (published ? "?published=true" : "");

	addHourglass();

	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			var h = [],
				idx = -1,
				i,
				item,
				$elem = $('#export_query');

			removeHourglass();

			if(data && data.length > 0) {
				for(i = 0; i < data.length; i++) {
					item = data[i];
					h[++idx] = '<option value="';
					h[++idx] = item.id;
					h[++idx] = '">';
					h[++idx] = '<td>';
					h[++idx] = item.name;
					h[++idx] = '</option>';
				}
			}

			$elem.html(h.join(''));

		}, error: function(xhr, textStatus, err) {

			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				alert("Error: Failed to get list of queriess: " + err);
			}
		}
	});
}

/*
 * ===============================================================
 * Common functions for managing media (on both the edit page and shared resource page)
 * ===============================================================
 */

/*
 * Upload files to the server
 * Writes status to   .upload_file_msg
 */
function uploadFiles(url, formName, callback1, param, callback2) {

	var f = document.forms.namedItem(formName),
		formData = new FormData(f);

	url = addUrlParam(url, "getlist=true");
	addHourglass();
	$('.submitFiles').addClass('disabled');
	$.ajax({
		url: url,
		type: 'POST',
		xhr: function () {
			var myXhr = $.ajaxSettings.xhr();
			if(myXhr.upload){
				myXhr.upload.addEventListener('progress', progressFn, false);
			}
			return myXhr;
		},
		data: formData,
		cache: false,
		contentType: false,
		processData:false,
		success: function(data) {
			removeHourglass();
			$('.submitFiles').removeClass('disabled');
			var callbackParam = param,
				cb1 = callback1,
				cb2 = callback2;
			$('.upload_file_msg').removeClass('alert-danger').addClass('alert-success').html(localise.set["c_success"]);
			if(typeof cb1 === "function") {
				cb1(data, callbackParam);
			}
			if(typeof cb2 === "function") {
				cb2(data);
			}
			document.forms.namedItem(formName).reset();

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			$('.submitFiles').removeClass('disabled');
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				var msg = xhr.responseText;
				if(msg && msg.indexOf("no tags") >= 0) {
					msg = localise.set["msg_u_nt"];
				} else {
					msg = localise.set["msg_u_f"] + " : " + msg;
				}
				$('.upload_file_msg').removeClass('alert-success').addClass('alert-danger').html(msg);

			}
		}
	});
}

/*
 * Add a parameter to a URL
 */
function addUrlParam(url, param) {
	if(url.indexOf("?") > 0) {
		url += "&" + param;
	} else {
		url += "?" + param;
	}
	return url;
}

/*
 * Progress function for the uploading of files
 */
function progressFn(e) {
	if(e.lengthComputable){
		var w = (100.0 * e.loaded) / e.total;
		$('.progress-bar').css('width', w+'%').attr('aria-valuenow', w);
	}
}

/*
 * Refresh the media view and then set the mode to manage
 */
function refreshMediaViewManage(data, sId) {
	refreshMediaView(data, sId);
	$('.mediaManage').show();
	$('.mediaSelect').hide();
}
/*
 * Refresh the view of any attached media if the available media items has changed
 */
function refreshMediaView(data, sId) {

	var i,
		survey = globals.model.survey,
		$element,
		h = [],
		idx = -1,
		files;

	if(survey && sId) {
		// Set the display name
		$('.formName').html(survey.displayName);
		$('#survey_id').val(sId);
		gSId = sId;
	}

	if(data) {
		files = data.files;

		if(sId) {
			$element = $('#filesSurvey');
		} else {
			$element = $('#filesOrg');
		}

		for(i = 0; i < files.length; i++){
			h[++idx] = '<tr class="';
			h[++idx] = files[i].type;
			h[++idx] = '">';
			h[++idx] = '<td class="preview">';
			h[++idx] = '<a target="_blank" href="';
			h[++idx] = files[i].url;
			h[++idx] = '">';
			if(files[i].type == "audio") {
				h[++idx] = addAudioIcon();
			} else if(files[i].type == "geojson") {
				h[++idx] = addVectorMapIcon();
			} else {
				h[++idx] = '<img width="100" height="100" src="';
				h[++idx] = files[i].thumbnailUrl;
				h[++idx] = '" alt="';
				h[++idx] = files[i].name;
				h[++idx] = '">';
			}
			h[++idx] = '</a>';
			h[++idx] = '</td>';
			h[++idx] = '<td class="filename">';
			h[++idx] = '<p>';
			h[++idx] = files[i].name;
			h[++idx] = '</p>';
			h[++idx] = '</td>';
			h[++idx] = '<td class="mediaManage">';
			h[++idx] = '<p>';
			h[++idx] = files[i].size;
			h[++idx] = '</p>';
			h[++idx] = '</td>';
			h[++idx] = '<td class="mediaManage">';
			h[++idx] = '<button class="media_del btn btn-danger" data-url="';
			h[++idx] = files[i].deleteUrl;
			h[++idx] = '">';
			h[++idx] = '<span class="glyphicon glyphicon-trash" aria-hidden="true"></span> '
			h[++idx] = localise.set['c_del'];
			h[++idx] = '</button>';
			h[++idx] = '</td>';
			h[++idx] = '<td class="mediaSelect">';
			h[++idx] = '<button class="mediaAdd btn btn-success">';
			h[++idx] = '<span class="glyphicon glyphicon-plus" aria-hidden="true"></span> '
			h[++idx] = localise.set['c_add'];
			h[++idx] = '</button>';
			h[++idx] = '</td>';


			h[++idx] = '</tr>';

		}


		$element.html(h.join(""));

		$('.media_del', $element).click(function () {
			var surveyId = sId,
				url = $(this).data('url'),
				idx = url.lastIndexOf('/'),
				filename = url.substring(idx + 1);

			if(confirm(localise.set["msg_confirm_del"] + filename)) {
				delete_media(url, surveyId);
			}
		});

	}

	// If this is the organisational view we can refresh the list of choices for selecting vector maps
	if(!sId) {
		refreshVectorSelects(data);
	}
}

/*
 * Refresh the vector select lists
 */
function refreshVectorSelects(data) {

	var i,
		$vectorData = $('#vector_data'),
		$vectorStyle = $('#vector_style'),
		h_d = [],
		idx_d = -1,
		h_s = [],
		idx_s = -1,
		files;

	if(data) {
		files = data.files;

		for(i = 0; i < files.length; i++){
			if(files[i].type === "geojson") {
				h_d[++idx_d] = '<option value="';
				h_d[++idx_d] = files[i].name;
				h_d[++idx_d] = '">';
				h_d[++idx_d] = files[i].name;
				h_d[++idx_d] = '</option>';
			}

			if(files[i].type === "TODO") {
				h_s[++idx_s] = '<option value="';
				h_s[++idx_s] = files[i].name;
				h_s[++idx_s] = '">';
				h_s[++idx_s] = files[i].name;
				h_s[++idx_s] = '</option>';
			}

		}


		$vectorData.html(h_d.join(""));
		$vectorStyle.html(h_s.join(""));


	}
}

function addAudioIcon() {
	var h = [],
		idx = -1;

	h[++idx] = '<span class="has_tt" title="Audio">';
	h[++idx] = '<span class="glyphicon glyphicon-volume-up edit_type"></span>';
	h[++idx] = '</span>';

	return h.join('');
}

function addVectorMapIcon() {
	var h = [],
		idx = -1;

	h[++idx] = '<span class="has_tt" title="Audio">';
	h[++idx] = '<span class="glyphicon glyphicon glyphicon-map-marker edit_type"></span>';
	h[++idx] = '</span>';

	return h.join('');
}

function getFilesFromServer(url, sId, callback) {

	if(sId) {
		gSId = sId;
		url += '?survey_id=' + sId;
	}

	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			var surveyId = sId;
			callback(data, surveyId);

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				$('.upload_file_msg').removeClass('alert-success').addClass('alert-danger').html("Error: " + err);
			}
		}
	});
}

/*
 * Delete a media file
 */
function delete_media(url, sId) {
	addHourglass();
	$.ajax({
		url: url,
		type: 'DELETE',
		cache: false,
		success: function(data) {
			removeHourglass();
			var surveyId = sId;
			refreshMediaViewManage(data, surveyId);

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				$('.upload_file_msg').removeClass('alert-success').addClass('alert-danger').html("Error: " + err);
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

		$("#hour_glass,.hour_glass,.sk-spinner").show();
	}
	++gWait;
}

function removeHourglass() {

	--gWait;
	if(gWait === 0) {

		$("#hour_glass,.hour_glass,.sk-spinner").hide();
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
		$elem,
		selector_disable_blocked,
		h = [],
		idx = -1,
		i,
		item;

	if(selector === undefined) {
		selector = ".survey_select";	// Update the entire class of survey select controls
	}
	selector_disable_blocked = selector + ".disable_blocked";
	$elem = $(selector);
	$elem_disable_blocked = $(selector_disable_blocked);

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
					h[++idx] = '<option value="_all">';
					h[++idx] = localise.set["c_all_s"];		// All Surveys
					h[++idx] = '</option>';
				}

				for(i = 0; i < data.length; i++) {
					item = data[i];
					h[++idx] = '<option';
					if(item.blocked) {
						h[++idx] = ' class="blocked"';
					}
					h[++idx] = ' value="';
					h[++idx] = item.id;
					h[++idx] = '">';
					h[++idx] = item.displayName;
					if(item.blocked) {
						h[++idx] = ' (' + localise.set["c_blocked"] + ')';
					}
					h[++idx] = '</option>';
				}

				$elem.empty().append(h.join(''));
				$("option.blocked", $elem_disable_blocked).attr("disabled", "disabled");

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

/*
 * Load the surveys from the server
 */
function loadForms(surveyId, selector) {

	var url="/surveyKPI/surveys/forms?surveyId=" + surveyId,
		$elem,
		selector_disable_blocked,
		h = [],
		idx = -1,
		i,
		item;

	if(selector === undefined) {
		selector = ".form_select";	// Update the entire class of form select controls
	}
	$elem = $(selector);


	addHourglass();

	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {

			removeHourglass();
			$elem.empty();

			for(i = 0; i < data.length; i++) {
				item = data[i];
				h[++idx] = '<option';
				h[++idx] = ' value="';
				h[++idx] = item.id;
				h[++idx] = '">';
				h[++idx] = item.name;
				h[++idx] = '</option>';
			}

			$elem.empty().append(h.join(''));

			if(globals.gCurrentForm > 0) {
				$elem.val(globals.gCurrentForm);
			}


		},
		error: function(xhr, textStatus, err) {

			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				console.log("Error: Failed to get list of forms: " + err);
			}
		}
	});

}

// Common Function to get the language and question list (for the default language)
function getLanguageList(sId, callback, addNone, selector, setGroupList, filterQuestion) {

	if(typeof sId === "undefined") {
		sId = globals.gCurrentSurvey;
	}

	if(typeof filterQuestion === "undefined") {
		filterQuestion = "-1";
	}

	function getAsyncLanguageList(sId, theCallback, selector, filterQuestion) {
		addHourglass();
		$.ajax({
			url: languageListUrl(sId),
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				globals.gSelector.setSurveyLanguages(sId, data);
				retrievedLanguages(sId, selector, data, theCallback, filterQuestion, setGroupList, addNone);
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

	var data = globals.gSelector.getSurveyLanguages(sId);
	if(data) {
		retrievedLanguages(sId, selector, data, callback, filterQuestion, setGroupList, addNone);
	} else {
		getAsyncLanguageList(sId, callback, selector, filterQuestion);
	}
}

/*
 * Called after languages have been retrieved
 */
function retrievedLanguages(sId, selector, data, theCallback, filterQuestion, setGroupList, addNone) {
	if(selector) {
		setSurveyViewLanguages(data, undefined, selector, addNone);
	} else {
		setSurveyViewLanguages(data, undefined, '#settings_language', false);
		setSurveyViewLanguages(data, undefined, '#export_language', true);
		setSurveyViewLanguages(data, undefined, '#language_name', false);
	}

	if(data[0]) {
		var dateqId = $('#task_start').val();
		getQuestionList(sId, data[0], filterQuestion, "-1", theCallback, setGroupList, undefined, dateqId, undefined);	// Default language to the first in the list
	} else {
		if(typeof theCallback === "function") {
			theCallback();
		}
	}
}

//Function to get the question list
function getQuestionList(sId, language, qId, groupId, callback, setGroupList, view, dateqId, qName) {

	function getAsyncQuestionList(sId, language, theCallback, groupId, qId, view, dateqId, qName) {

		addHourglass();
		$.ajax({
			url: questionListUrl(sId, language, true),
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				globals.gSelector.setSurveyQuestions(sId, language, data);
				setSurveyViewQuestions(data, qId, view, dateqId, qName);

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

	getAsyncQuestionList(sId, language, callback, groupId, qId, view, dateqId, qName);
}

//Function to get the meta list
function getMetaList(sId, metaItem) {

	addHourglass();
	$.ajax({
		url: "/surveyKPI/metaList/" + sId,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			globals.gSelector.setSurveyMeta(sId, data);
			setSurveyViewMeta(data, metaItem);
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				alert(localise.set["c_error"] + ": " + err);
			}
		}
	});
}


//Set the language list in the survey view control
function setSurveyViewLanguages(list, language,elem, addNone) {

	var $languageSelect = $(elem),
		i;

	$languageSelect.empty();
	if(addNone) {
		$languageSelect.append('<option value="none">None</option>');
	}

	for(i = 0; i < list.length; i++) {
		$languageSelect.append('<option value="' + list[i] + '">' + list[i] + '</option>');
	}

	if(language) {
		$languageSelect.val(language);
	}
}

// Set the question list in the survey view control
function setSurveyViewQuestions(list, qId, view, dateqId, qName) {

	var $questionSelect = $('.selected_question'),
		$dateQuestions = $('.date_questions'),
		$questionNameSelect = $('.selected_name_question'),     // this should replace selected_question
		label;

	$questionSelect.empty();
	$questionSelect.append('<option value="-1">' + localise.set["c_none"] + '</option>');

	$questionNameSelect.empty();
	$questionNameSelect.append('<option value="-1">' + localise.set["c_none"] + '</option>');

	$dateQuestions.empty();
	$dateQuestions.append('<option value="-1">' + localise.set["ed_i_c"] + '</option>');

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
				$questionNameSelect.append('<option value="' + item.name + '">' + item.name + '</option>');
				if(item.type === 'timestamp' || item.type === 'dateTime' || item.type == 'date') {
					$dateQuestions.append('<option value="' + item.id + '">' + item.name + " : " + label + '</option>');
				}
			}
		});
	}
	if(!qId) {
		qId = "-1";
	}
	$questionSelect.val(qId);

	if(!qName) {
		qName = "-1";
	}
	$questionNameSelect.val(qName);

	if(!dateqId) {
		dateqId = "-1";
	}
	$dateQuestions.val(dateqId);

	if(view) {
		setFilterFromView(view);	// Set the filter dialog settings
	}

}

// Set the meta list in the survey view control
function setSurveyViewMeta(list, metaItem) {

	var $metaSelect = $('.selected_meta'),
		item,
		i;

	$metaSelect.empty();
	$metaSelect.append('<option value="-1">None</option>');

	if(list) {
		for(i = 0; i < list.length; i++) {
			item = list[i];
			$metaSelect.append('<option value="' + item.name + '">' + item.name + '</option>');
		}
	}
	if(!metaItem) {
		metaItem = "-1";
	}
	$metaSelect.val(metaItem);


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

/*
 * Get a survey details - depends on globals being set
 */
function getSurveyDetails(callback, get_changes) {

	var tz = globals.gTimezone;
	var url="/surveyKPI/surveys/" + globals.gCurrentSurvey;
	if(get_changes) {
		url += "?get_changes=true";
		url += "&tz=" + encodeURIComponent(tz);
	} else {
		url += "?tz=" + encodeURIComponent(tz);
	}

	if(!globals.gCurrentSurvey) {
		alert("Error: Can't get survey details, Survey identifier not specified");
	} else {
		addHourglass();
		$.ajax({
			url: url,
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				globals.model.setSurveyData(data);
				globals.model.setSettings();
				setLanguages(data.languages, callback);

				if(typeof callback == "function") {
					callback();
				}
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
					return;  // Not an error
				} else {
					if(xhr.status == 404) {
						// The current survey has probably been deleted or the user no longer has access
						globals.gCurrentSurvey = undefined;
						return;
					}
					alert("Error: Failed to get survey: " + err);
				}
			}
		});
	}

}

/*
 * Set the languages for the editor
 */
function setLanguages(languages, languageCallback) {

	var h = [],
		h2 = [],
		idx = -1,
		idx2 = -1,
		$lang_menu = $('.language_menu_list'),
		$lang = $('.language_list'),
		$lang1 = $('#language1'),
		$lang2 = $('#language2'),
		i;

	globals.gLanguage1 = 0;	// Language indexes used for translations
	globals.gLanguage2 = 0;
	if(languages.length > 1) {
		globals.gLanguage2 = 1;
	}

	for (i = 0; i < languages.length; i++) {
		h[++idx] = '<li>';
		h[++idx] = '<a data-lang="';
		h[++idx] = i;
		h[++idx] = '" href="javascript:void(0)">';
		h[++idx] = languages[i].name;
		h[++idx] = '</a>';
		h[++idx] = '</li>';

		h2[++idx2] = '<option value="';
		h2[++idx2] = i;
		h2[++idx2] = '">';
		h2[++idx2] = languages[i].name;
		h2[++idx2] = '</option>';
	}

	$lang_menu.empty().append(h.join(""));
	$lang.empty().append(h2.join(""));

	$('#langSelected').html(languages[ globals.gLanguage].name);
	$('.language_menu_list a').click(function() {
		globals.gLanguage = $(this).data("lang");
		$('#langSelected').html(languages[ globals.gLanguage].name);
		languageCallback();
	});

	$lang1.val(globals.gLanguage1);
	$lang2.val(globals.gLanguage2)
}

/*
 * Get a survey details - depends on globals being set
 */
function createNewSurvey(name, existing, existing_survey, shared_results, callback) {

	console.log("create new: " + existing + " : " + existing_survey + " : " + shared_results);

	var url="/surveyKPI/surveys/new/" + globals.gCurrentProject + "/" + name;
	if(!existing) {
		existing_survey = 0;
	}

	addHourglass();
	$.ajax({
		type: "POST",
		url: url,
		cache: false,
		dataType: 'json',
		data: {
			existing: existing,
			existing_survey: existing_survey,
			existing_form: 0,
			shared_results: shared_results
		},
		cache: false,
		success: function(data) {
			removeHourglass();

			globals.model.setSurveyData(data);
			globals.model.setSettings();
			globals.gCurrentSurvey = data.id;

			saveCurrentProject(-1, globals.gCurrentSurvey, undefined);	// Save the current survey id

			setLanguages(data.languages, callback);

			if(typeof callback == "function") {
				callback();
			}
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				bootbox.alert(localise.set["c_error"] + xhr.responseText);
			}
		}
	});
}

/*
 * Open a form for editing
 */
function openForm(type) {

	$('.reusing_form').hide();
	$('#base_on_existing').prop('checked', false);
	$('#shared_results').prop('checked', false);
	$('#new_form_name').val("");
	if(type === "new") {
		$('.existing_form').hide();
		$('.new_form').show();
		$('#openSurveyLabel').html(localise.set["tm_g_new"]);
		$('#get_form').html(localise.set["c_create"]);
		globals.gExistingSurvey = false;
	} else {
		$('.existing_form').show();
		$('.new_form').hide();
		$('#openSurveyLabel').html(localise.set["tm_g_open"]);
		$('#get_form').html(localise.set["m_open"]);
		globals.gExistingSurvey = true;
	}
	$('#openFormModal').modal('show');

}

/*
 * Return true if this is a business server
 */
function isBusinessServer() {

	var hostname = location.hostname;
	var bs = true;

	if(hostname.indexOf('smap.com.au') > 0) {
		bs = false;
	}
	if(hostname.indexOf('sg.smap.com.au') >= 0 ||
		hostname.indexOf('dev.smap.com.au') >= 0) {
		bs = true;
	}

	return bs;
}

/*
 * Return true if this is a self registration server
 */
function isSelfRegistrationServer() {
	var hostname = location.hostname;
	var sr = true;

	if(hostname !== 'localhost' &&
		hostname !== 'sg.smap.com.au' &&
		hostname.indexOf('reachnettechnologies.com') < 0 &&
		hostname.indexOf('.icanreach.com') < 0 &&
		hostname.indexOf('encontactone.com') < 0 &&
		hostname !== 'app.kontrolid.com') {
		sr = false;
	}
	return sr;
}

/*
 * Validate start and end dates
 */
function validDates() {
	var $d1 = $('#startDate'),
		$d2 = $('#endDate'),
		d1 = $d1.data("DateTimePicker").date(),
		d2 = $d2.data("DateTimePicker").date()

	if(!d1 || !d1.isValid()) {
		$('#ut_alert').show().text("Invalid Start Date");
		setTimeout(function() {
			$('.form-control', '#startDate').focus();
		}, 0);
		return false;
	}

	if(!d2 || !d2.isValid()) {
		$('#ut_alert').show().text("Invalid End Date");
		setTimeout(function() {
			$('.form-control', '#endDate').focus();
		}, 0);
		return false;
	}

	if(d1 > d2) {
		$('#ut_alert').show().text("End date must be greater than or the same as the start date");
		setTimeout(function() {
			$('.form-control', '#startDate').focus();
		}, 0);
		return false;
	}

	$('#ut_alert').hide();
	return true;
}

/*
 * Convert a date into UTC
 */
function getUtcDate($element, start, end) {

	var theDate,
		utcDate;

	if(start) {
		theDate = $element.data("DateTimePicker").date().startOf('day');
	} else if (end) {
		theDate = $element.data("DateTimePicker").date().endOf('day');
	} else {
		theDate = $element.data("DateTimePicker").date();
	}

	utcDate = moment.utc(theDate);

	console.log("date:" + theDate.format("YYYY-MM-DD HH:mm:ss"));
	console.log("UTC:" + utcDate.format("YYYY-MM-DD HH:mm:ss"));

	return utcDate.valueOf();

}

/*
 * Get a description from a change made in the editor
 */
function getChangeDescription(change, version) {

	var h =[],
		idx = -1,
		oldVal,
		newVal,
		forms = globals.model.survey.forms,
		str;

	if(change.action === "external option") {
		/*
		 * Options added from a file
		 */
		h[++idx] = 'Choice <span style="color:blue;">';
		h[++idx] = change.option.externalLabel;
		h[++idx] = '</span>';
		h[++idx] = ' from file: <span style="color:blue;">';
		h[++idx] = change.fileName;
		h[++idx] = '</span>';

	}  else if(change.action === "settings_update") {
		h[++idx] = localise.set["ed_c_settings"];
		h[++idx] = ' <span style="color:blue;">';
		h[++idx] = change.msg;
		h[++idx] = '</span>';

	}  else if(change.action === "update") {

		/*
		 * Updates to questions and options and list names
		 */
		if(change.property.prop === "type") {
			newVal = translateType(change.property.newVal);
			oldVal = translateType(change.property.oldVal);
		} else {
			newVal = change.property.newVal;
			oldVal = change.property.oldVal;
		}


		if(change.property.prop === "name") {

			// Deprecate the following when the structure of these log objects is made consistent
			if(typeof change.property.type === "optionList" || change.property.type === "unknown") {
				change.type = "choice list ";
			}

			h[++idx] = change.property.type;
			h[++idx] = ' ';
			h[++idx] = localise.set["msg_ren"],
				h[++idx] = ': <span style="color:blue;">';
			h[++idx] = newVal;
			h[++idx] = '</span>';
			h[++idx] = ' from: <span style="color:red;">';
			h[++idx] = oldVal;
			h[++idx] = '</span>';
		} else {
			str = localise.set["ed_c_chg_p"];
			str = str.replace("%s1", '"' + change.property.prop + '"');
			str = str.replace("%s2", change.property.name);
			str = str.replace("%s3", '<span style="color:blue;">' + newVal + '</span>');
			str = str.replace("%s4", '<span style="color:red;">' + oldVal + '</span>');
			h[++idx] = str;
		}

	} else if(change.action === "add")  {

		/*
		 * New questions or options
		 */
		if(change.type === "question" || change.changeType === "question"){  // deprecate checking of changeType

			str = localise.set["ed_c_add_q"];
			str = str.replace("%s1", '<span style="color:blue;">' + change.question.name + "</span>");
			var typeString;
			if(change.question.type === "string") {
				typeString = 'text';
			} else if(change.question.type === "select"){
				typeString = 'select_multiple';
			} else if(change.question.type === "select1"){
				typeString = 'select_one';
			} else {
				typeString = change.question.type;
			}
			str = str.replace("%s2", '<span style="color:red;">' + typeString + "</span>");
			h[++idx] = str;

		} else if(change.type === "option" || change.changeType === "option") {	// deprecate checking of changeType
			/*
			 * Options added or deleted from the editor
			 */
			str = localise.set["ed_c_add_o"];
			var valueStr = '<span style="color:blue;">' + change.option.value;
			if(change.option.labels && change.option.labels.length >= 1) {
				valueStr += ' (';
				valueStr += change.option.labels[0].text;
				valueStr += ')';
			}
			valueStr += '</span>';
			str = str.replace("%s1", valueStr);
			str = str.replace("%s2", '<span style="color:blue;">' + change.option.optionList + '</span>');
			h[++idx] = str;
		}

	}  else if(change.action === "move")  {

		/*
		 * New questions or options
		 */
		h[++idx] = 'Moved ';

		if(change.type === "question" || change.changeType === "question") {  // deprecate checking of changeType){

			h[++idx] = 'question <span style="color:blue;">';
			h[++idx] = change.question.name;
			if(change.question.sourceSeq >= 0) {
				h[++idx] = '</span> from position <span style="color:red;">';
				h[++idx] = change.question.sourceSeq;
				h[++idx] = '</span> in form ';
				h[++idx] = forms[change.question.sourceFormIndex].name;
			} else {
				h[++idx] = '</span> from form ';
				h[++idx] = forms[change.question.sourceFormIndex].name;
			}
			h[++idx] = '</span> to position <span style="color:red;">';
			h[++idx] = change.question.seq;
			h[++idx] = '</span>';
			h[++idx] = ' in form ';
			h[++idx] = forms[change.question.formIndex].name;


		} else if(change.type === "option") {

			h[++idx] = 'choice <span style="color:blue;">';
			h[++idx] = change.option.value;
			if(change.option.labels && change.option.labels.length >= 1) {
				h[++idx] = ' (';
				h[++idx] = change.option.labels[0].text;
				h[++idx] = ')';
			}
			h[++idx] = '</span>';
			h[++idx] = ' from choice list: <span style="color:blue;">';
			h[++idx] = change.option.sourceOptionList;
			h[++idx] = '</span>';
			h[++idx] = ' to choice list: <span style="color:blue;">';
			h[++idx] = change.option.optionList;
			h[++idx] = '</span>';
		}

	} else if(change.action === "delete")  {

		if(change.type === "question" || change.changeType === "question"){

			h[++idx] = localise.set["ed_c_del_q"];

			h[++idx] = ' <span style="color:blue;">';
			h[++idx] = change.question.name;
			h[++idx] = '</span>';

		} else if(change.type === "option") {

			str = localise.set["ed_c_del_o"];
			var valueStr = '<span style="color:blue;">' + change.option.value;
			if(change.option.labels && change.option.labels.length >= 1) {
				valueStr  += ' (';
				valueStr  += change.option.labels[0].text;
				valueStr  += ')';
			}
			valueStr  += '</span>';
			str = str.replace("%s1", valueStr);
			str = str.replace("%s2", '<span style="color:blue;">' + change.option.optionList + '</span>');
			h[++idx] = str;
		}
	} else if(change.action === "set_required")  {
		h[++idx] = 'All questions set ';
		if(change.msg.indexOf('not') < 0) {
			h[++idx] = '<span style="color:blue;">';
			h[++idx] = 'required';
			h[++idx] = '</span>';
		} else {
			h[++idx] = '<span style="color:red;">';
			h[++idx] = 'not required';
			h[++idx] = '</span>';
		}

	} else if(change.action === "upload_template")  {

		if(version > 1) {
			h[++idx] = localise.set["msg_survey_replaced"];
		} else {
			h[++idx] = localise.set["msg_survey_loaded"];
		}

	} else {
		h[++idx] = change.type;
		h[++idx] = ' ';
		h[++idx] = change.name;
		h[++idx] = ' changed to: <span style="color:blue;">';
		h[++idx] = change.newVal;
		h[++idx] = '</span>';
		h[++idx] = ' from: <span style="color:red;">';
		h[++idx] = change.oldVal;
		h[++idx] = '</span>';
	}

	return h.join('');
}

// Translate types for use in change description
function translateType(input) {
	if(input === "string") {
		output = "text";
	} else {
		output = input;
	}
	return output;
}

/*
 * Get the shared locations from the server
 */
function getLocations(callback) {

	var url="/surveyKPI/tasks/locations";

	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			if(typeof callback === "function") {
				callback(data);
			}
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				console.log("Error: Failed to get list of locations: " + err);
			}
		}
	});

}

/*
 * update Location group list
 */
function refreshLocationGroups(tags, includeAll) {

	var g = undefined,
		h = [],
		idx = -1,
		i;

	var includeNfc = $('#includeNfc').prop('checked'),
		includeGeo = $('#includeGeo').prop('checked');

	if(tags) {
		for(i = 0; i < tags.length; i++) {
			if(includeAll || includeLocation(includeNfc, includeGeo, tags[i].uid, tags[i].lat, tags[i].lon)) {

				if (g != tags[i].group) {

					g = tags[i].group;
					if (typeof gCurrentGroup === "undefined") {
						gCurrentGroup = g;
					}

					if(includeAll) {
						if (gCurrentGroup === g) {
							$('.location_group_list_sel').text(g);
						}
						h[++idx] = '<a class="dropdown-item" href="#">';
						h[++idx] = g;
						h[++idx] = '</a>';
					} else {
						h[++idx] = '<option';
						if (gCurrentGroup === g) {
							h[++idx] = ' selected';
						}
						h[++idx] = ' value="';
						h[++idx] = g;
						h[++idx] = '">';
						h[++idx] = g;
						h[++idx] = '</option>';
					}
				}
			}
		}
	}

	$('.location_group_list').empty().html(h.join(""));
}

/*
 * Add the locations (NFC tags or geofence) to any drop down lists that use them
 */
function setLocationList(locns, current, currentGroup) {

	var h = [],
		idx = -1,
		i;

	if(locns && locns.length) {
		h[++idx] = '<option value="-1">';
		h[++idx] = localise.set["c_none"];
		h[++idx] = '</option>';
		for(i = 0; i < locns.length; i++) {
			if(locns[i].group === currentGroup) {
				h[++idx] = '<option value="';
				h[++idx] = i;
				h[++idx] = '">';
				h[++idx] = locns[i].name;
				h[++idx] = '</option>';
			}
		}
	}

	$('.location_select').empty().append(h.join(""));
	$('.location_select').val(current);


}

/*
 * Test for whether or not a location should be shown in the resource page
 */
function includeLocation(includeNfc, includeGeo, uid, lat, lon) {
	var include = false;

	if(includeNfc && typeof uid !== 'undefined' && uid !== '') {
		include = true;
	}
	if(!include && includeGeo && lat != 0 && lon != 0) {
		include = true;
	}

	return include;
}

/*
 * Convert a timestamp in UTC to local time and return a date object
 */
function localTimeAsDate(utcTime) {
	var utcDate,
		localTime;

	if(utcTime) {
		if(utcTime.indexOf('+') > 0) {
			utcDate  = moment.utc(utcTime, 'YYYY-MM-DD HH:mm:ss Z').toDate();
		} else {
			utcDate  = moment.utc(utcTime, 'YYYY-MM-DD HH:mm:ss').toDate();
		}
		localTime = moment(utcDate);
	}
	return localTime;
}

/*
 * Convert a timestamp in UTC to local time
 */
function localTime(utcTime) {
	var utcDate,
		localTime;

	if(utcTime) {
		if(utcTime.indexOf('+') > 0) {
			utcDate  = moment.utc(utcTime, 'YYYY-MM-DD HH:mm:ss Z').toDate();
		} else {
			utcDate  = moment.utc(utcTime, 'YYYY-MM-DD HH:mm:ss').toDate();
		}
		localTime = moment(utcDate).format('YYYY-MM-DD HH:mm:ss');
	}
	return localTime;
}


function utcTime(localTime) {

	var utcTime,
		localDate;

	if(localTime) {
		localDate = moment(localTime).toDate();
		utcTime =  moment.utc(localDate).format('YYYY-MM-DD HH:mm:ss');
	}
	return utcTime;

}

function formatLocalTime(localTime) {
	var l;
	if(localTime.indexOf('+') > 0) {
		l = moment(localTime, 'YYYY-MM-DD HH:mm:ss Z').toDate();
	} else {
		l = moment(localTime, 'YYYY-MM-DD HH:mm:ss').toDate();
	}
	var lf = moment(l).format('YYYY-MM-DD HH:mm:ss');

	if(lf === 'Invalid date') {
		lf = '';
	}
	return lf;
}

/*
 * Get the difference between 2 times
 */
function timeDifference(fromTime, toTime) {
	var from,
		to,
		timeDiff;

	if(fromTime && toTime) {
		if(fromTime.indexOf('+') > 0) {
			from  = moment(fromTime, 'YYYY-MM-DD HH:mm:ss Z');
		} else {
			from  = moment.utc(fromTime, 'YYYY-MM-DD HH:mm:ss');
		}
		if(toTime.indexOf('+') > 0) {
			to  = moment(toTime, 'YYYY-MM-DD HH:mm:ss Z');
		} else {
			to  = moment(toTime, 'YYYY-MM-DD HH:mm:ss');
		}

		timeDiff = moment.duration(to.diff(from));
	}
	return timeDiff;
}


function downloadFile(url) {

	// Add a cache buster
	if(url.indexOf("?") < 0) {
		url += "?";
	} else {
		url += "&";
	}
	url += "_v" + new Date().getTime().toString();

	$("body").append("<iframe src='" + url + "' style='display: none;' ></iframe>");
	// Check for errors allow 5 seconds for an error to be returned
	setTimeout(downloadFileErrorCheck, 5000);
}

// Show an error generated by file download
function downloadFileErrorCheck() {
	var msg = $("iframe").last().contents().find('body').html();
	if(msg && msg.indexOf("Error:") === 0) {
		alert(msg.substring(7));	// Jump over "Error: "
	} else if(msg && msg.length > 0) {
		alert(msg);
	}
}

/*
 * Post data to be converted into a file
 */
function generateFile(url, filename, format, mime, data, sId, managedId, title, project, charts, chartData, settings, tz) {

	var payload = "sId=" + sId;
	payload += "&format=" + format;
	if(managedId) {
		payload += "&managedId=" + managedId;
	}

	if(data) {
		payload += "&data=" + encodeURIComponent(JSON.stringify(data));
	}
	if(title) {
		payload += "&title=" + title;
	}
	if(project) {
		payload += "&project=" + project;
	}
	if(charts) {
		payload += "&charts=" + encodeURIComponent(JSON.stringify(charts));
	}
	if(chartData) {
		payload += "&chartdata=" + encodeURIComponent(JSON.stringify(chartData));
	}
	if(settings) {
		payload += "&settings=" + encodeURIComponent(JSON.stringify(settings));
	}
	if(tz) {
		payload += "&tz=" + encodeURIComponent(JSON.stringify(tz));
	}
	payload = payload.replace(/%20/g, '+');

	var xhr = new XMLHttpRequest();
	xhr.open('POST', url, true);
	xhr.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
	xhr.responseType = 'blob';

	xhr.onload = function(e) {
		if (this.status == 200) {
			// get binary data as a response
			var blob = new Blob([this.response], { type: mime });
			var downloadUrl = URL.createObjectURL(blob);
			var a = document.createElement("a");
			a.href = downloadUrl;
			a.download = filename;
			document.body.appendChild(a);
			a.click();
			setTimeout(function(){
				document.body.removeChild(a);
				window.URL.revokeObjectURL(url);
			}, 100);
		} else {
			alert(localise.set["c_error"]);
		}
	};

	xhr.onerror = function(e) {
		alert("Error: Upload Failed");
	}
	if(Pace) {
		Pace.restart();
	}
	xhr.send(payload);

}

/*
 * Post data to be converted into a file
 * This version creates a temporary file on the server
 */
function sendReports(url, filename, format, mime, data, sId, managedId, title, project, charts) {

	var update = {
		sId: sId,
		format: format,
		managedId: managedId,
		data: data,
		title: title,
		project: project,
		charts: charts
	}
	var saveString = JSON.stringify(update);

	addHourglass();
	$.ajax({
		type: "POST",
		dataType: 'text',
		cache: false,
		contentType: "application/json",
		url: url,
		data: { report: saveString },
		success: function(data, status) {
			removeHourglass();

		}, error: function(data, status) {
			removeHourglass();
			alert(data.responseText);
		}
	});

}

/*
 * Get the currently selected rows of datatable data as a json array
 * Also convert the JSON object into an array of Key values pairs. This allows easy converion
 * to a java object on the server
 */
function getTableData(table, columns, format) {

	var rows = table.rows({
		order:  'current',  // 'current', 'applied', 'index',  'original'
		page:   'all',      // 'all',     'current'
		search: 'applied',     // 'none',    'applied', 'removed'
	}).data();

	var data = [],
		cols = [],
		i, j;

	for(i = 0; i < rows.length; i++) {
		cols = [];
		for(j = 0; j < columns.length; j++) {
			if(format === "xlsx" || !columns[j].hide) {
				var k = columns[j].displayName;
				var v = rows[i][k];

				if (typeof v !== "string") {
					v = JSON.stringify(v);
				}
				cols.push({
					k: k,
					v: v
				})
			}
		}
		data.push(cols);
	}

	return data;


}

/*
 * Get server settings
 */
function getMapboxDefault(callback, param) {

	if(!globals.gMapboxDefault) {
		addHourglass();
		$.ajax({
			url: '/surveyKPI/server/mapbox',
			cache: false,
			success: function(data) {
				removeHourglass();
				globals.gMapboxDefault = data;
				if(typeof callback === "function") {
					callback(param);
				}
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
					return;  // Not an error
				} else {
					alert(localise.set["error"] + ": " + err);
				}
			}
		});
	} else {
		if(typeof callback === "function") {
			callback(param);
		}
	}
}


/*
 * Get google map api
 */
function getGoogleMapApi(callback, map) {

	console.log("getGoogleMapApi");

	if(!window.smapLoadedGMaps && !window.smapGMapsLoading) {
		console.log("about to call server");

		window.smapGMapsLoading = true;

		window.smapGMapsToLoad = [];
		window.smapGMapsToLoad.push({
			fn: callback,
			locn: map
		});

		addHourglass();
		$.ajax({
			url: '/surveyKPI/server/googlemaps',
			cache: false,
			success: function(data) {

				removeHourglass();
				console.log("Retrieved map keys from server");

				var gElement = document.createElement('script');
				var key = "";
				if(data) {
					key = "?key=" + data;
				}
				//gElement.src = "//maps.google.com/maps/api/js?v=3.6&amp";
				gElement.src = "https://maps.googleapis.com/maps/api/js" + key;
				if(typeof callback === "function") {
					gElement.onload = onLoad;
				}
				document.getElementsByTagName('head')[0].appendChild(gElement);

				function onLoad() {

					var i;

					window.smapGMapsLoading = false;
					window.smapLoadedGMaps = true;

					console.log("Google map loaded");

					for(i = 0; i < window.smapGMapsToLoad.length; i++) {
						console.log("map callback");
						window.smapGMapsToLoad[i].fn(window.smapGMapsToLoad[i].locn);
					}
					delete window.smapGMapsToLoad;
				}

			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
					return;  // Not an error
				} else {
					alert(localise.set["error"] + " " + err);

				}
			}
		});

	} else if(window.smapLoadedGMaps) {
		console.log("Already loaded calling map callback");
		callback(map);
	} else {
		console.log("Adding callback to queue");
		window.smapGMapsToLoad.push({
			fn: callback,
			locn: map
		});
	}
}

/*
 * Add google layers to a map
 */
function addGoogleMapLayers(map) {
	try {
		map.addLayer(new OpenLayers.Layer.Google("Google Satellite",{type: google.maps.MapTypeId.SATELLITE, 'sphericalMercator': true, numZoomLevels: 22}));
		map.addLayer(new OpenLayers.Layer.Google("Google Maps",{type: google.maps.MapTypeId.ROADMAP, 'sphericalMercator': true, numZoomLevels: 22}));
		map.addLayer(new OpenLayers.Layer.Google("Google Hybrid",{type: google.maps.MapTypeId.HYBRID, 'sphericalMercator': true, numZoomLevels: 22}));
	} catch (err) {
		// Fail silently, the user may not want google maps - this is probably caused by a missing maps api key
	}
}

function remoteSurveyChanged() {
	$('#fwd_rem_survey_id').val($('#fwd_rem_survey :selected').val());
	$('#fwd_rem_survey_nm').val($('#fwd_rem_survey :selected').text());
}

/*
 * Get a list of custom reports
 */
function getReports(callback1, callback2, type) {

	var url="/surveyKPI/custom_reports";

	if(type) {
		url += "?type=" + type;
	}

	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			var cb1 = callback1,
				cb2 = callback2,
				t = type;
			globals.gReports = data;
			if(typeof cb1 === "function") {
				cb1(data, cb1, cb2, t);
			}
			if(typeof cb2 === "function") {
				cb2(data, cb1, cb2, t);
			}

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
 * Allow the user to pick a report
 */
function showReportList(data) {
	var h = [],
		idx = -1,
		i;

	removeHourglass();

	if(data.length === 0) {

		// Enable / disable elements specifically for managed forms
		$('.selectmanaged').show();
		$('.no_oversight').show();
	} else {
		$('.no_oversight').hide();
		$('.selectmanaged').show();

		h[++idx] = '<option value="0">';
		h[++idx] = localise.set["c_none"];
		h[++idx] = '</option>';
		for(i = 0; i < data.length; i++) {
			h[++idx] = '<option value="';
			h[++idx] = data[i].id;
			h[++idx] = '">';
			h[++idx] = data[i].name;
			h[++idx] = '</option>';
		}
		$('.customReportList').empty().html(h.join(''));
	}
}

/*
 * Show the Custom Reports in a table
 */
function refreshCustomReportView(data, callback1, callback2, type) {

	var $selector = $('#cr_list'),
		i,
		h = [],
		idx = -1;

	$('.panel_msg').show();
	$('#addReportPopup').modal("hide");

	data = data || [];
	globals.gReports = data;

	h[++idx] = '<table class="table">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th>' + localise.set["c_name"], + '</th>';
	h[++idx] = '<th>' + localise.set["c_type"] + '</th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody class="table-striped">';

	for(i = 0; i < data.length; i++) {

		h[++idx] = '<tr>';

		// name
		h[++idx] = '<td>';
		h[++idx] = data[i].name;
		h[++idx] = '</td>';

		// type
		h[++idx] = '<td>';
		h[++idx] = data[i].type;
		h[++idx] = '</td>';

		// actions
		h[++idx] = '<td>';

		h[++idx] = '<button type="button" data-idx="';
		h[++idx] = i;
		h[++idx] = '" class="btn btn-default btn-sm rm_cr">';
		h[++idx] = '<i class="fa fa-trash-o"></i></button>';

		h[++idx] = '<button type="button" data-idx="';
		h[++idx] = i;
		h[++idx] = '" class="btn btn-default btn-sm download_cr">';
		h[++idx] = '<i class="fa fa-download"></i></button>';

		h[++idx] = '</td>';
		// end actions

		h[++idx] = '</tr>';
	}

	h[++idx] = '</tbody>';
	h[++idx] = '</table>';

	$selector.empty().append(h.join(''));

	$(".rm_cr", $selector).click(function(){
		var idx = $(this).data("idx");
		if(confirm(localise.set["msg_confirm_del"] + " " + globals.gReports[idx].name)) {
			deleteCustomReport(globals.gReports[idx].id, type);
		}
	});

	$(".download_cr", $selector).click(function(){
		var idx = $(this).data("idx");
		downloadFile("/surveyKPI/custom_reports/xls/" + globals.gReports[idx].id +
			"?filetype=xls&filename=" + cleanFileName(globals.gReports[idx].name));
	});


}

function deleteCustomReport(id, type) {

	var url = "/surveyKPI/custom_reports/" + id;
	if(type) {
		url += "?type=" + type;
	}

	addHourglass();
	$.ajax({
		type: "DELETE",
		url: url,
		success: function(data, status) {
			removeHourglass();
			var t = type;
			console.log("delete: " + t + " : " + type);
			getReports(refreshCustomReportView, showReportList, t);
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				alert(localise.set["msg_err_del"] + xhr.responseText);
			}
		}
	});
}

/*
 * Get the list of available roles from the server
 */
function getRoles(callback) {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/role/roles",
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			globals.gRoleList = data;
			if(typeof callback === "function") {
				callback();
			}
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				alert(localise.set["msg_err_get_r"] + " " + err);
			}
		}
	});
}

/*
 * Get the alerts
 *
function getAlerts() {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/user/alerts",
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			showAlerts(data);
			$('.alert_count').html(data.length);
			setTimeout(getAlerts, 600000);
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert(localise.set["msg_err_get_a"] + " " + err);
			}
		}
	});	
}
*/

/*
function showAlerts(alerts) {
	var h = [],
		idx = -1,
		i,
		a;

	 *
	 * Check for a change in the alert list
	 *
	if(alerts.length > 0) {
		if(alerts[0].updatedTime != globals.gLastAlertTime) {
			$('.alert_icon').addClass("text-danger");
			$('#chime')[0].play();
			
			globals.gLastAlertTime = alerts[0].updatedTime;
			globals.gAlertSeen = false;
			saveLastAlert(globals.gLastAlertTime, false);
		}
		
	}
	
	 *
	 * Drop down messages have 4 parts
	 * At left icon
	 * Main message
	 * Small message to right of main message
	 * Footer message (Required)
	 
	for(i = 0; i < alerts.length; i++) {
		a = alerts[i];
		h[++idx] = '<li>';
		h[++idx] = '<div class="dropdown-messages-box">';
		if(a.link) {
			h[++idx] = '<a href="';
			h[++idx] = a.link;
			h[++idx] = '" class="pull-left">';
		} 
		h[++idx] = '<i class="fa ';
		if(a.status == "complete") {
			h[++idx] = "green-bg "
		} else if(a.priority == 1) {
			h[++idx] = "red-bg "
		} else if(a.priority == 2) {
			h[++idx] = "orange-bg "
		} else {
			h[++idx] = "blue-bg "
		}
    	h[++idx] = 'fa-edit';
    	h[++idx] = ' fa-2x"></i>';
    	if(a.link) {
         	h[++idx] = '</a>';
        }

        h[++idx] = '<div class="media-body">';
        h[++idx] = '<small class="pull-right text-navy">'; 
        h[++idx] = getInterval(a.since);
        h[++idx] = '</small>';
        h[++idx] = '<strong>';
        h[++idx] = a.message;
        h[++idx] = '</strong><br>';
        h[++idx] =  '<small class="text-muted"></small>';
        h[++idx] = '</div>';
        h[++idx] = '</div>';
       
        h[++idx] = '</li>';
        h[++idx] = '<li class="divider"></li>';
	}
	$('.dropdown-messages').html(h.join(''));
}
*/

function getInterval(seconds) {
	if(seconds < 2) {
		return seconds + ' ' + localise.set["i_sec"];
	} else if(seconds < 60) {
		return seconds + ' ' + localise.set["i_secs"];
	} else if(seconds < 120) {
		return Math.floor(seconds/ 60) + ' ' + localise.set["i_min"];
	} else if(seconds < 3600) {
		return Math.floor(seconds/ 60) + ' ' + localise.set["i_mins"];
	} else if(seconds < (3600 * 2)) {
		return Math.floor(seconds/ (60 * 60)) + ' ' + localise.set["i_hour"];
	} else if(seconds < (3600 * 24)) {
		return Math.floor(seconds/ (60 * 60)) + ' ' + localise.set["i_hours"];
	} else if(seconds < (3600 * 24 * 2)) {
		return Math.floor(seconds/ (60 * 60 * 24)) + ' ' + localise.set["i_day"];
	} else if(seconds < (3600 * 24)) {
		return Math.floor(seconds/ (60 * 60 * 24)) + ' ' + localise.set["i_days"];
	}
}

/*
 * Clean the filename so that it can be passed in a URL
 */
function cleanFileName(filename) {

	var n;

	n = filename.replace(/\//g, '_');	// remove slashes from the filename
	n = n.replace(/[#?&]/g, '_');		// Remove other characters that are not wanted
	n = n.replace("'", "", 'g');		// Remove apostrophes

	return n;
}

/*
 * Convert a :: separated string containing link information into target survey id and question id
 */
function getLinkedTarget(input) {
	var lt,
		values = [];

	if(input) {

		lt = {
			sId: 0,
			qId: 0
		}

		values = input.split("::");
		if(values.length > 0) {
			lt.sId = +values[0].trim();
		}
		if(values.length > 1) {
			lt.qId = +values[1].trim();
		}
	}

	return lt;
}

/*
 * Add a list of forms to pick from during export
 */
function addFormPickList(sMeta, checked_forms) {

	var h = [],
		idx = -1,
		i;

	// Start with the top level form
	for(i = 0; i < sMeta.forms.length; i++) {
		if(sMeta.forms[i].p_id == 0) {
			$(".osmforms").html(addFormToList(sMeta.forms[i], sMeta, 0, true, false, checked_forms));
			$(".selectforms").html(addFormToList(sMeta.forms[i], sMeta, 0, false, false, checked_forms));
			$(".shapeforms,.taforms").html(addFormToList(sMeta.forms[i], sMeta, 0, true, true, checked_forms));
		}
	}

	$("button",".selectforms").click(function() {
		var $this = $(this),
			$check = $this.parent().find("input"),
			val,
			val_array = [];

		val = $check.val();
		val_array= val.split(":");
		if(val_array.length > 1) {
			if(val_array[1] === "true") {
				$check.val(val_array[0] + ":false");
				$this.text("Pivot");
			} else {
				$check.val(val_array[0] + ":true");
				$this.text("Flat");
			}
			$this.toggleClass('exportflat');
			$this.toggleClass('exportpivot');
		}

		return false;
	});
}

/*
 * Add a list of date questions to pick from
 */
function addDatePickList(sMeta, currentDate) {

	var h = [],
		idx = -1,
		i,
		value,
		key;

	if(sMeta && sMeta.dates) {
		for(i = 0; i < sMeta.dates.length; i++) {

			key = sMeta.dates[i].name;

			h[++idx] = '<option value="';
			h[++idx] = sMeta.dates[i].id;
			h[++idx] = '">';
			if(key === "Upload Time" || key === "_start" || key === "_end") {
				key = localise.set[key];
			} else if(key === "Scheduled Start") {
				key = localise.set["c_scheduled"]
			}
			h[++idx] = key;
			h[++idx] = '</option>';

		}

		$(".date_question").html((h.join('')));

		if(typeof currentDate !== "undefined" && currentDate != 0) {
			value = currentDate;
		} else {
			value = $("#settings_date_question").val();
		}
	}
}

function addFormToList(form, sMeta, offset, osm, set_radio, checked_forms) {

	var h = [],
		idx = -1,
		i,
		type,
		checked;

	if (set_radio) {
		type = "radio";
	} else {
		type = "checkbox";
	}

	// Set checked value based on previous selections
	if(set_radio && offset == 0) {
		checked = 'checked="checked"';
	} else {
		if (offset == 0 && (!checked_forms || checked_forms.length == 0)) {
			checked = 'checked="checked"';
		} else {
			checked = '';
		}
	}
	if(checked_forms && checked_forms.length > 0) {
		for(i = 0; i < checked_forms.length; i++) {
			if(form.f_id == checked_forms[i]) {
				checked = 'checked="checked"';
				break;
			}
		}
	} else {}

	h[++idx] = '<div class="' + type + '"';
	h[++idx] = '<span style="padding-left:';
	h[++idx]= offset + 'px;">';
	h[++idx] = '<label>';
	h[++idx] = '<input class="osmform" type="' + type + '" ' + checked + ' name="osmform" value="';
	h[++idx] = form.f_id;
	if(!osm) {
		h[++idx] = ':false"/>';
	} else {
		h[++idx] = '">';
	}
	h[++idx] = form.form;
	h[++idx] = '</label>';
	if(form.p_id != 0 && !osm) {
		h[++idx] = ' <button class="exportpivot">Pivot</button>';
	}
	h[++idx]= '</div>';

	// Add the children (recursively)
	for(i = 0; i < sMeta.forms.length; i++) {
		if(sMeta.forms[i].p_id != 0  && sMeta.forms[i].p_id == form.f_id) {
			h[++idx] = addFormToList(sMeta.forms[i], sMeta, offset + 20, osm, set_radio, checked_forms);
		}
	}

	return h.join('');
}

function getViewLanguages(view) {

	if(view.sId != -1) {
		var url = languageListUrl(view.sId);
		$.getJSON(url, function(data) {
			globals.gSelector.setSurveyLanguages(view.sId, data);
			setSurveyViewLanguages(data, view.lang, '#settings_language', false);
			setSurveyViewLanguages(data, view.lang, '#export_language', true);
		});
	}

}

function validateEmails(emails) {
	var valid = true,
		i;
	if(emails && emails.trim().length > 0) {
		var emailArray = emails.split(",");
		for (i = 0; i < emailArray.length; i++) {
			var validEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+.[A-Z]{2,4}/igm;
			if (!validEmail.test(emailArray[i])) {
				valid = false;
				break;
			}
		}
	}
	return valid;
}

/*
 * Prevent the menu bar from extending over two lines
 */
// From: http://stackoverflow.com/questions/20247945/bootstrap-3-navbar-dynamic-collapse
/*
function autocollapse() {
	var $navbar = $('#autocollapse');
    $navbar.removeClass('collapsed'); 
    if($navbar.innerHeight() > 60) // check if we've got 2 lines
        $navbar.addClass('collapsed'); // force collapse mode
}

$(document).on('ready', autocollapse);
$(window).on('resize', autocollapse);
*/

/*
 * Get the roles for a survey
 */
function getSurveyRoles(sId, selectedRoles, setall) {

	if (!gTasks.cache.surveyRoles[sId]) {
		addHourglass();
		$.ajax({
			url: "/surveyKPI/role/survey/" + sId + "?enabled=true",
			dataType: 'json',
			cache: false,
			success: function (data) {
				removeHourglass();
				gTasks.cache.surveyRoles[sId] = data;
				showRoles(gTasks.cache.surveyRoles[sId], selectedRoles);
			},
			error: function (xhr, textStatus, err) {

				removeHourglass();
				if (xhr.readyState == 0 || xhr.status == 0) {
					return;  // Not an error
				} else {
					console.log("Error: Failed to get roles for a survey: " + err);
				}
			}
		});
	} else {
		showRoles(gTasks.cache.surveyRoles[sId], selectedRoles, setall);
	}
}

/*
 * Show the roles
 */
function showRoles(data, selectedRoles, setall) {

	var h = [],
		idx = -1,
		i;

	$('.role_select_roles').empty();
	if (data.length > 0) {
		for (i = 0; i < data.length; i++) {
			h[++idx] = '<div class="checkbox">';
			h[++idx] = '<label><input type="checkbox" value="';
			h[++idx] = data[i].id;
			h[++idx] = '"';
			if(setall || roleSelected(data[i].id, selectedRoles)) {
				h[++idx] = ' checked';
			}
			h[++idx] = '>';
			h[++idx] = data[i].name;
			h[++idx] = '</label>';
			h[++idx] = '</div>';
		}
		$('.role_select').show();
		$('.role_select_roles').append(h.join(''));
	}
}

function roleSelected(roleId, selectedRoles) {
	var sel = false;
	if(selectedRoles) {
		for(var i = 0; i < selectedRoles.length; i++) {
			if(selectedRoles[i].id == roleId) {
				sel = true;
				break;
			}
		}
	}
	return sel;
}

 /*
  * Get all the surveys that a user can access
  */
function getAccessibleSurveys($elem, includeNone, includeBlocked, groupsOnly) {

	var url="/surveyKPI/surveys";
	var hasParam = false;
	if(includeBlocked) {
		url += hasParam ? '&' : '?';
		url += 'blocked=true';
		hasParam = true;
	}
	if(groupsOnly) {
		url += hasParam ? '&' : '?';
		url += 'groups=true';
		hasParam = true;
	}

	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			var h = [],
				idx = -1,
				i;

			if(includeNone) {
				h[++idx] = '<option value="">';
				h[++idx] = localise.set["c_none"]
				h[++idx] = '</option>';
			}
			for(i = 0; i < data.length; i++) {
				h[++idx] = '<option value="';
				h[++idx] = data[i].ident;
				h[++idx] = '">';
				h[++idx] = data[i].projectName;
				h[++idx] = ' : ';
				h[++idx] = data[i].displayName;
				h[++idx] = '</option>';
			}
			$elem.empty().append(h.join(''));

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
 * Get all the csv files that a user can access
 */
function getAccessibleCsvFiles($elem, includeNone) {

	var url="/surveyKPI/csv/files";

	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			globals.gCsvFiles = data;
			var h = [],
				idx = -1,
				i;

			if(includeNone) {
				h[++idx] = '<option value="">';
				h[++idx] = localise.set["c_none"]
				h[++idx] = '</option>';
			}
			for(i = 0; i < data.length; i++) {
				h[++idx] = '<option value="';
				h[++idx] = i;
				h[++idx] = '">';
				h[++idx] = data[i].filename;
				h[++idx] = '</option>';
			}
			$elem.empty().append(h.join(''));

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				console.log("Error: Failed to get list of csv files: " + err);
			}
		}
	});
}

 /*
  * Get the questions in a survey
  */
function getQuestionsInSurvey($elem, sIdent, includeNone) {

	function populateElement($elem, data) {
		var h = [],
			idx = -1,
			i;

		if (includeNone) {
			h[++idx] = '<option value="">';
			h[++idx] = localise.set["c_none"];
			h[++idx] = '</option>';
		}
		for (i = 0; i < data.length; i++) {
			h[++idx] = '<option value="';
			h[++idx] = data[i].name;
			h[++idx] = '">';
			h[++idx] = data[i].name;
			h[++idx] = '</option>';
		}
		$elem.empty().append(h.join(''));
	}

	if(gCache[sIdent]) {
		populateElement($elem, gCache[sIdent]);
	} else {
		if (sIdent !== "0") {
			addHourglass();
			$.ajax({
				url: "/surveyKPI/questionListIdent/" + sIdent + "/none?exc_ssc=true",
				dataType: 'json',
				cache: false,
				success: function (data) {
					removeHourglass();
					var theIdent = sIdent;
					var $theElem = $elem;

					gCache[theIdent] = data;
					populateElement($theElem, data);

				},
				error: function (xhr, textStatus, err) {
					removeHourglass();
					if (xhr.readyState == 0 || xhr.status == 0) {
						return;  // Not an error
					} else {
						alert(localise.set["msg_err_get_q"] + ": " + err);
					}
				}
			});
		} else {
			if (includeNone) {
				$elem.empty().append('option value="0">' + localise.set["c_none"] + '</option>');
			}
		}
	}

}

function getQuestionsInCsvFile($elem, index, includeNone) {
	var h = [],
		idx = -1,
		i;
	var data = globals.gCsvFiles[index].headers;

	if (includeNone) {
		h[++idx] = '<option value="">';
		h[++idx] = localise.set["c_none"];
		h[++idx] = '</option>';
	}
	for (i = 0; i < data.length; i++) {
		h[++idx] = '<option value="';
		h[++idx] = data[i].fName;
		h[++idx] = '">';
		h[++idx] = data[i].fName;
		h[++idx] = '</option>';
	}
	$elem.empty().append(h.join(''));
}

function tokenizeAppearance(input) {
	var chunks = [];
	var tokens = [];
	var chunkTokens = [];
	var i;
	var j;
	var chunk;

	// only search needs special treatment
	var idx1 = input.indexOf('search');
	if(idx1 >= 0) {
		chunks.push({
			val:input.substring(0, idx1),
			type: "text"
		});
		if(idx1 < input.length) {
			var idx2 = input.indexOf(')', idx1 + 1);
			if(idx2 >= 0) {
				chunks.push({
					val: input.substring(idx1, idx2 + 1),
					type: "fn"
				});
				if(idx2 < input.length) {
					chunks.push({
						val: input.substring(idx2 + 1),
						type: "text"
					});
				}
			}
		}
	} else {
		chunks.push({
			val: input,
			type: "text"
		});
	}
	for(i = 0; i < chunks.length; i++) {
		chunk = chunks[i].val.trim();
		if(chunk.length > 0) {
			if(chunks[i].type === "text") {
				chunkTokens = chunk.split(/(\s+)/);
			} else {
				chunkTokens.push(chunk);
			}
			for(j = 0; j < chunkTokens.length; j++) {
				tokens.push(chunkTokens[j].trim());
			}
		}
	}
	return tokens;
}

function setOrganisationTheme() {

	if(globals.gSetAsTheme && globals.gOrgId > 0) {

		var mainLogoSrc = getFromLocalStorage("main_logo");
		var logo = "/media/organisation/" + globals.gOrgId + '/settings/mainLogo';
		if(mainLogoSrc !== logo) {
			setInLocalStorage('main_logo', logo);
			$('.main_logo').attr("src", "/media/organisation/" + globals.gOrgId + '/settings/mainLogo');
		}

		// navbar color
		var navbarColor = getFromLocalStorage("navbar_color");
		if(navbarColor !== globals.gNavbarColor) {
			setInLocalStorage('navbar_color', globals.gNavbarColor);
		}
	} else {
		// remove styles
		var navbarColorElement = document. getElementById("navbar_color");
		if(navbarColorElement) {
			navbarColorElement.parentNode.removeChild(navbarColorElement);
		}
		setInLocalStorage('navbar_color', undefined);
		setInLocalStorage('main_logo', undefined);

		// Set the default logo
		setCustomMainLogo();
	}
}

/*
 * Surround get / set from local storage in case user has disabled local sorage reading in browser settings
 */
function getFromLocalStorage(key) {
	var value;
	try {
		value = localStorage.getItem(key);
	} catch (e) {

	}
	return value;
}

function setInLocalStorage(key, value) {
	try {
		localStorage.setItem(key, value);
	} catch(e) {

	}
}

function populateTaskGroupList() {
	if (typeof globals.gCurrentProject !== "undefined" && globals.gCurrentProject != -1) {
		addHourglass();
		$.ajax({
			url: "/surveyKPI/tasks/taskgroups/" + globals.gCurrentProject,
			cache: false,
			dataType: 'json',
			success: function (taskgroups) {
				removeHourglass();

				var h = [],
					idx = -1,
					i,
					grp,
					firstTg,
					hasCurrentTg = false;

				gTaskGroups = taskgroups;   // Keep the task group list

				if (typeof taskgroups != "undefined" && taskgroups.length > 0) {

					for (i = 0; i < taskgroups.length; i++) {
						grp = taskgroups[i];
						h[++idx] = '<option value="';
						h[++idx] = i;
						h[++idx] = '">';
						h[++idx] = grp.name;
						h[++idx] = '</option>';
					}
				}
				$('.task_group_select').html(h.join(''));
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
	}
}

/*
 * Show a loaded file as an image
 * From https://codepen.io/adamrifai/pen/YXdEwz
 */
function displayAsImage(file, img) {

	var imgURL = URL.createObjectURL(file);
	img.onload = function() {
		URL.revokeObjectURL(imgURL);
	};

	img.src = imgURL;
}

/*
 * If debug=yes is passed as a parameter then enable debuging statement
 */
function enableDebugging() {

	if(location.search.indexOf("debug=yes") >= 0) {
		$(document).on('click', function(e) { console.log(e.target) });
	}

}

/*
 * ----------------------------------------------------
 * Common task fuctions shared between task managmeent page and console
 */
function setupAssignType(user_id, role_id, emails) {
	$('.assign_group').hide();
	$('.assign_type').removeClass('active');
	if(user_id != 0) {
		$('.user_type_checkbox').addClass('active');
		$('.assign_user').show();
	} else  if(role_id != 0) {
		$('.role_type_checkbox').addClass('active');
		$('.assign_role').show();
	} else if(typeof emails !== "undefined" && emails.trim().length > 0) {
		$('.email_type_checkbox').addClass('active');
		$('.assign_email').show();
	} else {        // Default to user
		$('.user_type_checkbox').addClass('active');
		$('.assign_user').show();
	}
}

// Convert a location name into a location index
function getLocationIndex(name, tags) {
	var idx = -1,
		i;

	if(tags) {
		for(i = 0; i < tags.length; i++) {
			if(tags[i].name == name) {
				idx = i;
				break;
			}

		}
	}
	return idx;

}

function saveTask(isConsole, currentTaskFeature, saveType, updateId, callback) {
	var url = "/api/v1/tasks",
		taskFeature = {
			properties: {}
		},
		fromDate,
		toDate,
		MIN_SHOW_RANGE = 10;

	taskFeature = $.extend(true, {}, currentTaskFeature);
	/*
	 * Set the properties of the taskFeature from the dialog
	 */
	taskFeature.properties.pid = globals.gCurrentProject;
	taskFeature.properties.tg_id = globals.gCurrentTaskGroup;

	if (!taskFeature.properties.id || taskFeature.properties.id == "") {
		taskFeature.properties["id"] = 0;
	}
	taskFeature.properties.name = $('#tp_name').val();		// task name
	if(isConsole) {
		taskFeature.properties.survey_ident = $('#tp_form_name').val();	// form id
	} else {
		// old fashioned
		taskFeature.properties.form_id = $('#tp_form_name').val();	// form id
	}

	taskFeature.properties.assign_type = $("button.assign_type.active", "#task_properties").attr("id");
	if(taskFeature.properties.assign_type == 'tp_user_type') {
		taskFeature.properties.assignee = $('#tp_user').val();
	} else if(taskFeature.properties.assign_type == 'tp_email_type') {
		taskFeature.properties.assignee = 0;
		taskFeature.properties.emails = $('#tp_assign_emails').val();
		if(!validateEmails(taskFeature.properties.emails)) {
			alert(localise.set["msg_inv_email"]);
			return false;
		}
	}

	if(isConsole) {
		taskFeature.properties.update_id = updateId;
		taskFeature.properties.initial_data_source = 'survey';
	}

	taskFeature.properties.repeat = $('#tp_repeat').prop('checked');

	fromDate = $('#tp_from').data("DateTimePicker").date();
	toDate = $('#tp_to').data("DateTimePicker").date();
	if (fromDate) {
		taskFeature.properties.from = utcTime(fromDate.format("YYYY-MM-DD HH:mm:ss"));
	}
	if (toDate) {
		taskFeature.properties.to = utcTime(toDate.format("YYYY-MM-DD HH:mm:ss"));
	}
	taskFeature.properties.location_trigger = $('#nfc_uid').val();
	taskFeature.properties.guidance = $('#tp_guidance').val();
	taskFeature.properties.show_dist = $('#tp_show_dist').val();

	/*
	 * Save location group and location name
	 */
	var locationIdx = $('#location_select').val();
	if(saveType == "nl") {
		taskFeature.properties.location_group = $('#locationGroupSave').val();
		taskFeature.properties.location_name = $('#locationSave').val();
	} else if(saveType == "ul" && locationIdx != "-1") {
		taskFeature.properties.location_group = $('.location_group_list_sel').text();
		taskFeature.properties.location_name = gTags[locationIdx].name;
	} else {
		taskFeature.properties.location_group = undefined;
		taskFeature.properties.location_name = undefined;
	}
	taskFeature.properties.save_type = saveType;

	/*
	 * Convert the geoJson geometry into longitude and latitude for update
	 */
	if (currentTaskFeature.geometry) {
		if (currentTaskFeature.geometry.coordinates && currentTaskFeature.geometry.coordinates.length > 1) {
			//taskFeature.properties.location = "POINT(" + gCurrentTaskFeature.geometry.coordinates.join(" ") + ")";  // deprecate
			taskFeature.properties.lon = currentTaskFeature.geometry.coordinates[0];
			taskFeature.properties.lat = currentTaskFeature.geometry.coordinates[1];

		} else {
			//taskFeature.properties.location = "POINT(0 0)"; // deprecate
			taskFeature.properties.lon = 0;
			taskFeature.properties.lat = 0;
		}
	}

	// TODO task update details (updating existing record)

	// Validations
	if(typeof taskFeature.properties.show_dist === "undefined") {
		taskFeature.properties.show_dist = 0;
	} else {
		taskFeature.properties.show_dist = +taskFeature.properties.show_dist;
	}
	if (taskFeature.properties.show_dist && taskFeature.properties.show_dist < MIN_SHOW_RANGE) {
		alert(localise.set["msg_val_show_dist"]);
		$('#tp_show_dist').focus();
		return;
	}


	var tpString = JSON.stringify(taskFeature.properties);

	addHourglass();
	$.ajax({
		type: "POST",
		dataType: 'text',
		cache: false,
		contentType: "application/json",
		url: url,
		data: {task: tpString},
		success: function (data, status) {
			removeHourglass();
			$('#task_properties').modal("hide");
			callback();
		},
		error: function (xhr, textStatus, err) {

			removeHourglass();
			alert(localise.set["msg_err_upd"] + xhr.responseText);

		}
	});
}

/*
 * Get the list of users from the server so they can be assigned to tasks
 */
function getTaskUsers(projectId) {
	var $users = $('.users_select,#users_filter'),
		i, user,
		h = [],
		idx = -1;

	$users.empty();
	$('#users_filter').append('<option value="0">' + localise.set["t_au"] + '</options>');
	//$('#users_filter').append('<option value="-1">' + localise.set["t_u"] + '</options>');

	$('#users_select_new_task, #users_task_group, #users_select_user, #tp_user')
		.append('<option value="-1">' + localise.set["t_u"] + '</options>');

	$('#users_task_group').append('<option value="-2">' + localise.set["t_ad"] + '</options>');
	$.ajax({
		url: "/surveyKPI/userList",
		cache: false,
		success: function (data) {

			for (i = 0; i < data.length; i++) {
				user = data[i];
				// Check that this user has access to the project

				if (!projectId || userHasAccessToProject(user, projectId)) {
					h[++idx] = '<option value="';
					h[++idx] = user.id;
					h[++idx] = '">';
					h[++idx] = user.name;
					h[++idx] = '</option>';
				}
			}
			$users.append(h.join(''));
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

function userHasAccessToProject(user, projectId) {
	var i;
	if(user.projects) {
		for (i = 0; i < user.projects.length; i++) {
			if (user.projects[i].id == projectId) {
				return true;
			}
		}
	}
	return false;
}

function setupTaskDialog() {
	$('#tp_email_type, #assign_email_type').click(function() {
		$('.assign_type').removeClass('active');
		$(this).addClass('active');

		$('.assign_user, .assign_role,.assign_data').hide();
		$('.assign_email').show();
		$('#assign_data').prop('placeholder', localise.set['n_eqc']);
		$('.assign_data').show();
	});
	$('#tp_user_type, #assign_user_type').click(function() {
		$('.assign_type').removeClass('active');
		$(this).addClass('active');

		$('.assign_user').show();
		$('.assign_role,.assign_email').hide();
		if($('#users_task_group').val() == -2) {
			$('#assign_data').prop('placeholder', "");
			$('.assign_data').show();
		} else {
			$('.assign_data').hide();
		}
	});
	$('#tp_role_type, #assign_role_type').click(function() {
		$('.assign_type').removeClass('active');
		$(this).addClass('active');

		$('.assign_user, .assign_email').hide();
		$('.assign_role').show();
		if($('#roles_task_group').val() == -2) {
			$('#assign_data').prop('placeholder', "");
			$('.assign_data').show();
		} else {
			$('.assign_data').hide();
		}
	});

	$('#tp_from').datetimepicker({
		useCurrent: false,
		locale: gUserLocale || 'en'
	});

	$('#tp_to').datetimepicker({
		useCurrent: false,
		locale: gUserLocale || 'en'
	});

	$('#tp_from').on("dp.change", function () {

		var startDateLocal = $(this).data("DateTimePicker").date(),
			endDateLocal = $('#tp_to').data("DateTimePicker").date(),
			originalStart = gCurrentTaskFeature.properties.from,
			originalEnd = gCurrentTaskFeature.properties.to,
			newEndDate,
			duration;

		if (startDateLocal) {

			gCurrentTaskFeature.properties.from = utcTime(startDateLocal.format("YYYY-MM-DD HH:mm:ss"));

			if (!endDateLocal) {
				newEndDate = startDateLocal.add(1, 'hours');
			} else {
				if (originalEnd && originalStart) {
					duration = moment(originalEnd, "YYYY-MM-DD HH:mm:ss").diff(moment(originalStart, "YYYY-MM-DD HH:mm:ss"), 'hours');
				} else {
					duration = 1;
				}
				newEndDate = startDateLocal.add(duration, 'hours');
			}
		} else {
			if (!endDate) {
				return;
			} else {
				// Clear the end date
			}
		}

		$('#tp_to').data("DateTimePicker").date(newEndDate);

	});

	$('#tp_to').on("dp.change", function () {

		var endDateLocal = $('#tp_to').data("DateTimePicker").date();

		gCurrentTaskFeature.properties.to = utcTime(endDateLocal.format("YYYY-MM-DD HH:mm:ss"));

	});

}

function getStatusClass(status) {

	var statusClass = "";

	if (status === "error" || status === "new" || status === "unsent" || status === "unsubscribed"
		|| status === "blocked" || status === "rejected") {
		statusClass = "bg-danger";
	} else if (status === "submitted" || status === "success") {
		statusClass = "bg-success";
	} else if (status === "accepted") {
		statusClass = "bg-warning";
	} else {
		statusClass = "bg-success";
	}
	return statusClass;
}

/*
 *------------------------------------------------------------------
 * Common notification functions shared between console and notifications
 */
function edit_notification(idx) {

	var notification;

	document.getElementById("notification_edit_form").reset();
	setTargetDependencies("email");
	setTriggerDependencies("submission");
	setAttachDependencies();

	if(typeof idx !== "undefined") {
		notification = gNotifications[idx];

		title = localise.set["msg_edit_notification"];
		$('#trigger').val(notification.trigger);
		$('#target').val(notification.target);
		$('#name').val(notification.name);
		setTargetDependencies(notification.target);
		setTriggerDependencies(notification.trigger)
		setAttachDependencies(notification.notifyDetails.attach);

		$('#survey').val(notification.s_id);
		$('#not_filter').val(notification.filter);

		// reminder settings
		$('#task_group').val(getTaskGroupIndex(notification.tgId));
		if((notification.period)) {
			var periodArray = notification.period.split(" ");
			if(periodArray.length > 1) {
				$('#r_period').val(periodArray[0]);
				$('#period_list_sel').val(periodArray[1]);
			}
		}

		if(notification.notifyDetails) {
			if(notification.notifyDetails.emailQuestionName || notification.notifyDetails.emailMeta) {
				surveyChanged(notification.notifyDetails.emailQuestionName, notification.notifyDetails.emailMeta);
			}

			if(notification.target == "email") {
				if(notification.notifyDetails.emails) {
					$('#notify_emails').val(notification.notifyDetails.emails.join(","));
				}
				$('#email_subject').val(notification.notifyDetails.subject);
				$('#email_content').val(notification.notifyDetails.content);
				$('#email_attach').val(notification.notifyDetails.attach);
				$('#include_references').prop('checked', notification.notifyDetails.include_references);
				$('#launched_only').prop('checked', notification.notifyDetails.launched_only);
			} else if(notification.target == "sms") {
				if(notification.notifyDetails.emails) {
					$('#notify_sms').val(notification.notifyDetails.emails.join(","));
				}
				$('#sms_content').val(notification.notifyDetails.content);
				$('#sms_attach').val(notification.notifyDetails.attach);
				$('#sms_sender_id').val(notification.notifyDetails.subject);
			}
		}
		$('#fwd_rem_survey_id').val(notification.remote_s_ident);
		$('#fwd_rem_survey_nm').val(notification.remote_s_name);
		$('#fwd_user').val(notification.remote_user);
		// Password not returned from server - leave blank

		$('#fwd_host').val(notification.remote_host);
		if(notification.enabled) {
			$('#nt_enabled').prop('checked',true);
		} else {
			$('#nt_enabled').prop('checked', false);
		}

		window.gUpdateFwdPassword = false;
		window.gSelectedNotification = notification.id;
	} else {

		$('#fwd_host').val(window.gRemote_host);	// Set the values to the ones last used
		$('#fwd_user').val(window.gRemote_user);

		// Reminders
		$('#r_period').val(1);
		$('#period_list_sel').val('days');
		$('#nt_enabled').prop('checked',true);
		window.gUpdateFwdPassword = true;
		window.gSelectedNotification = -1;
	}

}

function setTargetDependencies(target) {
	if(target === "email") {
		$('.forward_options, .sms_options').hide();
		$('.email_options').show();
	} else if(target === "forward") {
		$('.email_options, .sms_options').hide();
		$('.forward_options').show();
	} else if(target === "sms") {
		$('.email_options, .forward_options').hide();
		$('.sms_options').show();
	}
}

function setTriggerDependencies(trigger) {
	if(trigger === "submission") {
		$('.task_reminder_options').hide();
		$('.submission_options').show();
	} else if(trigger === "task_reminder") {
		$('.submission_options').hide();
		$('.task_reminder_options').show();
		$('#target').val('email');
		setTargetDependencies('email');
	}
}

function setAttachDependencies(attach) {
	if(attach === "pdf" || attach === "pdf_landscape") {
		$('.pdf_options').show();
	} else  {
		$('.pdf_options').hide();
	}
}

/*
	 * Update the notification list
	 */
function updateNotificationTypes(data) {

	var $selector=$('#target'),
		i,
		h = [],
		idx = -1;

	for(i = 0; i < data.length; i++) {

		h[++idx] = '<option value="';
		h[++idx] = data[i];
		if(data[i] === 'forward') {
			h[++idx] = '" class="submission_options';
		}
		h[++idx] = '">';
		h[++idx] = localise.set["c_" + data[i]];
		h[++idx] = '</option>';
	}

	$selector.empty().append(h.join(''));

}

/*
 * Load the existing notifications from the server
 */
function getNotificationTypes() {

	addHourglass();
	$.ajax({
		url: '/surveyKPI/notifications/types',
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			window.gNotificationTypes = data;
			if(data) {
				updateNotificationTypes(data);
			}
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
				return;  // Not an error
			} else {
				console.log("Error: Failed to get list of notification types: " + err);
			}
		}
	});
}

function setupNotificationDialog() {
	// Set change function trigger
	$('#trigger').change(function() {
		setTriggerDependencies($(this).val());
	});
	setTriggerDependencies("submission");

	// Set change function target
	$('#target').change(function() {
		setTargetDependencies($(this).val());
	});
	setTargetDependencies("email");

	// Set change function attach
	$('#email_attach').change(function() {
		setAttachDependencies($(this).val());
	});

	// Set focus on notification name when edit notification is opened
	$('#addNotificationPopup').on('shown.bs.modal', function () {
		$('#name').focus();
	});

	/*
	 * Functions for forwarding
	 */
	$('#fwd_host').change(function(){
		var host = $(this).val();
		if(host.length === 0) {
			return false;
		} else if(host.substr(0, 4) !== "http") {
			alert(localise.set["msg_val_prot"]);
			return false;
		}
	});

	$('#fwd_password').change(function(){
		window.gUpdateFwdPassword = true;
	});

	$('#fwd_upd_rem_survey').click(function(){
		getRemoteSurveys();
	});

	$('#fwd_rem_survey').change(function(){
		remoteSurveyChanged();
	});
}

/*
 * Process a save notification when the target is "email"
 */
function saveEmail() {

	var notification = {};
	var emails = $('#notify_emails').val();
	var emailQuestionName = $('#email_question').val();
	var emailMetaItem = $('#email_meta').val();
	var emailArray;
	var i;

	// validate
	// Must specifify an email
	notification.error = false;
	if((!emails || emails.trim().length == 0) && (!emailQuestionName || emailQuestionName == "-1")
		&& (!emailMetaItem || emailMetaItem == "-1")) {
		notification.error = true;
		notification.errorMsg = localise.set["msg_inv_email"];
	}

	// Text email must be valid email addresses
	if(emails && emails.trim().length > 0) {
		emailArray = emails.split(",");
		for (i = 0; i < emailArray.length; i++) {
			if (!validateEmails(emailArray[i])) {
				notification.error = true;
				notification.errorMsg = localise.set["msg_inv_email"];
				break;
			}
		}
	}

	if(!notification.error) {
		notification.target = "email";
		notification.notifyDetails = {};
		notification.notifyDetails.emails = emailArray;
		notification.notifyDetails.emailQuestionName = emailQuestionName;
		notification.notifyDetails.emailMeta = emailMetaItem;
		notification.notifyDetails.subject = $('#email_subject').val();
		notification.notifyDetails.content = $('#email_content').val();
		notification.notifyDetails.attach = $('#email_attach').val();
		notification.notifyDetails.include_references = $('#include_references').prop('checked');
		notification.notifyDetails.launched_only = $('#launched_only').prop('checked');
	}

	return notification;
}

/*
 * Process a save notification when the target is "sms"
 */
function saveSMS() {

	var notification = {};

	notification.target = "sms";
	notification.notifyDetails = {};
	notification.notifyDetails.emails = $('#notify_sms').val().split(",");
	notification.notifyDetails.emailQuestionName = $('#sms_question').val();
	notification.notifyDetails.subject = $('#sms_sender_id').val();
	notification.notifyDetails.content = $('#sms_content').val();
	notification.notifyDetails.attach = $('#sms_attach').val();

	return notification;
}

/*
 * Process a save notification when the target is "document"
 */
function saveDocument() {

	var notification = {};

	notification.target = "document";
	notification.notifyDetails = {};


	return notification;
}

/*
 * Process a save notification when the target is "forward"
 */
function saveForward() {

	var error = false,
		remote_s_ident,
		host,
		$dialog,
		rem_survey_id,
		rem_survey_nm,
		notification = {};

	host = $('#fwd_host').val();
	remote_s_ident = $('#fwd_rem_survey :selected').val();
	remote_s_nm = $('#fwd_rem_survey :selected').text();

	// Remove any trailing slashes from the host
	if(host.substr(-1) == '/') {
		host = host.substr(0, host.length - 1);
	}

	if(typeof remote_s_ident === "undefined" || remote_s_ident.length == 0) {
		error = true;
		alert(localise.set["msg_val_rf"]);

	} else if(host.substr(0, 4) !== "http") {
		error = true;
		alert(localise.set["msg_val_prot"]);
		$('#fwd_host').focus();
	}

	if(!error) {

		notification.target = "forward";
		notification.remote_s_ident = remote_s_ident;
		notification.remote_s_name = remote_s_nm;
		notification.remote_user = $('#fwd_user').val();
		notification.remote_password = $('#fwd_password').val();
		notification.remote_host = host;
		notification.update_password = window.gUpdateFwdPassword;

		// Save the values temporarily entered by the user
		window.gRemote_host = host;
		window.gRemote_user = $('#fwd_user').val();

	} else {
		notification.error = true;
	}

	return notification;
}