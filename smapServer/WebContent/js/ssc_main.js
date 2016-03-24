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
    }
});

require([
         'jquery',
         'app/common', 
         'bootstrap.min', 
         'modernizr',
         'app/localise',
         'app/ssc',
         'app/globals'], 
		function($, common, bootstrap, modernizr, lang, ssc, globals) {


$(document).ready(function() {
	
	var i,
		params,
		pArray = [],
		param = [];
	
	if(typeof getVersion === "function") {
		getVersion();			// Update if the version on the server has changed
	}
	
	// Get the user details
	globals.gIsAdministrator = false;
	dont_get_current_survey = false;		// The current survey was not passed in the parameters
	getLoggedInUser(getSurveyList, false, true, undefined, false, dont_get_current_survey);
	

	// Add menu functions
	$('#m_open').off().click(function() {	// Open an existing form
		openForm("existing");
	});

    /*
     * Open a new form
     */
	$('#get_form').off().click(function() {
		globals.gCurrentSurvey = $('#survey_name option:selected').val();
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current survey id
		getSurveyDetails(surveyDetailsDone);
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
	 * Respond to save button being clicked
	 */
	$('#sscSave').off().click(function(){
		
		var fn = $('#ssc_function').val(),
			name = $('#ssc_name').val(),
			formId = $('#ssc_form option:selected').val(),
			form = $('#ssc_form option:selected').text();
		
		if(fn === "") {
			$('#ssc_alert').show().text("You must select a function");
			setTimeout(function() {
				$('#ssc_function').focus();
				}, 0);		
			return false;
		}
		if(name === "") {
			alert("You must specify a name");
			return false;
		}
		if(typeof form === "undefined" || form === "") {
			alert("You must specify a form");
			return false;
		}
		for(i = 0; i < globals.model.survey.sscList.length; i++) {
			if(globals.model.survey.sscList[i].name === name) {
				alert("There is an existing calculation with that name");
				return false;
			}
		}
		
		saveSSC();
		
		// Add the new ssc to the list
		/*
		var newSSC = {
				name: name,
				fn: fn,
				units: $('#ssc_units').val(),
				form: form,
				formId: formId
		}
		globals.model.survey.sscList.push(newSSC);
		setHtml('#sscList', globals.model.survey.sscList);
		globals.model.settingsChange();
		*/
		$('#ssc_alert').hide();
	
	});
});

function getSurveyList() {
	console.log("getSurveyList: " + globals.gCurrentSurvey);
	if(globals.gCurrentSurvey > 0) {
		loadSurveys(globals.gCurrentProject, undefined, false, false, surveyListDone);
	} else {
		loadSurveys(globals.gCurrentProject, undefined, false, false, undefined);
	}
}

/*
 * Save the SSC
 */
function saveSSC() {
	
	var name = $('#ssc_name').val(),
		form = $('#ssc_form option:selected').val(),
		fn = $('#ssc_function').val(),
		units = $('#ssc_units').val();
	
	addHourglass();
	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  dataType: "json",
		  url: "/surveyKPI/ssc/" + globals.gCurrentSurvey + "/" + fn + "/add",
		  data: { 
			  form: form,
			  name: name,
			  units: units
			  },
		  success: function(data, status) {
			  removeHourglass();
			  $('#add_ssc_modal').modal("hide");
			  getSurveyDetails(surveyDetailsDone);
		  }, error: function(xhr, textStatus, err) {
			  removeHourglass();
			  console.log(xhr);
			  alert("Failed to add server side calculation: " + xhr.responseText); 
		  }
	});
}

/*
 * Function delete an ssc entry
 */
function deleteSSC(idx) {
	
	var ssc = globals.model.survey.sscList[idx];
	
	addHourglass();
	$.ajax({
		  type: "DELETE",
		  contentType: "application/json",
		  dataType: "json",
		  url: "/surveyKPI/ssc/" + globals.gCurrentSurvey + "/" + ssc.fn + "/delete/" + ssc.id,
		  success: function(data, status) {
			  removeHourglass();
			  $('#add_ssc_modal').modal("hide");
			  getSurveyDetails(surveyDetailsDone);
		  }, error: function(xhr, textStatus, err) {
			  removeHourglass();
			  console.log(xhr);
			  alert("Failed to delete server side calculation: " + xhr.responseText); 
		  }
	});
}

function surveyListDone() {
	getSurveyDetails(surveyDetailsDone);
}

function surveyDetailsDone() {
	$('.formName').html(globals.model.survey.displayName);	
	ssc.init();	// initialise the Server Side Calculations section
	ssc.setHtml('#sscList', globals.model.survey.sscList);
	$('.ssc_btn_rem').click(function () {
		var idx = $(this).val();
		deleteSSC(idx);
	});
}




});