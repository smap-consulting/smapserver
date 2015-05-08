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
require.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min',
    	bootbox: 'bootbox.min',
    	lang_location: '..'

    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery'],
        'jquery-drag-ui.min': ['jquery'],
        'bootstrap.file-input': ['bootstrap.min'],
    	'bootbox': ['bootstrap.min']
        
    }
});

require([
         'jquery',
         'app/common', 
         'bootstrap.min', 
         'app/localise',
         'app/globals',
         'jquery-drag-ui.min',
         'jquery.autosize.min',
         'bootstrap.file-input',
         'bootbox',
         'app/question',
         'app/editorMarkup',
         'app/changeset'], 
		function(
				$, 
				common, 
				bootstrap, 
				lang, 
				globals, 
				jquery_ui, 
				jqas, 
				bsfi, 
				bootbox,
				question,
				markup,
				changeset) {


var	gMode = "survey",
	gTempQuestions = [],
	$gCurrentRow,			// Currently selected row
	gCollapsedPanels = [];

// Media globals
var gUrl,			// url to submit to
	gBaseUrl = '/surveyKPI/upload/media';

// Media Modal Parameters
var gNewVal,
	gSelFormId,
	gSelId,
	gOptionList,
	gQname,
	gElement,
	gNewVal,
	gIsSurveyLevel;


'use strict';

$(document).ready(function() {
  
	var i,
		params,
		pArray = [],
		param = [],
		dont_get_current_survey = true;
	
	window.bootbox = bootbox;
	
	localise.setlang();		// Localise HTML
	
	// Get the parameters and start editing a survey if one was passed as a parameter
	params = location.search.substr(location.search.indexOf("?") + 1)
	pArray = params.split("&");
	for (i = 0; i < pArray.length; i++) {
		param = pArray[i].split("=");
		if ( param[0] === "id" ) {
			globals.gCurrentSurvey = param[1];
			saveCurrentProject(-1, globals.gCurrentSurvey);	// Save the current survey id
		}
	}
	
	// Get the user details
	globals.gIsAdministrator = false;
	if(globals.gCurrentSurvey > 0) {
		dont_get_current_survey = true;		// The current survey was passed in the parameters
	} else {
		dont_get_current_survey = false;		// The current survey was not passed in the parameters
	}
	getLoggedInUser(getSurveyList, false, true, undefined, false, dont_get_current_survey);
	getFilesFromServer();		// Get the organisational level media files

	/*
	 * Refresh the view when the selected property changes
	 */
	$('#selProperty').change(function() {
		var i;
	
		refreshView();
		
	});
	
	// Add menu functions
	$('#m_open').off().click(function() {	// Open an existing form
		if(globals.model.changes.length > 0) {
			if (confirm("You have unsaved changes are you sure you want to leave?")) {
				$('#openFormModal').modal('show');
			}
		} else {
			$('#openFormModal').modal('show');
		}
		
	});
	$('.m_save_survey').off().click(function() {	// Save a survey to the server
		changeset.save(surveyListDone);
	});

	// Add menu functions
	$('#m_media').off().click(function() {	// MEDIA
		// Set up media dialog to manage loading and deleting of media
		$('.mediaManage').show();
		$('.mediaSelect').hide();
		$('#mediaModalLabel').html("Manage Media Files");
		$('#mediaModal table').off();
		$('#surveyPanel, #orgPanel').find('tr').removeClass('success');
		
		// Make sure all types of media are shown
		$('tr.image, tr.audio, tr.video,tr.unknown').show();
		// Close any drop downmenus
		$('.dropdown-toggle').parent().removeClass("open");
		$('.navbar-collapse').removeClass("in");
		
		// Set the default destination 
		if($('#orgLevelTab').hasClass("active")) {
			gUrl = gBaseUrl;
			$('#survey_id').val("");				// clear the survey id in the forms hidden field
			gIsSurveyLevel = false;
		} else {
			gUrl = gBaseUrl + '?sId=' + gSId;
    		$('#survey_id').val(gSId);			// Set the survey id in the forms hidden field
    		gIsSurveyLevel = true;
		}
		$('#mediaModal').modal('show');

	});
	
	$('#m_settings').off().click(function() {	// Show the settings dialog
		
		// Close any drop downmenus
		$('.dropdown-toggle').parent().removeClass("open");
		$('.navbar-collapse').removeClass("in");
		
		$('#settingsModal').modal('show');
	});


	$('#save_settings').off().click(function() {	// Save settings to Smap
		globals.model.save_settings();
	});
	
	// Add responses to events
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
		getSurveyList();
 	 });
	
	$('.language_list').off().change(function() {
		globals.gLanguage = $(this).val();
		refreshView();
 	 });
	
	// Check for changes in settings
	$('#set_survey_name').keyup(function(){
		globals.model.settingsChange();
	});
	$('#set_project_name').change(function() {
		saveCurrentProject($('#set_project_name option:selected').val(), globals.gCurrentSurvey);	// Save the current project id
		globals.model.settingsChange();
	});
	$('#set_default_language').change(function() {
		globals.model.settingsChange();
	});
	$('#task_file').change(function() {
		globals.model.settingsChange();
	});
	$('#addPdfTemplate').off().click(function() {
		globals.model.settingsAddPdfClicked();
	});

	// Check for selection of the label indicating successful updates and the one indicating failed
	$('#successLabel').off().click(function() {
		alert("success");
	});
	// Check for selection of the label indicating successful updates and the one indicating failed
	$('#failedLabel').off().click(function() {
		alert("failed");
	});
	
	
	enableUserProfileBS();
	
	// From: http://stackoverflow.com/questions/20247945/bootstrap-3-navbar-dynamic-collapse
	function autocollapse() {
	    var $navbar = $('.navbar');
	    $navbar.removeClass('collapsed'); 
	    if($navbar.innerHeight() > 60) // check if we've got 2 lines
	        $navbar.addClass('collapsed'); // force collapse mode
	}
	$(document).on('ready', autocollapse);
	$(window).on('resize', autocollapse);
	
	/*
	 * Add check prior to the user leaving the screen
	 */
	window.onbeforeunload = function() {
		if(globals.model.changes.length > 0) {
			return "You have unsaved changes are you sure you want to leave?";
		}
	};

	/*
	 * Set up media files
	 */
    $('#surveyLevelTab a').click(function (e) {
    	if(gSId) {
    		e.preventDefault();
    		$(this).tab('show');
    		gUrl = gBaseUrl + '?sId=' + gSId;
    		$('#survey_id').val(gSId);			// Set the survey id in the forms hidden field
    		gIsSurveyLevel = true;
    		
    		$('#orgPanel').hide();
    		$('#surveyPanel').show();
    	}
    })
    
    $('#orgLevelTab a').click(function (e) {
    	  e.preventDefault();
    	  $(this).tab('show');
    	  gUrl = gBaseUrl;
    	  $('#survey_id').val("");				// clear the survey id in the forms hidden field
  		  gIsSurveyLevel = false;
    	  
    	  $('#orgPanel').show();
    	  $('#surveyPanel').hide();
    })
    	
    $('.file-inputs').bootstrapFileInput();
    
    /*
     * Submit the files
     */
    $('#submitFiles').click( function() {
    	var sId = $('#survey_id').val();
    	var f = document.forms.namedItem("fileupload");
    	var formData = new FormData(f);
    	
    	addHourglass();
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
    			removeHourglass();
            	var surveyId = sId;
            	refreshMediaView(data, surveyId);
            	$('#upload_msg').removeClass('alert-danger').addClass('alert-success').html("Upload Success");
            	document.forms.namedItem("fileupload").reset();
            	
            },
            error: function(xhr, textStatus, err) {
    			removeHourglass();
  				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					$('#upload_msg').removeClass('alert-success').addClass('alert-danger').html("Upload failed: " + err);

				}
            }
        });
    });
    
    /*
     * Open a new form
     */
	$('#get_form').off().click(function() {
		globals.gCurrentSurvey = $('#form_name option:selected').val();
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the new survey id
		globals.model.setHasChanges(0);		// Clear any existing changes from a previous form
		getSurveyDetails(surveyDetailsDone);
 	 });
	
    /*
     * Save a selected media file
     */
	$('#mediaSelectSave').click(function() {
		if(gNewVal) {
			if(gOptionList) {
				type = "option";
			} else {
				type = "question";
			}
			updateLabel(type, gSelFormId, gSelId, gOptionList, gElement, gNewVal, gQname, "media");
		}
	});
	
	$('#removeMedia').click(function() {

		if(gOptionList) {
			type = "option";
		} else {
			type = "question";
		}
		updateLabel(type, gSelFormId, gSelId, gOptionList, gElement, undefined, gQname, "media");
		
	});
	
	setUpQuestionTypeDialog();

	
});

