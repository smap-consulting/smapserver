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
 * Purpose: Allow the user to select a web form in order to complete a survey
 */
var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

requirejs.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: '../../../../js/libs/jquery-2.1.1',
       	lang_location: '..'
    },
    shim: {
    	'app/common': ['jquery'],
    	'bootstrap.min': ['jquery']
    }
});

require([
         'jquery', 
         'bootstrap.min',
         'app/common', 
         'app/globals',
         'app/localise',
         'bootstrapfileinput'
         ], function($, bootstrap, common, globals, localise, bsfi) {

	var gMaps,
		gMapVersion,
		gMapId,
		gTags,          // NFC tags
		gCurrentGroup;
	
$(document).ready(function() {

    setCustomResources();			// Apply custom javascript
	setupUserProfile();
	var bs = isBusinessServer();
	localise.setlang();		// Localise HTML
	$('#map_name').attr("placeholder", localise.set["sr_m_ph"]);
	$('#map_description').attr("placeholder", localise.set["sr_m_d"]);
	$('#mapid').attr("placeholder", localise.set["sr_m_mb"]);
	
	// Get the user details
	globals.gIsAdministrator = false;
	globals.gCurrentSurvey = undefined;
	getLoggedInUser(undefined, false, false, undefined, false, true);
	
	getFilesFromServer('/surveyKPI/upload/media', undefined, refreshMediaViewManage);		// Get files available to the entire organisation
	
	// Set up the tabs
	if(bs) {
		getLocations(loadedLocationData);
	}
    $('#mediaTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');

    	$('.resourcePanel').hide();
		$('#mediaPanel').show();
		
		$('.upload_file_msg').removeClass('alert-danger').addClass('alert-success').html("");
    });
    $('#mapTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');

	    $('.resourcePanel').hide();
	   	$('#mapPanel').show();
		
		$('.upload_file_msg').removeClass('alert-danger').addClass('alert-success').html("");
    });
    $('#locationTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');

	    $('.resourcePanel').hide();
    	$('#locationPanel').show();
		
		$('.upload_file_msg').removeClass('alert-danger').addClass('alert-success').html("");
    });

    $('#crTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');

	    $('.resourcePanel').hide();
    	$('#crPanel').show();
		
		$('.upload_file_msg').removeClass('alert-danger').addClass('alert-success').html("");
    })
    
    /*
     * Set up media tab
     */
    $('.file-inputs').bootstrapFileInput();
    $('.upload_file_msg').removeClass('alert-danger').addClass('alert-success').html("");
    
    // Respond to file upload
    $('#submitFiles').click( function() {
    	if(!$('#submitFiles').hasClass('disabled')) {
    		uploadFiles('/surveyKPI/upload/media', "fileupload", refreshMediaViewManage, undefined, undefined);
    	}
    });
    
    // Respond to custom report upload
    $('#submitCustomReport').click( function() {
    	var reportName = $('#report_name').val(),
    		fileName = $('#report_file').val();
    	
    	if(!reportName || reportName.trim().length == 0) {
    		$('.upload_file_msg').removeClass('alert-success').addClass('alert-danger').html(localise.set["msg_val_nm"]);
    		return false;
    	}
    	if(!fileName || fileName.trim().length == 0) {
    		$('.upload_file_msg').removeClass('alert-success').addClass('alert-danger').html(localise.set["msg_val_file"]);
    		return false;
    	}
    	
    	uploadFiles('/surveyKPI/upload/lqasreport', "crupload", refreshCustomReportView, undefined, undefined);
    });
    
    // Respond to location upload
    $('#submitLocationFiles').click( function() {
    	if(!$('#submitLocationFiles').hasClass('disabled')) {
    		uploadFiles('/surveyKPI/tasks/locations/upload', "locationupload", loadedLocationData, undefined, undefined);
    	}
    });
    
    // Respond to location download
    $('#downloadLocationFiles').click( function() {
    	if(!$('#downloadLocationFiles').hasClass('disabled')) {
    		downloadFile('/surveyKPI/tasks/locations/download');
    	}
    });

    $('#location_group').change(function() {
	    refreshLocationView();
    });
	$('#includeNfc, #includeGeo').change(function() {
		refreshLocationGroups(gTags, false);
		refreshLocationView();
	});
    
    /*
     * Set up maps tab
     */
	$('#addMap').click(function(){
		edit_map();
		$('#addMapPopup').modal("show");
	});
	
	// Enable the save notifications function
	$('#saveMap').click(function(){saveMap();});
	
	// Respond to change of map type
	$(".vector_only").hide();
	$('#map_type').change(function(){
		showMapDialogSections($(this));
	});
	getMaps();
	
	/*
	 * Set up location tabs
	 */
	$('#addNfc').click(function(){
		$('#addMapPopup').modal("show");
	});
	
    $('.vector-data-inputs').bootstrapFileInput();
    $('.vector-style-inputs').bootstrapFileInput();
    
    /*
     * Set up reports tab
     */
	$('#addReport').click(function(){
		$('.panel_msg').hide();
		document.forms.namedItem("crupload").reset();
		$('#addReportPopup').modal("show");
	});
	getReports(refreshCustomReportView, undefined, "lqas");
	
	// On change of report name, hide any previous results
	$('#templateName').keydown(function(){
		$('.upload_file_msg').removeClass('alert-danger').addClass('alert-success').html("");
	});

	// Change function on file selected
	$('#report_file').change(function(){
		var reportName = $('#report_name').val(),
			$this = $(this),
			fileName = $this[0].files[0].name,
			newReportName;
		
		$('.upload_file_msg').removeClass('alert-danger').addClass('alert-success').html("");
		
		if(reportName && reportName.trim().length > 0) {
			// ignore - leave user specified name
		} else {
			var lastDot = fileName.lastIndexOf(".");
		    if (lastDot === -1) {
		    	newReportName = fileName;
		    } else {
		    	newReportName = fileName.substr(0, lastDot);
		    }
			$('#report_name').val(newReportName);
		}
	});

});

