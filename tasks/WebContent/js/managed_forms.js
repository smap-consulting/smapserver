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
    	toggle: 'bootstrap-toggle.min',
    	lang_location: '../../../../js',
    	file_input: '../../../../js/libs/bootstrap.file-input',
    	mapbox_app: '../../../../js/app/mapbox_app',
    	datetimepicker: '../../../../js/libs/bootstrap-datetimepicker.min',
       	datatables: '../../../../js/libs/DataTables/datatables',
    	'datatables.net': '../../../../js/libs/DataTables/DataTables/js/datatables.net',
    	'datatables.net-bs': '../../../../js/libs/DataTables/DataTables/js/datatables.bootstrap',
    	'datatables.select': '../../../../js/libs/DataTables/Select/js/dataTables.select.min',
    	icheck: '../../../../js/libs/wb/plugins/iCheck/icheck.min',
    	inspinia: '../../../../js/libs/wb/inspinia',
    	metismenu: '../../../../js/libs/wb/plugins/metisMenu/jquery.metisMenu',
    	slimscroll: '../../../../js/libs/wb/plugins/slimscroll/jquery.slimscroll.min',
    	pace: '../../../../js/libs/wb/plugins/pace/pace.min',
    	crf: '../../../../js/libs/commonReportFunctions',
    	qrcode: '../../../../js/libs/jquery-qrcode-0.14.0.min'
    	
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
    	'icheck': ['jquery'],
    	'slimscroll': ['jquery'],
    	'crf': ['jquery'],
       	'datatables': ['jquery', 'bootstrap'],
    	'app/common_mgmt': ['jquery'],
    	'qrcode': ['jquery'],
       	'toggle': ['bootstrap.min']
	
    	}
    });

require([
         'jquery',
         'bootstrap',
         'common', 
         'localise', 
         'globals',
         'datatables.net-bs',
         'datatables.select',
         'inspinia',
         'metismenu',
         'slimscroll',
         'pace',
         'datetimepicker',
         'icheck',
         'crf',
         'app/common_mgmt',
         'qrcode',
         'toggle'
         
         ], function($, 
        		 bootstrap, 
        		 common, 
        		 localise, 
        		 globals,
        		 datatables) {

	
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

		enableUserProfileBS();										// Enable user profile button	
		
		// Set change function on projects
		$('#project_name').change(function() {
			projectChanged();
	 	 });
		
		// Set change function on survey
		$('#survey_name').change(function() {
			gTasks.gSelectedSurveyIndex = $(this).val();
			globals.gCurrentSurvey = gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].id;
			surveyChanged();
		});
		
		/*
		 * Set up dialog to edit a record
		 */
		$('#editRecord').on('show.bs.modal', function (event) {
			var // index = $(event.relatedTarget).data("index"),
				//record = gTasks.cache.managedData[globals.gCurrentSurvey][index],
				record = gTasks.gSelectedRecord,
				columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns,
				$editForm = $('#editRecordForm'),
				$surveyForm = $('#surveyForm'),
				h = [],
				idx = -1,
				m = [],
				cnt = -1,
				i,
				configItem,
				first = true;
			
			//gTasks.gCurrentIndex = index;
			gTasks.gPriKey = record["prikey"];
			
			// Clear the update array
			gTasks.gUpdate = [];
			$('#saveRecord').prop("disabled", true);
			
			for(i = 0; i < columns.length; i++) {
				configItem = columns[i];
				
				if(configItem.mgmt) {
					h[++idx] = getEditMarkup(configItem, i, first, record);
				} else {
					m[++cnt] = getEditMarkup(configItem, i, first, record);
				}
				if(!configItem.readonly) {
					first = false;
				}
			}
			
			$editForm.html(h.join(''));
			$surveyForm.html(m.join(''));
			
			// Set up date fields
			$editForm.find('.date').datetimepicker({
				locale: gUserLocale || 'en',
				useCurrent: false,
				showTodayButton: true
			});
			
			// Respond to changes in the data by creating an update object
			$editForm.find('.form-control').keyup(function() {
				dataChanged($(this));
			});
			$editForm.find('.date').on("dp.change", function() {
				dataChanged($(this).find('input'));
			});
			$editForm.find('select').change(function() {
				dataChanged($(this));
			});
			
			// Set focus to first editable data item
			$editForm.find('[autofocus]').focus();
			
			
		});
		
		$('#saveRecord').click(function(){
			 saveString = JSON.stringify(gTasks.gUpdate);
			 addHourglass();
			 $.ajax({
				 type: "POST",
					  dataType: 'text',
					  contentType: "application/json",
					  cache: false,
					  url: "/surveyKPI/managed/update/" + globals.gCurrentSurvey + "/" + gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].managed_id,
					  data: { settings: saveString },
					  success: function(data, status) {
						  removeHourglass();
						  globals.gMainTable.ajax.reload();
					  }, error: function(data, status) {
						  removeHourglass();
						  alert(data.responseText);
					  }
				});
		});
		
		// Save changes to the table columns that are shown
		$('#applyColumns').click(function(){
			
			var 
				config = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex],
				$this;
			
			$('input', '#tab-columns-content').each(function(index){
				$this = $(this);
				config.columns[index + 1].hide = !$this.is(':checked');		// Ignore prikey
				
			});
			
			updateVisibleColumns(config.columns);
			saveConfig(config);
	
		});
		
		// Save changes to the barcodes that are shown
		$('#applyBarcodes').click(function(){
			
			var 
				config = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex],
				$this;
			
			$('input', '#tab-barcode-content').each(function(index){
				$this = $(this);
				config.columns[index + 1].barcode = $this.is(':checked');		// Ignore prikey
				
			});
			globals.gMainTable.ajax.reload(); // redraw
			saveConfig(config);
	
		});
		
		// Refresh menu
		$('#m_refresh').click(function (){
			refreshData();
		});
		
		/*
		 * Set up the action menu functions
		 */
		$('#getRelated').click( function () {
			
			var masterRecord = getSelectedRecord();
			
			if(typeof masterRecord != "undefined") {
				// 1. Hide results other than this primary result
				showManagedData(globals.gCurrentSurvey, '#trackingTable', masterRecord);
				
				// 2. Get related surveys and show it
				getRelatedList(globals.gCurrentSurvey, masterRecord);
			}
		});
		
     });	 
	 
	 /*
	  * Respond to a request to generate a file
	  */
	 $('.genfile').click( function() {
		 var url = "/surveyKPI/tables/generate",
		 	$this = $(this),
		 	filename,
		 	mime,
			data,
		 	manageId;
		 
		 
		 data = getTableData(globals.gMainTable, 
				 gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns)
				 
		 if($this.hasClass("xls")) {
			 filename = "displayName" + ".xlsx"
			 mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
			 format = "xlsx";
		 } else {
			 filename = "displayName" + ".pdf"
			 mime= "application/pdf";
			 format = "pdf";
		 }
		 
		 if(isBrowseResults) {
			 managedId = 0;
		 } else {
			 managedId = gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].managed_id;
		 }
		 generateFile(url, filename, format, mime, data, globals.gCurrentSurvey, managedId); 
	 });
	 

	 // Respond to duplicate reports menu
	 if(isDuplicates) {
		 $('#duplicateSearch').click(function(){
			 showDuplicateData(globals.gCurrentSurvey, '#trackingTable');
		 });
	 }


});

