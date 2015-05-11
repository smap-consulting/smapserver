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
         'jquery.autosize.min'], 
		function($, common, bootstrap, modernizr, lang, ssc, globals) {


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
	getLoggedInUser(getSurveyList, false, true, undefined, false, false);
	
	// Add menu functions
	$('#m_open').off().click(function() {	// Open an existing form
		$('#openFormModal').modal('show');
	});
	
	$('.save_form').off().click(function() {	// Save a survey to Smap
		globals.model.save();
		getSurveyDetails();
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
	
	// Add responses to events
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
		getSurveyList();
 	 });
	
	$('.language_list').off().change(function() {
		gLanguage1 = $('#language1').val();
		gLanguage2 = $('#language2').val();
		refreshView(gMode);
		//$('#set_language').foundation('reveal', 'close');
 	 });
	

	// Check for selection of the label indicating successful updates and the one indicating failed
	$('#successLabel').off().click(function() {
		alert("success");
	});
	// Check for selection of the label indicating successful updates and the one indicating failed
	$('#failedLabel').off().click(function() {
		alert("failed");
	});
	
    /*
     * Open a new form
     */
	$('#get_form').off().click(function() {
		globals.gCurrentSurvey = $('#form_name option:selected').val();
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current survey id
		getSurveyDetails(refreshView);
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
	
	// Set the display name
	$('.formName').html(survey.displayName);
			
	// Add all unique questions from all forms
	for(i = 0; i < survey.forms.length; i++) {
		console.log("Form name: " + survey.forms[i].name);
		var formQuestions = survey.forms[i].questions; 
		for(j = 0; j < formQuestions.length; j++) {
			
			if(formQuestions[j].labels[gLanguage1].text) {
				if((index = $.inArray(formQuestions[j].labels[gLanguage1].text, qList)) > -1) {
					console.log(formQuestions[j].labels[gLanguage1].text);
					gTempQuestions[index].indexes.push({
						form: i,
						question: j
					});
					console.log(gTempQuestions[index]);
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
	// Add all unique options from all option lists
	for(key in survey.optionLists) {
		console.log("Option list: " + key);
		var options = survey.optionLists[key].options; 
		for(j = 0; j < options.length; j++) {
			
			console.log("Option:" + options[j]);

			if(options[j].labels[gLanguage1].text) {
				if((index = $.inArray(options[j].labels[gLanguage1].text, qList)) > -1) {
					console.log(options[j].labels[gLanguage1].text);
					gTempQuestions[index].indexes.push({
						optionList: key,
						option: j
					});
					console.log(gTempQuestions[index]);
				} else {
					qList.push(options[j].labels[gLanguage1].text);
					gTempQuestions.push({
						label_a: options[j].labels[gLanguage1].text,
						label_b: options[j].labels[gLanguage2].text,
						indexes: [{
							optionList: key,
							option: j
						}]
					});
				}
			}
		}
	}
	qList = [];		// clear temporary question list	

	setTranslateHtml($('#translate .questions'), gTempQuestions, survey);
	var questionsedited="0";
	$(".lang_b").first().focus();
	$(".lang_b").change(function(){
		event.preventDefault();
		var $this = $(this);
		var index = $this.data("index");
		var newVal = $this.val();
		console.log(gTempQuestions[index]);
		console.log("New val:" + newVal);
		globals.model.modLabel(gLanguage2, gTempQuestions[index].indexes, newVal, "text", "label");
		$('.qcount').empty().append('Translations made: ' + (++questionsedited))
	});

	
}

/*
 * Convert JSON to html
 * Mustache is preferred however once you start needing helper functions this is more 
 * straightforward
 */
function setTranslateHtml($element, questions, survey) {
	var h =[],
		idx = -1,
		i;
	
	h[++idx] = '<div class="ribbon">';
		h[++idx] = '<div class="large-12 columns">';
			h[++idx] = '<h1 class="pagetitle"> Translating Survey:<span class="thick">';
			h[++idx] = survey.displayName;
			h[++idx] = '</span></h1>';
		h[++idx] = '</div>';
	h[++idx] = '</div>';
	

	for(i = 0; i < questions.length; i++) {
		h[++idx] = '<div class="fullest-width row center ribbonwrapper">';
			h[++idx] = '<div class="ribbon">';
				h[++idx] = '<div class="small-12 medium-6 columns">';
					h[++idx] = '<textarea class="lang_a" tabindex="-1" readonly>';
						h[++idx] = questions[i].label_a;
					h[++idx] = '</textarea>';
				h[++idx] = '</div>';
				h[++idx] = '<div class="small-12 medium-6 columns">';
					h[++idx] = '<textarea class="lang_b" tabindex="';
						h[++idx] = i + 1;
						h[++idx] = '" data-index="';
						h[++idx] = i;
						h[++idx] = '">';
						h[++idx] = questions[i].label_b;
					h[++idx] = '</textarea>';
				h[++idx] = '</div>';	
			h[++idx] = '</div>';
		h[++idx] = '</div>';
	}

	
	$element.html(h.join(''));
	translateHtmlFixup($element);
	
}

function translateHtmlFixup($element) {

	//$('.lang_a', $element).before('<h6 class="qtype">Original Language</h6>');
	//$('.lang_b', $element).before('<h6 class="qtype">Translated Language</h6>');
	$(document).on('focus', '.lang_a, .lang_b', function(event) {
		event.preventDefault();
		var half_height = $(window).height()/2.5;
		$("html, body").animate({ 
			scrollTop: 
			($(this).offset().top 
				-half_height) 
			},100);
		$(this).autosize();
		$(this).parent().prev().find('.lang_a').autosize();
	});

}


});