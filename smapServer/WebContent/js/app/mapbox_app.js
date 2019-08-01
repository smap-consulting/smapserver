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
//var map,
//	featureLayer,
//	gUserLocation,		// bounds
//	gLocationLayer;

var gUserLocation,
	gDraggableMarker,
	mapData = {};
	
/**
 * Map Initialisation
 */
function initialiseMap(elementId, zoom, setUserLocation, callbackClick, callbackInitialised) {
	
	if(!L.mapbox.accessToken) {
		addHourglass();
		$.ajax({
			url: '/surveyKPI/server/mapbox',
			cache: false,
			success: function(data) {
				removeHourglass();
				if(data) {
					L.mapbox.accessToken = data;
					initialiseMapKeySet(elementId, zoom, setUserLocation, callbackClick, callbackInitialised);
				} else {
					alert("mapbox key not set");
				}
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Error: Failed to get mapbox key: " + err);
				}
			}
		});	
		
	} else {
		initialiseMapKeySet(elementId, zoom, setUserLocation, callbackClick, callbackInitialised);
	}
	
	 
}

/*
 * This function does the initialisation once the mapbox key has been set
 */
function initialiseMapKeySet(elementId, zoom, setUserLocation, callbackClick, callbackInitialised) {
	
	var mapboxTiles = L.tileLayer('https://api.mapbox.com/v4/mapbox.streets/{z}/{x}/{y}.png?access_token=' + L.mapbox.accessToken, {
	    attribution: '© <a href="https://www.mapbox.com/map-feedback/">Mapbox</a> © <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
	});
	
	var thisMapData = {};
	
	console.log("initialise map: " + elementId);
	
	thisMapData.map = L.mapbox.map(elementId, 'mapbox.streets').setView([0, 0], zoom);
	if(setUserLocation) {
		thisMapData.gLocationLayer = L.mapbox.featureLayer().addTo(thisMapData.map);
	}
	thisMapData.featureLayer = L.mapbox.featureLayer(undefined, {
	    pointToLayer: function(feature, latlon) {
	    	
	    	var option = {
	    		    radius: 8,
	    		    weight: 5,
	    		    stroke: false,
	    		    opacity: 1,
	    		    fillOpacity: 0.8,
	    		    draggable: true
	    		};
	    	
	    	if(feature.properties.blocked) {
	    		option.fillColor = "#aaa";
	    		option.weight = 1;
	    		option.stroke = true;
	    		
	    	} else {
		    	if(feature.properties.status === "new") {
		    		option.fillColor = "#0000ff";
		    	} else if(feature.properties.status === "accepted") {
		    		option.fillColor = "#ffff00";
		    	} else if(feature.properties.status === "accepted") {
		    		option.fillColor = "#ffff00";
		    	} else if(feature.properties.status === "submitted") {
		    		option.fillColor = "#009933";
		    	} else {
		    		option.fillColor = "#fff";
		    	}
	    	}
	    	
	    	if(feature.properties.selected) {
	    		option.stroke = true;
	    		option.color = "#ff9900";
	    	}
	    	
	    	if(feature.geometry.coordinates[0] !== 0 && feature.geometry.coordinates[1] !== 0) {
	        	return L.circleMarker(latlon, option);
	    	}
	    }
	});
	
	/*
	 * Add call backs
	 */
	if(typeof callbackClick === "function") {
		thisMapData.map.on('click', function(data) {
			callbackClick(data.latlng);
		});
	}
	
	thisMapData.featureLayer.addTo(thisMapData.map);
	mapData[elementId] = thisMapData;


	
	/*
	 * Set users current location
	 */
	if(setUserLocation) {
		thisMapData.map.locate();
		thisMapData.map.on('locationfound', function(e) {
			
			gUserLocation = e.bounds;
		    
			thisMapData.gLocationLayer.setGeoJSON({
		        type: 'Feature',
		        geometry: {
		            type: 'Point',
		            coordinates: [e.latlng.lng, e.latlng.lat]
		        },
		        properties: {
		            'title': 'Here I am!',
		            'marker-color': '#ff8888',
		            'marker-symbol': 'star'
		        }
		    });
			zoomToFeatureLayer(elementId);
		});
		
	} else {
			zoomToFeatureLayer(elementId);
	}
	
	if(typeof callbackInitialised === "function") {
		callbackInitialised();
	}


}

/*
 * Assignment specific
 */
function refreshMapAssignments(elementId, taskList) {
	var thisMapData = mapData[elementId],
		i;

	// Filter on status
	var statusFilterArray = $('#status_filter').val();
	var statusFilter = statusFilterArray ? statusFilterArray.join('') : "";
	var statusLookup;

	var filteredGeoJson = {
		type: "FeatureCollection",
		features: []
	};

	// Filter out the tasks
	for (i = 0; i < taskList.features.length; i++) {
		if(statusFilter.indexOf(taskList.features[i].properties.status) >= 0) {
			filteredGeoJson.features.push(taskList.features[i]);
		}
	}

	if(thisMapData) {
		thisMapData.featureLayer.setGeoJSON(filteredGeoJson);
		zoomToFeatureLayer(elementId);
	}
}

/*
 * Add a draggable marker to the map
 */
function clearDraggableMarker(elementId) {
	if(gDraggableMarker) {
		mapData[elementId].map.removeLayer(gDraggableMarker);
	}
	gDraggableMarker = undefined;
}

/*
 * Add a draggable marker to the map
 */
function addDraggableMarker(elementId, latlng, callback) {
	// Creates a single, draggable marker on the page.
	
	if(gDraggableMarker) {
		mapData[elementId].map.removeLayer(gDraggableMarker);
	}
	
	gDraggableMarker = L.marker(latlng, {
	    icon: L.mapbox.marker.icon({
	        'marker-color': '1087bf'
	    }),
	    draggable: true
	}).addTo(mapData[elementId].map).on('dragend', function(data) {
	    if(typeof callback === "function") {
	    	callback(data.target._latlng);
	    }
	    mapData[elementId].map.panTo(data.target._latlng);
	});
	
	mapData[elementId].map.panTo(latlng);
}

/*
 * Zoom to the specified layer
 */
function zoomToFeatureLayer(elementId) {
	"use strict";
	
	var thisMapData = mapData[elementId];
	
	var flBounds = thisMapData.featureLayer.getBounds(),
		bounds,
		validFlBounds = false,
		validUserLocation = false,
		validMarkerLocation = false;
	
	if(flBounds) {
		validFlBounds = flBounds.isValid();
	}
	if(gUserLocation) {
		validUserLocation = gUserLocation.isValid();
	}
	if(gDraggableMarker) {
		validMarkerLocation = true;
	}

	// set initial bounds
	if(validFlBounds) {
		bounds = flBounds;
	} else if(validUserLocation) {
		bounds = gUserLocation;
	} else if(validMarkerLocation) {
		bounds = gDraggableMarker.getLatLng();
	}

	if(bounds) {
		// Extend bounds
		if(validUserLocation) {
			bounds = bounds.extend(gUserLocation);
		}
		if(validMarkerLocation) {
			bounds = bounds.extend(gDraggableMarker.getLatLng());
		}

		// Fit to bounds
		thisMapData.map.fitBounds(bounds);
	}
	

}

