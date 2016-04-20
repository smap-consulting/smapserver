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
	
	var gSurveyMeta = {},
		gTrackingData = {};
	
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
				dont_get_current_survey = true;		// Don't set teh current survey from the users defaults
				globals.gCurrentSurvey = -1;
				// TODO display list of 
			}
		}
		
		// Get the user details
		globals.gIsAdministrator = false;
		getLoggedInUser(getSurveyList, false, true, undefined, false, dont_get_current_survey);
		
		// Get the data for the current survey
		if(!gSurveyMeta[globals.gCurrentSurvey]) {
			getSurveyMetaData(globals.gCurrentSurvey);
		}
		getTrackingData(globals.gCurrentSurvey);

     });
	 
	 function getSurveyList() {
		 loadSurveys(globals.gCurrentProject, undefined, false, false, surveyListDone);
	 }
	 
	 function surveyListDone() {
		 //getSurveyDetails(surveyDetailsDone);
	 }
	 
	 function getTrackingData(sId) {
		 
		 var url = '/api/v1/data/' + sId;
		 
		 addHourglass();
		 $.ajax({
			 url: url,
			 cache: false,
			 dataType: 'json',
			 success: function(data) {
				 removeHourglass();
				 gTrackingData[sId] = data;
				 if(gSurveyMeta[sId]) {
					 showTrackingData(sId);
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
	 
	 function getSurveyMetaData(sId) {
		 
		 var url = '/surveyKPI/questionsInMainForm/' + sId;
		 
		 addHourglass();
		 $.ajax({
			 url: url,
			 cache: false,
			 dataType: 'json',
			 success: function(data) {
				 removeHourglass();
				 gSurveyMeta[sId] = data;
				 if(gTrackingData[sId]) {
					 showTrackingData(sId);
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
	 
	 function showTrackingData(sId) {
		 var x = 1,
		 	tracking = gTrackingData[sId],
		 	meta = gSurveyMeta[sId],
		 	h = [],
		 	idx = -1,
		 	i,
		 	$head = $('#trackingTable').find('thead'),
		 	$body = $('#trackingTable').find('tbody');
		 
		 // Add head
		 h[idx++] = '<tr>';
		 for(i = 0; i < meta.length; i++) {
			 if(i = 0) {
				 h[++idx]= '<th data-toggle="true">';
			 } else {
				 h[++idx] = '<th>';
			 }
			 h[++idx] = meta[i].name;
			 
			 
		 }
		 h[idx++] = '</tr>';
		 
		 	
		 
		$('.footable').footable();
	 }
	

});

