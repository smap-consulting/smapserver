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

"use strict";
require.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min',
    	lang_location: '..'
    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery'],
        'jquery-drag-ui.min': ['jquery']
    }
});

require([
         'jquery',
         'app/common', 
         'bootstrap.min', 
         'modernizr',
         'app/localise',
         'app/ssc',
         'app/globals',
         'app/csv',
         'jquery-drag-ui.min',
         'jquery.autosize.min'], 
		function($, common, bootstrap, modernizr, lang, ssc, globals, csv, jquery_ui) {


var	gMode = "survey",
	gTempQuestions = [],
	gLanguage1 = 0,
	gLanguage2 = 0,
	gIndex = 0;			// Unique index to each question

$(document).ready(function() {
	
	var i,
		params,
		pArray = [],
		param = [],
		dont_get_current_survey = true;
	
	localise.setlang();		// Localise HTML
	
	// Get the parameters and start editing a survey if one was passed as a parameter
	params = location.search.substr(location.search.indexOf("?") + 1)
	pArray = params.split("&");
	for (i = 0; i < pArray.length; i++) {
		param = pArray[i].split("=");
		console.log("param:" + param[0] +":");
		if ( param[0] === "id" ) {
			globals.gCurrentSurvey = param[1];
			saveCurrentProject(-1, globals.gCurrentSurvey);	// Save the current survey id
			console.log("Passed in survey is: " + globals.gCurrentSurvey);
		}
	}
	
	// Get the user details
	globals.gIsAdministrator = false;
	if(globals.gCurrentSurvey > 0) {
		dont_get_current_survey = true;		// The current survey was passed in the parameters
	} else {
		dont_get_current_survey = false;		// The current survey was passed in the parameters
	}
	getLoggedInUser(getSurveyList, false, true, undefined, false, dont_get_current_survey);

	/*
	 * Refresh the view when the selected property changes
	 */
	$('#selProperty').change(function() {
		refreshView();
	});
	
	// Add menu functions
	$('#m_open').off().click(function() {	// Open an existing form
		$('#openFormModal').modal('show');
	});
	$('.m_save_survey').off().click(function() {	// Save a survey to Smap
		globals.model.save();
		getSurveyDetails(refreshView);
	});

	// Add menu functions
	$('#m_simple_edit').off().click(function() {	// Edit a survey
		gMode = "simple_edit";
		refreshView(gMode);
	});
	$('#m_translate').off().click(function() {	// Translate languages
		gMode = "translate";
		refreshView(gMode);
	});
	$('#m_settings').off().click(function() {	// Get a survey from Smap
		gMode = "settings";
		refreshView(gMode);
	});

	$('#m_undo').off().click(function() {	// Undo last change
		globals.model.undo();
		refreshView(gMode);
	});
	$('#m_redo').off().click(function() {	// Redo last change
		globals.model.redo();
		refreshView(gMode);
	});
	$('#save_settings').off().click(function() {	// Save settings to Smap
		globals.model.save_settings();
	});
	
	// Add responses to events
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
		getSurveyList();
 	 });
	
	$('#get_survey').off().click(function() {
		globals.gCurrentSurvey = $('#survey_name option:selected').val();
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current survey id
		getSurveyDetails();
		$('#smap').foundation('reveal', 'close');
 	 });
	
	$('.language_list').off().change(function() {
		gLanguage1 = $('#language1').val();
		gLanguage2 = $('#language2').val();
		refreshView(gMode);
		//$('#set_language').foundation('reveal', 'close');
 	 });
	
	// Check for changes in settings
	$('#set_survey_name').keyup(function(){
		globals.model.settingsChange();
	});
	$('#set_project_name').change(function() {
		saveCurrentProject($('#set_project_name option:selected').val(), globals.gCurrentSurvey);	// Save the current project id
		globals.model.settingsChange();
	});
	$('#set_default_language').change(function() {
		globals.model.settingsChange();
	});

	// Check for selection of the label indicating successful updates and the one indicating failed
	$('#successLabel').off().click(function() {
		alert("success");
	});
	// Check for selection of the label indicating successful updates and the one indicating failed
	$('#failedLabel').off().click(function() {
		alert("failed");
	});
	
	
	enableUserProfileBS();
});

function enableDragablePanels() {
	var panelList = $('#formList');

    panelList.sortable({
        // Only make the .panel-heading child elements support dragging.
        // Omit this to make the entire <li>...</li> draggable.
        handle: '.panel-heading', 
        update: function() {
            $('.panel', panelList).each(function(index, elem) {
                 var $listItem = $(elem),
                     newIndex = $listItem.index();

                 // Persist the new indices.
            });
        }
    });
}

