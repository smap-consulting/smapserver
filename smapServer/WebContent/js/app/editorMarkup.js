/*
This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
uSMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

*/

/*
 * Functions for manipulating a question in the editor
 */

"use strict";

define([
         'jquery',
         'modernizr',
         'app/localise',
         'app/globals'], 
		function($, modernizr, lang, globals) {

	var gGroupStacks = [];
	
	return {	
		addOneQuestion: addOneQuestion,
		addPanelStyle: addPanelStyle,
		addQType: addQType,
		addFeaturedProperty: addFeaturedProperty,
		addQuestions: addQuestions,
		addMedia: addMedia,
		refresh: refresh,
		includeQuestion: includeQuestion,
		addQuestionSequence: addQuestionSequence,
		refreshFeaturedProperties: refreshFeaturedProperties
	};
	
	/*
	 * Add a single question
	 */
	function addOneQuestion(form, question, formIndex, qIndex, addNewButton, selProperty, update) {
		var h = [],
			idx = -1,
			questionId = "question" + formIndex + "_" + qIndex;
		
		// Support the legacy geopolygon and geolinestring types
		if(question.type === "geopolygon" || question.type === "geolinestring") {
			question.type = "begin repeat";
		}
		
		globals.gHasItems = true;
		if(!update) {					// Only increment maxQuestion if a new question is being added
			form.maxQuestion++;
		}
		
		selProperty = selProperty || globals.gSelProperty;
		
		if(addNewButton) {
			h[++idx] = addNewQuestionButton(false, false, formIndex, undefined, selProperty);
		}
		
		// Add the group to the group stack
		if(question.type === "begin group" && gGroupStacks[formIndex]) {
			gGroupStacks[formIndex].groupStack.push(question.name);
		}
		
		h[++idx] = addPanelStyle(question.type, formIndex, qIndex, question.error, questionId, undefined);
		h[++idx] = '<div class="panel-heading">';
			h[++idx] = addErrorMsg(question.errorMsg);
			h[++idx] = '<div class="row">';
				
			// Add question type
				h[++idx] = '<div class="col-xs-2 ">';
					h[++idx] = '<div class="question_type';
						if(question.published) {
							h[++idx] = ' readonly';
						}
						h[++idx] = '">';
						h[++idx] = addQType(question.type);
					h[++idx] = '</div>';
				h[++idx] = '</div>';	// End of question type cell
				
				// Add name and featured property cell
				h[++idx] = '<div class="col-xs-8"><div class="row">';
					
					// Add question name cell
					h[++idx] = '<div class="col-xs-12 col-md-3"><input class="qname form-control has_tt" title="Question Name" value="';
						h[++idx] = question.name;
						h[++idx] = '" ';
						
						if(question.published) {				// Mark disabled if the question has been published
							h[++idx] = 'readonly="true" ';
						}
					h[++idx] = 'type="text"></div>';

					// Add feature property cell
					h[++idx] = addFeaturedProperty(question, formIndex, qIndex, undefined, undefined);
				h[++idx] = '</div></div>';		// End of name and featured property cell
				
				// Add buttons
				h[++idx] = '<div class="col-xs-2 q_icons_col">';
					h[++idx] = '<div class="btn-group">';
						if(question.type === "begin repeat" 
								|| question.type === "begin group") {
							
							h[++idx] = '<a button tabindex="-1" class="btn btn-default" data-toggle="collapse"  href="#collapse';
							h[++idx] = globals.gElementIndex;
							h[++idx]='"><span class="glyphicon glyphicon-chevron-down edit_icon"></span></a>';
						} else if(question.type.indexOf("select") === 0) {
						
							h[++idx] = '<a button tabindex="-1" class="btn btn-default edit_choice" ';
							h[++idx]='"><span class="glyphicon glyphicon-edit edit_icon"></span></a>';
							
						}
						
						h[++idx] = '<button tabindex="-1" class="btn btn-default delete_question" data-id="';
						h[++idx] = questionId;
						h[++idx] = '">';
						h[++idx]='<span class="glyphicon glyphicon-remove-circle edit_icon"></span>';
						h[++idx]='</button>';
					 
					h[++idx] = '</div>';
				h[++idx] = '</div>';		// End of button cell
				h[++idx] = '</div>';		// End of row
		h[++idx] = '<div id="collapse';
		h[++idx] = globals.gElementIndex;
		h[++idx] = '" class="panel-body collapse';
		if(question.type.indexOf("select") === 0) {
			h[++idx] = ' selectquestion';
		}
		h[++idx] = '">';
		if(question.type === "begin repeat") {
			
			h[++idx] = '<div class="question-controls">';
			h[++idx] = '<div class="row">';
				h[++idx] = '<div class="col-md-6"></div>';		
				// A control to set repeat count
					h[++idx] = '<div class="col-md-6">';
						h[++idx] = '<label>Repeat Count: </label>';
						h[++idx] = '<div class="input-group">';
							h[++idx] = '<input class="form-control repeat-counts" value="';
							h[++idx] = question.calculation;
							h[++idx] = '">';
						h[++idx] = '</div>';
					h[++idx] = '</div>';
				h[++idx] = '</div>';
			h[++idx] = '</div>';
		
			h[++idx] = addSubForm(formIndex, qIndex);
			
		} 
		
		if(question.type === "begin group") {	/* Add questions up to the end group to this panel */
			h[++idx] = '<ul class="list-unstyled">';
		} else { 
			h[++idx] = '</div>';
			h[++idx] = '</li>';
		}
		
		return h.join("");
	}
	

	/*
	 * Add a single option list element to the choices view
	 */
	function addOneOptionList(list_name, addNewButton, selProperty) {
		
		var h = [],
			idx = -1,
			itemId = "ol_" + list_name,
			optionList = globals.model.survey.optionLists[list_name];
		
		globals.gHasItems = true;
		
		selProperty = selProperty || globals.gSelProperty;
		
		if(addNewButton) {
			h[++idx] = addNewQuestionButton(false, false, undefined, undefined, selProperty);		
		}
		
		h[++idx] = addPanelStyle("choices", undefined, undefined, false, itemId, list_name);
		h[++idx] = '<div class="panel-heading">';
		h[++idx] = addErrorMsg(optionList.errorMsg);
			h[++idx] = '<div class="row">';
				
				// Add name
				h[++idx] = '<div class="col-xs-8"><div class="row">';
					
					// Add choice name cell
					h[++idx] = '<div class="col-xs-12 col-md-3"><input class="olname form-control has_tt" title="List Name" value="';
						h[++idx] = list_name;
						h[++idx] = '" ';
					h[++idx] = 'type="text"></div>';

				h[++idx] = '</div></div>';		// End of name 
				
				// Add buttons
				h[++idx] = '<div class="col-xs-2 q_icons_col">';
					h[++idx] = '<div class="btn-group">';
					
						h[++idx] = '<a button tabindex="-1" class="btn btn-default edit_choice" ';
						h[++idx]='"><span class="glyphicon glyphicon-edit edit_icon"></span></a>';
					
						//h[++idx] = '<a button tabindex="-1" class="btn btn-default" data-toggle="collapse"  href="#collapse';
						//h[++idx] = globals.gElementIndex;
						//h[++idx]='"><span class="glyphicon glyphicon-chevron-down edit_icon"></span></a>';	
						
						h[++idx] = '<button tabindex="-1" class="btn btn-default">';
						h[++idx]='<span class="glyphicon glyphicon-remove-circle edit_icon delete_ol" data-id="';
						h[++idx] = itemId;
						h[++idx]='"></span>';
						h[++idx]='</button>';
					 
					h[++idx] = '</div>';
				h[++idx] = '</div>';		// End of button cell
				h[++idx] = '</div>';		// End of row
		h[++idx] = '</li>';
		
		
		return h.join("");
	}
	
	function addErrorMsg(msg) {
		var h = [],
			idx = -1;
		
		h[++idx] = '<p class="error-msg">';
		h[++idx] = msg;
		h[++idx] = '</p>';
		return h.join("");
	}
	
	function addNewQuestionButton(after, topLevelForm, formIndex, formName, selProperty) {
		var h = [],
			idx = -1,
			addButtonClass,
			locn;
		
		//console.log(" Group Stack: " + gGroupStacks[formIndex].groupStack.join(",") + " : Last group: " + gGroupStacks[formIndex].lastGroup);
		
		addButtonClass = after ? 'add_after_button add_button' : 'add_before_button add_button';
		locn = after ? 'after' : 'before';
		
		if(topLevelForm && locn === "after") {
			addButtonClass += ' add_final_button';
		}
		
		h[++idx] = '<li>';
		if(selProperty === "group") {
			h[++idx] = '<span class="has_tt" title="End the group here">';
		} else {
			h[++idx] = '<span class="has_tt" title="add question here">';
		}
		h[++idx] = '<button tabindex="-1" id="addnew_';
		h[++idx] = globals.gNewQuestionButtonIndex++;
		h[++idx] = '" type="button" class="add_question btn dropon ';
		h[++idx] = addButtonClass;
		if(globals.gNewQuestionButtonIndex == 1) { // First button in the form
			h[++idx] = ' first_element ';
		}	
		h[++idx] = '" data-locn="';
		h[++idx] = locn;
		//h[++idx] = '" data-qid="';
		//h[++idx] = questionId;
		h[++idx] = '" data-findex="';
		h[++idx] = formIndex;
		if(selProperty === "group") {
			h[++idx] = '" data-groups="';
			if(gGroupStacks[formIndex].groupStack.length > 0) {
				h[++idx] = gGroupStacks[formIndex].groupStack[0];
			}
			if(gGroupStacks[formIndex].groupStack.length > 0 && gGroupStacks[formIndex].lastGroup) {
				h[++idx] = ':';
			}
			if(gGroupStacks[formIndex].lastGroup) {
				h[++idx] = gGroupStacks[formIndex].lastGroup;
			} 
			
			if(topLevelForm) {
				h[++idx] = '">End group here'; 
			} else if(formName) {	
				h[++idx] = '" disabled="true">';
			} else {
				h[++idx] = '">';
			}
			
		} else {
			h[++idx] = '">Add New Question'; 
			if(formName) {
				h[++idx] = ' to ';
				h[++idx] = formName;
			}
		}
		h[++idx] = '</button>';
		h[++idx] = '</span>';
		h[++idx] = '</li>';
		
		return h.join('');
	}
	
	function addNewOptionListButton() {
		var h = [],
			idx = -1,
			locn;
		
		h[++idx] = '<li>';
	
		h[++idx] = '<button tabindex="-1" id="addnew_optionlist" ';
		h[++idx] = 'type="button" class="add_option_list add_button add_after_button add_final_button btn">';
		h[++idx] = 'Add New Choice List'; 	
		h[++idx] = '</button>';
		h[++idx] = '</li>';
		
		return h.join('');
	}
	
	/*
	 * Add the container for an editable element including its styling
	 */
	function addPanelStyle(type, formIndex, qIndex, error, elementId, list_name) {
		
		var h = [],
			idx = -1;
		
		h[++idx] = '<li class="panel editor_element question draggable';

		if(error) {
			h[++idx] = ' error ';
		}
		
		if(type === "begin repeat" || type === "begin group") {
			h[++idx] = ' panel-warning" id="';
		} else {
			h[++idx] = ' panel-success" id="';
		}
		h[++idx] = elementId;
		++globals.gElementIndex;
		h[++idx] = '"';
		
		// Add the question index and form index
		if(typeof formIndex !== "undefined") {
			h[++idx] = ' data-fid="';
			h[++idx] = formIndex;
			h[++idx] = '"';
		}
		if(typeof qIndex !== "undefined") {
			h[++idx] = ' data-id="';
			h[++idx] = qIndex;
			h[++idx] = '"';
		}
		if(typeof list_name !== "undefined") {
			h[++idx] = ' data-list_name="';
			h[++idx] = list_name;
			h[++idx] = '"';
		}
		
		
		h[++idx] = '>';
		
		return h.join('');
	}
	
	function addQType(type) {
		
		var i,
			types = globals.model.qTypes,
			h = [],
			idx = -1;
	
		for(i = 0; i < types.length; i++) {
			if(types[i].type === type) {
				h[++idx] = '<span class="has_tt" title="Question type: ';
				h[++idx] = types[i].name;
				h[++idx] = '">';
				if(types[i].glyphicon) {
					h[++idx] = '<span class="glyphicon glyphicon-';
					h[++idx] = types[i].glyphicon; 
					h[++idx] = ' edit_type"></span>';
				} else if(types[i].image) {
					h[++idx] = '<img class="edit_image" src="';
					h[++idx] = types[i].image; 
					h[++idx] = '">';
				} else if(types[i].text) {
					h[++idx] = '<span class="edit_type">';
					h[++idx] = types[i].text; 
					h[++idx] = '</span>';
				}
				h[++idx] = '</span>';
				break;
			}
		}	
		return h.join('');

	}
	
	/*
	 * One of the questions properties will be featured so that it can be edited in the header without expanding the question
	 */
	function addFeaturedProperty(question, fId, qIndex, list_name, qname) {
		
		var h = [],
			idx = -1,
			type = "question";
		
		if(list_name) {
			type = "option";
		}
		
		h[++idx] = '<div class="col-xs-12 col-md-9 ';
		h[++idx] = type;
	
		h[++idx] = '">';
		
		if(list_name) {
			type = "option";
		}
		h[++idx] = getFeaturedMarkup(question, type);
		
		h[++idx] = '</div>';
		return h.join("");
	}

	/*
	 * Get Featured Markup for the question
	 */
	function getFeaturedMarkup(question, type) {
		var h = [],
			idx = -1,
			i,
			linkedSurveys = globals.model.survey.linkedSurveys,
			selProperty = globals.gSelProperty,
			selLabel = globals.gSelLabel,
			naMedia = '<div class="naMedia text-center">' + localise.set["ed_namedia"] + '</div>';
		
			if(selProperty === "required" && type === "question"
				&& question.type != "calculate") {		// Add a boolean type
				
				h[++idx] = '<div class="row">';
				
				h[++idx] = '<div class="col-sm-6">';
			    h[++idx] = '<button type="button" class="btn labelButton ';
			    if(question[selProperty]) {
			    	h[++idx] = 'prop_yes" ';
			    } else {
			    	h[++idx] = 'prop_no" ';
			    }
			    h[++idx] = ' data-prop="';
				h[++idx] = selProperty;
				h[++idx] = '">';
				h[++idx] = '<span class="glyphicon ';
				if(question[selProperty]) {
			    	h[++idx] = 'glyphicon-ok-sign"> ';
			    	h[++idx] = localise.set["c_yes"];
			    } else {
			    	h[++idx] = 'glyphicon-remove-sign"> ';
			    	h[++idx] = localise.set["c_no"];
			    }
			    h[++idx] = '</span></button>';
			    h[++idx] = '</div>';
			    /*
			     * Add the text area for the required response text
			     */
			    h[++idx] = '<div class="col-sm-6">';
			    h[++idx] = '<textarea class="labelProp has_tt" title="';
			    h[++idx] = localise.set['ed_r_msg'];
				h[++idx] = '" data-prop="required_msg">';	
				h[++idx] = question["required_msg"];
				h[++idx] = '</textarea>';
			    h[++idx] = '</div>';
			    
				h[++idx] ='</div>';		// End Row
				
			} else if(selProperty === "readonly" && type === "question"
				&& question.type != "calculate") {		// Add a boolean type
				
				h[++idx] = '<div class="row">';
				
				h[++idx] = '<div class="col-sm-6">';
			    h[++idx] = '<button type="button" class="btn labelButton ';
			    if(question[selProperty]) {
			    	h[++idx] = 'prop_yes" ';
			    } else {
			    	h[++idx] = 'prop_no" ';
			    }
			    h[++idx] = ' data-prop="';
				h[++idx] = selProperty;
				h[++idx] = '">';
				h[++idx] = '<span class="glyphicon ';
				if(question[selProperty]) {
			    	h[++idx] = 'glyphicon-ok-sign"> Yes';
			    } else {
			    	h[++idx] = 'glyphicon-remove-sign"> No';
			    }
			    h[++idx] = '</span></button>';
			    h[++idx] = '</div>';
			   
			    
				h[++idx] ='</div>';		// End Row
				
			} else if(selProperty === "autoplay" && type === "question"
				&& question.type != "calculate") {		// Add a radio buttons to select autoplay status
				
				h[++idx] = '<div class="btn-group" role="group" aria-label="Autoplay Selection" data-toggle="buttons-radio">';
					// Add "none" autoplay option
					h[++idx] = '<button data-prop="autoplay" type="button" class="btn btn-default labelButton ';
					if(question[selProperty] == "none") {
						h[++idx] = "active";
					}
					h[++idx] = '" value="none">None</button>';
					
					// Add video autoplay option
					h[++idx] = '<button data-prop="autoplay" type="button" class="btn btn-default labelButton ';
					if(question[selProperty] == "video") {
						h[++idx] = "active";
					}
					h[++idx] = '" value="video">Video</button>';
					
					// Add audio autoplay option
					h[++idx] = '<button data-prop="autoplay" type="button" class="btn btn-default labelButton ';
					if(question[selProperty] == "audio") {
						h[++idx] = "active";
					}
					h[++idx] = '" value="audio">Audio</button>';
					
				h[++idx] = '</div>';
				
			} else if(selProperty === "linked_survey" && type === "question") {		// Add a select to get the linked survey
				
				h[++idx] = '<div class="row">';
				
				h[++idx] = '<div class="col-xs-6">';	// Start checkbox column
			    h[++idx] = '<button type="button" class="btn labelButton ';
			    if(question[selProperty]) {
			    	h[++idx] = 'prop_yes" ';
			    } else {
			    	h[++idx] = 'prop_no" ';
			    }
			    h[++idx] = ' data-prop="';
				h[++idx] = selProperty;
				h[++idx] = '">';
				h[++idx] = '<span class="glyphicon ';
				if(question[selProperty]) {
			    	h[++idx] = 'glyphicon-ok-sign"> ';
			    	h[++idx] = localise.set["ed_l"];
			    } else {
			    	h[++idx] = 'glyphicon-remove-sign"> ';
			    	h[++idx] = localise.set["ed_nl"];
			    }
			    h[++idx] = '</span></button>';
			    h[++idx] = '</div>';
			    /*
			     * Add the select question for the linked survey
			     */
			    h[++idx] = '<div class="col-xs-6">';	// Start select column
			    h[++idx] = '<div';
			    if(!question[selProperty]) {
			    	h[++idx] = ' style="display:none;"';
			    }
			    h[++idx] = '>';
				h[++idx] = '<div class="form-group">';
				h[++idx] = '<select class="form-control labelSelect"';
				h[++idx] = ' data-prop="';
					h[++idx] = selProperty;
				h[++idx] = '">';
				for(i = 0; i < linkedSurveys.length; i++) {
					h[++idx] = '<option value="';
					h[++idx] = linkedSurveys[i].id;
					h[++idx] = '"';
					if((question[selProperty] && question[selProperty] == linkedSurveys[i].id) || 
							(!question[selProperty] && i == 0)) {
						h[++idx] = ' selected';
					} 
					h[++idx] = '>';
					h[++idx] = linkedSurveys[i].name;
					h[++idx] = '</option>';
				}
				h[++idx] = '</select>';
				h[++idx] = '</div>';	// Form Group
				h[++idx] = '</div>';	// Show / No show

				h[++idx] ='</div>';		// End Col 
				h[++idx] ='</div>';		// End Row
				
			
				
			} else if(selProperty === "media" && question.type != "calculate") {
				h[++idx] = '<div class="row">';
				if(type === "question" && (question.inMeta || question.source != "user" )) {
					h[++idx] = '<div class="col-sm-4 col-sm-offset-4">';
					h[++idx] = naMedia;
					h[++idx] = '</div>';
				} else {
					h[++idx] = addMedia("Image", 
							question.labels[globals.gLanguage].image, 
							question.labels[globals.gLanguage].imageUrl, 
							question.labels[globals.gLanguage].imageThumb);
			        
					h[++idx] = addMedia("Video", 
							question.labels[globals.gLanguage].video, 
							question.labels[globals.gLanguage].videoUrl, 
							question.labels[globals.gLanguage].videoThumb);
					
					h[++idx] = addMedia("Audio", 
							question.labels[globals.gLanguage].audio, 
							question.labels[globals.gLanguage].audioUrl, 
							question.labels[globals.gLanguage].audioThumb);		
				}
				
			h[++idx] = '</div>';		// End of row

		} else {
			h[++idx] = '<textarea class="labelProp has_tt';
			if(question.type === 'calculate' && selProperty !== "appearance") {
				h[++idx] = ' calculate';
			}
			h[++idx] = '" title="';
			if(question.type === 'calculate' && selProperty !== "appearance") {
				h[++idx] = 'Add calculation here';
			} else {
				h[++idx] = type === "option" ? "Choice Label" : selLabel;
			}
			h[++idx] = '" data-prop="';
			h[++idx] = selProperty;
			h[++idx] = '"';
			if(type === "option" && selProperty !== "label") {
				h[++idx] = ' readonly tabindex="-1"';
			}
			if(type === "question" && selProperty !== "appearance" && 
					((question.source != "user" && 
					question.type != "begin group" && 
					question.type != "begin repeat"
						))) {
				h[++idx] = ' readonly tabindex="-1">';
				h[++idx] = selLabel;
				h[++idx] = ' not required';
			} else {
				h[++idx] = '>';
				if(question.type === 'calculate' && selProperty !== "appearance") {
					h[++idx] = question.calculation;
				} else {
					if(selProperty === "label") { 
						h[++idx] = question.labels[globals.gLanguage].text;
					} else if(selProperty === "hint") { 
						h[++idx] = question.labels[globals.gLanguage].hint;
					} else {
						h[++idx] = question[selProperty];
					}
				}
			}
			h[++idx] = '</textarea>';
		}
		
		return h.join("");
	}
	
	/*
	 * Add subform
	 */
	function addSubForm(parentFormIndex, parentQuestionIndex) {
		
		var h = [],
			idx = -1,
			//formName,
			survey = globals.model.survey,
			forms = survey.forms,
			i,
			form;
		
		h[++idx] = '<ol class="list-unstyled">';
		
		//formName = question.name;
		
		for(i = 0; i < forms.length; i++) {
			form = forms[i];
			if(forms[i].parentFormIndex === parentFormIndex && forms[i].parentQuestionIndex === parentQuestionIndex) {			
				h[++idx] = addQuestions(forms[i], i);
				break;
			}
		}
		
		h[++idx] = '</ol>';
		
		return h.join("");
	}

	/*
	 * Add the questions for a form
	 */
	function addQuestions(form, formIndex) {
		var i,
			question,
			h = [],
			idx = -1,
			topLevelForm = false,
			lastRealQuestionId = -1,
			finalButtonName,
			groupButtonName,
			selProperty = globals.gSelProperty;
		
		// Set the group counter for this form
		gGroupStacks[formIndex] = {
				groupStack: [],
				lastGroup: undefined
		}
		
		if(form) {
			addQuestionSequence(form);		// Add an array holding the question sequence if it does not already exist
			form.maxQuestion = 1;			// Add a number to use for the default next question in a form
			
			for(i = 0; i < form.qSeq.length; i++) {
				globals.gHasItems = true;
				question = form.questions[form.qSeq[i]];
				
				// Ignore property type questions, questions that have been deleted and meta questions like end repeat
				if(!includeQuestion(question)) {
					continue;
				}
				
				if(question.type === "end group") {
					groupButtonName = question.name.substring(0, question.name.indexOf('_groupEnd'));

					// Remove the group from the group stack and set the "last group" value
					gGroupStacks[formIndex].groupStack.pop();
					gGroupStacks[formIndex].lastGroup = groupButtonName;
					
					h[++idx] = addNewQuestionButton(true, false, formIndex, groupButtonName, selProperty);
					
					// End the group
					h[++idx] = '</ul>';
					h[++idx] = '</div>';
					h[++idx] = '</li>';
					
					// Add a dummy dom entry for this end group
					h[++idx] = '<li style="display:none;" id="';
					h[++idx] = "question" + formIndex + "_" + form.qSeq[i];
					h[++idx] = '"';
					h[++idx] = ' data-fid="';
					h[++idx] = formIndex;
					h[++idx] = '" data-id="';
					h[++idx] = form.qSeq[i];
					h[++idx] = '"></li>';
					
				
					continue;
				}

				h[++idx] = addOneQuestion(form, question, formIndex, form.qSeq[i], true, selProperty, false);
			}
			if(form.parentFormIndex == -1) {
				topLevelForm = true;
				finalButtonName = "end";
			} else {
				finalButtonName = form.name;
			}
			h[++idx] = addNewQuestionButton(true, topLevelForm, formIndex, finalButtonName, selProperty); 	// Adds a question at the end of the form
		}
		return h.join("");
	}

	/*
	 * Get the display sequence of the question by name
	 */
	function getIndexQuestionByName(name, form) {
		var i;
		
		for(i = 0; i < form.qSeq.length; i++) {
			if(form.questions[form.qSeq[i]].name === name) {
				return form.qSeq[i];
			}
		}
		alert("Could not find question with name: " + name);
		return 0;
	}
	
	/*
	 * Add the array containing the question sequence
	 * This will initially be the same as the order of questions but as new questions are added in the editor
	 *  then these new questions will be at the end of the question array
	 */
	function addQuestionSequence(form) {
		
		var i;
		
		if(!form.qSeq) {
			form.qSeq = [];
			for(i = 0; i < form.questions.length; i++) {
				form.qSeq[i] = i;		// Assume initial sequence corresponds to order of questions
			}
		}
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
	 * Refresh the content
	 */
	function refresh() {
		
		var content,
			i,
        	collapsedPanels = [];
		
		globals.gElementIndex = 0;
		globals.gHasItems = false;
		globals.gNewQuestionButtonIndex = 0;
		globals.gNewOptionButtonIndex = 0;
		
		if(globals.gIsQuestionView) {
			content = refreshQuestions();
		} else {
			content = refreshChoiceListView();
		}
		
		// Get the current list of collapsed panels
		$('.collapse.in', '#formList').each(function(){
			collapsedPanels.push($(this).closest('li').attr("id"));
		});
		
		// Update the content view
		$('#formList').html(content);
		
		// Restore collapsed panels
		for(i = 0; i < collapsedPanels.length; i++) {
			var $collapsedPanel = $('#' + collapsedPanels[i]);
			$collapsedPanel.find('.collapse').first().addClass("in");
			$collapsedPanel.find('.edit_icon.glyphicon-chevron-down').removeClass('glyphicon-chevron-down').addClass('glyphicon-chevron-up');
			
		}
		
		if(!globals.gHasItems) {
			// If there were no items then set focus to the add new item button
			$('.add_final_button').focus();
		} 
		
		return $('#formList');		// Return the context of the updated HTML so that events can be applied
	}

	/*
	 * Refresh the featured properties
	 */
	function refreshFeaturedProperties() {

        var survey = globals.model.survey;

		$('li.panel.question').each(function() {
			var $this = $(this);
			var fId = $this.data("fid");
			var id = $this.data("id");
			var question = survey.forms[fId].questions[id];

			$this.find('.question').html(getFeaturedMarkup(question, "question"));

		});

        return $('#formList');
	}
	
	/*
	 * Show the choice List view
	 */
	function refreshChoiceListView() {
		var h = [],
			survey = globals.model.survey,
			optionLists = survey.optionLists,
			idx = -1,
			name,
			nameArray = [],
			i;
		
		/*
		 * Process the choice lists in sequential order
		 */
		if(survey) {
			if(optionLists) {
				
				for (name in optionLists) {
					if (optionLists.hasOwnProperty(name)) {
					    nameArray.push(name);
					}
				}
				// Sort array of list names
				nameArray.sort();
				for(i = 0; i < nameArray.length; i++) {
					h[++idx] = addOneOptionList(nameArray[i], false, undefined);
				}
				h[++idx] = addNewOptionListButton(); 
			}
		}
		
		return h.join("");
	}
	
	/*
	 * Show the form on the screen
	 */
	function refreshQuestions() {
		
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
				for(i = 0; i < survey.forms.length; i++) {
					if(survey.forms[i].parentFormIndex == -1) {
						h[++idx] = addQuestions(survey.forms[i], i);
						break;
					}
				}
			}
		}


		gGroupStacks = [];		// save some memory
		
		return h.join("");


	}
	
	/*
	 * Return true for those questions that are of interest to the editor
	 */
	function includeQuestion(question) {
		if(question.propertyType || question.soft_deleted || question.type === "end repeat") {	
			return false;
		} else {
			return true;
		}
	}

});