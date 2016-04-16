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
 * Purpose: Manage the report items popup
 */
var hristart1 = '<li class="db_shown" value="';
var hristart2 = '"><img class="ri_thumb" src="';
var hristart3 = '" alt="';
var hristart4 = '" height="32"><div class="ri_caption">';
var hristart5 = '</div>';
var hridel = '<img class="ri_del" src="img/delete.png"/>';
var hriend = '</li>';

var gLon = null,
	gLat = null,
	reportMap = null,
	gReportIdent = undefined,
	gCalledFromReports = false,		// Deprecated - speed
	gReport = {};

$(document).ready(function() {
	      
	/*
	 * Report Dialog
	 */	
	$('#reportContainer').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			show:"drop",
			title: "Create Report",
			width: 500,
			zIndex: 2000,
			buttons: [
		        {
		        	text: "Cancel",
		        	click: function() {
		        		if(gCalledFromReports) {
		        			window.location.href="reports.html";
		        		} 
		        		$(this).dialog("close");
		        	}
		        },
		        {
		        	text: "Save",
		        	click: function() {
		        		// Validate
		        		if(!gReport.smap.data_bounds) {
		        			alert("You need to select the area covered by the report. Hold down shift key and " +
		        					"drag the mouse over the map.");
		        			return false;
		        		} else {
		        			saveReport();
		        		}	
		        		$(this).dialog("close");
		        	}		
		        }
			]
		}
	);

	// Report save button
	/*
	$('#report_save').button().click(function() {
		
		// Validate
		if(!gReport.smap.data_bounds) {
			alert("You need to select the area covered by the report. Hold down shift key and " +
					"drag the mouse over the map.");
		} else {
			saveReport();
		}
	});
	*/
		
});

// Set the current data for the report
function setReport(data) {
	
	var surveyMeta;

	if(typeof data.smap.data_bounds != "undefined") {
		initialiseReportMap(data.smap.data_bounds);
	} else {
		// Get the bounds from the survey meta data
		surveyMeta = globals.gSelector.getSurvey(data.smap.sId);
		if(!surveyMeta) {
			getSurveyMetaReport(data.smap.sId, data);
		} else {
			if(typeof surveyMeta.bbox !== "undefined") {
				data.smap.data_bounds = new OpenLayers.Bounds(surveyMeta.bbox[0], surveyMeta.bbox[1], surveyMeta.bbox[2], surveyMeta.bbox[3]).
						transform(new OpenLayers.Projection("EPSG:4326"), new OpenLayers.Projection("EPSG:900913"));
			}
			initialiseReportMap(data.smap.data_bounds);
		}
	}
	
	$('#report_title').val(data.title);
	$('#report_description').val(data.smap.description);
	$('#report_country').val(data.smap.country);
	$('#report_region').val(data.smap.region);
	$('#report_district').val(data.smap.district);
	$('#report_community').val(data.smap.community);

	//$('#report_save').button( "enable" );
	
	gReport = data;
}

/*
 * Get a surveys meta data
 */
function getSurveyMetaReport(sId, data) {

	if(sId != -1) {
		var url = surveyMeta(sId);
	
		addHourglass();
	 	$.ajax({
			url: url,
			dataType: 'json',
			success: function(surveyMeta) {
				removeHourglass();
				globals.gSelector.addSurvey(sId, surveyMeta);
				if(typeof surveyMeta.bbox !== "undefined") {
					data.smap.data_bounds = new OpenLayers.Bounds(surveyMeta.bbox[0], surveyMeta.bbox[1], surveyMeta.bbox[2], surveyMeta.bbox[3]).
							transform(new OpenLayers.Projection("EPSG:4326"), new OpenLayers.Projection("EPSG:900913"));
				}
				initialiseReportMap(data.smap.data_bounds);
			},
			error: function(data) {
				removeHourglass();
			}
		});
	}
}

// Save the current report
function saveReport() {
	
	gReport.title = $('#report_title').val();
	gReport.smap.description = $('#report_description').val();
	gReport.smap.country = $('#report_country').val();
	gReport.smap.region = $('#report_region').val();
	gReport.smap.district = $('#report_district').val();
	gReport.smap.community = $('#report_community').val();

	gReport.smap.bbox = gReport.smap.data_bounds.
			transform(reportMap.getProjectionObject(),new OpenLayers.Projection("EPSG:4326")).
			toArray();
	if(isNaN(gReport.smap.bbox[0])) {
		alert("Error: Please re-select area covered by the report");
		return;
	}
	
	var reportString = JSON.stringify(gReport),
		url;
	
	if(gCalledFromReports) {
		url = reportSaveURL(globals.gEditingReportProject);
	} else {
		url = reportSaveURL(globals.gCurrentProject);
	}
	
	addHourglass();
	//$('#report_save').button( "disable" );
	$.ajax({
		  type: "POST",
		  dataType: 'text',
		  contentType: "application/json",
		  //dataType: 'text/plain',
		  url: url,
		  data: { report: reportString },
		  success: function(data, status) {
			  
			  // clearReport();
			  removeHourglass();
			  if(gCalledFromReports) {
					window.location.href="reports.html";
			  } 
		  },
		  error: function(xhr, textStatus, err) {
			  removeHourglass();
			  if(xhr.readyState == 0 || xhr.status == 0) {
				  return;  // Not an error
			  } else { 
				  alert("Error report not saved" + err); 
			  }
		  }
	});
}


