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

var viewIdx = 0;

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
		var surveyList = globals.gSelector.getSurveyList();
		if(!surveyList) {	// Surveys have not yet been retrieved
			getViewSurveys({sId:"-1"});
		} 

		exportSurveyChanged();
		$('#export').dialog("open");
	}); 

	/*
	 * Get the list of available custom reports
	 */
	getCustomReportList();
	
	/*
	 * Export Dialog
	 */	
	setExportControls();
	$('#exp_from_date,#exp_to_date').datepicker({ dateFormat: "yy-mm-dd" });
	$('#export').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			show:"drop",
			width: 480,
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
			        		format = $('#exportformat').val(),
			        		mediaQuestion = $('#export_media_question').val(),
			        		split_locn = $('#splitlocn:checked').prop("checked"),
			        		xlstype = $('#export_xlstype').val(),
			        		merge_select_multiple = $('#mergeSelectMultiple:checked').prop("checked"),
			        		embedImages = $('#embedImages:checked').prop("checked"),
			        		incHxl = $('#incHxl:checked').prop("checked"),
			        		exportReadOnly = $('#exportReadOnly').prop("checked"),
			        		sources = $('#sources').prop("checked"),
			        		exportReport = $('#export_report_defn').val(),
			        		forms = [],
			        		form,
			        		name_questions = [],
			        		exp_from_date = $('#exp_from_date').datepicker({ dateFormat: 'yy-mm-dd' }).val(),
			        		exp_to_date = $('#exp_to_date').datepicker({ dateFormat: 'yy-mm-dd' }).val(),
			        		dateQuestionId = $('#export_date_question option:selected').val(),
			        		exportExtended = $('#exportExtended').prop("checked"),
			        		formList;
		        		
		        		if(exportExtended) {
		        			formList = extended_model.getPath();
		        			
		        			if(!formList) {
		        				alert(localise.set["a_sel_forms"]);
		        				return(false);
		        			}
		        		}
		        			
		        		if(sId == "-1") {
		        			alert(localise.set["msg_pss"]);
		        			return(false);
		        		}
		        		
		        		// TODO validate dates
		        		if(exp_from_date && exp_to_date && exp_to_date < exp_from_date) {
		        			alert(localise.set["msg_sel_dates"]);
		        			return(false);
		        		}
	        			
		        		if(format === "osm") {
		        			forms = $(':checkbox:checked', '.osmforms').map(function() {
		        			      return this.value;
		        			    }).get();
		        			url = exportSurveyOSMURL(sId, displayName, forms, exportReadOnly,
		        					exp_from_date, exp_to_date, dateQuestionId);
		        		
		        		} else if(format === "shape" 
		        				|| format === "kml" 
		        				|| format === "vrt" 
		        				|| format === "csv" 
		        				|| format === "spss"
		        				|| format === "stata") {
		        			
		        			if(exportExtended) {
		        				form = 0;
		        			} else {
			        			forms = $(':radio:checked', '.shapeforms').map(function() {
			        			      return this.value;
			        			    }).get();
			        			if(forms.length === 0) {
			        				alert(localise.set["msg_one_f2"]);
				        			return(false);
			        			}	
			        			form = forms[0];
		        			}
		        			url = exportSurveyShapeURL(sId, displayName, form, 
		        					format, exportReadOnly, language,
		        					exp_from_date, exp_to_date, dateQuestionId, formList);
		        		
		        		} else if(format === "thingsat") {
		        			forms = $(':radio:checked', '.shapeforms').map(function() {
		        			      return this.value;
		        			    }).get();
		        			if(forms.length === 0) {
		        				alert(localise.set["msg_one_f2"]);
			        			return(false);
		        			}		
		        			url = exportSurveyThingsatURL(sId, displayName, forms[0], language,
		        					exp_from_date, exp_to_date, dateQuestionId, formList);
		        		} else if(format === "trail") {
		        			forms = $(':radio:checked', '.shapeforms').map(function() {
		        			      return this.value;
		        			    }).get();
		        			if(forms.length === 0) {
		        				alert(localise.set["msg_one_f2"]);
			        			return(false);
		        			}		
		        			var traceFormat = "shape";	// Todo add gpx
		        			var type = "trail";			// Todo allow selection of events or trail
		        			url = exportSurveyLocationURL(sId, displayName, forms[0], traceFormat, type);
		        		
		        		} else if(format === "media") {
		        			
		        			// Validate
		        			if(!mediaQuestion) {
		        				alert(localise.set["msg_sel_media"]);
			        			return(false);
		        			}
		        			name_questions = $(':checkbox:checked', '.mediaselect').map(function() {
		        			      return this.value;
		        			    }).get();
		        			
		        			url = exportSurveyMediaURL(sId, displayName, undefined, mediaQuestion, name_questions.join(','),
		        					exp_from_date, exp_to_date, dateQuestionId);
		        		
		        		} else if(format === "lqas") {
		        			
		        			url = exportSurveyLqasURL(sId, sources, exportReport);
		        		
		        		} else {
		        			// XLS export
		        			forms = $(':checkbox:checked', '.selectforms').map(function() {
		        			      return this.value;
		        			    }).get();
		        			
		        			if(forms.length === 0) {
		        				alert(localise.set["msg_one_f"]);
			        			return(false);
		        			} else {
		        				if(embedImages === true && xlstype === "html") {
			        				alert(localise.set["msg_embed"]);
				        			return(false);
			        			}
		        			}
		        			url = exportSurveyURL(sId, displayName, language, format, split_locn, 
		        					forms, exportReadOnly, merge_select_multiple, xlstype, embedImages, incHxl,
		        					exp_from_date, exp_to_date, dateQuestionId);
		        		}
		        		
		        		downloadFile(url);
		  
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	
	
	// Change event on export dialog survey select
	$('#export_survey').change(function() {
		exportSurveyChanged();
 	});
	
	// Change event on including linked surveys
	$('#exportExtended').change(function() {
		exportExtendedChanged();
 	});
	
	/*
	 * Change event on export format select
	 */
	$('#exportformat').change(function(){
		setExportControls();
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
	
	// Edit thingsat button
	$('#btn_edit_thingsat').button().off().click(function(){
		require(['app/neo_model'], function(neo_model) {
			var sId = $('#export_survey option:selected').val(),
				language = $('#export_language option:selected').val(),
				form,
				forms = $(':radio:checked', '.shapeforms').map(function() {
					return this.value;
			    }).get();
			
			var sMeta = globals.gSelector.getSurvey(sId);
			
			if(forms.length === 0) {
				alert(localise.set["msg_one_f2"]);
				return(false);
			}
			
			if(sId != -1) {
				neo_model.init(sId, forms[0], language, sMeta.model);
				neo_model.showModel('#ta_model_edit', 300, 200);
				neo_model.showTable('#ta_items_edit');
				neo_model.startEdit();
			} 
		});	
	});

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
				globals.gEditingReportProject = aParam[1];
				
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
					  alert(localise.set("c_error"));
					  gReportIdent = undefined;
				  }
			});
				
		}
	}
 	
});

