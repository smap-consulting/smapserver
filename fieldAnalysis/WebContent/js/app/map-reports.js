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
var map;

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
			setData(dataJSON, map);
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
		arrayOSM,
		arrayHOT;

	// Set options and initialize map
	mapOptions = {
		projection: new OpenLayers.Projection("EPSG:900913"),
		displayProjection: new OpenLayers.Projection("EPSG:4326"),
		units: "m",
		numZoomLevels: 22,
		maxResolution: 156543.0339,
		maxExtent: new OpenLayers.Bounds(-20037508, -20037508, 20037508, 20037508.34),
		fallThrough: false
		//controls: []
	};
	
	loadEnd = false;
	function layerLoadStart(event) {
		loadEnd = false;
	}

	function layerLoadEnd(event) {
		loadEnd = true;
	}
	
	map = new OpenLayers.Map("map", mapOptions);  	
		
	// OSM Tile from mapquest
	arrayOSM = ["https://otile1-s.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg",
                 "https://otile2-s.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg",
                 "https://otile3-s.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg",
                 "https://otile4-s.mqcdn.com/tiles/1.0.0/osm/${z}/${x}/${y}.jpg"];
	
	arrayHOT = ["https://a.tile.openstreetmap.fr/hot/${z}/${x}/${y}.png",
	               "https://b.tile.openstreetmap.fr/hot/${z}/${x}/${y}.png",
	               "https://c.tile.openstreetmap.fr/hot/${z}/${x}/${y}.png"];
	 
	// Add layers
	//map.addLayer(new OpenLayers.Layer.OSM("OSM", arrayOSM,{numZoomLevels: 18, attribution: "(c) OpenStreetMap contributors"}));
	map.addLayer(new OpenLayers.Layer.OSM());
	map.addLayer(new OpenLayers.Layer.OSM("HOT", arrayHOT, {eventListeners: { "loadstart": layerLoadStart,"loadend": layerLoadEnd}, tileOptions: {crossOriginKeyword: null}}));
	if(typeof google != 'undefined' && typeof google.maps != 'undefined') {
		addGoogleMapLayers(map);
	} else {
		getGoogleMapApi(addGoogleMapLayers, map);
	}
	
	// Map Controls
	map.addControl(new OpenLayers.Control.Zoom());
	map.addControl(new OpenLayers.Control.Scale());
	map.addControl(new OpenLayers.Control.LayerSwitcher());

	
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




