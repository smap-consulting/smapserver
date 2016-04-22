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
 * Purpose: Manage the panels that display graphs, maps etc of results data
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
     	i18n: '../../../../js/libs/i18n',
     	async: '../../../../js/libs/async',
     	localise: '../../../../js/app/localise',
    	jquery: '../../../../js/libs/jquery-2.1.1',
    	modernizr: '../../../../js/libs/modernizr',
    	common: '../../../../js/app/common',
    	globals: '../../../../js/app/globals',
    	bootstrap: '../../../../js/libs/bootstrap.min',
    	crf: '../../../../js/libs/commonReportFunctions',
    	lang_location: '../../../../js',
    	file_input: '../../../../js/libs/bootstrap.file-input',
    	mapbox_app: '../../../../js/app/mapbox_app',
    	
    	inspinia: '../../../../js/libs/wb/inspinia',
    	metismenu: '../../../../js/libs/wb/plugins/metisMenu/jquery.metisMenu',
    	slimscroll: '../../../../js/libs/wb/plugins/slimscroll/jquery.slimscroll.min',
    	pace: '../../../../js/libs/wb/plugins/pace/pace.min',
    	footable: '../../../../js/libs/wb/plugins/footable/footable.all.min'
    },
    shim: {

    	'common': ['jquery'],
    	'datetimepicker': ['moment'],
    	'bootstrap': ['jquery'],
    	'app/plugins': ['jquery'],
    	'crf': ['jquery'],
    	'file_input': ['jquery'],
    	'inspinia': ['jquery'],
    	'metismenu': ['jquery'],
    	'slimscroll': ['jquery'],
    	'footable': ['jquery']
	
    	}
    });