function exportSurveyChanged() {
	var sId = $('#export_survey option:selected').val(),
		languages = globals.gSelector.getSurveyLanguages(sId),
		sMeta,
		questions;

	if(sId > 0) {
		if(!languages) {
			getLanguageList(sId, addMediaPickList);		// Retrieve the languages and questions for the default language
		} else {
			setSurveyViewLanguages(languages, undefined, '#settings_language', false );
			setSurveyViewLanguages(languages, undefined, '#export_language', true );
			questions = globals.gSelector.getSurveyQuestions(sId, languages[0].name);
			addMediaPickList();
		}
		
		sMeta = globals.gSelector.getSurvey(sId);
		if(!sMeta) {
			getSurveyMetaSE(sId, {}, false,true, true, false, false);
		} else {
			addFormPickList(sMeta);
			addDatePickList(sMeta);
		}
		
		// Update the thingsat model if we changed the survey
		if($('#exportformat').val() === "thingsat") {
			showModel();
		}
		
		exportExtendedChanged();
	} else {
		$('#export_date_question').html("");
	}
}

function exportExtendedChanged() {
	require(['app/extended_model'], function(extended_model) {
		
		var expExtended = $('#exportExtended').prop("checked"),
			sId = $('#export_survey option:selected').val(),
			sMeta;
		
		if(expExtended) {
			
			$('.showextselect').show();
			$('.showshape').hide();
			sMeta = globals.gSelector.getSurveyExtended(sId);
			if(!sMeta) {
				sMeta = getExtendedSurveyMetaSE(sId, extended_model.convertMetaToGraph);
			} else {
				extended_model.convertMetaToGraph(sMeta);
			}
		} else {
			
			setExportControls();
			$('.showextselect').hide();
			
		}
	});

}

