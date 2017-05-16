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
    	lang_location: '..',
    	icheck: '/wb/plugins/iCheck/icheck.min'

    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery'],
        'bootstrap.file-input': ['bootstrap.min'],
    	'bootbox': ['bootstrap.min'],
       	'toggle': ['bootstrap.min'],
    	'icheck': ['jquery']
        
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
         'app/question',
         'app/optionlist',
         'app/editorMarkup',
         'app/changeset',
         'app/option',
         'moment',
         'icheck'], 
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
				question,
				optionlist,
				markup,
				changeset,
				option,
				moment) {


var	gMode = "survey",
	gTempQuestions = [],
	$gCurrentRow,			// Currently selected row
	gTempLanguages = [],
	gTempPulldata = [],
	gDragCounter,
	gDragSourceId;

// Media globals
var gUrl,			// url to submit to
	gBaseUrl = '/surveyKPI/upload/media';

// Media Modal Parameters
var gNewVal,
	//gSelFormId,
	//gSelId,
	//gOptionList,
	gQname,
	gElement,
	gNewVal,
	gIsSurveyLevel;

window.moment = moment;

'use strict';

$(document).ready(function() {
  
	var i,
		params,
		pArray = [],
		param = [],
		openingNew = false,
		dont_get_current_survey = true,
		bs = isBusinessServer();
	
	window.bootbox = bootbox;
	
	localise.setlang();		// Localise HTML
	
	// Get the parameters and start editing a survey if one was passed as a parameter
	params = location.search.substr(location.search.indexOf("?") + 1)
	pArray = params.split("&");
	dont_get_current_survey = false;
	for (i = 0; i < pArray.length; i++) {
		param = pArray[i].split("=");
		if ( param[0] === "id" ) {
			dont_get_current_survey = true;		// Use the passed in survey id
			globals.gCurrentSurvey = param[1];
			saveCurrentProject(-1, globals.gCurrentSurvey, undefined);	// Save the current survey id
		} else if ( param[0] === "new" ) {
			dont_get_current_survey = true;		// Don't get any survey details
			globals.gCurrentSurvey = -1;
			openForm("new");
		}
	}
	
	/*
	 * Get location list
	 */
	// Get locations
	getLocations(setLocationList);
	
	/*
	 * Initialise controls in the open form dialog
	 */
	 $('#base_on_existing').click(function () {
		 if($(this).is(':checked')) {
			 $('.reusing_form').show();
				getSurveyForms($('#survey_name').val(), addForms);
		 } else {
			 $('.reusing_form').hide();
		 }
	 });
	 
	 $('#survey_name').change(function(){
		var $this = $(this);
		getSurveyForms($this.val(), addForms);
	 });
	 
	window.history.pushState('',document.title, document.location.origin + document.location.pathname);	// Strip out the parameters from the href
	
	// Get the user details
	globals.gIsAdministrator = false;
	getLoggedInUser(getSurveyList, false, true, undefined, false, dont_get_current_survey);
	getFilesFromServer(gBaseUrl, undefined, refreshMediaView);		// Get the organisational level media files

	/*
	 * Switch between choices list view and question view
	 */
	updateViewControls();
	$('#viewType').change(function() {
		globals.gIsQuestionView = $(this).prop('checked');
		updateViewControls();
		
		refreshForm();
	});
	
	/*
	 * Refresh the view when the selected property changes
	 */
	$('#selProperty a').click(function() {
		$('#propSelected').html($(this).html());
		globals.gSelLabel = $(this).html();
		globals.gSelProperty = $(this).data("prop")
		refreshFeaturedProperties();
		
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
		if(globals.model.survey.blocked) {
			bootbox.alert("The survey has been blocked. Changes cannot be saved.  You can unblock the " +
			"survey on the form management page.");
		} else {
			if(changeset.numberIssues("error") === 0) {
				changeset.save(surveyListDone);
			} else {
				bootbox.alert("Cannot save until errors are fixed");
			}
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
		
		if($(this).closest('li').hasClass('disabled')) {
			bootbox.alert("Cannot modify languages while there are unsaved changes");
		} else {
			gTempLanguages = globals.model.survey.languages.slice();
			updateLanguageView();
			$('#editLanguageModal').modal("show");
		}
	});
	
	$('#m_pulldata').off().click(function() {
		if(globals.model.survey.pulldata) {
			gTempPulldata = globals.model.survey.pulldata.slice();
		} else {
			gTempPulldata = [];
		}
		updatePulldataView();
		$('#pulldataModal').modal("show");
	});
	
	$('#m_required').off().click(function() {
		if($(this).closest('li').hasClass('disabled')) {
			bootbox.alert("Cannot set questions required while there are unsaved changes");
		} else {
			setAllRequired(true);
		}
	});
	
	$('#m_not_required').off().click(function() {
		if($(this).closest('li').hasClass('disabled')) {
			bootbox.alert("Cannot set questions not required while there are unsaved changes");
		} else {
			setAllRequired(false);
		}
	});
	
	$('#addLanguage').off().click(function() {
		gTempLanguages.push({
			id: -1,
			name: "",
			deleted: false
		});
		updateLanguageView();
	});
	
	$('#addPulldata').off().click(function() {
		gTempPulldata.push({
			survey: "",
			data_key: "",
			repeats: false,
			deleted: false
		});
		updatePulldataView();
	});
	
	// Set up view type toggle
	$('#viewType').attr("data-on", localise.set["c_questions"]).attr("data-off", localise.set["c_choices"]).bootstrapToggle();

	// Add menu functions
	$('#m_media').off().click(function() {	// MEDIA
		// Set up media dialog to manage loading and deleting of media
		$('.mediaManage').show();
		$('.mediaSelect').hide();
		$('#mediaModalLabel').html("Manage Media Files For this Form. " +
				"Note organisation level media files now found under <b>Shared Resources</b> in admin module.");
		$('#mediaModal table').off();
		$('#surveyPanel, #orgPanel').find('tr').removeClass('success');
		
		// Make sure all types of media are shown
		$('tr').show();
		// Close any drop downmenus
		$('.dropdown-toggle').parent().removeClass("open");
		$('.navbar-collapse').removeClass("in");
		
		// Only form level media is managed here, organisation level media is managed in the shared resources page
		$('#orgPanel').hide();
		$('#surveyPanel').show();
		gUrl = gBaseUrl + '?sId=' + gSId;
    	$('#survey_id').val(gSId);			// Set the survey id in the forms hidden field
    	gIsSurveyLevel = true;
    	
		$('.upload_file_msg').removeClass('alert-danger').addClass('alert-success').html("");
		$('#mediaModal').modal('show');

	});
	
	$('#m_settings').off().click(function() {	// Show the settings dialog
		
		// Close any drop downmenus
		$('.dropdown-toggle').parent().removeClass("open");
		$('.navbar-collapse').removeClass("in");
		
		updateSettingsData();		
		
		$('#settingsModal').modal('show');
	});


	$('#save_settings').off().click(function() {	// Save settings to the database
		globals.model.save_settings();
	});
	
	$('#m_info').off().click(function() {	// Show the info dialog
		
		var tableNames ="",
			i;
		
		for(i = 0; i < globals.model.survey.forms.length; i++) {
			if(i > 0) {
				tableNames += ", ";
			}
			tableNames += globals.model.survey.forms[i].tableName;
		}
		// Close any drop downmenus
		$('.dropdown-toggle').parent().removeClass("open");
		$('.navbar-collapse').removeClass("in");
		
		$('#i_name').val(globals.model.survey.displayName);
		$('#i_ident').val(globals.model.survey.ident);
		$('#i_version').val(globals.model.survey.version);
		$('#i_created').val(localTime(globals.model.survey.created));		
		$('#i_based_on').val(globals.model.survey.basedOn);	
		$('#i_table_names').val(tableNames);
		$('#i_shared').prop('checked', globals.model.survey.sharedTable);	
		
		$('#infoModal').modal('show');
	});
	
	/*
	 * Save changes to the language list
	 */
	$('#editLanguageSave').off().click(function() {	// Save languages to the database

		var languagesString = JSON.stringify(gTempLanguages);
		addHourglass();
		$.ajax({
			  type: "POST",
			  url: "/surveyKPI/surveys/save_languages/" + gSId,
			  dataType: 'json',
			  cache: false,
			  data: { languages: languagesString },
				success: function(data) {
					removeHourglass();
					$('#editLanguageModal').modal("hide");
					globals.model.survey = data;
					setLanguages(data.languages, refreshForm);
					refreshForm();
				},
				error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						alert("Error: Failed to save languages: " + xhr.responseText);
					}
				}
		});
	});
	
	/*
	 * Save changes to the pulldata settings
	 */
	$('#pulldataSave').off().click(function() {	// Save pulldata to the database

		var pulldataString = JSON.stringify(gTempPulldata);
		addHourglass();
		$.ajax({
			  type: "POST",
			  url: "/surveyKPI/surveys/save_pulldata/" + gSId,
			  cache: false,
			  data: { pulldata: pulldataString },
				success: function(data) {
					removeHourglass();
					globals.model.survey.pulldata = gTempPulldata;
					$('#pulldataModal').modal("hide");
				},
				error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						alert("Error: Failed to save pulldata settings: " + xhr.responseText);
					}
				}
		});
	});
	
	$('#project_name').change(function() {
		globals.gCurrentProject = $('#project_name option:selected').val();
		globals.gCurrentSurvey = -1;
		globals.gCurrentTaskGroup = undefined;
		
		saveCurrentProject(globals.gCurrentProject, 
				globals.gCurrentSurvey, 
				globals.gCurrentTaskGroup);
		
		getSurveyList();
 	 });
	
	// Check for changes in settings
	$('#set_survey_name, #set_instance_name, #set_hrk').keyup(function(){
		globals.model.settingsChange();
	});
	$('#set_project_name').change(function() {
		saveCurrentProject($('#set_project_name option:selected').val(), globals.gCurrentSurvey);	// Save the current project id
		globals.model.settingsChange();
	});
	$('#set_default_language, #set_style').change(function() {
		globals.model.settingsChange();
	});
	$('#task_file').change(function() {
		globals.model.settingsChange();
	});
	$('#timing_data').change(function() {
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
       	if(!$('#submitFiles').hasClass('disabled')) {
       		uploadFiles(gUrl, "fileupload", refreshMediaViewManage, globals.gCurrentSurvey, undefined);
       	}
    });
    
    /*
     * Open a new form
     */
	$('#get_form').off().click(function() {
		
		var name,
			existing,
			existing_survey,
			existing_form,
			shared_results;
		
		if(globals.gCurrentProject > 0) {
			changeset.setHasChanges(0);		// Clear any existing changes from a previous form
			
			if(globals.gExistingSurvey) {
				globals.gCurrentSurvey = $('#survey_name option:selected').val();
				saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the new survey id as the current survey
				getSurveyDetails(surveyDetailsDone);
			} else {
				name = $('#new_form_name').val();
				if(typeof name === "undefined" || name.trim() == "") {
					bootbox.alert("Please specify a name for the new survey");
					return false;
				}
				existing = $('#base_on_existing').prop('checked');
				existing_survey = $('#survey_name').val();
				existing_form = $('#form_name').val();
				shared_results = $('#shared_results').prop('checked');
				createNewSurvey(name, existing, existing_survey, existing_form, shared_results, surveyDetailsDone);
			}
		} else {
			alert(localise.set["msg_val_p"]);
			return false;
		}
	 });
	
	
    /*
     * Save a selected media file
     */
	$('#mediaSelectSave').click(function() {
		var type;
		if(gNewVal) {
			if(globals.gOptionList) {
				type = "option";
			} else {
				type = "question";
			}
			updateLabel(type, globals.gFormIndex, globals.gSelOptionId, globals.gOptionList, gElement, gNewVal, gQname, "media");
		}
	});
	
	$('#removeMedia').click(function() {
		var type;
		if(globals.gOptionList) {
			type = "option";
		} else {
			type = "question";
		}
		updateLabel(type, globals.gFormIndex, globals.gSelOptionId, globals.gOptionList, gElement, undefined, gQname, "media");
		
	});
	
	setupQuestionTypes($('#dialog_types'), 2, false, undefined);		// Double column, not draggable for change type dialog
	setupQuestionTypes($('#toolbar_types'), 1, true, undefined);		// Single column, draggable for toolbar
	
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
	
	/*
	 * Choice Editing
	 */
	$('#filterModalSave').off().click(function(){
		var survey = globals.model.survey,
			question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
		
		option.addFilter($('#filter_name').val());
		option.addOptionTable(question, globals.gFormIndex, globals.gListName);
		option.setupChoiceView($('#filterType').val());
		option.addFilterSelectList(survey.filters);
		respondToEventsChoices($('#optionTable'));
	});
	
	$("#filterModal").on("shown.bs.modal", function() {
	    $("#filter_name").focus();
	});
	
	$('#content').on('shown.bs.collapse', function (e) {
		$('a[href="#' + e.target.id + '"]', '#content').find('.edit_icon').removeClass('glyphicon-chevron-down').addClass('glyphicon-chevron-up');
	});
	
	$('#content').on('hidden.bs.collapse', function (e) {
		$('a[href="#' + e.target.id + '"]', '#content').find('.edit_icon').removeClass('glyphicon-chevron-up').addClass('glyphicon-chevron-down');
	});
	
	
});

