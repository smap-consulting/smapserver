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

/*
 * Purpose: Review and modify collected data
 */
define(['jquery', 'jquery_ui', 'localise', 'common', 'globals'], function($, ui, lang, common, globals) {

var gTextValues,	
	gCurrentLanguage,	
	gUpdate = {},
	gTextSelected = 0,	// checked
	gHasSelect = true,
	gHasText,
	gCountRecords = 0,
	gRelevance = [],
	gTextId,			// The id of the selected text question	
	gTextOtherId;		// The id of the other selected text question

$(document).ready(function() {
	
	console.log("review");
	 /*
	  * Get the user details so we have the default project
	  * Then load the available projects for the user and load the surveys for the default project
	  */
	getLoggedInUser(getSurveyList, false, true);
	$('#text_update').button().button("disable").click(function () {
			textUpdate();
	});
	
	// Set change function on projects
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
		getSurveyList();
 	 });
	
	// Set change function on surveys
	$('#survey_name').change(function() {
		globals.gCurrentSurvey = $('#survey_name option:selected').val();
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current survey id
		getReviewLanguageList();
 	 });
	
	// Set change function on languages
	$('#language_name').change(function() {
		getTextQuestions();
 	 });
	
	// Set change function on the text question
	$('#text_name').change(function() {
		getData();
		getRelevance();
 	 });
	
	// Set change function on the other text question
	$('#target_question_name').change(function() {
		getData();
 	 });
	
	// Set change function on the "other target" checkbox
	$('#other_target_cb').change(function() {
		var $this = $(this);
		if($this.is(':checked')) {
			$('#target_question_name_cont, .review_update_other').show();
		} else {
			$('#target_question_name_cont, .review_update_other').hide();
		}
 	 });
	$('#target_question_name_cont, .review_update_other').hide();
	
	// Add change functions to update dialog
	$('#tu_existing_option').change(function() {
		$('#tu_existing_text').val("");
		$('#tu_new_text').val("");
 	 });
	$('#tu_existing_text').change(function() {
		$('#tu_existing_option').val("");
		$('#tu_new_text').val("");
 	 });
	$('#tu_new_text').on('input', function() {
		$('#tu_existing_option').val("");
		$('#tu_existing_text').val("");
 	 });
	
	enableTextUpdate();
	enableUserProfile();
	
	/*
	 * Enable Menu events
	 */
	$('.rmm').delegate('#refreshMenu', 'click', function(e) {
		
		e.preventDefault();
		if(targetHasChanged()) {
			if (confirm("You have made some changes and not saved them, are you sure you want to refresh?")) {

				getData();
			}
		} else {
			getData();
		}
	}); 
	
	/*
	 * Add check prior to the user leaving the screen
	 */
	window.onbeforeunload = function() {
		if(targetHasChanged()) {
			return "You have unsaved changes are you sure you want to leave?";
		}
	};
});

