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

define(['jquery','localise', 'common', 'globals', 'bootbox'], function($, lang, common, globals, bootbox) {
	
var gUsers,
	gGroups,
	gOrganisationList,
	gControlCount,			// Number of users that have been set - used to enable / disable control buttons
	gControlProjectCount,	// Number of projects that have been set - used to enable / disable control buttons
	gControlOrganisationCount,
	gCurrentProjectIndex,	// Set while editing a projects details
	gCurrentOrganisationIndex,
	gCurrentUserIndex,		// Set while editing a users details
	gOrgId;

$(document).ready(function() {

	localise.setlang();		// Localise HTML
	getUsers();
	getGroups();
	getProjects();
	getLoggedInUser(undefined, false, false, getOrganisations);
	
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
    		  	  
		$('#projectPanel').hide();
		$('#organisationPanel').hide();
		$('#userPanel').show();
    })
    $('#projectsTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		  	  
		$('#projectPanel').show();
		$('#organisationPanel').hide();
		$('#userPanel').hide();
    })
    $('#organisationTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		  	  
		$('#projectPanel').hide();
		$('#organisationPanel').show();
		$('#userPanel').hide();
    })
    
    // Style the upload buttons
    $('.file-inputs').bootstrapFileInput();
    
    /*
     * Submit the banner logo
     *
    $('#submitBanner').click( function() {

    	$('#settingsFlag').val("true");
    	var f = document.forms.namedItem("fileupload");
    	var formData = new FormData(f);
    	
    	addHourglass();
        $.ajax({
            url: '/surveyKPI/upload/media',
            type: 'POST',
            data: formData,
            cache: false,
            contentType: false,
            processData:false,
            success: function(data) {
    			removeHourglass();
            	console.log("Success");
            	setBannerLogo(gOrgId);
            	
            	$('#upload_msg').val("");
            	document.forms.namedItem("fileupload").reset();
            	
            },
            error: function(xhr, textStatus, err) {
    			removeHourglass();
  				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Upload failed: " + err);

				}
            }
        });
    });
    */
    
    // Function to save a users details
    $('#userDetailsSave').click(function(e) {
		var userList = [],
		user = {},
		error = false,
		validIdent = new RegExp('^[a-z0-9]+$'),
		validEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+.[A-Z]{2,4}/igm,
		send_email = $('input[name=send_email]:checked', '#send_email_fields').val();
	
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
		if(user.ident.len)
		if(!user.ident.match(validIdent)) {
			alert("User ident must be specified and only include lowercase characters from a-z and numbers.  No spaces.");
			$('#user_ident').focus();
			return false;
		}
		if(user.ident.indexOf(' ') !== -1) {
			alert("Spaces are not allowed in the user ident");
			$('#user_ident').focus();
			return false;
		}
		if(user.email.length > 0) {
			if(!validEmail.test(user.email)) {
				error = true;
				alert("Email is not valid");
				$('#user_email').focus();
				return false;
			}
		}
		
		// For a new user, email must be specified if the send email check box is set
		if(user.sendEmail && user.email.length === 0) {
			error = true;
			alert("If sending an email to the user then email address must be specified");
			$('#user_email').focus();
			return false;
		}
	
		if($('#user_password').is(':visible')) {
			user.password = $('#user_password').val();
			if(user.password.length < 2) {
				error = true;
				user.password = undefined;
				alert("Passwords, if specified, must be longer than 1 character");
				$('#user_password').focus();
				return false;
			}
			if($('#user_password_confirm').val() !== user.password) {
				error = true;
				user.password = undefined;
				alert("Passwords do not match");
				$('#user_password').focus();
				return false;
			}
		} else {
			user.password = undefined;
		}
		
		user.groups = [];
		user.projects = [];
		$('#user_groups').find('input:checked').each(function(index) {
			user.groups[index] = {id: $(this).val()};
		});
		$('#user_projects').find('input:checked').each(function(index) {
			user.projects[index] = {id: $(this).val()};
	
		});
		userList[0] = user;
		writeUserDetails(userList, $(this));	// Save the user details to the database
 		
    });
  
	/* 
	 * Set focus to first element on opening modals
	 */
	$('.modal').on('shown.bs.modal', function() {
		$(this).find('input[type=text],textarea,select').filter(':visible:first').focus();
	});
    
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
	  		
		projectList[0] = project;
		var projectString = JSON.stringify(projectList);
	
		addHourglass();
		$.ajax({
			  type: "POST",
			  contentType: "application/json",
			  dataType: "json",
			  async: false,
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
					  var msg = err;
					  if(err.indexOf("Conflict") >= 0) {
						  msg = "Duplicate project name";
					  }
					  alert("Error: Failed to save project details: " + msg);
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
		organisation.admin_email = $('#o_admin_email').val();
		organisation.smtp_host = $('#o_smtp_host').val();
		organisation.email_domain = $('#o_email_domain').val();
		organisation.default_email_content = $('#o_default_email_content').val();
	
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
					  alert("Error organisation details not saved: " + msg);
				  }
			  }
		});
	
    });
    
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
	 
	 // Initialse the move organisation dialog
	 /*
	 $('#move_to_organisation_popup').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			show:"drop",
			title: "Move to Organisation",
			width:380,
			height:300,
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
			        	decision = confirm(h.join(''));
			        	
			        	if (decision === true) {
			        		for(i = 0; i < users.length; i++) {
			        			if(users[i].id === globals.gLoggedInUser.id) {
			        				users[i].keepProjects = true;
			        			} else {
			        				users[i].keepProjects = false;
			        			}			
			        		}
			        		moveToOrganisations(orgId, users, projects);	
			        	}
			        	$(this).dialog("close");
		        	}	
		        }
		        	
			]
		}
	 );
	 */
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
 */