//Set up question type dialog
function setUpQuestionTypeDialog() {
	var i,
		types = globals.model.qTypes,
		$elem = $('#typeModalButtonGrp'),
		h = [],
		idx = -1;
	
	for(i = 0; i < types.length; i++) {
		h[++idx] = '<button type="button" class="btn btn-primary">';
		h[++idx] = types[i].name;
		h[++idx] = '</button>';
	}	
	$elem.html(h.join(''));
}

function enableDragablePanels() {
	var panelList = $('#formList');

    panelList.sortable({
        // Only make the .panel-heading child elements support dragging.
        // Omit this to make the entire <li>...</li> draggable.
        handle: '.panel-heading', 
        update: function() {
            $('.panel', panelList).each(function(index, elem) {
                 var $listItem = $(elem),
                     newIndex = $listItem.index();

                 // Persist the new indices.
            });
        }
    });
}

function getSurveyList() {
	if(globals.gCurrentSurvey > 0) {
		loadSurveys(globals.gCurrentProject, undefined, false, false, surveyListDone);
	} else {
		loadSurveys(globals.gCurrentProject, undefined, false, false, undefined);
	}
}


function surveyListDone() {
	getSurveyDetails(surveyDetailsDone);
}

function surveyDetailsDone() {
	// Get survey level files
	if(globals.gCurrentSurvey) {
		$('#surveyLevelTab').removeClass("disabled");
		getFilesFromServer(globals.gCurrentSurvey);
	}
	
	// Update edit view
	updateSettingsData();
	refreshView();
}