function setExportControls() {
	var format = $('#exportformat').val();
	
	// Make sure the export button is enabled as export to things at may have disabled it
	$('#export').next().find("button:contains('Export')").removeClass("ui-state-disabled");
	
	if(format === "osm") {
		$('.showshape,.showspreadsheet,.showxls,.showthingsat,.showtrail, .showmedia, .showlqas, .showext').hide();
		$('.showosm,.showro,.showlang').show();
	} else if(format === "shape" || format === "kml" || format === "vrt" || format === "csv") {
		$('.showspreadsheet,.showxls,.showosm,.showthingsat, .showmedia, .showlqas').hide();
		$('.showshape,.showro,.showlang, .showext').show();
	} else if(format === "stata" || format === "spss") {
		$('.showxls,.showosm,.showthingsat, .showmedia, .showlqas, .showext').hide();
		$('.showshape,.showspreadsheet,.showro,.showlang').show();
	} else if(format === "thingsat") {
		$('.showxls,.showosm, .showmedia, .showlqas, .showext').hide();
		$('.showshape,.showspreadsheet,.showro,.showlang').show();
		showModel();			// Show the thingsat model
	} else if(format === "trail") {
		$('.showxls,.showosm,.showro,.showlang,.showthingsat, .showmedia, .showlqas, .showext').hide();
		$('.showshape,.showspreadsheet').show();
	} else if(format === "media") {
		$('.showshape, .showxls,.showosm,.showro,.showlang,.showthingsat,.showmedia, .showlqas, .showext').hide();
		$('.showspreadsheet,.showmedia, .showlang').show();
	} else if(format === "lqas") {
		$('.showshape, .showxls,.showosm,.showro,.showlang,.showthingsat,.showmedia, .showlqas, .showext').hide();
		$('.showlqas').show();
		getReports(showReportList, undefined, "lqas");
		
	} else {
		$('.showshape,.showspreadsheet,.showxls,.showosm,.showthingsat, .showmedia, .showlqas, .showext').hide();
		$('.showxls,.showspreadsheet,.showro,.showlang').show();
	}
}

/*
 * Add the pick list for media export
 */
