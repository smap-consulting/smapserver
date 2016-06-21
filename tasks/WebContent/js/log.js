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
    	moment: '../../../../js/libs/moment-with-locales.min',
    	datetimepicker: '../../../../js/libs/bootstrap-datetimepicker.min',
    	common: '../../../../js/app/common',
    	globals: '../../../../js/app/globals',
    	bootstrap: '../../../../js/libs/bootstrap.min',
    	bootbox: '../../../../js/libs/bootbox.min',
    	crf: '../../../../js/libs/commonReportFunctions',
    	lang_location: '../../../../js',
    	file_input: '../../../../js/libs/bootstrap.file-input',
    	mapbox_app: '../../../../js/app/mapbox_app',
    	
    	mapbox: '../../../../js/libs/mapbox/js/mapbox',
    	inspinia: '../../../../js/libs/wb/inspinia',
    	metismenu: '../../../../js/libs/wb/metisMenu/jquery.metisMenu',
    	slimscroll: '../../../../js/libs/wb/slimscroll/jquery.slimscroll.min',
    	pace: '../../../../js/libs/wb/pace/pace.min',
    	peity: '../../../../js/libs/wb/peity/jquery.peity.min',
    	icheck: '../../../../js/libs/wb/plugins/iCheck/icheck.min',
    	calendar: '../../../../js/libs/wb/plugins/fullcalendar/fullcalendar.min',
    	jquery_ui: '../../../../js/libs/wb/jquery-ui.custom.min'
    },
    shim: {

    	'common': ['jquery'],
    	'datetimepicker': ['moment'],
    	'bootstrap': ['jquery'],
    	'bootbox': ['bootstrap'],
    	'crf': ['jquery'],
    	'file_input': ['jquery'],
    	'mapbox_app' : ['jquery', 'mapbox'],
    	'mapbox': {
            exports: 'L'
        },
        
    	'inspinia': ['jquery'],
    	'metismenu': ['jquery'],
    	'slimscroll': ['jquery'],
    	'peity': ['jquery'],
    	'icheck': ['jquery'],
    	'calendar': ['jquery_ui'],
    	'jquery_ui': ['jquery']
	
    	}
    });

require([
         'jquery',
         'bootstrap',
         'common', 
         'localise', 
         'globals',
         'bootbox',
         'crf',
         'moment',
         'datetimepicker',
         'file_input',     
         'inspinia',
         'metismenu',
         'slimscroll',
         'pace',
         'peity',
         'icheck',
         'jquery_ui'
         
         ], function($, 
        		 bootstrap, 
        		 common, 
        		 localise, 
        		 globals, 
        		 bootbox, 
        		 crf, 
        		 moment) {

});