function showMapDialogSections(type) {
	if(type === "mapbox") {
		$(".mapbox_only").show();
		$(".vector_only").hide();
	} else {
		$(".vector_only").show();
		$(".mapbox_only").hide();
	}
}

/*
 * Open a map for editing or create a new map
 */
function edit_map(idx) {
	
	var map,
		title = localise.set["msg_add_map"];
	
	document.getElementById("map_edit_form").reset();
	
	if(typeof idx !== "undefined") {
		map = gMaps[idx];

		title = localise.set["msg_edit_map"],
		
		$('#map_name').val(map.name);
		$('#map_type').val(map.type);
		$('#map_description').val(map.description);
		
		$('#map_zoom').val(map.config.zoom);
		$('#mapid').val(map.config.mapid);
		$('#vector_data').val(map.config.vectorData);
		$('#style_data').val(map.config.styleData);
		
		gMapVersion = map.version;
		gMapId = map.id;
	} else {
		gMapVersion = 1;
		gMapId = -1;
	}
	
	showMapDialogSections($('#map_type').val())
	$('#addMapLabel').html(title);
	
}

/*
 * Save a map
 */
function saveMap() {
	
	var map,
		url = "/surveyKPI/shared/maps",
		mapString;
	
	map = {};
	map.name = $('#map_name').val();
	map.type = $('#map_type').val();
	map.description = $('#map_description').val();
	map.config = {};
	map.config.zoom = $('#map_zoom').val();
	map.config.mapid = $('#mapid').val();
	map.config.vectorData = $('#vector_data').val();
	map.config.styleData = $('#style_data').val();
	
	map.version = gMapVersion;
	map.id = gMapId;
	
	mapString = JSON.stringify(map);
	addHourglass();
	$.ajax({
		  type: "POST",
		  async: false,
		  cache: false,
		  url: url,
		  contentType: "application/json",
		  data: { map: mapString },
		  success: function(data, status) {
			  removeHourglass();
			  getMaps();
			  $('#addMapPopup').modal("hide");
		  },
		  error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert(localise.set["msg_err_save"] + xhr.responseText);
				}
			}
	});
			        	    	

}	

/*
 * Get the shared maps from the server
 */
function getMaps() {

	var url="/surveyKPI/shared/maps/";
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			gMaps = data;
			updateMapList(data);
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get list of maps: " + err);
			}
		}
	});	

}


/*
 * Update the list of maps
 */
