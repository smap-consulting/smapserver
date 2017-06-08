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

along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

*/

/*
 * Manage Queries
 * Each Query has:
 *     name
 *     queryDetails
 * Query Details
 */
var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

"use strict";
require.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: 'jquery-2.1.1',
    	bootbox: 'bootbox.min',
    	toggle: 'bootstrap-toggle.min',
    	moment: 'moment-with-locales.min',
    	lang_location: '..'

    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery'],
        'bootstrap.file-input': ['bootstrap.min'],
    	'bootbox': ['bootstrap.min'],
       	'toggle': ['bootstrap.min']
        
    }
});

require([
         'jquery',
         'app/common', 
         'bootstrap.min', 
         'app/localise',
         'app/globals',
         'jquery.autosize.min',
         'bootstrap.file-input',
         'bootbox',
         'toggle',
         'moment',
         'app/surveyCache',
         'app/formCache'], 
		function(
				$, 
				common, 
				bootstrap, 
				lang, 
				globals, 
				jqas, 
				bsfi, 
				bootbox,
				toggle,
				moment,
				surveyCache,
				formCache) {



window.moment = moment;

var gQueries,
	gCurrentQuery,
	gCurrentFormNewSurvey;

'use strict';

$(document).ready(function() {
 
	localise.setlang();		// Localise HTML
	
	loadQueries();
	
	globals.gIsAdministrator = false;
	getLoggedInUser(getSurveys, false, true, undefined, false, false);  // TODO set callback
	
	// Project changes update surveys and forms
	$('#new_project').change(function() {
		getSurveys();
	});
	
	// Survey changes - update forms
	$('#new_survey').change(function() {
		getForms();
	});
	
	/*
	 * Add a new query
	 */
	$('#addQuery').click(function(){
		
		$('.panel_msg').hide();
		
		gCurrentQuery = {
				id: -1,				// New query
				name: undefined
		};
		
		//$('#queryEditForm')[0].reset();
		$('#query_name').val("");
		
		$('.querycontent,.querytoolbar').toggle();
	});
	
	/*
	 * Save a query
	 */
	$('#saveQuery').click(function(){

		$('.panel_msg').hide();
		
		// Get the values
		gCurrentQuery.name = $('#query_name').val();
		
		// Validate
		if(!gCurrentQuery.name || gCurrentQuery.name.trim().length == 0) {
			$('.panel_msg').show().addClass('alert-danger').removeClass('alert-success').html(localise.set["msg_val_nm"]);
			return;
		}
		
		var queryString = JSON.stringify(gCurrentQuery);
		addHourglass();
		$.ajax({
			  type: "POST",
			  url: "/surveyKPI/query",
			  dataType: 'json',
			  cache: false,
			  data: { query: queryString },
				success: function(data) {
					removeHourglass();
					$('.panel_msg').show().addClass('alert-success').removeClass('alert-danger').html(localise.set["c_success"]);
					gQueries = data;
					showQueries();
					$('.querycontent,.querytoolbar').toggle();
				},
				error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						$('.panel_msg').show().addClass('alert-danger').removeClass('alert-success').html(localise.set["msg_err_save"]);
					}
				}
		});
	
	});
	
	$('#cancelQuery').click(function(){
		$('.querycontent,.querytoolbar').toggle();
		$('.panel_msg').hide();
	});
	
	
	
	/*
	 * Add a form to a query
	 */
	$('#addForm').click(function(){
		setAddNewForm();
		$('#add_form_popup').modal("show");
	});

	/*
	 * Change the addNewSurvey checkbox
	 */
	$('#add_new_survey').change(function() {
		var $this = $(this);
		addNewSurveyChanged($this.prop('checked'));
			
	});
	
	/*
	 * Save a new form
	 * Rules:  
	 *   1) Each new form must produce only a single record for each value of the existing form it is attached to
	 *   	a) For forms in the same survey this is established through the parent hierarchy
	 *   	b) For forms in a different survey this can be tested against data - maybe have a validate query option
	 *   2) When you add a form into an existing survey the links need to be recalculated
	 *   	For example a -> b -> c  a is is added first, then c, when b is added the links are rechecked
	 * 
	 */
	$('#addFormSave').click(function() {
		var form = {},
			valid = true,
			i,
			msg;
		
		form.project = $('#new_project').val();
		form.project_name = $('#new_project option:selected').text();
		
		form.survey = $('#new_survey').val();
		form.survey_name = $('#new_survey option:selected').text();
		
		form.form = $('#new_form').val();
		form.form_name = $('#new_form option:selected').text();
		
		form.question = $('#new_question').val();
		
		// If this is a new survey for the query then add the linking data
		if(gCurrentFormNewSurvey) {
			form.link_project = $('#link_project').val();
			form.link_survey = $('#link_survey').val();
			form.link_form = $('#elink_form').val();
			form.link_question = $('#link_question').val();
		}
		
		if(!gCurrentQuery.forms) {
			gCurrentQuery.forms = [];
		}
		
		if(valid === true) {
			if(gCurrentQuery.forms.length > 0) {
				for(i = 0; i < gCurrentQuery.forms.length; i++) {
					// validate that the form has not already been added
					if(gCurrentQuery.forms[i].form === form.form) {
						valid = false;
						msg = localise.set["msg_dup_f"];
						break;
					}
				}
			}
		}
		
		// Show an error if validation failed
		if(!valid) {
			$('.add_form_msg').show().addClass('alert-danger').removeClass('alert-success').html(msg);
			return;
		}
		
		
		

		gCurrentQuery.forms.push(form);
		updateFormEditor();
		$('#add_form_popup').modal("hide");
	});
	
});

