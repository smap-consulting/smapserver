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
var map,
	featureLayer,
	gUserLocation,		// bounds
	gLocationLayer;
	
/**
 * Map Initialization
 */
function initializeMap() {
	
	if(!L.mapbox.accessToken) {
		addHourglass();
		$.ajax({
			url: '/surveyKPI/server',
			cache: false,
			success: function(data) {
				removeHourglass();
				if(data.mapbox_default) {
					L.mapbox.accessToken = data.mapbox_default;
					initializeMapKeySet();
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
		initializeMapKeySet();
	}
	
	 
}

/*
 * This function does the initialization once the mapbox key has been set
 */
function initializeMapKeySet() {
	
	var mapboxTiles = L.tileLayer('https://api.mapbox.com/v4/mapbox.streets/{z}/{x}/{y}.png?access_token=' + L.mapbox.accessToken, {
	    attribution: '© <a href="https://www.mapbox.com/map-feedback/">Mapbox</a> © <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>'
	});
	
	map = L.mapbox.map('map', 'mapbox.streets').setView([0, 0], 1);
	
	gLocationLayer = L.mapbox.featureLayer().addTo(map);
	
	featureLayer = L.mapbox.featureLayer(undefined, {
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
	featureLayer.addTo(map);
	
	/*
	 * Set users current found
	 */
	map.locate();
	map.on('locationfound', function(e) {
		
		gUserLocation = e.bounds;
	    
		gLocationLayer.setGeoJSON({
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
		
		zoomToFeatureLayer();

	});
}

/*
 * Assignment specific
 */
function refreshMapAssignments() {
	featureLayer.setGeoJSON(globals.gTaskList);
	zoomToFeatureLayer();
}

/*
 * Zoom to the specified layer
 */
function zoomToFeatureLayer() {
	"use strict";
	
	var flBounds = featureLayer.getBounds(),
		validFlBounds = false,
		validUserLocation = false;
	
	if(flBounds) {
		validFlBounds = flBounds.isValid();
	}
	if(gUserLocation) {
		validUserLocation = gUserLocation.isValid();
	}
	
	if(validUserLocation && validFlBounds) {
		map.fitBounds(flBounds.extend(gUserLocation));
	} else if(validFlBounds) {
		map.fitBounds(flBounds);
	} else if(validUserLocation) {
		map.fitBounds(gUserLocation);
	}
}