function addMediaPickList() {
	
	var sId = $('#export_survey option:selected').val(),
		format = $('#exportformat').val(),
		languages = globals.gSelector.getSurveyLanguages(sId),
		questions = globals.gSelector.getSurveyQuestions(sId, languages[0]),
		i,
		h = [],
		idx = -1,
		h2 = [],
		idx2 = -1;
	
	/*
	 * Add the media question select list
	 */
	if(typeof questions !== "undefined") {
		for(i = 0; i < questions.length; i++) {
			if(questions[i].type === "image" || questions[i].type === "video" || questions[i].type === "audio") {
				h[++idx] = '<option value="';
				h[++idx] = questions[i].id;
				h[++idx] = '">';
				h[++idx] = questions[i].name;
				h[++idx] = '</option>';
			} else if(questions[i].name !== "_task_key") {
				
				if(questions[i].type === "string" ||
					questions[i].type === "select1" ||
					questions[i].type === "date" ||
					questions[i].type === "dateTime" ||
					questions[i].type === "int" ||
					questions[i].type === "decimal" ||
					questions[i].type === "barcode" ||
					questions[i].type === "geopoint"
					) {
					
					h2[++idx2] = '<div class="checkbox"><label><input type="checkbox" name="mediaselect" value="';
					h2[++idx2] = questions[i].id;
					h2[++idx2] = '" class="mediaselectoption"/>';
					h2[++idx2] = questions[i].name;
					h2[++idx2] = '</label></div>';
				}
				
			}
		}
		
		if(idx === -1 && format === "media") {
			alert(localise.set["msg_nm"]);
		}
		$('#export_media_question').html(h.join(''));
		$('.mediaselect').html(h2.join(''));
	}

}

/*
 * Get the list of available projects from the server
 */
function getCustomReportList() {
	addHourglass();
	$.ajax({
		url: "/surveyKPI/custom_reports?type=oversight&negateType=true",
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			addCustomReportList(data);
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get list of custom reports: " + err);
			}
		}
	});	
}

/*
 * Add the Custom Report Configuration Select list
 */
function addCustomReportList(templates) {
	
	var i,
		h = [],
		idx = -1;
	
	for(i = 0; i < templates.length; i++) {
		h[++idx] = '<option value="';
		h[++idx] = templates[i].id;
		h[++idx] = '">';
		h[++idx] = templates[i].name;
		h[++idx] = '</option>';
	}
	
	$('#export_report_defn').html(h.join(''));

}

/* 
 * Show a newo4J model of the survey
 */
function showModel() {
	require(['app/neo_model'], function(neo_model) {
		var sId = $('#export_survey option:selected').val();
		if(sId != -1) {
			var sMeta = globals.gSelector.getSurvey(sId);
			if(!sMeta) {
				 getSurveyMetaSE(sId, {}, false, false, false, undefined, neo_model);
			} else {
				$('.showthingsat').show();
				
				// Set the form to the value stored in the model
				if(sMeta.model) {
					var graph = JSON.parse(sMeta.model);
					$('.osmform[value=' + graph.form + ']').prop("checked", "checked");
				}
				
				neo_model.init(sId, undefined, undefined, sMeta.model);
				neo_model.showModel('#ta_model_show', 300, 200);
			}
		} else {
				neo_model.clear('#ta_model_show');
		}
	});
}



/*
 * Add a list of forms to pick from during export
 */
