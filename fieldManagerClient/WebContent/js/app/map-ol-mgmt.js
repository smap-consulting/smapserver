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
 * This javascript file handles map initialization and events.
 */


var selectResultsControl = null, // OpenLayers select control for vector feature layer
	bounds,
	allLayers,   // Vector layers, Layer 0 = events, layer 1 = regions
	index,
	defaultMapExtent = [-20037508, -20037508, 20037508, 20037508.34],
	map,
	gNewTasksLayer = false,		// Only in this java script file
	gTaskClick;					// Only in this java script file

/**
 * Map Initialization
 */
function initializeMap(){
	// Set options and initialize map

	if(typeof OpenLayers === "undefined") {
		console.log("Openlayers not available");
	} else {
		console.log("initialize map");
		var mapOptions = {
				projection: new OpenLayers.Projection("EPSG:900913"),
				displayProjection: new OpenLayers.Projection("EPSG:4326"),
				units: "m",
				numZoomLevels: 18,
				maxResolution: 156543.0339,
				maxExtent: new OpenLayers.Bounds(-20037508, -20037508, 20037508, 20037508.34),
				fallThrough: false
				},
			control,
			markers;
	   	
		map = new OpenLayers.Map("map", mapOptions);  	
		
		// Add layers
		map.addLayer(new OpenLayers.Layer.XYZ("OSM", "http://otile1.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.png"));
		if(typeof google != 'undefined') {
			map.addLayer(new OpenLayers.Layer.Google("Google Satellite",{type: google.maps.MapTypeId.SATELLITE, 'sphericalMercator': true, numZoomLevels: 18}));
			map.addLayer(new OpenLayers.Layer.Google("Google Maps",{type: google.maps.MapTypeId.ROADMAP, 'sphericalMercator': true, numZoomLevels: 18}));
			map.addLayer(new OpenLayers.Layer.Google("Google Hybrid",{type: google.maps.MapTypeId.HYBRID, 'sphericalMercator': true, numZoomLevels: 18}));
		}
		
		// Map Controls
		map.addControl(new OpenLayers.Control.Zoom());
		map.addControl(new OpenLayers.Control.LayerSwitcher({'div': OpenLayers.Util.getElement('current_layers')}));			
	
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
	    		var targetBounds = new OpenLayers.Bounds();
	            var	ll = map.getLonLatFromPixel(new OpenLayers.Pixel(box.left, box.bottom)); 
	            var ur = map.getLonLatFromPixel(new OpenLayers.Pixel(box.right, box.top)); 
	            targetBounds.extend(ll);
	            targetBounds.extend(ur);
	            var isOpen = $('#region_create').dialog( "isOpen" );
	            if(isOpen) {
	            	globals.gRegion["lower_left_x"] = ll.lon.toFixed(0);
	            	globals.gRegion["lower_left_y"] = ll.lat.toFixed(0);
	            	globals.gRegion["upper_right_x"] = ur.lon.toFixed(0);
	            	globals.gRegion["upper_right_y"] = ur.lat.toFixed(0);
	            	
		            $('#region_bounds').val((((ur.lon - ll.lon)/1000).toFixed(2)) + " X " + (((ur.lat - ll.lat)/1000).toFixed(2)) + " KM" );
	            }
	            map.zoomToExtent(targetBounds);
	        }
	    });
	    map.addControl(control);
	    
	    // Add click handler for the adding of tasks
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
	    		var lonlat = map.getLonLatFromViewPortPx(e.xy),
	    			point = new OpenLayers.Geometry.Point(lonlat.lon, lonlat.lat);
	 
	    		var attributes = {
	    				assignment_status: "accepted",
	    				userId: globals.gCurrentUserId,
	    			};
	    		var feature = new OpenLayers.Feature.Vector(point, attributes);
	    	    gNewTasksLayer.addFeatures([feature]);
	    	    incrementNewTaskCount();
	        }
	    });
	    gTaskClick = new OpenLayers.Control.Click();
	    map.addControl(gTaskClick);
	    
		// Add markers layer to record the centre of the cell of a region
		markers = new OpenLayers.Layer.Markers( "Markers" );
		markers.id = "Markers";
		map.addLayer(markers);
	
		
	
		if (!map.getCenter()) {
			map.zoomToExtent(new OpenLayers.Bounds(defaultMapExtent[0],defaultMapExtent[1],defaultMapExtent[2],defaultMapExtent[3]));
		}
		
		//Replot on resize events
		$('#map').bind('resized', function() {
			map.updateSize();
		});
	}
	
}

