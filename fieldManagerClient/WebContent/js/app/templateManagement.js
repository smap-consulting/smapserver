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

define(['jquery','localise', 'common', 'globals',  'bootstrap','moment'], 
		function($, lang, common, globals, bootstrap, moment) {
	
var	gSurveys,		// Only in this java script file
	gUpdateFwdPassword,
	gControlDelete,
	gControlRestore,
	gShowDeleted = false,
	gSelectedTemplate,
	gSelectedTemplateName,
	gRemote_host,
	gRemote_user;	
	
$(document).ready(function() {
	
	localise.setlang();		// Localise HTML
	
	/*
	 * Add functionality to control buttons
	 */
	$('#delete_survey').click(function () {
		if(!$(this).hasClass("disabled")) {
			surveyDelete();
		}
	});
	$('#erase_survey').click(function () {
		if(!$(this).hasClass("disabled")) {
			surveyErase();
		}
	});
	$('#un_delete_survey').click(function () {
		if(!$(this).hasClass("disabled")) {
			surveyUnDelete();
		}
	});
	$('#show_deleted').click(function() {
		gShowDeleted = $('#show_deleted').is(':checked');
		completeSurveyList();
	});
	$('#show_deleted').prop('checked', false);
	
	// Get the user details
	getLoggedInUser(projectSet, false, true, undefined);

	// Set change function on projects
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;
		globals.gCurrentTaskGroup = undefined;
		
		$('#projectId').val(globals.gCurrentProject);		// Set the project value for the hidden field in template upload
		projectSet();
		
		saveCurrentProject(globals.gCurrentProject, 
				globals.gCurrentSurvey, 
				globals.gCurrentTaskGroup);
 	 });
	
	// Upload File
    $('#submitFile').click( function(e) {
    	
    	$('#up_alert, #up_warnings').hide();
    	e.preventDefault();
    	var sId = $('#survey_id').val();
    	var f = document.forms.namedItem("uploadForm");
    	var formData = new FormData(f);
    	
		addHourglass();
        $.ajax({
            url: '/fieldManagerServer/formUpload',
            type: 'POST',
            data: formData,
            dataType: 'json',
            cache: false,
            contentType: false,
            processData:false,
            success: function(data) {
				removeHourglass();
				
            	var surveyId = sId;
            	console.log("Done");
            	console.log(data);
            	projectSet(data, surveyId);
            	
            	// Check for errors in the form
            	if(data && data.status === "error") {
            		$('#up_alert').show().removeClass('alert-success').addClass('alert-danger').html(getResponseHtml(data));
            		var sendto = data.administrator || '';
            		$('#email_button').attr("href", "mailto:" + data.administrator + "?subject=Error loading template&body=" + msgToText(data));
            	} else {
            		document.forms.namedItem("uploadForm").reset();
            		$('#up_alert').show().removeClass('alert-danger').addClass('alert-success').html("Template Loaded");
            	}
            	
            	// Check for warnings in the form
            	if(data && data.warnings && data.warnings.length > 0) {
            		$('#up_warnings').show().html(warningMsgToHtml(data));
            	}
            	
            },
            error: function(xhr, textStatus, err) {
				removeHourglass();
  				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					var msg = xhr.responseText;
					if(msg && msg.indexOf("Content is not allowed in prolog") === 0) {
						msg = "File is not a valid form definition. check that the file is of type .xls or .xml.";
					}
					$('#up_alert').show().removeClass('alert-success').addClass('alert-danger').html("Upload failed: " + msg);

				}
            }
        });
    });
    
	// Download file
	$('#downloadFile').click(function () {
		var docURL,
		language,
		orientation,
		type;

		type = $("input[name='download_type']:checked", "#download_template").val();
		language = $('#download_language option:selected').val();
		orientation = $("input[name='orientation']:checked", "#download_template").val();

		if(type === "pdf") {
			docURL = "/surveyKPI/pdf/" + gSelectedTemplate + "?filename=" + gSelectedTemplateName + "&language=" + language;
			if(orientation === "landscape") {
				docURL += "&landscape=true";
			}
		} else if(type === "xls_edited") {
			docURL = "/surveyKPI/xlsForm/" + gSelectedTemplate + "?filetype=" + "xlsx";	
		} else {
			docURL = "/surveyKPI/survey/" + gSelectedTemplate + "/download?type=" + type + "&language=" + language;	
		}
		window.location.href = docURL;
	});
	
	// On change of template name, hide any previous results
	$('#templateName').keydown(function(){
		$('#up_alert, #up_warnings').hide();
	});
	
	// Change function on file selected
	$('#file').change(function(){
		var templateName = $('#templateName').val();
		var $this = $(this);
		var fileName = $this[0].files[0].name;
		var newTemplateName;
		
		$('#up_alert, #up_warnings').hide();
		
		if(templateName && templateName.trim().length > 0) {
			// ignore - leave user specified name
		} else {
			var lastDot = fileName.lastIndexOf(".");
		    if (lastDot === -1) {
		    	newTemplateName = fileName;
		    } else {
		    	newTemplateName = fileName.substr(0, lastDot);
		    }
			$('#templateName').val(newTemplateName);
		}
	});
	
	// Change function on download file type
	$("input[name='download_type']", "#download_template").change(function() {
		var type = $("input[name='download_type']:checked", "#download_template").val();
		if(type === "pdf" || type == "codebook") {
			$('#download_language_div').show();
		} else {
			$('#download_language_div').hide();
		}
		if(type === "pdf") {
			$('#download_orientation_div').show();
		} else {
			$('#download_orientation_div').hide();
		}
	});
	
	
	$('#fwd_rem_survey').change(function(){
		remoteSurveyChanged();
	});
	
	// Validate upload form on submit
	// Check that the survey has a valid name
	$('#uploadForm').on("submit", function(e) {
		
		var file = $('#templateName').val(),
			reg_start = /^[a-zA-Z_]+.*/,
			pId = $('#projectId').val();
		
		// Check file name
		if(!reg_start.test(file)) {
			alert(localise.set["msg_val_let"]);
			return false;
		} 
		
		// Check for valid project id
		if(pId <= 0) {
			alert(localise.set["msg_val_p"]);
			return false;
		}
		
		return true;
	});
	
	enableUserProfileBS();

});

