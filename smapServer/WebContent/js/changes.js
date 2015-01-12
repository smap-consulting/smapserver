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
        'jquery.autosize.min': ['jquery']
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
         'jquery.autosize.min'], 
		function($, common, bootstrap, modernizr, lang, ssc, globals, csv) {


var	gMode = "survey",
	gTempQuestions = [],
	gLanguage1 = 0,
	gLanguage2 = 0;

$(document).ready(function() {
	
	var i,
		params,
		pArray = [],
		param = [];
	
	localise.setlang();		// Localise HTML
	

	// Get the user details
	globals.gIsAdministrator = false;
	getLoggedInUser(surveyListDone, false, true, undefined, false, false);

	// Add menu functions
	$('#m_open').off().click(function() {	// Open an existing form
		$('#openFormModal').modal('show');
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
	$('#m_changes').off().click(function() {	// View the changes to this survey
		gMode = "changes";
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
function getSurvey() {

	var url="/surveyKPI/surveys/" + globals.gCurrentSurvey;
	console.log("Getting survey: " + globals.gCurrentSurvey);
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			globals.model.survey = data;
			globals.model.setSettings();
			console.log("Survey");
			console.log(data);
			setLanguages(data.languages);
			
			// Set the link to the media editor
			$('#m_media').attr("href", '/fieldManager/browseForms.jsp?id=' + globals.gCurrentSurvey + '&name=' + data.displayName);
			
			refreshView();
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get survey: " + err);
			}
		}
	});	
}


function setLanguages(languages) {
	
	var h = [],
		idx = -1,
		$lang = $('.language_list'),
		$lang1 = $('#language1'),
		$lang2 = $('#language2'),
		i;
	
	gLanguage1 = 0;	// Language indexes used for translations
	gLanguage2 = 0;
	if(languages.length > 1) {
		gLanguage2 = 1;
	}

	for (i = 0; i < languages.length; i++) {
		h[++idx] = '<option value="';
			h[++idx] = i;
			h[++idx] = '">';
			h[++idx] = languages[i];
		h[++idx] = '</option>';
	}
	$lang.empty().append(h.join(""));
	$lang1.val(gLanguage1);
	$lang2.val(gLanguage2)
}
*/

function refreshView() {
	
	var i,
		j,
		qList = [],
		index = -1,
		survey = globals.model.survey,
		numberLanguages,
		key,
		options = [];
	
	gTempQuestions = [];
	
	if(survey) {
		numberLanguages = survey.languages.length;
	}

	setChangesHtml($('#changes'), survey, survey);
}


/*
 * Convert change log JSON to html
 */
function setChangesHtml($element, survey, survey) {
	var h =[],
		idx = -1,
		i,
		changes;
	
	if(!survey) {
		$('#errormesg').html("<strong>No Changes</strong> Create or select a survey to see changes");
		$('#infobox').show();
	} else {

		changes = survey.changes;
		
		h[++idx] = '<table class="table table-striped">';
		
		// write the table headings
		h[++idx] = '<thead>';
			h[++idx] = '<tr>';
				h[++idx] = '<th>Version</th>';
				h[++idx] = '<th>Change</th>';
				h[++idx] = '<th>Changed By</th>';
				h[++idx] = '<th>When changed</th>';
			h[++idx] = '</tr>';
		h[++idx] = '</thead>';
		
		// Write the table body
		h[++idx] = '<body>';
		for(i = 0; i < changes.length; i++) {
			
			h[++idx] = '<tr>';
				h[++idx] = '<td>';
				h[++idx] = changes[i].version;
				h[++idx] = '</td>';	
				h[++idx] = '<td>';
				h[++idx] = getChangeDescription(changes[i]);
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = changes[i].userName;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = changes[i].updatedTime;
				h[++idx] = '</td>';
			h[++idx] = '</tr>';
		}
		h[++idx] = '</body>';
		
		h[++idx] = '</table>';
	} 
	
	$element.html(h.join(''));
	
	
}

function getChangeDescription(change) {
	
	var h =[],
		idx = -1;
	
	if(change.changeType && change.changeType === "option_update") {
		h[++idx] = 'Choice <span style="color:blue;">'; 
		h[++idx] = change.newVal;
		h[++idx] = '</span>';
		h[++idx] = ' added to';
		h[++idx] = ' question: <span style="color:blue;">';
		h[++idx] = change.name;
		h[++idx] = '</span>';
		h[++idx] = ' from file: <span style="color:blue;">';
		h[++idx] = change.fileName;
		h[++idx] = '</span>';
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

});