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
 * Entry point for report map page
 */

requirejs.config({
    baseUrl: '/fieldAnalysis/js/libs',
    waitSeconds: 0,
    paths: {
     	app: '../app',
    	i18n: '../../../../js/libs/i18n',
     	async: '../../../../js/libs/async',
    	jquery: '../../../../js/libs/jquery-2.1.1',
    	jquery_ui: [
    	       '//ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min',
    		   '../../../../js/libs/jquery-ui-1.10.3.custom.min'
    		   ],
    	modernizr: '../../../../js/libs/modernizr',
    	localise: '../../../../js/app/localise',
    	common: '../../../../js/app/common',
    	rmm: '../../../../js/libs/responsivemobilemenu',
    	crf: '../../../../js/libs/commonReportFunctions',
    	lang_location: '../../../../js'
    },
    shim: {
    	'rmm': ['jquery'],
    	'jquery_ui': 'jquery',
    	'app/map-functions': ['jquery_ui'],
       	'common': ['jquery'], 	
    	'app/map-reports': ['jquery'],
    	}
    });


require([
         'jquery',  
         'modernizr', 
         'rmm', 
         'common',
         'crf', 
         'localise', 
         'app/map-functions',
         'app/map-reports'
         
         ], function($, modernizr, rmm, common, crf, localise, map_functions, map_reports) {
	
	$(document).ready(function() {		// Wait for Openlayers
		var data_source = $("#data_source").text();
		getData(data_source);
	});
});
