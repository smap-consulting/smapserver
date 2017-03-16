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
 * Purpose: Allow the user to select a web form in order to complete a survey
 */
var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

var gDelSig = false;

requirejs.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	wfapp: '../app/webform',
    	build: '../../build/js',
    	jquery: '../../../../js/libs/jquery-2.1.1',
       	lang_location: '../'
    },
    shim: {

    }
});

require([
         'jquery',
         'wfapp/gui',
         'wfapp/store',
         'wfapp/file-manager',
         'wfapp/controller-webform',
         'wfapp/fastclick',
         'enketo-core'
         ], function($, gui, cache, recordStore, fileStore, controller, FastClick, enketo) {

	$(document).ready(function() {
	        	
		window.enketo = controller; 		// Make controller global so it can be called by cordova app
	            
		if(typeof surveyData !== "undefined") {
			controller.init( 'form.or:eq(0)', {
				recordStore: recordStore,
				fileStore: fileStore,
				submitInterval: 300 * 1000
			} );
		}
	            
	} );
            

} );
