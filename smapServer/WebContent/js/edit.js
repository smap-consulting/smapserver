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
    	icheck: '/wb/plugins/iCheck/icheck.min',
	    bootstrapcolorpicker: 'bootstrap-colorpicker.min',

    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery'],
        'bootstrap.file-input': ['bootstrap.min'],
    	'bootbox': ['bootstrap.min'],
       	'toggle': ['bootstrap.min'],
	    'bootstrapcolorpicker': ['bootstrap', 'jquery'],
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
		 'bootstrapcolorpicker',
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
				bootstrapcolorpicker,
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
	gElement,
	gQname,
	gQType,
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
	window.moment = moment;

    setCustomEdit();
	setupUserProfile();
	localise.setlang();		// Localise HTML
	
	// Get the parameters and start editing a survey if one was passed as a parameter
	params = location.search.substr(location.search.indexOf("?") + 1);
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
	//getLocations(setLocationList);

	/*
	 * Get surveys and csv files that the user can link to
	 */
	getAccessibleSurveys($('.linkable_surveys'), true, true, false);
	getAccessibleCsvFiles($('.linkable_files'), true);

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
	changeset.updateViewControls();
	$('#viewType').change(function() {
		globals.gIsQuestionView = $(this).prop('checked');
		changeset.updateViewControls();
		
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
			if (confirm(localise.set["c_unsav"])) {
				openForm("existing");
			}
		} else {
			openForm("existing");
		}
		
	});
	$('#m_new').off().click(function() {	// Open a new form
		if(globals.changes.length > 0) {
			if (confirm(localise.set["c_unsav"])) {
				openForm("new");
			}
		} else {
			openForm("new");
		}
		
	});
	$('.m_save_survey').off().click(function() {	// Save a survey to the server
		changeset.validateAll();
		if(globals.model.survey.blocked) {
			bootbox.alert({
				locale: gUserLocale,
				message: localise.set["ed_blocked"]
			});
		} else {
			if(changeset.numberIssues("error") === 0) {
				changeset.save(surveyListDone);
			} else {
				bootbox.alert(localise.set["ed_er"]);
			}
		}
	});

	$('#next-error').off().click(function(){
		nextIssue("error");
	});
	
	$('#next-warning').off().click(function(){
		nextIssue("warning");
	});
	
	$('.m_validate').off().click(function() {
		changeset.validateAll();
	});
	
	$('.m_languages').off().click(function() {
		
		if($(this).closest('li').hasClass('disabled')) {
			bootbox.alert(localise.set["ed_cml"]);
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
			bootbox.alert(localise.set["ed_csr"]);
		} else {
			setAllRequired(true);
		}
	});
	
	$('#m_not_required').off().click(function() {
		if($(this).closest('li').hasClass('disabled')) {
			bootbox.alert(localise.set["ed_csr"]);
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
		$('#mediaModalLabel').html(localise.set["ed_mmf"]);
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

		$('#settingsMsg').html("").hide();
		$('#settingsModal').modal('show');
	});

    $('#m_keys').off().click(function() {	// Show the keys dialog

        // Close any drop downmenus
        $('.dropdown-toggle').parent().removeClass("open");
        $('.navbar-collapse').removeClass("in");

        updateSettingsData();

        $('#keysModal').modal('show');
    });


	$('#save_settings, #save_keys').off().click(function() {	// Save settings to the database

		// validate
		var displayName = $('#set_survey_name').val();
		if(!displayName || displayName.trim().length == 0) {
			alert(localise.set["ed_er"]);
            return false;
		}
		globals.model.save_settings();
	});
	
	$('#m_info').off().click(function() {	// Show the info dialog
		
		var tableNames ="",
			i,
			idx;

		idx = 0;
		for(i = 0; i < globals.model.survey.forms.length; i++) {
			if(!globals.model.survey.forms[i].reference) {
                if (idx > 0) {
                    tableNames += ", ";
                }
                tableNames += globals.model.survey.forms[i].tableName;
                idx++;
            }
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
        $('#i_id').val(globals.model.survey.id);
		
		$('#infoModal').modal('show');
	});

    $('#m_export').off().click(function() {	// Export to XLS
        if($(this).closest('li').hasClass('disabled')) {
            bootbox.alert(localise.set["ed_cx"]);
        } else {
            window.location.href = "/surveyKPI/xlsForm/" + gSId + "?filetype=" + "xlsx";
        }

    });

    /*
     * Respond to a change in the form to be launched
     * If the question type is a child form then the list of questions needs to be updated
     */
	$('#p_form_identifier').change(function(){
		var survey = globals.model.survey;
		var qType = survey.forms[globals.gFormIndex].questions[globals.gItemIndex].type;
		if(qType === "child_form") {
			getQuestionsInSurvey($('#p_key_question'), $(this).val(), true);
		}
	});

    /*
     * Respond to clicking of the save parameters button in the parameters edit modal
     */
	$('#parameterSave').click(function() {
		var params = [];
		var newVal;
		var survey = globals.model.survey;
		var question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
		var i;
		var paramDetails;
		var other;

		$('#parameter_msg').hide();
		var qParams = globals.model.qParams[question.type];
		if(qParams && qParams.length > 0) {
			for(i = 0; i < qParams.length; i++) {
				paramDetails = globals.model.paramDetails[qParams[i]];
				if(!getParam($('#' + paramDetails.field), params, qParams[i], paramDetails.type)) {
					return false;
				}
			}
		}
		other=$('#p_other').val();
		// validate
		if(other.length > 0) {
			var oArray = other.split(';');
			for(i = 0; i < oArray.length; i++) {
				var oArray2 = oArray[i].split('=');
				if(oArray2.length != 2) {
					$('#parameter_msg').show().html(localise.set["msg_pformat"]);
					return false;
				}
			}
		}

		newVal = params.join(';');
		if(newVal.length > 0 && other.length > 0) {
			newVal += ';';
		}
		newVal += other;
		updateLabel("question", globals.gFormIndex, globals.gItemIndex, undefined, "text", newVal, gQname, "parameters");

		$('#parameterModal').modal("hide");
	});

	/*
     * Set up colour picker
     */
	$('.colorpicker-component').colorpicker({
		format: 'hex'
	});

	// Set up the tabs
	$('#standardTab a').click(function (e) {
		e.preventDefault();
		$(this).tab('show');

		$(".appearancetab").hide();
		$('#standardPanel').show();

	});
	$('#searchTab a').click(function (e) {
		e.preventDefault();
		$(this).tab('show');

		$(".appearancetab").hide();
		$('#searchPanel').show();
	});

	$('#pdfTab a').click(function (e) {
		e.preventDefault();
		$(this).tab('show');

		$(".appearancetab").hide();
		$('#pdfPanel').show();
	});

	// Hide and show search elements
	$('#a_filter_column, #a_second_filter_column, #a_csv_identifier, ' +
		'#a_survey_identifier, input[type=radio][name=search_source],' +
		'#a_search_value, #a_search_label').change(function() {
		showSearchElements();
	});
	$('#a_pdfno').change(function() {
		if($(this).prop('checked')) {
			$('.pdf_appearance_field').hide();
		} else {
			$('.pdf_appearance_field').show();
		}
	});
	$('#a_pdfcols_number').change(function() {
		var val = $(this).val();
		var i;

		$('.pdfcols').hide();
		if($(this).val() !== '') {
			for(i = 1; i <= val; i++) {
				$('.pdfcols' + i).show();
				$('#a_pdfcols_' + i + '_l').html(localise.set["ed_col_width"].replace('%s1', i));
			}
		}
	});

	// Validate on value change
	$('#a_sep, #a_numbers, #a_select1_type, #a_likert').change(function(){
		checkForAppearanceWarnings();
	});

	/*
	 * Respond to a change in the question used as the value for a search filter
	 */
	$('#a_filter_value_sel').change(function(){
		$('#a_filter_value_static').val('${' + $(this).val() + '}')
	});
	$('#a_second_filter_value_sel').change(function(){
		$('#a_second_filter_value_static').val('${' + $(this).val() + '}')
	});

	/*
     * Respond to a change in the form that is to be searched
     * If the question type is a child form then the list of questions needs to be updated
     */
	$('#a_survey_identifier, #a_csv_identifier').change(function(){
		var survey = globals.model.survey;
		var search_source = $('input[type=radio][name=search_source]:checked').val();
		if(search_source === "survey") {
			getQuestionsInSurvey($('.column_select'), $(this).val(), true);
		} else {
			getQuestionsInCsvFile($('.column_select'), $(this).val(), true);
		}

	});

	/*
     * Respond to clicking of the save appearances button in the appearance edit modal
     */
	$('#appearanceSave').click(function() {
		var appearances = [];       // Array of appearance values taken from dialog
		var app_choices = [];       // Dummy appearances for choice value and labels
		var newVal;
		var newAppChoiceVal;
		var survey = globals.model.survey;
		var question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
		var i;
		var appearanceDetails;
		var other;

		$('#appearance_msg').hide();
		/*
		 * Get the appearance values from the dialog
		 */
		var qAppearances = globals.model.qAppearances[question.type];
		if(qAppearances && qAppearances.length > 0) {
			for(i = 0; i < qAppearances.length; i++) {
				appearanceDetails = globals.model.appearanceDetails[qAppearances[i]];
				if(!getAppearance($('#' + appearanceDetails.field), appearances, qAppearances[i], appearanceDetails, question.type, app_choices)) {
					return false;       // getAppearance returns false if there is an error
				}
			}
		}

		/*
		 * Get common appearance values
		 */
		var colour;
		if($('#a_hidden').prop('checked')) {
			appearances.push('hidden');
		}
		if($('#a_pdfno').prop('checked')) {
			appearances.push('pdfno');
		}
		if($('#a_pdf_lw').val() !== '') {
			appearances.push('pdflabelw_' + $('#a_pdf_lw').val());
		}
		if($('#a_pdfheight').val() && $('#a_pdfheight').val() !== '') {
			var pdfHeight = $('#a_pdfheight').val();
			if(pdfHeight < 0) {
				showAppearanceError(localise.set["ed_gt_0"]);
				$('#a_pdfheight').focus();
				return false;
			}
			appearances.push('pdfheight_' + pdfHeight);
		}
		var pdfcolsnumber = $('#a_pdfcols_number').val();
		if(pdfcolsnumber && pdfcolsnumber !== '') {
			var pdfcols = 'pdfcols';
			var pdfcolscount = +0;
			for(i = 1; i<= pdfcolsnumber; i++) {
				var col_i = $('#a_pdfcols_' + i).val();
				pdfcolscount += +col_i;
				pdfcols += '_' + col_i;
				if(+col_i > 10 || +col_i < 1) {
					showAppearanceError(localise.set["msg_pdfcols_width"]);
					$('#a_pdfcols_' + i).focus();
					return false;
				}
			}
			if(pdfcolscount !== 10) {
				showAppearanceError(localise.set["msg_pdfcols_count"]);
				$('#a_pdfcols_1').focus();
				return false;
			}
			appearances.push(pdfcols);
		}
		colour = $('input', '#a_pdflabelbg').val();
		if(colour && colour !== '#ffffff') {
			 var c1 = colour.substring(1,3);
			 var c2 = colour.substring(3,5);
			 var c3 = colour.substring(5,7);
			 appearances.push('pdflabelbg_' + c1 + '_' + c2 + '_' +c3);
		}
		colour = $('input', '#a_pdfvaluebg').val();
		if(colour && colour !== '#ffffff') {
			var c1 = colour.substring(1,3);
			var c2 = colour.substring(3,5);
			var c3 = colour.substring(5,7);
			appearances.push('pdfvaluebg_' + c1 + '_' + c2 + '_' +c3);
		}
		colour = $('input', '#a_pdfmarkercolor').val();
		if(colour && colour !== '#ffffff') {
			var c1 = colour.substring(1,3);
			var c2 = colour.substring(3,5);
			var c3 = colour.substring(5,7);
			appearances.push('pdfmarkercolor_' + c1 + '_' + c2 + '_' +c3);
		}
		if($('#a_pdfspace').val() && $('#a_pdfspace').val() !== '') {
			appearances.push('pdfspace_' + $('#a_pdfspace').val());
		}
		if($('#a_pdfnewpage').prop('checked')) {
			appearances.push('pdfnewpage');
		}
		if($('#a_pdflabelcaps').prop('checked')) {
			appearances.push('pdflabelcaps');
		}
		if($('#a_pdflabelbold').prop('checked')) {
			appearances.push('pdflabelbold');
		}
		if($('#a_pdfapp').prop('checked')) {
			appearances.push('pdfapp');
		}
		if($('#a_pdfbarcode').prop('checked')) {
			appearances.push('pdfbarcode');
		}
		if($('#a_pdfhyperlink').prop('checked')) {
			appearances.push('pdfhyperlink');
		}
		if($('#a_pdfaddto').val() !== '') {
			appearances.push('pdfaddto_' + $('#a_pdfaddto').val());
		}

		/*
		 * Add other
		 */
		other=$('#a_other').val();

		newVal = appearances.join(' ');
		if(newVal.length > 0 && other.length > 0) {
			newVal += ' ';
		}
		newVal += other;
		updateLabel("question", globals.gFormIndex, globals.gItemIndex, undefined, "text", newVal, gQname, "appearance");

		// Save the updated settings for search choices
		newAppChoiceVal = app_choices.join(' ');
		updateLabel("question", globals.gFormIndex, globals.gItemIndex, undefined, "text", newAppChoiceVal, gQname, "app_choices");
		$('#appearanceModal').modal("hide");
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
					globals.model.setSurveyData(data);
					setLanguages(data.languages, refreshForm);
					refreshForm();
				},
				error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						alert(localise.set["msg_err_save"] + " " + xhr.responseText);
					}
				}
		});
	});

	$('#p_form_identifier').change(function() {
		var ident = $(this).val();

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
						alert(localise.set["msg_err_save"] + ' ' + xhr.responseText);
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

		// validate
		var displayName = $('#set_survey_name').val();
        if(!displayName || displayName.length == 0) {
            $('#settings_msg').html(localise.set["msg_val_nm"]).removeClass("alert-success").addClass("alert-danger").show();
        } else {

            $('#settings_msg').hide();
		}
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
		if($(this).is(':checked')) {
			$('.audit_location_data').show();
		} else {
			$('.audit_location_data').hide();
		}
		globals.model.settingsChange();
	});
	$('#hide_on_device, #audit_location_data, #track_changes').change(function() {
		globals.model.settingsChange();
	});
    $('#exclude_empty').change(function() {
        globals.model.settingsChange();
    });
	$('#addPdfTemplate').off().click(function() {
		globals.model.settingsAddPdfClicked();
        $('#pdfSet').val("yes");
	});
	$('#delPdfTemplate').off().click(function() {
		globals.model.settingsAddPdfClicked();
		$('#pdf_file_msg').val("");
		$('#pdfSet').val("no");
	});
    $('#downloadPdfTemplate').off().click(function() {
        downloadFile("/surveyKPI/file/pdf/surveyPdfTemplate/" + gSId);
    });
	$('#prevPdfTemplate').off().click(function() {
		downloadFile("/surveyKPI/file/pdf/surveyPdfTemplate/" + gSId + '?recovery=true');
	});
    $('#set_key_policy').change(function() {
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

    $('.m_test_survey').off().click(function() {
        if(globals.changes.length > 0) {
            alert(localise.set["msg_test"]);
            return false;
        }
		return true;
	});
	
	/*
	 * Add check prior to the user leaving the screen
	 */
	window.onbeforeunload = function() {
		if(globals.changes.length > 0) {
			return localise.set["msg_leave"];
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
			existing_project,
			//existing_form,
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
					bootbox.alert(localise.set["msg_val_nm"]);
					return false;
				}
				existing = $('#base_on_existing').prop('checked');
				existing_project = $('#existing_project').val();
				existing_survey = $('#survey_name').val();
				shared_results = $('#shared_results').prop('checked');
				createNewSurvey(name, existing, existing_survey, shared_results, surveyDetailsDone);
			}
		} else {
			alert(localise.set["msg_val_p"]);
			return false;
		}
	 });

	/*
	 * If the survey is being based on another survey then the user can select the project for the
	 * other existing survey
	 */
    $('#existing_project').change(function() {
        var existingProject = $('#existing_project option:selected').val();
        loadSurveys(existingProject, "#survey_name", false, false, undefined);			// Get surveys
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
				var msg = localise.set['ed_qs'] + (required ? localise.set['ed_req'] : localise.set['ed_nreq']);
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
	var i,j,
		tArray,
		types = globals.model.qTypes,
		h = [],
		idx = -1,
		count,
		name;
	
	h[++idx] = '<div class="row margin-bottom">';
	for(i = 0; i < types.length; i++) {
		
		if(types[i].canSelect && isCompatible(types[i].compatTypes, currentType) ) {

			name = localise.set[types[i].trans];
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
				tArray = types[i].glyphicon.split(',');
				for(j = 0; j < tArray.length; j++) {
					h[++idx] = '<span class="glyphicon glyphicon-';
					h[++idx] = tArray[j].trim();
					h[++idx] = ' edit_type_select"></span>';
				}
				h[++idx] = '<br/>';
			} else if(types[i].image) {
				h[++idx] = '<img class="edit_image_select" src="';
				h[++idx] = types[i].image; 
				h[++idx] = '"></img><br/>';
			} else if(types[i].text) {
				h[++idx] = '<span class="edit_type_select">';
				h[++idx] = types[i].text; 
				h[++idx] = '</span><br/>';
			}
			h[++idx] = name;
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
		bootbox.alert(localise.set["ed_blocked"]);
	}
	
	/*
	 * Refresh the form
	 */
	if(globals.gShowingChoices) {
		// skip the refresh of the choices as when the data was reloaded the item index may have changed hence we can't be guaranteed which question will be refreshed
		// Safer to return to the question view
		globals.gShowingChoices = false;
		changeset.updateViewControls();
		
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
	$('.m_test_survey').attr("href", "/webForm/" + globals.model.survey.ident);
	
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

	changeset.validateAll();

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
		changeset.updateViewControls();
		
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
			alert(localise.set["c_error"] + ": " + localise.set["msg_prev_select"]);
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
			itemIndex = $elem.data("id"),
			qname = $elem.data("qname"),
			newVal = $this.val();
		
		updateLabel("option", 0, itemIndex, listName, "text", newVal, "", "value") ;
		
	});

	// Update the option display name
	$context.find('.odisplayname').change(function(){

		var $this = $(this),
			$elem = $this.closest('tr'),
			listName = $elem.data("list_name"),
			itemIndex = $elem.data("id"),
			qname = $elem.data("qname"),
			newVal = $this.val();

		updateLabel("option", 0, itemIndex, listName, "text", newVal, "", "display_name") ;

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
		changeset.updateViewControls();

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
			labelType,
			linkedQuestionId = 0;
		
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
		} else if (prop === "linked_target") {
			if($this.hasClass("prop_no")) {
				linkedQuestionId = $this.closest('.row').find(".linkedQuestion").val();
				if(!linkedQuestionId) {
					linkedQuestionId = 0;		// HRK
				}
				newVal = $this.closest('.row').find(".linkedSurvey").val() + "::" +
					linkedQuestionId;
			} else {
				newVal = undefined;
			}
		} else if(prop === "readonly") {
			newVal = $this.hasClass("prop_no");		// If set false then newVal will be true
		}
		updateLabel(type, formIndex, itemIndex, optionList, labelType, newVal, qname, prop); 

	});

	// Respond to a click on the parameter button
	$context.find('.parameterButton').off().click(function() {

		var $this = $(this),
			$li = $this.closest('li'),
			survey = globals.model.survey;

		var formIndex = $li.data("fid");
		var itemIndex = $li.data("id");
		globals.gFormIndex = formIndex;
		globals.gItemIndex = itemIndex;

		var qType = survey.forms[formIndex].questions[itemIndex].type;
		var qName = survey.forms[formIndex].questions[itemIndex].name;
		gQname = qName;
		var qParams = globals.model.qParams[qType];
		var paramDetails;
		var paramArray = [];

		var paramData = $li.find('.labelProp').val();
		var otherParams = '';
		var i, j;
		var foundParam;

		if(paramData) {
			paramArray = paramData.split(';');
			if(paramArray.length == 0) {
				paramArray = paramData.split(' ');
			}
		}

		/*
         * Add a question select list
         */
		var sIdent = "0";
		if(qType === "parent_form") {
			$('#p_key_question_label').html(localise.set["ed_qk"]);
			$('#p_key_question').empty().append(getQuestionsAsSelect());
		} else if (qType === "child_form") {
			$('#p_key_question_label').html(localise.set["ed_qkc"]);
			// Get the form ident
			for (i = 0; i < paramArray.length; i++) {
				var p = paramArray[i].split('=');
				if (p.length > 1) {
					if (p[0].trim() === 'form_identifier') {
						sIdent = p[1].trim();
						break;
					}
				}
			}
			getQuestionsInSurvey($('#p_key_question'), sIdent, true);
		} else if(qType === "begin repeat") {
			$('#p_ref').empty().append(getFormsAsSelect(qName));
		}


		/*
         * Show any parameter attributes for this question type
         */
		$('#parameter_form')[0].reset();
		$('.parameter_field').hide();
		if(qParams && qParams.length > 0) {
			for (j = 0; j < qParams.length; j++) {
				paramDetails = globals.model.paramDetails[qParams[j]];
				$('.' + paramDetails.field).show();
			}
		}


		for (i = 0; i < paramArray.length; i++) {

			var p = paramArray[i].split('=');
			if (p.length > 1) {
				foundParam = false;
				if(qParams && qParams.length > 0) {

					for (j = 0; j < qParams.length; j++) {
						paramDetails = globals.model.paramDetails[qParams[j]];

						if (p[0].trim() === qParams[j]) {
							foundParam = true;
							setParam($('#' + paramDetails.field), p[1].trim(), paramDetails.type);
							break;
						}
					}
				}

				if(!foundParam) {
					if (otherParams.length > 0) {
						otherParams += '; ';
					}
					otherParams += p[0].trim() + '=' + p[1].trim();
				}
			}
		}

		// Add any parameter values not explicetely set
		$('#p_other').val(otherParams);       // Not sure if we want to do this


		$('#parameterModal').modal({
			keyboard: true,
			backdrop: 'static',
			show: true
		});

	});

	// Respond to a click on the appearance button
	$context.find('.appearanceButton').off().click(function() {

		var $this = $(this),
			$li = $this.closest('li'),
			survey = globals.model.survey;

		var formIndex = $li.data("fid");
		var itemIndex = $li.data("id");
		globals.gFormIndex = formIndex;
		globals.gItemIndex = itemIndex;

		var question = survey.forms[formIndex].questions[itemIndex];
		var qType = question.type;
		var qName = question.name;
		gQname = qName;
		gQType = qType;
		var qAppearances = globals.model.qAppearances[qType];
		var appearanceDetails;
		var appearanceArray = [];
		var appChoiceArray = [];

		var appearanceData = $li.find('.labelProp').val();
		var app_choices = question.app_choices;
		var otherAppearances = '';
		var i, j;
		var foundAppearance;

		if(appearanceData) {
			appearanceArray = tokenizeAppearance(appearanceData);
		}
		if(app_choices) {
			appChoiceArray = app_choices.split(' ');
		}

		/*
         * Show any appearance attributes for this question type
         */
		$('#appearance_form')[0].reset();
		$('#appearance_search_form')[0].reset();
		$('#appearance_pdf_form')[0].reset();
		$('input','.colorpicker-component').colorpicker('setValue', '#ffffff');
		$('.appearance_field, .appearance_search_details').hide();
		$('.pdf_appearance_field').show();
		$('.pdfcols').hide();
		$('#standardTab a').click();
		$('#a_pdfaddto').empty().append(getQuestionsAsSelect());
		if(qType === 'image') {
			$('.a_pdfhyperlink').show();
		} else {
			$('.a_pdfhyperlink').hide();
		}

		/*
		 * Show form controls relevant for this question type
		 */
		if(qAppearances && qAppearances.length > 0) {
			for (j = 0; j < qAppearances.length; j++) {
				appearanceDetails = globals.model.appearanceDetails[qAppearances[j]];
				if(qAppearances[j] === 'w') {
					if(globals.model.survey.surveyClass === "theme-grid") {
						$('.' + appearanceDetails.field).show(); // Only show width if style is for grid
					}
				} else {
					$('.' + appearanceDetails.field).show();
				}
			}
		}

		// Get questions to select from this survey
		$('.questions_in_form').empty().append(getQuestionsAsSelect(localise.set["c_question"] + "..."));

		// Add value and label(s) from choices list or they may already be specified as temporary appearance values
		addLabelControls();


		for (i = 0; i < appearanceArray.length; i++) {

			if (qAppearances && qAppearances.length > 0) {
				foundAppearance = false;
				for (j = 0; j < qAppearances.length; j++) {
					appearanceDetails = globals.model.appearanceDetails[qAppearances[j]];

					var m = appearanceArray[i].match(appearanceDetails.rex);
					if (m) {
						if(globals.model.survey.surveyClass === "theme-grid" || qAppearances[j] !== 'w') {
							foundAppearance = true;
							var val = m[0].substring(appearanceDetails.value_offset);
							setAppearance($('#' + appearanceDetails.field), val, appearanceDetails.type, appearanceArray[i], question, survey);
							break;
						}
					}
				}

			}

			/*
			 * Check for common appearances that are set on every questions type
			 */
			var pdfa;
			var colour;
			if(appearanceArray[i] === 'hidden') {
				$('#a_hidden').prop('checked', true);
				foundAppearance = true;
			} else if(appearanceArray[i] === 'pdfno') {
				$('#a_pdfno').prop('checked', true);
				$('.pdf_appearance_field').hide();
				foundAppearance = true;
			} else if(appearanceArray[i].indexOf('pdflabelw_') === 0) {
				pdfa = appearanceArray[i].split('_');
				if(pdfa.length > 1) {
					$('#a_pdf_lw').val(pdfa[1]);
					foundAppearance = true;
				}
			} else if(appearanceArray[i].indexOf('pdfheight_') === 0) {
				pdfa = appearanceArray[i].split('_');
				if(pdfa.length > 1) {
					$('#a_pdfheight').val(pdfa[1]);
					foundAppearance = true;
				}
			} else if(appearanceArray[i].indexOf('pdflabelbg_') === 0) {
				pdfa = appearanceArray[i].split('_');
				foundAppearance = true;
				if(pdfa.length > 1) {
					colour = '#' + pdfa[1];
				}
				if(pdfa.length > 2) {
					colour += pdfa[2];
				}
				if(pdfa.length > 3) {
					colour += pdfa[3];
				}
				$('input', '#a_pdflabelbg').colorpicker('setValue', colour);
			}  else if(appearanceArray[i].indexOf('pdfvaluebg_') === 0) {
				pdfa = appearanceArray[i].split('_');
				foundAppearance = true;
				if(pdfa.length > 1) {
					colour = '#' + pdfa[1];
				}
				if(pdfa.length > 2) {
					colour += pdfa[2];
				}
				if(pdfa.length > 3) {
					colour += pdfa[3];
				}
				$('input', '#a_pdfvaluebg').colorpicker('setValue', colour);

			} else if(appearanceArray[i].indexOf('pdfmarkercolor_') === 0) {
				pdfa = appearanceArray[i].split('_');
				foundAppearance = true;
				if(pdfa.length > 1) {
					colour = '#' + pdfa[1];
				}
				if(pdfa.length > 2) {
					colour += pdfa[2];
				}
				if(pdfa.length > 3) {
					colour += pdfa[3];
				}
				$('input', '#a_pdfmarkercolor').colorpicker('setValue', colour);

			} else if(appearanceArray[i].indexOf('pdfspace_') === 0) {
				pdfa = appearanceArray[i].split('_');
				if(pdfa.length > 1) {
					$('#a_pdfspace').val(pdfa[1]);
					foundAppearance = true;
				}

			} else if(appearanceArray[i] === 'pdfnewpage') {
				$('#a_pdfnewpage').prop('checked', true);
				foundAppearance = true;

			} else if(appearanceArray[i] === 'pdflabelcaps') {
				$('#a_pdflabelcaps').prop('checked', true);
				foundAppearance = true;
			} else if(appearanceArray[i] === 'pdflabelbold') {
				$('#a_pdflabelbold').prop('checked', true);
				foundAppearance = true;
			} else if(appearanceArray[i] === 'pdfapp') {
				$('#a_pdfapp').prop('checked', true);
				foundAppearance = true;
			} else if(appearanceArray[i] === 'pdfbarcode') {
				$('#a_pdfbarcode').prop('checked', true);
				foundAppearance = true;
			} else if(appearanceArray[i] === 'pdfhyperlink') {
				$('#a_pdfhyperlink').prop('checked', true);
				foundAppearance = true;
			} else if(appearanceArray[i].indexOf('pdfcols_') === 0) {
				pdfa = appearanceArray[i].split('_');
				foundAppearance = true;
				if(pdfa.length > 1) {
					$('.pdfcols').hide();
					$('#a_pdfcols_number').val(pdfa.length - 1);
					for (j = 1; j <= pdfa.length - 1; j++) {
						$('#a_pdfcols_' + j + '_l').html(localise.set["ed_col_width"].replace('%s1', j));
						$('#a_pdfcols_' + j).val(pdfa[j])
						$('.pdfcols' + j).show();
					}
				}
			} else if(appearanceArray[i].indexOf('pdfaddto_') === 0) {
				pdfa = appearanceArray[i].split('_');
				if(pdfa.length > 1) {
					$('#a_pdfaddto').val(pdfa[1]);
					foundAppearance = true;
				}
			}

			/*
			 * Add other
			 */
			if (!foundAppearance) {
				if (otherAppearances.length > 0) {
					otherAppearances += ' ';
				}
				otherAppearances += appearanceArray[i];
			}
		}

		/*
		 * Add appearance values for app choices
		 */
		var langIdx = 0;
		for(i = 0; i < appChoiceArray.length; i++) {
			var ace = appChoiceArray[i].split('::');
			if(ace.length > 1) {
				if(ace[0] === '_sv') {
					$('#a_search_value').val(ace[1]);
				} else if(ace[0] === '_sl' && ace.length > 2) {
					$('#a_search_label' + langIdx++).val(ace[2]);
				}
			}
		}

		// Add any appearance values not explicetely set
		$('#a_other').val(otherAppearances);       // Not sure if we want to do this


		$('#appearance_msg').hide();
		$('#appearanceModal').modal({
			keyboard: true,
			backdrop: 'static',
			show: true
		});

	});


	// Respond to changes on linkedTarget (Survey or Question changed)
	$context.find('.linkedTarget').off().change(function() {

		var $this = $(this),
			prop = $this.data("prop"),
			$li = $this.closest('li'),
			formIndex = $li.data("fid"),
			itemIndex = $li.data("id"),
			newVal,		
			type,
			optionList = $li.data("list_name"),
			qname = $li.data("qname"),
			labelType,
			linkedQuestionId;
	
		type = "question";
		labelType = "text";
		
		linkedQuestionId = $this.closest('.row').find(".linkedQuestion").val();
		if(!linkedQuestionId) {
			linkedQuestionId = 0;		// HRK
		}
		newVal = $this.closest('.row').find(".linkedSurvey").val() + "::" + linkedQuestionId;	
		updateLabel(type, formIndex, itemIndex, optionList, labelType, newVal, qname, prop); 
		

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
	
	// Get linked questions
	$context.find('.linkedSurvey').change(function() {
		var $this = $(this),
			$li = $this.closest('li'),
			item = $li.prop("id"),
			surveyId = $this.val();
		
		markup.getLinkedQuestions(item, surveyId, 0);
		
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
			saved,
			survey = globals.model.survey,
			name,
			formIndex,
			itemIndex,
			i;
		
		if(globals.gSaveInProgress) {
			return;
		}
		
		formIndex = $questionElement.data("fid");
		itemIndex = $questionElement.data("id");
		
		published = survey.forms[formIndex].questions[itemIndex].published;
		saved = (survey.forms[formIndex].questions[itemIndex].published > 0);
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

                    // If the group was originally a begin repeat then it may not be empty
                    var seq = question.getSequenceQuestion(itemIndex, survey.forms[formIndex], false, undefined);
                    var endGroupSeq = seq;
                    for(i = seq + 1; i < survey.forms[formIndex].qSeq.length; i++) {
                        if(survey.forms[formIndex].questions[survey.forms[formIndex].qSeq[i]].memberGroup === survey.forms[formIndex].questions[itemIndex].name) {
                            endGroupSeq++;
                        } else {
                        	break;
						}
                    }

					name = survey.forms[formIndex].questions[itemIndex].name + "_groupEnd" ;
					$context = question.add(formIndex,
							//$questionElement.attr("id"),
                        	"question" + formIndex + "_" + survey.forms[formIndex].qSeq[endGroupSeq],
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
	$('#mediaModalLabel').html(localise.set['msg_sel_media_f']);
	
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
	$('#pdf_file_msg').val(globals.model.survey.pdfTemplateName);
	if(globals.model.survey.pdfTemplateName && globals.model.survey.pdfTemplateName.trim().length > 0) {
		$('#pdfSet').val("yes");
	} else {
        $('#pdfSet').val("no");
	}
	$('#set_hrk').val(globals.model.survey.hrk);
	if(globals.model.survey.key_policy) {
        $('#set_key_policy').val(globals.model.survey.key_policy);
    } else {
        $('#set_key_policy').val("replace");
	}
	$('#task_file').prop('checked', globals.model.survey.task_file);
	$('#timing_data').prop('checked', globals.model.survey.timing_data);
	if(globals.model.survey.timing_data) {
		$('.audit_location_data').show();
	} else {
		$('.audit_location_data').hide();
	}
	$('#audit_location_data').prop('checked', globals.model.survey.audit_location_data);
	$('#track_changes').prop('checked', globals.model.survey.track_changes);
	$('#hide_on_device').prop('checked', globals.model.survey.hideOnDevice);
    $('#exclude_empty').prop('checked', globals.model.survey.exclude_empty);
}


/*
 * Update the language model view
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
	h[++idx] = '<th>' + localise.set["c_del"] + '</th>';
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
			h[++idx] = '">';
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
			/*
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
			*/
			
			// Identifier
			/*
			h[++idx] = '<td>';
			h[++idx] = '<input type="text" data-idx="';
			h[++idx] = i;
			h[++idx] = '" readonly class="form-control" value="';
			h[++idx] = "linked_s_pd_" + pulldata[i].survey;
			h[++idx] = '"';
			h[++idx] = '</td>';
			*/
		
			// actions
			h[++idx] = '<td>';
		
			h[++idx] = '<button type="button" data-idx="';
			h[++idx] = i;
			h[++idx] = '" class="btn btn-default btn-sm rm_pulldata danger">';
			h[++idx] = '<span class="glyphicon glyphicon-trash" aria-hidden="true"></span></button>';
		
			h[++idx] = '</td>';
			// end actions
		
			h[++idx] = '</tr>';
		}
	}
	
	h[++idx] = '</tbody>';
	h[++idx] = '</table>';

	$selector.empty().append(h.join(''));

    $('input.pd_survey[data-idx="' + (pulldata.length - 1) +'"]' , $selector).focus();

	$(".pd_survey", $selector).change(function(){
		var idx = $(this).data("idx");
		gTempPulldata[idx].survey = $(this).val();
	});
	
	$(".pd_data_key", $selector).change(function(){
		var idx = $(this).data("idx");
		gTempPulldata[idx].data_key = $(this).val();
	});

    $(".rm_pulldata", $selector).click(function(){
        var idx = $(this).data("idx");
        gTempPulldata.splice(idx, 1);
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
	 * property type is type, name or display name
	 */
	if(typeof questionType !== "undefined"
			&& questionType === "calculate"
			&& prop !== "name"
        	&& prop !== "type"
			&& prop !== "display_name"
			&& prop !== "appearance"
        	&& prop !== "parameters") {	// Whatever the property for a calculation type the label field contains the calculation expression
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

	if(sId != -1 && sId && sId !== 'null') {
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
					bootbox.alert(localise.set["msg_err_get_s"] + ":" + xhr.responseText + " : " + sId);
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

            function nextIssue(severity) {

                var i, pos;
				/*
				 * Make sure we start inside the array
				 */
                if(globals.errors.length > 0) {
                    if(globals.gErrorPosition > globals.errors.length - 1) {
                        globals.gErrorPosition  = 0;
                    }
                    for(i = globals.gErrorPosition + 1; i < globals.gErrorPosition + globals.errors.length + 1; i++) {

                        if(i > globals.errors.length - 1 ) {
                            pos = i - globals.errors.length;
                        } else {
                            pos = i;
                        }

                        if(globals.errors[pos].severity === severity) {
                            globals.gErrorPosition = pos;
                            break;
                        }
                    }

                    focusOnError(globals.gErrorPosition);
                } else {
                    globals.gErrorPosition = 0;
                }
            }


            function focusOnError(position) {
                var survey = globals.model.survey,
                    error = globals.errors[position],
                    itemId,
                    $textarea,
                    $item,
                    $parents;

                if(error.itemType === "question") {

                    itemId = "question" + error.container + "_" + error.itemIndex;
                    $item = $('#' + itemId);

                    // Expand all parent panes
                    $parents = $item.parents('div.collapse');
                    $parents.show();

                    $textarea = $item.find('.question').find('textarea');
                    if($textarea.length > 0) {
                        $textarea.focus();
                    } else {
                        $item.find('button').focus();
                    }

                    if(!$("#viewType").prop('checked')) {
                        $("#viewType").closest('.toggle').trigger("click");
                    }

                } else {

                    //globals.gIsQuestionView = false;
                    changeset.updateViewControls();
                    //refreshForm();

                    if($("#viewType").prop('checked')) {
                        $("#viewType").closest('.toggle').trigger("click");
					}

                    $item = $('.olname[value="' + error.container + '"]');
                    $item.focus();
                }

            }

            /*
             * Get the value of a parameter from the parameter dialog
             */
            function getParam($elem, params, key, type) {
				var val;
				if(type === "boolean") {
					val = $elem.prop('checked') ? 'yes' : 'no';
				} else {
					val = $elem.val();
				}
				if(val) {
					val = val.trim();
					if(val.length > 0) {
						// validate
						if(key === 'max-pixels') {
							if(val <= 0) {
								showParameterError(localise.set["ed_gt_0"]);
								$('#p_max_pixels').focus();
								return false;
							}
						}

						// Save parameter
						if(key) {
							val = key + '=' + val;
						}
						params.push(val);
					}
				}
				return true;
            }

			/*
             * Get the value of an appearance from the appearance dialog
             */
			function getAppearance($elem, appearances, key, details, qtype, app_choices) {
				var val,
					msg,
					i;

				if(details.type === "boolean") {
					val = $elem.prop('checked') ? key : undefined;
				} else if(details.type === "select") {
					if($elem.val() === details.undef_value) {
						val = undefined;
					} else {
						if (details.valIsAppearance) {
							val = $elem.val();
						} else {
							val = key + $elem.val();
						}
					}
				} else if(details.type === "form") {
					// Handcoded
					if(details.field === 'a_search') {
						var search_source = $('input[type=radio][name=search_source]:checked').val();
						if(search_source !== 'worksheet') {
							val = "search(";        // open

							// filename
							var filename;
							var csvfile;

							if(search_source === "survey") {
								if($('#a_survey_identifier').val() === '') {
									showAppearanceError(localise.set["msg_search_source2"]);
									return false;
								}
								filename = 'linked_' + $('#a_survey_identifier').val();
							} else if(search_source === "csv") {
								if($('#a_csv_identifier').val() === '') {
									showAppearanceError(localise.set["msg_search_source2"]);
									return false;
								}
								csvfile = globals.gCsvFiles[$('#a_csv_identifier').val()];
								filename = csvfile.filename;
								var idx = filename.lastIndexOf('.');    // remove the extension
								if(idx > 0) {
									filename = filename.substring(0, idx);
								}
							} else {
								showAppearanceError(localise.set["msg_search_source"]);
								return false;
							}
							val += "'" + filename + "'";

							/*
							 * Add dummy appearances in app_choices for choice value and choice labels
							 *
							 */
							var searchValue = $('#a_search_value').val();
							if(!searchValue || searchValue.trim().length == 0) {
								showAppearanceError(localise.set["msg_choice_value"]);
								return false;
							} else {
								app_choices.push('_sv::' + searchValue);
							}
							var languages = globals.model.survey.languages;
							for(i = 0; i < languages.length; i++) {
								var labelValue = $('#a_search_label' + i).val();
								if(!labelValue || labelValue.trim().length == 0) {
									labelValue = searchValue;
								}
								app_choices.push('_sl::' + languages[i].name + '::' +   labelValue);
							}


							// first filter
							var filterColumn = $('#a_filter_column').val().trim();
							var filter = $('#a_match').val();
							var filterValue;
							var secondFilterColumn;
							var secondFilterValue;
							if(filterColumn !== '') {
								if(filter === '') {
									msg = localise.set["msg_filter_col"];
									msg = msg.replace('%s1', filterColumn);
									$('#appearance_msg').removeClass('alert-warning').addClass('alert-danger').show().html(msg);
									return false;
								} else {
									val += ", '" + filter + "'";
								}

								// first filter column
								val += ", '" + filterColumn + "'";

								// first filter Value
								filterValue = $('#a_filter_value_static').val().trim();
								if(filterValue.indexOf('${') === 0) {
									// question value
									// TODO check that question is in survey
								} else {
									// static value
									filterValue = "'" + filterValue + "'";      // add quotes
								}
								val += ", " + filterValue;

								// second filter
								secondFilterColumn = $('#a_second_filter_column').val();
								if(secondFilterColumn !== '') {
									val += ", '" + secondFilterColumn + "'";

									// second filter Value
									secondFilterValue = $('#a_second_filter_value_static').val().trim();
									if(secondFilterValue.indexOf('${') === 0) {
										// question value
										// TODO check that question is in survey
									} else {
										// static value
										secondFilterValue = "'" + secondFilterValue + "'";      // add quotes
									}
									val += ", " + secondFilterValue;
								}
							}



							val += ")";    // Close
						} else {
							val = undefined;
						}
					} else if(details.field === 'a_select1_type' || details.field === 'a_select_type') {
						var s1Val = $elem.val();
						if(s1Val === '') {
							val = undefined;
						} else if (s1Val === 'compact' || s1Val === 'quickcompact') {
							var numberColumns = $('#a_number_columns').val();
							if(numberColumns === '') {
								val = s1Val;
							} else {
								val = s1Val + '-' + numberColumns;
							}
						} else {
							val = s1Val;
						}
					}
				}

				if(val) {
					val = val.trim();
					appearances.push(val);
				}

				return validateAppearance(qtype, appearances, $('#appearance_msg'));

			}

			/*
             * Set the value of a parameter in the parameter dialog
             */
			function setParam($elem, val, type) {
				var val;
				if (type === "boolean") {
					$elem.prop('checked', val == 'yes' || val === 'true');
				} else {
					$elem.val(val);
				}
			}

			/*
             * Set the value of an appearance in the appearance dialog
             */
			function setAppearance($elem, val, type, appearance, question, survey) {
				var val;
				if (type === "boolean") {
					$elem.prop('checked', true);
				} else if (type === "form") {
					// Custom - hardcoded
					if(val === "search(") {

						// Now check parameters
						var idx1 = appearance.indexOf('(');
						var idx2 = appearance.indexOf(')');
						var params = appearance.substring(idx1 + 1, idx2);
						var paramsArray = [];
						if(params) {
							paramsArray = params.split(',');
						}
						if(paramsArray.length > 0) {
							var filter;
							var filter_column;
							var filter_value;
							var second_filter_column;
							var second_filter_value;

							// 1. First parameter is the filename
							var filename = paramsArray[0].trim();
							filename = filename.replace(/'/g, "");
							if(filename.startsWith('linked_s')) {
								var sIdent = filename.substring("linked_s".length - 1);
								$('input[type=radio][name=search_source][value=survey]').prop('checked', true);
								$('#a_survey_identifier').val(sIdent);
								$('.search_survey').show();
								getQuestionsInSurvey($('.column_select'), sIdent, true);
							} else {
								var csvIndex = getIndexOfCsvFilename(filename);
								$('input[type=radio][name=search_source][value=csv]').prop('checked', true);
								$('#a_csv_identifier').val(csvIndex);
								$('.search_csv').show();
								if(csvIndex) {
									getQuestionsInCsvFile($('.column_select'), csvIndex, true);
								}
							}

							filter = '';    // default
							if(paramsArray.length > 1) {
								// Second parameter is the filter
								filter = paramsArray[1].trim();
								filter = filter.replace(/'/g, "");
								$('#a_match').val(filter);
							}

							if(paramsArray.length > 2) {
								// Third parameter is the filter column
								filter_column = paramsArray[2].trim();
								filter_column = filter_column.replace(/'/g, "");
								$('#a_filter_column').val(filter_column);
							}

							if(paramsArray.length > 3) {
								// Fourth parameter is the filter value
								filter_value = paramsArray[3].trim();
								filter_value = filter_value.replace(/'/g, "");
								$('#a_filter_value_static').val(filter_value);
							}

							if(paramsArray.length > 4) {
								// Fifth parameter is the second filter column
								second_filter_column = paramsArray[4].trim();
								second_filter_column = second_filter_column.replace(/'/g, "");
								$('#a_second_filter_column').val(second_filter_column);
							}


							if(paramsArray.length > 5) {
								// Sixth parameter is the filter value
								second_filter_value = paramsArray[5].trim();
								second_filter_value = second_filter_value.replace(/'/g, "");
								$('#a_second_filter_value_static').val(second_filter_value);
							}


						}
						/*
                         * Add the choice values
                         */
						var optionList = survey.optionLists[question.list_name];
						if(optionList && optionList.options.length > 0) {
							var i;
							for(i = 0; i < optionList.options.length; i++) {
								var v = optionList.options[i].value;
								if(isNaN(v)) {
									// Apply this choice
									$('#a_search_value').val(v);
									var choiceIdx = 0;
									var labels = optionList.options[i].labels;
									for(choiceIdx = 0; choiceIdx <  optionList.options[i].labels.length; choiceIdx++) {
										$('#a_search_label' + choiceIdx).val(labels[choiceIdx].text);
									}
									break;
								} else {
									continue;   // Purely numeric must be a static choice
								}
							}

						}

						showSearchElements();
					} else if(val === 'compact' || val === 'quickcompact') {
						var paramsArray = appearance.split('-');
						$elem.val(paramsArray[0]);
						if(paramsArray.length > 0) {
							$('#a_number_columns').val(paramsArray[1]);
						}
						$('.a_number_columns').show();

					} else {
						$elem.val(val);
					}
				} else {
					$elem.val(val);
				}
			}

			/*
             * Get the forms in the survey as options for a select
             */
			function getFormsAsSelect(excludeForm) {

				var i,
					survey = globals.model.survey,
					h = [],
					idx = -1;

				if(survey) {
					if(survey.forms && survey.forms.length > 0) {

						h[++idx] = '<option value="">';
						h[++idx] = localise.set["c_none"];
						h[++idx] = '</option>';

						for(i = 0; i < survey.forms.length; i++) {
							if(survey.forms[i].name !== excludeForm && survey.forms[i].parentFormIndex != -1) {
								h[++idx] = '<option value="';
								h[++idx] = survey.forms[i].name;
								h[++idx] = '">';
								h[++idx] = survey.forms[i].name;
								h[++idx] = '</option>';
							}
						}
					}
				}

				return h.join("");

			}

			/*
			 * Get the questions in the form currently being edited as options for a select question
			 */
			function getQuestionsAsSelect(noneText) {

				var i,
					survey = globals.model.survey,
					h = [],
					idx = -1;

				/*
				 * Process the questions in the top level form (parent is 0)
				 *   Questions that are "begin repeat" type will link to sub level forms which are then processed in turn
				 *
				 */
				if(survey) {
					if(survey.forms && survey.forms.length > 0) {

						h[++idx] = '<option value="">';
						if(!noneText) {
							h[++idx] = localise.set["c_none"];
						} else {
							h[++idx] = noneText;
						}
						h[++idx] = '</option>';

						for(i = 0; i < survey.forms.length; i++) {
							if(survey.forms[i].parentFormIndex == -1) {
								h[++idx] = getQuestionsFromForm(survey.forms[i], i);
								break;
							}
						}
					}
				}

				return h.join("");

			}

			function getQuestionsFromForm(form, formIndex) {
				var i,
					question,
					h = [],
					idx = -1;

				if(form) {

					for (i = 0; i < form.qSeq.length; i++) {
						globals.gHasItems = true;
						question = form.questions[form.qSeq[i]];

						// Ignore property type questions, questions that have been deleted and meta questions like end repeat
						if (!markup.includeQuestion(question)) {
							continue;
						}

						h[++idx] = '<option value="';
						h[++idx] = question.name;
						h[++idx] = '">';
						h[++idx] = question.name;
						h[++idx] = '</option>';
					}
				}
				return h.join('');
			}

			function getIndexOfCsvFilename(filename) {
				var csvArray = globals.gCsvFiles;
				var i;

				filename += ".csv";     // The filename in the csvArray includes the extension
				for(i = 0; i < csvArray.length; i++) {
					if(csvArray[i].filename === filename) {
						return i;
					}
				}
				return undefined;
			}

			function showSearchElements() {

				var aFilterColumn = $('#a_filter_column').val();
				var aSecondFilterColumn = $('#a_second_filter_column').val();
				var searchSource = $('input[type=radio][name=search_source]:checked').val();
				var searchChoiceValue = $('#a_search_value').val();
				var fileIdentifier;
				var hasSearch;

				if(searchSource && searchSource !== '' && searchSource !== 'worksheet') {
					hasSearch =true;
				}

				$('#appearance_msg').hide();

				if(hasSearch) {
					$('.appearance_search_details').show();

					$('.search_csv, .search_survey').hide();
					if(searchSource == "survey") {
						$('.search_survey').show();
						fileIdentifier = $('#a_survey_identifier').val();
					} else if(searchSource == "csv") {
						$('.search_csv').show();
						fileIdentifier = $('#a_csv_identifier').val();
					}

					if(!fileIdentifier || fileIdentifier === '') {
						$('.a_choice_values').hide();
					} else {
						$('.a_choice_values').show();
					}

					if(!searchChoiceValue || searchChoiceValue === '') {
						$('.a_filter_column').hide();
					} else {
						$('.a_filter_column').show();
					}

					if(!aFilterColumn || aFilterColumn === "") {
						$(".has_filter, .a_second_filter_column, .has_second_filter").hide();
					} else {
						$(".has_filter, .a_second_filter_column").show();
					}

					if(!aSecondFilterColumn || aSecondFilterColumn === "") {
						$('.has_second_filter').hide();
					} else {
						$('.has_second_filter').show();
					}

				} else {
					$('.appearance_search_details').hide();
				}
			}

			function checkForAppearanceWarnings() {
				var warningMsg = '';
				var i;
				var msg;
				var qtype = gQType;

				if(qtype === 'string') {
					// Warn if thousands separator is used without numbers on a text question
					var ts = $('#a_sep').is(':checked');
					var numbers = $('#a_numbers').is(':checked');
					if(ts && !numbers) {
						if(warningMsg.length > 0) {
							warningMsg += '. ';
						}
						warningMsg += localise.set["msg_numb_ts"];
					}

				}

				if(qtype === 'select1') {
					// Warn if likert appearance is used
					var select1Type = $('#a_select1_type').val();
					var likert = $('#a_likert').is(':checked');
					if(likert) {
						if(warningMsg.length > 0) {
							warningMsg += '. ';
						}
						warningMsg += localise.set["msg_warn_likert"];
					}
					if(likert && select1Type !== '') {
						if(warningMsg.length > 0) {
							warningMsg += '. ';
						}
						warningMsg += localise.set["msg_warn_likert_n"];
					}
				}

				if(warningMsg.length > 0) {
					$('#appearance_msg').removeClass('alert-danger').addClass('alert-warning').show().html(warningMsg);
				} else {
					$('#appearance_msg').hide();
				}

				/*
				 * Show / hide controls
				 */
				if(qtype === 'select1') {
					var select1Type = $('#a_select1_type').val();
					if(select1Type === 'compact' || select1Type === 'quickcompact') {
						$('.a_number_columns').show();
					} else {
						$('.a_number_columns').hide();
					}
				}

			}

			/*
			 * Check for errors before returning
			 * TODO
			 */
			function validateAppearance() {
				return true;
			}

			/*
			 * Add a label control for each language
			 */
			function addLabelControls() {
				var languages = globals.model.survey.languages;
				var i;
				var h = [];
				var idx = -1;
				var labelControlId;

				for (i = 0; i < languages.length; i++) {
					labelControlId = 'a_search_label' + i;
					h[++idx] = '<div class="form-group search_label">';
						h[++idx] = '<label for="';
						h[++idx] = labelControlId;
						h[++idx] = '" class="col-sm-4 control-label">';
						h[++idx] = languages[i].name;
						h[++idx] = '</label>';
					h[++idx] = '<div class="col-sm-8">';
					h[++idx] = '<select id="';
						h[++idx] = labelControlId;
						h[++idx] = '" class="form-control column_select"></select>';
					h[++idx] = '</div>';
					h[++idx] = '</div>';
				}
				$('#search_label_list').empty().append(h.join(''));
			}

			function showAppearanceError(msg) {
				$('#appearance_msg').removeClass('alert-warning').addClass('alert-danger').show().html(msg);
			}
			function showParameterError(msg) {
				$('#parameter_msg').removeClass('alert-warning').addClass('alert-danger').show().html(msg);
			}
});
