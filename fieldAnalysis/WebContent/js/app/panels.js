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
 * Purpose: Manage the panels that display graphs, maps etc of results data
 * 
 * translations: partial
 */

// HTML fragments for constructing panels

define(['jquery', 'jquery_ui', 'localise', 'common', 'globals','app/script', 'rmm', 'app/neo_model'], 
		function($, ui, lang, common, globals, script, rmm, neo_model) {
	
var hstart = '<li class="ui-state-default pSmall" id="p';
var hstart2 = '">';
var hend = '</li>';

var hdiv1 = '<div>';
var hdiv2 = '</div>';
var hcont = '<div class="pContent">';

var hspan1 = '<span class="panel_title">';
var hspan2 = '</span>';

var hdel1 = '<img class="pDel" value="';
var hdel2 = '" src="img/delete.png"/>';
var hexp1 = '<img class="pExpand" value="';
var hexp2 = '" src="img/expand.png"/>';
var hset1 = '<img class="pSettings" value="';
var hset2 = '" src="img/settings16.png"/>';
var hrep1 = '<img class="pReport" value="';
var hrep2 = '" src="img/report.png"/>';

var hmap1 = '<a class="slide" href="#slideLeft">\<</a>';
var hmap2 = '<div style="height: 100%; width: 100%;" >';
var hmap3 =	'<div style="" class="r_overview"><div class="r_description"></div></div>';
var hmap4 = '<div class="analysis map_panel" id="map_panel';
var hmap5 = '"></div>';		// close map panel
var hmap6 = '</div>';       // close overview and map panel
var hmap7 = '<div class="pSidebar">';
var hmap8 = 	'<h3>' + localise.set["c_layers"] + '</h3>';
var hmap9 =		'<div id="mLayers';
var hmap10 =		'"></div>';
var hmap11 = 	'<h3>' + localise.set["c_data"] + '</h3>';
var hmap12 =		'<div class="dataOptions" id="mDataOptions';
var hmap13 = 	'"></div>';
var hmap14 = 	'<h3>' + localise.set["c_settings"] + '</h3>';
var hmap15 =	'<div id="mLayerSettings';
var hmap16 = 	'"></div>';
var hmap17 = '</div>';

var hmapTs1 = '<div class="timecontrols" style="display:none;"><div class="timecontrols_inner">';
var hmapTs2 = 'Interval: <select name="span"></select>';
var hmapTs3 = '<button class="starttimer">' + localise.set["c_start"] + '</button>';
var hmapTs4 = '<input type="text" class="slide_date1" readonly>';
var hmapTs5 = '<input type="text" class="slide_date2" readonly>';
var hmapTs6 = '<div style="clear: both;"></div>';
var hmapTs7 = '<div class="slider-range"></div>';
var hmapTs8 = '</div></div>';

var hgraph1 = '<a class="slide" href="#slideLeft">\<</a><a class="graphLabel">L</a>';
var hgraph2 = '<div class="analysis graph_panel" id="graph_panel';
var hgraph3 = '">';
var hgraph4 = 	'<div class="r_overview"><div class="r_description"></div></div>';
var hgraph5 = 	'<div id="chartdiv';
var hgraph6 = 	'" style="height:90%;width:100%; "></div>';
var hgraph7 = '</div>';
var hgraph8 = '<div class="pSidebar">';
var hgraph9 = 	'<h3>' + localise.set["c_options"] + '</h3>';
var hgraph10 = 	'<div id="mLayers';
var hgraph11 = 	'"></div>';
var hgraph12 = 	'<h3>' + localise.set["c_data"] + '</h3>';
var hgraph13 = 	'<div class="dataOptions" id="mDataOptions';
var hgraph14 = 	'">';
var hgraph15 = '</div>';

var htable1 = '<a class="slide" href="#slideLeft">\<</a>';
var htable2 = '<div class="analysis table_panel" id="table_panel';
var htable3 = '"></div>';
var htable4 = '<div class="pSidebar">';
var htable5 = 	'<h3>' + localise.set["c_tables"] + '</h3>';
var htable6 =	'<div class="phead"></div>';
var htable7 = 	'<h3>' + localise.set["m_export"] + '</h3>';
var htable8 =	'<div class="pfoot">';
var htable9 =		'<form action="services/getCSV.php" method="post">';
var htable10 =			'<input type="hidden" name="csv_text" class="csv_text"/>';
var htable11 = 			'<input type="hidden" name="csv_name" class="csv_name"/>';
var htable12 =			'<input type="submit" value="Export Table" class="tExport" />';
var htable13 = 		'</form>';
var htable14 =		'<button class="tDelete">' + localise.set["c_del"] + '</button>';
var htable15 =	'</div>' ;
var htable16 = '</div>';

var hmedia1	= '<div class="analysis media_panel" id="media_panel';
var hmedia2 = '"><div class="image_wrap"><img src="img/blank.gif" width="512" height="344" /></div>' +
	'<div class="flow_player_wrap"><div id="flow_player';
var hmedia3 = '"><div class="player"></div></div></div><br clear="all" /><div class="scrollable" id="scrollable';
var hmedia4 = '"></div></div>';

var gNewPanel = false;	// Set to true when editing settings of a new panel
var gExpandedPanelSeq;	// Set to the sequence number of a newly created panel

$(document).ready(function() {
	
	var aDate;
	console.log("panels");
	localise.setlang();		// Localise HTML
	
    // Add a new panel button click
    $('.db_settings_add').click(function() { 
    	addNewPanel($(this).val());
    });
    
    
    // ** End Translations **
    
	 // Initialse the settings dialog
	 $('#p_settings').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			show:"drop",
			width:600,
			zIndex: 2000,
			buttons: [
		        {
		        	text: "Cancel",
		        	click: function() {
		        		if(gNewPanel) {
		        			gNewPanel = false;
		        			delPanel($('#p' + globals.gViewIdx).find('.pDel'), globals.gViewIdx);
		        		}
		        		$(this).dialog("close");
		        	}
		        }, {
		        	text: "Save",
		        	click: function() {
		        		var i, views, view,
		        			newType, newTitle,
		        			qMeta, groupMeta,
		        			qId,
		        			sId,
		        			qId_is_calc = false;
		        		
		        		// Check that the meta data for the question has been retrieved
		        		if(gMetaInProgress !== 0) {
		        			//alert("Waiting for a response from the server. Please try again in a few seconds");
		        			alert(localise.set["msg_wait"]);
		        			return false;
		        		}
		        		
		        		// Check that the settings are valid
						newType = $('#settings_type option:selected').attr("value");
						newTitle = $('#settings_title').val();
		        		qId = $('#settings_question option:selected').val();
		        		if(qId.indexOf(":") > 0) {
		        			var calcs = qId.split(":");
		        			if(calcs.length > 0) {
		        				qId = calcs[1];
		        				qId_is_calc = true;
		        			}
	 					} 
						sId  = $('#settings_survey option:selected').val();
		        		if(sId == "-1") {
							//alert("You must select a survey");
							alert(localise.set["msg_sel_survey"]);
		        			return false;
		        		}
		        		if(newType == "graph" && qId == "-1") {
							//alert("You must select a question to show on a graph");
		        			alert(localise.set["msg_sel_q"]);
		        			return false;
						}
		        		
		        		gNewPanel = false;
		        		views = globals.gSelector.getViews();
						view = views[globals.gViewIdx];

						view.sId  = sId;
						view.qId = qId;
						view.qId_is_calc = qId_is_calc;
						
						view.dateQuestionId = $('#settings_date_question option:selected').val();
						view.timeGroup = $('#time_group').val();
						view.fromDate = $('#from_date').datepicker({ dateFormat: 'yy-mm-dd' }).val();
						if(typeof view.fromDate !== "undefined" && view.fromDate.length === 0) {
							view.fromDate = undefined;
						}
						view.toDate = $('#to_date').datepicker({ dateFormat: 'yy-mm-dd' }).val();
						if(typeof view.toDate !== "undefined" && view.toDate.length === 0) {
							view.toDate = undefined;
						}
						
						if(newType === "map") {
							view.groupQuestionId = gSurveyControlView.geometryQuestion;
							view.groupQuestionText = "Location";
							view.region = $('#settings_group option:selected').val();
						} else {
							view.groupQuestionId = $('#settings_group option:selected').val();
							view.groupQuestionText = $('#settings_group option:selected').text();
							if(view.groupQuestionText == "None") {
								view.groupQuestionText = "";
							}
			        		view.region = "none";
						}
						// Determine if we need to redraw the panel
						if(newType !== view.type) {
							setPanelType(newType, globals.gViewIdx, view.timeGroup, view.qId);
						}
						if(newTitle !== view.title) {	// Set the new title
							$('#p' + view.pId).find('span').html(newTitle);
						}
						view.type = newType;
						view.title = newTitle;
						view.layerId = $('#display_panel option:selected').val();
						if(view.layerId != "-1") {
							view.state = "minimised";
						}
						view.sName = $('#settings_survey option:selected').text();
						view.question = $('#settings_question option:selected').text();
		        		view.lang = $('#settings_language option:selected').val();
		        		view.fn = $('#q1_function option:selected').val();
		        		
		        		view.key_words = $('#settings_key_words').val();
		        		
		        		// Set the data names and labels, this is the question / options that the user wants to view
		        		qMeta = globals.gSelector.getQuestion(view.qId, view.lang);
		        		
		        		// Set the group type
		        		groupMeta = globals.gSelector.getQuestion(view.groupQuestionId, view.lang);
		        		if(groupMeta && view.groupQuestionId != "-1") {
		        			if(groupMeta.type === "geopoint" || groupMeta.type === "geopolygon" || 
		        					groupMeta.type === "geolinestring" || 
		        					groupMeta.type === "geoshape" ||
		        					groupMeta.type === "geotrace" ||
		        					groupMeta.type === "string") {
		        				view.groupType = groupMeta.type;
		        			} else {
		        				view.groupType = "normal";
		        			}
		        		} else {
			        		// No group was selected
			        		view.groupType = "normal";
		        		}
		        		
		        		view.filter = getFilter();
		        		
		        		savePanels(view);	// Save to the database
			        	$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	
	 /*
	  * Get the user details so we have the default project
	  * Then load the available projects for the user and load the panels for the default project
	  */
	getLoggedInUser(getPanels, false, true);
	
	// Set change function on projects
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;

		getPanels(globals.gCurrentProject);				
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id, survey id
		getViewSurveys({sId:"-1"});				// Update the survey list to match the new project
 	 });
	
	enableUserProfile();
	
});

/*
 * Remove any text enclosed in brackets ( ), this is assumed to be hints to the enumerator
 * and does not need to be displayed
 */
function removeHint(input) {
	var startIdx, endIdx, newString;
	
	while((startIdx = input.indexOf('(')) != -1) {
		endIdx = input.indexOf(')');
		if(endIdx > startIdx) {
			newString = input.substring(0, startIdx) + input.substring(endIdx + 1);
			input = newString;
		} else {
			// Badly structured just wipe everything after the bracket
			input = input.substring(0, startIdx);
		}
	}
	
	return input;
}

function getPanels(projectId) {
	
	if(projectId != -1) {
		addHourglass();
	 	$.ajax({
			url: dashboardURL(projectId),
			dataType: 'json',
			cache: false,
			success: function(data) {
				removeHourglass();
				globals.gSelector.setViews(data);
				refreshPanels();	// nodbl	
			},
			error: function(data) {
				removeHourglass();
				if(data.status === 401) {
					$('#status_msg_msg').empty().text("Not Authorised");
				} else {
					$('#status_msg_msg').empty().text("Error: Failed to get dashboard settings");
				}
				$("#status_msg").dialog("open");
			}
		});
	}
 	
}

// Create the dashboard list
function createDashboard() {
	// Create the panels
	refreshPanels();
}



// Re-create panels based on the current view list
function refreshPanels() {
		
	var views, $panels,
		multiLayerMaps = [],
		i,j, idx;
	
	addHourglass();
	// Get the view list
	//dbList = $( "#db_list" ).sortable("toArray"),  ndbl
	views = globals.gSelector.getViews();
	
	$panels = $('#panels');
	$panels.empty();	// Remove existing panels
	
	for(i = 0; i < views.length; i++) {
		if(views[i].state != "deleted") {
			createPanel(i, $panels, views[i].title, views[i].sName);
			setPanelType(views[i].type, i, views[i].timeGroup, views[i].qId);
			getData(views[i]);		
		}
	}
	
	addTriggers();	// Add triggers

	// Override the settings for expanded / shown. This is due to the expanded / shown state not being saved on change to the database so the database is probably wrong
	for(i = 0; i < views.length; i++) {
		if(views[i].state !== "deleted") {
			if(views[i].seq === gExpandedPanelSeq && views[i].layerId == "-1") {		// Make sure new panels are expanded unless the data is to be shown on a different panel (ie map layers)
				$('#p' + i).find('.pExpand').trigger('click');
			} else if(views[i].state === "minimised") {
				setPanelState(views[i], i, "minimised");
			} else if(views[i].state === "expanded") {
				// Expanded but not the correct sequence
				views[i].state = "shown";
			}
		}
	}
	removeHourglass();
}

// Set the state of the panel based on the view settings
function setPanelState(view, idx, oldState) {
	$panel = $('#p' + idx);
	$panel.removeClass("shown expanded delete minimised").addClass(view.state);
	
	switch(view.state) {
	case "shown":
		$panel.find('.pExpand').attr("src", "img/expand.png");
		$panel.removeClass('pLarge pHide').addClass('pSmall');
		break;
	case "expanded":
		$panel.find('.pExpand').attr("src", "img/show.png");
		$panel.removeClass('pSmall pHide').addClass('pLarge');
		break;
	case "minimised":
		$panel.removeClass('pSmall pHide').addClass('pHide');
		break;
	}
	
}


// Add triggers to panel menu items
function addTriggers() {
	
	// Add triggers
	$('.pSettings').off().click(function() {	// display the settings dialog
		showSettings($(this));
	});
	
	$('.pExpand').off().click(function() {	// Toggle expanded panel
		expandFunction($(this));
	});
	
	$('.pDel').off().click(function() {	// Delete a panel
		delPanel($(this));
	});
	
	$('.pReport').off().click(function() {	// Export a panel
		exportReport($(this));
	});
	
	$('.slide').off().click(function() {	// Slide left right panels
		slide($(this));
	});
	
	$('.set_date_range').off().click(function() {	// Slide left right panels
		set_date_range($(this));
	});
	
	$('.clear_date_range').off().click(function() {	// Slide left right panels
		clear_date_range($(this));
	});
}

// Add a new panel on to the end of the current panels
function addNewPanel(type) {
	
	var views = globals.gSelector.getViews();
	var idx = 0;
	if(views) {
		idx = views.length;
	}

	views[idx] = {id:-1, seq: idx, state: "shown", title:"New Chart", pId: idx, 
			sId:-1, type:type, region:"None", lang:"", qId:"-1", table:"", groupId:-1,
			groupType:"normal"};		// default to graph type
	gExpandedPanelSeq = idx;	// Make sure this panel is shown as expanded when the panels are initially refreshed. This happens after panel creation

	var $panels = $('#panels');
	createPanel(idx, $panels, views[idx].title, views[idx].sName);
	setPanelType(views[idx].type, idx, views[idx].timeGroup, views[idx].qId);
	addTriggers();
	gNewPanel = true;
	$('#p' + idx).find('.pSettings,.pExpand').trigger('click');		// Make the new panel full screen and edit the settings
}

// delete a panel
function delPanel($this, idx) {

	var msg,
		decision = false,
		views,
		e;		// event
	
	if(typeof idx === "undefined") {
		// Confirm that the user wants to delete the panel and get the panel index
		//msg = "Are you sure you want to delete the panel?";
		//decision = confirm(msg);
		decision = true;	// Asking the user for confirmation is unnecessary
		idx = $this.attr("value");
	} else {
		// Delete called to explicitely remove the specified panel
		decision = true;
	}
	
	if (decision === true) {
			
		views = globals.gSelector.getViews();
		if(views[idx].state === "expanded") {
			gExpandedPanelSeq = idx;	
			$('.pSmall').removeClass('pHide');	// unhide other panels
		}
		views[idx].state = "deleted";

		$('#p' + idx).remove();
		
		// Send a resized event to the analysis panels in case they need to take some action
		e = jQuery.Event("resized");
		$('.analysis').trigger(e);
		savePanelState(views[idx]);	// Save the state
		if(views[idx].layerId != "-1") {
			refreshPanels();	// If this panel's data was stored in a different panel then refresh all the panels to remove it
		}
	}
}


// Create a single panel 
function createPanel(idx, $panels, title, surveyName) {
	
	
	var h = [],
		i = -1;
	
	// If the Chart title is the default "New Chart" then set to the survey name
	if(title === "New Chart") {
		title = surveyName;
	}

	
	h[++i] = hstart;
	h[++i] = idx;
	h[++i] = hstart2;
	h[++i] = hdiv1;
	h[++i] = hdel1;
	h[++i] = idx;
	h[++i] = hdel2;
	h[++i] = hspan1;
	h[++i] = title;
	h[++i] = hspan2;
	h[++i] = hexp1;
	h[++i] = idx;
	h[++i] = hexp2;
	h[++i] = hset1;
	h[++i] = idx;
	h[++i] = hset2;
	h[++i] = hrep1;
	h[++i] = idx;
	h[++i] = hrep2;
	h[++i] = hdiv2;
	h[++i] = hcont;
	h[++i] = hdiv2;
	h[++i] = hend;
	
	$panels.append(h.join(''));
}

//Create a single panel for the passed in view
function setPanelType(type, idx, period, qId) {
	
	$panelContent = $('#p' + idx).find('.pContent');
	$panelContent.empty();
	
	var h = [];
	var i = -1;
	
	switch(type) {
	case "map":
		h[++i] = hmap1;
		h[++i] = hmap2;
		h[++i] = hmap3;
		h[++i] = hmap4;
		h[++i] = idx;
		h[++i] = hmap5;
		h[++i] = hmap6;
		if(typeof period !== "undefined" && period !== "none") {
			h[++i] = hmapTs1;
			h[++i] = hmapTs2;
			h[++i] = period;
			h[++i] = "(s)";
			h[++i] = hmapTs3;
			h[++i] = hmapTs4;
			h[++i] = hmapTs5;
			h[++i] = hmapTs6;
			h[++i] = hmapTs7;
			h[++i] = hmapTs8;
		}
		h[++i] = hmap7;
		h[++i] = hmap8;
		h[++i] = hmap9;
		h[++i] = idx;
		h[++i] = hmap10;
		h[++i] = hmap11;
		h[++i] = hmap12;
		h[++i] = idx;
		h[++i] = hmap13;
		h[++i] = hmap14;
		h[++i] = hmap15;
		h[++i] = idx;
		h[++i] = hmap16;
		h[++i] = hmap17;
		$panelContent.append(h.join(''));
		initializeMap(idx);
		break;
	case "table":
		h[++i] = htable1;
		h[++i] = htable2;
		h[++i] = idx;
		h[++i] = htable3;
		h[++i] = htable4;
		if(typeof qId === "undefined" || qId == -1) {
			h[++i] = htable5;
			h[++i] = htable6;
		}
		h[++i] = htable7;
		h[++i] = htable8;
		h[++i] = htable9;
		h[++i] = htable10;
		h[++i] = htable11;
		h[++i] = htable12;
		h[++i] = htable13;
		h[++i] = htable14;
		h[++i] = htable15;
		h[++i] = htable16;
		$panelContent.append(h.join(''));
		break;
	case "graph":
		h[++i] = hgraph1;
		h[++i] = hgraph2;
		h[++i] = idx;
		h[++i] = hgraph3;
		h[++i] = hgraph4;
		h[++i] = hgraph5;
		h[++i] = idx;
		h[++i] = hgraph6;
		h[++i] = hgraph7;
		h[++i] = hgraph8;
		h[++i] = hgraph9;
		h[++i] = hgraph10;
		h[++i] = idx;
		h[++i] = hgraph11;
		h[++i] = hgraph12;
		h[++i] = hgraph13;
		h[++i] = idx;
		h[++i] = hgraph14;
		h[++i] = hgraph15;
		$panelContent.append(h.join(''));
		break;
	case "media":
		h[++i] = hmedia1;
		h[++i] = idx;
		h[++i] = hmedia2;
		h[++i] = idx;
		h[++i] = hmedia3;
		h[++i] = idx;
		h[++i] = hmedia4;	
		$panelContent.append(h.join(''));
		break;
	default:
		console.log("No type");
		break;
	}
	
}

// Handle export item to report
function exportReport($this) {
	
	var $data, map,
		html,
		viewIdx = $this.attr("value"),
		views = globals.gSelector.getViews(),
		canvas, canvasData, item,
		csvValue,
		format, num,
		i,
		items,
		multiLayers = [];		// Extension of results to allow for multiple map layers
	
	// Get the report item and add it to the report dialog
	if(views[viewIdx].type == "graph")  {
		
		//var canvas = $('#chartdiv' + views[viewIdx].pId).jqplotToImage(0, 0);
		canvas = jqplotToImg($('#chartdiv' + views[viewIdx].pId));
		canvasData = canvas.toDataURL();
		gReport = {
				title: views[viewIdx].title, 
				smap: {
					data_gen_capture: canvasData,
					data_gen: JSON.stringify(views[viewIdx].results),
					data_gen_type: "graph",
					sId: views[viewIdx].sId
				},
				type: "data_url",
				action: "new"
				};
		
	} else if(views[viewIdx].type == "table"){
		
		$data = $('#table_panel' + views[viewIdx].pId).find('table');
		csvValue = $data.table2CSV({delivery:'value'});
		
		gReport = {
				title: views[viewIdx].title, 
				type: 'rich',
				smap: {
					data_gen_type: "table",
					data_gen: JSON.stringify(views[viewIdx].results),
					data_gen_capture: csvValue,
					sId: views[viewIdx].sId
				},
				type: "data_url",
				html: html,
				width: 500,
				height: 800,
				action: "new"
		};
		
	} else if(views[viewIdx].type == "map") {
		
		// Add the layers from the selected panel
		for(i = 0; i < views[viewIdx].results.length; i++) {
			multiLayers.push(views[viewIdx].results[i]);
		}
		// Add the layers from other panels that may be displayed on this map
		for(i = 0; i < views.length; i++) {
			if(i !== viewIdx && views[i].state != "deleted"  && views[i].layerId === views[viewIdx].id) {
				for(j = 0; j < views[i].results.length; j++) {
					multiLayers.push(views[i].results[j]);
				}
			}
		}
		
		gReport = {
				title: views[viewIdx].title, 
				width: 1024,
				height: 768,
				smap: {
					data_gen_type: "map",
					data_gen: JSON.stringify(multiLayers),
					data_bounds: views[viewIdx].bounds,		// TODO include bounds from other layers
					sId: views[viewIdx].sId
				},
				type: "data_url",
				action: "new"
		};
		
		
	} else {
		alert("Reports can only be created from graphs, tables, maps and images");
	}
	
	$('#reportContainer').dialog("open");
	setReport(gReport);
	
}

// Handle expand / normal size actions on panels
function expandFunction($this) {

	var viewIdx = $this.attr("value"),	
		views = globals.gSelector.getViews(),
		oldState,
		i,
		e;
	
	if($('#p' + viewIdx).hasClass('expanded')) {
		// un-expand selected panel
		oldState = views[viewIdx].state;
		views[viewIdx].state = "shown";
		setPanelState(views[viewIdx], viewIdx, oldState);
		gExpandedPanelSeq = -1;	
		$('.pSmall').removeClass('pHide').css('margin-left', '0px');
	} else {
		// expand selected panel
		// First un-expand any other currently expanded panels
		for(i = 0; i < views.length; i++) {
			if(views[i].state == "expanded") {
				oldState = views[i].state;
				views[i].state = "shown";
				setPanelState(views[i], i, oldState);
				break;	// Should only ever be a max of one other panel in expanded state
			}
		}
		
		oldState = views[viewIdx].state;
		views[viewIdx].state = "expanded";
		setPanelState(views[viewIdx], viewIdx, oldState);
		gExpandedPanelSeq = views[viewIdx].seq;	
		$('.pSmall').addClass('pHide');
		$('.pLarge').removeClass('pHide');
		
	}
	

	// Send a resized event to the analysis panels in case they need to take some action
	e = jQuery.Event("resized");
	$('.analysis').trigger(e);
	
}

/*
 * Save the panels to the database
 */
function savePanels(newPanel) {
	var views = globals.gSelector.getViews(),
		idx,
		i,
		inViews = [],
		saveViews,
		viewString;

	// Set the sequence	
	idx = 0;
	gExpandedPanelSeq = -1;
	for(i = 0; i < views.length; i++) {
		if(views[i].state != "deleted") {
			views[i].seq = idx;
			if(views[i].state === "expanded") {
				gExpandedPanelSeq= idx;		// Expand this panel after refresh
			}
			idx++;
		}
	}
	
	if(typeof newPanel === "undefined") {	// Save all panels
		inViews = views;
	} else {	// Save the passed in view
		inViews[0] = newPanel;	
	}
	
	// Get a deep copy of the views as some properties will need to be deleted so that stringify can work
	saveViews = $.extend(true, [], inViews);
	// Remove view items that should not be saved
	for(i = 0; i < saveViews.length; i++) {
		delete saveViews[i].bounds;
		delete saveViews[i].selectResultsControl;
		delete saveViews[i].allLayers;
		delete saveViews[i].results;
	}

	viewsString = JSON.stringify(saveViews);
	addHourglass();
	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  dataType: "json",
		  url: "/surveyKPI/dashboard/",
		  data: { settings: viewsString },
		  success: function(data, status) {
			  removeHourglass();
			  getPanels(globals.gCurrentProject);
		  }, error: function(data, status) {
			  removeHourglass();
			  console.log("Error: Failed to save panel");
			  // Fail silently - not a big deal if settings can't be saved
		  }
	});
}

/*
 * Save the state of a single panel to the database
 */

function savePanelState(view) {
	
	var saveView = $.extend(true, {}, view);
	delete saveView.allLayers;
	delete saveView.bounds;
	delete saveView.selectResultsControl;
	
	var viewString = JSON.stringify(saveView);
	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  dataType: "json",
		  url: dashboardStateURL(),
		  data: { state: viewString },
		  success: function(data, status) {
		  }, error: function(data, status) {
			  // alert("Error state not saved"); // ignore errors
		  }
	});
}