/*
 * Convert an error response into HTML
 */
function getResponseHtml(msg) {
	var i,
		frag;
	
	msg.mesg = "";
	for(i = 0; i < msg.mesgArray.length; i++) {
		if(msg.mesgArray[i].indexOf("$") === 0) {
			frag = localise.set[msg.mesgArray[i].substring(1)];
			if(frag && frag.length > 0) {
				msg.mesg += localise.set[msg.mesgArray[i].substring(1)];
			} else {
				msg.mesg += msg.mesgArray[i];
			}
		} else {
			msg.mesg += msg.mesgArray[i];
		}
	}
	
	if(msg.hints) {
		for(i = 0; i < msg.hints.length; i++) {
			if(msg.hints[i].indexOf("$") === 0) {
				frag = localise.set[msg.hints[i].substring(1)];
				if(frag && frag.length > 0) {
					msg.hints[i] = frag;
				}
			} 
		}
	}
	
	return msgToHtml(msg);
}

/*
 * Convert the error response to html
 */
function msgToHtml(msg) {
	var idx = -1,
		h = [];
	
	h[++idx] = '<span class="glyphicon glyphicon-exclamation-sign" aria-hidden="true"></span>';
	h[++idx] = '<span class="sr-only"> Error:</span>';
	h[++idx] = " Error: " + msg.mesg;
	h[++idx] = '<ul>';
	for(i = 0; msg.hints && i < msg.hints.length; i++) {
		h[++idx] = '<li>';
		h[++idx] = msg.hints[i];
		h[++idx] = '</li>';
	}
	h[++idx] = '</ul>';
	h[++idx] = '<a class="btn btn-default" id="email_button" href="#" type="button">';
	h[++idx] = '<span class="glyphicon glyphicon-envelope" aria-hidden="true"></span>';
	h[++idx] = '<span class="lang" data-lang="m_help"> Get Help</span>';
	h[++idx] = '</a>';
	
	return h.join('');
}

/*
 * Convert the warning response to html
 */