function refreshView() {
	var selProperty = $('#selProperty').val();
	
	if(selProperty === "layout") {
		refreshLayout();
	} else {
		refreshForm();
	}
}

/*
 * Show the form on the screen
 */
function refreshForm() {
	
	var $context;
	
	$context = markup.refresh();
	respondToEvents($context);
	

}

function respondToEvents($context) {
	
	// Add tooltips
	$('.has_tt', $context).tooltip();
	
	// Respond to changes in the attribute that currently has focus
	$('.labelProp', $context).change(function(){

		var $this = $(this),
			prop = $this.data("prop"),
			$parent = $this.parent(),
			formIndex = $parent.data("fid"),
			itemIndex = $parent.data("id"),
			newVal = $this.val(),
			type,
			optionList = $parent.data("list_name"),
			qname = $parent.data("qname");
		
		if($parent.hasClass("option")) {
			type = "option";
		} else {
			type = "question";
		}

		updateLabel(type, formIndex, itemIndex, optionList, "text", newVal, qname, prop); // TODO Hint

	});
	
	// Selected a media property
	$('.mediaProp', $context).off().click(function(){
		
		var $this = $(this);
		mediaPropSelected($this);

	});
	
	// Add new question
	$('.add_question', $context).off().click(function() {
		var $this = $(this),
			qIndex = $(this).data("index"),
			locn = $(this).data("locn");	// Add before or after the element id referenced by qIdx
		
		question.add(qIndex, locn);
	});
	
	// Select types
	$('.question_type', $context).off().click(function() {
		var $this = $(this),
			qIndex = $(this).data("index");
		
		question.setType(qIndex);
	});
	
}


