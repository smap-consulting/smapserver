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
 * Quick solution to issue of legacy globals after migrating to AMD / require.js
 */
define(function() {
	window.globals = {
	
		gProjectList: undefined,
		gCurrentProject: 0,
		gCurrentLanguage: undefined,
		gCurrentSurvey: 0,
		gCurrentLayer: undefined,
		gLoggedInUser: undefined,
		gEditingReportProject: undefined,   		// Set if fieldAnalysis called to edit a report
		gIsAdministrator: false,
		gIsAnalyst: false,
		gIsOrgAdministrator: false,
		gSendTrail: false,
		gViewIdx: 0,
		gSelector: new Selector(),
		
		gRegions: undefined,
		gRegion: {},
		
		gServerCanSendEmail: false,
		
		// Reports
		gEmailEnabled: false,
		gFacebookEnabled: false,
		gTwitterEnabled: false,
		
		// Tasks
		gCurrentUserId: undefined,
		gCurrentUserName: undefined,
		gAssignmentsLayer: undefined,
		gPendingUpdates: [],
		//gDeleteSelected: false,		// When set if the user clicks on a task it will be marked for deletion
		
		model: new Model()

	}
	
	return window.globals;
	
	function Selector() {
		
		this.dataItems = new Object();
		//this.panelDataItems = new Object();
		this.surveys = new Object();
		this.surveyLanguages = new Object();
		this.surveyQuestions = new Object();
		this.questions = new Object();
		this.allSurveys;				// Simple list of surveys
		this.allRegions;
		this.views = [];			// Simple list of views
		this.maps = {};				// map panels indexed by the panel id
		this.changed = false;
		this.SURVEY_KEY_PREFIX = "surveys";
		this.TASK_KEY = "tasks";
		this.TASK_COLOR = "#dd00aa";
		this.SURVEY_COLOR = "#00aa00";
		this.SELECTED_COLOR = "#0000aa";
		this.currentPanel = "map";
		
		/*
		 * Get Functions
		 */
		this.getAll = function () {
			return this.dataItems;
		};
		
		this.getItem = function (key) {
			return this.dataItems[key];
		};
		
		// Return all the table data available for a survey
		this.getFormItems = function (sId) {
			var tableItems = new Object();
			for(var key in this.dataItems) {
				var item = this.dataItems[key];
				if(item.table == true && item.sId == sId) {
					tableItems[key] = item;
				}
			}
			return tableItems;
		};
		
		this.getSurvey = function (key) {
			return this.surveys[key];
		};
		
		this.getSurveyQuestions = function (sId, language) {
			var langQ = this.surveyQuestions[sId];
			if(langQ) {
				return langQ[language];
			} else {
				return null;
			}
		};
		
		this.getSurveyLanguages = function (key) {
			return this.surveyLanguages[key];
		};
		
		// Returns the list of surveys on the home server
		this.getSurveyList = function () {
			return this.allSurveys;
		};
		
		this.getRegionList = function () {
			return this.allRegions;
		};
		
		// deprecate question meta should be replaced by all question details in the question list
		this.getQuestion = function(qId, language) {
			var langQ = this.questions[qId];
			if(langQ) {
				return langQ[language];
			} else {
				return null;
			}
		};
		
		/*
		 * Get the question details that came with the question list
		 * This approach should replace the concept of "question meta"
		 */
		this.getQuestionDetails = function(sId, qId, language) {
			var qList = this.getSurveyQuestions(sId, language),
				i;
			
			for(i = 0; i < qList.length; i++) {
				if(qList[i].id == qId) {
					return qList[i];
				}
			}
			return null;
		};
		
		this.hasQuestion = function(key) {
			if(this.questions[key] != undefined) {
				return true;
			} else {
				return false;
			}
		};
		
		// Return the list of current views
		this.getViews = function () {
			return this.views;
		};
		
		// Return a map if it exists
		this.getMap = function (key) {
			return this.maps[key];
		};
		
		
		/*
		 * Set Functions
		 */
		this.addDataItem = function (key, value) {
			this.dataItems[key] = value;
			this.changed = true;
		};	
		
		this.clearDataItems = function () {
			this.dataItems = new Object();
		};	
		
		this.clearSurveys = function () {
			this.surveys = new Object();
			this.surveyLanguages = new Object();
			this.surveyQuestions = new Object();
			this.questions = new Object();
			this.allSurveys = undefined;				
			this.allRegions = undefined;
		};	
		
		this.setSurveyList = function (list) {
			this.allSurveys = list;
			if(typeof list[0] !== "undefined") {
				this.selectedSurvey = list[0].sId;
			}
		};	
		
		this.setSurveyLanguages = function (key, value) {
			this.surveyLanguages[key] = value;
		};
		
		this.setSurveyQuestions = function (sId, language, value) {
			var langQ = new Object();
			langQ[language] = value;
			this.surveyQuestions[sId] = langQ;
		};
		
		this.setRegionList = function (list) {
			this.allRegions = list;
		};	
		
		this.addSurvey = function (key, value) {
			this.surveys[key] = value;
		};
		
		this.setSelectedSurvey = function (survey) {
			this.selectedSurvey = survey;
		};
		
		this.setSelectedQuestion = function (id) {
			this.selectedQuestion = id;
		};
		
		this.addQuestion = function (qId, language, value) {	
			var langQ = this.questions[qId];
			if(!langQ) {
				this.questions[qId] = new Object();
				langQ = this.questions[qId];
			}
			langQ[language] = value;
		};	
		
		// Set the list of views to the passed in array
		this.setViews = function (list) {
			this.views = list;
		};	
		
		// Set the passed in map into the maps object indexed by key
		this.setMap = function (key, value) {
			this.maps[key] = value;
		};
		
	}
	
	/*
	 * Model for Survey editing
	 */
	function Model() {
		
		this.survey = undefined;
		this.changes = [];
		this.currentChange = 0;
		this.savedSettings = undefined;
	
		
		// Save the survey
		this.save = function(callback) {
			
			var url="/surveyKPI/surveys/save/" + globals.gCurrentSurvey;
			var changesString = JSON.stringify(this.changes);		
			
			globals.model.setHasChanges(0);
			addHourglass();
			$.ajax({
				url: url,
				type: 'PUT',
				dataType: 'json',
				cache: false,
				data: { changes: changesString },
				success: function(data) {
					var responseFn = callback;
					var h = [],
						idx = -1,
						i;
					
					removeHourglass();
					
					// Reset the set of pending updates
					//getSurveyDetails(surveyDetailsDone);
					if(typeof responseFn === "function") { 
						responseFn();
					}
					
					// Report success and failure
					globals.model.lastChanges = data.changeSet;
					$('#successLabel .counter').html(data.success);
					$('#failedLabel .counter').html(data.failed);	
					
					if(data.success > 0) {
						h[++idx] = '<div class="alert alert-success" role="alert">';
						h[++idx] = '<p>';
						h[++idx] = data.success;
						h[++idx] = " changes successfully applied";
						h[++idx] = '</p>'
						h[++idx] = '<ol>';
						for(i = 0; i < data.changeSet.length; i++) {
							h[++idx] = addUpdateMessage(data.changeSet[i], false);
						}
						h[++idx] = '</ol>';
						h[++idx] = '</div>';
					}
					if(data.failed > 0) {
						h[++idx] = '<div class="alert alert-danger" role="alert">';
						h[++idx] = data.failed;
						h[++idx] = " changes failed";
						h[++idx] = '<ol>';
						for(i = 0; i < data.changeSet.length; i++) {
							h[++idx] = addUpdateMessage(data.changeSet[i], true);
						}
						h[++idx] = '</ol>';
						h[++idx] = '</div>';
					}
					bootbox.alert(h.join(""));

				},
				error: function(xhr, textStatus, err) {
					removeHourglass();
					if(xhr.readyState == 0 || xhr.status == 0) {
			              return;  // Not an error
					} else {
						alert("Error: Failed to save survey: " + err);
					}
				}
			});	
			
		};
		
		// Update settings when the number of changes to apply changes 
		this.setHasChanges = function(numberChanges) {
			if(numberChanges === 0) {
				globals.model.changes = [];
				globals.model.currentChange = 0;
				$('.m_save_survey').addClass("disabled").attr("disabled", true).find('.badge').html(numberChanges);
				
			} else {
				$('.m_save_survey').removeClass("disabled").attr("disabled", false).find('.badge').html(numberChanges);
			}
		}
		
		// Save the settings for the survey
		this.save_settings = function() {
			
			var settings = JSON.stringify(this.getSettings());
			
			
			addHourglass();
			$.ajax({
				  type: "POST",
				  contentType: "application/json",
				  dataType: "json",
				  url: "/surveyKPI/surveys/save_settings/" + globals.gCurrentSurvey,
				  data: {
					  settings: settings
				  },
				  success: function(data, status) {
					  removeHourglass();
					  globals.model.savedSettings = settings;
					  $('#save_settings').attr("disabled", true);
					  
					  $('.formName').html(globals.model.survey.displayName);
					  $('#settingsModal').modal("hide");
				  },
				  error: function(xhr, textStatus, err) {
					  removeHourglass();  	
					  if(xhr.readyState == 0 || xhr.status == 0) {
					      return;  // Not an error
					 } else {
						 bootbox.alert("Error saving settings. " + xhr.responseText);
					 }
				  }
			});
			
		};
		
		// Apply the current change
		this.doChange = function() {
			
			var change = this.changes[this.currentChange];
			var i,
				question;

			if(change.type === "label") {
				for(i = 0; i < change.items.length; i++) {
					label = change.items[i];
					
					if(label.form) {
						this.survey.forms[label.formIdx].questions[label.questionIdx].
							labels[label.language][label.element] = label.newVal;
					} else if(label.optionListIdx) {
						this.survey.optionLists[label.optionListIdx][label.optionIdx].
							labels[label.language][label.element] = label.newVal;
					}
				}
			} else {
				alert("Error: unknown item type: " + change.type);
			}
			
		}
		
		this.undo = function() {
			var change = this.changes[this.currentChange--];
			this.setHasChanges(this.changes.length);
			if(change.type === "label") {
				for(i = 0; i < change.labels.length; i++) {
					label = change.labels[i];
					if(label.form) {
						this.survey.forms[label.formIdx].questions[label.questionIdx].
							labels[label.language][label.element] = label.oldVal;
					} else if(question.optionList) {
						this.survey.optionLists[label.optionListIdx][label.optionIdx].
							labels[label.language][label.element] = label.oldVal;
					}
				}
			} 
		}
		
		this.redo = function() {
			if(this.currentChange < this.changes.length - 1) {
				this.currentChange++;
				this.setHasChanges(this.changes.length);
				this.doChange();
			}
		}
		
		// Modify a label for a question or an option called from translate where multiple questions can be modified at once if the text is the same
		this.modLabel = function(language, changedQ, newVal, element) {
			
			var labelMod = {
					type: "label",
					items: []
			}
			
			var i,
				label = {},
				item,
				qname;
				
			
			for(i = 0; i < changedQ.length; i++) {
				label = {};

				// For questions
				if(typeof changedQ[i].form !== "undefined") {
					label.formIdx = changedQ[i].form;
					label.questionIdx = changedQ[i].question;
					label.type = "question";
					item = this.survey.forms[label.formIdx].questions[label.questionIdx];
					label.name = item.name;	
					label.qId = item.id;
				} else {
					// For options
					label.optionListIdx = changedQ[i].optionList;
					qname = changedQ[i].qname;
					label.optionIdx = changedQ[i].option;
					item = this.survey.optionLists[label.optionListIdx][label.optionIdx];
					label.type = "option";
					label.name = label.optionListIdx;		// The option list name
				}
					
				label.newVal = newVal;
				label.oldVal = item.labels_orig[language][element];
				label.element = element;
				label.language = language;
				
				label.languageName = this.survey.languages[language];			// For logging the event
				var form = this.survey.forms[label.formIdx];

				if(item.text_id) {
					label.key = item.text_id;
				} else {
					// Create reference for this new Label		
					if(typeof changedQ[i].form !== "undefined") {
						label.key = "/" + form.name + "/" + item.name + ":label";	// TODO hint
					} else {
						label.key = "/" + form.name + "/" + qname + "/" + item.name + ":label";
					}
				}

				labelMod.items.push(label);
			}
			
			removeDuplicateChange(this.changes, labelMod);
			if(labelMod.items[0].newVal !== labelMod.items[0].oldVal) {		// Add if the value has changed
				this.currentChange = this.changes.push(labelMod) - 1;
				this.doChange();				// Apply the current change
			}
			this.setHasChanges(this.changes.length);
		};
		
		/*
		 * Functions for managing settings
		 */
		this.getSettings = function() {
			var current =  {
				displayName: $('#set_survey_name').val(),
				p_id: $('#set_project_name option:selected').val(),
				def_lang: $('#set_default_language option:selected').text()
			}
			
			// Update the model to reflect the current values
			this.survey.displayName = current.displayName;
			this.survey.p_id = current.p_id;
			this.survey.def_lang = current.def_lang;
			
			return current;
		} 
		
		this.setSettings = function() {
			this.savedSettings = JSON.stringify({
				displayName: this.survey.displayName,
				project_id: String(this.survey.p_id)
			});
		} 
		
		this.settingsChange = function() {
			var current = globals.model.getSettings();
			
			if(JSON.stringify(current) !== globals.model.savedSettings) {
				$('#save_settings').attr("disabled", false);
			} else {
				$('#save_settings').attr("disabled", true);
			}
		}
	}
	
	/*
	 * If this is the second time an element has been modified then remove the original modification
	 * Only check the first item as if the first item is a duplicate all should be duplicates
	 */
	function removeDuplicateChange(changes, change) {
		var j, 
			item,
			newItem;
		
		for(j = 0; j < changes.length; j++) {
			
				item = changes[j].items[0];
				newItem = change.items[0];

				if(item.language === newItem.language 
						&& item.type === newItem.type) {
					if(
							(newItem.type === "question" && 
									newItem.questionIdx === item.questionIdx &&
									newItem.formIdx === item.formIdx) ||
							(newItem.type === "option" && 
									newItem.optionIdx === item.optionIdx &&
									newItem.name === item.name) ) {
						
						changes.splice(j,1);	// Remove this item
						return;					
						
					}
				}
			
		}
	}
	
	function addUpdateMessage(data, forError) {
		var h = [],
			idx = -1,
			j;
		
		if(data.updateFailed && forError || !data.updateFailed && !forError) {
			for(j = 0; j < data.items.length; j++) {
				h[++idx] = '<li>';
				h[++idx] = 'Question: ';
				h[++idx] = data.items[j].name;
				h[++idx] = ' ';
				h[++idx] = data.errorMsg;
				h[++idx] = '</li>'
			}
		}
		
		return h.join("");
	}

	
});