/*
 * Set all the questions to either required or not required
 */
function setAllRequired(required) {
	
	addHourglass();
	$.ajax({
		  type: "POST",
		  cache: false,
		  url: "/surveyKPI/surveys/set_required/" + gSId + "/" + (required ? "true" : "false"),
			success: function(data) {
				removeHourglass();
				getSurveyDetails(surveyDetailsDone);
				var msg = "Questions set as " + (required ? "required" : "not required");
				bootbox.alert(msg);
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					var msg = "Error: Failed to set questions as " + (required ? "required" : "not required") + " " + xhr.responseText;
					alert(msg);
				}
			}
	});
}

//Set up question type dialog
function setupQuestionTypes($elem, columns, draggable, currentType) {
	var i,
		types = globals.model.qTypes,
		h = [],
		idx = -1,
		count;
	
	h[++idx] = '<div class="row margin-bottom">';
	for(i = 0; i < types.length; i++) {
		
		if(types[i].canSelect && isCompatible(types[i].compatTypes, currentType) ) {
			
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

/*
 * return true if the current published type can be converted to the new type
 */
function isCompatible(compatTypes, currentType) {
	var compatible = false;
	if(!currentType) {
		compatible = true;
	} else {
		if(compatTypes && compatTypes.indexOf(currentType) >= 0) {
			compatible = true;
		}
	}
	return compatible;
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
	
	// Show message if the survey is blocked
	if(globals.model.survey.blocked) {
		bootbox.alert("The survey has been blocked. Changes cannot be saved.  You can unblock the " +
				"survey on the form management page.");
	}
	
	/*
	 * Refresh the form
	 */
	if(globals.gShowingChoices) {
		// skip the refresh of the choices as when the data was reloaded the item index may have changed hence we can't be guaranteed which question will be refreshed
		// Safer to return to the question view
		globals.gShowingChoices = false;
		updateViewControls();
		
		/*
		globals.gSelChoiceProperty = globals.gSelProperty;	// Restore selProperty and selLabel for questions
		globals.gSelProperty = globals.gSelQuestionProperty;
		globals.gSelChoiceLabel = globals.gSelLabel;	
		globals.gSelLabel = globals.gSelQuestionLabel;
		$('#propSelected').html(globals.gSelLabel);
		*/
		
		$('.editorContent').toggle();
		$('.notoptionslist').show();
	} 
	refreshForm();
	
	// Set up link to test file
	$('.m_test_survey').attr("href", "/webForm/s" + globals.gCurrentProject + "_" + globals.gCurrentSurvey);
	
}

/*
 * Refresh the options
 */
function refreshOptions() {
	
	var $context = option.createChoiceView(),
		survey,
		question;

	// Set the previous choices list box
	var prevListName = $('#previousSelect').val();
	if(prevListName) {
		option.setPreviousChoices(prevListName);
	}
	
	// Show the table of options
	if(typeof globals.gFormIndex !== "undefined" && typeof globals.gItemIndex !== undefined) {
		// opened from question
		survey = globals.model.survey,
		question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
	}
	
	option.addOptionTable(question, globals.gFormIndex, globals.gListName);
	option.setupChoiceView($('#filterType').val());
	
	respondToEventsChoices($context);
}

/*
 * Show the form on the screen
 */
function refreshForm() {
	
	var $context,
		survey,
		question;
	
	if(globals.gShowingChoices) {
		survey = globals.model.survey;
		if(typeof globals.gFormIndex !== "undefined") {
			question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
		}
		option.addOptionTable(question, globals.gFormIndex, globals.gListName);
		option.setupChoiceView($('#filterType').val());
		respondToEventsChoices($('#optionTable'));
	} else {
		$context = markup.refresh();
		respondToEvents($context);
	}

}

			/*
			 * Refresh the featured properties part of the form
			 */
            function refreshFeaturedProperties() {

                var $context,
                    survey,
                    question;

                if(globals.gShowingChoices) {
                    survey = globals.model.survey;
                    if(typeof globals.gFormIndex !== "undefined") {
                        question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
                    }
                    option.addOptionTable(question, globals.gFormIndex, globals.gListName);
                    option.setupChoiceView($('#filterType').val());
                    respondToEventsChoices($('#optionTable'));
                } else {
                    $context = markup.refreshFeaturedProperties();
                    respondToEvents($context);
                }

            }

/*
 * The passed in context is for a list of choices
 */
function respondToEventsChoices($context) {
	
	$('[type="checkbox"]', $context).iCheck({
	    checkboxClass: 'icheckbox_square-green',
	    radioClass: 'iradio_square-green'
	});
	
	$('.exitOptions', $context).off().click(function() {
		
		globals.gShowingChoices = false;
		updateViewControls();
		
		/*
		globals.gSelChoiceProperty = globals.gSelProperty;	// Restore selProperty and selLabel for questions
		globals.gSelProperty = globals.gSelQuestionProperty;
		globals.gSelChoiceLabel = globals.gSelLabel;	
		globals.gSelLabel = globals.gSelQuestionLabel;
		$('#propSelected').html(globals.gSelLabel);
		*/
		
		$('.editorContent').toggle();
		$('.notoptionslist').show();
		refreshForm();
	});
	
	$('#addFilter', $context).off().click(function() {
		$('#filterModal').modal('show');	
	});
	
	// Set option list value
	$context.find('.option-lists', $context).each(function(index){
		var $this = $(this),
			$elem = $this.closest('.question_head'),
			formIndex = $elem.data("fid"),
			itemIndex = $elem.data("id"),
			survey = globals.model.survey,
			question;
		
		question = survey.forms[formIndex].questions[itemIndex];
		if(!optionListExists(question.list_name)) {
			if(!optionListExists(question.name)) {
				survey.optionLists[question.name] = {
						oSeq: [],
						options: []
					};
					option.refreshOptionListControls();
			}
			$this.val(question.name);
		} else {
			$this.val(question.list_name);
		}
		
	});
	
	
	$('#filterType', $context).off().change(function() {
		var $this = $(this),
			survey = globals.model.survey,
			question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex],
			filterType = $this.val(),
			choiceFilter,
			proceed = true;

		choiceFilter = $('#choiceFilter').val();
		
		// Show an error and set the filter to none if the user chose cascade when there are no previous select questions
		if (filterType === "cascade" && $('#previousSelect option').length == 0) {
			alert("Error; There are no previous select questions to get values from. You may want to set a cutom filter.");
			filterType = "none";
			$('#filterType').val(filterType);
		}
		
		if(filterType != "custom") {
			if(choiceFilter && choiceFilter.indexOf("_smap_cascade") < 0) {
				proceed = confirm(localise.set["msg_rep_f"] + ": " + choiceFilter + "?");
			}
		}
		
		if(proceed) {
			if(filterType === "cascade") {
				setCascadeFilter();
				option.addFilter("_smap_cascade");  // Make sure _smap_cascade is in the list of filters
			} if(filterType === "none") {
				setNoFilter();
			}
		} 
		
		if(filterType !== "none") {
			option.addOptionTable(question, globals.gFormIndex, globals.gListName);
			respondToEventsChoices($('#optionTable'));
		}
		option.setupChoiceView($this.val());
		
		
	});
	
	// Respond to columns of filters being hidden or made visible
	$('input', '#custom_filters').off().on('ifToggled', function(event) {
		var $this = $(this),
			survey = globals.model.survey,
			setting = $this.is(':checked');
		
		survey.filters[$this.val()] = setting;
		option.resetFilterColumns();
		
	});

	
	// Option list change
	$context.find('.option-lists').off().change(function(){
		var $this = $(this),
			$elem = $this.closest('.question_head'),
			formIndex = $elem.data("fid"),
			itemIndex = $elem.data("id");
	
		updateLabel("question", formIndex, itemIndex, undefined, "text", $this.val(), undefined, "list_name");
	});
	
	// Choice filter change
	$context.find('#choiceFilter').off().change(function(){
		var $this = $(this),
			$elem = $this.closest('.question_head'),
			formIndex = $elem.data("fid"),
			itemIndex = $elem.data("id");
	
		updateLabel("question", formIndex, itemIndex, undefined, "text", $this.val(), undefined, "choice_filter");
	});
	
	// Previous question for cascading select changes
	$context.find('#previousSelect').off().change(function(){
		var $this = $(this),
			survey = globals.model.survey,
			question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
		
		option.setPreviousChoices($this.val());	
		option.addOptionTable(question, globals.gFormIndex, globals.gListName);
		respondToEventsChoices($('#optionTable'));
	});
	
	// Previous choice for cascading select changes
	$context.find('#previousSelectChoice').off().change(function() {
		var $this = $(this),
			survey = globals.model.survey,
			question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
		
		option.addOptionTable(question, globals.gFormIndex, globals.gListName);
		respondToEventsChoices($('#optionTable'));
	});
	
	// Add tooltips
	$context.find('.has_tt').tooltip();
	

	// Respond to clicks on a label text area
	$context.find('.labelProp').change(function() {

		var $this = $(this),
			$elem = $this.closest('tr'),
			formIndex = $elem.data("fid"),
			itemIndex = $elem.data("id"),
			newVal = $this.val(),
			optionList = $elem.data("list_name"),
			qname = $elem.data("qname");
		
		updateLabel("option", formIndex, itemIndex, optionList, "text", newVal, qname, "label"); 

	});
	
	// Fix issues with dragging and selecting text in text area or input when draggable is set
	// Mainly a problem with Firefox however in Chrome selecting text by dragging does not work
	// Refer: http://stackoverflow.com/questions/21680363/prevent-drag-event-to-interfere-with-input-elements-in-firefox-using-html5-drag
	$context.find('input, textarea').focusin(function() {
		$(this).closest('.draggable').prop("draggable", false);
	}).blur(function() {
        $(this).closest('.draggable').prop("draggable", true);
        console.log("blur");
    });

	// validate the option name
	$context.find('.oname').keyup(function(){

		var $this = $(this),
			$elem = $this.closest('tr'),
			formIndex = $elem.data("fid"),
			itemIndex = $elem.data("id"),
			listName = $elem.data("list_name"),
			newVal = $this.val();
		
		changeset.validateName(listName, itemIndex, newVal, "option", true);
		changeset.updateModelWithErrorStatus(listName, itemIndex, "option");		// Update model and DOM

	});
	
	
	// Update the option name
	$context.find('.oname').change(function(){

		var $this = $(this),
			$elem = $this.closest('tr'),
			listName = $elem.data("list_name"),
			formIndex = $elem.data("fid"),
			itemIndex = $elem.data("id"),
			qname = $elem.data("qname"),
			newVal = $this.val();
		
		updateLabel("option", formIndex, itemIndex, listName, "text", newVal, qname, "value") ;
		
	});
	
	// Update the filter values when a custom filter value is changed
	$context.find('.filter').change(function(){
		updateFilterValues($(this), false, undefined);
	});
	
	// Update the cascade filter values when a cascade filter value is checked
	$('tr'). find('.cascadeFilter').off().on('ifToggled', function(event) {
		var $this = $(this),
		survey = globals.model.survey;
	
		updateFilterValues($(this), true, $this.is(':checked'));
	
	});
	
	// Add new option after
	$context.find('.add_option_after').off().click(function() {
		var $this = $(this).closest('.editor_element');
		addNewOption($this, "after");
	});
	
	// Add new option before
	$context.find('.add_option_before').off().click(function() {	
		var $this = $(this).closest('.editor_element');
		addNewOption($this, "before");
	});
	
	// Add new option using the "Add New Choice" button
	$context.find('.add_option').off().click(function() {
		var $this = $(this);

		addNewOption($this, "end");
	
	});
	
	// Delete option
	$context.find('.delete_option').off().click(function() {
		var $this = $(this),
			$context,						// Updated Html
			index = $this.closest(".editor_element").data("id"),
			list_name = $this.closest(".editor_element").data("list_name");
		
		bootbox.confirm(localise.set["msg_del_c"], function(result) {
			if(result) {
				$context = question.deleteOption(index, list_name);
			}
		}); 
		
		
	});
	
	// Selected a media property
	$context.find('.mediaProp').off().click(function(){
		
		var $this = $(this);
		mediaPropSelected($this);

	});

	/*
	 * Enable drag and drop to move choices
	 * 
	 * First add handlers for draggable components
	 */
	$('.draggable.option').prop('draggable', 'true')
	
	.off('dragstart')
	.on('dragstart', function(evt){
		var ev = evt.originalEvent,
			$elem = $(ev.target);
		
		ev.effectAllowed = "move";		// Only allow move, TODO copy
		gDragCounter = 0;
		

		ev.dataTransfer.setData("text", $elem.closest('li').data('id'));
		gDragSourceId = $elem.closest('tr').data('id');
		
		console.log("Draggable item id: " + $elem.closest('tr').data('id'));
		//$('.dropon.add_option').addClass("add_drop_button").removeClass("add_button");
		
		return true;
	})
	
	// clean up after drag
	.off('dragend')
	.on('dragend', function(evt){
		//$('.dropon.add_option').addClass("add_button").removeClass("add_drop_button").removeClass("over_drop_button");
		return false;
	})
	
	// Don't allow a draggable component to be dropped onto a text field in some other option
	.off('drop')
	.on('drop', function(evt){
		evt.originalEvent.preventDefault();
	});
	
	/*
	 * Handle drop on or dragging over a drop zone
	 */
	// Entering a drop zone
	$('.dropon.option')
	
	.off('dragenter')
	.on('dragenter', function(evt){
		var ev = evt.originalEvent,
			$elem = $(ev.target),	
			targetId = $elem.closest('tr').data('id'),
			btnId = $elem.data('id');
		
		$('tr', '#choiceView').removeClass("over_drop_elem");
		if(typeof(targetId) !== "undefined" && targetId != gDragSourceId) {
			ev.preventDefault();	
			$elem.closest('tr').addClass("over_drop_elem");
		} else if(typeof(btnId) !== "undefined" && btnId == -1) {
			ev.preventDefault();
			$elem.addClass("over_drop_button").removeClass("add_button");
		}
	
	})
	
	// Leaving a drop zone
	.off('dragleave')
	.on('dragleave', function(evt){
		
		var ev = evt.originalEvent,
			$elem = $(ev.target),
			targetId = $elem.closest('tr').data('id'),
			btnId = $elem.data('id');
		
		if(typeof(btnId) !== "undefined" && btnId == -1) {
			$elem.addClass("add_button").removeClass("over_drop_button");
		} else if(typeof(targetId) === "undefined") {
			$('tr', '#choiceView').removeClass("over_drop_elem");
		} 
		
		
	})
	
	.off('dragover')
	.on('dragover', function(evt){
		evt.originalEvent.dataTransfer.dropEffect = "move";
		evt.originalEvent.preventDefault();
		evt.originalEvent.stopPropagation();
	})
	
	// Drop the option
	.off('drop')
	.on('drop', function(evt){
		var ev = evt.originalEvent,
			$elem = $(ev.target),
			$targetElem = $elem.closest('tr'),
			$sourceElem,
			sourceId = gDragSourceId,
			targetId = $targetElem.data('id'),
			btnId = $elem.data('id'),
			listName = $targetElem.data('list_name'),
			$context,
			$elemBeforeTarget = $targetElem.prev('tr'),
			elemBeforeTargetId = $elemBeforeTarget.data('id'),
			$choiceBeforeButton = $("tr", "#choiceView").last();
	
		ev.preventDefault();
		ev.stopPropagation();
		 
		
		$('tr', '#choiceView').removeClass("over_drop_elem");		
		if(sourceId === targetId || sourceId === elemBeforeTargetId) {
			// Dropped on itself do not move
		} else {
			
			if(btnId === -1) {
				// Dropped on final add choice button
				targetId = $choiceBeforeButton.data('id');
				listName = $choiceBeforeButton.data('list_name')
				$context = question.moveBeforeOption(listName, sourceId, listName, targetId, "after");
			} else {
				$context = question.moveBeforeOption(listName, sourceId, listName, targetId, "before");
			}
			respondToEventsChoices($context);			// Add events on to the altered html
		}
	});
}

/*
 * Add a new option
 */
function addNewOption($elem, locn) {
	var oId =  $elem.data("id"),
		fId =  $elem.data("fid"),
		qname = $elem.data("qname"),
		list_name = $elem.data("list_name");


	var $context = question.addOption(oId, locn, list_name, fId, qname);
	
	respondToEventsChoices($context);				// Add events on to the altered html
	
	// Set focus to the new option
	$context.find('textarea').focus();			// Set focus to the new option
}

/*
 * The passed in context is for a list item containing either a question or an option
 */
function respondToEvents($context) {
	
	// Open choices for editing
	$('.edit_choice', $context).off().click(function(index){
		var $this = $(this),
			$li = $this.closest('li'),
			$context,
			survey,
			question;
			
		// Set global variables that will be used if the contents of this dialog are refreshed
		globals.gListName = $li.data("list_name");
		globals.gFormIndex = $li.data("fid");
		globals.gItemIndex = $li.data("id");
		globals.gShowingChoices = true;
		
		$context = option.createChoiceView();

		// Set the previous choices list box
		var prevListName = $('#previousSelect').val();
		if(prevListName) {
			option.setPreviousChoices(prevListName);
		}
		
		// Show the table of options
		if(typeof globals.gFormIndex !== "undefined" && typeof globals.gItemIndex !== undefined) {
			// opened from question
			survey = globals.model.survey,
			question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
		}
		
		option.addOptionTable(question, globals.gFormIndex, globals.gListName);
		option.setupChoiceView($('#filterType').val());
		
		respondToEventsChoices($context);
		updateViewControls();

		/*
		globals.gSelQuestionProperty = globals.gSelProperty;	// Restore selProperty and selLabel for options
		globals.gSelProperty = globals.gSelChoiceProperty;
		globals.gSelQuestionLabel = globals.gSelLabel;	
		globals.gSelLabel = globals.gSelChoiceLabel;
		$('#propSelected').html(globals.gSelLabel);
		*/
		
		$('.editorContent').toggle();
		$('.notoptionslist').hide();
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
			newVal,		
			type,
			optionList = $li.data("list_name"),
			qname = $li.data("qname"),
			labelType;
		
		if($li.hasClass("option")) {
			type = "option";
		} else {
			type = "question";
		}

		labelType = prop === "hint" ? "hint" : "text";
		if(prop === "required") {
			newVal = $this.hasClass("prop_no");		// If set false then newVal will be true
		} else if (prop === "autoplay") {
			newVal = $this.val();
		} else if (prop === "linked_survey") {
			if($this.hasClass("prop_no")) {
				newVal = $this.closest('.row').find(".labelSelect").val();
			} else {
				newVal = undefined;
			}
		} else if(prop === "readonly") {
			newVal = $this.hasClass("prop_no");		// If set false then newVal will be true
		}
		updateLabel(type, formIndex, itemIndex, optionList, labelType, newVal, qname, prop); 

	});
	
	// Respond to changes on a label select
	$context.find('.labelSelect').off().change(function() {

		var $this = $(this),
			prop = $this.data("prop"),
			$li = $this.closest('li'),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id"),
			newVal,		
			type,
			optionList = $li.data("list_name"),
			qname = $li.data("qname"),
			labelType;
		
		if($li.hasClass("option")) {
			type = "option";
		} else {
			type = "question";
		}

		labelType = prop === "hint" ? "hint" : "text";
		if (prop === "linked_survey") {
			newVal = $this.val();
			
			updateLabel(type, formIndex, itemIndex, optionList, labelType, newVal, qname, prop); 
		} 
		

	});
	
	// Respond to clicks on a label text area
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

		var labelType = prop === "hint" ? "hint" : "text";
		updateLabel("question", formIndex, itemIndex, optionList, labelType, newVal, qname, prop); 

	});
	
	// validate the name on focus as duplicates may have been removed elsewhere
	$context.find('.qname').focusin(function(){

		var $this = $(this),
			$li = $this.closest('li'),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id");
		
		changeset.validateItem(formIndex, itemIndex, "question", true); 

	});
	
	// Fix issues with dragging and selecting text in text area or input when draggable is set
	// Mainly a problem with Firefox however in Chrome selecting text by dragging does not work
	// Refer: http://stackoverflow.com/questions/21680363/prevent-drag-event-to-interfere-with-input-elements-in-firefox-using-html5-drag
	$context.find('input, textarea').focusin(function() {
		$(this).closest('.draggable').prop("draggable", false);
	}).blur(function() {
        $(this).closest('.draggable').prop("draggable", true);
        console.log("blur");
    });
	
	// On tab in question name move to the feature input
	$context.find('.qname').keydown(function(e){
		if(e.keyCode === 9) {
			e.preventDefault();
			$(this).closest('.row').find('.labelProp').focus();
		}
	});

	
	// validate the question name on every character change
	$context.find('.qname').keyup(function() {

		var $this = $(this),
			$li = $this.closest('li'),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id"),
			newVal = $this.val();
		
		changeset.validateName(formIndex, itemIndex, newVal, "question", true);
		changeset.updateModelWithErrorStatus(formIndex, itemIndex, "question");		// Update model and DOM

	});
	
	// Update the question name
	$context.find('.qname').change(function(){

		if(globals.gSaveInProgress) {
			return;
		}
		
		var $this = $(this),
			$li = $this.closest('li'),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id"),
			newVal = $this.val();
		
		updateLabel("question", formIndex, itemIndex, undefined, "text", newVal, undefined, "name") ;

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
		
		if(globals.gSaveInProgress) {
			return;
		}
		
		addQuestion($this, "string");


	});
	
	/*
	 * Add a new choice list 
	 */
	$context.find('.add_option_list').off().click(function() {
		var $this = $(this),
			$context,
			justAddedId,
			val;
		
		if(globals.gSaveInProgress) {
			return;
		}
		
		console.log("adding choices list");
		$context = optionlist.add();
		respondToEvents($context);
		
		// Set focus to the new option list
		var justAddedID = '#ol_' + globals.gLatestOptionList;
		var $input = $('input', justAddedID);
		val = $input.val();
		$input.val("").focus().val(val);		// Set text entry to end of text field	

	});
	
	// Delete question
	$context.find('.delete_question').off().click(function() {
		var $this = $(this),
			$context,						// Updated Html
			item = $(this).data("id");
		
		if(globals.gSaveInProgress) {
			return;
		}
		
		bootbox.confirm(localise.set["msg_del_q"], function(result) {
			if(result) {
				question.deleteQuestion(item);
			}
		}); 
		
	});
	
	// validate the optionlist name
	$context.find('.olname').keyup(function(){

		var $this = $(this),
			$elem = $this.closest('.question'),
			itemIndex = $elem.prop("id"),
			listName = $elem.data("list_name"),
			newVal = $this.val();
		
		changeset.validateName(listName, itemIndex, newVal, "optionlist", true);
		changeset.updateModelWithErrorStatus(listName, itemIndex, "optionlist");		// Update model and DOM

	});
	
	// Update the option list name
	$context.find('.olname').change(function(){

		var $this = $(this),
			$li = $this.closest('.question'),
			oldVal = $li.data("list_name"),
			newVal = $this.val();
		
		if(globals.gSaveInProgress) {
			return;
		}
		
		// Only apply the update if there is no error on this option list
		if(!$li.hasClass("error")) {
			$li.data("list_name", newVal);	// First update the HTML
			//$('button.add_option',$li).data("list_name", newVal).removeClass('l_' + oldVal)
			//	.addClass('l_' + newVal);
			updateLabel("optionlist", undefined, undefined, undefined, "text", newVal, oldVal, "name") ;
		}

	});
	
	// Delete option list
	$context.find('.delete_ol').off().click(function() {
		var $this = $(this),
			$context,						// Updated Html
			item = $(this).data("id");
		
		if(globals.gSaveInProgress) {
			return;
		}
		
		bootbox.confirm(localise.set["msg_del_cl"], function(result) {
			if(result) {
				$context = optionlist.deleteList(item);
				respondToEvents($context);		// The entire view is refreshed after deleting an option list
			}
		}); 
		
	});
	
	// Select types
	$context.find('.question_type').off().click(function() {
		
		var $this = $(this),
			$questionElement = $this.closest('li'),
			published,
			survey = globals.model.survey,
			name,
			formIndex,
			itemIndex;
		
		if(globals.gSaveInProgress) {
			return;
		}
		
		formIndex = $questionElement.data("fid");
		itemIndex = $questionElement.data("id");
		
		published = survey.forms[formIndex].questions[itemIndex].published;
		if($this.hasClass("disabled")) {
			alert("You cannot change the type of a question that has an invalid name");
		} else {
			if(published) {
                setupQuestionTypes($('#dialog_types'), 1, false, survey.forms[formIndex].questions[itemIndex].type);
			} else {
                setupQuestionTypes($('#dialog_types'), 2, false, undefined);
			}
			$('.question_type_sel', '#dialog_types').off().click(function(){
				var type = $(this).val();
				
				updateLabel("question", formIndex, itemIndex, undefined, "text", type, undefined, "type");
				$('#typeModal').modal('hide');
				
				// Add an end group question if a new group has been created
				if(type === "begin group") {
					name = survey.forms[formIndex].questions[itemIndex].name + "_groupEnd" ;
					$context = question.add(formIndex,
							$questionElement.attr("id"), 
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
	$('.draggable').prop('draggable', 'true')
	
	.off('dragstart')
	.on('dragstart', function(evt){
		var ev = evt.originalEvent;
		
		ev.effectAllowed = "move";		// Only allow move, TODO copy
		
		if(typeof ev.target.value !== "undefined" && ev.target.value.length > 0) {
			ev.dataTransfer.setData("type", ev.target.value);
		} else {	
			if(ev.target.id === "") {	// Moving an option
				ev.dataTransfer.setData("list_name", ev.target.dataset.list_name);
				ev.dataTransfer.setData("index", ev.target.dataset.id);
			} else {	// Moving a question
				ev.dataTransfer.setData("text/plain", ev.target.id);
			}
		}
		$('.dropon.add_question').addClass("add_drop_button").removeClass("add_button");
		
		return true;
	})
	
	// clean up after drag
	.off('dragend')
	.on('dragend', function(evt){
		$('.dropon.add_question').addClass("add_button").removeClass("add_drop_button").removeClass("over_drop_button");
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
	$('.dropon.add_question')
	
	.off('dragenter')
	.on('dragenter', function(evt){
		var ev = evt.originalEvent,
			$elem = $(ev.target),	
			targetId = $elem.data('qid');
		
		$elem.addClass("over_drop_button").removeClass("add_button").addClass("add_drop_button");
	
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
	
	// Drop the question or type
	.off('drop')
	.on('drop', function(evt){
		var ev = evt.originalEvent,
			$targetListItem = $(ev.target),
			$sourceElem,
			sourceId = ev.dataTransfer.getData("text/plain"),
			sourceValue = ev.dataTransfer.getData("type"),		// The type of a new question that is being dropped
			sourceListName = ev.dataTransfer.getData("list_name"),
			sourceItemIndex = ev.dataTransfer.getData("index"),
			targetId = $targetListItem.data('qid'),
			formIndex,
			locn = $targetListItem.data("locn"),			// Before or after the target question
			targetListName,									// For option
			targetItemIndex,								// For option
			sourceListName,									// For option
			sourceItemIndex,								// For option
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
			addQuestion($targetListItem, sourceValue);
		} else {
			
			if($targetListItem.hasClass('add_question')) {
				type = "question";
				
				formIndex = $targetListItem.data("findex");
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
				
				if(sourceId != targetId) {
					
					console.log("Dropped: " + sourceId + " : " + targetId + " : " + sourceValue);
					
					$context = question.moveQuestion(formIndex, sourceId, targetId, locn);
					respondToEvents($context);						// Add events on to the altered html
				}
			} 
			
			/*
			else {
				type = "option";
				
				targetListName = $targetListItem.data("list_name");
				targetItemIndex = $targetListItem.data("index");
				
				if(sourceListName === targetListName && sourceItemIndex === targetItemIndex) {
					// Dropped on itself do not move
				} else {
					
					console.log("Dropped option: " + sourceListName + " : " + sourceItemIndex + 
							" : " + targetListName + " : " + targetItemIndex);
					
					$context = question.moveBeforeOption(sourceListName, sourceItemIndex, 
							targetListName, targetItemIndex, locn);
					respondToEvents($context);						// Add events on to the altered html
				}
			}
			*/

			
		}
			
	
	});

	
}

/*
 * End of drag and drop
 */

function mediaPropSelected($this) {
	
	var $elem = $this.closest('li'),
		$immedParent = $this.closest('div');
	
	if(!$elem.hasClass("question")) {
		$elem = $this.closest('tr');
	}
	// Set up media view
	gElement = $this.data("element");
	globals.gFormIndex = $elem.data("fid");
	globals.gSelOptionId = $elem.data("id");
	globals.gOptionList = $elem.data("list_name"); 		// Option list only used with choices which are in a table
	gQname = $elem.data("qname"); 
	$gCurrentRow = $elem;
	
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
	var $empty = $immedParent.find('.emptyMedia');
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
	
	$('.upload_file_msg').removeClass('alert-danger').addClass('alert-success').html("");
	$('#mediaModal').modal('show');

}

/*
 * Add a new question after an add new question button identified by $this is selected
 */
function addQuestion($this, type) {
	var $context,						// Updated HTML
		survey = globals.model.survey,
		prop = $('#selProperty').val(),
		qId,
		$related,
		$li,
		formIndex = $this.data("findex"),
		itemIndex,
		forms,
		justAddedQuestionId,
		availableGroups,
		$textArea,
		textAreaVal,
		locn = $this.data("locn");	// Add before or after the element id referenced by qIdx
	
	if(!survey) {
		alert(localise.set["ed_ns"]);
	} else {
		forms = survey.forms;
		
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
			$context = question.add(formIndex, qId, locn, type, undefined);
		}
		
		respondToEvents($context);				// Add events on to the altered html
		if($context.attr("id") !== "formList") {
			respondToEvents($context.prev());		// Add events on the "insert before" button
		}
		
		// Set focus to the new question
		var justAddedQuestionID = '#question' + formIndex +  '_' + (forms[formIndex].questions.length - 1);
		$textArea = $('textarea', justAddedQuestionID);
		textAreaVal = $textArea.val();
		$textArea.val("").focus().val(textAreaVal);		// Should set text entry to end of text field	
		
		// Add an end group question if a new group has been created
		if(type === "begin group") {
			itemIndex = forms[formIndex].questions.length - 1;
			var name = survey.forms[formIndex].questions[itemIndex].name + "_groupEnd" ;
			$context = question.add(formIndex, "question" + formIndex + "_" + itemIndex, 
					"after", 
					"end group",
					name);
			respondToEvents($context);	
		}
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
	$('#set_style').val(globals.model.survey.surveyClass);
	$('.upload_file_msg').val(globals.model.survey.pdfTemplateName);
	$('#set_hrk').val(globals.model.survey.hrk);
	$('#task_file').prop('checked', globals.model.survey.task_file);
	$('#timing_data').prop('checked', globals.model.survey.timing_data);
}


/*
 * Update the language modal view
 */
function updateLanguageView() {
	var i,
		$selector = $('#language_edit_list'),
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
		
		if(!languages[i].deleted) {
			h[++idx] = '<tr>';
			
			// name
			h[++idx] = '<td>';
			h[++idx] = '<input type="text" data-idx="';
			h[++idx] = i;
			h[++idx] = '" required class="form-control" value="';
			h[++idx] = languages[i].name;
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
	}
	
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';

	$selector.empty().append(h.join(''));

	$(".rm_language", $selector).click(function(){
		var idx = $(this).data("idx");
		gTempLanguages[idx].deleted = true;
		updateLanguageView();
	});

	$("input", $selector).change(function(){
		var idx = $(this).data("idx");
		gTempLanguages[idx].name = $(this).val();
		updateLanguageView();

	});
	
	
}

/*
 * Update the pulldata modal view
 */
function updatePulldataView() {
	var i,
		$selector = $('#pulldata_edit_list'),
		pulldata = gTempPulldata,
		h = [],
		idx = -1;
	

	h[++idx] = '<table class="table">';
	h[++idx] = '<thead>';
	h[++idx] = '<tr>';
	h[++idx] = '<th>' + localise.set["c_survey"], + '</th>';
	h[++idx] = '<th>' + localise.set["ed_dk"] + '</th>';
	h[++idx] = '<th>' + localise.set["c_repeats"] + '</th>';
	h[++idx] = '<th>' + localise.set["c_ident"] + '</th>';
	h[++idx] = '</tr>';
	h[++idx] = '</thead>';
	h[++idx] = '<tbody class="table-striped">';

	for(i = 0; i < pulldata.length; i++) {
		
		if(!pulldata[i].deleted) {
			h[++idx] = '<tr>';
			
			// Survey
			h[++idx] = '<td>';
			h[++idx] = '<input type="text" data-idx="';
			h[++idx] = i;
			h[++idx] = '" required class="form-control pd_survey" value="';
			h[++idx] = pulldata[i].survey;
			h[++idx] = '"';
			h[++idx] = '</td>';
			
			// Data Key
			h[++idx] = '<td>';
			h[++idx] = '<input type="text" data-idx="';
			h[++idx] = i;
			h[++idx] = '" required class="form-control pd_data_key" value="';
			h[++idx] = pulldata[i].data_key;
			h[++idx] = '"';
			h[++idx] = '</td>';
			
			// Repeats
			h[++idx] = '<td>';
		      h[++idx] = '<input type="checkbox" class="pd_repeats" data-idx="';
		      h[++idx] = i;
		      h[++idx] = '" ';
		      if(pulldata[i].repeats) {
		    	  h[++idx] = 'checked=true ';
		      }
		      h[++idx] = 'value="';
		      h[++idx] = '';
		      h[++idx] = '"> ';
			h[++idx] = '</td>';
			
			// Identifier
			h[++idx] = '<td>';
			h[++idx] = '<input type="text" data-idx="';
			h[++idx] = i;
			h[++idx] = '" readonly class="form-control" value="';
			h[++idx] = "linked_s_pd_" + pulldata[i].survey;
			h[++idx] = '"';
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
	}
	
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';

	$selector.empty().append(h.join(''));

	$(".rm_pulldata", $selector).click(function(){
		var idx = $(this).data("idx");
		gTempPulldata[idx].deleted = true;
		updatePulldataView();
	});

	$(".pd_survey", $selector).change(function(){
		var idx = $(this).data("idx");
		gTempPulldata[idx].survey = $(this).val();
		updatePulldataView();
	});
	
	$(".pd_data_key", $selector).change(function(){
		var idx = $(this).data("idx");
		gTempPulldata[idx].data_key = $(this).val();
		updatePulldataView();
	});
	
	$(".pd_repeats", $selector).change(function(){
		var idx = $(this).data("idx");
		gTempPulldata[idx].repeats = $(this).prop('checked');
		updatePulldataView();
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
		questionType,
		repeat_path,
		oldVal,
		i,
		question;
	
	if(type === "question") {
		question = survey.forms[formIndex].questions[itemIndex];
		questionType = question.type;
	}
	
	if(type === "optionlist") {
		oldVal = qname;
	}
	
	/*
	 * If the question type is a calculate then the label will contain the calculation unless the
	 * property type is type, name or linked_survey or display name
	 */
	if(typeof questionType !== "undefined"
			&& questionType === "calculate"
			&& prop !== "name"
        	&& prop !== "type"
			&& prop !== "linked_survey"
			&& prop !== "display_name"
			&& prop !== "appearance") {	// Whatever the property for a calculation type the label field contains the calculation expression
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
				break;
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
				type: type,					// question or option or optionlist
				name: undefined,			// name of the question or the option value
				propType: element,			// text or hint or image or video or audio or video
				prop: prop,					// Property to be changed, for example: label or appearance
				languageName: undefined,	// Language Name
				allLanguages: false,		// Set true if all languages should be updated with a new label
				repeat_path: repeat_path,	// Path to repeat count question if this is a begin repeat
				
				newVal: newVal,				// New value to be applied
				oldVal: oldVal,			// Old value for this property
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
		// Do not set focus on change of label, the user has just clicked out of this label text area
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

/*
 * Return true if the option list exists
 */
function optionListExists(list) {
	var optionLists = globals.model.survey.optionLists;
	
	if(typeof optionLists[list] === "undefined") {
		return false;
	} else {
		return true;
	}
}

/*
 * Get the forms in a survey
 */
/*
 * Get forms for a survey
 */
function getSurveyForms(sId, callback) {

	if(sId != -1) {
		var url = '/surveyKPI/survey/' + sId + '/getMeta';
	
		addHourglass();
	 	$.ajax({
			url: url,
			cache: false,
			dataType: 'json',
			success: function(data) {
				removeHourglass();
				if(typeof callback === "function") {
					callback(data);
				}
				

			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
  				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					bootbox.alert("Error failed to get forms for survey:" + sId);
				}
			}
		});
	
	}
}

function addForms(data) {
	var h = [],
		idx = -1,
		i,
		forms;
	
	forms = data.forms;
	for(i = 0; i < forms.length; i++) {
		h[++idx] = '<option value="';
		h[++idx] = forms[i].f_id;
		h[++idx] = '">';
		if(forms[i].form === "main") {
			h[++idx] = localise.set["ed_tlf"];
		} else {
			h[++idx] = forms[i].form;
		}
		h[++idx] = '</option>';	
	}
	$('#form_name').html(h.join(""));

}

/*
 * User has changed the filter value on an option
 */
function updateFilterValues($this, isCascade, isChecked) {
	
	var $elem = $this.closest('tr'),
		$f = $this.closest('td'),
		listName = $elem.data("list_name"),
		formIndex = $elem.data("fid"),
		itemIndex = $elem.data("id"),
		qname = $elem.data("qname"),
		currentFilters,
		filterName,
		fVal,
		newVal;

	if(isCascade) {
		filterName = "_smap_cascade";
		if(isChecked) {
			fVal = $("#previousSelectChoice").val();
		} else {
			fVal = undefined;
		}
		currentFilters = {};
	} else {
		filterName = $f.data("f_name");
		fVal = $this.val();
		currentFilters = $elem.data("filters")
	}
	
	if(typeof currentFilters !== "object") {
		currentFilters = {};
	}
	
	newVal = currentFilters;
	if(typeof fVal === "undefined" || fVal.trim().length === 0) {
		delete(newVal[filterName]);
	} else {
		newVal[filterName] = fVal;
	}
	$elem.data("filters", newVal);
	
	updateLabel("option", formIndex, itemIndex, listName, "text", newVal, qname, "cascade_filters") ;
}

/*
 * Set the choice filter to a value appropriate for cascade selects
 */
function setCascadeFilter() {
	var filter = "selected(${" + $('#previousSelect option:selected').html() + "}, _smap_cascade)";
	$('#choiceFilter').val(filter);
	updateLabel("question", globals.gFormIndex, 
			globals.gItemIndex, undefined, "text", filter, undefined, "choice_filter");
}

/*
 * clear the choice filter
 */
function setNoFilter() {
	$('#choiceFilter').val("");
	updateLabel("question", globals.gFormIndex, 
			globals.gItemIndex, undefined, "text", "", undefined, "choice_filter");
}

/*
 * Modify controls that are dependent on the view being either for questions or choices
 */
function updateViewControls() {
	
	//if(globals.gSelProperty !== "media") {		// media is the only common attribute between question and option view
	//	globals.gSelProperty = "label";
	//}
	if(globals.gIsQuestionView && !globals.gShowingChoices) {
		$('.q_only').show();
		$('.o_only').hide();
		globals.gSelLabel = $('#selProperty > li.q_only.default').text();
	} else {
		$('.q_only').hide();
		$('.o_only').show();
		globals.gSelLabel = $('#selProperty > li.o_only.default').text();
	}
	globals.gSelProperty = "label";
	$('#propSelected').text(globals.gSelLabel);
}

});
