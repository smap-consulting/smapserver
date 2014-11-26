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
 * Entry point for user trail page
 */

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

requirejs.config({
    baseUrl: 'js/libs',
    locale: gUserLocale,
    waitSeconds: 0,
    paths: {
     	app: '../app',
    	i18n: 'i18n',
    	jquery: [
    	       '//ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min',
    	       'jquery-1.8.3.min'
    	       ],
    	modernizr: 'modernizr',
    	localise: '../app/localise',
    	bootstrap: 'bootstrap.min',
    	ol: 'ol3/js/ol-debug',
    	lang_location: '..'
    },
    shim: {
    	'app/common': ['jquery'],
    	'ol': ['jquery'],
    	'bootstrap': ['jquery']
    	}
    });


require([
         'jquery', 
         'app/common',
         'bootstrap', 
         'modernizr', 
         'localise',
         'ol'
         
         ], function($, common, bootstrap, modernizr, localise) {
	
var gTrailData;
var gFeatures = [];
var gTrailSource;
var gTrailLayer;
var gMap;

$(document).ready(function() {
	
	gTrailSource = new ol.source.Vector({
		projection: 'EPSG:4326',
		features: gFeatures
		});
	
	gTrailLayer = new ol.layer.Vector ({
		projection: 'EPSG:4326',
		source: gTrailSource
	});
	

    
    getData();
});

function getData() {
	
	var url = '/surveyKPI/usertrail/trail?projectId=1&userId=1&startDate=2000-01-01&endDate=2020-01-01';
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();		
			console.log("Got user trail");
			console.log(data);
			gTrailData = data;
			showData();
			
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get user trail: " + err);
			}
		}
	});	
}

function showData() {
	var i;
	for(i = 0; i < gTrailData.features.length; i++) {
		
		var f = new ol.Feature({
			geometry: new ol.geom.Point(gTrailData.features[i].coordinates),
			name: gTrailData.features[i].time
		});
		gFeatures.push(f);
		gFeatures
	}
	
	gTrailSource.addFeatures(gFeatures);
	
	var base = new ol.layer.Tile({source: new ol.source.MapQuest({layer: 'sat'})}); 
	
    gMap = new ol.Map({
        target: 'map',
        layers: [base, gTrailLayer],
        view: new ol.View({
          center: ol.proj.transform([37.41, 8.82], 'EPSG:4326', 'EPSG:3857'),
          zoom: 4
        })
      });
	//gMap.render();
}
});