/*
 * Set the addNewSurvey checkbox to its initial value
 */
function setAddNewForm() {
	
	var on = $('#add_new_survey').prop('checked', on);
	
	//if(!gCurrentQuery.forms  || gCurrentQuery.forms.length == 0) {
	//	on = true;
	//} else {
		// TODO set on if all forms in the survey list have been selected
	//}
	
	gCurrentFormNewSurvey = false;
	$('.add_form_msg').hide();
	addNewSurveyChanged(on);
}

/*
 * Enable / Disable elements based on the value of addNewSurvey
 */
function addNewSurveyChanged(isSet) {
	if(isSet) {
		if(gCurrentQuery.forms && gCurrentQuery.forms.length > 0) {
			$('.new_survey_only').show();
		} else {
			$('.new_survey_only').hide();
		}	
	} else {
		$('.new_survey_only').hide();
	}
}
/*
 * Load surveys for the current project
 */
function getSurveys() {	
	var projectId = $('#new_project').val();
	surveyCache.get(projectId, showSurveys);
}

function showSurveys(surveyList) {
	var $elem = $('.survey_select'),
		h = [],
		idx = -1,
		i;
	
	$elem.empty();
	
	for(i = 0; i < surveyList.length; i++) {
		var item = surveyList[i];
		h[++idx] = '<option value="';
		h[++idx] = item.id;
		h[++idx] = '">';
		h[++idx] = item.displayName;
		h[++idx] = '</option>';
	}

	$elem.empty().append(h.join(''));
	getForms();
}

/*
 * Load forms for the current survey
 */
function getForms() {
	var surveyId = $('#new_survey').val();
	formCache.get(surveyId, showForms);
}

function showForms(formList) {
	var $elem = $('.form_select'),
		h = [],
		idx = -1,
		i;

	$elem.empty();

	for(i = 0; i < formList.length; i++) {
		var item = formList[i];
		h[++idx] = '<option value="';
		h[++idx] = item.f_id;
		h[++idx] = '">';
		h[++idx] = item.form;
		h[++idx] = '</option>';
	}
	
	$elem.empty().append(h.join(''));
}

/*
 * Get queries and update the query lists on this page
 */	
