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
		gLoggedInUser: undefined,
		gEditingReportProject: undefined,   		// Set if fieldAnalysis called to edit a report
		gIsAdministrator: false,
		gIsOrgAdministrator: false,
		gViewIdx: 0,
		gSelector: new Selector(),
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
		this.save = function() {
			
			var url="/surveyKPI/surveys/save/" + globals.gCurrentSurvey;
			var changesString = JSON.stringify(this.changes);
			console.log("Saving as: " + url);			
			
			addHourglass();
			$.ajax({
				url: url,
				type: 'PUT',
				dataType: 'json',
				cache: false,
				data: { changes: changesString },
				success: function(data) {
					removeHourglass();
					this.changes = [];
					this.currentChange = 0;
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
		
		// Save the settings for the survey
		this.save_settings = function() {
			
			var settings = JSON.stringify(this.getSettings());
			
			
			addHourglass();
			$.ajax({
				  type: "POST",
				  contentType: "application/json",
				  dataType: "json",
				  url: "/surveyKPI/surveys/" + globals.gCurrentSurvey + "/save_settings",
				  data: {
					  settings: settings
				  },
				  success: function(data, status) {
					  removeHourglass();
					  this.savedSettings = settings;
					  $('#save_settings').attr("disabled", true);
				  }, error: function(data, status) {
					  removeHourglass();
					  alert("Failed to update survey settings"); 
				  }
			});
			
		};
		
		// Apply the current change
		this.doChange = function() {
			
			var item = this.changes[this.currentChange];
			var i,
				question;

			if(item.type === "q") {
				for(i = 0; i < item.questions.length; i++) {
					question = item.questions[i];
					
					if(question.form) {
						this.survey.forms[question.form].questions[question.question].
							labels[question.language][question.element] = question.newVal;
					} else if(question.optionList) {
						this.survey.optionLists[question.optionList][question.option].
							labels[question.language][question.element] = question.newVal;
					}
				}
			} else {
				alert("Error: unknown item type: " + item.type);
			}
			
		}
		
		this.undo = function() {
			var item = this.changes[this.currentChange--];
			if(item.type === "q") {
				for(i = 0; i < item.questions.length; i++) {
					question = item.questions[i];
					if(question.form) {
						this.survey.forms[question.form].questions[question.question].
							labels[question.language][question.element] = question.oldVal;
					} else if(question.optionList) {
						this.survey.optionLists[question.optionList][question.option].
							labels[question.language][question.element] = question.oldVal;
					}
				}
			} 
		}
		
		this.redo = function() {
			if(this.currentChange < this.changes.length - 1) {
				this.currentChange++;
				this.doChange();
			}
		}
		
		// Modify a question or an option
		this.modQuestion = function(language, changedQ, newVal, element) {
			
			var questionMod = {
					type: "q",
					questions: []
			}
			
			var i,
				question = {},
				item;
			
			for(i = 0; i < changedQ.length; i++) {
				question = {};

				// For questions
				if(typeof changedQ[i].form !== "undefined") {
					question.form = changedQ[i].form;
					question.question = changedQ[i].question;
					item = this.survey.forms[question.form].questions[question.question];
					//question.oldVal = this.survey.forms[question.form].questions[question.question].labels[language][element];
				} else {
					// For options
					question.optionList = changedQ[i].optionList;
					question.option = changedQ[i].option;
					item = this.survey.optionLists[question.optionList][question.option];
					//question.oldVal = this.survey.optionLists[question.optionList][question.option].labels[language][element];
				}
				
				
					
				question.newVal = newVal;
				question.oldVal = item.labels[language][element];
				question.element = element;
				question.language = language;
				
				// The following items are to write the change to the database
				question.languageName = this.survey.languages[language];
				//question.transId = this.survey.forms[question.form].questions[question.question].text_id;
				question.transId = item.text_id;
				questionMod.questions.push(question);
			}
			
			this.currentChange = this.changes.push(questionMod) - 1;
			this.doChange();				// Apply the current change
		};
		
		this.getSettings = function() {
			var current =  {
				displayName: $('#set_survey_name').val(),
				p_id: $('#set_project_name option:selected').val(),
				def_lang: $('#set_default_language option:selected').text(),
				sscList: this.survey.sscList,
				surveyManifest: this.survey.surveyManifest
			}
			
			// Update the model to reflect the current values
			this.survey.displayName = current.displayName;
			this.survey.p_id = current.p_id;
			this.survey.def_lang = current.def_lang;
			console.log("updated model");
			console.log(this.survey);
			
			return current;
		} 
		
		this.setSettings = function() {
			this.savedSettings = JSON.stringify({
				displayName: this.survey.displayName,
				project_id: String(this.survey.p_id),
				def_lang: this.survey.def_lang,
				sscList: this.survey.sscList
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
	

	
});