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

var gTasksEnabled = false;
var viewIdx = 0;
var gWait = 0;	// Counter set non zero when there are long running processes running

$(document).ready(function() {

	
	/*
	 * Enable Menu events
	 */
	$('.rmm').delegate('#refreshMenu', 'click', function(e) {
		e.preventDefault();
		refreshAnalysisData();
	}); 
	
	$('.rmm').delegate('#exportMenu', 'click', function(e) {
		e.preventDefault();
		
		// Set the survey selector
		var surveyList = gSelector.getSurveyList();
		if(!surveyList) {	// Surveys have not yet been retrieved
			getViewSurveys({sId:"-1"});
		} 

		$('#export').dialog("open");
	}); 

	/*
	 * Export Dialog
	 */	
	$('#export').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			show:"drop",
			zIndex: 2000,
			buttons: [
		        {
		        	text: "Cancel",
		        	click: function() {
		        		$(this).dialog("close");
		        	}
		        },
		        {
		        	text: "Export",
		        	click: function() {
		        		var sId = $('#export_survey option:selected').val(),
		        			language = $('#export_language option:selected').val(),
		        			displayName = $('#export_survey option:selected').text(),
			        		//format = $('.exportformat:checked').attr("value"),
			        		format = $('#exportformat').val(),
			        		split_locn = $('.splitlocn:checked').attr("value"),
			        		forms = [];
		        			
		        		if(sId == "-1") {
		        			alert("Please select a survey");
		        			return(false);
		        		}
	        			
		        		if(format === "osm") {
		        			forms = $(':checkbox:checked', '.osmforms').map(function() {
		        			      return this.value;
		        			    }).get();
		        			url = exportSurveyOSMURL(sId, displayName, forms);
		        		} else if(format === "shape" || format === "kml" || format === "vrt" || format === "csv") {
		        			forms = $(':radio:checked', '.shapeforms').map(function() {
		        			      return this.value;
		        			    }).get();
		        			if(forms.length === 0) {
		        				alert("A form must be selected");
			        			return(false);
		        			}		
		        			url = exportSurveyShapeURL(sId, displayName, forms[0], format);
		        		} else {

		        			forms = $(':checkbox:checked', '.selectforms').map(function() {
		        			      return this.value;
		        			    }).get();
		        			
		        			if(forms.length === 0) {
		        				alert("At least one form must be selected");
			        			return(false);
		        			}
		        			url = exportSurveyURL(sId, displayName, language, format, split_locn, forms);
		        		}
		        		
		        		
		        		$("body").append("<iframe src='" + url + "' style='display: none;' ></iframe>");
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	
	
	// Change event on export dialog survey select
	$('#export_survey').change(function() {
		var sId = $('#export_survey option:selected').val(),
			languages = gSelector.getSurveyLanguages(sId),
			sMeta,
			questions;
		
		if(!languages) {
			getLanguageList(sId);		// Retrieve the languages and questions for the default language
		} else {
			setSurveyViewLanguages(languages, undefined, '#settings_language', false );
			setSurveyViewLanguages(languages, undefined, '#export_language', true );
			questions = gSelector.getSurveyQuestions(sId, languages[0].name);
		}
		
		// Add the form list for osm export an identifying forms to include in spreadsheet export
		sMeta = gSelector.getSurvey(sId);
		if(!sMeta) {
			getSurveyMetaSE(sId, {}, false,true, false);
		} else {
			addFormPickList(sMeta);
		}
		
 	});
	
	/*
	 * Change event on export format select
	 */
	//$('.exportformat').change(function(){
	//	var format = $('.exportformat:checked').attr("value");
	$('#exportformat').change(function(){
		var format = $('#exportformat').val();
		if(format === "osm") {
			$('.showshape,.showspreadsheet,.showxls,.showosm').hide();
			$('.showosm').show();
		} else if(format === "shape" || format === "kml" || format === "vrt" || format === "csv") {
			$('.showshape,.showspreadsheet,.showxls,.showosm').hide();
			$('.showshape').show();
		} else {
			$('.showshape,.showspreadsheet,.showxls,.showosm').hide();
			$('.showxls,.showspreadsheet').show();
		}
	});
	
	/*
	 * Message Dialog
	 */
	$('#status_msg').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, model:true,
			show:"drop",
			zIndex: 2000,
			buttons: [
		        {
		        	text: "Ok",
		        	click: function() {
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	
	/*
	 * Enable hover functionality for menu options
	 *
	$('#nav li a').hover(menuIn, menuOut);
	function menuIn() {
		if($(this).hasClass('menu_enabled')) {
			$(this).toggleClass('menu_hover').toggleClass('menu_no_hover');
		}
	}
	function menuOut() {
		if($(this).hasClass('menu_enabled')) {
			$(this).toggleClass('menu_hover').toggleClass('menu_no_hover');
		}
	}
	*/

});


$(window).load(function() {
 
 	var param_string,
 		i,
 		params,
 		aParam;
 
	// Open the panel items dialog by default
	// Initialise the panel items dialog
	$('#panelItems').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:false,
			width:300,
			resizable: false,
			draggable: false,
			title: "Panel Management",
			position: { my: "left top", at: "left top", of:'#main', collision: 'none'},
			zIndex: 2000,
			beforeClose: function () {
				return false;		// Disable closing as resizing of panels is causing problems with maps
			}
		}
	);
	
	
	// Get parameters - If the ident parameter is set then the report dialog is opened
	param_string = window.location.search.substring(1);
	if(param_string) {
		params = param_string.split("&");
		for (i = 0; i < params.length; i++) {
			aParam = params[i].split("=");
			if(aParam[0] == "ident") {
				gReportIdent = aParam[1];
				gCalledFromReports = true;
			} else if(aParam[0] == "projectId") {
				gEditingReportProject = aParam[1];
				
			}
		}
		if(gCalledFromReports) {

			$.ajax({   // Get the existing report details to edit
				  url: getReportURL(gReportIdent, "json"),
				  cache: false,
				  success: function(data, status) {
					
					  $('#reportContainer').dialog("open");
					  
					  // Populate gReport from the existing data
					  gReport = data;
					  gReport.smap.data_bounds = new OpenLayers.Bounds(data.smap.bbox[0], data.smap.bbox[1], data.smap.bbox[2], data.smap.bbox[3]).
						transform(new OpenLayers.Projection("EPSG:4326"), new OpenLayers.Projection("EPSG:900913"));
						
					  setReport(gReport);
					  gReportIdent = undefined;
				  }, error: function(data, status) {
					  alert("Failed to load report");
					  gReportIdent = undefined;
				  }
			});
				
		}
	}
 	
});

/*
 * Add a list of forms to pick from during export
 */
function addFormPickList(sMeta) {
	
	var h = [],
	idx = -1,
	i;
	
	// Start with the top level form
	for(i = 0; i < sMeta.forms.length; i++) {
		if(typeof sMeta.forms[i].p_id === "undefined") {
			$(".osmforms").html(addFormToList(sMeta.forms[i], sMeta, 0, true, false));
			$(".selectforms").html(addFormToList(sMeta.forms[i], sMeta, 0, false, false));
			$(".shapeforms").html(addFormToList(sMeta.forms[i], sMeta, 0, true, true));
		}
	}

	(h.join(''));
	$("button",".selectforms").click(function() {
		var $this = $(this),
			$check = $this.parent().find("input"),
			val,
			val_array = [];
		
		val = $check.val();
		val_array= val.split(":");
		if(val_array.length > 1) {
			if(val_array[1] === "true") {
				$check.val(val_array[0] + ":false");
				$this.text("Pivot");
			} else {
				$check.val(val_array[0] + ":true");
				$this.text("Flat");
			}
			$this.toggleClass('exportflat');
			$this.toggleClass('exportpivot');
		}
		
		return false;
	});
}

/*
 * Get the type of a question
 */
function getQuestionInfo(sId, language, qId) {

	var qList = gSelector.getSurveyQuestions(sId, language),
		i,
		qInfo;
	
	console.log("Question list");
	console.log(qList);
	if(qList) {
		for(i = 0; i < qList.length; i++) {
			if(qList[i].id == qId) {
				qInfo = {};
				qInfo.type = qList[i].type;
				qInfo.name = qList[i].name;
				break;
			}
		}
	}
	
	return qInfo;
}

/*
 * Add a list of date questions to pick from 
 */
function addDatePickList(sMeta, currentDate) {
	
	var h = [],
	idx = -1,
	i,
	value;
	
	for(i = 0; i < sMeta.dates.length; i++) {
		
		h[++idx] = '<option value="';
		h[++idx] = sMeta.dates[i].id;
		h[++idx] = '">';
		h[++idx] = sMeta.dates[i].name;
		h[++idx] = '</option>';
		
	}
	if(typeof currentDate !== "undefined") {
		value = currentDate;
	} else {
		value = $("#settings_date_question").val();
	}
	$("#settings_date_question").html((h.join('')));
	if(typeof value !== "undefined") {
		$("#settings_date_question").val(value);
	}
	

}

function addFormToList(form, sMeta, offset, osm, set_radio) {

	var h = [],
		idx = -1,
		i,
		type,
		checked;
	
	if(set_radio) {
		type="radio";
	} else {
		type="checkbox";
	}
	
	// Don't automatically check the forms that hold the points for geopolygon and linestring
	if(form.form.substring(0, 10) === "geopolygon" || form.form.substring(0, 13) === "geolinestring") {
		checked = '';
	} else {
		checked = 'checked="checked"';
	}
	
	h[++idx] = '<span style="padding-left:';
	h[++idx] = offset;
	h[++idx] = 'px;">';
	if(osm && (!set_radio || offset > 0)) {
		h[++idx] = '<input class="osmform" type="' + type + '" name="osmform" value="';
	} else {
		h[++idx] = '<input class="osmform" type="' + type + '" ' + checked + ' name="osmform" value="';
	}
	h[++idx] = form.f_id;
	h[++idx] = ':false"/>';
	h[++idx] = form.form;
	if(typeof form.p_id !== "undefined" && !osm) {
		h[++idx] = ' <button class="exportpivot">Pivot</button>';
	}
	h[++idx] = '<br/>';
	h[++idx] = '</span>';
	
	// Add the children (recursively)
	for(i = 0; i < sMeta.forms.length; i++) {
		if(typeof sMeta.forms[i].p_id !== "undefined" && sMeta.forms[i].p_id == form.f_id) {
			h[++idx] = addFormToList(sMeta.forms[i], sMeta, offset + 20, osm, set_radio);
		}
	}
	
	return h.join('');
}


/******************************************************************************/

/**
 * Generic Functions
 * @author 		Tobin Bradley, Neil Penman
 */

/**
 * Return whether a string is a number or not
 */
function isNumber (o) {
  return ! isNaN (o-0);
}

function esc(input) {
	if(input != null && input != undefined ) {
		return input
			.replace('&', '&amp;')
			.replace('<','&lt;')
			.replace('>', '&gt;');
	} else {
		return "";
	} 
	
};


/**
 * Add commas to numeric values
 */
$.fn.digits = function(){ 
	return this.each(function(){ 
		$(this).text( $(this).text().replace(/(\d)(?=(\d\d\d)+(?!\d))/g, "$1,") ); 
	});
};

function downloadFileURL() {

	var url = "/download";	
	return url;
}

function toggleBadURL(tableName, pKey) {

	var url = "/surveyKPI/items/";	
	url += tableName;
	url += "/bad/";
	url += pKey;
	return url;
}

function getReportURL(ident, format) {

	var url = "/surveyKPI/reports/view/" + ident;	
	if(typeof format != "undefined") {
		url += "?format=" + format; 
	}
	return url;
}

function reportSaveURL(projectId) {

	var url = "/surveyKPI/reports/report/" + projectId;
	return url;
}

function dashboardStateURL() {

	var url = "/surveyKPI/dashboard/state";		
	return url;
}

function dashboardURL(projectId) {

	if(!projectId) {
		projectId = 0;
	}
	var url = "/surveyKPI/dashboard/" + projectId;		
	return url;
}

function deleteSurveyDataURL(sId) {

	var url = "/surveyKPI/surveyResults/";
	url += sId;		
	return url;
}

function regionURL(region) {

	var url = "/surveyKPI/region/";
	url += region;	
	
	return url;
}

function resultsURL (sId, qId, dateId, groupId, groupType, geoTable, fn, lang, timeGroup, 
		startDate, endDate, qId_is_calc, filter) {
	
	var url = "/surveyKPI/results/";
	url += sId;
	url += "?qId=" + qId;
	
	if(dateId != null) {
		url += "&dateId=" + dateId;
	}
	if(groupId != null && groupId != "-1") {
		url += "&groupId=" + groupId;
		
		if(groupType != null) {
			url += "&group_t=" + groupType;
		}
		if(geoTable != null && geoTable.toLowerCase() != "none") {
			url += "&geoTable=" + geoTable;
		}
	}
	if(fn) {
		url += "&fn=" + fn;
	} else {
		url += "&fn=percent";
	}
	
	if(lang) {
		url += "&lang=" + lang;
	} else {
		url += "&lang=eng";
	}
	
	if(typeof timeGroup !== "undefined") {
		url+= "&timeGroup=" + timeGroup;
	}
	
	if(typeof startDate !== "undefined" && startDate.length > 0) {
		url+= "&startDate=" + startDate;
	}
	
	if(typeof endDate !== "undefined" && endDate.length > 0) {
		url+= "&endDate=" + endDate;
	}
	
	if(qId_is_calc) {
		url+= "&qId_is_calc=true";
	}
	
	if(typeof filter !== "undefined") {
		url+= "&filter=" + filter;
	}

	return url;
}

/**
 * Web service handler for survey Meta Data
 * @param {string} survey name
 */
function surveyMeta (survey) {

	var url = "/surveyKPI/survey/";
	url += survey;
	url += "/getMeta";
	return url;
}


function surveyList () {

	var url = "/surveyKPI/surveys";
	if(gCurrentProject !== 0 && gCurrentProject !== -1) {
		url += "?projectId=" + gCurrentProject;
		url += "&blocked=true";
		return url;
	} else {
		return undefined;
	}

}


function regionsURL () {

	var url = "/surveyKPI/regions";
	return url;
}

/**
 * Web service handler for retrieving items in a table
 * @param {string} survey
 */
function formItemsURL (form, getFeatures, mustHaveGeom, start_key, rec_limit, bBad, filter) {

	var url = "/surveyKPI/items/";
	
	url += form;
	ampersand = false;
	if(getFeatures == "no") {
		if(ampersand) {
			url += "&";
		} else {
			url += "?";
		}
		ampersand=true;
		url += "feats=no";
	}
	if(mustHaveGeom == "no") {
		if(ampersand) {
			url += "&";
		} else {
			url += "?";
		}
		ampersand=true;
		url += "mustHaveGeom=no";
	}
	url += "&start_key=" + start_key;
	if(rec_limit) {
		url += "&rec_limit=" + rec_limit;
	}
	if(bBad) {
		url += "&get_bad=true";
	}
	
	if(typeof filter !== "undefined") {
		url+= "&filter=" + filter;
	}

	return url;
}

/*
 * Web service handler for exporting a table
 * @param {string} table
 * @param {string} format
 */
function exportTableURL (table, format) {

	var url = "/surveyKPI/export/";
	url += table;
	url += "/" + format;
	return url;
}

/*
 * Web service handler for exporting an entire survey to CSV
 */
function exportSurveyURL (sId, filename, language, format, split_locn, forms) {

	var url = "/surveyKPI/exportSurvey/";
	
	if(!format) {
		format="xls";
	}
	if(!split_locn) {
		split_locn="false";
	}
	
	url += sId;
	url += "/" + filename;
	url += "?language=" + language;

	url += "&format=" + format;
	if(format === "xls" && split_locn === "true") {
		url += "&split_locn=true";
	}
	url+="&forms=" + forms;
	return encodeURI(url);
}

/*
 * Web service handler for exporting an entire survey to OSM
 */
function exportSurveyOSMURL (sId, filename, forms) {

	var url = "/surveyKPI/exportSurveyOSM/",
		form,
		ways = [];
	
	url += sId;
	url += "/" + filename;
	

	if(typeof forms !== undefined && forms.length > 0 ) {

		for(i = 0; i < forms.length; i++) {
			// Remove the ":false" from the form id which used by xls exports
			form = forms[i];		
			form = form.substring(0, form.lastIndexOf(":"));
			ways[i] = form;
		}
		url += "?ways=" + ways.join(',');
	}
	return encodeURI(url);
}

/*
 * Web service handler for exporting a form as a shape file
 */
function exportSurveyShapeURL (sId, filename, form, format) {

	var url = "/surveyKPI/exportSurveyMisc/";
	
	// Remove the ":false" from the form id which used by xls exports
	form = form.substring(0, form.lastIndexOf(":"));
	
	url += sId;
	url += "/" + filename;
	url += "/shape";
	url += "?form=" + form;
	url += "&format=" + format;
		
	return encodeURI(url);
}
