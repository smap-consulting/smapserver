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
    	jquery: '../../../../js/libs/jquery-1.8.3.min',
    	bootstrap: '../../../../js/libs/bootstrap.min',
    	bootbox: '../../../../js/libs/bootbox.min',
    	bootstrapfileinput: '../../../../js/libs/bootstrap.file-input',
    	common: '../../../../js/app/common',
    	globals: '../../../../js/app/globals',
    	crf: '../../../../js/libs/commonReportFunctions',
    	lang_location: '../../../../js'
    },
    shim: {
       	'bootstrap': ['jquery'],
       	'bootstrapfileinput': ['jquery'],
    	'bootbox': ['bootstrap'],
    	'common': ['jquery']
    	}
    });

require([
         'jquery', 
         'bootstrap', 
         'common', 
         'localise', 
         'globals',
         'bootbox',
         'app/userManagement',
         'bootstrapfileinput'
         
         ], function($, bootstrap, common, localise, globals) {

});

