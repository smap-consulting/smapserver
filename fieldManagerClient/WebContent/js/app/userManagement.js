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

define(['jquery','localise', 'common', 'globals', 
        'bootbox', 
        'moment',
        'datetimepicker'], function($, lang, common, globals, bootbox, moment) {
	
var gUsers,
	gGroups,
	gOrganisationList,
	gControlCount,			// Number of users that have been set - used to enable / disable control buttons
	gControlProjectCount,	// Number of projects that have been set - used to enable / disable control buttons
	gControlOrganisationCount,
	gCurrentProjectIndex,	// Set while editing a projects details
	gCurrentRoleIndex,	// Set while editing a user role details
	gCurrentOrganisationIndex,
	gCurrentUserIndex,		// Set while editing a users details
	gOrgId;

$(document).ready(function() {

	localise.setlang();		// Localise HTML
	
	getUsers();
	getGroups();
	getProjects();
	getLoggedInUser(userKnown, false, false, getOrganisations);
	
	getAvailableTimeZones($('#o_tz'), showTimeZones);
	
	// Add change event on group and project filter
	$('#group_name, #project_name').change(function() {
		var group=$('#group_name').val(), 
			project=$('#project_name').val();
		
 	 	updateUserTable(group, project);
 	 });
	
	// Set button style and function
	$('#create_user').click(function () {
		openUserDialog(false, -1);
	});
	$('#delete_user').click(function () {
		deleteUsers();
	});

	$('#create_project').click(function () {
		openProjectDialog(false, -1);
	});
	$('#delete_project').click(function () {
		deleteProjects();
	});
	
	$('#create_user_role').click(function () {
		openRoleDialog(false, -1);
	});
	$('#delete_role').click(function () {
		deleteRoles();
	});
	
	$('#create_organisation').click(function () {
		openOrganisationDialog(false, -1);
	});
	$('#delete_organisation').click(function () {
		deleteOrganisations();
	});
	$('.move_to_org').click(function () {
		$('#move_to_organisation_popup').modal("show");
	});
	
	// Set up the tabs
    $('#usersTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		 
		$('#rolesPanel').hide();
		$('#projectPanel').hide();
		$('#organisationPanel').hide();
		$('#serverPanel').hide();
		$('#userPanel').show();
    })
    $('#projectsTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		  	  
		$('#projectPanel').show();
		$('#organisationPanel').hide();
		$('#serverPanel').hide();
		$('#userPanel').hide();
		$('#rolesPanel').hide();
    })
    $('#organisationTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		  	  
		$('#projectPanel').hide();
		$('#organisationPanel').show();
		$('#serverPanel').hide();
		$('#userPanel').hide();
		$('#rolesPanel').hide();
    });
    $('#serverTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		  	  
		$('#projectPanel').hide();
		$('#organisationPanel').hide();
		$('#serverPanel').show();
		$('#userPanel').hide();
		$('#rolesPanel').hide();
    });
    $('#roleTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		  	  
		$('#projectPanel').hide();
		$('#organisationPanel').hide();
		$('#serverPanel').hide();
		$('#userPanel').hide();
		$('#rolesPanel').show();
    })
    
    // Style the upload buttons
    $('.file-inputs').bootstrapFileInput();
    
    // Copy user ident to email if it is a valid email
    $('#user_ident').blur(function(){
    	var validEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+.[A-Z]{2,4}/igm,
    		ident = $('#user_ident').val();
    	
    	if(validEmail.test(ident)) {
    		$('#user_email').val(ident);
    	}
    	
    });
    
    // Function to save a users details
    $('#userDetailsSave').click(function(e) {
		var userList = [],
			user = {},
			error = false,
			securityManagerChecked,
			validIdent = new RegExp('^[a-z0-9_]+$'),
			validEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+.[A-Z]{2,4}/igm,
			validEmail2 = /[A-Z0-9._%+-]+@[A-Z0-9.-]+.[A-Z]{2,4}/igm,
			send_email = $('input[name=send_email]:checked', '#send_email_fields').val();
	
		// Ignore click if button disabled
		console.log($('#userDetailsSave').prop("disabled"));
		if($('#userDetailsSave').prop("disabled")) {
			return;
		}
		
		// Disable the save button to prevent double clicking
		$('#userDetailsSave').prop("disabled", true);
		
		if(gCurrentUserIndex === -1) {
			user.id = -1;
		} else {
			user.id = gUsers[gCurrentUserIndex].id;
		}
		user.ident = $('#user_ident').val();
		user.name = $('#user_name').val();
		user.email = $('#user_email').val();
		if(gCurrentUserIndex === -1 && send_email == "send_email") {
			user.sendEmail = true;
		} else {
			user.sendEmail = false;
		}
		
		// Validations
		if((!validIdent.test(user.ident) && !validEmail.test(user.ident)) || user.ident.length == 0) {
			alert("User ident must be specified and either be an email address or " +
					"only include lowercase characters from a-z and numbers.  No spaces.");
			$('#user_ident').focus();
			$('#userDetailsSave').prop("disabled", false);
			return false;
		}
		if(user.ident.indexOf(' ') !== -1) {
			alert("Spaces are not allowed in the user ident");
			$('#user_ident').focus();
			$('#userDetailsSave').prop("disabled", false);
			return false;
		}
		if(user.email.length > 0) {
			if(!validEmail2.test(user.email)) {
				error = true;
				alert("Email is not valid");
				$('#user_email').focus();
				$('#userDetailsSave').prop("disabled", false);
				return false;
			}
		}
		
		// For a new user, email must be specified if the send email check box is set
		if(user.sendEmail && user.email.length === 0) {
			error = true;
			alert("If sending an email to the user then email address must be specified");
			$('#user_email').focus();
			$('#userDetailsSave').prop("disabled", false);
			return false;
		}
	
		if($('#user_password').is(':visible')) {
			user.password = $('#user_password').val();
			if(user.password.length < 2) {
				error = true;
				user.password = undefined;
				alert("Passwords, if specified, must be longer than 1 character");
				$('#user_password').focus();
				$('#userDetailsSave').prop("disabled", false);
				return false;
			}
			if($('#user_password_confirm').val() !== user.password) {
				error = true;
				user.password = undefined;
				alert("Passwords do not match");
				$('#user_password').focus();
				$('#userDetailsSave').prop("disabled", false);
				return false;
			}
		} else {
			user.password = undefined;
		}
		
		user.groups = [];
		user.projects = [];
		user.roles= [];
		securityManagerChecked = false;
		$('#user_groups').find('input:checked').each(function(index) {
			var val = $(this).val();
			user.groups[index] = {id: val};
			if(val == 6) {		// Security Management
				securityManagerChecked = true;
			}
		});
		if(globals.gIsOrgAdministrator && globals.gLoggedInUser.ident == user.ident) {
			// Update security manager setting straight away if the user is updating their own settings
			// as this affects ongoing use of the user management page
			globals.gIsSecurityAdministrator = securityManagerChecked;
		}
		$('#user_projects').find('input:checked').each(function(index) {
			user.projects[index] = {id: $(this).val()};
		});
		$('#user_roles').find('input:checked').each(function(index) {
			user.roles[index] = {id: $(this).val()};
		});
		userList[0] = user;
		writeUserDetails(userList, $('#create_user_popup'));	// Save the user details to the database
 		
    });
  
    // Function to save a users details
    $('#saveServer').click(function(e) {
    	writeServerDetails();
    });
    
	/* 
	 * Set focus to first element on opening modals
	 */
	$('.modal').on('shown.bs.modal', function() {
		$(this).find('input[type=text],textarea,select').filter(':visible:first').focus();
	});
    
	/*
	 * Add date time picker to usage date
	 */
	moment.locale();
	$('#usageDate').datetimepicker({
		useCurrent: false,
		format: "MM/YYYY",
		viewMode: "months",
		locale: gUserLocale || 'en'
	}).data("DateTimePicker").date(moment());

	
	isBusinessServer();
	
    /*
     * Save a project details
     */
    $('#projectSave').click(function(){
		var projectList = [],
			project = {},
			error = false;
	
		if(gCurrentProjectIndex === -1) {
			project.id = -1;
		} else {
			project.id = globals.gProjectList[gCurrentProjectIndex].id;
		}
	
		project.name = $('#p_name').val();
		project.desc = $('#p_desc').val();
		project.tasks_only = $('#p_tasks_only').is(':checked');
	  		
		projectList[0] = project;
		var projectString = JSON.stringify(projectList);
	
		addHourglass();
		$.ajax({
			  type: "POST",
			  contentType: "application/json",
			  cache: false,
			  url: "/surveyKPI/projectList",
			  data: { projects: projectString },
			  success: function(data, status) {
				  removeHourglass();
				  getUsers();
				  getProjects();
				  $('#create_project_popup').modal("hide");
			  },
			  error: function(xhr, textStatus, err) {
				  removeHourglass();
	
				  if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
				  } else {
					  var msg = xhr.responseText;
					  alert(localise.set["msg_err_upd"] + msg);
				  }
			  }
		});   		

    });
    
    /*
     * Save a role details
     */
    $('#roleSave').click(function(){
		var roleList = [],
			role = {},
			error = false;
	
		if(gCurrentRoleIndex === -1) {
			role.id = -1;
		} else {
			role.id = globals.gRoleList[gCurrentRoleIndex].id;
		}
	
		role.name = $('#ur_name').val();
		role.desc = $('#ur_desc').val();
	  		
		if(role.name.indexOf(',') >= 0) {
			alert(localise.set["msg_err_nc"]);
			return -1;
		}
		
		roleList[0] = role;
		var roleString = JSON.stringify(roleList);
	
		addHourglass();
		$.ajax({
			  type: "POST",
			  contentType: "application/json",
			  cache: false,
			  url: "/surveyKPI/role/roles",
			  data: { roles: roleString },
			  success: function(data, status) {
				  removeHourglass();
				  getRoles(updateRoleTable);
				  $('#create_role_popup').modal("hide");
			  },
			  error: function(xhr, textStatus, err) {
				  removeHourglass();
	
				  if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
				  } else {
					  var msg = xhr.responseText;
					  alert(localise.set["msg_err_upd"] + msg);
				  }
			  }
		});   		

    });
    
    
    /*
     * Save the organisation details
     */
    $('#organisationSave').click(function() {
    	var organisationList = [],
			organisation = {},
			error = false,
			options=[],
			i,
			validEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+.[A-Z]{2,4}/igm;
	
		if(gCurrentOrganisationIndex === -1) {
			organisation.id = -1;
		} else {
			organisation.id = gOrganisationList[gCurrentOrganisationIndex].id;
		}
	
		organisation.name = $('#o_name').val();
		
		organisation.company_name = $('#o_company_name').val();
		organisation.company_address = $('#o_company_address').val();
		organisation.company_phone = $('#o_company_phone').val();
		organisation.company_email = $('#o_company_email').val();
		
		organisation.admin_email = $('#o_admin_email').val();
		organisation.smtp_host = $('#o_smtp_host').val();
		organisation.email_domain = $('#o_email_domain').val();
		organisation.email_user = $('#o_email_user').val();
		organisation.email_password = $('#o_email_password').val();
		organisation.email_port = parseInt($('#o_email_port').val());
		organisation.default_email_content = $('#o_default_email_content').val();
		organisation.locale = $('#o_language').val();
		organisation.timeZone = $('#o_tz').val();
	
		if(typeof organisation.email_port !== "number") {
			organisation.email_port = 0;
		}
		// Validate
		if(organisation.name.length === 0) {
			alert("Name must be specified");
			$('#o_name').focus();
			return false;
		}
		if(organisation.admin_email.length > 0) {
    		if(!validEmail.test(organisation.admin_email)) {
				error = true;
				alert("Email is not valid");
				$('#o_admin_email').focus();
				return false;
			}
		}
		if(organisation.email_user.indexOf('@') > 0) {
			error = true;
			alert("Email user name should not include the email domain.  So for an email address of example@org.com the user name would be 'example'");
			$('#o_email_user').focus();
			return false;
		}
		
		options = $(".puboption:checked").map(function(){
	        	return $(this).val();
	    	}).toArray();
		
		console.log("options");
		console.log(options);
		for(i = 0; i < options.length; i++) {
			if(options[i] === "email") {
				organisation.allow_email = true;
			} else if(options[i] === "facebook") {
				organisation.allow_facebook = true;
			} else if(options[i] === "twitter") {
				organisation.allow_twitter = true;
			} else if(options[i] === "can_edit") {
				organisation.can_edit = true;
			} else if(options[i] === "ft_delete_submitted") {
				organisation.ft_delete_submitted = true;
			} else if(options[i] === "ft_send_trail") {
				organisation.ft_send_trail = true;
			} else if(options[i] === "ft_sync_incomplete") {
				organisation.ft_sync_incomplete = true;
			}
		}
		organisationList[0] = organisation;	
		var organisationString = JSON.stringify(organisationList);
    	
		$('#orgSettings').val(organisationString);
    	var f = document.forms.namedItem("organisationsave");
    	var formData = new FormData(f);
    	
		addHourglass();
		$.ajax({
              type: 'POST',
              data: formData,
              cache: false,
              contentType: false,
              processData:false,
			  url: "/surveyKPI/organisationList",
			  success: function(data, status) {
				  removeHourglass();
				  getOrganisations();
				  $('#create_organisation_popup').modal("hide");
			  }, error: function(xhr, textStatus, err) {	
				  document.forms.namedItem("organisationsave").reset();
				  removeHourglass();
				  if(xhr.readyState == 0 || xhr.status == 0) {
					  return;  // Not an error
				  } else {
					  var msg = err;
					  if(err.indexOf("Conflict") >= 0) {
    						msg = "Duplicate organisation name";
					  }
					  alert(localise.set["msg_err_upd"] + msg);
				  }
			  }
		});
	
    });
    
    /*
     * Get a usage report
     */
    $('#usageGet').click(function() {
    	var usageMsec = $('#usageDate').data("DateTimePicker").date(),
    		d = new Date(usageMsec),
    		month = d.getMonth() + 1,
    		year = d.getFullYear(),
    		oId = gOrganisationList[gCurrentOrganisationIndex].id,
    		period,
    		month_names = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
    	
    	// Calculate the period as text so the web service doesn't have to (can also use language translations here)  	
    	period = month_names[month -1] + " " + year;
    	
    	$('#get_usage_popup').modal("hide");
    	getUsage(oId, month, year, period);
    });
    
    /*
     * Move a user to a new organisation
     */
	$('#organisationMove').click(function(){
		 var users = [],
			projects =[],
			decision = false,
			h = [],
			i = -1,
			idx,
			orgId,
			orgName,
			hasUsers = false,
			hasProjects = false,
			keepProjects = false;
 	
		 h[++i] = "Are you sure you want to move these ";
		 $('#user_table').find('input:checked').each(function(index) {
			 if(!hasUsers) {
				 h[++i] = "users ("; 
				 hasUsers = true;
			 } else {
				 h[++i] = ",";
			 }
			 idx = $(this).val();
			 users[index] = {id: gUsers[idx].id};
			 h[++i] = gUsers[idx].name;
		 });
 	
		 $('#project_table').find('input:checked').each(function(index) {
			 if(hasUsers && !hasProjects) {
				 h[++i] = ") and these projects (";
				 hasProjects = true;
			 } else if(!hasProjects){
				 h[++i] = "projects (";
				 hasProjects = true;
			 } else {
				 h[++i] = ",";
			 }
			 idx = $(this).val();
			 projects[index] = {id: globals.gProjectList[idx].id};
 		
			 h[++i] = globals.gProjectList[idx].name;
		 });
 	
		 orgId = $('#target_organisation').val();
		 orgName = $('#target_organisation :selected').text();
 	
		 h[++i] = ") to " + orgName + "?";
		 bootbox.confirm(h.join(''), function(result){
			 if(result) {
				 for(i = 0; i < users.length; i++) {
					 if(users[i].id === globals.gLoggedInUser.id) {
						 users[i].keepProjects = true;
			 		 } else {
			 			 users[i].keepProjects = false;
			 		 }			
			 	 }
			 	 moveToOrganisations(orgId, users, projects);	
			 }
		 });

	 });
	 

	 // Initialise the reset password checkbox
	 $('#reset_password').click(function () {
		 if($(this).is(':checked')) {
			 $('#password_fields').show();
		 } else {
			 $('#password_fields').hide();
		 }
	 });

	enableUserProfileBS();	// Allow user to reset their own profile
	
});