function getSurveyList() {
	console.log("getSurveyList: " + globals.gCurrentSurvey);
	if(globals.gCurrentSurvey > 0) {
		loadSurveys(globals.gCurrentProject, undefined, false, false, surveyListDone);
	} else {
		loadSurveys(globals.gCurrentProject, undefined, false, false, undefined);
	}
}


function surveyListDone() {
	getSurveyDetails(refreshView);
}


/*
 * Show the form on the screen
 */
function refreshView() {
	
	var i,
		survey = globals.model.survey,
		key,
		h = [],
		idx = -1;
	
	
	if(survey) {
		numberLanguages = survey.languages.length;
		
		if(survey.forms && survey.forms.length > 0) {
			h[++idx] = addQuestions(survey.forms[0]);
		}
	}
	$('#formList').html(h.join(""));
	//enableDragablePanels();
}

/*
 * Add the questions for a form
 */
function addQuestions(form) {
	var i,
		question,
		h = [],
		idx = -1;
	
	if(form) {
		for(i = 0; i < form.questions.length; i++) {
			question = form.questions[i];
			console.log(question);
			// Ignore the following questions
			if(question.name === '_task_key' || 
					question.name === 'instanceID' || 
					question.name === 'meta' || 
					question.name === 'meta_groupEnd' ||
					question.name.indexOf('_groupEnd') > 0) {
				continue;
			}
			h[++idx] = addOneQuestion(question);
		}
	}
	return h.join("");
}

function addOneQuestion(question) {
	var h = [],
		idx = -1;
	
	h[++idx] = addPanelStyle(question.type);
		h[++idx] = '<div class="panel-heading">';
			//h[++idx] = '<div class="container">';
			//	h[++idx] = '<div class="row">';
				h[++idx] = '<table class="table">';
					//h[++idx] = '<div class="col-sm-2 col-xs-4 head1">';
					h[++idx] = '<td class="q_type_col">';
						h[++idx] = addQType(question.type);
					h[++idx] = '</td>';
					//h[++idx] = '<div class="col-sm-3 col-xs-8 head2"><input class="qname" value="';
					h[++idx] = '<td class="q_name_col"><input class="qname form-control" value="';
					h[++idx] = question.name;
					h[++idx] = '" type="text"></td>';
					h[++idx] = addFeaturedProperty(question);
					h[++idx] = '<td class="q_icons_col">';
						h[++idx] = '<span class="glyphicon glyphicon-trash edit_icon1"></span>';
						h[++idx] = '<a data-toggle="collapse"  href="#collapse';
						h[++idx] = ++gIndex;
						h[++idx]='"><span class="glyphicon glyphicon-collapse-down edit_icon2"></span></a>';
				h[++idx] = '</td>';
				h[++idx] = '</table>';
			//h[++idx] = '</div>';
		//h[++idx] = '</div>';
		h[++idx] = '<div id="collapse';
		h[++idx] = gIndex;
		h[++idx] = '" class="panel-body collapse">';
		if(question.type === "begin repeat") {
			h[++idx] = addSubForm(question);
		} else if(question.type.indexOf("select") === 0) {
			h[++idx] = addOptions(question);
		}
		h[++idx] = '</div>';
	h[++idx] = '</li>';
	
	return h.join("");
}

function addPanelStyle(type) {
	
	if(type === "begin repeat" || type === "begin group") {
		return '<li class="panel panel-warning">';
	} else {
		return '<li class="panel panel-success">';		
	}
}

function addQType(type) {
	if(type === "string") {
		return '<span class="glyphicon glyphicon-font edit_type"></span>';	
	} else if(type === "select1") {
		return '<img class="edit_image" src="/images/select1_64.png">';
	} else if(type === "select") {
		return '<img class="edit_image" src="/images/select_64.png">';
	} else if(type === "begin repeat") {
		return '<span class="glyphicon glyphicon-repeat edit_type"></span>';
	} else if(type === "begin group") {
		return '<span class="glyphicon glyphicon-folder-open edit_type"></span>';
	} else if(type === "image") {
		return '<span class="glyphicon glyphicon-camera edit_type"></span>';
	} else if(type === "audio") {
		return '<span class="glyphicon glyphicon-volume-up edit_type"></span>';
	} else if(type === "video") {
		return '<span class="glyphicon glyphicon-facetime-video edit_type"></span>';
	} else if(type === "geopoint") {
		return '<span class="glyphicon glyphicon-map-marker edit_type"></span>';
	} else if(type === "dateTime" || type === "date") {
		return '<span class="glyphicon glyphicon-calendar edit_type"></span>';
	} else if(type === "time") {
		return '<span class="glyphicon glyphicon-time edit_type"></span>';
	} else if(type === "barcode") {
		return '<span class="glyphicon glyphicon-barcode edit_type"></span>';
	}  else if(type === "int") {
		return '<span class="edit_type">#</span>';
	} else if(type === "decimal") {
		return '<span class="edit_type">#.#</span>';
	} else if(type === "geolinestring") {
		return '<img class="edit_image" src="/images/linestring_64.png">';
	} else if(type === "geopolygon") {
		return '<img class="edit_image" src="/images/polygon_64.png">';
	}else {
		return '<span class="glyphicon glyphicon-record edit_type"></span>';
	}
}

