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
 * This file manages the selection of data sources
 */

var gSurveyControlView;	// Store the temporary state of the view
var gMetaInProgress = 0;	// Records the existence of asynch meta calls qhich must complete before user saves

$(document).ready(function() {
	
	// Add change event on panel type
	$('#settings_type').change(function() {
 	 	typeChangeEvent($('#settings_type option:selected').val());
 	 });
	
	// Add change event on survey select
	$('#settings_survey').change(function() {
 	 	surveyChangeEvent($('#settings_survey option:selected').val());
 	 });
	
	// Add change function on language select
	$('#settings_language').change(function() {	
 	 	languageChangeEvent();
 	 });
	
	// Add change function on question select
	$('#settings_question').change(function() {
 	 	var surveyId = $('#settings_survey option:selected').val();
 	 	var qId = $('#settings_question option:selected').val();
 	 	var question = $('#settings_question option:selected').text();
 	 	questionChangeEvent(surveyId, qId, question);
 	 });
	
	  // Change event on group select
	 $('#settings_group').change(function() {	
	 	var sId = $('#settings_survey option:selected').val();
 		var groupId = $('#settings_group option:selected').val();
	 	groupChangeEvent(sId, groupId);
	 });
	 
	 // Add datepicker functionality TODO Bootstrap
	 $('#from_date').datepicker({ dateFormat: "yy-mm-dd" });
	 $('#to_date').datepicker({ dateFormat: "yy-mm-dd" });
	 
	 /*
	  * Question filter
	  */
	 // Initialse the dialog
	 $('#filter_dialog').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			show:"drop",
			width:600,
			zIndex: 2010,
			buttons: [
		        {
		        	text: "OK",
		        	click: function() {
		        		
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	$('#filter_button').button().click(function() {
		$("#filter_dialog").dialog("open");
		return false;
	});
	
	  // Change event on question filter
	 $('#filter_question').change(function() {	
	 	var sId = $('#settings_survey option:selected').val();
		var qId = $('#filter_question option:selected').val();
		var language = $('#settings_language option:selected').val();

		var qInfo = getQuestionInfo(sId, language, qId);
		
		$('#filter_value').empty(); 
		$('#filter_button').removeClass('hasfilter');
		if(qId != -1) {
			getFilterValues(sId, qId, undefined, qInfo.type, language);
		} 
	 });
	 
	  // Filter clear
	 $('#filter_clear').click(function() {	
		 $('#filter_question').val(-1);
		 $('#filter_value').empty(); 
		 $('#filter_button').removeClass('hasfilter');
		 return false;
	 });
	 
});

//Handle click on settings button
function showSettings($this) {
	
	globals.gViewIdx = $this.attr("value");
	
	var views = globals.gSelector.getViews();
	
	gSurveyControlView = copyView(views[globals.gViewIdx]);		// Get a copy of the current view

	//gSurveyControlView = $.extend(true, {}, views[globals.gViewIdx]);	// Get a copy of the current view (stack overflow)
	
	getViewData(gSurveyControlView);
	setSurveyViewControl(gSurveyControlView);		// Set the values in the settings dialog from the view
	
	$('#p_settings').dialog("open");
}

/*
 * Copy a view
 */
function copyView(v) {
	cp = {
			type: v.type,
			title: v.title,
			timeGroup: v.timeGroup,
			tableCount: v.tableCount,
			table: v.table,
			state: v.state,
			seq: v.seq,
			sName: v.sName,
			sId: v.sId,
			region: v.region,
			question: v.question,
			qId_is_calc: v.qId_is_calc,
			qId: v.qId,
			pId: v.pId,
			layerId: v.layerId,
			lang: v.lang,
			key_words: v.key_words,
			id: v.id,
			groupType: v.groupType,
			groupQuestionText: v.groupQuestionText,
			groupQuestionId: v.groupQuestionId,
			fromDate: v.fromDate,
			toDate: v.toDate,
			fn: v.fn,
			filter: v.filter,
			dateQuestionId: v.dateQuestionId
	}
	
	return cp;
}

/*
 * Set the values in the settings dialog from the view
 */
function getViewData(view) {

	if(view.type) {
		$('#settings_type').val(view.type);
	}
	
	// Set the survey selector
	var surveyList = globals.gSelector.getSurveyList();
	if(!surveyList) {	// Surveys have not yet been retrieved
		getViewSurveys(view);
	} 
	
	$('#settings_title').val(view.title);
	
	// Set the survey meta data
	var sMeta = globals.gSelector.getSurvey(view.sId);
	if(!sMeta) {
		getSurveyMetaSE(view.sId, undefined, false, true, true, view.dateQuestionId);
	} else {
		addFormPickList(sMeta);
		addDatePickList(sMeta);
	}
	
	// Get the languages 
	var surveyLanguages = globals.gSelector.getSurveyLanguages(view.sId);
	if(!surveyLanguages) {	
		getViewLanguages(view);
	} else {
		setSurveyViewLanguages(surveyLanguages, view.lang, '#settings_language', false);
		setSurveyViewLanguages(surveyLanguages, view.lang, '#export_language', true);
	}
	
	/*
	 * Get the question meta data
	 */
	if(view.qId != "-1") {
		if(!view.qId_is_calc) {
			var qMeta = globals.gSelector.getQuestion(view.qId, view.lang);
			if(!qMeta) {
				getQuestionMeta(view.sId, view.qId, view.lang, view);
			} else {
				setQ1Functions(qMeta.type, view.type, view.fn);
			}
		} else {
			view.qId = "s:" + view.qId;
			if(view.sId != "-1") {
				var qMeta = getCalculationQMeta(view.sId, view.lang, view.qId);
				setQ1Functions(qMeta.type, undefined, "average");	
			}
		}
	}
	if(view.groupQuestionId != "-1") {
		var qMeta = globals.gSelector.getQuestion(view.groupQuestionId, view.lang);
		if(!qMeta) {
			getGroupMeta(view.sId, view.groupQuestionId, view.lang);
		}
	}

	/*
	 * Get the list of questions
	 */
	if(view.sId != "-1") {
		var questions = globals.gSelector.getSurveyQuestions(view.sId, view.lang);
		var setGroupList = false;
		if(view.type != "map") {
			setGroupList = true;
		}
		
		if(!questions) {	

			getQuestionList(view.sId, view.lang, view.qId, view.groupQuestionId, undefined, setGroupList, view);
		} else {
			setSurveyViewQuestions(questions, view.qId, view);
			if(setGroupList) {
				setSurveyViewQuestionGroups(questions, view.groupQuestionId);
			}
		}
	} else {
		setSurveyViewQuestions(undefined, undefined, undefined);
	}
	
	/*
	 * Get the regions
	 */
	var regions = globals.gSelector.getRegionList();
	if(!regions) {
		getViewRegions(view);
	} else {
		if(view.type == "map") {
			setSurveyViewRegions(regions, view.region);
		}
	}
		
	// Enable the timeseries select if appropriate
	if((view.qId != "-1") && (view.type === "map" || view.type === "graph" || view.type === "table")) {
		if(typeof view.timeGroup === "undefined") {
			view.timeGroup = "none";
		}
		$('#time_group').prop("disabled", false).val(view.timeGroup);
		
	} else {
		$('#time_group').prop("disabled", true).val("none");
	}
	
	// Set the date question id if appropriate
	if(typeof view.dateQuestionId !== "undefined" && view.dateQuestionId != 0) {
		$('#settings_date_question').val(view.dateQuestionId);
	}
	
	// Set the from and to dates  if appropriate	
	$('#from_date').datepicker("setDate", view.fromDate);
	$('#to_date').datepicker("setDate", view.toDate);
	
}

// Panel type has changed
function typeChangeEvent(type) {
	
	var qId = $('#settings_question option:selected').val();
	var surveyId = $('#settings_survey option:selected').val();
	var language = $('#settings_language option:selected').val();
	var questions;
	
	var	oldType = gSurveyControlView.type;
	gSurveyControlView.type = type;
	if(oldType === "map") {
		gSurveyControlView.groupQuestionId = undefined;
	}
	
	if(type == "map") {
		var regions = globals.gSelector.getRegionList();
		setSurveyViewRegions(regions, gSurveyControlView.region);
		
		// Set the geometry question 
		if(qId != "-1") {
			if(qId.indexOf(":") < 0) {
				var qMeta = globals.gSelector.getQuestion(qId, language);
				getGeometryQuestion(surveyId, qMeta.f_id);
			} else {
				var qMeta = getCalculationQMeta(surveyId, language, qId);
				getGeometryQuestion(surveyId, qMeta.f_id); 
			}
		}
		$('.map_only', '#p_settings').show();
	} else {
		 	 
		questions = globals.gSelector.getSurveyQuestions(surveyId, language);
		if(questions) {
			setSurveyViewQuestionGroups(questions, gSurveyControlView.groupQuestionId);
		} else {
			setSurveyViewQuestionGroups();	// Clear the existing list
		}
		$('#display_panel').val("-1");					// Only maps can be displayed on a different panel to the one on which they are specified
		$('.map_only').hide();
	}
	
	// Enable the timeseries select if appropriate
	if((qId != "-1") && (type === "map" || type === "graph" || type === "table")) {
		$('#time_group').prop("disabled", false);
	} else {
		$('#time_group').prop("disabled", true).val("none");
	}
	
}

//Selected survey has changed
function surveyChangeEvent(sId) {
	
	// Get the survey meta data
	var surveyMeta = globals.gSelector.getSurvey(sId);
	if(!surveyMeta) {
		getSurveyMetaSE(sId, undefined, false, false, true);
	} else {
		addDatePickList(surveyMeta);
	}
	
	// Get the languages for this survey
	var languages = globals.gSelector.getSurveyLanguages(sId);
	var setGroupList = false;
	if(gSurveyControlView.type != "map") {
		setGroupList = true;
	}
	
	if(!languages) {
		getLanguageList(sId, languageChangeEvent, false, undefined, setGroupList);		// Retrieve the languages and questions for the default language
	} else {
		setSurveyViewLanguages(languages, undefined, '#settings_language', false);
		setSurveyViewLanguages(languages, undefined, '#export_language', true);
		var questions = globals.gSelector.getSurveyQuestions(sId, languages[0].name);
		if(!questions) {
			getQuestionList(sId, languages[0].name, "-1", "-1", undefined, setGroupList, undefined);	// Default language to the first in the list
		} else {
			setSurveyViewQuestions(questions, undefined, undefined);
			if(setGroupList) {
				setSurveyViewQuestionGroups(questions);
			}
		}
		
	}

	gSurveyControlView.sId = sId;

	$('#settings_group').prop("disabled", true).val("-1");
	$('#q1_function').prop("disabled", true).val("none");
	$('.select_only', '#p_settings').hide();
	$('#time_group').prop("disabled", true).val("none");


}


//Selected language has changed
function languageChangeEvent() {
	
	var sId = $('#settings_survey option:selected').val();
	var language = $('#settings_language option:selected').val();
	var qList = globals.gSelector.getSurveyQuestions(sId, language);
	
	var setGroupList = false;
	if(gSurveyControlView.type != "map") {
		setGroupList = true;
	}
	
	if(!qList) {
		getQuestionList(sId, language, "-1", "-1", undefined, setGroupList, undefined);
	} else {
		setSurveyViewQuestions(qList, undefined, undefined);
		if(setGroupList) {
			setSurveyViewQuestionGroups(qList);
		}
		
	}	
}

// Selected question has changed
function questionChangeEvent(sId, qId, question) {

	var type = $('#settings_type option:selected').val(),
		language,
		qMeta;
	
	if(qId == "-1") {	
		// Disable question level filters
		$('#settings_group').val("-1");
		$('#settings_group').prop("disabled", true);
		$('#q1_function').prop("disabled", true).val("none");
		$('.select_only', '#p_settings').hide();
		$('#time_group').prop("disabled", true).val("none");
		
	} else {
		
		gSurveyControlView.type = type;
		language = $('#settings_language option:selected').val();
		if(qId.indexOf(":") < 0) {	
		
			// Not an SSC question
			// Get the question meta data

			qMeta = globals.gSelector.getQuestion(qId, language);
			if(!qMeta) {
				 getQuestionMeta(sId, qId, language, gSurveyControlView);
			 } else {
				if(gSurveyControlView.type == "map") {
					getGeometryQuestion(sId, qMeta.f_id);
				} 
				setQ1Functions(qMeta.type, undefined, "percent");
			 }
		} else {
			qMeta = getCalculationQMeta(sId, language, qId);
			getGeometryQuestion(sId, qMeta.f_id); 
			setQ1Functions(qMeta.type, undefined, "average");
		}
		// Enable question group bys
		$('#settings_group').prop("disabled", false);
		$('#q1_function').prop("disabled", false);
		
		// Enable the timeseries select if appropriate
		if(type === "map" || type === "graph" || type === "table") {
			$('#time_group').prop("disabled", false);
		} 

	}

}

function getCalculationQMeta(sId, lang, qId) {
	var questionList = globals.gSelector.getSurveyQuestions(sId, lang),
		i,
		qMeta = {
			type: "decimal",
			f_id: "-1"
		};
	
	if(typeof questionList !== "undefined" && questionList) {
		for(i = questionList.length - 1; i >= 0; i--) {
			if(questionList[i].id === qId) {
				qMeta.f_id = questionList[i].f_id;
				qMeta.type = questionList[i].type;
				qMeta.fn = questionList[i].fn;
				break;
			}
		}
	}
	return qMeta;
}

// Get the geometry group. Use (in preferential order)
// 	1) the geo question in the question's form
// 	2) the geo question in the top level form
// 	3) Show an alert if no suitable geometry question exists
function getGeometryQuestion(sId, f_id) {
	var sMeta = globals.gSelector.getSurvey(sId);
	var geomId = null;
	
	// First try to get the geometry id from the question form
	if(typeof sMeta !== "undefined" && typeof sMeta.forms !== "undefined") {
		$.each(sMeta.forms, function(j, item) {
			if(item.f_id == f_id && item.geom) {
				geomId = item.geom_id;
			}
		});
	
		// Then try to get geometry from the top level form
		if(geomId == null) {
			$.each(sMeta.forms, function(j, item) {
				if(item.p_id == 0 && item.geom) {
					geomId = item.geom_id;
				}
			});
		}
		
		// We are not fussy! Try to get the geometry from any other form in the survey
		if(geomId == null) {
			$.each(sMeta.forms, function(j, item) {
				if(item.geom) {
					geomId = item.geom_id;
				}
			});
		}
	}
	
	if(geomId != null) {
		gSurveyControlView.geometryQuestion = geomId;
		var language = $('#settings_language option:selected').val();
		var groupMeta = globals.gSelector.getQuestion(geomId, language);
		if(!groupMeta) {
			getGroupMeta(sId, geomId, language);
		}
	} else {
		// alert("Could not find a location question in order to position the results on the map");  // Do we need an alert?
	}
}

//Selected group by has changed
function groupChangeEvent(sId, groupId) {
	
	var language, groupMeta;
	
	// Get the meta data for this group
	if(groupId != "-1") {
		language = $('#settings_language option:selected').val();
		groupMeta = globals.gSelector.getQuestion(groupId, language);
		if(!groupMeta) {
			 getGroupMeta(sId, groupId, language);
		}
	} 
}

//Set the state of the survey view control according to the view
function setSurveyViewControl(view) {

	$('#settings_survey').prop("disabled", false);
	if(view.sId) {
		$('#settings_survey').val(view.sId);
	}
	
	$('#settings_language').prop("disabled", false);
	if(view.lang) {
		$('#settings_language').val(view.lang);
	}
	
	// Display Panel and other map specific fields
	var views = globals.gSelector.getViews();
	$display_panel = $('#display_panel');
	$display_panel.empty().append('<option value="-1">This Chart</option>');
	for (var i = 0; i < views.length; i++) {
		if(views[i].pId != view.pId && views[i].type == "map" && views[i].layerId < 1) {
			if(views[i].state !== "deleted") {
				$display_panel.append('<option value="' + views[i].id + '">' + views[i].title + '</option>');
			}
		}
	}
	if(view.type == "map") {
		$('.map_only').show();
		$display_panel.val(view.layerId);
	} else {
		$('.map_only').hide();
	}
	
	// Question
	$('#settings_question').removeAttr("disabled");
	if(view.qId && view.qId != "-1") {
		$('#settings_question').val(view.qId);	
		$('#q1_function').val(view.fn);	
		$('#settings_group').prop("disabled", false);
		$('#q1_function').prop("disabled", false);
	} else {
		$('#settings_question').val("-1");
		$('#settings_group').prop("disabled", true);
		$('#q1_function').prop("disabled", true);
		$('.select_only', '#p_settings').hide();
	}	
		

	// Group
	if(view.type == "map") {
		gSurveyControlView.geometryQuestion = view.groupQuestionId;
		if(view.region) {
			$('#settings_group').val(view.region);
		} else {
			$('#settings_group').val("-1");
		}
	} else {
		if(view.groupQuestionId) {
			$('#settings_group').val(view.groupQuestionId);
		} else {
			$('#settings_group').val("-1");
		}
	}

	// Filter
	setFilterFromView(view);
	
}

/*
 * Populate the survey drop down list
 */
function setSurveyViewSurveys(surveyList, sId, elem) {

	var $surveySelect = $(elem);
	$surveySelect.empty();
	$surveySelect.append('<option value="-1">None</option>');	// Default is no survey selected
	$.each(surveyList, function(j, item) {
		$surveySelect.append('<option value="' + item.id + '">' + item.displayName + '</option>');
	});
	
	$surveySelect.val(sId);
}

//Set the region list in the survey view control

function setSurveyViewRegions(list, region) {

	var $regionSelect = $('#settings_group');
	$regionSelect.empty();
	$regionSelect.append('<option value="None">None</option>');
	$.each(list, function(j, item) {
		$regionSelect.append('<option value="' + item.table + '">' + item.name + '</option>');
	});
	$regionSelect.val(region);
	gSurveyControlView.region = region;
}



//Set the group list in the survey view control
function setSurveyViewQuestionGroups(list, groupId) {
	"use strict";
	
	var $groupSelect = $('#settings_group'),
		label;
	$groupSelect.empty();
	$groupSelect.append('<option value="-1">None</option>');
	if(typeof list !== "undefined") {
		$.each(list, function(j, item) {
			if(typeof item.q === "undefined") {
				label = "";
			} else {
				label = item.q;
			}

			if(typeof item.type !== "undefined" && item.type !== "dateTime" && item.type !== "date" 
					&& item.type !== "time" && item.name !== "_task_key") {
				if(item.is_ssc) {
					// $groupSelect.append('<option value="' + item.id + '">ssc : ' + item.name + " : " + item.fn + '</option>');  TODO allow grouping by server side calculates
				} else {
					$groupSelect.append('<option value="' + item.id + '">' + item.name + " : " + label + '</option>');
				}
			}
		});
	}
	
	if(!groupId) {
		groupId = "-1";
	}
	$groupSelect.val(groupId);
}
 
// Refresh the data, clears the cache and reloads data from the server
function refreshAnalysisData() {

	globals.gSelector.clearDataItems();	// clear any cached data
	globals.gSelector.clearSurveys();	// Clear the list of surveys and survey definitions
	
	// Get the view list
	var // dbList = $( "#db_list" ).sortable("toArray"), nodbl
		views = globals.gSelector.getViews(),
		i, j, idx,
		multiLayerMaps = [];
	
	for(i = 0; i < views.length; i++) {
		if(views[i].state != "deleted") {
			getData(views[i]);		// Add the data	
		}
	}
	
}

//Get the data for the specified view
function getData(view) {
	
	if(view.qId != "-1") {			// Question level results
		getResults(view);
		
	} else {				// Whole of survey results
		var sMeta = globals.gSelector.getSurvey(view.sId);
		if(!sMeta) {
			 getSurveyMetaSE(view.sId, view, true, false, true, view.dateQuestionId);
		 } else {
			addDatePickList(sMeta);
			getSurveyDataSE(view.sId, view);	
		 }
	}
}

// Populate the list of available functions
function setQ1Functions(type, panelType, defValue) {
	
	var $fnElem = $('#q1_function'),
		pType,
		h =[],
		idx = -1;
	
	$fnElem.empty();
	h[++idx] = '<option value="none">None</option>';
	if(type.substring(0, 6) === "select") {
		
		// For select questions the default value should not be "none". Override it here.
		if(defValue === "none") {
			defValue = "percent";
		}
		
		h[++idx] = '<option value="count">Count</option>';
		h[++idx] = '<option value="percent">Percent</option>';
	
		if(!panelType) {
			pType = $('#settings_type option:selected').val();
		} else {
			pType = panelType;
		}
		if(pType === "map") {
			h[++idx] = '<option value="ocha">OCHA Icons</option>';
		}

	} else if(type === "int" || type === "decimal" || type === "calculate" ){
		
		// For these questions the default value should not be "percent" if there is a group by.
		if(defValue === "percent") {
			defValue = "average";
		}
		h[++idx] = '<option value="average">Average</option>';
		h[++idx] = '<option value="total">Total</option>';
	} else {
		
	}
	$fnElem.append(h.join(''));
	
	$fnElem.val(defValue);	
}

/*
 * Set the filter dialog values from the filter string stored in the view
 */
function setFilterFromView(view) {
	// Set the filter if the question list has already been loaded
	if(typeof view.filter !== "undefined") {
		var filterObj = JSON.parse(view.filter);
		if(filterObj.qId != -1) {
			$('#filter_question').val(filterObj.qId);
			getFilterValues(view.sId, filterObj.qId, filterObj.value, filterObj.qType, view.lang);
		} else {
			$('#filter_value').empty(); 
			$('#filter_button').removeClass('hasfilter');
			$('#filter_question').val(-1);
		}
	} else {
		$('#filter_value').empty(); 
		$('#filter_button').removeClass('hasfilter');
		$('#filter_question').val(-1);
	}
}

/*
 * Get unique values for the filter
 */
function getFilterValues(sId, qId, value, qType, language) {

	if(typeof qType !== "undefined") {
		if(qType === "select" || qType === "select1") {
			getSelectOptions(sId, qId, value, language);
		} else {
			getTextValues(sId, qId, value);
		}
	}
}


/*
 * Retrieve the text values for filter from the server
 */
function getTextValues(sId, qId, value) {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/review/" + sId+ "/results/distinct/" + qId + "?sort=asc",
		dataType: 'json',
		cache: false,
		success: function(data) {
			
			removeHourglass();
			console.log("Values");
			console.log(data);
			
			$('#filter_button').addClass('hasfilter');
			updateFilterOptions(data, value, false);
	
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get values for question filter: " + err);
				alert("Invalid filter question");
			}
		}
	});	
}

