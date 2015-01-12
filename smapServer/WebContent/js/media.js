var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

var gUrl;			// url to submit to
var gBaseUrl = '/surveyKPI/upload/media';
var gSId;

"use strict";
require.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min',
    	lang_location: '..'
    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'bootstrap.file-input': ['bootstrap.min'],
    }
});

require([
         'jquery',
         'app/common', 
         'bootstrap.min',
         'app/localise',
         'app/globals',
         'bootstrap.file-input'
        ], 
        function($,common, bootstrap, localise, globals) {
	
    'use strict';
    
$(document).ready(function() {

    	
	localise.setlang();		// Localise HTML
	
	// Get the user details
	globals.gIsAdministrator = false;
	getLoggedInUser(gotLoggedInUser, false, false, undefined, false, false);
	getFilesFromServer();		// Get the organisational level media files
	
	gSId = undefined;
	gUrl = gBaseUrl;
	$('#survey_id').val("");				// clear the survey id in the forms hidden field
    $('#surveyLevelTab a').click(function (e) {
    	  e.preventDefault();
    	  $(this).tab('show');
    	  gUrl = gBaseUrl + '?sId=' + gSId;
    	  $('#survey_id').val(gSId);			// Set the survey id in the forms hidden field
    	  
    	  $('#orgPanel').hide();
    	  $('#surveyPanel').show();
    })
    
    $('#orgLevelTab a').click(function (e) {
    	  e.preventDefault();
    	  $(this).tab('show');
    	  gUrl = gBaseUrl;
    	  $('#survey_id').val("");				// clear the survey id in the forms hidden field
    	  
    	  $('#orgPanel').show();
    	  $('#surveyPanel').hide();
    })
    	
    $('.file-inputs').bootstrapFileInput();
    
    /*
     * Submit the files
     */
    $('#submitFiles').click( function() {
    	console.log("Serialize");
    	var sId = $('#survey_id').val();
    	var f = document.forms.namedItem("fileupload");
    	var formData = new FormData(f);
    	

        $.ajax({
            url: gUrl,
            type: 'POST',
            xhr: function () {
            	var myXhr = $.ajaxSettings.xhr();
        		if(myXhr.upload){ 
        			myXhr.upload.addEventListener('progress', progressFn, false); 
        		}
        		return myXhr;
            },
            data: formData,
            cache: false,
            contentType: false,
            processData:false,
            success: function(data) {
            	
            	var surveyId = sId;
            	console.log("Success");
            	console.log(data);
            	refreshView(data, surveyId);
            	$('#upload_msg').removeClass('alert-anger').addClass('alert-success').html("Upload Success");
            	document.forms.namedItem("fileupload").reset();
            	
            },
            error: function(xhr, textStatus, err) {
            	
  				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					$('#upload_msg').removeClass('alert-success').addClass('alert-danger').html("Upload failed: " + err);

				}
            }
        });
    });
});

function progressFn(e) {
	if(e.lengthComputable){
        var w = (100.0 * e.loaded) / e.total;
        $('.progress-bar').css('width', w+'%').attr('aria-valuenow', w); 
    }
}

function gotLoggedInUser() {
	getSurveyDetails(gotSurveyDetails);
}

function gotSurveyDetails() {
	if(globals.gCurrentSurvey) {
		$('#surveyLevelTab').removeClass("disabled");
		getFilesFromServer(globals.gCurrentSurvey);
	}
}

function getFilesFromServer(sId) {
	
	var url = gBaseUrl;
	if(sId) {
		gSId = sId;
		url += '?sId=' + sId;
	}
	console.log("Getting media: " + url);
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			
			var surveyId = sId;
			console.log(data);
			refreshView(data, surveyId);

		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				$('#upload_msg').removeClass('alert-success').addClass('alert-danger').html("Error: " + err);
			}
		}
	});	
	
}

function refreshView(data, sId) {
	
	var i,
		survey = globals.model.survey,
		$element,
		h = [],
		idx = -1,
		files;
	
	if(survey && sId) {
		// Set the display name
		$('#formName').html(survey.displayName);
		$('#survey_id').val(sId);
		gSId = sId;
	}
	
	if(data) {
		files = data.files;
		
		if(sId) {
			$element = $('#filesSurvey');
		} else {
			$element = $('#filesOrg');
		}
		
		for(i = 0; i < files.length; i++){
			h[++idx] = '<tr>';
			
			h[++idx] = '<td>';
				h[++idx] = '<span class="preview>';
				h[++idx] = '<a href="';
				h[++idx] = files[i].url;
				h[++idx] = '"><img src="';
				h[++idx] = files[i].thumbnailUrl;
				h[++idx] = '" alt="';
				h[++idx] = files[i].name;
				h[++idx] = '"></a>';
			h[++idx] = '</td>';
			h[++idx] = '<td>';
				h[++idx] = '<p>';
				h[++idx] = files[i].name;
				h[++idx] = '</p>';
			h[++idx] = '</td>';
			h[++idx] = '<td>';
				h[++idx] = '<p>';
				h[++idx] = files[i].size;
				h[++idx] = '</p>';
			h[++idx] = '</td>';
			h[++idx] = '<td>';
				h[++idx] = '<button class="media_del btn btn-danger" data-url="';
				h[++idx] = files[i].deleteUrl;
				h[++idx] = '">';
				h[++idx] = '<span class="glyphicon glyphicon-trash" aria-hidden="true"></span>'
				h[++idx] = ' Delete';
				h[++idx] = '</button>';
			h[++idx] = '</td>';
			
			
			h[++idx] = '</tr>';
		}
		
		$element.html(h.join(""));
		$('.media_del', $element).click(function () {
			delete_media($(this).data('url'));
		});
	
	}	
}

function delete_media(url) {
	addHourglass();
	$.ajax({
		url: url,
		type: 'DELETE',
		cache: false,
		success: function(data) {
			removeHourglass();
			console.log(data);
			
			var address = url;
			if(url.indexOf('organisation') > 0) {
				refreshView(data);
			} else {
				refreshView(data, gSId);
			}
	
		},
		error: function(xhr, textStatus, err) {
			removeHourglass();
			if(xhr.readyState == 0 || xhr.status == 0) {
	              return;  // Not an error
			} else {
				$('#upload_msg').removeClass('alert-success').addClass('alert-danger').html("Error: " + err);
			}
		}
	});	
}

});
	    	
	       

