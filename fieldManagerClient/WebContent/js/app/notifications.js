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

define(['jquery','localise', 'common', 'globals',  'tablesorter', 'bootstrap'], 
		function($, lang, common, globals, tablesorter, bootstrap) {
	
var	gNotifications,		// Globals in this java script file
	gUpdateFwdPassword,
	gSelectedNotification = -1,
	gRemote_host,
	gRemote_user;	
	
$(document).ready(function() {
	
	localise.setlang();		// Localise HTML
		
	// Get the user details
	getLoggedInUser(projectSet, false, true, undefined);

	// Set change function on projects
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = 1;
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);		// Save the current project id
		projectSet();
 	});
	
	// Set change function target
	$('#target').change(function() {
		var target = $(this).val();
		
		setTargetDependencies(target);
 	});
	setTargetDependencies("email");
	
	// Enable the save notifications function
	$('#saveNotification').click(function(){saveNotification();});
	
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
		gUpdateFwdPassword = true;
	});
	
	$('#fwd_upd_rem_survey').click(function(){
		getRemoteSurveys();
	});
	
	$('#fwd_rem_survey').change(function(){
		remoteSurveyChanged();
	});
	
	$('#addNotification').click(function(){
		edit_notification();
		$('#addNotificationPopup').modal("show");
	});
	
	// Add response to a source survey being selected 
	$('#survey').change(function() {
		surveyChanged();
	});
		
	enableUserProfileBS();
});	

function surveyChanged(qId) {
	
	var language = "none",
		sId = $('#survey').val(),
		qList;
	
	if(!qId) {
		qId = "-1";
	}
	
	qList = globals.gSelector.getSurveyQuestions(sId, language);
	
	if(!qList) {
		getQuestionList(sId, language, qId, "-1", undefined, false, undefined);
	} else {
		setSurveyViewQuestions(qList, qId);
	}
}

function setTargetDependencies(target) {
	if(target === "email") { 
		$('#email_options').show();
		$('#forward_options').hide();
	} else if(target === "forward") {
		$('#forward_options').show();
		$('#email_options').hide();
	}		
}

function projectSet() {
	
	loadSurveys(globals.gCurrentProject, undefined, false, false, surveyChanged);			// Get surveys
	getNotifications(globals.gCurrentProject);
}

/*
 * Save a notification
 */
function saveNotification() {
	
	var notication,
		url,
		notificationString,
		target = $('#target').val();
	
	if(target === "email") {
		notification = saveEmail();
	} else if(target === "forward") {
		notification = saveForward();
	}
		
	if(!notification.error) {
		
		notification.s_id = $('#survey').val();
		notification.enabled = $('#nt_enabled').is(':checked');
		
		if(gSelectedNotification !== -1) {
			notification.id = gSelectedNotification;
			url = "/surveyKPI/notifications/update";
		} else {
			url = "/surveyKPI/notifications/add";
		}
			
			
		notificationString = JSON.stringify(notification);
		$dialog = $(this);
		addHourglass();
		$.ajax({
			  type: "POST",
			  contentType: "application/json",
			  dataType: "json",
			  async: false,
			  url: url,
			  data: { notification: notificationString },
			  success: function(data, status) {
				  removeHourglass();
				  getNotifications(globals.gCurrentProject);
				  $('#addNotificationPopup').modal("hide");
			  },
			  error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						alert(localise.set["msg_err_save"] + xhr.responseText);
					}
				}
		});
			        	    	
	}
}	

/*
 * Process a save notification when the target is "email"
 */
function saveEmail() {
	
	var error = false,
		notification = {};

	notification.target = "email";
	notification.notifyDetails = {};
	notification.notifyDetails.emails = $('#notify_emails').val().split(",");
	notification.notifyDetails.emailQuestion = $('#email_question').val();
	notification.notifyDetails.from = $('#from_email').val();
	notification.notifyDetails.subject = $('#email_subject').val();
	notification.notifyDetails.content = $('#email_content').val();
	notification.notifyDetails.attach = $('#email_attach').val();
	
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
		notification.update_password = gUpdateFwdPassword;
		
		// Save the values temporarily entered by the user
		gRemote_host = host;
		gRemote_user = $('#fwd_user').val();	
		
	} else {
		notification.error = true;
	}
	
	return notification;
}