/*
 * Retrieve the select options for filter from the server
 */
function getSelectOptions(sId, qId, value, language) {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/optionList/" + sId+ "/" + language + "/" + qId,
		dataType: 'json',
		cache: false,
		success: function(data) {
			
			removeHourglass();
			console.log("Values");
			console.log(data);
			
			$('#filter_button').addClass('hasfilter');
			updateFilterOptions(data, value, true);
	
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get values for question filter: " + err);
			}
		}
	});	
}

/*
 * Update the list of filter options with the data retrieved from the server
 */
function updateFilterOptions(data, value, isSelect) {

	var	$elem = $('#filter_value'),
		i,
		h = [],
		idx = -1;
	$elem.empty();

	if(isSelect) {
		var sortedList = data.slice();
		sortedList.sort(function(a, b) {return a.label.toLocaleLowerCase() > b.label.toLocaleLowerCase()});
        for (i = 0; i < sortedList.length; i++) {
            h[++idx] = '<option value="';
			h[++idx] = sortedList[i].value;
            h[++idx] = '">';
			h[++idx] = sortedList[i].label;
            h[++idx] = '</option>';
        }
	} else {
        for (i = 0; i < data.length; i++) {
            h[++idx] = '<option value="';
			h[++idx] = data[i].text;
            h[++idx] = '">';
			h[++idx] = data[i].text;
            h[++idx] = '</option>';
        }
    }
			
	$elem.append(h.join(''));
			
	if(typeof value !== "undefined") {
		$('#filter_value').val(value);
	}
}

