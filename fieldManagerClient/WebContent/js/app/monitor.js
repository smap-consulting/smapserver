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

define(['jquery','jquery_ui', 'app/map-ol-mgmt', 'localise', 'common', 'globals', 'moment'], 
		function($, ui, ol_mgmt, lang, common, globals, moment) {

var gStartEvents = [],		// Only in this java script file
	gPageCount = 1;			// Only in this java script file

$(document).ready(function() {
	
	window.moment = moment;
	
	localise.setlang();		// Localise HTML
	
	getLoggedInUser(projectChanged, true, true, undefined);
	 
	refreshRegions();
	
	// Initialise the map and then hide it
	initializeMap();
	$('#map, #layers').hide();
	
	// Menu functions
	$('.rmm').delegate('#refreshMenu', 'click', function(e) {
		e.preventDefault();
		refreshData(globals.gCurrentProject, $('#survey option:selected').val());
	});
	
	// change functions
	// Display Type
	$('input[name=showsource]').change(function () {
		setcontrols();
		refreshData(globals.gCurrentProject, $('#survey option:selected').val());
	});
	
	$('input[name=target]').change(function () {
		$('#map,#events_table,#layers').toggle();
	});
	
	$('input[name=showtype]').change(function () {
		setcontrols();
		refreshData(globals.gCurrentProject, $('#survey option:selected').val());
	});
	
	$('input[name=groupsurvey]').change(function () {
		refreshData(globals.gCurrentProject, $('#survey option:selected').val());
	});
	
	// Set change function on projects
	$('#project_name').change(function() {
		projectChanged();
 	 });
	
	// Survey Change
	$('#survey').change(function () {
		setcontrols();
		refreshData(globals.gCurrentProject, $('#survey option:selected').val());
	});
	
	// Status values change
	$('#showstatus :checkbox:checked').change(function () {
		refreshData(globals.gCurrentProject, $('#survey option:selected').val());
	});
	
	// Add zoom to data button
	$('#zoomData').button().click(function () {
		zoomTo("events");
	});
	
	// Add zoom to regions button
	$('#zoomRegions').button().click(function () {
		if(globals.gCurrentLayer) {
			zoomTo(globals.gCurrentLayer);
		} else {
			alert("No region set");
		}
	});
	
	// Add button to add a Layer along with its dialog
	$('#addLayer').button().click(function () {
		var $elem = $("#available_regions");
		$elem.empty();
		for(var i = 0; i < globals.gRegions.length; i++) {
			if(globals.gRegions[i].name != globals.gCurrentLayer) {
				$elem.append('<option value="' + globals.gRegions[i].name + '">' + globals.gRegions[i].name + '</option>');
			}
		}
		$('#regions').dialog("open");
	});
	$('#regions').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			show:"drop",
			buttons: [
		        {
		        	text: "Cancel",
		        	click: function() {
		        		$(this).dialog("close");
		        	}
		        },
		        {
		        	text: "Done",
		        	click: function() {
		        		setMapRegions($('#available_regions option:selected').val());
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	
	// Remove layer button
	$('#removeLayer').button().click(function () {
		if(globals.gCurrentLayer) {
			clearLayer(globals.gCurrentLayer);
			globals.gCurrentLayer = undefined;
		} else {
			alert("No region to remove");
		}
	});
	
	// Create Layer button and dialog
	$('#createLayer').button().click(function () {
		// clear the form
		document.getElementById("region_create_form").reset();
		
		// Register for clicks on the map so we can get the report location
		map.events.register("click", map , function(e) {
			var lonlat = map.getLonLatFromViewPortPx(e.xy);
			addMarker(lonlat, true);
		});
		
		// open the dialog
		$('#region_create').dialog("open");
	});
	$('#region_submit').button().click(function () {
		var error = false;
		globals.gRegion["name"] = $('#region_name').val();
		globals.gRegion["width"] = $('#region_width').val();
		if(!globals.gRegion["name"]) {
			alert("Name must be specified");
			error = true;
			$('#region_name').focus();
		}
		if(globals.gRegion["name"].indexOf(' ') > 0) {
			alert("No spaces are allowed in the name");
			error = true;
			$('#region_name').focus();
		}
		if(globals.gRegion["name"] !== globals.gRegion["name"].toLowerCase()) {
			alert("Name must be lower case");
			error = true;
			$('#region_name').focus();
		}
		
		if(!error && (!globals.gRegion["width"] || globals.gRegion["width"] < 10)) {
			alert("Width must be specified and greater than 10");
			error = true;
			$('#region_width').focus();
		}
		if(!error && !globals.gRegion["centre_x"]) {
			alert("You must select the centre of one cell by clicking on the map");
			error = true;
		}
		if(!error && !globals.gRegion["lower_left_x"]) {
			alert("You must select the bounds of the region by pressing shift, clicking with the left button and dragging");
			error = true;
		}

		if(!error) {
    		var boundsWidth = globals.gRegion["upper_right_x"] - globals.gRegion["lower_left_x"];
    		var boundsHeight = globals.gRegion["upper_right_y"] - globals.gRegion["lower_left_y"];
    		
    		if(boundsWidth / globals.gRegion["width"] > 100 || boundsHeight / globals.gRegion["width"] > 100) {
    			alert("The bounding region is too large or the cell width is too small");
    			error = true;
    		}
		}
		if(!error) {
    		var regionString = JSON.stringify(globals.gRegion);
			addHourglass();
    		$.ajax({
    			  type: "POST",
    			  dataType: 'text',
    			  cache: false,
				  contentType: "application/json",
    			  url: "/surveyKPI/regions",
    			  data: { settings: regionString },
    			  success: function(data, status) {
    				  refreshRegions();
    				  setMapRegions(globals.gRegion["name"]);
    				  removeHourglass();
    			  }, error: function(data, status) {
    				  removeHourglass();
    				  alert("Error: Failed to create region"); 
    			  }
    		});
		}
		return false;
	});
	$('#region_create').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:false,
			show:"drop",
			position: { my: "left top", at: "left top", of:'#aside'},
			buttons: [
		        {
		        	text: "Close",
		        	click: function() {
		        		// Unregister for clicks on the map 
		        		map.events.remove("click"); 
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	
	// Delete Layers button and dialog
	$('#deleteLayer').button().click(function () {
		showRegions();		
		// open the dialog
		$('#regions_delete').dialog("open");
	});
	$('#regions_delete').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:false,
			show:"drop",
			buttons: [
		        {
		        	text: "Close",
		        	click: function() {
		        		 
		        		$(this).dialog("close");
		        	}
		        },
		        {
		        	text: "Delete",
		        	click: function() {
		        		$dialog=$(this);
		        		if(confirm(localise.set["msg_del_r"])) {
		        			var count = 0;
		        			var allCalled = false;
		        			$('#regions_delete input:checked').each(function(index) {
		        				++count;
		        				
		        				$.ajax({
		        					url: "/surveyKPI/regions/" + $(this).val(),
		        					type: "DELETE",
		        					cache: false,
		        					success: function(data) {
		        						--count;
		        						if(allCalled && count == 0) {
		        							refreshRegions(true);
		        						}

		        					},
		        					error: function(xhr, textStatus, err) {
		        						--count;
		        						if(xhr.readyState == 0 || xhr.status == 0) {
		        				              return;  // Not an error
		        						} else {
		        							alert("Failed to delete region");
		        						}
		        						if(allCalled && count == 0) {
		        							refreshRegions(true);
		        						}
		        					}
		        				});	
		        				
		        			});
		        			allCalled = true;
		        		} 
		        	}
		        }
			]
		}
	);
	
	enableUserProfile();
	setcontrols();

	$('#tableradio').prop('checked',true);
	
	// Handle more / less buttons
	$('.get_less').button().click(function() {
		var currentStart = gStartEvents.pop();
		var newStart = gStartEvents.pop();
		gPageCount--;
		refreshData(globals.gCurrentProject, $('#survey option:selected').val(), newStart);
	});
	$('.get_more').button().click(function() {
		gPageCount++;
		refreshData(globals.gCurrentProject, $('#survey option:selected').val(), parseInt($(this).val()));
	});

});

function setcontrols() {
	
	var survey = $('#survey option:selected').val(),
		showSource = $("input[name=showsource]:checked").val(),
		showType = $("input[name=showtype]:checked").val();
	
	if(showSource === "forms") {
		$('#showtype, #showstatus').hide();
	} else {
		$('#showtype, #showstatus').show();
	}
	
	if(typeof survey === "undefined" || survey === "_all" || showType === "instances") {
		$('#groupsurvey').hide();
	} else {
		$('#groupsurvey').show();
	}
	if(showType === "totals") {
		$("input[value='map']", "#showtarget").prop('checked', false);
		$("input[value='table']", "#showtarget").prop('checked',true);
		$(".showmap").hide();
		$('#map,#layers,.get_less_more').hide();
		$('#events_table').show();
		//refreshData(globals.gCurrentProject, survey);
	} else {
		$(".showmap,.get_less_more").show();
	}
}

function projectChanged() {
	globals.gCurrentProject = $('#project_name option:selected').val();
	globals.gCurrentSurvey = -1;
	loadSurveys(globals.gCurrentProject, undefined, false, true);			// Get surveys
	saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
	refreshData(globals.gCurrentProject, "_all");
}

function showRegions() {
	$elem = $('#regions_delete > div');
	var h = [];
	var idx = -1;
	for(var i = 0; i < globals.gRegions.length; i++) {
		h[++idx] = '<input type="checkbox" name="delregion" value="';
		h[++idx] = globals.gRegions[i].name;
		h[++idx] = '" id="del_region';
		h[++idx] = i;
		h[++idx] = '"/><label for "del_region';
		h[++idx] = i;
		h[++idx] = '">';
		h[++idx] = globals.gRegions[i].name;
		h[++idx] = '</label></br>';	
	}
	$elem.empty().append(h.join(''));
}

function refreshData(projectId, surveyId, start_rec) {
	
	var hide_success=true,
		hide_errors=true,
		hide_duplicates=true,
		hide_merged=true,
		hide_not_loaded=true;
	
	var status_values = $('#showstatus :checkbox:checked').each(function() {
	      var $this = $(this).val();
	      if($this === "success") {
	    	  hide_success=false;
	      }
	      if($this === "errors") {
	    	  hide_errors=false;
	      }
	      if($this === "not_loaded") {
	    	  hide_not_loaded=false;
	      }
	      if($this === "duplicates") {
	    	  hide_duplicates=false;
	      }
	      if($this === "merged") {
	    	  hide_merged=false;
	      }
	    });
	
	var groupby =  $("input[name=groupsurvey]:checked").val();
	var showType = $("input[name=showtype]:checked").val();
	var showSource = $("input[name=showsource]:checked").val();
	var isForward = false;
	
	if(showSource === "uploaded") {
		isForward=false;
	} else {
		isForward=true;
	}
	function refreshDataExec(showTypeE, showSourceE) {
		
		if(typeof start_rec === "undefined") {
			start_rec = 0;
			gPageCount = 1;
		}
		
		var url;
		if(showSourceE === "notifications") {
			url = "/surveyKPI/eventList/notifications/" + projectId + "/" + surveyId;
		} else {
			url = "/surveyKPI/eventList/" + projectId + "/" + surveyId;
		}

		
		if(showSourceE === "forms") {
			url += "/forms";
		} else {
			if(showTypeE === "totals" ) {
				url += "/totals";
			}
			url += 
				"?hide_success=" + hide_success +
				"&hide_errors=" + hide_errors +
				"&hide_not_loaded=" + hide_not_loaded +
				"&hide_duplicates=" + hide_duplicates +
				"&hide_merged=" + hide_merged +
				"&is_forward=" + isForward;
			
			if(showTypeE === "totals" && surveyId !== "_all") {
				url += "&groupby=" + groupby;
			}
			
			url += "&start_key=" + start_rec;
			url += "&rec_limit=200";
		}
		
		addHourglass();
		$.ajax({
			url: url,
			cache: false,
			dataType: 'json',
			success: function(data) {
				removeHourglass();
				
				// Save start and end records for less & more buttons
				if(typeof data.totals !== "undefined") {
					gStartEvents.push(start_rec);
					$('.get_more').val(data.totals.max_rec);
					if(start_rec === 0) {
						$('.get_less').button({ disabled: true });
					} else {
						$('.get_less').button({ disabled: false });
					}
					if(data.totals.more_recs === 0) {
						$('.get_more').button({ disabled: true });
					} else {
						$('.get_more').button({ disabled: false });
					}
					var totals_msg = "Page " + gPageCount + " from " + data.totals.from_date + 
							" to " +  data.totals.to_date;
					$('.get_less_more_text').html(totals_msg);
				}
				if(showSourceE === "forms") {
					refreshFormsTable(data);
				} else if(showSourceE === "notifications") {
					refreshNotificationsTable(data, showType);
				} else {
					refreshTable(data, showType);
					if(showTypeE !== "totals") {
						refreshMap(data);
					}
				}
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Failed to get data on submission of results");
				}
			}
		});
	}
	
	if(typeof projectId !== "undefined" && projectId != -1 && typeof surveyId != "undefined") {
		refreshDataExec(showType, showSource);
	}
}

function refreshTable(data, showType) {

	var features = data.features,
		$elem = $("#events tbody"),
		$head = $("#events thead"),
		$msg = $('#events_table_msg'),
		h = [],
		i = -1,
		j,
		locn,
		status,
		reason,
		sId = $('#survey option:selected').val(),
		isForwarded = $("input[name=showsource]:checked").val() === "forwarded" ? true : false,
		groupby =  $("input[name=groupsurvey]:checked").val(),
		showSource = $("input[name=showsource]:checked").val();
	
	$head.empty();
	$elem.empty();
	$msg.empty();
	
	if(typeof features === "undefined" || features.length === 0) {
		var msg;
		if(showSource === "forms" && sId == "_all") {
			msg = "<h1>" + localise.set["msg_saf"] + "</h1>";
		} else if(showSource === "forms" ) {
			msg = "<h1>" + localise.set["msg_nf"] + "</h1>";
		} else if(showSource === "uploaded") {
			msg = "<h1>" + localise.set["msg_ns"] + "</h1>";
		} else if(isForwarded) {
			msg = "<h1>" + localise.set["msg_nrf"] + "</h1>";
		} else {
			msg = "<h1>" + localise.set["msg_us"] + "</h1>";
		}
		$msg.html(msg);
		return;
	}
	// Add the head
	h[++i] = '<tr>';
	if(showType === "totals") {
		if(sId === "_all") {
			h[++i] = '<th>' + localise.set["c_survey"] + '</th>';
			if(isForwarded) {
				h[++i] = '<th>Dest</th>';
			}
		} else {
			h[++i] = '<th>' + groupby + '</th>';
			if(isForwarded) {
				h[++i] = '<th>Dest</th>';
			}
		}
		if(typeof features[0].properties.success !== "undefined") {
			h[++i] = '<th>' + localise.set["c_success"] + '</th>';
		} 
		if(typeof features[0].properties.errors !== "undefined") {
			h[++i] = '<th>' + localise.set["c_errors"] + '</th>';
		}
		if(typeof features[0].properties.duplicates  !== "undefined") {
			h[++i] = '<th>' + localise.set["mon_dup"] + '</th>';
		}
		if(typeof features[0].properties.merged  !== "undefined") {
			h[++i] = '<th>' + localise.set["mon_att"] + '</th>';
		}
		if(typeof features[0].properties.not_loaded  !== "undefined") {
			h[++i] = '<th>' + localise.set["mon_nl"] + '</th>';
		}

	} else {
		h[++i] = '<th>Id</th>';
		h[++i] = '<th>Upload Id</th>';
		h[++i] = '<th>' + localise.set["mon_ud"] + ' ' + localise.set["c_lt"] + '</th>';
		h[++i] = '<th>' + localise.set["c_user"] + ' ' + localise.set["c_ident"] + '</th>';
		h[++i] = '<th>' + localise.set["mon_pi"] + '</th>';
		h[++i] = '<th>' + localise.set["mon_file"] + '</th>';
		h[++i] = '<th>' + localise.set["c_survey"] + '</th>';
		h[++i] = '<th>Ident</th>';
		if(isForwarded) {
			h[++i] = '<th>Dest</th>';
		}
		h[++i] = '<th>' + localise.set["c_location"] + '</th>';
		h[++i] = '<th>' + localise.set["c_status"] + '</th>';
		h[++i] = '<th>' + localise.set["mon_fr"] + '</th>';
	}
	h[++i] = '</tr>';
	$head.append(h.join(''));
	
	h = [];
	i = -1;
	// Add the body
	for(j = 0; j < features.length; j++) {
		h[++i] = '<tr>';
		if(showType === "totals") {
			h[++i] = '<td>' + features[j].properties.key + '</td>';
			if(isForwarded) {
				h[++i] = '<td>' + features[j].properties.dest + '</td>';;
			}
			if(typeof features[j].properties.success !== "undefined") {
				h[++i] = '<td>' + features[j].properties.success + '</td>';
			}
			if(typeof features[j].properties.errors !== "undefined") {
				h[++i] = '<td>' + features[j].properties.errors + '</td>';
			}
			if(typeof features[j].properties.duplicates !== "undefined") {
				h[++i] = '<td>' + features[j].properties.duplicates + '</td>';
			}
			if(typeof features[j].properties.merged !== "undefined") {
				h[++i] = '<td>' + features[j].properties.merged + '</td>';
			}
			if(typeof features[j].properties.not_loaded !== "undefined") {
				h[++i] = '<td>' + features[j].properties.not_loaded + '</td>';
			}
		} else {
			h[++i] = '<td>' + ((typeof features[j].properties.se_id === "undefined") ? "" : features[j].properties.se_id) + '</td>';
			h[++i] = '<td>' + features[j].properties.ue_id + '</td>';
			h[++i] = '<td>' + localTime(features[j].properties.upload_time) + '</td>';
			h[++i] = '<td>' + features[j].properties.user_name + '</td>';
			h[++i] = '<td>' + features[j].properties.imei + '</td>';
			h[++i] = '<td>' + features[j].properties.file_name + '</td>';
			h[++i] = '<td>' + features[j].properties.survey_name + '</td>';
			h[++i] = '<td>' + features[j].properties.ident + '</td>'
			if(isForwarded) {
				h[++i] = '<td>' + features[j].properties.dest + '</td>';;
			}

			if(features[j].geometry) {
				locn = 'lon:' + features[j].geometry.coordinates[0] + ' lat:' + features[j].geometry.coordinates[1];
			} else {
				locn = "none";
			}
			h[++i] = '<td>' + locn + '</td>';
			status = features[j].properties.status;
			h[++i] = '<td class="' + status + '">' + status + '</td>';
			reason = features[j].properties.reason;
			if(typeof reason === "undefined") {
				reason = "";
			}
			h[++i] = '<td>' + reason + '</td>';
		}
		h[++i] = '</tr>';
		
	}
	
	$elem.append(h.join(''));
	
			
}

function refreshNotificationsTable(data, showType) {

	var features = data.features,
		$elem = $("#events tbody"),
		$head = $("#events thead"),
		$msg = $('#events_table_msg'),
		h = [],
		i = -1,
		j,
		locn,
		status,
		reason,
		showSource = $("input[name=showsource]:checked").val();
	
	$head.empty();
	$elem.empty();
	$msg.empty();
	
	if(typeof features === "undefined" || features.length === 0) {
		var msg = "<h1>No notifications</h1>";	
		$msg.html(msg);
		return;
	}
	
	// Add the head
	h[++i] = '<tr>';
	if(showType === "totals") {
		h[++i] = '<th>' + localise.set["c_success"] + '</th>';
		h[++i] = '<th>' + localise.set["c_errors"] + '</th>';

	} else {
		h[++i] = '<th>Id</th>';
		h[++i] = '<th>' + localise.set["c_details"] + '</th>';
		h[++i] = '<th>' + localise.set["c_status"] + '</th>';
		h[++i] = '<th>' + localise.set["mon_fr"] + '</th>';
		h[++i] = '<th>' + localise.set["c_lt"] + '</th>';
	}
	h[++i] = '</tr>';
	$head.append(h.join(''));
	
	h = [];
	i = -1;
	// Add the body
	for(j = 0; j < features.length; j++) {
		h[++i] = '<tr>';
		if(showType === "totals") {
			h[++i] = '<td>' + features[j].properties.success + '</td>';
			h[++i] = '<td>' + features[j].properties.errors + '</td>';
		} else {
			
			h[++i] = '<td>' + features[j].properties.id + '</td>';
			h[++i] = '<td>' + features[j].properties.notify_details + '</td>';
			status = features[j].properties.status;
			h[++i] = '<td class="' + status + '">' + status + '</td>';
			if(features[j].properties.status_details) {
				h[++i] = '<td>' + features[j].properties.status_details + '</td>';
			} else {
				h[++i] = '<td></td>';
			}
			h[++i] = '<td>' + localTime(features[j].properties.event_time) + '</td>';
			

		}
		h[++i] = '</tr>';
		
	}
	
	$elem.append(h.join(''));

			
}

function refreshFormsTable(data) {

	var forms = data.forms,
		$elem = $("#events tbody"),
		$head = $("#events thead"),
		$msg = $('#events_table_msg'),
		h = [],
		i = -1,
		j,
		locn,
		status,
		reason,
		survey_ident,
		sId = $('#survey option:selected').val();
	
	$head.empty();
	$elem.empty();
	$msg.empty();
	
	// Add the head
	h[++i] = '<tr>';
		h[++i] = '<th>' + localise.set["c_user"] + ' ' + localise.set["c_name"] + '</th>';
		h[++i] = '<th>' + localise.set["c_user"] + ' ' + localise.set["c_ident"] + '</th>';
		h[++i] = '<th>' + localise.set["c_device"] + ' ' + localise.set["c_ident"] + '</th>';
		h[++i] = '<th>' + localise.set["c_survey"] + ' ' + localise.set["c_name"] + '</th>';
		h[++i] = '<th>' + localise.set["c_survey"] + ' ' + localise.set["c_version"] + '</th>';
	h[++i] = '</tr>';
	$head.append(h.join(''));
	
	h = [];
	i = -1;
	// Add the body
	for(j = 0; j < forms.length; j++) {
		h[++i] = '<tr>';
			h[++i] = '<td>' + forms[j].u_name + '</td>';
			h[++i] = '<td>' + forms[j].u_ident + '</td>';
			h[++i] = '<td>' + forms[j].device_id + '</td>';
			
			survey_ident = forms[j].survey_ident;
			if(survey_ident) {
				h[++i] = '<td class="success">' + data.all_surveys[survey_ident].name + '</td>';	
			} else {
				h[++i] = '<td class="error">Not Downloaded</td>';
			}
			if(survey_ident) {
				if(forms[j].survey_version === data.all_surveys[survey_ident].version) {
					h[++i] = '<td class="success">' + forms[j].survey_version + '</td>';
				} else {
					h[++i] = '<td class="error">' + forms[j].survey_version + '</td>';
				}
			} else {
				h[++i] = '<td class="error"></td>';
			}
		h[++i] = '</tr>';
		
	}
	
	$elem.append(h.join(''));
	
	// Apply styling
	//$("#events").tablesorter({ widgets: ['zebra'] });
			
}

function refreshRegions(show) {
	// Get the regions
	addHourglass();
	$.ajax({
		url: "/surveyKPI/regions",
		dataType: 'json',
		cache: false,
		success: function(data) {
			globals.gRegions = data;
			if(show) {
				showRegions();
			}
			removeHourglass();
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Failed to get region list");
			}
		}
	});	
}
});