function edit_notification(idx) {
	
	var notification,
		title = localise.set["msg_add_notification"];
	
	document.getElementById("notification_edit_form").reset();
	
	if(typeof idx !== "undefined") {
		notification = gNotifications[idx];
		
		title = localise.set["msg_edit_notification"],
		
		$('#target').val(notification.target);
		setTargetDependencies(notification.target)
		
		$('#survey').val(notification.s_id);
		if(notification.notifyDetails && notification.notifyDetails.emails) {
			$('#notify_emails').val(notification.notifyDetails.emails.join(";"));
			$('#from_email').val(notification.notifyDetails.from);
			if(notification.notifyDetails.emailQuestion) {
				surveyChanged(notification.notifyDetails.emailQuestion);
			}
			$('#email_subject').val(notification.notifyDetails.subject);
			$('#email_content').val(notification.notifyDetails.content);
			$('#email_attach').val(notification.notifyDetails.attach);
		}
		$('#fwd_rem_survey_id').val(notification.remote_s_ident);
		$('#fwd_rem_survey_nm').val(notification.remote_s_name);	
		$('#fwd_user').val(notification.remote_user);	
		// Password not returned from server - leave blank
		
		$('#fwd_host').val(notification.remote_host);
		if(notification.enabled) {
			$('#nt_enabled').attr('checked','checked');
		} else {
			$('#nt_enabled').removeAttr('checked');
		}
		gUpdateFwdPassword = false;
		gSelectedNotification = notification.id;
	} else {
		
		$('#fwd_host').val(gRemote_host);	// Set the values to the ones last used
		$('#fwd_user').val(gRemote_user);
		
		$('#nt_enabled').attr('checked','checked');
		gUpdateFwdPassword = true;
		gSelectedNotification = -1;
	}
	$('#addNotificationLabel').html(title);
	
}

/*
 * Load the existing notifications from the server
 */
function getNotifications(projectId) {

	if(projectId != -1) {
		var url="/surveyKPI/notifications/" + projectId;
		
		addHourglass();
		$.ajax({
			url: url,
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				gNotifications = data;
				if(data) {
					updateNotificationList(data);
				}
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					console.log("Error: Failed to get list of notifications: " + err);
				}
			}
		});	
	}
}



/*
 * Delete a notification
 */
