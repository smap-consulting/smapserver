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
    	lang_location: '..'
    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery'],
        'jquery-drag-ui.min': ['jquery'],
        'bootstrap.file-input': ['bootstrap.min'],
    	'bootbox.min': ['bootstrap.min']
        
    }
});

require([
         'jquery',
         'app/common', 
         'bootstrap.min', 
         'modernizr',
         'app/localise',
         'app/globals',
         'jquery-drag-ui.min',
         'jquery.autosize.min',
         'bootstrap.file-input',
         'bootbox.min'], 
		function($, common, bootstrap, modernizr, lang, globals, jquery_ui) {


var	gMode = "survey",
	gTempQuestions = [],
	gLanguage = 0,
	gIndex = 0,			// Unique index to each question
	$gCurrentRow,		// Currently selected row
	gCollapsedPanels = [];

// Media globals
var gUrl,			// url to submit to
	gBaseUrl = '/surveyKPI/upload/media',
	gSId;

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
	
	localise.setlang();		// Localise HTML
	
	// Get the parameters and start editing a survey if one was passed as a parameter
	params = location.search.substr(location.search.indexOf("?") + 1)
	pArray = params.split("&");
	for (i = 0; i < pArray.length; i++) {
		param = pArray[i].split("=");
		console.log("param:" + param[0] +":");
		if ( param[0] === "id" ) {
			globals.gCurrentSurvey = param[1];
			saveCurrentProject(-1, globals.gCurrentSurvey);	// Save the current survey id
			console.log("Passed in survey is: " + globals.gCurrentSurvey);
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
		globals.model.save(surveyListDone);
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
		
		// Set the default destination as organisation rather than survey level files
		gUrl = gBaseUrl;
		$('#survey_id').val("");				// clear the survey id in the forms hidden field
		gIsSurveyLevel = false;
		
		$('#mediaModal').modal('show');

	});
	
	$('#m_settings').off().click(function() {	// Get a survey from Smap
		
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
		gLanguage = $(this).val();
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
            	refreshMediaView(data, surveyId);
            	$('#upload_msg').removeClass('alert-danger').addClass('alert-success').html("Upload Success");
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
			updateLabel(type, gSelFormId, gSelId, gOptionList, gElement, gNewVal, gQname);
		}
	});
	
	$('#removeMedia').click(function() {

		if(gOptionList) {
			type = "option";
		} else {
			type = "question";
		}
		updateLabel(type, gSelFormId, gSelId, gOptionList, gElement, undefined, gQname);
		
	});
});

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
	console.log("getSurveyList: " + globals.gCurrentSurvey);
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
/*
 * Show the form on the screen
 */