function userKnown() {
	if(globals.gIsOrgAdministrator || globals.gIsSecurityAdministrator) {
		getRoles(updateRoleTable);
	}
}

/*
 * Get the list of available projects from the server
 */
function getProjects() {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/projectList",
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			globals.gProjectList = data;
			updateProjectList(true, 0);
			updateProjectTable();
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
 * Get the list of available organisations from the server
 * Get the server details
 */
function getOrganisations() {

	// Show the current organisation
	$('#org_alert').text(localise.set["c_org"] + ": " + globals.gLoggedInUser.organisation_name);
	addHourglass();
	$.ajax({
		url: "/surveyKPI/organisationList",
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			gOrganisationList = data;
			updateOrganisationTable();
			updateOrganisationList();
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get list of organisations: " + err);
			}
		}
	});	
	
	
	// Get the server details
	addHourglass();
	$.ajax({
		url: "/surveyKPI/server",
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			updateServerData(data);
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get list of organisations: " + err);
			}
		}
	});	
}

/*
 * Populate the server tab
 */
function updateServerData(data) {
	$('#mapbox_key').val(data.mapbox_default);
	$('#google_key').val(data.google_key);
	$('#s_smtp_host').val(data.smtp_host);
	$('#s_email_domain').val(data.email_domain);
	$('#s_email_user').val(data.email_user);
	$('#s_email_password').val(data.email_password);
	$('#s_email_port').val(data.email_port);
	
}

