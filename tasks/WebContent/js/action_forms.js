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

		var $editForm = $('#editRecordForm'),
			$surveyForm = $('#surveyForm');
		
		showEditRecordForm(gRecord[0], gSurveyConfig.columns, $editForm, $surveyForm);
		
		
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
		

		
     });	 
	 



});