function enableTextUpdate() {
	 $('#text_update_popup').dialog(
				{
					autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
					title:"Update Text",
					show:"drop",
					width: 350,
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
				        		
				        		// TODO only save if value has changed
				        		var updateString,
				        			newValue,
				        			optionSelected,
				        			newUpdates = [];

				        		// Get the new value to be applied, first try the list of options
				        		newValue = $('#tu_existing_option option:selected').val();
				        		if(typeof newValue !== "undefined" && newValue !== "") {
				        			optionSelected = true;
					        		
				        		} else {
				        		
				        			newValue = $('#tu_existing_text option:selected').text();
	 
					        		if(typeof newValue === "undefined" || newValue === "") {
					        			newValue = $('#tu_new_text').val();
					        		}
					        		if(typeof newValue === "undefined" || newValue === "") {
					        			alert("Please specify a new value");
					        			if(gHasSelect) {
					        				$('#tu_existing_option').focus();
					        			} else if (gHasText) {
					        				$('#tu_existing_text').focus();
					        			} else {
					        				$('#tu_new_text').focus();
					        			}
					        			return false;
					        		}
					        		
				        		}
				        		
				        		for(i = 0; i < gUpdate.reviewItems.length; i++) {
				        			if(optionSelected) {
				        				newUpdates.push({
				        					q_id:  gRelevance[0].qId,
				        					r_id:  gUpdate.reviewItems[i].r_id,
				        					valueFilter: gUpdate.reviewItems[i].valueFilter,
				        					qFilter: gUpdate.reviewItems[i].qFilter,
				        					newValue: newValue,
				        					set: true
				        				});
				        				gUpdate.reviewItems[i].newValue = undefined;
				        				
				        				// If this is a select multiple then unselect the option that made the text question relevant
				        				if(gRelevance[0].type === "select") {
					        				newUpdates.push({
					        					q_id:  gRelevance[0].qId,
					        					r_id:  gUpdate.reviewItems[i].r_id,
					        					valueFilter: gUpdate.reviewItems[i].valueFilter,
					        					qFilter: gUpdate.reviewItems[i].qFilter,
					        					newValue: gRelevance[0].oname,
					        					set: false
					        				});
				        				}
				        			} else {
				        				gUpdate.reviewItems[i].newValue = newValue;
				        			}
				        			
				        		}
				        		
				        		// Set description of change
				        		gUpdate.description = "Replace text (" + gUpdate.valueFilter + ") in ";
				        		gUpdate.description += gCountRecords;
				        		if(optionSelected) {
				        			gUpdate.description += " records with option (" + newValue + ") from question (";
				        			gUpdate.description += gRelevance[0].label + ")";
				        		} else {
				        			gUpdate.description += " records with (" + newValue + ")";
				        		}
				        		
				        		// Create combined list of all items to be updated
				        		gUpdate.reviewItems = gUpdate.reviewItems.concat(newUpdates);
				        		
				        		gUpdate.reason = $('#tu_reason').val();
				        		
				        		updateString = JSON.stringify(gUpdate);
				        		
				        		addHourglass();
				        		$.ajax({
				        			  type: "POST",
				        			  contentType: "application/json",
				        			  dataType: "json",
				        			  url: "/surveyKPI/review/" + globals.gCurrentSurvey,
				        			  data: { updates: updateString },
				        			  success: function(data, status) {
				        				  removeHourglass();
				        				  getData();
				        				  
				        			  }, error: function(data, status) {
				        				  removeHourglass();
				        				  console.log("Error: Failed to save updates");
				        			  }
				        		});
				        		$(this).dialog("close");
				        	}
				        }
					]
				}
			 );
}

function getSurveyList() {
	loadSurveys(globals.gCurrentProject, undefined, false, false, getReviewLanguageList);
}


function getReviewLanguageList() {
	globals.gCurrentSurvey = $('#survey_name option:selected').val();	// TODO Remove when gCurrent survey is truly global and preserved for duration of app
	if(globals.gCurrentSurvey) {
		getLanguageList(globals.gCurrentSurvey, getTextQuestions, false, '#language_name', false);
	}
}


function getTextQuestions() {
	var i,
		$text_name = $('#text_name,#target_question_name');
	
	gCurrentLanguage = $('#language_name option:selected').val();
	
	addHourglass();
	$.ajax({
		url: "/surveyKPI/questionList/" + globals.gCurrentSurvey + "/" + gCurrentLanguage + "?exc_read_only=true&single_type=string&exc_ssc=true",
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			$text_name.empty();
			
			for(i = 0; i < data.length; i++) {
				$text_name.append('<option value="' + data[i].id + '">' + data[i].name + ' : ' + data[i].q + '</option>');
			}
			getData();
			getRelevance();
		
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get list of text questions: " + err);
			}
		}
	});	
}

function getData() {
	var	$elem = $('#review-container tbody'),
		i,
		h = [],
		idx = -1,
		$textUpdate = $('#text_update'),
		textOtherOption = "";
	
	gTextId = $('#text_name option:selected').val();
	gTextOtherId = $('#target_question_name option:selected').val();
	
	textOtherOption = "?targetQuestion=" + gTextOtherId;

	$elem.empty();
	
	addHourglass();
	$.ajax({
		url: "/surveyKPI/review/" + globals.gCurrentSurvey + "/results/distinct/" + gTextId + textOtherOption,
		dataType: 'json',
		cache: false,
		success: function(data) {
			
			removeHourglass();
			$textUpdate.button("disable");
			gTextValues = data;
			gTextSelected = 0;
			
			for(i = 0; i < data.length; i++) {
				h[++idx] = "<tr>";
				h[++idx] = '<td>';
				h[++idx] = data[i].text;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = data[i].count;
				h[++idx] = '</td>';	
				h[++idx] = '<td>';
				h[++idx] = '<button class="review_update" type="button" value="';
				h[++idx] = i;
				h[++idx] = '"><img src="img/rightarrow.png" height="16" width="16"></button>';
				h[++idx] = '</td>';
				h[++idx] = '<td class="review_update_other">';
				h[++idx] = '<input type="text" data-orig="';
				h[++idx] = data[i].targetQuestion;
				h[++idx] = '" value="';
				h[++idx] = data[i].targetQuestion;
				h[++idx] = '">';
				h[++idx] = '</td>';
				h[++idx] = "<tr>";	
			}
			
			$elem.append(h.join(''));
			$('.review_update').button().click(function(e) {
				gTextIdx = $(this).val();
				textUpdate();
			});
			
			if($('#other_target_cb').is(':checked')) {
				$('#target_question_name_cont, .review_update_other').show();
			} else {
				$('#target_question_name_cont, .review_update_other').hide();
			}
			

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get results: " + err);
			}
		}
	});	
}