/*
 * Show the user dialog
 */
function openUserDialog(existing, userIndex) {
    'use strict';
	var i,
		$user_groups = $('#user_groups'),
		$user_projects = $('#user_projects'),
		$user_roles = $('#user_roles'),
		h,
		idx,
		filter_group, 
		filter_project,
		filter_role;
	
	gCurrentUserIndex = userIndex;
	
	
	filter_group = $('#group_name').val();
	h = [];
	idx = -1;
	for(i = 0; i < gGroups.length; i++) {
		if((gGroups[i].id !== 4 || globals.gIsOrgAdministrator) && 
				(gGroups[i].id !== 6 || globals.gIsOrgAdministrator || globals.gIsSecurityAdministrator)) {
			h[++idx] = '<div class="checkbox"><label>';
			h[++idx] = '<input type="checkbox" id="'; 
			h[++idx] = 'user_groups_cb' + i;
			h[++idx] = '" name="';
			h[++idx] = 'user_groups_cb';
			h[++idx] = '" value="';
			h[++idx] = gGroups[i].id + '"';
			if(filter_group === gGroups[i].name) {
				h[++idx] = ' checked="checked"';
			} else if(existing) {
				if(hasName(gUsers[userIndex].groups, gGroups[i].name)) {
					h[++idx] = ' checked="checked"';
				}
			}
			h[++idx] = '/> ';
			h[++idx] = gGroups[i].name;
			h[++idx] =	 '</label></div>';
			
		}
	}
	$user_groups.empty().append(h.join(''));

	// Add projects
	filter_project = $('#project_name').val();
	h = [];
	idx = -1;
	for(i = 0; i < globals.gProjectList.length; i++) {
		h[++idx] = '<div class="checkbox"><label>';
		h[++idx] = '<input type="checkbox" id="'; 
		h[++idx] = 'user_projects_cb' + i;
		h[++idx] = '" name="';
		h[++idx] = 'user_projects_cb';
		h[++idx] = '" value="';
		h[++idx] = globals.gProjectList[i].id + '"';
		if(filter_project === globals.gProjectList[i].name) {
			h[++idx] = ' checked="checked"';
		} else if(existing) {
	
			if(hasId(gUsers[userIndex].projects, globals.gProjectList[i].id)) {
				h[++idx] = ' checked="checked"';
			}
		}
		h[++idx] = '/>';
		h[++idx] = globals.gProjectList[i].name;
		h[++idx] = '</label></div>';
	}
	$user_projects.empty().append(h.join(''));
	
	// Add roles
	if(globals.gRoleList) {
		filter_role = $('#role_name').val();
		h = [];
		idx = -1;
		for(i = 0; i < globals.gRoleList.length; i++) {
			h[++idx] = '<div class="checkbox"><label>';
			h[++idx] = '<input type="checkbox"';
			h[++idx] = ' name="user_roles_cb"';
			h[++idx] = ' value="';
			h[++idx] = globals.gRoleList[i].id + '"';
			if(filter_role === globals.gRoleList[i].name) {
				h[++idx] = ' checked="checked"';
			} else if(existing) {
		
				if(hasId(gUsers[userIndex].roles, globals.gRoleList[i].id)) {
					h[++idx] = ' checked="checked"';
				}
			}
			h[++idx] = '/>';
			h[++idx] = globals.gRoleList[i].name;
			h[++idx] = '</label></div>';
		}
		$user_roles.empty().append(h.join(''));
	}
	
	$('#user_create_form')[0].reset();
	if(!existing) {
		if($('#send_email').is(':checked')) {
			$('#password_fields').hide();
		} else {
			$('#password_fields').show();
		}
		$('#send_email_fields').show();
		$('#reset_password_fields').hide();
	} else {
		$('#reset_password_fields').show();
		$('#send_email_fields').hide();
		$('#password_fields').hide();
		$('#user_ident').val(gUsers[userIndex].ident);
		$('#user_name').val(gUsers[userIndex].name);
		$('#user_email').val(gUsers[userIndex].email);
	}
	
	 // Initialise the send email or set password radio buttons
	if(!existing) {
		 if(globals.gServerCanSendEmail) {
			 $('input[type=radio][name=send_email]').change(function() {
			        if (this.value == 'send_email') {
			        	$('#password_fields').hide();
			        } else if (this.value == 'set_password') {
			        	$('#password_fields').show();
			        }
			    });
		 } else {
			 $('#password_fields').show();
			 $('input[type=radio][name=send_email]').prop('disabled',true);
			 $('#set_password').prop('checked',true);
		 }
	}
	 
	$('#create_user_popup').modal("show");
}