function incrementNewTaskCount() {
	var currentS = $('#new_task_count').html(),
		currentI = 0;
	
	$('#new_task_count').html(parseInt(currentS) + 1);
}

// add a marker
function addMarker(lonlat, clearOld) {
    var size = new OpenLayers.Size(21,25);	
    var offset = new OpenLayers.Pixel(-(size.w/2), -size.h);
	var icon = new OpenLayers.Icon('/images/blue-marker.png', size, offset);   
    var markerslayer = map.getLayer('Markers');
    if(clearOld) {
    	markerslayer.clearMarkers();
    }
    markerslayer.addMarker(new OpenLayers.Marker(lonlat, icon));
	
	var proj = new OpenLayers.Projection("EPSG:4326");
	var point = lonlat.clone().transform(new OpenLayers.Projection("EPSG:900913"), proj);
	globals.gRegion["centre_x"] = lonlat.lon.toFixed(0);	// Metres
	globals.gRegion["centre_y"] = lonlat.lat.toFixed(0);

	$('#region_centre').val(point.lon.toFixed(2) + " : " + point.lat.toFixed(2));

}

// Zoom to layer
function zoomTo(layerName) {
	
	var layer = null;
	if(map) {
		var num = map.getNumLayers();
		for(var i = num - 1; i >= 0; i--) {
			if(!map.layers[i].isBaseLayer && map.layers[i].name == layerName) {
				layer = map.layers[i];
				break;
			}
		}
	}
	
	if(layer) {
		var bounds = null;
		for (var i = 0; i < layer.features.length; i++) {
			var feature = layer.features[i];
			if(feature.geometry) {
				if(!bounds) {
					bounds = feature.geometry.getBounds();
				} else {
					bounds.extend(feature.geometry.getBounds());
				}
			}
		}

		if(bounds != null) {
			if(bounds.getWidth() == 0) {
				bounds.left -= 100;			// Create 200 meter by 200 meter box
				bounds.right += 100;
				bounds.top += 100;
				bounds.bottom -= 100;
			}

			map.zoomToExtent(bounds);
		}
	}
}

/*
 * -----------------------------------------------------------
 * Functions to set the map data
 */
function refreshMap(data) {
	"use strict";
	
	if(typeof OpenLayers !== "undefined") {
		bounds = new OpenLayers.Bounds();
		allLayers = new Array();
		clearLayer("events");
		loadFeatures(data);
		
		if(bounds !=null && bounds.getWidth() != 0) {
			map.zoomToExtent(bounds);
		}
			
		selectResultsControl = new OpenLayers.Control.SelectFeature(allLayers,
	            {onSelect: onFeatureSelectOL, onUnselect: onFeatureUnselect});
		map.addControl(selectResultsControl);
		selectResultsControl.activate();
	}

}

/*
 * Refresh the tasks layer
 */
function refreshMapAssignments(data) {
	"use strict";
	
	bounds = new OpenLayers.Bounds();
	allLayers = new Array();
	clearLayer("assignments");
	loadAssignments(data);
	
	if(bounds !=null && bounds.getWidth() != 0) {
		map.zoomToExtent(bounds);
	} 
		
	selectResultsControl = new OpenLayers.Control.SelectFeature(allLayers,
            {onSelect: onFeatureSelectOL, onUnselect: onFeatureUnselect});
	map.addControl(selectResultsControl);
	selectResultsControl.activate();

}

/*
 * This function adds a vector layer to the map, this layer should consist only of polygons
 * Commonly it would be the same layer that is being used to geo-spatially aggregate the survey
 * data, however if the data isn't being aggregated then it can be any arbitrary vector layer
 */