function addFormPickList(sMeta) {
	
	var h = [],
	idx = -1,
	i;
	
	// Start with the top level form
	for(i = 0; i < sMeta.forms.length; i++) {
		if(sMeta.forms[i].p_id == 0) {
			$(".osmforms").html(addFormToList(sMeta.forms[i], sMeta, 0, true, false));
			$(".selectforms").html(addFormToList(sMeta.forms[i], sMeta, 0, false, false));
			$(".shapeforms,.taforms").html(addFormToList(sMeta.forms[i], sMeta, 0, true, true));
		}
	}

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

	var qList = globals.gSelector.getSurveyQuestions(sId, language),
		i,
		qInfo;
	
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
	
	if(sMeta && sMeta.dates) {
		for(i = 0; i < sMeta.dates.length; i++) {
			
			h[++idx] = '<option value="';
			h[++idx] = sMeta.dates[i].id;
			h[++idx] = '">';
			h[++idx] = sMeta.dates[i].name;
			h[++idx] = '</option>';
			
		}
		
		$(".date_question").html((h.join('')));
		
		if(typeof currentDate !== "undefined" && currentDate != 0) {
			value = currentDate;
		} else {
			value = $("#settings_date_question").val();
		}
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
	if(!osm) {
		h[++idx] = ':false"/>';
	} else {
		h[++idx] = '"/>';
	}
	h[++idx] = form.form;
	if(form.p_id != 0 && !osm) {
		h[++idx] = ' <button class="exportpivot">Pivot</button>';
	}
	h[++idx] = '<br/>';
	h[++idx] = '</span>';
	
	// Add the children (recursively)
	for(i = 0; i < sMeta.forms.length; i++) {
		if(sMeta.forms[i].p_id != 0  && sMeta.forms[i].p_id == form.f_id) {
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

function toggleBadURL(form, pKey) {

	var url = "/surveyKPI/items/";	
	url += form;
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
	if(globals.gCurrentProject !== 0 && globals.gCurrentProject !== -1) {
		url += "?projectId=" + globals.gCurrentProject;
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
function formItemsURL (form, getFeatures, mustHaveGeom, start_key, rec_limit, bBad, filter, dateId, startDate, endDate) {

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
	
	if(dateId != null) {
		url += "&dateId=" + dateId;
	}
	
	if(typeof startDate !== "undefined" && startDate.length > 0) {
		url+= "&startDate=" + startDate;
	}
	
	if(typeof endDate !== "undefined" && endDate.length > 0) {
		url+= "&endDate=" + endDate;
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
function exportSurveyURL (
		sId, 
		filename, 
		language, 
		format, 
		split_locn, 
		forms, 
		exp_ro, 
		merge_select_multiple, 
		xlstype, 
		embedImages,
		incHxl,
		exp_from_date,
		exp_to_date,
		dateQuestionId) {

	var url;
	if(xlstype === "html") {
		url = "/surveyKPI/exportSurvey/";
	} else {
		url = "/surveyKPI/exportxls/";
	}
	
	filename = cleanFileName(filename);	
	
	exp_ro = exp_ro || false;
	
	if(!format) {
		format="xls";
	}
	
	url += sId;
	url += "/" + filename;
	url += "?language=" + language;

	url += "&format=" + format;
	if(format === "xls" && split_locn === true) {
		url += "&split_locn=true";
	}
	if(format === "xls" && merge_select_multiple === true) {
		url += "&merge_select_multiple=true";
	}
	url += "&forms=" + forms;
	url += "&exp_ro=" + exp_ro;
	if(typeof embedImages !== "undefined") {
		url += "&embedimages=" + embedImages;
	}
	if(typeof incHxl !== "undefined") {
		url += "&hxl=" + incHxl;
	}
	
	if(xlstype != "html") {
		url += "&filetype=" + xlstype;
	}
	if(dateQuestionId > 0 || dateQuestionId == -100) {	// -100 is a pseudo ID for Upload Time
		url += "&dateId=" + dateQuestionId;
		if(exp_from_date) {
			url += "&from=" + exp_from_date;
		}
		if(exp_to_date) {
			url += "&to=" + exp_to_date;
		}
	}
	
	return encodeURI(url);
}

function exportSurveyMediaURL (sId, filename, form, mediaQuestion, nameQuestions,
		exp_from_date,
		exp_to_date,
		dateQuestionId
		) {

	var url = "/surveyKPI/exportSurveyMedia/";
	
	filename = cleanFileName(filename);	
	
	
	
	url += sId;
	url += "/" + filename;
	
	url+="?mediaquestion=" + mediaQuestion;
	if(nameQuestions && nameQuestions.trim().length > 0) {
		url+="&namequestions=" + nameQuestions;
	}

	if(dateQuestionId > 0 || dateQuestionId == -100) {	// -100 is a pseudo ID for Upload Time
		url += "&dateId=" + dateQuestionId;
		if(exp_from_date) {
			url += "&from=" + exp_from_date;
		}
		if(exp_to_date) {
			url += "&to=" + exp_to_date;
		}
	}
	
	return encodeURI(url);
}

function exportSurveyLqasURL (sId, sources, reportDefn,
		exp_from_date,
		exp_to_date,
		dateQuestionId) {

	var url = "/surveyKPI/lqasExport/",
		hasParam = false;

	url += sId;
	url += "/" + reportDefn;
	
	if(sources) {
		url+="?sources=true";
		hasParam = true;
	}
	
	if(dateQuestionId > 0) {
		if(hasParam) {
			url += "&";
		} else {
			url += "?";
		}
		url += "dateId=" + dateQuestionId;
		
		if(exp_from_date) {
			url += "&from=" + exp_from_date;
		}
		if(exp_to_date) {
			url += "&to=" + exp_to_date;
		}
	}
	
	return encodeURI(url);
}

/*
 * Web service handler for exporting an entire survey to OSM
 */
function exportSurveyOSMURL (sId, filename, forms, exp_ro,
		exp_from_date,
		exp_to_date,
		dateQuestionId) {

	var url = "/surveyKPI/exportSurveyOSM/",
		form,
		ways = [];
		
	filename = cleanFileName(filename);	
	
	exp_ro = exp_ro || false;
	
	url += sId;
	url += "/" + filename;
	

	if(typeof forms !== undefined && forms.length > 0 ) {
		url += "?ways=" + forms.join(',');
		url+= "&exp_ro=" + exp_ro;
	} else {
		url += "?exp_ro=" + exp_ro;
	}
	
	if(dateQuestionId > 0 || dateQuestionId == -100) {	// -100 is a pseudo ID for Upload Time
		url += "&dateId=" + dateQuestionId;
		if(exp_from_date) {
			url += "&from=" + exp_from_date;
		}
		if(exp_to_date) {
			url += "&to=" + exp_to_date;
		}
	}
	
	return encodeURI(url);
}

/*
 * Web service handler for exporting a form as a shape file
 */
function exportSurveyShapeURL (sId, filename, form, format, exp_ro, language,
		exp_from_date,
		exp_to_date,
		dateQuestionId,
		formList) {

	var url = "/surveyKPI/exportSurveyMisc/";
	
	exp_ro = exp_ro || false;
	
	filename = cleanFileName(filename);	
	
	// Remove the ":false" from the form id which used by xls exports
	//form = form.substring(0, form.lastIndexOf(":"));
	
	url += sId;
	url += "/" + filename;
	url += "/shape";
	url += "?form=" + form;
	url += "&format=" + format;
	url += "&exp_ro=" + exp_ro;
	url += "&language=" + language;
		
	if(dateQuestionId > 0 || dateQuestionId == -100) {	// -100 is a pseudo ID for Upload Time
		url += "&dateId=" + dateQuestionId;
		if(exp_from_date) {
			url += "&from=" + exp_from_date;
		}
		if(exp_to_date) {
			url += "&to=" + exp_to_date;
		}
	}
	
	if(formList != null) {
		url += "&forms=" + JSON.stringify(formList);
	}
	
	return encodeURI(url);
}

/*
 * Web service handler for exporting a form as a shape file
 */
function exportSurveyThingsatURL (sId, filename, form, language) {

	var url = "/surveyKPI/exportSurveyThingsat/";
	
	
	filename = cleanFileName(filename);	
	
	url += sId;
	url += "/" + filename;
	url += "?form=" + form;
	url += "&language=" + language;
		
	return encodeURI(url);
}

/*
 * Web service handler for exporting a form as a shape file
 */
function exportSurveyLocationURL (sId, filename, form, format, type) {

	var url = "/surveyKPI/exportSurveyLocation/";
	
	
	filename = cleanFileName(filename);	
	
	url += sId;
	url += "/" + filename;
	url += "?form=" + form;
	url += "&format=" + format;
	url += "&type=" + type;
		
	return encodeURI(url);
}
