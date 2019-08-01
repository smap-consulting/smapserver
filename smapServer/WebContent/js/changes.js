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
    	jquery: 'jquery-2.1.1',
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
	gTempQuestions = [];

$(document).ready(function() {
	
	var i,
		params,
		pArray = [],
		param = [];

	setupUserProfile();
	localise.setlang();		// Localise HTML

	// Get the user details
	globals.gIsAdministrator = false;
	getLoggedInUser(surveyListDone, false, true, undefined, false, false);

	// Add menu functions
	$('#m_open').off().click(function() {	// Open an existing form
		openForm("existing");
	});

	// Add menu functions
	$('#m_simple_edit').off().click(function() {	// Edit a survey
		gMode = "simple_edit";
		refreshView(gMode);
	});

	
	$('.language_list').off().change(function() {
		globals.gLanguage1 = $('#language1').val();
		globals.gLanguage2 = $('#language2').val();
		refreshView(gMode);
		//$('#set_language').foundation('reveal', 'close');
 	 });

});

function surveyListDone() {
	getSurveyDetails(refreshView, true);
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

	setChangesHtml($('#changes'), survey, survey);
}


/*
 * Convert change log JSON to html
 */
function setChangesHtml($element, survey) {
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
        		h[++idx] = '<th>';
        			h[++idx] = localise.set["c_version"];
        		h[++idx] = '</th>';

        		h[++idx] = '<th>';
        			h[++idx] = localise.set["c_changes"];
        		h[++idx] = '</th>';

        		h[++idx] = '<th>';
					h[++idx] = localise.set["rev_cb"];
        		h[++idx] = '</th>';

				h[++idx] = '<th>';
					h[++idx] = localise.set["ed_dt"];
					h[++idx] = ' (';
					h[++idx] = globals.gTimezone;
					h[++idx] = ')';
				h[++idx] = '</th>';

				h[++idx] = '<th>' + localise.set["c_file"] + '</th>';
				h[++idx] = '<th>' + localise.set["c_msg"] + '</th>';
			h[++idx] = '</tr>';
		h[++idx] = '</thead>';
		
		// Write the table body
		h[++idx] = '<body>';
		for(i = 0; i < changes.length; i++) {
			
			var status = "pending";
			if(!changes[i].apply_results) {		// Change has been applied to the results tables
				status = changes[i].success ? "success" : "failed";
			}
			var filehtml = "";
			if(changes[i].change.fileName && changes[i].change.fileName.trim().length > 0) {
                var filename = changes[i].change.fileName;
				var fnIndex = changes[i].change.fileName.lastIndexOf('/');
				if(fnIndex >= 0) {
					filename = filename.substr(fnIndex + 1);
				}
				var url = null;
				if(filename.indexOf("_template.pdf") > 0) {
					url = '/surveyKPI/file/' + filename + '/surveyPdfTemplate/' + changes[i].change.origSId + '?archive=true';
				} else {
					url = '/surveyKPI/survey/' + changes[i].change.origSId + '/download?type=xls';
				}
                filehtml = '<a href="' + url + '">' + filename + '</a>';
			}
			h[++idx] = '<tr class="change_';
					h[++idx] = status;
					h[++idx] = '">';
				h[++idx] = '<td>';
				h[++idx] = changes[i].version;
				h[++idx] = '</td>';	
				h[++idx] = '<td>';
				h[++idx] = getChangeDescription(changes[i].change, changes[i].version);
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = changes[i].userName;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = changes[i].updatedTime;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = filehtml;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = changes[i].msg;
				h[++idx] = '</td>';
			h[++idx] = '</tr>';
		}
		h[++idx] = '</body>';
		
		h[++idx] = '</table>';
	} 
	
	$element.html(h.join(''));
	
	
}

});