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

var gUserLocation;
var mapData = {};
	
/**
 * Map Initialization
 */
function initializeMap(elementId) {
	
	if(!L.mapbox.accessToken) {
		addHourglass();
		$.ajax({
			url: '/surveyKPI/server',
			cache: false,
			success: function(data) {
				removeHourglass();
				if(data.mapbox_default) {
					L.mapbox.accessToken = data.mapbox_default;
					initializeMapKeySet(elementId);
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
		initializeMapKeySet(elementId);
	}
	
	 
}

/*
 * This function does the initialization once the mapbox key has been set
 */
function initializeMapKeySet(elementId) {
	
	var mapboxTiles = L.tileLayer('https://api.mapbox.com/v4/mapbox.streets/{z}/{x}/{y}.png?access_token=' + L.mapbox.accessToken, {
	    attribution: '© <a href="https://www.mapbox.com/map-feedback/">Mapbox</a> © <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
	});
	
	var thisMapData = {};
	
	console.log("initialise map: " + elementId);
	
	thisMapData.map = L.mapbox.map(elementId, 'mapbox.streets').setView([0, 0], 1);
	thisMapData.gLocationLayer = L.mapbox.featureLayer().addTo(thisMapData.map);
	thisMapData.featureLayer = L.mapbox.featureLayer(undefined, {
	    pointToLayer: function(feature, latlon) {
	    	
	    	var option = {
	    		    radius: 8,
	    		    weight: 5,
	    		    stroke: false,
	    		    opacity: 1,
	    		    fillOpacity: 0.8
	    		};
	    	
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
	    	
	    	if(feature.properties.selected) {
	    		option.stroke = true;
	    		option.color = "#ff9900";
	    	}
	    	
	        return L.circleMarker(latlon, option);
	    }
	});
	mapData[elementId] = thisMapData;

	thisMapData.featureLayer.addTo(thisMapData.map);
	
	/*
	 * Set users current found
	 */
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
}

/*
 * Assignment specific
 */
function refreshMapAssignments(elementId, taskList) {
	var thisMapData = mapData[elementId];
	
	if(thisMapData) {
		thisMapData.featureLayer.setGeoJSON(taskList);
		zoomToFeatureLayer(elementId);
	}
}

/*
 * Zoom to the specified layer
 */
function zoomToFeatureLayer(elementId) {
	"use strict";
	
	var thisMapData = mapData[elementId];
	
	var flBounds = thisMapData.featureLayer.getBounds(),
		validFlBounds = false,
		validUserLocation = false;
	
	if(flBounds) {
		validFlBounds = flBounds.isValid();
	}
	if(gUserLocation) {
		validUserLocation = gUserLocation.isValid();
	}
	
	if(validUserLocation && validFlBounds) {
		thisMapData.map.fitBounds(flBounds.extend(gUserLocation));
	} else if(validFlBounds) {
		thisMapData.map.fitBounds(flBounds);
	} else if(validUserLocation) {
		thisMapData.map.fitBounds(gUserLocation);
	}
}