function setMapRegions(region) {
	
	
	function getRegion(theRegion) {

		var url="/surveyKPI/region/" + theRegion;

		addHourglass();

		$.ajax({
			url: url,
			cache: false,
			success: function(data) {
				removeHourglass();
				var features = JSON.stringify(data);

				var featuresObj = new OpenLayers.Format.GeoJSON({
			        	'internalProjection': new OpenLayers.Projection("EPSG:900913"),
			        	'externalProjection': new OpenLayers.Projection("EPSG:4326")
					}).read(features);
			
				if(featuresObj.constructor != Array) {
			        featuresObj = [featuresObj];
			    }
				
				var defaultStyle = new OpenLayers.Style(
						{				
							pointRadius: "${radius}",
							fillOpacity: 0.0,
							strokeWidth: "${width}",
						});
				var selectStyle = new OpenLayers.Style(
						{
							'fillColor': "blue",
							'fillOpacity': 0.5
						});
				
				var styleMap = new OpenLayers.StyleMap({'default': defaultStyle,
			        'select': selectStyle});
				
				var regionLayer = new OpenLayers.Layer.Vector(theRegion, {styleMap:styleMap});
				regionLayer.addFeatures(featuresObj);
				map.addLayer(regionLayer);
				zoomTo(theRegion);
				allLayers[1] = regionLayer;

			}, error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Failed to set map regions");
				}
			}
			
		});	
	}

	if(globals.gCurrentLayer) {
		clearLayer(globals.gCurrentLayer);
	}
	globals.gCurrentLayer = region;
	getRegion(region);

}

function clearLayer(layerName) {
	var i, num;
	
	if(map) {
		num = map.getNumLayers();
		for(i = num - 1; i >= 0; i--) {
			if(!map.layers[i].isBaseLayer && map.layers[i].name === layerName) {
				map.layers[i].removeAllFeatures();
				map.removeLayer(map.layers[i]);	
				break;	// There should only be one
			}		
		}
	}
}

function loadFeatures(data) {
	
	var features = JSON.stringify(data);

	var featuresObj = new OpenLayers.Format.GeoJSON({
        	'internalProjection': new OpenLayers.Projection("EPSG:900913"),
        	'externalProjection': new OpenLayers.Projection("EPSG:4326")
		}).read(features);
	
	if(!featuresObj) {
		return;
	}
	
	if(featuresObj.constructor != Array) {
        featuresObj = [featuresObj];
    }
	
	var defaultStyle = new OpenLayers.Style(
			{
				fillColor: "green",
				pointRadius: "${radius}",
				fillOpacity: 0.6,
				strokeWidth: "${width}"
			}, {
                context: {
                    width: function(feature) {
                        return (feature.cluster) ? 2 : 1;
                    },
                    radius: function(feature) {
                        var pix = 8;
                        if(feature.cluster) {
                            pix = Math.min(feature.attributes.count, 8) + 8;
                            feature.attributes.status = "success";
                            for(var i = 0; i < feature.cluster.length; i++) {
                            	if(feature.cluster[i].attributes.status === "error") {
                            		feature.attributes.status = "error";
                            		break;
                            	}
                            }

                        }
                        return pix;
                    }
                }
			});
	var selectStyle = new OpenLayers.Style(
			{
				'fillOpacity': 1.0
			});
	
	var lookup = {
			'error' : {fillColor: "red"},
			'filtered' : {fillColor: "orange"},
			'not_loaded' : {fillColor: "yellow"},
			'success' : {fillColor: "green"}
	};
	var styleMap = new OpenLayers.StyleMap({'default': defaultStyle,
        'select': selectStyle});
	styleMap.addUniqueValueRules("default", "status", lookup);
	
	// Compute the bounds

	for (var i = 0; i < featuresObj.length; i++) {
		if(featuresObj[i].geometry) {
			if(!bounds) {
				bounds = featuresObj[i].geometry.getBounds();
			} else {
				bounds.extend(featuresObj[i].geometry.getBounds());
			}
		}
	}

	var strategy = new OpenLayers.Strategy.Cluster({distance: 10, threshold: 3});
	var resultsLayer = new OpenLayers.Layer.Vector("events", {
		strategies: [strategy],
		 styleMap:styleMap
	});

	map.addLayer(resultsLayer);
	resultsLayer.addFeatures(featuresObj);
	allLayers[0] = resultsLayer;
	
	
}