/*
 * Show the project dialog
 */
function openProjectDialog(existing, projectIndex) {
	
	gCurrentProjectIndex = projectIndex;
	
	
	$('#project_create_form')[0].reset();
	if(existing) {
		$('#p_name').val(globals.gProjectList[projectIndex].name);
		$('#p_desc').val(globals.gProjectList[projectIndex].desc);
		$('#p_tasks_only').prop('checked', globals.gProjectList[projectIndex].tasks_only);
	}
	
	$('#create_project_popup').modal("show");
}

/*
 * Show the user role dialog
 */
function openRoleDialog(existing, roleIndex) {
	
	gCurrentRoleIndex = roleIndex;
	
	$('#role_create_form')[0].reset();
	if(existing) {
		$('#ur_name').val(globals.gRoleList[roleIndex].name);
		$('#ur_desc').val(globals.gRoleList[roleIndex].desc);
	}
	
	$('#create_role_popup').modal("show");
}


/*
 * Show the organisation dialog
 */
function openOrganisationDialog(existing, organisationIndex) {
	
	var org = gOrganisationList[organisationIndex];
	gCurrentOrganisationIndex = organisationIndex;
	
	$('#organisation_create_form')[0].reset();
	$('#organisation_logo_form')[0].reset();
	$('#o_banner_logo').attr("src", "/images/smap_logo.png");
	
	if(existing) {
		$('#o_name').val(org.name);
		$('#o_company_name').val(org.company_name);
		$('#o_company_address').val(org.company_address);
		$('#o_company_phone').val(org.company_phone);
		$('#o_company_email').val(org.company_email);
		$('#o_admin_email').val(org.admin_email);
		$('#o_smtp_host').val(org.smtp_host);
		$('#o_email_domain').val(org.email_domain);
		$('#o_email_user').val(org.email_user);
		$('#o_email_password').val(org.email_password);
		$('#o_email_port').val(org.email_port);
		$('#o_default_email_content').val(org.default_email_content);
		$('.puboption').each(function() {
			console.log("option: " + $(this).val() );
			if($(this).val() === "email") {
				this.checked = org.allow_email;	
			} else if($(this).val() === "facebook") {
				this.checked = org.allow_facebook;
			} else if($(this).val() === "twitter") {
				this.checked = org.allow_twitter;
			} else if($(this).val() === "can_edit") {
				this.checked = org.can_edit;
			} else if($(this).val() === "ft_delete_submitted") {
				this.checked = org.ft_delete_submitted;
			} else if($(this).val() === "ft_send_trail") {
				this.checked = org.ft_send_trail;
			} else if($(this).val() === "ft_sync_incomplete") {
				this.checked = org.ft_sync_incomplete;
			}
		});
		addLanguageOptions($('#o_language'), org.locale);
		$('#o_tz').val(org.timeZone);
		gOrgId = org.id;
		setBannerLogo(org.id);

	}
	$('#create_organisation_popup').modal("show");
}