function warningMsgToHtml(msg) {
	var idx = -1,
		h = [];
	
	h[++idx] = '<span class="sr-only"> Warnings:</span>';
	h[++idx] = "Warnings:";
	h[++idx] = '<ul>';
	for(i = 0; msg.warnings && i < msg.warnings.length; i++) {
		h[++idx] = '<li>';
		h[++idx] = msg.warnings[i];
		h[++idx] = '</li>';
	}
	h[++idx] = '</ul>';
	
	return h.join('');
}

/*
 * Convert the error response to a text message suitable for email
 */
function msgToText(msg) {
	var nl = escape('\r'),
		tab = escape('        '),
		msgtext = msg.mesg + nl;
	
	for(i = 0; msg.hints && i < msg.hints.length; i++) {
		msgtext += tab + msg.hints[i] + nl;
	}
	msgtext += nl;
	msgtext += 'Host: ' + msg.host + nl;
	msgtext += 'Project: ' + msg.project + nl;
	msgtext += 'Survey: ' + msg.survey + nl;
	msgtext += 'FileName: ' + msg.fileName + nl;
	
	msgtext += nl + 'Attach template to email';
	
	return msgtext;
}


function projectSet() {
	var groups = globals.gLoggedInUser.groups,
		group,
		redirect = true,
		i;
	
	for (i = 0; i < groups.length; i++) {
		group = groups[i];
		if(group.name === "admin" || group.name === "analyst") {
			redirect = false;
			break;
		}
	}
	if(redirect) {
		window.location.href = "/webForm.html";
	}
	
	getSurveysForList(globals.gCurrentProject);			// Get surveys
}

/*
 * Load the surveys from the server and populate the survey table
 */