function mediaPropSelected($this) {
	var $parent = $this.closest('td'),
		$immedParent = $this.closest('div');
	
	// Set up media view
	gElement = $this.data("element");
	gSelFormId = $parent.data("fid");
	gSelId = $parent.data("id");
	gOptionList = $parent.data("list_name"); 
	gQname = $parent.data("qname"); 
	$gCurrentRow = $parent;
		
	$('.mediaManage').hide();						// MEDIA
	$('.mediaSelect').show();
	$('#mediaModalLabel').html("Select Media File");
	
	// Remove any current selections
	$('#surveyPanel, #orgPanel').find('tr').removeClass('success');
	
	// Only show relevant media
	$('tr.image, tr.audio, tr.video,tr.unknown').hide();
	$('tr.' + gElement).show();
	
	$('#mediaModal table').on('click', 'tbody tr', function(e) {
		var $sel = $(this);
		
		$('#surveyPanel, #orgPanel').find('tr').removeClass('success');	// Un mark any other selcted rows
	    $sel.addClass('success');
	 
	    gNewVal = $sel.find('.filename').text();		    
	   
	});
	
	// Set the status of the remove button
	$empty = $immedParent.find('.emptyMedia');
	if($empty.length > 0) {
		$('#removeMedia').addClass("disabled");
	} else {
		$('#removeMedia').removeClass("disabled");
	}
	
	// On double click save and exit
	$('#mediaModal table').on('dblclick', 'tbody tr', function(e) {
		var $sel = $(this);
		
	    gNewVal = $sel.find('.filename').text();		    
	    $('#mediaSelectSave').trigger("click");
	});
	
	// If the user clicks on "Add" save and exit
	
	$('.mediaAdd').on('click', function(e) {
		var $sel = $(this).closest('tr');
		
	    gNewVal = $sel.find('.filename').text();		    
	    $('#mediaSelectSave').trigger("click");
	});
	
	$('#mediaModal').modal('show');

}
/*
 * Update the list of languages and other settings data
 */
function updateSettingsData() {
	var i,
		languages = globals.model.survey.languages,
		key,
		h = [],
		idx = -1,
		defLangIdx = 0;
	
	for(i = 0; i < languages.length; i++) {
		h[++idx] = '<option value="';
		h[++idx] = i;
		h[++idx] = '">';
		h[++idx] = languages[i];
		h[++idx] = '</option>';
		
		if(globals.model.survey.def_lang === languages[i]) {
			defLangIdx = i;
		}
	}
	$('.language_list').html(h.join(""));
	$('.survey_name').val(globals.model.survey.displayName);
	$('.formName').html(globals.model.survey.displayName);
	$('#set_survey_ident').val(globals.model.survey.ident);
	$('#set_default_language').val(defLangIdx);
	$('.upload_file_msg').val(globals.model.survey.pdfTemplateName);
}


/*
 * Call this to update a label
 * Label processing is different than other attributes since it has to be applied to multiple languages
 * Parameters:
 * 	element:  text || hint || image || video || audio
 *  newVal: The new value for the label
 *  type: question || option
 */