// Respond to a feature being selected
function onFeatureSelectOL(feature) {
	
	var status, assignment;
	
	onFeatureUnselect();
	if(feature.attributes.isSelected === 6) {
		removePendingTask(feature.attributes.task_id, "map");
		feature.attributes.isSelected = 0;
	} else {
		addPendingTask(feature.attributes.task_id, feature.attributes.assignment_id, feature.attributes.assignment_status, "map");
		feature.attributes.isSelected = 6;
	}
	// Action for assignments
	/*
	if(globals.gCurrentUserName && 
				(feature.attributes.assignment_status === "new" || feature.attributes.assignment_status === "accepted")) {
			
		feature.attributes.user_name = globals.gCurrentUserName;
		feature.attributes.assignment_status = "accepted";	// By pass pending state
		globals.gAssignmentsLayer.redraw();
		var user = {id: globals.gCurrentUserId};
		var assignment = {
				assignment_id: feature.attributes.assignment_id,
				assignment_status: "accepted",
				user: user,
				task_id: feature.attributes.task_id			
				};
		globals.gPendingUpdates.push(assignment);
		
	} else if(globals.gDeleteSelected) {
		
		deleteFeature(feature);

	}
	
	else {

		$("#features").show();		  
		$("#features").featureSelect(feature.data, feature.cluster);
		$('#fDel').off().click(function() {	// Closing the panel manually
			$("#features").hide().empty();
		});
	}
	*/

}

/*
function removeFromPending(assignmentId) {
	var i;
	for (i = 0; i < globals.gPendingUpdates.length; i++) {
		if(globals.gPendingUpdates[i].assignment_id === assignmentId) {
			globals.gPendingUpdates.splice(i,1);
			break;
		}
	}
}
*/

function onFeatureUnselect() {
    $("#features").hide().empty();
}

/*
 * --------
 * Assignments specific
 */
