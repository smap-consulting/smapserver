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
		addOneOption: addOneOption,
		addPanelStyle: addPanelStyle,
		addQType: addQType,
		addFeaturedProperty: addFeaturedProperty,
		addQuestions: addQuestions,
		addMedia: addMedia,
		refresh: refresh,
		refreshOptionLists: refreshOptionLists
	};
	
	function addOneQuestion(question, formIndex, qIndex, addNewButton, selProperty) {
		var h = [],
			idx = -1,
			questionId = "question" + formIndex + "_" + qIndex;
		
		selProperty = selProperty || $('#selProperty').val();
		
		if(addNewButton) {
			h[++idx] = addNewQuestionButton(false, false, questionId, formIndex, undefined, selProperty);
		}
		
		// Add the group to the group stack
		if(question.type === "begin group" && gGroupStacks[formIndex]) {
			gGroupStacks[formIndex].groupStack.push(question.name);
		}
		
		h[++idx] = addPanelStyle(question.type, formIndex, qIndex, question.error, questionId);
		h[++idx] = '<div class="panel-heading">';
			h[++idx] = addErrorMsg(question.errorMsg);
			h[++idx] = '<table class="table">';
				h[++idx] = '<td class="q_type_col">';
					h[++idx] = '<div class="question_type';
					if(question.published) {
						h[++idx] = ' disabled';
					}
					h[++idx] = '">';
					h[++idx] = addQType(question.type);
					h[++idx] = '</div>';
				h[++idx] = '</td>';
				h[++idx] = '<td class="q_name_col"><input class="qname form-control" value="';
					h[++idx] = question.name;
				h[++idx] = '" ';
				if(question.published) {				// Mark disabled if the question has been published
					h[++idx] = 'disabled="true" ';
				}
				h[++idx] = 'type="text"></td>';
				h[++idx] = addFeaturedProperty(question, formIndex, qIndex, undefined, undefined);
				h[++idx] = '<td class="q_icons_col">';
					h[++idx] = '<div class="btn-group">';
					h[++idx] = '<a button tabindex="-1" class="btn btn-default" data-toggle="collapse"  href="#collapse';
					h[++idx] = globals.gElementIndex;
					h[++idx]='"><span class="glyphicon glyphicon-collapse-down edit_icon"></span></a>';
					
					h[++idx] = '<button tabindex="-1" class="btn btn-default">';
					h[++idx]='<span class="glyphicon glyphicon-trash edit_icon delete_question" data-id="';
					h[++idx] = questionId;
					h[++idx]='"></span>';
					h[++idx]='</button>';
					 
				h[++idx] = '</td>';
				h[++idx] = '</table>';
		h[++idx] = '<div id="collapse';
		h[++idx] = globals.gElementIndex;
		h[++idx] = '" class="panel-body collapse';
		if(question.type.indexOf("select") === 0) {
			h[++idx] = ' selectquestion';
		}
		h[++idx] = '">';
		if(question.type === "begin repeat" || question.type === "geopolygon" || question.type === "geolinestring") {
			h[++idx] = addSubForm(formIndex, qIndex);
		} else if(question.type.indexOf("select") === 0) {
			if(!question.error) {	// Only add the options if the question it self does not have any errors
				
				h[++idx] = '<div class="option-controls">';
					h[++idx] = '<div class="row">';
						h[++idx] = '<div class="col-md-6"></div>';
						
						// A control to set option lists
						h[++idx] = '<div class="col-md-6">';
							h[++idx] = '<label>Choice List: </label>';
							h[++idx] = '<div class="input-group">';
								h[++idx] = '<select class="form-control option-lists">';
								h[++idx] = getOptionLists();
								h[++idx] = '</select>';
								h[++idx] = '<span class="input-group-addon">Edit</span>';
							h[++idx] = '</div>';
						h[++idx] = '</div>';
					h[++idx] = '</div>';
				h[++idx] = '</div>';
				
				h[++idx] = addOptions(question, formIndex);
			}
		} 
		
		if(question.type === "begin group") {	/* Add questions up to the end group to this panel */
			h[++idx] = '<ul class="list-unstyled">';
		} else { 
			h[++idx] = '</div>';
			h[++idx] = '</li>';
		}
		
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
	
	function addNewQuestionButton(after, topLevelForm, questionId, formIndex, formName, selProperty) {
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
		h[++idx] = '<button button tabindex="-1" id="addnew_';
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
	
	function addNewOptionButton(after, optionId, list_name, formIndex, qname) {
		var h = [],
			idx = -1,
			addButtonClass,
			locn;
		
		addButtonClass = after ? 'add_after_button add_button' : 'add_before_button add_button';
		locn = after ? 'after' : 'before';
		
		h[++idx] = '<li>';
		h[++idx] = '<button button tabindex="-1" id="addnewoption_';
		h[++idx] = globals.gNewOptionButtonIndex++;
		h[++idx] = '" type="button" class="add_option btn dropon ';
		h[++idx] = addButtonClass;
		h[++idx] = '" data-locn="';
		h[++idx] = locn;
		h[++idx] = '" data-oid="';
		h[++idx] = optionId;
		h[++idx] = '" data-list_name="';
		h[++idx] = list_name;
		h[++idx] = '" data-qname="';
		h[++idx] = qname;
		h[++idx] = '" data-fid="';
		h[++idx] = formIndex;
		h[++idx] = '">Add New Choice</button>';
		h[++idx] = '</li>';
		
		return h.join('');
	}
	
	function addPanelStyle(type, formIndex, qIndex, error, questionId) {
		
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
		h[++idx] = questionId;
		++globals.gElementIndex;
		h[++idx] = '"';
		
		// Add the question index and form index
		h[++idx] = ' data-fid="';
		h[++idx] = formIndex;
		h[++idx] = '" data-id="';
		h[++idx] = qIndex;
		
		
		h[++idx] = '">';
		
		return h.join('');
	}
	
	function addQType(type) {
		
		var i,
			types = globals.model.qTypes,
			h = [],
			idx = -1;
	
		for(i = 0; i < types.length; i++) {
			if(types[i].type === type) {
				h[++idx] = '<span class="has_tt" title="';
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
		
		h[++idx] = '<td class="q_label_col ';
		h[++idx] = type;
		h[++idx] = '" data-fid="';					// Deprecated (Now set in list element)
		h[++idx] = fId;
		h[++idx] = '" data-id="';					// Deprecated (Now set in list element)
		h[++idx] = qIndex;
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
			selLabel = $('#selProperty option:selected').text(),
			naMedia = '<div class="naMedia text-center">Media cannot be used with this question</div>';
		
			if(selProperty === "required") {		// Add a boolean type
				
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
			} else if(selProperty === "media") {
				h[++idx] = '<div class="row">';
				if(type === "question" && (question.inMeta || question.source != "user" || question.calculation)) {
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
			h[++idx] = '<textarea class="labelProp has_tt" title="';
			h[++idx] = type === "option" ? "Choice Label" : selLabel;
			h[++idx] = '" data-prop="';
			h[++idx] = selProperty;
			h[++idx] = '"';
			if(type === "question" && selProperty !== "appearance" && 
					((question.source != "user" && 
					question.type != "begin group" && 
					question.type != "begin repeat") || question.calculation)) {
				h[++idx] = ' readonly tabindex="-1">';
				h[++idx] = selLabel;
				h[++idx] = ' not required';
			} else {
				h[++idx] = '>';
				if(selProperty === "label") { 
					h[++idx] = question.labels[globals.gLanguage].text;
				} else if(selProperty === "hint") { 
					h[++idx] = question.labels[globals.gLanguage].hint;
				} else {
					h[++idx] = question[selProperty];
				}
			}
			h[++idx] = '</textarea>';
		}
		
		return h.join("");
	}
	
	
	/*
	 * Show the options
	 */
	function addOptions(question, formIndex) {
		var survey = globals.model.survey,
			optionList = survey.optionLists[question.list_name],
			oSeq,
			h = [],
			idx = -1,
			i,
			optionId = -1;
		
		addOptionSequence(optionList);		// Add an array holding the option sequence if it does not already exist
		oSeq = optionList.oSeq;
		h[++idx] = h[++idx] = '<ul class="list-unstyled">';
		if(oSeq) {
			for(i = 0; i < oSeq.length; i++) {
				optionId = "option_" + question.name + "_" + oSeq[i];
				h[++idx] = addOneOption(optionList.options[oSeq[i]], 
						formIndex, 
						oSeq[i], 
						question.list_name, 
						question.name, 
						true,
						optionId);
			}
			h[++idx] = addNewOptionButton(true, optionId, question.list_name, formIndex, question.name); 
		}
		h[++idx] = '</ul>';
		return h.join("");
	}

	/*
	 * Add a single option
	 */
	function addOneOption(option, formIndex, id, list_name, qname, addNewButton, optionId) {
		var h = [],
			idx = -1;
		
		if(addNewButton) {
			h[++idx] = addNewOptionButton(false, optionId, list_name, formIndex, qname);
		}
		
		h[++idx] = '<li class="editor_element option draggable';
		if(option.error) {
			h[++idx] = ' error';
		}
		h[++idx] = '" id="';
		h[++idx] = optionId;
		
		// Add the option index 
		h[++idx] = '" data-id="';
		h[++idx] = id;
		h[++idx] = '" data-fid="';					
		h[++idx] = formIndex;
		h[++idx] = '" data-qname="';
		h[++idx] = qname;
		h[++idx] = '" data-list_name="';
		h[++idx] = list_name;
		h[++idx] = '">';
		
		if(option.error) {
			h[++idx] = addErrorMsg(option.errorMsg);
		}
		
		h[++idx] = '<table class="table">';
		h[++idx] = '<td class="q_name_col"><input class="oname form-control has_tt" value="';
		h[++idx] = option.value;
		h[++idx] = '" type="text" title="Choice Value"></td>';
		h[++idx] = addFeaturedProperty(option, formIndex, id, list_name, qname);
		
		// Add button bar
		h[++idx] = '<td class="q_icons_col">';
		h[++idx] = '<div class="btn-group">';
		h[++idx] = '<button class="btn btn-default" tabindex="-1">';
		h[++idx] = '<span class="glyphicon glyphicon-trash edit_icon delete_option" data-id="';
		h[++idx] = optionId;
		h[++idx] = '"></span>';
		h[++idx] = '</button>';		
		h[++idx] = '</td>';
		
		h[++idx] = '</table>';
		h[++idx] = '</li>';

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
			selProperty = $('#selProperty').val();
		
		// Set the group counter for this form
		gGroupStacks[formIndex] = {
				groupStack: [],
				lastGroup: undefined
		}
		
		if(form) {
			addQuestionSequence(form);		// Add an array holding the question sequence if it does not already exist
			for(i = 0; i < form.qSeq.length; i++) {
				globals.gHasQuestions = true;
				question = form.questions[form.qSeq[i]];
				
				if(question.propertyType) {	// Ignore property type questions
					continue;
				}
				
				if(question.type === "end group") {
					groupButtonName = question.name.substring(0, question.name.indexOf('_groupEnd'));

					// Remove the group from the group stack and set the "last group" value
					gGroupStacks[formIndex].groupStack.pop();
					gGroupStacks[formIndex].lastGroup = groupButtonName;
					
					h[++idx] = addNewQuestionButton(true, false, lastRealQuestionId, formIndex, groupButtonName, selProperty);
					
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
					
					// The next "Add after" button should point to the group that just finished
					//lastRealQuestionId = "question" + formIndex + "_" + form.qSeq[i];
					
					continue;
				}
				if(question.type === "end repeat") {
					continue;
				}
				lastRealQuestionId = "question" + formIndex + "_" + form.qSeq[i];
				globals.hasQuestions = true;
				h[++idx] = addOneQuestion(question, formIndex, form.qSeq[i], true, selProperty);
				//console.log("Add: " + question.type + " : " + lastRealQuestionId);
			}
			if(form.parentFormIndex == -1) {
				topLevelForm = true;
				finalButtonName = "end";
			} else {
				finalButtonName = form.name;
			}
			h[++idx] = addNewQuestionButton(true, topLevelForm, lastRealQuestionId, formIndex, finalButtonName, selProperty); 	// Adds a question at the end of the form
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
		if(!form.qSeq) {
			form.qSeq = [];
			for(i = 0; i < form.questions.length; i++) {
				form.qSeq[i] = i;		// Assume initial sequence corresponds to order of questions
			}
		}
	}
	
	/*
	 * Add the array containing the option sequence
	 */
	function addOptionSequence(optionList) {
		if(!optionList.oSeq) {
			optionList.oSeq = [];
			for(i = 0; i < optionList.options.length; i++) {
				optionList.oSeq[i] = i;
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
	 * Show the form on the screen
	 */
	function refresh() {
		
		var i,
			survey = globals.model.survey,
			key,
			h = [],
			idx = -1;
		
		/*
		 * Process the questions in the top level form (parent is 0) 
		 *   Questions that are "begin repeat" type will link to sub level forms which are then processed in turn
		 * 
		 */
		globals.gElementIndex = 0;
		globals.gHasQuestions = false;
		globals.gNewQuestionButtonIndex = 0;
		globals.gNewOptionButtonIndex = 0;

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
		
		// Get the current list of collapsed panels
		gCollapsedPanels = [];
		$('.collapse.in', '#formList').each(function(){
			gCollapsedPanels.push($(this).closest('li').attr("id"));
		});
		
		// Update the form view
		$('#formList').html(h.join(""));
		
		// Restore collapsed panels
		for(i = 0; i < gCollapsedPanels.length; i++) {
			$('#' + gCollapsedPanels[i]).find('.collapse').addClass("in");
		}
		
		
		if(!globals.gHasQuestion) {
			// If there were no questions then set focus to the add new question button
			$('.add_final_button').focus();
		} 
		return $('#formList');		// Return the context of the updated HTML so that events can be applied

		

	}
	
	/*
	 * Refresh the select controls that show the available option lists
	 */
	function refreshOptionLists() {
		var $selector = $(".option-lists");
		$selector.html(getOptionLists());
	}
	
	function getOptionLists() {
		
		var lists = survey = globals.model.survey.optionLists,
		name,
		nameArray = [],
		h = [],
		idx = -1;

		// get the names into an array so they can be sorted
		for (name in lists) {
			if (lists.hasOwnProperty(name)) {
			    nameArray.push(name);
			}
		}
		// Sort array of list names
		nameArray.sort();
		
		// Create html
		for(i = 0; i < nameArray.length; i++) {
			h[++idx] = '<option value ="';
			h[++idx] = nameArray[i];
			h[++idx] = '">';
			h[++idx] = nameArray[i];
			h[++idx] = '</option>';
		}
		return h.join("");
	}

});