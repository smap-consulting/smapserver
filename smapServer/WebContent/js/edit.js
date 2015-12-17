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
    	jquery: 'jquery-1.8.3.min',
    	bootbox: 'bootbox.min',
    	lang_location: '..'

    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery'],
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
				jqas, 
				bsfi, 
				bootbox,
				question,
				markup,
				changeset) {


var	gMode = "survey",
	gTempQuestions = [],
	$gCurrentRow,			// Currently selected row
	gCollapsedPanels = [],
	gTempLanguages = [],
	gErrorPosition = 0;

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
		openingNew = false,
		dont_get_current_survey = true;
	
	window.bootbox = bootbox;
	
	localise.setlang();		// Localise HTML
	
	// Get the parameters and start editing a survey if one was passed as a parameter
	params = location.search.substr(location.search.indexOf("?") + 1)
	pArray = params.split("&");
	dont_get_current_survey = false;
	for (i = 0; i < pArray.length; i++) {
		param = pArray[i].split("=");
		if ( param[0] === "id" ) {
			dont_get_current_survey = true;		// USe the passed in survey id
			globals.gCurrentSurvey = param[1];
			saveCurrentProject(-1, globals.gCurrentSurvey);	// Save the current survey id
		} else if ( param[0] === "new" ) {
			dont_get_current_survey = true;		// Don't get any survey details
			globals.gCurrentSurvey = -1;
			openForm("new");
		}
	}
	
	window.history.pushState('',document.title, document.location.origin + document.location.pathname);	// Strip out the parameters from the href
	
	// Get the user details
	globals.gIsAdministrator = false;
	getLoggedInUser(getSurveyList, false, true, undefined, false, dont_get_current_survey);
	getFilesFromServer(gBaseUrl, undefined, refreshMediaView);		// Get the organisational level media files

	/*
	 * Refresh the view when the selected property changes
	 */
	$('#selProperty').change(function() {
		var i;
	
		refreshForm();
		
	});
	
	// Add menu functions
	$('#m_open').off().click(function() {	// Open an existing form
		if(globals.changes.length > 0) {
			if (confirm("You have unsaved changes are you sure you want to leave?")) {
				openForm("existing");
			}
		} else {
			openForm("existing");
		}
		
	});
	$('#m_new').off().click(function() {	// Open a new form
		if(globals.changes.length > 0) {
			if (confirm("You have unsaved changes are you sure you want to leave?")) {
				openForm("new");
			}
		} else {
			openForm("new");
		}
		
	});
	$('.m_save_survey').off().click(function() {	// Save a survey to the server
		changeset.validateAll();
		if(changeset.numberIssues("error") === 0) {
			changeset.save(surveyListDone);
		} else {
			bootbox.alert("Cannot save until errors are fixed");
		}
	});

	$('#next-error').off().click(function(){
		changeset.nextIssue("error");
	});
	
	$('#next-warning').off().click(function(){
		changeset.nextIssue("warning");
	});
	
	$('.m_validate').off().click(function() {
		changeset.validateAll();
	});
	
	$('.m_languages').off().click(function() {
		console.log("Edit languages");
		gTempLanguages = globals.model.survey.languages.slice();
		updateLanguageView();
		$('#editLanguageModal').modal("show");
	});
	
	$('#addLanguage').off().click(function() {
		gTempLanguages.push("");
		updateLanguageView();
	});
	
	// Add menu functions
	$('#m_media').off().click(function() {	// MEDIA
		// Set up media dialog to manage loading and deleting of media
		$('.mediaManage').show();
		$('.mediaSelect').hide();
		$('#mediaModalLabel').html("Manage Media Files For this Form");
		$('#mediaModal table').off();
		$('#surveyPanel, #orgPanel').find('tr').removeClass('success');
		
		// Make sure all types of media are shown
		$('tr').show();
		// Close any drop downmenus
		$('.dropdown-toggle').parent().removeClass("open");
		$('.navbar-collapse').removeClass("in");
		
		// Set the default destination 
		//if($('#orgLevelTab').hasClass("active")) {
		//	gUrl = gBaseUrl;
		//	$('#survey_id').val("");				// clear the survey id in the forms hidden field
		//	gIsSurveyLevel = false;
		//} else {
		
		// Only form level media is managed here, organisation level media is managed in the shared resources page
		$('#orgPanel').hide();
		$('#surveyPanel').show();
		gUrl = gBaseUrl + '?sId=' + gSId;
    	$('#survey_id').val(gSId);			// Set the survey id in the forms hidden field
    	gIsSurveyLevel = true;
    	
		//}
		$('#upload_msg').removeClass('alert-danger').addClass('alert-success').html("");
		$('#mediaModal').modal('show');

	});
	
	$('#m_settings').off().click(function() {	// Show the settings dialog
		
		// Close any drop downmenus
		$('.dropdown-toggle').parent().removeClass("open");
		$('.navbar-collapse').removeClass("in");
		
		$('#settingsModal').modal('show');
	});


	$('#save_settings').off().click(function() {	// Save settings to the database
		globals.model.save_settings();
	});
	
	/*
	 * Save changes to the language list
	 */
	$('#editLanguageSave').off().click(function() {	// Save languages to the database

		var languagesString = JSON.stringify(gTempLanguages);
		console.log("Saving languages: " + languagesString);
		addHourglass();
		$.ajax({
			  type: "POST",
			  contentType: "application/json",
			  dataType: "json",
			  url: "/surveyKPI/surveys/save_languages/" + gSId,
			  data: { languages: languagesString },
				success: function(data) {
					removeHourglass();
					$('#editLanguageModal').modal("hide");
					globals.model.survey = data;
					setLanguages(data.languages);
					refreshForm();
				},
				error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						alert("Error: Failed to create survey: " + xhr.responseText);
					}
				}
		});
	});
	
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;
		saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
		getSurveyList();
 	 });
	
	$('.language_list').off().change(function() {
		globals.gLanguage = $(this).val();
		refreshForm();
 	 });
	
	// Check for changes in settings
	$('#set_survey_name, #set_instance_name').keyup(function(){
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
	$('#delPdfTemplate').off().click(function() {
		globals.model.settingsAddPdfClicked();
		$('.upload_file_msg').val("");
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
		if(globals.changes.length > 0) {
			return "You have unsaved changes are you sure you want to leave?";
		} else {
			return;
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
    	uploadFiles(gUrl, "fileupload", refreshMediaView, globals.gCurrentSurvey);
    });
    
    /*
     * Open a new form
     */
	$('#get_form').off().click(function() {
		
		var name;
		
		changeset.setHasChanges(0);		// Clear any existing changes from a previous form
		
		if(globals.gExistingSurvey) {
			globals.gCurrentSurvey = $('#form_name option:selected').val();	
			saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the new survey id as the current survey
			getSurveyDetails(surveyDetailsDone);
		} else {
			name = $('#new_form_name').val();
			createNewSurvey(name, surveyDetailsDone);
		}
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
	
	setupQuestionTypes($('#dialog_types'), 2, false);		// Double column, not draggable for change type dialog
	setupQuestionTypes($('#toolbar_types'), 1, true);		// Single column, draggable for toolbar
	
	// Set focus on survey name when create form modal is opened
	$('#openFormModal').on('shown.bs.modal', function () {
		$('#new_form_name').focus();
	});
	
	/*
	 * Toolbar
	 */
	$('.question_type_sel', '#toolbar_types').off().click(function(){
		var type = $(this).val(),
			$finalButton = $('.add_final_button', '#formList');
		
		addQuestion($finalButton, type);
		

	});
	
});

//Set up question type dialog
function setupQuestionTypes($elem, columns, draggable) {
	var i,
		types = globals.model.qTypes,
		h = [],
		idx = -1,
		count;
	
	h[++idx] = '<div class="row margin-bottom">';
	for(i = 0; i < types.length; i++) {
		
		if(types[i].canSelect) {
			
			h[++idx] = '<div class="col-xs-12 ';
			h[++idx] = columns === 1 ? '" ' : 'col-md-6" ';
			h[++idx] = ' style="height:65px;">';	
			h[++idx] = '<button type="button" class="btn btn-large btn-default question_type_sel full_width_btn';
			if(draggable) {
				h[++idx] = ' draggable';
			}
			h[++idx] = '" value="';
			h[++idx] = types[i].type;
			h[++idx] = '">';
			if(types[i].glyphicon) {
				h[++idx] = '<span class="glyphicon glyphicon-';
				h[++idx] = types[i].glyphicon; 
				h[++idx] = ' edit_type_select"></span><br/>';
			} else if(types[i].image) {
				h[++idx] = '<img class="edit_image_select" src="';
				h[++idx] = types[i].image; 
				h[++idx] = '"></img><br/>';
			} else if(types[i].text) {
				h[++idx] = '<span class="edit_type_select">';
				h[++idx] = types[i].text; 
				h[++idx] = '</span><br/>';
			}
			h[++idx] = types[i].name;
			h[++idx] = '</button>';
			h[++idx] = '</div>';		// End col

			
			//count++;
		}
	}	
	h[++idx] = '</div>';	// End of a row
	//h[++idx] = '</div>';	// End of a row
	
	$elem.html(h.join(''));
	
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
		getFilesFromServer(gBaseUrl, globals.gCurrentSurvey, refreshMediaView);
	}
	
	$('#openFormModal').modal("hide");		// Hide the open form modal if its open
	
	updateSettingsData();		// Update edit view
	
	refreshForm();
	
	// Set up link to test file
	$('.m_test_survey').attr("href", "/webForm/s" + globals.gCurrentProject + "_" + globals.gCurrentSurvey);
	
}



/*
 * Show the form on the screen
 */
function refreshForm() {
	
	var $context;
	
	$context = markup.refresh();
	respondToEvents($context);
	

}

/*
 * The passed in context is for a list item containing either a question or an option
 */
function respondToEvents($context) {
	
	// Set option list value
	$context.find('.option-lists', $context).each(function(index){
		var $this = $(this),
			$elem = $this.closest('li'),
			formIndex = $elem.data("fid"),
			itemIndex = $elem.data("id"),
			survey = globals.model.survey,
			question;
		
		question = survey.forms[formIndex].questions[itemIndex];
		$this.val(question.list_name);
		
	});
	
	// Option list change
	$context.find('.option-lists').change(function(){
		var $this = $(this),
			$elem = $this.closest('li'),
			formIndex = $elem.data("fid"),
			itemIndex = $elem.data("id"),
			survey = globals.model.survey,
			question;
	
		updateLabel("question", formIndex, itemIndex, undefined, "text", $this.val(), undefined, "list_name") ;
	});
	
	// Repeat count change
	$context.find('.repeat-counts').change(function(){
		var $this = $(this),
			$elem = $this.closest('li'),
			formIndex = $elem.data("fid"),
			itemIndex = $elem.data("id"),
			survey = globals.model.survey,
			question;
	
		updateLabel("question", formIndex, itemIndex, undefined, "text", $this.val(), undefined, "calculation") ;
	});
	
	// Add tooltips
	$context.find('.has_tt').tooltip();
	
	// Respond to changes in the label field - this would change the property that has focus
	$context.find('.labelButton').off().click(function() {

		var $this = $(this),
			prop = $this.data("prop"),
			$li = $this.closest('li'),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id"),
			newVal = $this.hasClass("prop_no"),		// If set false then newVal will be true
			type,
			optionList = $li.data("list_name"),
			qname = $li.data("qname");
		
		if($li.hasClass("option")) {
			type = "option";
		} else {
			type = "question";
		}

		var labelType = prop === "hint" ? "hint" : "text";
		updateLabel(type, formIndex, itemIndex, optionList, labelType, newVal, qname, prop); 

	});
	
	// Respond to clicks on a label button
	$context.find('.labelProp').change(function(){

		var $this = $(this),
			prop = $this.data("prop"),
			$li = $this.closest('li'),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id"),
			newVal = $this.val(),
			type,
			optionList = $li.data("list_name"),
			qname = $li.data("qname");
		
		if($li.hasClass("option")) {
			type = "option";
		} else {
			type = "question";
		}

		var labelType = prop === "hint" ? "hint" : "text";
		updateLabel(type, formIndex, itemIndex, optionList, labelType, newVal, qname, prop); 

	});
	
	// validate the name on focus as duplicates may have been removed elsewhere
	$context.find('.qname').focusin(function(){

		var $this = $(this),
			$li = $this.closest('li'),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id");
		
		changeset.validateItem(formIndex, itemIndex, "question"); 

	});
	
	// Fix issues with dragging and selecting text in text area or input when draggable is set
	// Mainly a problem with Firefox however in Chrome selecting text by dragging does not work
	// Refer: http://stackoverflow.com/questions/21680363/prevent-drag-event-to-interfere-with-input-elements-in-firefox-using-html5-drag
	$context.find('input, textarea').focusin(function() {
		$(this).closest('.draggable').attr("draggable", false);
	}).blur(function() {
        $(this).closest('.draggable').attr("draggable", true);
        console.log("blur");
    });
	
	// On tab in question name move to the feature input
	$context.find('.qname').keydown(function(e){
		if(e.keyCode === 9) {
			e.preventDefault();
			$(this).closest('tr').find('.labelProp').focus();
		}
	});

	
	// validate the question name on every character change
	$context.find('.qname').keyup(function(){

		var $this = $(this),
			$li = $this.closest('li'),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id"),
			newVal = $this.val();
		
		changeset.validateName(formIndex, itemIndex, newVal, "question");
		changeset.updateModelWithErrorStatus(formIndex, itemIndex, "question");		// Update model and DOM

	});
	
	// validate the option name
	$context.find('.oname').keyup(function(){

		var $this = $(this),
			$li = $this.closest('li');
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id"),
			listName = $li.data("list_name"),
			newVal = $this.val();
		
		changeset.validateName(listName, itemIndex, newVal, "option");
		changeset.updateModelWithErrorStatus(listName, itemIndex, "option");		// Update model and DOM

	});
	
	// Update the question name
	
	$context.find('.qname').change(function(){

		var $this = $(this),
			$li = $this.closest('li'),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id"),
			newVal = $this.val();
		
		updateLabel("question", formIndex, itemIndex, undefined, "text", newVal, undefined, "name") ;

	});
	
	// Update the option name
	$context.find('.oname').change(function(){

		var $this = $(this),
			$li = $this.closest('li'),
			listName = $li.data("list_name"),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id"),
			qname = $li.data("qname"),
			newVal = $this.val();
		
		updateLabel("option", formIndex, itemIndex, listName, "text", newVal, qname, "value") ;
		

	});
	
	// Selected a media property
	$context.find('.mediaProp').off().click(function(){
		
		var $this = $(this);
		mediaPropSelected($this);

	});
	
	/*
	 * Add a new question 
	 *  (or if the property type is groups then extend a group to the selected location)
	 */
	$context.find('.add_question').off().click(function() {
		var $this = $(this);
		
		addQuestion($this, "string");


	});
	
	// Delete question
	$context.find('.delete_question').off().click(function() {
		var $this = $(this),
			$context,						// Updated Html
			item = $(this).data("id");
		
		bootbox.confirm("Are you sure you want to delete this question?", function(result) {
			if(result) {
				$context = question.deleteQuestion(item);
			}
		}); 
		
	});
	
	// Add new option
	$context.find('.add_option').off().click(function() {
		var $this = $(this),
			$context,						// Updated Html
			oId = $this.data("oid"),
			fId = $this.data("fid"),
			qname = $this.data("qname"),
			list_name = $this.data("list_name"),
			locn = $this.data("locn");	// Add before or after the element id referenced by oId
		
		$context = question.addOption($this, oId, locn, list_name, fId, qname);
		respondToEvents($context);				// Add events on to the altered html
		if($context.attr("id") !== "formList") {
			respondToEvents($context.prev());		// Add events on the "insert before" button
		}
		
		// Set focus to the new option
		$context.find('textarea').focus();			// Set focus to the new option
		
		/*
		 * If this option was added by an "add after" button then that button should add future questions
		 *  after this newly added option.  Hence update the reference question
		 */
		if(locn == "after") {
			$this.data("oid", $context.attr("id"));
		}
	});
	
	
	// Delete option
	$context.find('.delete_option').off().click(function() {
		var $this = $(this),
			$context,						// Updated Html
			item = $(this).data("id");
		
		bootbox.confirm("Are you sure you want to delete this choice?", function(result) {
			if(result) {
				$context = question.deleteOption(item);
			}
		}); 
		
		
	});
	
	// Select types
	$context.find('.question_type').off().click(function() {
		
		var $this = $(this),
			$questionElement = $this.closest('li'),
			published,
			survey = globals.model.survey,
			name;
		
		gFormIndex = $questionElement.data("fid");
		gItemIndex = $questionElement.data("id");
		
		published = survey.forms[gFormIndex].questions[gItemIndex].published;
		if(published) {
			alert("You cannot change the type or name of a question that has already been published");
		} else if($this.hasClass("disabled")) {
			alert("You cannot change the type of a question that has an invalid name");
		} else {
			$('.question_type_sel', '#dialog_types').off().click(function(){
				var type = $(this).val();
				
				updateLabel("question", gFormIndex, gItemIndex, undefined, "text", type, undefined, "type");
				$('#typeModal').modal('hide');
				
				// Add an end group question if a new group has been created
				if(type === "begin group") {
					name = survey.forms[gFormIndex].questions[gItemIndex].name + "_groupEnd" ;
					$context = question.add(gFormIndex, $questionElement.attr("id"), 
							"after", 
							"end group",
							name);
					respondToEvents($context);	
				}
			});
			
			$('#typeModal').modal({
					keyboard: true,
					backdrop: 'static',
					show: true
				});
		}

	});
	
	/*
	 * Enable drag and drop to move questions and choices
	 * 
	 * First add handlers for draggable components
	 */
	$('.draggable').attr('draggable', 'true')
	
	.off('dragstart')
	.on('dragstart', function(evt){
		var ev = evt.originalEvent;
		
		ev.effectAllowed = "move";		// Only allow move, TODO copy
		
		if(typeof ev.target.value !== "undefined" && ev.target.value.length > 0) {
			ev.dataTransfer.setData("type", ev.target.value);
		} else {	// Moving a question / option
			ev.dataTransfer.setData("text/plain", ev.target.id);
		}
		$('.dropon').addClass("add_drop_button").removeClass("add__button");
		
		return true;
	})
	
	// clean up after drag
	.off('dragend')
	.on('dragend', function(evt){
		$('.dropon').addClass("add_button").removeClass("add_drop_button").removeClass("over_drop_button");
		return false;
	})
	
	// Don't allow a draggable component to be dropped onto a text field in some other question / option
	.off('drop')
	.on('drop', function(evt){
		evt.originalEvent.preventDefault();
	});
	
	
	/*
	 * Handle drop on or dragging over a drop zone
	 */
	
	// Entering a drop zone
	$('.dropon')
	
	.off('dragenter')
	.on('dragenter', function(evt){
		var ev = evt.originalEvent,
			$elem = $(ev.target),	
			targetId = $elem.data('qid');
		
		$elem.addClass("over_drop_button").removeClass("add_button").addClass("add_frop_button");
	
	})
	
	// Leaving a drop zone
	.off('dragleave')
	.on('dragleave', function(evt){
		
		var ev = evt.originalEvent,
			$elem = $(ev.target),
			sourceId = ev.dataTransfer.getData("text/plain"),
			targetId = $elem.data('qid');
		
		$elem.addClass("add_button").removeClass("over_drop_button").addClass("add_drop_button");
		
		
	})
	
	.off('dragover')
	.on('dragover', function(evt){
		evt.originalEvent.dataTransfer.dropEffect = "move";
		evt.originalEvent.preventDefault();
		evt.originalEvent.stopPropagation();
	})
	
	// Drop the question, option or type
	.off('drop')
	.on('drop', function(evt){
		var ev = evt.originalEvent,
			$targetListItem = $(ev.target),
			$sourceElem,
			sourceId = ev.dataTransfer.getData("text/plain"),
			sourceValue = ev.dataTransfer.getData("type"),		// The type of a new question that is being dropped
			targetId = $targetListItem.data('qid'),
			locn = $targetListItem.data("locn"),			// Before or after the target question
			$context,
			$related,
			$li,
			type,											// Question or option									
			dropType = false;								// Set true if a question type is being dropped
	
		ev.preventDefault();
		ev.stopPropagation();
		 
		if(typeof sourceValue !== "undefined" && sourceValue.length > 0) {		// Dropped a new type - Question only
			type = "question";
			dropType = true;
		} else {
			
			$li = $targetListItem.closest('li');
			if(locn === "after") {
				$related = $li.prev();
			} else {
				$related = $li.next();
			} 
			if($related.length === 0) {   // Empty group, location is "after"
				targetId = $li.parent().closest('li').attr("id");
			} else {
				targetId = $related.attr("id");
			}
			
			if(sourceId.indexOf("option") === 0) {						// Dropped a question or option
				type = "option";
			} else {
				type = "question";
			}
		}
		
		console.log("Dropped: " + sourceId + " : " + targetId + " : " + sourceValue);
			
		if(dropType) {
			addQuestion($targetListItem, sourceValue);
		} else {
			if(sourceId != targetId) {
				if (ev.ctrlKey || ev.altKey) {
					console.log("Control was pressed");
					$sourceElem = $(document.getElementById(sourceId)).clone(true);
				} else {
					$sourceElem = $(document.getElementById(sourceId));
				}
			    
			    if(type === "question") {
			    	$context = question.moveQuestion(sourceId, targetId, locn);
			    } else if(type === "option") {
			    	$context = question.moveBeforeOption(sourceId, targetId, locn);
			    }
				respondToEvents($context);						// Add events on to the altered html
			}
		}
	});

	
}

/*
 * End of drag and drop
 */

function mediaPropSelected($this) {
	
	var $li = $this.closest('li'),
		$immedParent = $this.closest('div');
	
	// Set up media view
	gElement = $this.data("element");
	gSelFormId = $li.data("fid");
	gSelId = $li.data("id");
	gOptionList = $li.data("list_name"); 
	gQname = $li.data("qname"); 
	$gCurrentRow = $li;
	
	if($('#orgLevelTab').hasClass("active")) {
		$('#orgPanel').show();
		$('#surveyPanel').hide();
		gUrl = gBaseUrl;
    	$('#survey_id').val("");			// Set the survey id in the forms hidden field
    	gIsSurveyLevel = false;
	} else {
		$('#orgPanel').hide();
		$('#surveyPanel').show();
		gUrl = gBaseUrl + '?sId=' + gSId;
    	$('#survey_id').val(gSId);			// Set the survey id in the forms hidden field
    	gIsSurveyLevel = true;
	}
	
	$('.mediaManage').hide();						
	$('.mediaSelect').show();
	$('#mediaModalLabel').html("Select Media File");
	
	// Remove any current selections
	$('#surveyPanel, #orgPanel').find('tr').removeClass('success');
	
	// Only show relevant media
	$('tr','#surveyPanel, #orgPanel').hide();
	$('tr.' + gElement, '#surveyPanel, #orgPanel').show();
	
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
	
	$('#upload_msg').removeClass('alert-danger').addClass('alert-success').html("");
	$('#mediaModal').modal('show');

}

/*
 * Add a new question after an add new question button identified by $this is seelected
 */
function addQuestion($this, type) {
	var $context,						// Updated Html
		prop = $('#selProperty').val(),
		qId,
		$related,
		$li,
		formIndex = $this.data("findex"),
		forms = globals.model.survey.forms,
		justAddedQuestionId,
		availableGroups,
		$textArea,
		textAreaVal,
		locn = $this.data("locn");	// Add before or after the element id referenced by qIdx
	
	$li = $this.closest('li');
	if(locn === "after") {
		$related = $li.prev();
	} else {
		$related = $li.next();
	} 
	if($related.length === 0) {   // Empty group, location is "after"
		qId = $li.parent().closest('li').attr("id");
	} else {
		qId = $related.attr("id");
	}
	
	if(prop === "group") {		// Extend a group
		availableGroups = $this.data("groups").split(":");
		$context = question.setGroupEnd(formIndex, qId, locn ,undefined, undefined, availableGroups);
	} else {
		$context = question.add(formIndex, qId, locn, type);
	}
	
	respondToEvents($context);				// Add events on to the altered html
	if($context.attr("id") !== "formList") {
		respondToEvents($context.prev());		// Add events on the "insert before" button
	}
	
	// Set focus to the new question
	justAddedQuestionID = '#question' + formIndex +  '_' + (forms[formIndex].questions.length - 1);
	$textArea = $('textarea', justAddedQuestionID);
	textAreaVal = $textArea.val();
	$textArea.val("").focus().val(textAreaVal);		// Should set text entry to end of text field	
	
	// Add an end group question if a new group has been created
	if(type === "begin group") {
		name = survey.forms[gFormIndex].questions[gItemIndex].name + "_groupEnd" ;
		$context = question.add(gFormIndex, $questionElement.attr("id"), 
				"after", 
				"end group",
				name);
		respondToEvents($context);	
	}
}

/*
 * Update the settings data (excluding languages which is set globally)
 */
function updateSettingsData() {

	$('.survey_name').val(globals.model.survey.displayName);
	$('.formName').html(globals.model.survey.displayName);
	$('#set_survey_ident').val(globals.model.survey.ident);
	$('#set_instance_name').val(globals.model.survey.instanceNameDefn);
	$('.upload_file_msg').val(globals.model.survey.pdfTemplateName);
}


/*
 * Update the language modal view
 */
function updateLanguageView() {
	var i,
		$selector = $('#language_list'),
		languages = gTempLanguages,
		h = [],
		idx = -1;
	

	h[++idx] = '<table class="table">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th>' + localise.set["c_name"], + '</th>';
	h[++idx] = '<th>' + localise.set["c_desc"] + '</th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody class="table-striped">';

	for(i = 0; i < languages.length; i++) {
		
		h[++idx] = '<tr>';
		
		// name
		h[++idx] = '<td>';
		h[++idx] = '<input type="text" data-idx="';
		h[++idx] = i;
		h[++idx] = '" required class="form-control" value="';
		h[++idx] = languages[i];
		h[++idx] = '"';
		h[++idx] = '</td>';
		
		// description
		h[++idx] = '<td>';
		h[++idx] = '';
		h[++idx] = '</td>';
		
	
		// actions
		h[++idx] = '<td>';
	
		h[++idx] = '<button type="button" data-idx="';
		h[++idx] = i;
		h[++idx] = '" class="btn btn-default btn-sm rm_language danger">';
		h[++idx] = '<span class="glyphicon glyphicon-trash" aria-hidden="true"></span></button>';
	
		h[++idx] = '</td>';
		// end actions
	
		h[++idx] = '</tr>';
	}
	
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';

	$selector.empty().append(h.join(''));

	$(".rm_language", $selector).click(function(){
		var idx = $(this).data("idx");
		gTempLanguages.splice(idx, 1);
		updateLanguageView();
	});

	$("input", $selector).change(function(){
		var idx = $(this).data("idx");
		gTempLanguages.splice(idx, 1, $(this).val());
		updateLanguageView();

	});
	
	
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
		change,
		changeType,
		survey = globals.model.survey,
		forms = survey.forms,
		question = forms[formIndex].questions[itemIndex],
		questionType,
		repeat_path,
		i;
	
	if(type === "question") {
		questionType = question.type;
	}
	
	if(typeof questionType !== "undefined" && questionType === "calculate"  && prop === "label") {
		changeType = "property";
		prop = "calculation";
	} else {
		if(prop === "label" || prop === "media" || prop === "hint")
			changeType = "label";
		else {
			changeType = "property";
		}
	}
	
	if(typeof questionType !== "undefined" && questionType === "begin repeat") {
		for(i = 0; i < forms.length; i++) {
			if(forms[i].parentFormIndex === formIndex && forms[i].parentQuestionIndex === itemIndex) {
				repeat_path = forms[i].repeat_path;
			}
		}
	}
	
	change = {
			changeType: changeType,		// survey | form | language | question | option | (property | label) last two are types of property change
			action: "update",			// add | delete | update
			source: "editor",				// editor | file
			property: {
				qId: undefined,				// qId must be set to apply the change
				qType: questionType,		// Question type
				type: type,					// question or option
				name: undefined,			// name of the question or the option value
				propType: element,			// text or hint or image or video or audio or video
				prop: prop,					// Property to be changed, for example: label or appearance
				languageName: undefined,	// Language Name
				allLanguages: false,		// Set true if all languages should be updated with a new label
				repeat_path: repeat_path,	// Path to repeat count question if this is a begin repeat
				
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
	if($context) {
		$context.find('.labelProp').focus();	// Set focus to label property input
		respondToEvents($context);				// Add events on to the altered html
	}
	
}

/*
 * Return true if the passed in value is a valid option name
 */
function isValidOptionName(val) {
	
	var vCheck = val.trim(),
		isValid = true;
	
	if(vCheck.indexOf(" ") > 0) {
		isValid = false;
	}
	
	return isValid;	
}

/* **********************************************************************************************
 * Error Panel
 */

/*
function showErrorPanel() {
	
    var $panel = $('#toolbar'),
    	$container = $('#content > .container');
    
    $panel.addClass('shown').animate({'margin-left':'0px'});  
    $container.css({'padding-left':'200px'});

}

function hideErrorPanel() {
	
    var $panel = $('#toolbar'),
    $container = $('#content > .container');
    
    $panel.removeClass('shown').animate({'margin-left':'-250px'});  
    $container.css({'padding-left':'15px'});

}
*/


});