function getOrganisations() {

	// Show the current organisation
	$('#org_alert').text("Organisation: " + globals.gLoggedInUser.organisation_name);
	addHourglass();
	$.ajax({
		url: "/surveyKPI/organisationList",
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			console.log("org data");
			console.log(data);
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
}

/*
 * Show the user dialog
 */
function openUserDialog(existing, userIndex) {
	var i,
		$user_groups = $('#user_groups'),
		$user_projects = $('#user_projects'),
		h,
		idx,
		filter_group, filter_project;
	
	gCurrentUserIndex = userIndex;
	
	
	filter_group = $('#group_name').val();
	h = [];
	idx = -1;
	for(i = 0; i < gGroups.length; i++) {
		if(gGroups[i].id !== 4 || globals.gIsOrgAdministrator) {
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
			 $('input[type=radio][name=send_email]').attr('disabled',true);
			 $('#set_password').attr('checked',true);
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
	}
	$('#create_project_popup').modal("show");
}

/*
 * Show the organisation dialog
 */
function openOrganisationDialog(existing, organisationIndex) {
	
	var org = gOrganisationList[organisationIndex];
	gCurrentOrganisationIndex = organisationIndex;
	
	$('#organisation_create_form')[0].reset();
	if(existing) {
		$('#o_name').val(org.name);
		$('#o_company_name').val(org.company_name);
		$('#o_admin_email').val(org.admin_email);
		$('#o_smtp_host').val(org.smtp_host);
		$('#o_email_domain').val(org.email_domain);
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
			}
		});
		gOrgId = org.id;
		setBannerLogo(org.id);

	}
	$('#create_organisation_popup').modal("show");
}

function setBannerLogo(orgId) {
	var d = new Date();
	$('#o_banner_logo').attr("src", "/media/organisation/" + orgId + "/settings/bannerLogo" + "?" + d.valueOf() );
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
		  dataType: "json",
		  async: false,
		  url: "/surveyKPI/userList",
		  data: { users: userString },
		  success: function(data, status) {
			  removeHourglass();
			  //if(typeof $dialog !== "undefined") {
				  $('#create_user_popup').modal("show");
				  //$dialog.modal("close");
			  //}
			  getUsers();
		  }, error: function(xhr, textStatus, err) {
			  removeHourglass();
			  if(xhr.readyState == 0 || xhr.status == 0) {
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
 * Update the server with the project details
 */
function writeOrganisationDetails(organisationList) {



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
	h[++idx] = '<tr>';
	h[++idx] = '<th>Select</th>';
	h[++idx] = '<th>User Id</th>';
	h[++idx] = '<th>Name</th>';
	//h[++idx] = '<th>Email</th>';
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
	h[++idx] = '<thead>';
	h[++idx] = '<col style="width:auto;">';
	h[++idx] = '<col style="width:auto;">';
	h[++idx] = '<col style="width:160px;">';	
	h[++idx] = '<col style="width:auto;">';
	h[++idx] = '<tr>';
	h[++idx] = '<th></th>';
	h[++idx] = '<th>Project Id</th>';
	h[++idx] = '<th>Name</th>';
	h[++idx] = '<th>Changed By</th>';
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
 * Update the organisation table with the latest organisation data
 */
function updateOrganisationTable() {

	gControlOrganisationCount = 0;
	$('#organisation_controls').find('button').addClass("disabled");

	var $organisationTable = $('#organisation_table'),
		i, organisation,
		h = [],
		idx = -1;
	
	h[++idx] = '<table class="table table-striped">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th></th>';
	h[++idx] = '<th>Organisation Id</th>';
	h[++idx] = '<th>Name</th>';
	h[++idx] = '<th>Changed By</th>';
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
		
		h[++idx] = '</tr>';
	}	
	
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
	
	$organisationTable.empty().append(h.join(''));
	$('.organisation_edit').click(function() {
		openOrganisationDialog(true, $(this).val());
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
	for(i = 0; i < itemList.length; i++) {
		if(itemList[i].id === item) {
			return true;
		}
	}
	return false;
}

/*
 * Get the list of users from the server
 */
function getUsers() {
	
	addHourglass();
	$.ajax({
		url: "/surveyKPI/userList",
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
		if(gGroups[i].name !== 'org admin' || globals.gIsOrgAdministrator) {
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
	
	
	decision = confirm("Are you sure you want to delete these users?\n" + h.join());
	if (decision === true) {
		addHourglass();
		$.ajax({
			  type: "DELETE",
			  contentType: "application/json",
			  async: false,
			  dataType: "json",
			  url: "/surveyKPI/userList",
			  data: { users: JSON.stringify(users) },
			  success: function(data, status) {
				  removeHourglass();
				  getUsers();
			  }, error: function(data, status) {
				  var msg = "Error users not deleted";
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

	
	decision = confirm("Are you sure you want to delete these projects?\n" + h.join());
	if (decision === true) {
		addHourglass();
		$.ajax({
			  type: "DELETE",
			  contentType: "application/json",
			  async: false,
			  dataType: "json",
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
					  alert("Error: projects not deleted");
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

	
	decision = confirm("Are you sure you want to delete these organisations?\n" + h.join());
	if (decision === true) {
		addHourglass();
		$.ajax({
			  type: "DELETE",
			  contentType: "application/json",
			  async: false,
			  dataType: "json",
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
					  alert("Error: organisations not deleted");
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
		  async: false,
		  dataType: "json",
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
	
