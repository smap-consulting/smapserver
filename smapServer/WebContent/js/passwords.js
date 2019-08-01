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

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

"use strict";
requirejs.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: 'jquery-2.1.1',
       	lang_location: '../'
    },
    shim: {
    	'bootstrap.min': ['jquery'],
    	'bootstrapValidator.min': ['bootstrap.min'],
    	'app/common': ['jquery']
    }
});

require([
         'jquery', 
         'bootstrap.min',
         'bootstrapValidator.min',
         'app/localise',
         'app/common'
         ], function($, bootstrap, bv, localise) {

	var gToken;
	
$(document).ready(function() {
	
	var i,
		params,
		pArray = [],
		param = [];
	
	localise.setlang();
	$('#forgottenPasswordEmail').attr('data-bv-emailaddress-message', localise.set["msg_inv_email"]);
	
	// Add the organisation to the title
	if(window.location.hostname.indexOf("smap") > 0) {
		$('#website_id').text("Smap");
	}
	
	// Get the authentication token if it has been passed in parameters
	params = location.search.substr(location.search.indexOf("?") + 1)
	pArray = params.split("&");
	for (i = 0; i < pArray.length; i++) {
		param = pArray[i].split("=");
		if ( param[0] === "token" ) {
			gToken = param[1];
		}
	}
	
    $('#emailForm').bootstrapValidator({
        feedbackIcons: {
            valid: 'glyphicon glyphicon-ok',
            invalid: 'glyphicon glyphicon-remove',
            validating: 'glyphicon glyphicon-refresh'
        }
    });
    
    $('#resetPassword').bootstrapValidator({
        feedbackIcons: {
            valid: 'glyphicon glyphicon-ok',
            invalid: 'glyphicon glyphicon-remove',
            validating: 'glyphicon glyphicon-refresh'
        },
        fields: {
            password: {
                validators: {
                    identical: {
                        field: 'confirmPassword',
                        message: 'The password and its confirm are not the same'
                    }
                }
            },
            confirmPassword: {
                validators: {
                    identical: {
                        field: 'password',
                        message: 'The password and its confirm are not the same'
                    }
                }
            }
        }
    });
    
    $('#resetPasswordSubmit').click(function(e){
    	e.preventDefault();
    	
    	var pd = {
    			onetime: gToken,
    			password: $('#passwordValue').val()
    		},
    		pdString;
    	
    	pdString = JSON.stringify(pd);
    	
    	
		addHourglass();
    	$.ajax({
			  type: "POST",
			  cache: false,
			  url: "/surveyKPI/onetimelogon",
			  data: { passwordDetails: pdString },
			  success: function(data, status) {
				  removeHourglass();
				  alert(localise.set["msg_pr"]);
				  window.location.href="/";

			  }, error: function(data, status) {
				  removeHourglass();
				  alert("Error: " + data.responseText); 
			  }
		});
    });
    
    $('#forgottenPasswordSubmit').click(function(e){
    	e.preventDefault();
    	
    	var email = $('#forgottenPasswordEmail').val();
    	
		addHourglass();
    	$.ajax({
			  type: "GET",
			  cache: false,
			  url: "/surveyKPI/onetimelogon/" + email,
			  success: function(data, status) {
				  removeHourglass();
				  alert(localise.set["msg_es"] + " " + email);
				  window.location.href="/";
			  }, error: function(data, status) {
				  removeHourglass();
				  var msg = data.responseText;
				  var idx1 = msg.indexOf('ApplicationException:');
				  var idx2 = msg.indexOf('</h1>');
				  if(idx1 > 0 && idx2 > idx1) {
				  	msg = msg.substring(idx1, idx2);
				  }
				  alert(localise.set["c_error"] + ": " + msg);
			  }
		});
	
    });
});




});



