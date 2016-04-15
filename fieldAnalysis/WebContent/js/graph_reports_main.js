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
 * Entry point for report list page
 */

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

requirejs.config({
    baseUrl: '/fieldAnalysis/js/libs',
    waitSeconds: 0,
    paths: {
     	app: '../app',
     	main: '..',
    	i18n: '../../../../js/libs/i18n',
     	async: '../../../../js/libs/async',
    	jquery: '../../../../js/libs/jquery-2.1.1',
	    jquery_ui: [
	    	   '//ajax.googleapis.com/ajax/libs/jqueryui/1.10.3/jquery-ui.min',
	    	   '../../../../js/libs/jquery-ui-1.10.3.custom.min'
	    	   ],
    	modernizr: '../../../../js/libs/modernizr',
    	localise: '../../../../js/app/localise',
    	rmm: '../../../../js/libs/responsivemobilemenu',
    	crf: '../../../../js/libs/commonReportFunctions',
    	lang_location: '../../../../js'
    },
    shim: {
    	'rmm': ['jquery'],
    	'jquery_ui': ['jquery'],
    	'crf': ['jquery'],
    	'main/jqplot_main': ['jquery'],
    	'app/graph-functions': ['main/jqplot_main'],
    	'app/graph-reports': ['app/graph-functions']
    	
    	}
    });


require([
         'jquery',  
         'jquery_ui', 
         'rmm', 
         'crf', 
         'localise', 
         'main/jqplot_main',
         'app/graph-functions',
         'app/graph-reports'
         
         ], function($, jquery_ui, rmm, crf, localise) {
	
	window.jQuery.browser = {};
	(function () {
	    window.jQuery.browser.msie = false;
	    window.jQuery.browser.version = 0;
	    if (navigator.userAgent.match(/MSIE ([0-9]+)\./)) {
	        window.jQuery.browser.msie = true;
	        window.jQuery.browser.version = RegExp.$1;
	    }
	})();
	
	var data_source = $("#data_source").text();
	getData(data_source);
});