function loadAssignments(data) {

	var showCompleted,
		filter,
		filterStrategy,
		featuresToLoad = [],
		i, j;
	
	// Project the coordinates
	var features = JSON.stringify(data);
	var featuresObj = new OpenLayers.Format.GeoJSON({
        	'internalProjection': new OpenLayers.Projection("EPSG:900913"),
        	'externalProjection': new OpenLayers.Projection("EPSG:4326")
		}).read(features);
	
	if(!featuresObj) {
		return;
	}
	
	if(featuresObj.constructor != Array) {
        featuresObj = [featuresObj];
    }
	
	var defaultStyle = new OpenLayers.Style(
			{
				fillColor: "green",
				pointRadius: "${radius}",
				fillOpacity: 1.0,
				strokeWidth: 2,
				strokeWidth: 20,
				fontColor: "blue",
				label: "${user_name}"	
			}, {
                context: {
                    radius: function(feature) {
                        var pix = 8;
                        if(feature.cluster) {
                            pix = Math.min(feature.attributes.count, 8) + 8;
                        }
                        return pix;
                    }
				}
			});
	var selectStyle = new OpenLayers.Style(
			{
				'fillColor': "orange",
				'pointRadius': 8,
				'fillOpacity': 1.0
			});
	
	var lookup = {
			'new' : {fillColor: "red", strokeColor: "orange", strokeWidth: "${isSelected}"},
			'rejected' : {fillColor: "white", strokeColor: "orange", strokeWidth: "${isSelected}"},
			'cancelled' : {fillColor: "white", strokeColor: "orange", strokeWidth: "${isSelected}"},
			'accepted' : {fillColor: "yellow", strokeColor: "orange", strokeWidth: "${isSelected}"},
			'completed' : {fillColor: "green", strokeColor: "orange", strokeWidth: "${isSelected}"},
			'submitted' : {fillColor: "green", strokeColor: "orange", strokeWidth: "${isSelected}"}
	};
	var lookup_width = {
			//'POINT' : {strokeWidth: 2, strokeColor: "black"},
			'LINESTRING' : {strokeWidth: 5},
			'POLYGON' : {strokeWidth: 2}
	};

	var styleMap = new OpenLayers.StyleMap({'default': defaultStyle,
        'select': selectStyle});
	styleMap.addUniqueValueRules("default", "assignment_status", lookup);
	styleMap.addUniqueValueRules("default", "geo_type", lookup_width);

	
	// Compute the bounds and add isSelected setting

	for (var i = 0; i < featuresObj.length; i++) {
		featuresObj[i].attributes.isSelected = 0;
		
		if(featuresObj[i].geometry) {
			if(!bounds) {
				bounds = featuresObj[i].geometry.getBounds();
			} else {
				bounds.extend(featuresObj[i].geometry.getBounds());
			}
		}
	}

	// Filter out features that are not to be shown
	showCompleted = $('#filter_completed').prop('checked');
	if(!showCompleted) {
		for(i= 0, j = 0; i < featuresObj.length; i++) {
			if(featuresObj[i].attributes.assignment_status !== "submitted") {
				featuresToLoad.push(featuresObj[i]);
				j++;
			}
		}
	} else {
		featuresToLoad = featuresObj;
	}

	globals.gAssignmentsLayer = new OpenLayers.Layer.Vector("assignments", {
		 styleMap:styleMap
	});
	
	globals.gAssignmentsLayer.addFeatures(featuresToLoad);
	
	map.addLayer(globals.gAssignmentsLayer);
	allLayers[0] = globals.gAssignmentsLayer;
	
}

function updateMapTaskSelections(task_id, selected) {
	var feats = globals.gAssignmentsLayer.getFeaturesByAttribute("task_id", task_id);
	var selF = feats[0];
	if(selF) {
		selected == true ? selF.attributes.isSelected = 6 : selF.attributes.isSelected = 0;
		globals.gAssignmentsLayer.drawFeature(selF);
	}
}

function registerForClicks() {
	// Register for clicks so we can get the report location
	
	map.events.register("click", map , function(e) {
		var lonlat = map.getLonLatFromViewPortPx(e.xy);
		addMarker(lonlat, false);
	});

}

// Functions for creating new ad-hoc tasks
function registerForNewTasks() {
	
	// Create the new tasks layer if it does not already exist
	if(!gNewTasksLayer) {
		gNewTasksLayer = new OpenLayers.Layer.Vector( "New Tasks" );
		gNewTasksLayer.id = "New Tasks";
		map.addLayer(gNewTasksLayer);
	}
	
	gTaskClick.activate();
}

function clearNewTasks() {
	gTaskClick.deactivate();
	clearLayer("New Tasks");
	gNewTasksLayer = undefined;
	$('#new_task_count').html("0");  // clear New Task Count
}

/*
 * Get the new tasks as geoJSON
 */
function getTasksAsGeoJSON() {
	var featuresString = new OpenLayers.Format.GeoJSON({
		'internalProjection': new OpenLayers.Projection("EPSG:900913"),
		'externalProjection': new OpenLayers.Projection("EPSG:4326")
	}).write(gNewTasksLayer.features);

	return featuresString;
}

/*
function deleteFeature(feature) {		

	if(feature.attributes.assignment_status === "cancelled") {
		feature.attributes.assignment_status = feature.attributes.old_assignment_status;
		removeFromPending(feature.attributes.assignment_id, "map");

	} else {	
		feature.attributes.old_assignment_status = feature.attributes.assignment_status;
		feature.attributes.assignment_status = "cancelled";
		assignment = {
				assignment_id: feature.attributes.assignment_id,
				assignment_status: "cancelled",
				task_id: feature.attributes.task_id			
				};
		globals.gPendingUpdates.push(assignment);
	}
}
*/