function updateMapList(data) {

	var $selector=$('#map_list'),
		i, 
		h = [],
		idx = -1;
	
	h[++idx] = '<table class="table">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th>' + localise.set["c_name"], + '</th>';
	h[++idx] = '<th>' + localise.set["c_type"] + '</th>';
	h[++idx] = '<th>' + localise.set["c_desc"] + '</th>';
	h[++idx] = '<th>' + localise.set["c_details"] + '</th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody class="table-striped">';
	
	for(i = 0; i < data.length; i++) {
		
		h[++idx] = '<tr>';
		
		// name
		h[++idx] = '<td>';
		h[++idx] = data[i].name;
		h[++idx] = '</td>';

		// type
		h[++idx] = '<td>';
		h[++idx] = data[i].type;
		h[++idx] = '</td>';
		
		// description
		h[++idx] = '<td>';
		h[++idx] = data[i].desc;
		h[++idx] = '</td>';
		
		// details
		h[++idx] = '<td>';
		
		h[++idx] = data[i].config.zoom +" levels";
		if(data[i].type === "mapbox" && data[i].config.mapid) {
			h[++idx] = ", Mapbox Id: " + data[i].config.mapid;
		}
		if(data[i].type === "vector" && data[i].config.vectorData) {
			h[++idx] = ", Vector file: " + data[i].config.vectorData;
		}
		if(data[i].type === "vector" && data[i].config.styleData) {
			h[++idx] = ", styled by " + data[i].config.styleData;
		}
		h[++idx] = '</td>';
		
		// actions
		h[++idx] = '<td>';
		
		h[++idx] = '<button type="button" data-idx="';
		h[++idx] = i;
		h[++idx] = '" class="btn btn-default btn-sm edit_map warning">';
		h[++idx] = '<span class="glyphicon glyphicon-edit" aria-hidden="true"></span></button>';
		
		h[++idx] = '<button type="button" data-idx="';
		h[++idx] = i;
		h[++idx] = '" class="btn btn-default btn-sm rm_map danger">';
		h[++idx] = '<span class="glyphicon glyphicon-trash" aria-hidden="true"></span></button>';
		
		h[++idx] = '</td>';
		// end actions
		
		h[++idx] = '</tr>';
	}
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';
	
	$selector.empty().append(h.join(''));
	
	$(".rm_map", $selector).click(function(){
		var idx = $(this).data("idx");
		delete_map(gMaps[idx].id);
	});
	
	$(".edit_map", $selector).click(function(){
		var idx = $(this).data("idx");
		edit_map(idx);
		$('#addMapPopup').modal("show");
	});

}

/*
 * Delete a shared map
 */
function delete_map(id) {
	
	addHourglass();
	$.ajax({
		  type: "DELETE",
		  async: false,
		  url: "/surveyKPI/shared/maps/" + id,
		  success: function(data, status) {
			  removeHourglass();
			  getMaps();
		  },
		  error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert(localise.set["msg_err_del"] + xhr.responseText);
				}
			}
	});
}

/*
 * Show the NFC tags
 */
function loadedLocationData(tags) {

	gTags = tags;
	refreshLocationGroups(gTags, false);
	refreshLocationView();
}

function refreshLocationView() {
	
	var i,
		survey = globals.model.survey,
		$element,
		h = [],
		idx = -1,
		currentGroup = $('#location_group').val();

	var includeNfc = $('#includeNfc').prop('checked'),
		includeGeo = $('#includeGeo').prop('checked');

	if(gTags) {

		$element = $('#locationList');


		for(i = 0; i < gTags.length; i++){

			if(currentGroup === gTags[i].group) {

				if(includeLocation(includeNfc, includeGeo, gTags[i].uid, gTags[i].lat, gTags[i].lon)) {
					h[++idx] = '<tr>';

					h[++idx] = '<td>';
					h[++idx] = gTags[i].type;
					h[++idx] = '</td>';

					h[++idx] = '<td>';
					h[++idx] = gTags[i].group;
					h[++idx] = '</td>';

					h[++idx] = '<td>';
					h[++idx] = gTags[i].uid;
					h[++idx] = '</td>';

					h[++idx] = '<td>';
					h[++idx] = gTags[i].name;
					h[++idx] = '</td>';

					h[++idx] = '<td>';
					h[++idx] = gTags[i].lat == "0" ? '' : gTags[i].lat;
					h[++idx] = '</td>';

					h[++idx] = '<td>';
					h[++idx] = gTags[i].lon == "0" ? '' : gTags[i].lon;
					h[++idx] = '</td>';

					h[++idx] = '</tr>';
				}
			}
			
		}
		
	
		$element.html(h.join(""));
	}
}

});