function setBannerLogo(orgId) {
	var d = new Date();
	$('#o_banner_logo').attr("src", "/surveyKPI/file/bannerLogo/organisation?settings=true&org=" + orgId + "&" + d.valueOf() );
}

/*
 * Update the server with the user details
 */
function writeUserDetails(userList, $dialog) {

	var userString = JSON.stringify(userList);
	
	addHourglass();
	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  url: "/surveyKPI/userList",
		  data: { users: userString },
		  success: function(data, status) {
			  removeHourglass();
			  $('#userDetailsSave').prop("disabled", false);
			  getUsers();
			  $dialog.modal("hide");
		  }, error: function(xhr, textStatus, err) {
			  removeHourglass();
			  $('#userDetailsSave').prop("disabled", false);
			  if(xhr.readyState == 0 || xhr.status == 0) {
				  $dialog.modal("hide");
				  return;  // Not an error
			  } else {
				  if(xhr.status === 409) {
					  var msg;
					  if(xhr.responseText.indexOf("email") > 0) {
						  msg = "Duplicate email. Some other user has this email."
					  } else {
						  msg = "Duplicate user identification. Please change the user ident.";
					  }
					  alert(msg); 
				  } else {
					  alert("Error user details not saved: " + xhr.responseText);
				  }
			  }
				
			  
		  }
	});
}


/*
 * Update the server with the server config
 */
function writeServerDetails() {

	var url = "/surveyKPI/server",
		serverString,
		server = {	
			mapbox_default: $('#mapbox_key').val(),
			google_key: $('#google_key').val(),
			smtp_host: $('#s_smtp_host').val(),
			email_domain: $('#s_email_domain').val(),
			email_user: $('#s_email_user').val(),
			email_password: $('#s_email_password').val(),
			email_port: $('#s_email_port').val()
		};
	
	var serverString = JSON.stringify(server);
	
	$('#org_alert').hide();
	addHourglass();
	$.ajax({
		  type: "POST",
		  data: { settings: serverString },
		  cache: false,
		  contentType: "application/json",
		  url: url,
		  success: function(data, status) {
			  removeHourglass();
			  $('#org_alert').show().removeClass('alert-danger').addClass('alert-success').html(localise.set["c_saved"]);
		  },
		  error: function(xhr, textStatus, err) {
			  removeHourglass(); 
			  $('#org_alert').show().removeClass('alert-success').addClass('alert-danger').html(localise.set["t_ens"] + xhr.responseText);
			 
		  }
	});
}

/*
 * Update the user table with the latest user data
 */
