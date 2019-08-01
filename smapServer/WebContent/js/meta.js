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
		jquery: 'jquery-2.1.1',
		lang_location: '..'
	},
	shim: {
		'app/common': ['jquery'],
		'bootstrap.min': ['jquery'],
		'jquery.autosize.min': ['jquery']
	}
});

require([
		'jquery',
		'app/common',
		'bootstrap.min',
		'modernizr',
		'app/localise',
		'app/ssc',
		'app/globals',
		'jquery.autosize.min'],
	function($, common, bootstrap, modernizr, localise, ssc, globals) {


		var	gMode = "survey",
			gIdx;;

		$(document).ready(function() {

			setupUserProfile();
			localise.setlang();		// Localise HTML

			// Get the user details
			globals.gIsAdministrator = false;
			getLoggedInUser(surveyListDone, false, true, undefined, false, false);

			// Add menu functions
			$('#m_open').off().click(function() {	// Open an existing form
				openForm("existing");
			});

			// Add menu functions
			$('#m_simple_edit').off().click(function() {	// Edit a survey
				gMode = "simple_edit";
				refreshView(gMode);
			});

			$('#addPreload').off().click(function() {
				$('#metaForm')[0].reset();
				$('#addModal').modal('show');
			});

			$('#saveMetaItem').click(function() {

				var item = {
					name: $('#item_name').val(),
					display_name: $('#item_display_name').val(),
					sourceParam: $('#item_source_param').val()
				};

				// Settings
				var set = [];
				var pdfNo = $('#a_pdfno').prop('checked');
				if(pdfNo) {
					set.push('pdfno');
				}
				item.settings = set.join(' ');

				updateMetaItem(item, surveyListDone);

				$('#addModal').modal("hide");
			});

			// Add responses to events
			$('#project_name').change(function() {
				globals.gCurrentProject = $('#project_name option:selected').val();
				globals.gCurrentSurvey = -1;
				globals.gCurrentTaskGroup = undefined;

				saveCurrentProject(globals.gCurrentProject,
					globals.gCurrentSurvey,
					globals.gCurrentTaskGroup);

				getSurveyList();
			});

		});

		function getSurveyList() {
			console.log("getSurveyList: " + globals.gCurrentSurvey);
			if(globals.gCurrentSurvey > 0) {
				loadSurveys(globals.gCurrentProject, undefined, false, false, surveyListDone);
			} else {
				loadSurveys(globals.gCurrentProject, undefined, false, false, undefined);
			}
		}

		function surveyListDone() {
			getSurveyDetails(refreshView, true);
		}

		function refreshView() {
			setChangesHtml($('#meta'), globals.model.survey);
		}

		/*
		 * Convert list of meta items to html
		 */
		function setChangesHtml($element, survey) {
			var h =[],
				idx = -1,
				i,
				j;

			$('.formName').html(survey.displayName);

			if(!survey) {
				$('#errormesg').html("<strong>No Changes</strong> Create or select a survey to see meta items");
				$('#infobox').show();
			} else {

				var metaList =[
					{
						value: "start",
						label: "c_start"
					},
					{
						value: "end",
						label: "c_end"
					},
					{
						value: "deviceid",
						label: "c_device"
					},
					{
						value: "subscriberid",
						label: "c_subscriberid"
					},
					{
						value: "simserial",
						label: "c_simserial"
					},
					{
						value: "phonenumber",
						label: "c_phone"
					},
					{
						value: "username",
						label: "c_user"
					},
					{
						value: "email",
						label: "c_email"
					}
				];

				h[++idx] = '<table class="table table-striped">';

				for(i = 0; i < survey.meta.length; i++) {

					if(survey.meta[i].isPreload) {
						h[++idx] = '<tr>';
						h[++idx] = '<td>';
						h[++idx] = survey.meta[i].sourceParam;
						h[++idx] = '</td>';
						h[++idx] = '<td>';
						h[++idx] = survey.meta[i].name;
						h[++idx] = '</td>';
						h[++idx] = '<td>';
						h[++idx] = survey.meta[i].display_name;
						h[++idx] = '</td>';
						h[++idx] = '<td>';
						h[++idx] = survey.meta[i].settings;
						h[++idx] = '</td>';
						h[++idx] = '<td>';
						h[++idx] = '<button type="button" data-idx="';
						h[++idx] = i;
						h[++idx] = '" class="btn btn-primary btn-sm edit_preload"><span class="glyphicon glyphicon-edit" aria-hidden="true"></span></button>';
						h[++idx] = '<button type="button" data-idx="';
						h[++idx] = i;
						h[++idx] = '" style="margin-left:2px;" class="btn btn-default btn-sm rm_preload danger"><span class="glyphicon glyphicon-trash" aria-hidden="true"></span></button>';
						h[++idx] = '</td>';
						h[++idx] = '</tr>';
					}

					for(j = 0; j < metaList.length; j++) {
						if(metaList[j].sourceParam === survey.meta[i].sourceParam) {
							metaList[j].metaIndex = i;
							metaList[j].set = true;
							break;
						}
					}
				}

			}

			$element.html(h.join(''));

			/*
			 * Add a dropdown to select a new preload
			 */
			h = [];
			idx = -1;
			for(j = 0; j < metaList.length; j++) {
				h[++idx] = '<option value="';
				h[++idx] = metaList[j].value;
				h[++idx] = '">';
				h[++idx] = localise.set[metaList[j].label];
				h[++idx] = '</option>';
			}
			$('#item_source_param').html(h.join(''));

			/*
			 * Add a response to actions
			 */
			$('.rm_preload', $element).click(function(){
				var item = globals.model.survey.meta[$(this).data("idx")];
				deleteMetaItem(item.sourceParam, surveyListDone);
			});

			$('.edit_preload', $element).click(function(){

				gIdx = $(this).data("idx");
				var item = globals.model.survey.meta[gIdx];
				var i;

				$('#metaForm')[0].reset();
				$('#item_name').val(item.name);
				$('#item_display_name').val(item.display_name);
				$('#item_source_param').val(item.sourceParam);

				if(item.settings) {
					var set = item.settings.split(' ');
					for(i = 0; i < set.length; i++) {
						if(set[i] === 'pdfno') {
							$('#a_pdfno').prop('checked', true);
						}
					}
				}
				$('#addModal').modal('show');
			});

		}

		function updateMetaItem(item, callback) {

			var url="/surveyKPI/surveys/add_meta/" + globals.model.survey.ident;
			var itemString = JSON.stringify(item);

			addHourglass();
			$.ajax({
				type: "POST",
				url: url,
				cache: false,
				data: {
					item: itemString
				},
				success: function(data) {
					removeHourglass();

					if(typeof callback == "function") {
						callback();
					}
				},
				error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
						return;  // Not an error
					} else {
						alert(localise.set["c_error"] + xhr.responseText);
					}
				}
			});
		}

		function deleteMetaItem(sourceParam, callback) {

			var url="/surveyKPI/surveys/meta/" + globals.model.survey.ident + "/" + sourceParam;

			addHourglass();
			$.ajax({
				type: "DELETE",
				url: url,
				cache: false,
				success: function(data) {
					removeHourglass();

					if(typeof callback == "function") {
						callback();
					}
				},
				error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
						return;  // Not an error
					} else {
						alert(localise.set["c_error"] + xhr.responseText);
					}
				}
			});
		}

	});