function getSurveysForList(projectId) {

	if(projectId != -1) {
		var url="/surveyKPI/surveys?projectId=" + projectId + "&blocked=true";
		
		if(globals.gIsAdministrator || globals.gIsAnalyst) {
			url+="&deleted=true";
		}
		
		addHourglass();
		$.ajax({
			url: url,
			dataType: 'json',
			cache: false,
			success: function(data) {
				gSurveys = data;
				setLocalTime();		// Convert timestamps from UTC to local time
				completeSurveyList();
				removeHourglass();
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
}

/*
 * Set any timestamps in the display name to local time
 */
function setLocalTime() {
	var i;
	
	for(i = 0; i < gSurveys.length; i++) {
		gSurveys[i].displayName = convertTimesToLocal(gSurveys[i].displayName);
	}
}

/*
 * Fill in the survey list
 */
function completeSurveyList() {

	gControlDelete = 0;
	gControlRestore = 0;
	$('#tem_controls').find('button').addClass("disabled");
	
	var $surveys = $('#survey_table'),
	i, survey,
	h = [],
	idx = -1;
	
	h[++idx] = '<table class="table" style="table-layout:fixed;">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th class="col-md-1"></th>';
	h[++idx] = '<th class="col-md-7">' + localise.set["c_name"], + '</th>';
	h[++idx] = '<th class="col-md-2">' + localise.set["c_block"] + '</th>';
	h[++idx] = '<th class="col-md-2">' + localise.set["c_download"] + '</th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody class="table-striped">';

	for(i = 0; i < gSurveys.length; i++) {
		survey = gSurveys[i];
		
		if(gShowDeleted || !survey.deleted) {
			h[++idx] = '<tr';
			if(survey.deleted) {
				h[++idx] = ' class="deleted"';
			} else if(survey.blocked) {
				h[++idx] = ' class="blocked"';
			}
			h[++idx] = '>';
			h[++idx] = '<td class="control_td"><input type="checkbox" name="controls" value="';
			h[++idx] = i;
			h[++idx] = '"></td>';
			h[++idx] = '<td class="displayName">';
			h[++idx] = '<a class="displayName" href="';
            if(survey.deleted) {
                h[++idx] = '#"';
            } else {
                h[++idx] = '/edit.html?id=';
                h[++idx] = survey.id;
                h[++idx] = '&name=';
                h[++idx] = encodeURI(survey.displayName);
                h[++idx] = '"';
			}
			h[++idx] = '><span style="word-wrap: break-word;">';
			h[++idx] = survey.displayName;
			h[++idx] = '</span></a></td>';
			h[++idx] = '<td class="control_block"><input type="checkbox" name="block" value="';
			h[++idx] = survey.id;
			h[++idx] = '" ';
			if(survey.blocked) {
				h[++idx] = 'checked="checked"';
			}
			h[++idx] = '></td>';
			h[++idx] = '<td>';
			h[++idx] = '<button class="btn pdf_td" value="';
			h[++idx] = survey.id;
			h[++idx] = '"><img src="images/downarrow.png" height="16" width="16"></button>';
			h[++idx] = '</td>';
			h[++idx] = '</tr>';	
		}
	}

	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
	
	$surveys.empty().append(h.join(''));
	
	$('.control_td').find('input').click(function() {

		if($(this).is(':checked')) {
			if(gSurveys[$(this).val()].deleted === false) {
				++gControlDelete;
			} else {
				++gControlRestore;
			}

			if(gControlDelete === 1) {
				$('#delete_survey').removeClass("disabled");
			}
			if(gControlRestore === 1) {
				$('#un_delete_survey').removeClass("disabled");
				$('#erase_survey').removeClass("disabled");
			}
		} else {

			if(gSurveys[$(this).val()].deleted === false) {
				--gControlDelete;
			} else {
				--gControlRestore;
			}
			if(gControlDelete === 0) {
				$('#delete_survey').addClass("disabled");
			}
			if(gControlRestore === 0) {
				$('#un_delete_survey').addClass("disabled");
				$('#erase_survey').addClass("disabled");
			}
		}
 
	});
	
	$('.control_block').find('input').click(function() {

		var $template,
			$this = $(this),
			id;
		
		$template = $this.closest('tr');
		id=$this.val();
		
		if($this.is(':checked')) {	
			$template.addClass('blocked');
			executeBlock(id, true);
		} else {
			$template.removeClass('blocked');
			executeBlock(id, false);
		}
 
	});
	
	$('.pdf_td').click(function(e) {
		var surveyIndex = $(this).closest('tr').find("[name='controls']").val(),
			surveyVersion = gSurveys[surveyIndex].version,
			loadedFromXLS = gSurveys[surveyIndex].loadedFromXLS;
		
		gSelectedTemplate = $(this).val();
		$.getJSON("/surveyKPI/languages/" + gSelectedTemplate, function(data) {

			var $languageSelect = $('#download_language');
			$languageSelect.empty();
			
			$.each(data, function(j, item) {
				$languageSelect.append('<option value="' + item + '">' + item + '</option>');
			});
		});
		gSelectedTemplateName = $(this).parent().siblings(".displayName").text();
		$('h4', '#download_template').html(localise.set["c_download"] + " " + gSelectedTemplateName);
		$('#dtversion').html(surveyVersion);
		if(loadedFromXLS) {
			$('#dtorigxls').show();
		} else {
			$('#dtorigxls').hide();
		}
		$('form', '#download_template')[0].reset();
		$('#download_language_div, #download_orientation_div').hide();
		$('#download_template').modal('show');
	});
	
	
}

/*
 * Permanently erase a survey
 */
function surveyErase() {
	
	var surveys = [],
		decision = false,
		h = [],
		i = -1,
		index = 0,
		surveyIdx;
	
	$('.control_td').find('input:checked').each(function() {
		surveyIdx = $(this).val();
		if(gSurveys[surveyIdx].deleted === true) {
			surveys[index++] = {id: gSurveys[surveyIdx].id, name: gSurveys[surveyIdx].displayName};
			h[++i] = gSurveys[surveyIdx].displayName;
		}
	});
	
	decision = confirm(localise.set["msg_erase"] + "\n" + h.join());	
	
	if (decision == true) {
		for(i = 0; i < surveys.length; i++) {
			deleteTemplate(surveys[i].id, surveys[i].name, true);
		}
	}
}

/*
 * Restore soft deleted surveys
 */
function surveyUnDelete() {
	
	var surveys = [],
		decision = false,
		h = [],
		i = -1,
		index = 0;

	$('.control_td').find('input:checked').each(function() {
		var surveyIdx = $(this).val();
		if(gSurveys[surveyIdx].deleted === true) {
			surveys[index++] = {id: gSurveys[surveyIdx].id, name: gSurveys[surveyIdx].displayName};
			h[++i] = gSurveys[surveyIdx].displayName;
		}
	});

	decision = confirm(localise.set["msg_restore"] + "\n" + h.join());

	if (decision == true) {
		for(i = 0; i < surveys.length; i++) {
			executeUnDelete(surveys[i].id, surveys[i].name);
		}
	}
}

/*
 * Soft delete surveys
 */
function surveyDelete() {
	
	var surveys = [],
		decision = false,
		h = [],
		i = -1,
		index = 0;

	$('.control_td').find(':checked').each(function() {
		var surveyIdx = $(this).val();
		if(gSurveys[surveyIdx].deleted === false) {
			surveys[index++] = {id: gSurveys[surveyIdx].id, name: gSurveys[surveyIdx].displayName};
			h[++i] = gSurveys[surveyIdx].displayName;
		}
	});

	decision = confirm(localise.set["msg_del_s"] + "\n" + h.join());

	if (decision == true) {
		for(i = 0; i < surveys.length; i++) {
			deleteTemplate(surveys[i].id, surveys[i].name, false);
		}
	}
}


function deleteTemplate(template, name, hard) {

	// Check for results associated with this template
	var resultCountURL = "/surveyKPI/survey/" + template + "/getMeta";
	
	if(hard) {
		addHourglass();
		$.ajax({
			type : 'Get',
			url : resultCountURL,
			dataType : 'json',
			cache: false,
			error : function() {
				removeHourglass();
				executeDelete(template, true, hard);	// Just delete as this is what the user has requested

			},
			success : function(response) {
				var totalRows = 0,
					msg, decision;
				
				removeHourglass();
	
				$.each(response.forms, function(index,value) {
					totalRows += value.rows;
				});
				
				if (totalRows == 0) {							
					executeDelete(template, true, hard);	// Delete survey template and data tables
				} else {
					msg = localise.set["msg_del_recs"];
					msg = msg.replace("%s1", totalRows);
					msg = msg.replace("%s2", name);
					
					decision = confirm(msg);
					if (decision == true) {
						executeDelete(template, true, hard);
					}
				}
			}
		});
	} else {
		// This is just a soft delete, no need to worry the user about data
		executeDelete(template, true, hard);
	}
}

// Delete the template
function executeDelete(template, delTables, hard) {
	
	var delURL = "/surveyKPI/survey/" + template;

	if(delTables) {
		delURL += "?tables=yes&delData=true&hard=" + hard;
	} else {
		delURL += "?hard=" + hard;
	}
	
	addHourglass();
	$.ajax({
		type : 'DELETE',
		url : delURL,
		cache: false,
		error : function() {
			removeHourglass();
			alert(localise.set["msg_err_del"]);
		},
		success : function() {
			removeHourglass();
			var projectId = $('#project_name option:selected').val();
			getSurveysForList(projectId);
		}
	});
}

/*
 * Un-Delete the template
 * TODO: Using DELETE to un-delete has to violate innumerable laws of REST!!!!
 * TODO: Presumably the use of DELETE to do a soft delete is also problematic
 */

function executeUnDelete(template) {
	
	var url = "/surveyKPI/survey/" + template + "?undelete=true";
	
	addHourglass();
	$.ajax({
		type : 'DELETE',
		url : url,
		cache: false,
		error : function() {
			removeHourglass();
			alert(localise.set["msg_err_res"]);
		},
		success : function() {
			var projectId = $('#project_name option:selected').val();
			getSurveysForList(projectId);
			removeHourglass();
		}
	});
}

//Block or ublock the template
function executeBlock(template, set) {
	
	var blockURL = "/surveyKPI/survey/" + template + "/block?set=" + set;
	
	addHourglass();	
	$.ajax({
		type : 'POST',
		url : blockURL,
		cache: false,
		error : function() {
			removeHourglass();
			alert(localise.set["msg_err_block"]);
		},
		success : function() {
			removeHourglass();
			var projectId = $('#project_name option:selected').val();
			getSurveysForList(projectId);
		}
	});
}


});	