/*
 * Get the list of available surveys
 */
function getViewSurveys (view) {
		
	var url = surveyList();
	if(typeof url !== "undefined") {
		$.ajax({
			url: url,
			cache: false,
			dataType: 'json',
			success: function(data) {
				globals.gSelector.setSurveyList(data);
				if(view) {
					setSurveyViewSurveys(data, view.sId, '#settings_survey, #export_survey, #survey_to_update');
				}
			},
			error: function(xhr, textStatus, err) {
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					$('#status_msg_msg').empty().text("Error: Failed to get a list of surveys");
					$("#status_msg").dialog("open");
				}
			}
		});		
	}
}

function getViewLanguages(view) {
	
	if(view.sId != -1) {
		var url = languageListUrl(view.sId);
		$.getJSON(url, function(data) {
			globals.gSelector.setSurveyLanguages(view.sId, data);
			setSurveyViewLanguages(data, view.lang, '#settings_language', false);	
			setSurveyViewLanguages(data, view.lang, '#export_language', true);
		});
	}
	
}

/*
 * Get the list of available regions
 */
function getViewRegions(view) {	
			
	var url = regionsURL();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			globals.gSelector.setRegionList(data);
			if(view.type == "map") {
				setSurveyViewRegions(data, view.region);
			}
		},
		error: function(xhr, textStatus, err) {
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get list of regions: " + err);
			}
		}
	});		
}