function updateUserTable(group, projectStr) {
	
	var $userTable = $('#user_table'),
		i, user,
		h = [],
		idx = -1,
		filterGroup = true, filterProject = true,
		yesGroup, yesProject,
		project;
	
	gControlCount = 0;
	$('#controls').find('button').addClass("disabled");
	
	if(group === undefined || group === null) {
		group = $('#group_name').val(); 

	}
	if(projectStr === undefined) {
		projectStr = $('#project_name').val();
	}

	project = Number(projectStr);
	
	if(!group || group === "All") {
		filterGroup = false;
	}
	if(project === 0) {
		filterProject = false;
	}
	
	
	h[++idx] = '<table class="table table-striped">';
	h[++idx] = '<thead>';
	h[++idx] = '<col style="width:auto;">';
	h[++idx] = '<col style="width:160px;">';	
	h[++idx] = '<col style="width:auto;">';
	//h[++idx] = '<tr>';
	//h[++idx] = '<th colspan="3"></th>';
	//h[++idx] = '<th colspan="3" style="text-align: center;">Forms Submitted</th>';
	//h[++idx] = '</tr>';
	h[++idx] = '<tr>';
	h[++idx] = '<th>Select</th>';
	h[++idx] = '<th>User Id</th>';
	h[++idx] = '<th>Name</th>';
	//h[++idx] = '<th>Email</th>';
	//h[++idx] = '<th>This Month</th>';
	//h[++idx] = '<th>Last Month</th>';
	//h[++idx] = '<th>All Time</th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody>';


	for(i = 0; i < gUsers.length; i++) {
		user = gUsers[i];

		yesGroup = !filterGroup || hasName(user.groups, group);
		yesProject = !filterProject || hasId(user.projects, project);
		
		if(yesGroup && yesProject) {
			h[++idx] = '<tr>';
			h[++idx] = '<td class="control_td"><input type="checkbox" name="controls" value="';
			h[++idx] = i;
			h[++idx] = '"></td>';
			h[++idx] = '<td class="user_edit_td"><button class="btn btn-default user_edit" style="width:100%;" value="';
			h[++idx] = i;
			h[++idx] = '">';
			h[++idx] = user.ident;
			h[++idx] = '</button></td>';
			h[++idx] = '<td>';
			h[++idx] = user.name;
			h[++idx] = '</td>';
			//h[++idx] = '<td>';
			//h[++idx] = user.email;
			//h[++idx] = '</td>';
			//h[++idx] = '<td>';
			//h[++idx] = user.this_month;
			//h[++idx] = '</td>';
			//h[++idx] = '<td>';
			//h[++idx] = user.last_month;
			//h[++idx] = '</td>';
			//h[++idx] = '<td>';
			//h[++idx] = user.all_time;
			//h[++idx] = '</td>';
			h[++idx] = '</tr>';
		}
	}	
	
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
	
	$userTable.empty().append(h.join(''));
	$('.user_edit').click(function() {
		openUserDialog(true, $(this).val());
	});
	$('#user_table .control_td').find('input').click(function() {
		if($(this).is(':checked')) {

			++gControlCount;
			if(gControlCount === 1) {
				$('#controls').find('button').removeClass("disabled");
				$('.move_to_organisation').removeClass("disabled");
			}
		} else {

			--gControlCount;
			if(gControlCount === 0) {
				$('#controls').find('button').addClass("disabled");
				if(gControlProjectCount === 0) {
					$('.move_to_organisation').addClass("disabled");
				}
			}
		}
 
	});

}

/*
 * Update the project table with the latest project data
 */
function updateProjectTable() {

	gControlProjectCount = 0;
	$('#project_controls').find('button').addClass("disabled");
	$('.move_to_organisation').addClass("disabled");
	
	var $projectTable = $('#project_table'),
		i, project,
		h = [],
		idx = -1;
	
	h[++idx] = '<table class="table table-striped">';
	h[++idx] = '<colgroup>';
	h[++idx] = '<col style="width:auto;">';
	h[++idx] = '<col style="width:auto;">';
	h[++idx] = '<col style="width:160px;">';	
	h[++idx] = '<col style="width:auto;">';
	h[++idx] = '</colgroup>';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th></th>';
	h[++idx] = '<th>';
	h[++idx] = localise.set["c_id"];	// Project Id
	h[++idx] = '</th>';
	h[++idx] = '<th>';
	h[++idx] = localise.set["c_name"];	// Name
	h[++idx] = '</th>';
	h[++idx] = '<th>';
	h[++idx] = localise.set["u_chg"];	// Changed by
	h[++idx] = '</th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody>';


	for(i = 0; i < globals.gProjectList.length; i++) {
		project = globals.gProjectList[i];
		
		h[++idx] = '<tr>';
		h[++idx] = '<td class="control_td"><input type="checkbox" name="controls" value="';
		h[++idx] = i;
		h[++idx] = '"></td>';
		h[++idx] = '<td>';
		h[++idx] = project.id;
		h[++idx] = '</td>';
		h[++idx] = '<td class="user_edit_td"><button class="btn btn-default project_edit" style="width:100%;" value="';
		h[++idx] = i;
		h[++idx] = '">';
		h[++idx] = project.name;
		h[++idx] = '</button></td>';
		h[++idx] = '<td>';
		h[++idx] = project.changed_by;
		h[++idx] = '</td>';
		
		h[++idx] = '</tr>';
	}	
	
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
	
	$projectTable.empty().append(h.join('')).find('table');
	$('.project_edit').click(function() {
		openProjectDialog(true, $(this).val());
	});
	$('#project_table .control_td').find('input').click(function() {
		if($(this).is(':checked')) {

			++gControlProjectCount;
			if(gControlProjectCount === 1) {
				$('#project_controls').find('button').removeClass("disabled");
				$('.move_to_organisation').removeClass("disabled");
			}
		} else {

			--gControlProjectCount;
			if(gControlProjectCount === 0) {
				$('#project_controls').find('button').addClass("disabled");
				if(gControlCount === 0) {
					$('.move_to_organisation').addClass("disabled");
				}
			}
		}
 
	});

}

/*
 * Update the user role table with the latest list of roles
 */