/*
 * One of the questions properties will be featured so that it can be edited in the header without expanding the question
 */
function addFeaturedProperty(question) {
	
	var h = [];
		idx = -1,
		selProperty = $('#selProperty').val(),
		mediaButtonMarkup =  '<p><a href="#" class="btn btn-primary" role="button">Add</a> <a href="#" class="btn btn-danger" role="button">Delete</a></p>';
		
		
	h[++idx] = '<td class="q_label_col">';
	if(selProperty === "label") {
		h[++idx] = '<textarea class="surveytitleinput" placeholder="Label">';
		h[++idx] = question.labels[gLanguage1].text;
		h[++idx] = '</textarea>';
	} else if(selProperty === "media") {
		h[++idx] = '<div class="row">';
			h[++idx] = '<div class="col-sm-3">';
				h[++idx] = '<a href="#" class="thumbnail">';
				h[++idx] = '<img height="100" width="100" src="/images/fieldTaskBigLogo.jpg">';
				h[++idx] = '</a>';
			    h[++idx] = '<div class="caption">';
		        h[++idx] = '<h3 class="text-center">Image</h3>';
		        h[++idx] = '</div>';
		    h[++idx] = '</div>';		        
			h[++idx] = '<div class="col-sm-3">';
			    h[++idx] = '<a href="#" class="thumbnail">';
			    h[++idx] = '<img height="100" width="100" src="/images/su_logo.png">';
			    h[++idx] = '</a>';
			    h[++idx] = '<div class="caption">';
		        h[++idx] = '<h3 class="text-center">Video</h3>';
		        h[++idx] = '</div>';
			h[++idx] = '</div>';
			h[++idx] = '<div class="col-sm-3">';
			    h[++idx] = '<a href="#" class="thumbnail">';
			    h[++idx] = '<img height="100" width="100" src="/images/su_logo.png">';
			    h[++idx] = '</a>';
			    h[++idx] = '<div class="caption">';
		        h[++idx] = '<h3 class="text-center">Audio</h3>';
		        h[++idx] = '</div>';
		        h[++idx] = '</div>';
			h[++idx] = '</div>';
	    h[++idx] = '</div>';
	    
	}
	h[++idx] = '</td>';
	return h.join('');
}

/*
 * Add subform
 */
function addSubForm(question) {
	
	var h = [],
		idx = -1,
		formName,
		survey = globals.model.survey,
		forms = [],
		i,
		form;
	
	h[++idx] = '<ol class="list-unstyled">';
	
	// Get the form
	formName = question.name;
	forms = survey.forms;
	for(i = 0; i < forms.length; i++) {
		form = forms[i];
		if(forms[i].name === formName) {
			h[++idx] = addQuestions(forms[i]);
			break;
		}
	}
	
	h[++idx] = '</ol>';
	
	return h.join("");
}

/*
 * Show the options
 */
function addOptions(question) {
	var survey = globals.model.survey,
		options = survey.optionLists[question.list_name],
		h = [],
		idx = -1,
		i;
	
	console.log("options to write");
	console.log(options);
	
	if(options) {
		for(i = 0; i < options.length; i++) {
			h[++idx] = addOneOption(options[i]);
		}
	}
	return h.join("");
}

/*
 * Add a single option
 */
function addOneOption(option) {
	var h = [],
	idx = -1;

	h[++idx] = '<table class="table">';
				h[++idx] = '<td class="q_name_col"><input class="qname form-control" value="';
				h[++idx] = option.value;
				h[++idx] = '" type="text"></td>';
				h[++idx] = addFeaturedProperty(option);
				h[++idx] = '<td class="q_icons_col">';
					h[++idx] = '<span class="glyphicon glyphicon-trash edit_icon1"></span>';
				h[++idx] = '</td>';
			h[++idx] = '</table>';
		


return h.join("");
}
});