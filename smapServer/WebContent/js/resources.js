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
 * Purpose: Allow the user to select a web form in order to complete a survey
 */
var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

requirejs.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    paths: {
    	app: '../app',
    	jquery: [
      	       '//ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min',
      	       '../../../../js/libs/jquery-1.8.3.min'
      	       ],
       	lang_location: '../'
    },
    shim: {
    	'app/common': ['jquery'],
    	'bootstrap.min': ['jquery']
    }
});

require([
         'jquery', 
         'bootstrap.min',
         'app/common', 
         'app/globals',
         'app/localise',
         'bootstrapfileinput'
         ], function($, bootstrap, common, globals, localise, bsfi) {

	var gMaps,
		gMapVersion,
		gMapId;
	
$(document).ready(function() {
	
	var url = '/surveyKPI/upload/media';
	localise.setlang();		// Localise HTML
	
	// Get the user details
	globals.gIsAdministrator = false;
	globals.gCurrentSurvey = undefined;
	
	getFilesFromServer(url, undefined, refreshMediaView);		// Get the organisational level media files
	
	// Set up the tabs
    $('#mediaTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		
		$('#mapPanel').hide();
		$('#mediaPanel').show();
    })
    $('#mapTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		  	  
		$('#mapPanel').show();
		$('#mediaPanel').hide();
    })
    
    $('.file-inputs').bootstrapFileInput();
    $('#upload_msg').removeClass('alert-danger').addClass('alert-success').html("");
    
    // Respond to file upload
    $('#submitFiles').click( function() {
    	uploadFiles(url, "fileupload", refreshMediaView);
    });
    
    /*
     * Set up maps tab
     */
	$('#addMap').click(function(){
		edit_map();
		$('#addMapPopup').modal("show");
	});
	// Enable the save notifications function
	$('#saveMap').click(function(){saveMap();});
	
	enableUserProfileBS();
});


function edit_map(idx) {
	
	var map,
		title = localise.set["msg_add_map"];
	
	document.getElementById("map_edit_form").reset();
	
	if(typeof idx !== "undefined") {
		map = gMaps[idx];

		title = localise.set["msg_edit_map"],
		
		$('#map_name').val(map.name);
		$('#map_type').val(map_name);
		$('#map_description').val(map.description);
		
		gMapVersion = map.version;
		gMapId = map.id;
	} else {
		gMapVersion = 1;
		gMapId = -1;
	}
	
	$('#addMapLabel').html(title);
	
}

/*
 * Save a map
 */
function saveMap() {
	
	var map,
		url = "/surveyKPI/shared/maps",
		mapString;
	
	map = {};
	map.name = $('#map_name').val();
	map.type = $('#map_name').val();
	map.description = $('#map_description').val();
	map.version = gMapVersion;
	map.id = gMapId;
	
	mapString = JSON.stringify(map);
	addHourglass();
	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  dataType: "json",
		  async: false,
		  url: url,
		  data: { map: mapString },
		  success: function(data, status) {
			  removeHourglass();
			  //getMaps();
			  $('#addMapPopup').modal("hide");
		  },
		  error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert(localise.set["msg_err_save"] + xhr.responseText);
				}
			}
	});
			        	    	

}	

});