function updateRoleTable() {


	var $tab = $('#role_table'),
		i, role,
		h = [],
		idx = -1;
	
	h[++idx] = '<table class="table table-striped">';
	h[++idx] = '<colgroup>';
	h[++idx] = '<col style="width:auto;">';
	h[++idx] = '<col style="width:auto;">';
	h[++idx] = '<col style="width:160px;">';	
	h[++idx] = '<col style="width:auto;">';
	h[++idx] = '</colgroup>';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th></th>';
	h[++idx] = '<th>';
	h[++idx] = localise.set["c_id"];	// Id
	h[++idx] = '</th>';
	h[++idx] = '<th>';
	h[++idx] = localise.set["c_name"];	// Name
	h[++idx] = '</th>';
	h[++idx] = '<th>';
	h[++idx] = localise.set["u_chg"];	// Changed by
	h[++idx] = '</th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody>';


	for(i = 0; i < globals.gRoleList.length; i++) {
		role = globals.gRoleList[i];
		
		h[++idx] = '<tr>';
		h[++idx] = '<td class="control_td"><input type="checkbox" name="controls" value="';
		h[++idx] = i;
		h[++idx] = '"></td>';
		h[++idx] = '<td>';
		h[++idx] = role.id;
		h[++idx] = '</td>';
		h[++idx] = '<td class="user_edit_td"><button class="btn btn-default role_edit" style="width:100%;" value="';
		h[++idx] = i;
		h[++idx] = '">';
		h[++idx] = role.name;
		h[++idx] = '</button></td>';
		h[++idx] = '<td>';
		h[++idx] = role.changed_by;
		h[++idx] = '</td>';
		
		h[++idx] = '</tr>';
	}	
	
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
	
	$tab.empty().append(h.join(''));
	$('.role_edit', $tab).click(function() {
		openRoleDialog(true, $(this).val());
	});

}

/*
 * Update the organisation table with the latest organisation data
 */
function updateOrganisationTable() {

	gControlOrganisationCount = 0;
	$('#organisation_controls').find('button').addClass("disabled");

	var $organisationTable = $('#organisation_table'),
		i, organisation,
		h = [],
		idx = -1,
		bs = isBusinessServer();
	
	h[++idx] = '<table class="table table-striped">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th></th>';
	h[++idx] = '<th>';
	h[++idx] = localise.set["c_id"];	// Id
	h[++idx] = '</th>';
	h[++idx] = '<th>';
	h[++idx] = localise.set["c_name"];	// Name
	h[++idx] = '</th>';
	h[++idx] = '<th>';
	h[++idx] = localise.set["u_chg"];	// Changed by
	h[++idx] = '</th>';
	if(bs) {
		h[++idx] = '<th>';
		h[++idx] = localise.set["u_usage"];	// Changed by
		h[++idx] = '</th>';
	}
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody>';


	for(i = 0; i < gOrganisationList.length; i++) {
		organisation = gOrganisationList[i];
		
		h[++idx] = '<tr>';
		h[++idx] = '<td class="control_td"><input type="checkbox" name="controls" value="';
		h[++idx] = i;
		h[++idx] = '"></td>';
		h[++idx] = '<td>';
		h[++idx] = organisation.id;
		h[++idx] = '</td>';
		h[++idx] = '<td class="user_edit_td"><button style="width:100%;" class="btn btn-default organisation_edit" value="';
		h[++idx] = i;
		h[++idx] = '">';
		h[++idx] = organisation.name;
		h[++idx] = '</button></td>';
		h[++idx] = '<td>';
		h[++idx] = organisation.changed_by;
		h[++idx] = '</td>';
		if(bs) {
			h[++idx] = '<td class="usage_report_td"><button style="width:100%;" class="btn btn-default btn-warning usage_report" value="';
			h[++idx] = i;
			h[++idx] = '">';
			h[++idx] = '<span class="glyphicon glyphicon-download" aria-hidden="true"></span>';
			h[++idx] = '</button></td>';
		}
		h[++idx] = '</tr>';
	}	
	
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
	
	$organisationTable.empty().append(h.join(''));
	$('.organisation_edit').click(function() {
		openOrganisationDialog(true, $(this).val());
	});
	$('.usage_report', '#organisation_table').click(function () {
		gCurrentOrganisationIndex = $(this).val();
		$('#get_usage_popup').modal("show");
	});
	$('#organisation_table .control_td').find('input').click(function() {
		if($(this).is(':checked')) {

			++gControlOrganisationCount;
			if(gControlOrganisationCount === 1) {
				$('.move_to_organisation').removeClass("disabled");
			}
		} else {

			--gControlOrganisationCount;
			if(gControlOrganisationCount === 0) {
				$('.move_to_organisation').addClass("disabled");
			}
		}
 
	});

}

/*
 * Update simple organisation selects
 */
function updateOrganisationList() {

	var $organisationSelect = $('.organisation_select'),
		i, organisation,
		h = [],
		idx = -1;
	
	for(i = 0; i < gOrganisationList.length; i++) {
		organisation = gOrganisationList[i];
		if(organisation.name !== globals.gLoggedInUser.organisation_name) {
		
			h[++idx] = '<option value="';
			h[++idx] = organisation.id;
			h[++idx] = '">';
			h[++idx] = organisation.name;
			h[++idx] = '</option>';
		}
		
	}	
	
	$organisationSelect.empty().append(h.join(''));

}

// Return true if the item with the name is in the list
function hasName(itemList, item) {
	var i;
	for(i = 0; i < itemList.length; i++) {
		if(itemList[i].name === item) {
			return true;
		}
	}
	return false;
}

//Return true if the item with the id is in the list
function hasId(itemList, item) {
	var i;
	if(itemList) {
		for(i = 0; i < itemList.length; i++) {
			if(itemList[i].id === item) {
				return true;
			}
		}
	}
	return false;
}

/*
 * Get the list of users from the server
 */
function getUsers() {
	
	var month,		// Month and year that submissions for each user are to be calculated
		year,
		today;
	
	today = new Date();
	month = today.getMonth() + 1;	// Server works from 1 - 12
	year = today.getFullYear();
	
	addHourglass();
	$.ajax({
		url: "/surveyKPI/userList?year=" + year + "&month=" + month,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			gUsers = data;
			updateUserTable();
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get list of users: " + err);
			}
		}
	});	
}

/*
 * Get a usage report
 */
function getUsage(oId, month, year, period) {
	

	docURL = "/surveyKPI/usage/" + oId + "?month=" + month + "&year=" + year + "&period=" + period;	
	window.location.href = docURL;
	
}