function loadQueries() {
 	
 	var url="/surveyKPI/query";

 	addHourglass();

	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
 				
			removeHourglass();
			gQueries = data;
			showQueries();

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
 * Save the queries and then update the query lists on this page
 */	
function saveQuery() {
 	
 	var url="/surveyKPI/query",
 		queryString = JSON.stringify(gCurrentQuery);

 	addHourglass();

	$.ajax({
		url: url,
		type: "POST",
		cache: false,
		dataType: 'json',
		data: { query: queryString },
		success: function(data) {	
			removeHourglass();
			gQueries = data;
			showQueries();

		}, error: function(xhr, textStatus, err) {
 				
 			removeHourglass();
 			if(xhr.readyState == 0 || xhr.status == 0) {
 				return;  // Not an error
 			} else {
 				alert("Error: Failed to save queries: " + err);
 			}
 		}
 	});	 
 }

function showQueries() {
	var i,
		item,
		h = [],
		idx = -1,
		$elem = $('#queryListTable');
	
	h[++idx] = '<table class="table">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th>' + localise.set["c_name"], + '</th>';
	h[++idx] = '<th>' + localise.set["c_desc"] + '</th>';
	h[++idx] = '<th></th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody class="table-striped">';
	
	if(gQueries.length > 0) {
		for(i = 0; i < gQueries.length; i++) {
			item = gQueries[i];

			h[++idx] = '<tr>';
				h[++idx] = '<td>';
				h[++idx] = item.name;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = item.query;	// TODO replace with a human readable description
				h[++idx] = '</td>';
				
				// actions
 				h[++idx] = '<td>';
 				
 				h[++idx] = '<button type="button" data-idx="';
 				h[++idx] = i;
 				h[++idx] = '" class="btn btn-default btn-sm edit_link btn-info">';
 				h[++idx] = '<span class="glyphicon glyphicon-edit" aria-hidden="true"></span></button>';
 				
 				h[++idx] = '</td>';
 				// end actions
			h[++idx] = '</tr>';
			
		}
	} else {
		$('.panel_msg').show().removeClass('alert-danger').addClass('alert-success').html(localise.set["msg_nq"]);
	}

	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
			

	$elem.empty().html(h.join(''));
	
	// Add response to clicking on the edit button
	$(".edit_link", $elem).click(function(){
		var idx = $(this).data("idx");
		
		gCurrentQuery = jQuery.extend(true, {}, gQueries[idx]);

		updateFormEditor();

		$('.querycontent,.querytoolbar').toggle();
	});

		
}

function updateFormEditor() {
	
	var i,
		idx = -1,
		h = [],
		needSep = false,
		sep = ", "
	
	$('#queryEditForm')[0].reset();
	$('#query_name').val(gCurrentQuery.name);
	
	if(gCurrentQuery.forms) {
		for(i = 0; i < gCurrentQuery.forms.length; i++) {
			h[++idx] = '<tr>';
				needSep = false;
				h[++idx] = '<td>';	// Project / survey / form
					if(gCurrentQuery.forms[i].project) {
						h[++idx] =  gCurrentQuery.forms[i].project_name;
						needSep = true;
					}
					if(gCurrentQuery.forms[i].survey) {
						if(needSep) {
							h[++idx] = sep;
						}
						h[++idx] = gCurrentQuery.forms[i].survey_name;
						needSep = true;
					}
					if(gCurrentQuery.forms[i].form) {
						if(needSep) {
							h[++idx] = sep;
						}
						h[++idx] =  gCurrentQuery.forms[i].form_name;
						needSep = true;
					}
				h[++idx] = '</td>';
				h[++idx] = '<td></td>';				// Question
				h[++idx] = '<td></td>';				// Link project / survey / form
				h[++idx] = '<td></td>';				// Link Question
				h[++idx] = '<td><button value="';	// Delete button
				h[++idx] = i;
				h[++idx] = '" class="form_del btn btn-danger"><span class="glyphicon glyphicon-trash" aria-hidden="true"></span> </button></td>';		// Link Question
				
			h[++idx] = '</tr>';
		}
		$('#queryTable tbody').html(h.join(''));
		$('#queryTable .form_del').click(function(){
			gCurrentQuery.forms.splice($(this).val(), 1);
			updateFormEditor();
		});
	}
	
}

});
