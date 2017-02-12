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

along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

*/

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

"use strict";
require.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: 'jquery-2.1.1',
    	bootbox: 'bootbox.min',
    	toggle: 'bootstrap-toggle.min',
    	moment: 'moment-with-locales.min',
    	lang_location: '..'

    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery'],
        'bootstrap.file-input': ['bootstrap.min'],
    	'bootbox': ['bootstrap.min'],
       	'toggle': ['bootstrap.min']
        
    }
});

require([
         'jquery',
         'app/common', 
         'bootstrap.min', 
         'app/localise',
         'app/globals',
         'jquery.autosize.min',
         'bootstrap.file-input',
         'bootbox',
         'toggle',
         'moment'], 
		function(
				$, 
				common, 
				bootstrap, 
				lang, 
				globals, 
				jqas, 
				bsfi, 
				bootbox,
				toggle,
				moment) {



window.moment = moment;

'use strict';

$(document).ready(function() {
 
	$('#addQuery').click(function(){
		$('#add_query_popup').modal("show");
	});
	
	$('#addForm').click(function(){
		$('#add_form_popup').modal("show");
	});


	
});

});