function delete_notification(id) {
	
	addHourglass();
	$.ajax({
		  type: "DELETE",
		  contentType: "application/json",
		  dataType: "json",
		  async: false,
		  url: "/surveyKPI/notifications/" + id,
		  success: function(data, status) {
			  removeHourglass();
			  getNotifications(globals.gCurrentProject);
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
 * Update the list of remote survey
 */
function updateRemoteSurveys(surveyList) {

	console.log("updateRemoteSurvey");

	var $rs = $('#fwd_rem_survey'),
		i, survey,
		h = [],
		idx = -1;

	for(i = 0; i < surveyList.length; i++) {
		survey = surveyList[i];
		h[++idx] = '<option value="';
		h[++idx] = survey.formID;
		h[++idx] = '">';
		h[++idx] = survey.name;
		h[++idx] = '</option>';
	}
	
	$rs.empty().append(h.join(''));
	remoteSurveyChanged();
		
}



/*
 * Get available surveys from a remote host
 */
function getRemoteSurveys() {
	
	var host,
		user,
		password,
		remote = {},
		remoteString;
	
	remote.address = $('#fwd_host').val();
	remote.user = $('#fwd_user').val();
	remote.password = $('#fwd_password').val();
	
	
	if(!remote.address || remote.address.length == 0) {
		alert(localise.set["msg_val_rh"]);
		$('#fwd_host').focus();
		return;
	} else if(!remote.user || remote.user.length == 0) {
		alert(localise.set["msg_val_u_id"]);
		$('#fwd_user').focus();
		return;
	} else if(!remote.password || remote.user.password == 0) {
		alert(localise.set["msg_val_pass"]);
		$('#fwd_password').focus();
		return;
	}
	
	remoteString = JSON.stringify(remote);
	addHourglass();
	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  dataType: "json",
		  async: true,
		  url: "/surveyKPI/notifications/getRemoteSurveys",
		  data: { remote: remoteString },
		  success: function(data, status) {
			  removeHourglass();
			  updateRemoteSurveys(data);
		  },
		  error: function(xhr, textStatus, err) {
				removeHourglass();
				$('#fwd_rem_survey').empty();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					var msg;
					if(xhr.responseText.indexOf("RSA premaster") >= 0) {				
						msg = localise.set["msg_err_cert"];
					} else {
						msg = xhr.responseText;
					}
					alert(localise.set["msg_err_get_f"] + msg);
				}
			}
	});

}

/*
 * Update the notification list
 */
function updateNotificationList(data) {

	var $selector=$('#notification_list'),
		i, 
		h = [],
		idx = -1,
		updateCurrentProject = true;
	
	h[++idx] = '<table class="table">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th>' + localise.set["c_survey"], + '</th>';
	h[++idx] = '<th>' + localise.set["c_target"] + '</th>';
	h[++idx] = '<th>' + localise.set["c_details"] + '</th>';
	h[++idx] = '<th>' + localise.set["c_action"] + '</th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody class="table-striped">';
	
	for(i = 0; i < data.length; i++) {
		
		h[++idx] = '<tr>';
		
		// survey
		h[++idx] = '<td>';
		h[++idx] = data[i].s_name;
		h[++idx] = '</td>';

		// target
		h[++idx] = '<td>';
		h[++idx] = data[i].target;
		h[++idx] = '</td>';
		
		// details
		h[++idx] = '<td>';
		if(data[i].target === "email" && data[i].notifyDetails) {
			if((data[i].notifyDetails.emails.length > 0 && data[i].notifyDetails.emails[0].trim().length > 0) || (data[i].notifyDetails.emailQuestion && data[i].notifyDetails.emailQuestion > 0)) {
				h[++idx] = 'Send ' + data[i].notifyDetails.attach + ' to ';
				h[++idx] = data[i].notifyDetails.emails.join(","); 
				if(data[i].notifyDetails.emailQuestion && data[i].notifyDetails.emailQuestion > 0) {
					if(data[i].notifyDetails.emails.length > 0 && data[i].notifyDetails.emails[0].trim().length > 0) {
						h[++idx] = ', and'
					}
					h[++idx] = ' emails entered in response to a question';
				}
			}
		} else if(data[i].target === "forward"){
			h[++idx] = data[i].remote_host;
			h[++idx] = ':';
			h[++idx] = data[i].remote_s_name;
		}
		h[++idx] = '</td>';
		
		// actions
		h[++idx] = '<td>';
		
		h[++idx] = '<button type="button" data-idx="';
		h[++idx] = i;
		h[++idx] = '" class="btn btn-default btn-sm edit_not warning">';
		h[++idx] = '<span class="glyphicon glyphicon-edit" aria-hidden="true"></span></button>';
		
		h[++idx] = '<button type="button" data-idx="';
		h[++idx] = i;
		h[++idx] = '" class="btn btn-default btn-sm rm_not danger">';
		h[++idx] = '<span class="glyphicon glyphicon-trash" aria-hidden="true"></span></button>';
		
		h[++idx] = '</td>';
		// end actions
		
		h[++idx] = '</tr>';
	}
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
	
	$selector.empty().append(h.join(''));
	
	$(".rm_not", $selector).click(function(){
		var idx = $(this).data("idx");
		delete_notification(gNotifications[idx].id);
	});
	
	$(".edit_not", $selector).click(function(){
		var idx = $(this).data("idx");
		edit_notification(idx);
		$('#addNotificationPopup').modal("show");
	});

}

});	