function slide($elem) {
	var current = $elem.attr("href");
	var e = jQuery.Event("resized");
	if(current == "#slideLeft") {
		$elem.html(">");
		$elem.attr("href", "#slideRight");
		$elem.closest('.pContent').find('.pSidebar').show();
		$elem.closest('.pContent').find('.map_panel, .graph_panel, .table_panel, .timecontrols').css('width', '74%').trigger(e);
		$elem.closest('.pContent').find('.timecontrols').css('width', '70%');
	} else {
		$elem.html("<");
		$elem.attr("href", "#slideLeft");
		$elem.closest('.pContent').find('.pSidebar').hide();
		$elem.closest('.pContent').find('.map_panel, .graph_panel, .table_panel, .timecontrols').css('width', '100%').trigger(e);
		$elem.closest('.pContent').find('.timecontrols').css('width', '95%');
	}
}

/*
 * 
 */
function getFilter() {
	 var qFilter = $('#filter_question option:selected').val();
	 var filterValue = $('#filter_value option:selected').val(); 
	 var sId  = $('#settings_survey option:selected').val();
	 var language = $('#settings_language option:selected').val();
	 
	 var qInfo = getQuestionInfo(sId, language, qFilter);
		
	 if(qFilter !== -1 && typeof filterValue !== "undefined") {
		 var filterObj = {};
		 filterObj.qId = qFilter;
		 filterObj.qType = qInfo.type;
		 filterObj.qName = qInfo.name;
		 filterObj.value = filterValue;
		 return JSON.stringify(filterObj);
	 } else {
		 return undefined;
	 }
}

});
