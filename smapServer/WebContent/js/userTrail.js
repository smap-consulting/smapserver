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
    	lang_location: '..'
    },
    shim: {
    	'app/common': ['jquery'],
    	'bootstrap.min': ['jquery'],
    	'bootstrap-datetimepicker.min': ['moment.min']
    	}
    });


require([
         'jquery', 
         'app/common',
         'bootstrap.min', 
         'app/localise',
         'app/globals',
         'ol3/js/ol',
         'moment.min',
         'bootstrap-datetimepicker.min',
         'app/version'
         
         ], function($, common, bootstrap, localise, globals) {

var gOverlayHasFeature;
var gTrailData;
var gSurveys = [];
var gTrailSource;
var gSurveyLocations;
var gSurveyLocationLayer;
var gSurveyLocationSource;
var gTrailLayer;
var featureOverlay;
var gMap;
var point = null;
var line = null;
var gTime = {
	  start: Infinity,
	  stop: -Infinity,
	  duration: 0
	};

// Style for survey locations
var iconStyle = new ol.style.Style({
	  image: new ol.style.Icon(/** @type {olx.style.IconOptions} */ ({
	    anchor: [0.5, 0.5],
	    anchorXUnits: 'fraction',
	    anchorYUnits: 'fraction',
	    height: 36,
	    width: 36,
	    opacity: 0.75,
	    src: 'images/survey.png'
	  }))
	});

// Style for selected points
var imageStyle = new ol.style.Circle({
	  radius: 5,
	  fill: null,
	  stroke: new ol.style.Stroke({
	    color: 'rgba(255,0,0,0.9)',
	    width: 1
	  })
	});

// Style for line to selected points
var strokeStyle = new ol.style.Stroke({
	color: 'rgba(255,0,0,0.9)',
	width: 3
});

//Style for user trail
var trailStyle = new ol.style.Style({
		stroke: new ol.style.Stroke({
			color: 'rgba(20,20,237,0.8)',
			width: 3
		})
});

$(document).ready(function() {
	
	localise.setlang();
		
	// Set up the start and end dates with date picker
	$('#startDate').datetimepicker({
		pickTime: false,
		useCurrent: false
	});
	$('#startDate').data("DateTimePicker").setDate(moment());
	
	$('#endDate').datetimepicker({
		pickTime: false,
		useCurrent: false
	});
	$('#endDate').data("DateTimePicker").setDate(moment());
	
	// Set base layers
	var osm = new ol.layer.Tile({source: new ol.source.OSM()}); 
	//var osmVisible = new ol.dom.Input(document.getElementById('osmVisible'));
	//osmVisible.bindTo('checked', base, 'osmVisible');
	
	// Source and Layer objects for gps points
	gTrailSource = new ol.source.Vector({
		});
	
	gTrailLayer = new ol.layer.Vector ({
		source: gTrailSource
	});
	
	// Source and Layer objects for gps path
	//gPathSource = new ol.source.Vector({
	//	});
	
	//gPathLayer = new ol.layer.Vector ({
	//	source: gPathSource
	//});
	
	// Source and Layer objects for survey locations
	gSurveyLocationSource = new ol.source.Vector({
		features: gSurveys
		});
	
	gSurveyLocationLayer = new ol.layer.Vector ({
		source: gSurveyLocationSource,
		style: iconStyle
	});
	
	getLoggedInUser(getUserList, false, true, undefined, false, true);
	
	// Add responses to events
	$('#project_list').change(function() {
		globals.gCurrentProject = $('#project_list option:selected').val();
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
		getUserList();
 	 });
	
	// Add responses to changing parameters
	$('#user_list').change(function() {		
		getData();
 	 });
	
	// Add responses to changing parameters
	$('#startDate,#endDate').change(function(e) {	
		if(validDates()) {
			getData();
			return true;
		} 
 	 });


	// Add the map	
    gMap = new ol.Map({
        target: 'map',
        layers: [osm, gTrailLayer, gSurveyLocationLayer],
        view: new ol.View(
        		{
        			center: ol.proj.transform([0.0, 0.0], 'EPSG:4326', 'EPSG:3857'),
        			zoom: 1
        		}
        	)
      });
    
    // Overlay to highlight time of slider
    featureOverlay = new ol.FeatureOverlay({
    	  map: gMap,
    	  style: new ol.style.Style({
    	    image: new ol.style.Circle({
    	      radius: 5,
    	      fill: new ol.style.Fill({
    	        color: 'rgba(255,0,0,0.9)'
    	      }),
    	      stroke: null
    	    })
    	  })
    	});
    
    $(gMap.getViewport()).on('click', function(evt) {
    	
    	var coordinate = gMap.getEventCoordinate(evt.originalEvent);   	  
    	displaySnap(coordinate);
    	  
    });
    
    gMap.on('postcompose', function(evt) {
    	  var vectorContext = evt.vectorContext;
    	  if (point !== null) {
    	    vectorContext.setImageStyle(imageStyle);
    	    vectorContext.drawPointGeometry(point);
    	  }
    	  if (line !== null) {
    	    vectorContext.setFillStrokeStyle(null, strokeStyle);
    	    vectorContext.drawLineStringGeometry(line);
    	  }
    	});
    
    // Enable tooltips
    $('[data-toggle="tooltip"]').tooltip();
	
    // Enable the time slider
    $('#time').on('input', function(event) {
    	
    	point = null;		// Clear any selected points
	    line = null;
	    
    	var value = parseInt($(this).val(), 10) / 100;
    	var m = gTime.start + (gTime.duration * value);
    	gTrailSource.forEachFeature(function(feature) {
    		var geometry = /** @type {ol.geom.LineString} */ (feature.getGeometry());
    	    var coordinate = geometry.getCoordinateAtM(m, true);
    	    var highlight = feature.get('highlight');
    	    if (highlight === undefined) {
    	      highlight = new ol.Feature(new ol.geom.Point(coordinate));
    	      feature.set('highlight', highlight);
    	    } else {
    	      highlight.getGeometry().setCoordinates(coordinate);
    	    }
    	    if(!gOverlayHasFeature) {
    	    	featureOverlay.addFeature(highlight);
    	    	gOverlayHasFeature = true;
    	    }
    	});
    	gMap.render();
    	  
  	   	var date = new Date(m);	// Using Measure coordinate to store unix date
  	    document.getElementById('info').innerHTML = date;
  	    
    });
    
	// From: http://stackoverflow.com/questions/20247945/bootstrap-3-navbar-dynamic-collapse
    /*
	function autocollapse() {
	    var $navbar = $('.navbar');
	    $navbar.removeClass('collapsed'); 
	    if($navbar.innerHeight() > 60) // check if we've got 2 lines
	        $navbar.addClass('collapsed'); // force collapse mode
	}

	$(document).on('ready', autocollapse);
	$(window).on('resize', autocollapse);
	*/
	enableUserProfileBS();
    
});



function getUserList(projectId) {
	
	addHourglass();
	$.ajax({
		url: "/surveyKPI/userList/" + globals.gCurrentProject,
		cache: false,
		dataType: 'json',
		success: function(data) {
			removeHourglass();
			updateUserList(data);
			getData();

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				alert("Error: Failed to get user list: " + err);
			}
		}
	});	
}

function updateUserList(users, addAll) {

	var $userSelect = $('.user_list'),
		i, 
		h = [],
		idx = -1;
	
	if(addAll) {
		h[++idx] = '<option value="0">All</option>';
		updateCurrentProject = false;
	}
	for(i = 0; i < users.length; i++) {
		h[++idx] = '<option value="';
		h[++idx] = users[i].id;
		h[++idx] = '">';
		h[++idx] = users[i].name;
		h[++idx] = '</option>';
		
		
	}
	$userSelect.empty().append(h.join(''));
}

function getData() {
	
	var startDate = $('#startDate').data("DateTimePicker").getDate().startOf('day'),
		endDate = $('#endDate').data("DateTimePicker").getDate().endOf('day');	// Get end of displayed date
	
	var startUtc = moment.utc(startDate),
		endUtc = moment.utc(endDate);
		
	getTrailData(startUtc.valueOf(), endUtc.valueOf());
	getSurveyLocations(startUtc.valueOf(), endUtc.valueOf());

}
function getTrailData(startDate, endDate) {
	
	var projectId = globals.gCurrentProject,
		userId = $('#user_list option:selected').val();

	var url = '/surveyKPI/usertrail/trail?projectId=' + projectId +
		'&userId=' + userId +
		'&startDate=' + startDate +
		'&endDate=' + endDate;
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();		
			gTrailData = data;
			showUserTrail();
			
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

function getSurveyLocations(startDate, endDate) {
	
	var projectId = globals.gCurrentProject,
		userId = $('#user_list option:selected').val();

	var url = '/surveyKPI/usertrail/surveys?projectId=' + projectId +
		'&userId=' + userId +
		'&startDate=' + startDate +
		'&endDate=' + endDate;
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();		
			gSurveyLocations = data;
			showSurveyLocations();
			
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

function showUserTrail() {
	var i,
		lineFeature,
		coords = [];
	
	gTrailSource.clear();
	
	// Add points
	for(i = 0; i < gTrailData.features.length; i++) {
		
		gTrailData.features[i].coordinates.push(gTrailData.features[i].rawTime);	// Add attributes to Measure coordinate
		coords.push(gTrailData.features[i].coordinates);
	}
	if(coords.length > 0) {
		var geometry = new ol.geom.LineString(coords, 'XYM');
		var lineFeature = new ol.Feature({
			geometry: geometry
		});
		lineFeature.setStyle(trailStyle);
		gTrailSource.addFeature(lineFeature);
		
		gTime.start = Math.min(gTime.start, geometry.getFirstCoordinate()[2]);
		gTime.stop = Math.max(gTime.stop, geometry.getLastCoordinate()[2]);
		gTime.duration = gTime.stop - gTime.start;
	}
	
	gMap.getView().fitExtent(gTrailSource.getExtent(), gMap.getSize());

	gMap.render();
}

function showSurveyLocations() {
	var i,
		lineFeature,
		coords = [];
	
	gSurveyLocationSource.clear();
	gSurveys = [];
	
	// Add points
	for(i = 0; i < gSurveyLocations.surveys.length; i++) {
		
		var f = new ol.Feature({
			geometry: new ol.geom.Point(gSurveyLocations.surveys[i].coordinates),
			name: gSurveyLocations.surveys[i].time
		});
		//f.setStyle(iconStyle);
		
		gSurveys.push(f);
		coords.push(gSurveyLocations.surveys[i].coordinates);
	}
	
	gSurveyLocationSource.addFeatures(gSurveys);
	
	// TODO fit the extent to the combination of trail data and survey locations
	gMap.getView().fitExtent(gTrailSource.getExtent(), gMap.getSize());

	gMap.render();
}

// Show data for closes features
var displaySnap = function(coordinate) {
	
	// Clear the slider
	var overlays = featureOverlay.getFeatures(),
		i;
	for(i = 0; i < overlays.a.length; i++) {
		featureOverlay.removeFeature(overlays.a[i]);
	}
	gOverlayHasFeature = false;
	  
	var closestFeature = gTrailSource.getClosestFeatureToCoordinate(coordinate);
	var info = document.getElementById('info');
	  
	if (closestFeature === null) {
	    point = null;
	    line = null;
	    info.innerHTML = '&nbsp;';
	  } else {
	    var geometry = closestFeature.getGeometry();
	    var closestPoint = geometry.getClosestPoint(coordinate);
	    if (point === null) {
	      point = new ol.geom.Point(closestPoint);
	    } else {
	      point.setCoordinates(closestPoint);
	    }
	
	    var date = new Date(closestPoint[2]);	// Using Z coordinate to store unix date
	    info.innerHTML = date;
	    var coordinates = [coordinate, [closestPoint[0], closestPoint[1]]];
	    if (line === null) {
	      line = new ol.geom.LineString(coordinates);
	    } else {
	      line.setCoordinates(coordinates);
	    }
	  }
	  gMap.render();
	};
});