function refreshView() {
	
	var i,
		survey = globals.model.survey,
		key,
		h = [],
		idx = -1;
	
	if(survey) {
		if(survey.forms && survey.forms.length > 0) {
			for(i = 0; i < survey.forms.length; i++) {
				if(survey.forms[i].parentform == 0) {
					h[++idx] = addQuestions(survey.forms[i], i);
					break
				}
			}
		}
	}
	
	// Get the current list of collapsed panels
	gCollapsedPanels = [];
	gIndex = 0;
	$('.in').each(function(){
		gCollapsedPanels.push($(this).attr("id"));
	});
	
	// Update the form view
	$('#formList').html(h.join(""));
	
	// Restore collapsed panels
	for(i = 0; i < gCollapsedPanels.length; i++) {
		console.log("collapsed: " + gCollapsedPanels[i]);
		$('#' + gCollapsedPanels[i]).addClass("in");
	}
	
	//enableDragablePanels();
	
	$('.labelProp').change(function(){
		event.preventDefault();
		var $this = $(this),
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
		updateLabel(type, formIndex, itemIndex, optionList, "text", newVal, qname); // TODO Hint

	});
	
	$('.mediaProp').off().click(function(){
		event.preventDefault();
		
		var $this = $(this);
		mediaPropSelected($this);

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
		
		$('#surveyPanel, #orgPanel').find('tr').removeClass('success');	// Un mark any other seelcted rows
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
		idx = -1;
	
	for(i = 0; i < languages.length; i++) {
		h[++idx] = '<option value="';
		h[++idx] = i;
		h[++idx] = '">';
		h[++idx] = languages[i];
		h[++idx] = '</option>';
	}
	$('.language_list').html(h.join(""));
	$('.survey_name').val(globals.model.survey.displayName);
	$('.survey_name_view').html(globals.model.survey.displayName);
	$('#set_survey_ident').val(globals.model.survey.ident);
}


/*
 * Add the questions for a form
 */
function addQuestions(form, fId) {
	var i,
		question,
		h = [],
		idx = -1;
	
	if(form) {
		for(i = 0; i < form.questions.length; i++) {
			question = form.questions[i];
			// Ignore the following questions
			if(question.name === '_task_key' || 
					question.name === 'instanceID' || 
					question.name === 'meta' || 
					question.name === 'meta_groupEnd') {
				continue;
			}
			if(question.type === "end group") {
				h[++idx] = '</ol>';
				h[++idx] = '</div>';
				h[++idx] = '</li>';
				continue;
			}
			if(question.type === "end repeat") {
				continue;
			}
			h[++idx] = addOneQuestion(question, fId, i);
		}
	}
	return h.join("");
}

function addOneQuestion(question, fId, id) {
	var h = [],
		idx = -1;
	
	h[++idx] = addPanelStyle(question.type);
	h[++idx] = '<div class="panel-heading">';
		//h[++idx] = '<div class="container">';
		//	h[++idx] = '<div class="row">';
			h[++idx] = '<table class="table">';
				//h[++idx] = '<div class="col-sm-2 col-xs-4 head1">';
				h[++idx] = '<td class="q_type_col">';
					h[++idx] = addQType(question.type, question.calculation);
				h[++idx] = '</td>';
				//h[++idx] = '<div class="col-sm-3 col-xs-8 head2"><input class="qname" value="';
				h[++idx] = '<td class="q_name_col"><input class="qname form-control" value="';
				h[++idx] = question.name;
				h[++idx] = '" type="text"></td>';
				h[++idx] = addFeaturedProperty(question, fId, id, undefined, undefined);
				h[++idx] = '<td class="q_icons_col">';
					//h[++idx] = '<span class="glyphicon glyphicon-trash edit_icon1"></span>';
					h[++idx] = '<a data-toggle="collapse"  href="#collapse';
					h[++idx] = ++gIndex;
					h[++idx]='"><span class="glyphicon glyphicon-collapse-down edit_collapse_icon"></span></a>';
			h[++idx] = '</td>';
			h[++idx] = '</table>';
		//h[++idx] = '</div>';
	//h[++idx] = '</div>';
	h[++idx] = '<div id="collapse';
	h[++idx] = gIndex;
	h[++idx] = '" class="panel-body collapse';
	if(question.type.indexOf("select") === 0) {
		h[++idx] = ' selectquestion';
	}
	h[++idx] = '">';
	if(question.type === "begin repeat" || question.type === "geopolygon" || question.type === "geolinestring") {
		h[++idx] = addSubForm(question, globals.model.survey.forms[fId].id);
	} else if(question.type.indexOf("select") === 0) {
		h[++idx] = addOptions(question, fId);
	} 
	
	if(question.type === "begin group") {	/* Add questions up to the end group to this panel */
		h[++idx] = '<ol>';
	} else { 
		h[++idx] = '</div>';
		h[++idx] = '</li>';
	}
	
	return h.join("");
}

function addPanelStyle(type) {
	
	if(type === "begin repeat" || type === "begin group") {
		return '<li class="panel panel-warning">';
	} else {
		return '<li class="panel panel-success">';		
	}
}

function addQType(type, calculation) {
	if(type === "string" && !calculation) {
		return '<span class="glyphicon glyphicon-font edit_type"></span>';	
	} else if(type === "select1") {
		return '<img class="edit_image" src="/images/select1_64.png">';
	} else if(type === "select") {
		return '<img class="edit_image" src="/images/select_64.png">';
	} else if(type === "begin repeat") {
		return '<span class="glyphicon glyphicon-repeat edit_type"></span>';
	} else if(type === "begin group") {
		return '<span class="glyphicon glyphicon-folder-open edit_type"></span>';
	} else if(type === "image") {
		return '<div style="width:100%;" class="text-center"><span class="glyphicon glyphicon-camera edit_type"></span></div>';
	} else if(type === "audio") {
		return '<div style="width:100%;" class="text-center"><span class="glyphicon glyphicon-volume-up edit_type"></span></div>';
	} else if(type === "video") {
		return '<div style="width:100%;" class="text-center"><span class="glyphicon glyphicon-facetime-video edit_type"></span></div>';
	} else if(type === "geopoint") {
		return '<span class="glyphicon glyphicon-map-marker edit_type"></span>';
	} else if(type === "dateTime" || type === "date") {
		return '<span class="glyphicon glyphicon-calendar edit_type"></span>';
	} else if(type === "time") {
		return '<span class="glyphicon glyphicon-time edit_type"></span>';
	} else if(type === "barcode") {
		return '<span class="glyphicon glyphicon-barcode edit_type"></span>';
	}  else if(type === "int") {
		return '<span class="edit_type">#</span>';
	} else if(type === "decimal") {
		return '<span class="edit_type">#.#</span>';
	} else if(type === "geolinestring") {
		return '<img class="edit_image" src="/images/linestring_64.png">';
	} else if(type === "geopolygon") {
		return '<img class="edit_image" src="/images/polygon_64.png">';
	} else if(type === "string" && calculation) {
		return '<img class="edit_image" src="/images/calc_64.png">';
	} else {
		return '<span class="glyphicon glyphicon-record edit_type"></span>';
	}
}

/*
 * One of the questions properties will be featured so that it can be edited in the header without expanding the question
 */
function addFeaturedProperty(question, fId, id, list_name, qname) {
	
	var h = [],
		idx = -1,
		type = "question";
	
	if(list_name) {
		type = "option";
	}
	
	h[++idx] = '<td class="q_label_col ';
	h[++idx] = type;
	h[++idx] = '" data-fid="';
	h[++idx] = fId;
	h[++idx] = '" data-id="';
	h[++idx] = id;
	if(qname) {
		h[++idx] = '" data-qname="';
		h[++idx] = qname;
	}
	if(list_name) {
		h[++idx] = '" data-list_name="';
		h[++idx] = list_name;
		type = "option";
	}
	h[++idx] = '">';
	h[++idx] = getFeaturedMarkup(question, type);
	h[++idx] = '</td>';
	return h.join("");
}

/*
 * Get Featured Markup for the question
 */
function getFeaturedMarkup(question, type) {
	var h = [],
		idx = -1,
		selProperty = $('#selProperty').val(),
		naMedia = '<div class="naMedia text-center">Media cannot be used with this question</div>';
	
	if(selProperty === "label") {
		h[++idx] = '<textarea class="labelProp" placeholder="Label"';
		if((type === "question" && (question.source != "user" && question.type != "begin group" && question.type != "begin repeat") || question.calculation)) {
			h[++idx] = ' readonly tabindex="-1">';
			h[++idx] = 'Label not required';
		} else {
			h[++idx] = ' tabindex="';
			h[++idx] = gIndex;
			h[++idx] = '">';
			h[++idx] = question.labels[gLanguage].text;
		}
		h[++idx] = '</textarea>';
	} else if(selProperty === "media") {
		h[++idx] = '<div class="row">';
			if(type === "question" && (question.inMeta || question.source != "user" || question.calculation)) {
				h[++idx] = '<div class="col-sm-4 col-sm-offset-4">';
				h[++idx] = naMedia;
				h[++idx] = '</div>';
			} else {
				h[++idx] = addMedia("Image", 
						question.labels[gLanguage].image, 
						question.labels[gLanguage].imageUrl, 
						question.labels[gLanguage].imageThumb);
		        
				h[++idx] = addMedia("Video", 
						question.labels[gLanguage].video, 
						question.labels[gLanguage].videoUrl, 
						question.labels[gLanguage].videoThumb);
				
				h[++idx] = addMedia("Audio", 
						question.labels[gLanguage].audio, 
						question.labels[gLanguage].audioUrl, 
						question.labels[gLanguage].audioThumb);
				

			}
			
		h[++idx] = '</div>';		// End of row

	}
	
	return h.join("");
}

/*
 * Add a media type
 */
function addMedia(label, mediaIdent, url, thumbUrl) {
	var h = [],
		idx = -1,
		emptyMedia = '<div class="emptyMedia text-center">Empty</div>',
		lcLabel = label.toLowerCase();
	
	h[++idx] = '<div class="col-sm-3 ';
	h[++idx] = lcLabel;
	h[++idx] = 'Element">';
	if(mediaIdent) {
		h[++idx] = '<a target="_blank" href="';
		h[++idx] = url
		h[++idx] = '"';
	} else {
		h[++idx] = "<div";
	}
	h[++idx] = ' class="thumbnail preview">';

	if(mediaIdent) {
		if(thumbUrl || (lcLabel === "image" && url)) {
			h[++idx] = '<img height="100" width="100" src="';
			if(thumbUrl) {
				h[++idx] = thumbUrl;
			} else {
				h[++idx] = url;
			}
			h[++idx] = '">';
		} else {
			h[++idx] = addQType(lcLabel)
		}
	} else {
		h[++idx] = emptyMedia;
	}

	if(mediaIdent) {
		h[++idx] = '</a>';
	} else {
		h[++idx] = '</div>';
	}
    h[++idx] = '<a type="button" class="btn btn-default mediaProp form-control" data-element="';
    h[++idx] = label.toLowerCase();
    h[++idx] = '">';
    h[++idx] = lcLabel;
    h[++idx] = '</a>';
 
    h[++idx] = '</div>';
    
    return h.join("");
}
/*
 * Add subform
 */
function addSubForm(question, parentId) {
	
	var h = [],
		idx = -1,
		formName,
		survey = globals.model.survey,
		forms = [],
		i,
		form;
	
	h[++idx] = '<ol class="list-unstyled">';
	
	// Get the form
	formName = question.name;
	forms = survey.forms;
	for(i = 0; i < forms.length; i++) {
		form = forms[i];
		if(forms[i].parentform === parentId) {
			h[++idx] = addQuestions(forms[i], i);
			break;
		}
	}
	
	h[++idx] = '</ol>';
	
	return h.join("");
}

/*
 * Show the options
 */
function addOptions(question, fId) {
	var survey = globals.model.survey,
		options = survey.optionLists[question.list_name],
		h = [],
		idx = -1,
		i;
	
	if(options) {
		for(i = 0; i < options.length; i++) {
			h[++idx] = addOneOption(options[i], fId, i, question.list_name, question.name);
		}
	}
	return h.join("");
}

/*
 * Add a single option
 */
function addOneOption(option, fId, id, list_name, qname) {
	var h = [],
		idx = -1;

	h[++idx] = '<table class="table">';
	h[++idx] = '<td class="q_name_col"><input class="qname form-control" value="';
	h[++idx] = option.value;
	h[++idx] = '" type="text"></td>';
	h[++idx] = addFeaturedProperty(option, fId, id, list_name, qname);
	//h[++idx] = '<td class="q_icons_col">';	TODO Add Deletion
	//h[++idx] = '<span class="glyphicon glyphicon-trash edit_icon1"></span>';
	//h[++idx] = '</td>';
	h[++idx] = '</table>';

	return h.join("");
}

/*
 * Call this to update a label
 * Label processing is different than other attributes since it has to be applied to multiple languages
 * Parameters:
 * 	element:  text || hint || image || video || audio
 *  newVal: The new value for the label
 *  type: question || option
 */
function updateLabel(type, formIndex, itemIndex, optionList, element, newVal, qname) {
	
	var item = [],		// An array is used because the translate page can push multiple questions / options into the list that share the same text
		markup,
		survey = globals.model.survey,
		i;
	
	console.log(survey);
	if(type === "question") {
		item.push({
			form: formIndex,
			question: itemIndex
		});
		
		// Update the in memory survey model
		if(element === "text") {
			survey.forms[formIndex].questions[itemIndex].labels[gLanguage][element] = newVal;
		} else {
			// For non text changes update all languages
			for(i = 0; i < survey.forms[formIndex].questions[itemIndex].labels.length; i++) {
				survey.forms[formIndex].questions[itemIndex].labels[i][element] = newVal;
				survey.forms[formIndex].questions[itemIndex].labels[i][element + "Url"] = getUrl(survey.o_id, survey.ident, newVal, false);;
			}
		}
	} else {
		item.push({
			optionList: optionList,
			qname: qname,
			option: itemIndex
		});
		if(element === "text") {
			survey.optionLists[optionList][itemIndex].labels[gLanguage][element] = newVal;
		} else {
			// For non text changes update all languages
			for(i = 0; i < survey.optionLists[optionList][itemIndex].labels.length; i++) {
				survey.optionLists[optionList][itemIndex].labels[i][element] = newVal;
				survey.optionLists[optionList][itemIndex].labels[i][element+ "Url"] = getUrl(survey.o_id, survey.ident, newVal, false);
			}
		}
	}
	
	// Add the change to the list of changes to be applied
	globals.model.modLabel(gLanguage, item, newVal, element);
	
	
	// Update the current markup
	if(element === "image") {	
		
		markup = addMedia("Image", 
				newVal, 
				getUrl(survey.o_id, survey.ident, newVal, false), 
				getUrl(survey.o_id, survey.ident, newVal, true)
				);
		
	} else if(element === "video") {
		
		markup = addMedia("Video", 
				newVal, 
				getUrl(survey.o_id, survey.ident, newVal, false), 
				getUrl(survey.o_id, survey.ident, newVal, true)
				);
		
	} else if(element === "audio") {
		
		markup = addMedia("Audio", 
				newVal, 
				getUrl(survey.o_id, survey.ident, newVal, false), 
				undefined
				);	

	}
	
	if($gCurrentRow) {
		$gCurrentRow.find('.' + element + 'Element').replaceWith(markup);
		$('.mediaProp', $gCurrentRow).off().click(function(){
			event.preventDefault();	
			var $this = $(this);
			mediaPropSelected($this);
		});
	}
}

/*
 * Media functions
 */
function getUrl(o_id, s_ident, newVal, thumbs) {
	var url = "/media/";
	if(gIsSurveyLevel) {
		url += s_ident;
		url += "/";
		if(thumbs) {
			url += "thumbs/"; 
		}
		url += newVal;
	} else {
		url += "organisation/";
		url += o_id;
		url += "/";
		if(thumbs) {
			url += "thumbs/"; 
		}
		url += newVal;
	}
	
	return url;
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

function refreshMediaView(data, sId) {
	
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
			h[++idx] = '<tr class="';
			h[++idx] = files[i].type;
			h[++idx] = '">';
			h[++idx] = '<td class="preview">';
			h[++idx] = '<a target="_blank" href="';
			h[++idx] = files[i].url;
			h[++idx] = '">';
			if(files[i].type == "audio") {
				h[++idx] = addQType("audio");
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
});