function initialiseReportMap(bounds) {
		
	// Set options and initialize map
	var mapOptions = {
			projection: new OpenLayers.Projection("EPSG:900913"),
			displayProjection: new OpenLayers.Projection("EPSG:4326"),
			units: "m",
			numZoomLevels: 18,
			maxResolution: 156543.0339,
			maxExtent: new OpenLayers.Bounds(-20037508, -20037508, 20037508, 20037508.34),
			fallThrough: false
		},
		markers,
		lonlat,
		defaultMapExtent = [-20037508, -20037508, 20037508, 20037508.34],
		control,
		click,
		size,
		offset,
		icon,
		currentMarker,
		maxZoom = 14;
   	
	if(reportMap != null) {
		reportMap.destroy();
		reportMap = null;
		gCurrentBoundsLayer = null;
	}
	
	reportMap = new OpenLayers.Map("report_map", mapOptions); 
	reportMap.addLayer(new OpenLayers.Layer.XYZ("OSM", "http://otile1-s.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.png"));
	if(typeof google != 'undefined') {
		reportMap.addLayer(new OpenLayers.Layer.Google("Google Satellite",{type: google.maps.MapTypeId.SATELLITE, 'sphericalMercator': true, numZoomLevels: 18}));
		reportMap.addLayer(new OpenLayers.Layer.Google("Google Maps",{type: google.maps.MapTypeId.ROADMAP, 'sphericalMercator': true, numZoomLevels: 18}));
		reportMap.addLayer(new OpenLayers.Layer.Google("Google Hybrid",{type: google.maps.MapTypeId.HYBRID, 'sphericalMercator': true, numZoomLevels: 18}));
	}
	
	// Add markers
	markers = new OpenLayers.Layer.Markers( "Markers" );
	reportMap.addLayer(markers);
	size = new OpenLayers.Size(21,25);
	offset = new OpenLayers.Pixel(-(size.w/2), -size.h);
	icon = new OpenLayers.Icon('/images/blue-marker.png', size, offset);
		
	// Map Controls
	reportMap.addControl(new OpenLayers.Control.Zoom());
	reportMap.addControl(new OpenLayers.Control.Scale());
	reportMap.addControl(new OpenLayers.Control.LayerSwitcher({roundedCorner: false}));			
	reportMap.addControl(new OpenLayers.Control.ScaleLine());
	
	// Register for single clicks
    OpenLayers.Control.Click = OpenLayers.Class(OpenLayers.Control, {                
        defaultHandlerOptions: {
            'single': true,
            'double': false,
            'pixelTolerance': 0,
            'stopSingle': false,
            'stopDouble': false
        },

        initialize: function(options) {
            this.handlerOptions = OpenLayers.Util.extend(
                {}, this.defaultHandlerOptions
            );
            OpenLayers.Control.prototype.initialize.apply(
                this, arguments
            ); 
            this.handler = new OpenLayers.Handler.Click(
                this, {
                    'click': this.trigger
                }, this.handlerOptions
            );
        }, 

        trigger: function(e) {
        	var newBounds = new OpenLayers.Bounds(),
            	lonlat = reportMap.getLonLatFromPixel(e.xy);
            newBounds.extend(lonlat);
            if(typeof currentMarker != "undefined") {
            	markers.removeMarker(currentMarker);
            }
            currentMarker = new OpenLayers.Marker(lonlat,icon);
            markers.addMarker(currentMarker);

            gReport.smap.data_bounds = showBounds(reportMap, newBounds, maxZoom);
        }

    });
    click = new OpenLayers.Control.Click();
    
	// Register for bounding box
    control = new OpenLayers.Control();
    OpenLayers.Util.extend(control, {
        draw: function () {
            // this Handler.Box will intercept the shift-mousedown
            // before Control.MouseDefault gets to see it
            this.box = new OpenLayers.Handler.Box( control,
                {"done": this.notice},
                {keyMask: OpenLayers.Handler.MOD_SHIFT});
            this.box.activate();
        },

        notice: function (box) {
       		var newBounds = new OpenLayers.Bounds();
            var	ll = reportMap.getLonLatFromPixel(new OpenLayers.Pixel(box.left, box.bottom)); 
            var ur = reportMap.getLonLatFromPixel(new OpenLayers.Pixel(box.right, box.top)); 
            var centre = reportMap.getLonLatFromPixel(new OpenLayers.Pixel(box.left + (box.right - box.left)/2, 
            		box.bottom + (box.top - box.bottom)/2)); 
            
            newBounds.extend(ll);
            newBounds.extend(ur);
            if(typeof currentMarker != "undefined") {
            	markers.removeMarker(currentMarker);
            }
            currentMarker = new OpenLayers.Marker(centre,icon);
            markers.addMarker(currentMarker);

            gReport.smap.data_bounds = showBounds(reportMap, newBounds, maxZoom);
        }
    });
    reportMap.addControl(control);
    reportMap.addControl(click);
    click.activate();

	if(!bounds) {
		reportMap.zoomToExtent(new OpenLayers.Bounds(defaultMapExtent[0],defaultMapExtent[1],defaultMapExtent[2],defaultMapExtent[3]));
	} else {
		gReport.smap.data_bounds = showBounds(reportMap, bounds);
	}
}