/*
 * Get the list of available groups from the server
 */
function getGroups() {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/groupList",
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			gGroups = data;
			updateGroupTable();
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get list of groups: " + err);
			}
		}
	});	
}



/*
 * Update the group table with the current group list
 */
function updateGroupTable() {
	var $groupSelect = $('#group_name'),
	i, 
	h = [],
	idx = -1;
	
	h[++idx] = '<option value="All">All</option>';
	for(i = 0; i < gGroups.length; i++) {
		if((gGroups[i].id !== 4 || globals.gIsOrgAdministrator) && (gGroups[i].id !== 6 || globals.gIsSecurityAdministrator)) {
			h[++idx] = '<option value="';
			h[++idx] = gGroups[i].name;
			h[++idx] = '">';
			h[++idx] = gGroups[i].name;
			h[++idx] = '</option>';
		}
	}
	$groupSelect.empty().append(h.join(''));
	$groupSelect.val("All");
}

/*
 * Delete the selected users
 */
function deleteUsers () {
	
	var users = [],
		decision = false,
		h = [],
		i = -1;
	
	$('#user_table').find('input:checked').each(function(index) {
		userIdx = $(this).val();
		users[index] = {id: gUsers[userIdx].id};
		h[++i] = gUsers[userIdx].name;
	});
	
	
	decision = confirm(localise.set["msg_del_users"] +"\n" + h.join());
	if (decision === true) {
		addHourglass();
		$.ajax({
			  type: "DELETE",
			  contentType: "application/json",
			  url: "/surveyKPI/userList",
			  data: { users: JSON.stringify(users) },
			  success: function(data, status) {
				  removeHourglass();
				  getUsers();
			  }, error: function(data, status) {
				  var msg = localise.set["msg_err_del"];
				 removeHourglass();
				 if(typeof data != "undefined" && typeof data.responseText != "undefined" ) {
					 msg = data.responseText;
				 }
				 alert(msg); 
			  }
		});
	}
}

/*
 * Delete the selected projects
 */
function deleteProjects () {
	
	var projects = [],
		decision = false,
		h = [],
		i = -1,
		projectIdx,
		$dialog;
	
	$('#project_table').find('input:checked').each(function(index) {
		projectIdx = $(this).val();
		projects[index] = {id: globals.gProjectList[projectIdx].id};
		h[++i] = globals.gProjectList[projectIdx].name;
	});

	
	decision = confirm(localise.set["msg_del_projects"] + "\n" + h.join());
	if (decision === true) {
		addHourglass();
		$.ajax({
			  type: "DELETE",
			  contentType: "application/json",
			  url: "/surveyKPI/projectList",
			  data: { projects: JSON.stringify(projects) },
			  success: function(data, status) {
				  removeHourglass();
				  getProjects();
			  }, error: function(data, status) {
				  removeHourglass();
				  if(data && data.responseText) {
					  alert(data.responseText);
				  } else {
					  alert(localise.set["msg_err_del"]);
				  }
			  }
		});
	}
}
	
/*
 * Delete the roles
 */
function deleteRoles () {
	
	var roles = [],
		decision = false,
		h = [],
		i = -1,
		roleIdx,
		$dialog;
	
	$('#role_table').find('input:checked').each(function(index) {
		roleIdx = $(this).val();
		roles[index] = {id: globals.gRoleList[roleIdx].id};
		h[++i] = globals.gRoleList[roleIdx].name;
	});

	
	decision = confirm(localise.set["msg_del_roles"] + "\n" + h.join());
	if (decision === true) {
		addHourglass();
		$.ajax({
			  type: "DELETE",
			  contentType: "application/json",
			  url: "/surveyKPI/role/roles",
			  data: { roles: JSON.stringify(roles) },
			  success: function(data, status) {
				  removeHourglass();
				  getRoles(updateRoleTable);
			  }, error: function(data, status) {
				  removeHourglass();
				  if(data && data.responseText) {
					  alert(data.responseText);
				  } else {
					  alert(localise.set["msg_err_del"]);
				  }
			  }
		});
	}
}

/*
 * Delete the selected organisations
 */
function deleteOrganisations () {
	
	var organisations = [],
		decision = false,
		h = [],
		i = -1,
		orgIdx;
	
	$('#organisation_table').find('input:checked').each(function(index) {
		orgIdx = $(this).val();
		organisations[index] = {id: gOrganisationList[orgIdx].id, name: gOrganisationList[orgIdx].name};
		h[++i] = gOrganisationList[orgIdx].name;
	});

	
	decision = confirm(localise.set["mesg_del_orgs"] + "\n" + h.join());
	if (decision === true) {
		addHourglass();
		$.ajax({
			  type: "DELETE",
			  contentType: "application/json",
			  url: "/surveyKPI/organisationList",
			  data: { organisations: JSON.stringify(organisations) },
			  success: function(data, status) {
				  removeHourglass();
				  getOrganisations();
			  }, error: function(data, status) {
				  removeHourglass();
				  if(data && data.responseText) {
					  alert(data.responseText);
				  } else {
					  alert(localise.set["msg_err_del"]);
				  }
			  }
		});
	}
}

/*
 * Switch to the selected organisation
 */
function moveToOrganisations (orgId, users, projects) {

	addHourglass();
	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  cache: false,
		  url: "/surveyKPI/organisationList/setOrganisation",
		  data: { 
			  orgId: orgId,
			  users : JSON.stringify(users),
			  projects: JSON.stringify(projects)
			  },
		  success: function(data, status) {
			  removeHourglass();
			  window.location.reload();
		  }, error: function(data, status) {
			  removeHourglass();
			  if(data && data.responseText) {
				  alert(data.responseText);
			  } else {
				  alert("Error: Failed to move users and projects to new organisation");
			  }
		  }
	});

}

});
	