function getRelevance() {
	
	gTextId = $('#text_name option:selected').val();
	
	addHourglass();
	$.ajax({
		url: "/surveyKPI/review/" + globals.gCurrentSurvey + "/relevance/" + gCurrentLanguage + "/" + gTextId,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			gRelevance = data;

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get relevance options: " + err);
			}
		}
	});	
}


function textUpdate() {
	
	var $tu_existing_text = $('#tu_existing_text'),
		$tu_existing_option = $('#tu_existing_option'),
		$text_change_list = $('#text_to_mod'),
		i,
		h = [],
		idx = -1;
	
	gUpdate = {};
	gUpdate.reviewItems = [];
	gCountRecords = 0;
	
	// Get selected values that are to be changed

	gUpdate.reviewItems.push( 
		{
			q_id: gTextId
		}
	);
	gUpdate.qFilter = gTextId,
	gUpdate.valueFilter = gTextValues[gTextIdx].text;
	
	gCountRecords = parseInt(gTextValues[gTextIdx].count);
	$text_change_list.html(gTextValues[gTextIdx].text);
	
	// Add existing option selection options

	gHasSelect = false;
	// Assume just one relevant question for now
	h[++idx] = '<option value=""></option>';
	if(gRelevance.length > 0) {
		for(i = 0; i < gRelevance[0].options.length; i++) {
			gHasSelect = true;
			h[++idx] = '<option value="';
			h[++idx] = gRelevance[0].options[i].name;
			h[++idx] = '">';
			h[++idx] = gRelevance[0].options[i].label;
			h[++idx] = '</option>';
		}
	}
	$tu_existing_option.empty().html(h.join(''));
	if(!gHasSelect) {
		//$tu_existing_option.attr("disabled", "disabled");
		$('.tu_existing_option').css('color','#ccc').attr("disabled", "disabled");
	}
	
	// Add existing text options
	idx = -1;
	h = [];
	gHasText = false;
	h[++idx] = '<option value=""></option>';
	for(i = 0; i < gTextValues.length; i++) {
		if(i != gTextIdx) {
			gHasText = true;
			h[++idx] = '<option value="';
			h[++idx] = i;	// Use any unique value other than "", only the text content is used
			h[++idx] = '">';
			h[++idx] = gTextValues[i].text;
			h[++idx] = '</option>';
		}
	}
	
	$tu_existing_text.empty().html(h.join(''));
	if(!gHasText) {
		$('.tu_existing_text').css('color','#ccc').attr("disabled", "disabled");
	}
	
	$('#text_update_popup').dialog("open");
}


/*
 * Save any changes to the "other" question
 */
function saveTargetResults() {
	
	var updateString,
		newValue,
		newUpdates = [];

		// for each item where value has changed
		newValue = $('#tu_new_text').val();
		
		gUpdate.reviewItems.push( 
				{
					q_id: gTextId
				}
			);
			gUpdate.qFilter = gTextId,
			gUpdate.valueFilter = gTextValues[gTextIdx].text;
			
			gCountRecords = parseInt(gTextValues[gTextIdx].count);
	
	
	
		// Set description of change
		gUpdate.description = "Replace value (" + gUpdate.valueFilter + ") in ";
		gUpdate.description += gCountRecords;
	
		gUpdate.description += " records with (" + newValue + ")";
	
	
		// Create combined list of all items to be updated
		gUpdate.reviewItems = gUpdate.reviewItems.concat(newUpdates);
	
		gUpdate.reason = "";
	
		updateString = JSON.stringify(gUpdate);
	
		addHourglass();
		$.ajax({
			  type: "POST",
			  contentType: "application/json",
			  dataType: "json",
			  url: "/surveyKPI/review/" + globals.gCurrentSurvey,
			  data: { updates: updateString },
			  success: function(data, status) {
				  removeHourglass();
				  getData();
				  
			  }, error: function(data, status) {
				  removeHourglass();
				  console.log("Error: Failed to save updates");
			  }
		});
	
}

function targetHasChanged() {
	changed = false;
	
	$('.review_update_other input').each(function(index){
		var $this = $(this);
		if($this.data("orig") !== $this.val()) {
			console.log("val: " + $this.val() + " has changed");
			changed = true;
		}
		
	});
	
	return changed;
}


});

