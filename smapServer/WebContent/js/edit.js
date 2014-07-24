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

require.config({
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min',
    	lang_location: '..'
    },
    shim: {
    	'mustache': {
    		exports: 'Mustache'
        },
    	'app/common': ['jquery'],
        'foundation.min': ['jquery']
    }
});

require([
         'jquery',
         'app/common', 
         'foundation.min', 
         'mustache', 
         'modernizr',
         'app/localise',
         'app/globals'], 
		function($, common, foundation, Mustache, modernizr, lang, globals) {


var	gQuestions,
	gCurrentLang = 0,	// Each language is in an array, this determines the array element that will be shown
	gMode = "translate",
	gTempQuestions = [],
	gLanguage1 = 0,
	gLanguage2 = 0;

$(document).ready(function() {
	
	var i,
		params,
		pArray = [],
		param = [];
		
	// Get projects and surveys
	getMyProjects(-1, getSurveyList, false);
	
	// Add menu functions
	$('#m_get_survey').off().click(function() {	// Get a survey from Smap
		$('#smap').foundation('reveal', 'open');
	});
	$('#m_simple_edit').off().click(function() {	// Edit a survey
		gMode = "simple_edit";
		refreshView(gMode);
	});
	$('#m_translate').off().click(function() {	// Translate languages
		gMode = "translate";
		refreshView(gMode);
	});
	
	// Add responses to events
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		saveCurrentProject(globals.gCurrentProject);	// Save the current project id
		getSurveyList();
 	 });
	
	$('#get_survey').off().click(function() {
		getSurvey($('#survey_name option:selected').val());
		$('#smap').foundation('reveal', 'close');
 	 });
	
	$(document).foundation();		// Add foundation styling
	
	// Get the parameters and start editing a survey if one was passed as a parameter
	params = location.search.substr(location.search.indexOf("?") + 1)
	pArray = params.split("&");
	for (i = 0; i < pArray.length; i++) {
		param = pArray[i].split("=");
		console.log("param:" + param[0] +":");
		if ( param[0] === "id" ) {
			globals.gCurrentSurvey = param[1];
			console.log("Get survey: " + globals.gCurrentSurvey);
			getSurvey(globals.gCurrentSurvey);
		}
	}

	
});

function getSurveyList() {
	loadSurveys(globals.gCurrentProject, undefined, false, false, undefined);
}


function getSurvey(sId) {

	var url="/surveyKPI/surveys/" + sId;
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			globals.model.set(data);
			console.log("Survey");
			console.log(data);
			gQuestions = {questions: data.forms[0].questions};
			gCurrentLanguage = data.languages.indexOf(data.def_lang);	// Set the initial language to the default language
			gLanguage1 = 0;	// Language indexes used for translations
			gLanguage2 = 0;
			if(data.languages.length > 1) {
				gLanguage2 = 1;
			}
			refreshView(gMode);
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

function refreshView(mode) {
	
	var i,
		j,
		qList = [],
		index = -1,
		survey = globals.model.getSurvey(),
		numberLanguages = survey.languages.length;
	
	
	// Modify Template to reflect view parameters
	
	if(mode === "simple_edit") {
		$('#questions').html(Mustache.to_html( $('#tpl').html(), gQuestions));
		$('#questions select').each(function(){
			$(this).val($(this).attr("data-sel"));
		});
	} else if(mode === "translate") {
		
		// Add all unique questions / options from all surveys
		for(i = 0; i < survey.forms.length; i++) {
			console.log("Form name: " + survey.forms[i].name);
			var formQuestions = survey.forms[i].questions; 
			for(j = 0; j < formQuestions.length; j++) {
				if(formQuestions[j].labels[gLanguage1].text) {
					if($.inArray(formQuestions[j].labels[gLanguage1].text, qList) > -1) {
						// TODO update indexes
					} else {
						qList.push(formQuestions[j].labels[gLanguage1].text);
						gTempQuestions.push({
							label_a: formQuestions[j].labels[gLanguage1].text,
							label_b: formQuestions[j].labels[gLanguage2].text,
							indexes: [{
								form: i,
								question: j
							}]
						});
					}
				}
			}
		}

		$('#questions').html(getTranslateHtml(gTempQuestions, survey));
		$(".lang_b").first().focus();
		$(".lang_b").change(function(){
			alert("hi you have changed: " + $(this).data("index"));
		});

		
	} else {
		alert("unknown mode");
		refreshView("simple_edit");
	}
	$('#questions').foundation();
	
}


/*
 * Convert JSON to html
 * Mustache is preferred however once you start needing helper functions this is more 
 * straightforward
 */
function getTranslateHtml(questions, survey) {
	var h =[],
		idx = -1,
		i;
	
	for(i = 0; i < questions.length; i++) {
		h[++idx] = '<div class="row">';
			h[++idx] = '<div class="small-6 large-6 columns">';
				h[++idx] = '<input type="text"  class="lang_a" tabindex="-1" readonly value="';
					h[++idx] = questions[i].label_a;
				h[++idx] = '">';
			h[++idx] = '</div>';
			h[++idx] = '<div class="small-6 large-6 columns">';
				h[++idx] = '<input type="text"  class="lang_b" value="';
					h[++idx] = questions[i].label_b;
					h[++idx] = '" ';
					h[++idx] = 'tabindex="';
					h[++idx] = i;
					h[++idx] = '" ';
					h[++idx] = 'data-index="';
					h[++idx] = i;
				h[++idx] = '">';
			h[++idx] = '</div>';	
		h[++idx] = '</div>';
	}
	
	return h.join('');
}
});