function updateLabel(type, formIndex, itemIndex, optionList, element, newVal, qname, prop) {
	
	var $context,
		change;
	
	change = {
			changeType: "label",		// survey | form | language | question | option | (property | label | media) last three are types of property change
										// Also option_update which is not used by the editor
			action: "update",
			property: {
				qId: undefined,				// qId must be set to apply the change
				qType: undefined,			// Question type
				type: type,					// question or option
				name: undefined,			// name of the question or the option list
				propType: element,			// text or image or video or audio
				prop: prop,					// Property to be changed, for example: label or appearance
				languageName: undefined,	// Language Name
				allLanguages: false,		// Set true if all languages should be updated with a new label
				
				newVal: newVal,				// New value to be applied
				oldVal: undefined,			// Old value for this property
				key: undefined,				// or Translation the "text_id", For option updates the option "value"
				
				// Helper values temporary indexes to access the model which has values for the question or option to be updated
				qname: qname,					// Question name used when updating an option
				language: globals.gLanguage,	// Index into language array
				formIndex: formIndex,		// Index into the array of forms
				itemIndex: itemIndex,		// Index into the form or choice list (for choices)

				optionList: optionList,		// Name of the choice list (if this is an choice update)	
				isSurveyLevel: gIsSurveyLevel	// Set true for media if the media was added at the survey level rather than organisation
			}
	};
	
	$context = changeset.add(change);
	respondToEvents($context);				// Add events on to the altered html
	
}


function progressFn(e) {
	if(e.lengthComputable){
        var w = (100.0 * e.loaded) / e.total;
        $('.progress-bar').css('width', w+'%').attr('aria-valuenow', w); 
    }
}

function getFilesFromServer(sId) {
	
	var url = gBaseUrl;
	if(sId) {
		gSId = sId;
		url += '?sId=' + sId;
	}
	
	addHourglass();
	$.ajax({
		url: url,
		dataType: 'json',
		cache: false,
		success: function(data) {
			removeHourglass();
			
			var surveyId = sId;
			refreshMediaView(data, surveyId);

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



function delete_media(url) {
	addHourglass();
	$.ajax({
		url: url,
		type: 'DELETE',
		cache: false,
		success: function(data) {
			removeHourglass();
			
			var address = url;
			if(url.indexOf('organisation') > 0) {
				refreshMediaView(data);
			} else {
				refreshMediaView(data, gSId);
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

/*
 * Refresh the view of any attached media if the available media items has changed
 */
function refreshMediaView(data, sId) {
	
	var i,
		survey = globals.model.survey,
		$element,
		h = [],
		idx = -1,
		files;
	
	if(survey && sId) {
		// Set the display name
		$('.formName').html(survey.displayName);
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
			h[++idx] = '<tr class="';
			h[++idx] = files[i].type;
			h[++idx] = '">';
			h[++idx] = '<td class="preview">';
			h[++idx] = '<a target="_blank" href="';
			h[++idx] = files[i].url;
			h[++idx] = '">';
			if(files[i].type == "audio") {
				h[++idx] = markup.addQType("audio");
			} else {
				h[++idx] = '<img src="';
				h[++idx] = files[i].thumbnailUrl;
				h[++idx] = '" alt="';
				h[++idx] = files[i].name;
				h[++idx] = '">';
			}
			h[++idx] = '</a>';
			h[++idx] = '</td>';
			h[++idx] = '<td class="filename">';
				h[++idx] = '<p>';
				h[++idx] = files[i].name;
				h[++idx] = '</p>';
			h[++idx] = '</td>';
			h[++idx] = '<td class="mediaManage">';
				h[++idx] = '<p>';
				h[++idx] = files[i].size;
				h[++idx] = '</p>';
			h[++idx] = '</td>';
			h[++idx] = '<td class="mediaManage">';
				h[++idx] = '<button class="media_del btn btn-danger" data-url="';
				h[++idx] = files[i].deleteUrl;
				h[++idx] = '">';
				h[++idx] = '<span class="glyphicon glyphicon-trash" aria-hidden="true"></span>'
				h[++idx] = ' Delete';
				h[++idx] = '</button>';
			h[++idx] = '</td>';
			h[++idx] = '<td class="mediaSelect">';
				h[++idx] = '<button class="mediaAdd btn btn-success">';
				h[++idx] = '<span class="glyphicon glyphicon-plus" aria-hidden="true"></span>'
					h[++idx] = ' Add';
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

});