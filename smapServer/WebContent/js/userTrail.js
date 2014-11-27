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
         'modernizr', 
         'app/localise',
         'app/globals',
         'ol3/js/ol',
         'moment.min',
         'bootstrap-datetimepicker.min'
         
         ], function($, common, bootstrap, modernizr, localise, globals) {
	
var gTrailData;
var gFeatures = [];
var gTrailSource;
var gTrailLayer;
var gMap;

$(document).ready(function() {
	
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
	
	var base = new ol.layer.Tile({source: new ol.source.MapQuest({layer: 'sat'})}); 
	
	gTrailSource = new ol.source.Vector({
		features: gFeatures
		});
	
	gTrailLayer = new ol.layer.Vector ({
		source: gTrailSource
	});
	
	getLoggedInUser(getUserList, false, true, undefined, true, true);
	
	// Add responses to events
	$('#project_list').change(function() {
		globals.gCurrentProject = $('#project_list option:selected').val();
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
		getUserList();
 	 });
	
	// Add responses to events
	$('#user_list').change(function() {
		
		getTrailData(
				globals.gCurrentProject, 
				$('#user_list option:selected').val(),
				$('#startDate').data("DateTimePicker").getDate().format("YYYY-MM-DD"),
				$('#endDate').data("DateTimePicker").getDate().format("YYYY-MM-DD")
				);
 	 });

	// Show the map

	
    gMap = new ol.Map({
        target: 'map',
        layers: [base, gTrailLayer],
        view: new ol.View({
          center: ol.proj.transform([37.41, 8.82], 'EPSG:4326', 'EPSG:3857'),
          zoom: 4
        })
      });
    
});

function getUserList(projectId) {
	
	addHourglass();
	$.ajax({
		url: "/surveyKPI/userList/" + globals.gCurrentProject,
		cache: false,
		dataType: 'json',
		success: function(data) {
			removeHourglass();
			console.log(data);
			updateUserList(data);
			getTrailData(
					globals.gCurrentProject, 
					$('#user_list option:selected').val(),
					$('#startDate').data("DateTimePicker").getDate().format("YYYY-MM-DD"),
					$('#endDate').data("DateTimePicker").getDate().format("YYYY-MM-DD")
					);

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				console.log("Error: Failed to get user list: " + err);
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

function getTrailData(projectId, userId, startDate, endDate) {
	
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
			console.log("Got user trail");
			console.log(data);
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

function showUserTrail() {
	var i,
		lineFeature,
		coords = [];
	
	gTrailSource.clear();
	
	// Add points
	for(i = 0; i < gTrailData.features.length; i++) {
		
		var f = new ol.Feature({
			geometry: new ol.geom.Point(gTrailData.features[i].coordinates),
			name: gTrailData.features[i].time
		});
		gFeatures.push(f);
		coords.push(gTrailData.features[i].coordinates);
	}
	gTrailSource.addFeatures(gFeatures);
	
	// Add line
	lineFeature = new ol.Feature({
		geometry: new ol.geom.LineString(coords)
	});
	gTrailSource.addFeature(lineFeature);
	


	gMap.render();
}
});


