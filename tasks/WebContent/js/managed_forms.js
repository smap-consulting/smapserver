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
    	icheck: '../../../../js/libs/wb/plugins/iCheck/icheck.min',
    	inspinia: '../../../../js/libs/wb/inspinia',
    	metismenu: '../../../../js/libs/wb/plugins/metisMenu/jquery.metisMenu',
    	slimscroll: '../../../../js/libs/wb/plugins/slimscroll/jquery.slimscroll.min',
    	pace: '../../../../js/libs/wb/plugins/pace/pace.min',
    	crf: '../../../../js/libs/commonReportFunctions',
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
    	'app/common_mgmt': ['jquery']
	
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
         'datetimepicker',
         'icheck',
         'crf',
         'app/common_mgmt'
         
         ], function($, 
        		 bootstrap, 
        		 common, 
        		 localise, 
        		 globals) {

	
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
		getLoggedInUser(projectChanged, false, true, undefined, false, dont_get_current_survey);

		enableUserProfileBS();										// Enable user profile button	
		
		// Set change function on projects
		$('#project_name').change(function() {
			projectChanged();
	 	 });
		
		// Set change function on survey
		$('#survey_name').change(function() {
			gTasks.gSelectedSurveyIndex = $(this).val();
			surveyChanged();
		});
		
		/*
		 * Set up dialog to edit a record
		 */
		$('#editRecord').on('show.bs.modal', function (event) {
			var index = $(event.relatedTarget).data("index"),
				record = gTasks.cache.managedData[globals.gCurrentSurvey][index],
				config = gTasks.cache.surveyConfig[globals.gCurrentSurvey],
				$editForm = $('#editRecordForm'),
				$surveyForm = $('#surveyForm'),
				h = [],
				idx = -1,
				m = [],
				cnt = -1,
				i,
				configItem,
				first = true;
			
			gTasks.gCurrentIndex = index;
			gTasks.gPriKey = record["prikey"];
			
			// Clear the update array
			gTasks.gUpdate = [];
			$('#saveRecord').prop("disabled", true);
			
			for(i = 0; i < config.length; i++) {
				configItem = config[i];
				
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
					  url: "/surveyKPI/managed/update/" + globals.gCurrentSurvey + "/" + gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].managed_id,
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
		
		// Apply changes to the table columns that are shown
		$('#applyTableSort').click(function(){
			
			var tableColumns = [],
				tc,
				$this;
			
			$('input', '#tab-settings-content').each(function(){
				$this = $(this);
				tc = {};
				tc.name = $this.val();
				tc.hide = !$this.is(':checked');
				tc.include = true;
				tableColumns.push(tc);
			});
			
			 saveString = JSON.stringify(tableColumns);
			 
			 addHourglass();
			 $.ajax({
				 type: "POST",
					  dataType: 'text',
					  contentType: "application/json",
					  url: "/surveyKPI/managed/updatecols/" + globals.gCurrentSurvey,
					  data: { settings: saveString },
					  success: function(data, status) {
						  removeHourglass();
						  $('#right-sidebar').removeClass("sidebar-open");
						  refreshData();
					  }, error: function(data, status) {
						  removeHourglass();
						  alert(data.responseText);
					  }
				});
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
				getRelatedData(globals.gCurrentSurvey, masterRecord);
			}
		});
		
     });	 


});

