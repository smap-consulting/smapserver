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

"use strict";
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
    	datetimepicker: '../../../../js/libs/bootstrap-datetimepicker.min',
    	
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
         'footable',
         'datetimepicker',
         
         ], function($, 
        		 bootstrap, 
        		 common, 
        		 localise, 
        		 globals) {
	
	var cache = {
			surveyConfig: {},
			managedData: {}
		},
		gManageId = undefined,
		gUpdate = [],
		gCurrentIndex,
		gPriKey,
		gManagedForms,
		gSelectedSurvey;
	
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

		enableUserProfileBS();	
		
		$('#saveRecord').click(function(){
			 saveString = JSON.stringify(gUpdate);
			 addHourglass();
			 $.ajax({
				 type: "POST",
					  dataType: 'text',
					  cache: false,
					  contentType: "application/json",
					  url: "/surveyKPI/managed/update/" + globals.gCurrentSurvey + "/" + gManageId,
					  data: { settings: saveString },
					  success: function(data, status) {
						  removeHourglass();
						  getManagedData(globals.gCurrentSurvey);
					  }, error: function(data, status) {
						  removeHourglass();
						  alert(data.responseText);
					  }
				});
		});
		
		/*
		 * Create new managed surveys
		 */
		$('#managedSurveyCreate').click(function(){
			createManagedSurvey(
					$('#newManagedSurvey').val(), 
					$('#newOversightForm').val());	
		});
		
		/*
		 * Change the oversight form associated with a managed survey
		 */
		$('#managedSurveyChange').click(function(){
			createManagedSurvey(
					gSelectedSurvey, 
					$('#newOversightForm').val());	
		});
		
		/*
		 * Respond to adding report
		 */
		$('#addReport').click(function(){
			$('.panel_msg').hide();
			document.forms.namedItem("crupload").reset();
			$('#addReportPopup').modal("show");
		});
		
		// Respond to custom report upload
	    $('#submitCustomReport').click( function() {
	    	var reportName = $('#report_name').val(),
	    		fileName = $('#report_file').val();
	    	
	    	if(!reportName || reportName.trim().length == 0) {
	    		$('.upload_file_msg').removeClass('alert-success').addClass('alert-danger').html(localise.set["msg_val_nm"]);
	    		return false;
	    	}
	    	if(!fileName || fileName.trim().length == 0) {
	    		$('.upload_file_msg').removeClass('alert-success').addClass('alert-danger').html(localise.set["msg_val_file"]);
	    		return false;
	    	}
	    	
	    	uploadFiles('/surveyKPI/upload/customreport', "crupload", refreshCustomReportView, undefined);
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
		 getReports(showReportList, refreshCustomReportView, "oversight");
		 loadManagedSurveys(globals.gCurrentProject, managedSurveysLoaded);
	 }
	 
	 function managedSurveysLoaded() {
		 if(globals.gCurrentSurvey > 0 && gManageId) {
			 getManagedData(globals.gCurrentSurvey);
			 getSurveyConfig(globals.gCurrentSurvey, gManageId);
		 }
	 }
	 
	 /*
	  * Get the tracking data for the specified survey
	  */
	 function getManagedData(sId) {
		 
		 var url = '/api/v1/data/' + sId + "?mgmt=true";
		 
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
		 	columns = cache.surveyConfig[sId],

		 	h = [],
		 	idx = -1,
		 	i,j,
		 	$table = $('#trackingTable'),
		 	doneFirst = false,
		 	headItem;
		 
		 // Add head
		 h[++idx] = '<thead>';
		 h[++idx] = '<tr>';
		 for(i = 0; i < columns.length; i++) {
			 headItem = columns[i];
			 
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
				 h[++idx] = headItem.humanName;
				 h[++idx] = '</th>';
			 }
		 }
		 h[++idx] = '<th>Action</th>';
		 h[++idx] = '</tr>';
		 h[++idx] = '</thead>';
		 
		 // Add body
		 h[++idx] = '<tbody>';
		 for(j = 0; j < managed.length; j++) {
			 record = managed[j];
			 h[++idx] = '<tr>';
			 for(i = 0; i < columns.length; i++) {
				 headItem = columns[i];
				
				 if(headItem.include) {
					 h[++idx] = '<td>';
					 if(headItem.readonly || !headItem.inline) {
						 h[++idx] = record[headItem.humanName];
					 } else {
						 h[++idx] = addEditableColumnMarkup(headItem, record[headItem.humanName], i);
					 }
					 h[++idx] = '</td>';
				 }
					 
			 }
			 h[++idx] = '<td><a data-toggle="modal" href="#editRecord" data-index="';
			 h[++idx] = j;
			 h[++idx] = '"><i class="fa fa-check text-navy"></i></a></td>';
			 h[++idx] = '</tr>';
			 
		 }
		 h[++idx] = '</tbody>';
		 
		 $table.removeClass("footable-loaded").empty().append(h.join(''));	
		 $('.footable').footable();
	 }
	 
	/*
	 * Add the markup for an editable column
	 */
	function addEditableColumnMarkup(column, value, itemIndex, first) {
		var h = [],
			idx = -1;
		
		if(column.type === "text") {
			h[++idx] = ' <input type="text" class="form-control editable" value="';
			h[++idx] = value;
			h[++idx] = '" data-item="';
			h[++idx] = itemIndex;
			if(first) {
				h[++idx] = '" autofocus/>';
			} else {
				h[++idx] = '"/>';
			}
		} else if(column.type === "date") {
			 h[++idx] = '<div class="input-group date" data-container="body">';
             h[++idx] = '<input type="text" class="form-control editable" data-date-format="YYYY-MM-DD" value="';
             h[++idx] = value;
             h[++idx] = '" data-item="';
             h[++idx] = itemIndex;
             if(first) {
 				h[++idx] = '" autofocus/>';
 			} else {
 				h[++idx] = '"/>';
 			}
             h[++idx] = '<span class="input-group-addon"><span class="glyphicon glyphicon-calendar"></span></span>';
             h[++idx] = '</div>';
		} else {
			h[++idx] = value;
		}
		
		return h.join('');
	} 
	
	/*
	 * Get surveys and update the survey lists on this page
	 *  This is a different function from the common loadSurveys function as processing differs depending on whether there is tracking
	 *   applied to the survey
	 */	
	 function loadManagedSurveys(projectId, callback) {
	 	
	 	var url="/surveyKPI/managed/surveys/" + projectId,
	 		$elemNonTracking = $('.nonTrackingSurveys'),
	 		$elemTracking = $('#surveyTable');

		gIsManageId = undefined;
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
	 				
	 				gManagedForms = data;
	 				
	 				hT[++idxT] = '<table class="table">';
	 				hT[++idxT] = '<thead>';
	 				hT[++idxT] = '<tr>';
	 				hT[++idxT] = '<th>' + localise.set["c_form"], + '</th>';
	 				hT[++idxT] = '<th>' + localise.set["mf_of"] + '</th>';
	 				hT[++idxT] = '<th></th>';
	 				hT[++idxT] = '</tr>';
	 				hT[++idxT] = '</thead>';
	 				hT[++idxT] = '<tbody class="table-striped">';
	 				
	 				
	 				for(i = 0; i < data.length; i++) {
	 					item = data[i];
	 					if(!item.managed) {
	 						hNT[++idxNT] = '<option value="';
	 						hNT[++idxNT] = item.sId;
	 						hNT[++idxNT] = '">';
	 						hNT[++idxNT] = item.surveyName;
	 						hNT[++idxNT] = '</option>';
	 					} else {
	 						hT[++idxT] = '<tr>';
	 							hT[++idxT] = '<td>';
	 							hT[++idxT] = item.surveyName;
	 							hT[++idxT] = '</td>';
	 							hT[++idxT] = '<td>';
	 							hT[++idxT] = item.oversightName;
	 							hT[++idxT] = '</td>';
	 							
	 							// actions
	 			 				hT[++idxT] = '<td>';
	 			 				
	 			 				hT[++idxT] = '<button type="button" data-idx="';
	 			 				hT[++idxT] = i;
	 			 				hT[++idxT] = '" class="btn btn-default btn-sm edit_link btn-info">';
	 			 				hT[++idxT] = '<span class="glyphicon glyphicon-edit" aria-hidden="true"></span></button>';
	 			 				
	 			 				hT[++idxT] = '</td>';
	 			 				// end actions
	 						hT[++idxT] = '</tr>';
	 					}
	 					
	 					if(i == 0) {
	 						if(globals.gCurrentSurvey <= 0) {
	 		 					globals.gCurrentSurvey = item.id;
	 		 					saveCurrentProject(-1, globals.gCurrentSurvey);
	 		 				}
	 					}
	 					if(item.managed_id > 0 && item.id === globals.gCurrentSurvey) {
	 						gManageId = item.managed_id;
	 					}
	 				}

	 				hT[++idxT] = '</tbody>';
	 				hT[++idxT] = '</table>';
	 				
	 				$elemNonTracking.empty().html(hNT.join(''));
	 				$elemTracking.empty().html(hT.join(''));
	 				
	 				$(".edit_link", $elemTracking).click(function(){
	 					var idx = $(this).data("idx");
	 					editManagedForm(idx);
	 				});
	 				
	 				if(typeof callback == "function") {
	 					callback();
	 				}
	 			},
	 			error: function(xhr, textStatus, err) {
	 				
	 				removeHourglass();
	 				if(xhr.readyState == 0 || xhr.status == 0) {
	 		              return;  // Not an error
	 				} else {
	 					alert("Error: Failed to get list of surveys: " + err);
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
	  * Edit the link between a form and an oversight form
	  */
	 function editManagedForm(idx) {
		 $('#changeOversightForm').val(gManagedForms[idx].managedId);
		 gSelectedSurvey = gManagedForms[idx].sId;
		 $('#changeManagedSurvey').modal("show");
	 }
	 
	 /*
	  * Get the columns for a survey
	  */
	 function getSurveyConfig(sId, manageId) {
		 
		 if(!cache.surveyConfig[sId]) {
			 var url = '/surveyKPI/managed/config/' + sId + "/" + manageId;
			 
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
		 } else {
			 if(cache.managedData[sId]) {
				 showManagedData(sId);
			 }
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
				  cache: false,
				  contentType: "application/json",
				  url: "/surveyKPI/managed/add",
				  data: { settings: saveString },
				  success: function(data, status) {
					  removeHourglass();
					  refreshData();
				  }, error: function(data, status) {
					  removeHourglass();
					  alert(localise.set["msg_err_cr"]);
				  }
			});
	 }

});

