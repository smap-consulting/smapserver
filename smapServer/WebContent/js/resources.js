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

requirejs.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    paths: {
    	app: '../app',
    	jquery: [
      	       '//ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min',
      	       '../../../../js/libs/jquery-1.8.3.min'
      	       ],
       	lang_location: '../'
    },
    shim: {
    	'app/common': ['jquery'],
    	'bootstrap.min': ['jquery']
    }
});

require([
         'jquery', 
         'bootstrap.min',
         'app/common', 
         'app/globals',
         'app/localise',
         'bootstrapfileinput'
         ], function($, bootstrap, common, globals, localise, bsfi) {

$(document).ready(function() {
	
	var url = '/surveyKPI/upload/media';
	localise.setlang();		// Localise HTML
	
	// Get the user details
	globals.gIsAdministrator = false;
	globals.gCurrentSurvey = undefined;
	
	getFilesFromServer(undefined, refreshMediaView);		// Get the organisational level media files
	
	// Set up the tabs
    $('#mediaTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		
		$('#mapPanel').hide();
		$('#mediaPanel').show();
    })
    $('#mapTab a').click(function (e) {
    	e.preventDefault();
    	$(this).tab('show');
    		  	  
		$('#mapPanel').show();
		$('#mediaPanel').hide();
    })
    
    $('.file-inputs').bootstrapFileInput();
    
	enableUserProfileBS();
});



});

