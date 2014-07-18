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

var defaultMapExtent = [-20037508, -20037508, 20037508, 20037508.34];
var gMap;

/**
 * Get the report data afer initialising the map
 */
function getData(feature_url) {
	
	var hourGlass,
		xmlhttp,
		dataJSON;

	// Show hourglass 
	hourGlass = document.getElementById('hour_glass');
	hourGlass.style.display="block";
	
	initializeMap();

	xmlhttp = new XMLHttpRequest();
	  
	xmlhttp.onreadystatechange=function() {
		if (xmlhttp.readyState === 4 && xmlhttp.status === 200) {
			dataJSON = JSON.parse(xmlhttp.responseText);
			setData(dataJSON, gMap);
		}
		hourGlass.style.display="none";
	}
	
	xmlhttp.open("GET", feature_url, true);
	xmlhttp.send();
	
}

/**
 * Map Initialization
 */
function initializeMap() {
	
	var mapOptions,
		arrayOSM;

	// Set options and initialize map
	mapOptions = {
		projection: new OpenLayers.Projection("EPSG:900913"),
		displayProjection: new OpenLayers.Projection("EPSG:4326"),
		units: "m",
		numZoomLevels: 16,
		maxResolution: 156543.0339,
		maxExtent: new OpenLayers.Bounds(-20037508, -20037508, 20037508, 20037508.34),
		fallThrough: false,
		controls: []
	};
	
	gMap = new OpenLayers.Map("map", mapOptions);  	
		
	// OSM Tile from mapquest
	arrayOSM = ["http://otile1.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg",
                 "http://otile2.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg",
                 "http://otile3.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg",
                 "http://otile4.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg"];
	 
	// Add layers
	gMap.addLayer(new OpenLayers.Layer.OSM("OSM", arrayOSM,{numZoomLevels: 18, attribution: "(c) OpenStreetMap contributors"}));
	if(typeof google != 'undefined') {
		gMap.addLayer(new OpenLayers.Layer.Google("Google Satellite",{type: google.maps.MapTypeId.SATELLITE, 'sphericalMercator': true, numZoomLevels: 18}));
		gMap.addLayer(new OpenLayers.Layer.Google("Google Maps",{type: google.maps.MapTypeId.ROADMAP, 'sphericalMercator': true, numZoomLevels: 18}));
		gMap.addLayer(new OpenLayers.Layer.Google("Google Hybrid",{type: google.maps.MapTypeId.HYBRID, 'sphericalMercator': true, numZoomLevels: 18}));
	}
	
	// Map Controls
	gMap.addControl(new OpenLayers.Control.PanZoomBar());
	//gMap.addControl(new OpenLayers.Control.Scale());
	gMap.addControl(new OpenLayers.Control.LayerSwitcher());	
	

	
}

/*
 * -----------------------------------------------------------
 * Functions to set the map data
 */
function setData(layers, map) {
	
	var i,
		view = {},		// Object to store common data used by multiple layers
		title;
	
	for(i = 0; i < layers.length; i++) {
		
		if(typeof layers[i].question !== "undefined") {
			title = layers[i].question;
		} else {
			title = "layer " + i;
		}
		addLayer(layers[i], 0, i, view, title, map);
	}
	
	if(typeof view.bounds != "undefined") {
		map.zoomToExtent(view.bounds);
	}
}




