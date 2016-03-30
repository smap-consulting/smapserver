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
	gChangeset,
	gAuditItems,	
	gQuestions,
	gRecordIdx,
	gUpdate = {},
	gTextSelected = 0,
	gHasSelect = true,
	gHasText,
	gTextId;			// The id of the selected text question	

$(document).ready(function() {
	
	if(typeof getVersion === "function") {
		getVersion();			// Update if the version on the server has changed
	}
	
	 /*
	  * Get the user details so we have the default project
	  * Then load the available projects for the user and load the surveys for the default project
	  */
	getLoggedInUser(getSurveyList, false, true);
	
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
		getAuditList();
 	 });
	
	enableReversal();
	enableDetails();
	enableUserProfile();
	
});

function enableDetails() {
	 $('#details_popup').dialog(
				{
					autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
					title:"Change Details",
					show:"drop",
					width: 350,
					zIndex: 2000,
					buttons: [
				        {
				        	text: "Close",
				        	click: function() {
				        		
				        		$(this).dialog("close");
				        	}
				        }
					]
				}
			 );
}

function enableReversal() {
	 $('#reversal_popup').dialog(
				{
					autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
					title:"Reverse Update",
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
				        	text: "Reverse Change",
				        	click: function() {
				        		addHourglass();
				        		$.ajax({
				        			  type: "POST",
				        			  url: "/surveyKPI/review/" + globals.gCurrentSurvey + "/undo/" + gChangeset,
				        			  success: function(data, status) {
				        				  removeHourglass();
				        				  getAuditList();
				        			  },
				        				error: function(xhr, textStatus, err) {
				        					var resp = [],
				        						blockingChangeset;
				        					
				        					removeHourglass();
				        					console.log(xhr);
				        					alert(xhr.responseText);
				        					resp = xhr.responseText.split(":");
				        					if(resp.length > 0) {
				        						blockingChangeset = resp[1];
				        					}
				        					getAuditList(blockingChangeset);
				        						
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
	loadSurveys(globals.gCurrentProject, undefined, false, false, getAuditList);
}


function getAuditList(highlightCS) {
	var	$elem,
		i,
		h = [],
		idx = -1,
		intHighlightCS = 0;
	
	if(typeof highlightCS !== "undefined") {
		intHighlightCS = parseInt(highlightCS);
	}
	
	globals.gCurrentSurvey = $('#survey_name option:selected').val();	// TODO remove, the global survey should have already been set
	addHourglass();
	$.ajax({
		url: "/surveyKPI/review/" + globals.gCurrentSurvey + "/audit",
		dataType: 'json',
		cache: false,
		success: function(data) {

			removeHourglass();
			$elem = $('#review-container tbody');
			gAuditItems = data;
			$elem.empty();
			
			for(i = 0; i < data.length; i++) {
				if(data[i].reversed) {
					h[++idx] = '<tr class="reversed">';
				} else if(data[i].id === intHighlightCS) {
					h[++idx] = '<tr class="highlight">';
				} else {
					h[++idx] = "<tr>";
				}
				h[++idx] = '<td>';
				h[++idx] = data[i].id;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = data[i].change_reason;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = data[i].description;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = '<button class="details" type="button" value="';
				h[++idx] = data[i].id;
				h[++idx] = '">Details</button>';
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = data[i].change_name;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = data[i].reverse_name;
				h[++idx] = '</td>';
				h[++idx] = '<td>';
				h[++idx] = '<button class="reverse" type="button" value="';
				h[++idx] = data[i].id;
				h[++idx] = '"><img src="img/leftarrow.png" height="16" width="16"></button>';
				h[++idx] = '</td>';
				h[++idx] = "</tr>";	
			}
			
			$elem.append(h.join(''));
			
			$('.reverse').button().click(function(e) {

				gChangeset = $(this).val();
				$('h1', '#reversal_popup').html("Reverse change number " + gChangeset);
				$('#reversal_popup').dialog("open");
			});
			$('.reversed .reverse').prop("disabled", true);
			
			$('.details').button().click(function(e) {

				gChangeset = $(this).val();
				$('h1', '#details_popup').html("Details for change number " + gChangeset);
				
				$.ajax({
					url: "/surveyKPI/review/" + globals.gCurrentSurvey + "/audit/details/" + gChangeset,
					dataType: 'json',
					cache: false,
					success: function(data) {

						var $elemDetails = $('#details_popup ul'),
							h = [],
							idx = -1,
							j;
						
						
						for(j = 0; j < data.length; j++) {
							h[++idx] = '<li>';
							h[++idx] = '[';
							h[++idx] = data[j].rId;
							h[++idx] = '] Changed question: ';
							h[++idx] = data[j].qname;
							if(data[j].qtype === "select") {
								if(data[j].set) {
									h[++idx] = ' selected option: ';
								} else {
									h[++idx] = ' un-selected option: ';
								}
								h[++idx] = data[j].newValue;
							} else {
								h[++idx] = ' from "';
								h[++idx] = data[j].oldValue;
								h[++idx] = '" to "';
								h[++idx] = data[j].newValue;
								h[++idx] = '"';
							}
							h[++idx] = '</li>';
						}
						$elemDetails.html(h.join(''));
					},
					error: function(xhr, textStatus, err) {
						if(xhr.readyState == 0 || xhr.status == 0) {
				              return;  // Not an error
						} else {
							console.log("Error: Failed to get audit details: " + err);
						}
					}
				});	
						
				$('#details_popup').dialog("open");
			});

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get audit list: " + err);
			}
		}
	});	
}

});