require([
         'jquery',
         'bootstrap',
         'common', 
         'localise', 
         'globals',
         'inspinia',
         'metismenu',
         'slimscroll',
         'pace',
         'footable'
         
         ], function($, 
        		 bootstrap, 
        		 common, 
        		 localise, 
        		 globals) {
	
	var cache = {
			surveyConfig: {},
			managedData: {}
		},
		gIsManagedSurvey = false;
	
	 $(document).ready(function() {

		var i,
			params,
			pArray = [],
			param = [],
			openingNew = false,
			dont_get_current_survey = true,
			bs = isBusinessServer();
			
		localise.setlang();		// Localise HTML
		 
		// Get the parameters and show a management survey if required
		params = location.search.substr(location.search.indexOf("?") + 1)
		pArray = params.split("&");
		dont_get_current_survey = false;
		for (i = 0; i < pArray.length; i++) {
			param = pArray[i].split("=");
			if ( param[0] === "id" ) {
				dont_get_current_survey = true;		// Use the passed in survey id
				globals.gCurrentSurvey = param[1];
				saveCurrentProject(-1, globals.gCurrentSurvey);	// Save the current survey id
			} else if ( param[0] === "new" ) {
				dont_get_current_survey = true;		// Don't set the current survey from the users defaults
				globals.gCurrentSurvey = -1;
				// TODO display list of 
			}
		}
		
		// Get the user details
		globals.gIsAdministrator = false;
		getLoggedInUser(refreshData, false, true, undefined, false, dont_get_current_survey);

		/*
		 * Set up dialog to add a new managed survey
		 */
		//$('#addManagedSurvey').on('show.bs.modal', function (event) {
	
		//});
		$('#managedSurveyCreate').click(function(){
			createManagedSurvey($('#newManagedSurvey').val(), 1);	// TODO remove hard coding of managed survey defn (current set to 1)
		});
     });
	 
	 /*
	  * Refresh the data used in this page
	  */
	 function refreshData() {
		 
		 // Clear cache
		 cache.surveyConfig = {};
		 cache.managedData = {};
		 
		 // Get the list of available surveys
		 getSurveyList();
		 
	 }
	 
	 /*
	  * Get the list of available surveys in this project
	  * Called after the user details have been retrieved
	  */
	 function getSurveyList() {
		 loadManagedSurveys(globals.gCurrentProject, managedSurveysLoaded);
	 }
	 
	 function managedSurveysLoaded() {
		 if(globals.gCurrentSurvey > 0 && gIsManagedSurvey) {
			 getManagedData(globals.gCurrentSurvey);
			 getSurveyConfig(globals.gCurrentSurvey);
		 }
	 }
	 
	 /*
	  * Get the tracking data for the specified survey
	  */
	 function getManagedData(sId) {
		 
		 var url = '/api/v1/data/' + sId;
		 
		 addHourglass();
		 $.ajax({
			 url: url,
			 cache: false,
			 dataType: 'json',
			 success: function(data) {
				 removeHourglass();
				 cache.managedData[sId] = data;
				 if(cache.surveyConfig[sId]) {
					 showManagedData(sId);
				 }
			 },
			 error: function(xhr, textStatus, err) {
				 removeHourglass();
				 if(xhr.readyState == 0 || xhr.status == 0) {
					 return;  // Not an error
				 } else {
						alert("Error failed to get data from survey:" + sId);
				 }
			 }
		 });
	 }
	 
	 
	 function showManagedData(sId) {
		 
		 var x = 1,
		 	managed = cache.managedData[sId],
		 	config = cache.surveyConfig[sId],
		 	h = [],
		 	idx = -1,
		 	i,j,
		 	$table = $('#trackingTable'),
		 	doneFirst = false,
		 	headItem;
		 
		 // Add head
		 h[++idx] = '<thead>';
		 h[++idx] = '<tr>';
		 for(i = 0; i < config.length; i++) {
			 headItem = config[i];
			 
			 if(headItem.include) {
				 if(!doneFirst) {
					 h[++idx]= '<th data-toggle="true">';
					 doneFirst = true;
				 } else {
					 if(headItem.hide) {
						 h[++idx] = '<th data-hide="all">';
					 } else {
						 h[++idx] = '<th>';
					 }
				 }
				 h[++idx] = headItem.name;
				 h[++idx] = '</th>';
			 }
		 }
		 h[++idx] = '</tr>';
		 h[++idx] = '</thead>';
		 
		 // Add body
		 h[++idx] = '<tbody>';
		 for(j = 0; j < managed.length; j++) {
			 record = managed[j];
			 h[++idx] = '<tr>';
			 for(i = 0; i < config.length; i++) {
				 headItem = config[i];
				 if(headItem.include) {
					 h[++idx] = '<td>';
					 h[++idx] = record[headItem.name];
					 h[++idx] = '</td>';
				 }
			 }
			 h[++idx] = '</tr>';
			 
		 }
		 h[++idx] = '</tbody>';
		 
		 $table.empty().append(h.join(''));	
		 $('.footable').footable();
	 }
	 
	/*
	 * Get surveys and update the survey lists on this page
	 *  This is a different function from the common loadSurveys function as processing differs depending on whether there is tracking
	 *   applied to the survey
	 */	
	 function loadManagedSurveys(projectId, callback) {
	 	
	 	var url="/surveyKPI/surveys?projectId=" + projectId + "&blocked=true",
	 		$elemNonTracking = $('.nonTrackingSurveys'),
	 		$elemTracking = $('#surveyTable').find('tbody');

	 	
		gIsManagedSurvey = false;
	 	if(typeof projectId !== "undefined" && projectId != -1 && projectId != 0) {
	 		
	 		addHourglass();

	 		$.ajax({
	 			url: url,
	 			dataType: 'json',
	 			cache: false,
	 			success: function(data) {
	 				
	 				var i,
	 					item,
	 					hNT = [],
	 					idxNT = -1,
	 					hT = [],
	 					idxT = -1,
	 					firstSurvey = -1;
	 				
	 				removeHourglass();
	 				for(i = 0; i < data.length; i++) {
	 					item = data[i];
	 					if(item.managed_id === 0) {
	 						hNT[++idxNT] = '<option value="';
	 						hNT[++idxNT] = item.id;
	 						hNT[++idxNT] = '">';
	 						hNT[++idxNT] = item.displayName;
	 						hNT[++idxNT] = '</option>';
	 					} else {
	 						hT[++idxT] = '<tr>';
	 							hT[++idxT] = '<th data-toggle="true">';
	 							hT[++idxT] = item.displayName;
	 							hT[++idxT] = '</th>';
	 						hT[++idxT] = '</tr>';
	 					}
	 					
	 					if(i == 0) {
	 						if(globals.gCurrentSurvey <= 0) {
	 		 					globals.gCurrentSurvey = item.id;
	 		 					saveCurrentProject(-1, globals.gCurrentSurvey);
	 		 				}
	 					}
	 					if(item.id === globals.gCurrentSurvey) {
	 						gIsManagedSurvey = true;
	 					}
	 				}

	 				$elemNonTracking.empty().html(hNT.join(''));
	 				$elemTracking.empty().html(hT.join(''));
	 				
	 				if(typeof callback == "function") {
	 					callback();
	 				}
	 			},
	 			error: function(xhr, textStatus, err) {
	 				
	 				removeHourglass();
	 				if(xhr.readyState == 0 || xhr.status == 0) {
	 		              return;  // Not an error
	 				} else {
	 					console.log("Error: Failed to get list of surveys: " + err);
	 				}
	 			}
	 		});	
	 	} else {
	 		$elem.empty();
	 		if(addAll) {
	 			$elem.append('<option value="_all">All Surveys</option>');	
	 		}
	 		
	 		if(callback) {
	 			callback();
	 		}

	 	}
	 }
	
	 /*
	  * Get the columns for a survey
	  */
	 function getSurveyConfig(sId) {
		 
		 if(!cache.surveyConfig[sId]) {
			 var url = '/surveyKPI/managed/questionsInMainForm/' + sId;
			 
			 addHourglass();
			 $.ajax({
				 url: url,
				 cache: false,
				 dataType: 'json',
				 success: function(data) {
					 removeHourglass();
					 cache.surveyConfig[sId] = data;
					 if(cache.managedData[sId]) {
						 showManagedData(sId);
					 }
				 },
				 error: function(xhr, textStatus, err) {
					 removeHourglass();
					 if(xhr.readyState == 0 || xhr.status == 0) {
						 return;  // Not an error
					 } else {
							alert("Error failed to get column names from survey: " + sId + " " + xhr.responseText);
					 }
				 }
			 });
		 } 
		 
		 
	 }
	 
	 /*
	  * Create a new managed Survey
	  */
	 function createManagedSurvey(sId, manageId) {
		 
		 var saveObj = {
				 sId: sId,
				 manageId: manageId
		 }
		 
		 saveString = JSON.stringify(saveObj);
		 addHourglass();
		 $.ajax({
			 type: "POST",
				  dataType: 'text',
				  contentType: "application/json",
				  url: "/surveyKPI/managed/add",
				  data: { settings: saveString },
				  success: function(data, status) {
					  removeHourglass();
					  
				  }, error: function(data, status) {
					  removeHourglass();
					  refreshData();
					  alert("Error: Failed to create managed service");
				  }
			});
	 }

});

