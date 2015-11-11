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

	return {	
		addOneQuestion: addOneQuestion,
		addOneOption: addOneOption,
		addPanelStyle: addPanelStyle,
		addQType: addQType,
		addFeaturedProperty: addFeaturedProperty,
		addQuestions: addQuestions,
		addMedia: addMedia,
		refresh: refresh
	};
	
	function addOneQuestion(question, formIndex, qIndex, addNewButton) {
		var h = [],
			idx = -1;
		
		if(addNewButton) {
			h[++idx] = addNewQuestionButton(false, false, "question" + (globals.gQuestionIndex + 1));
		}
		
		h[++idx] = addPanelStyle(question.type, formIndex, qIndex, question.error);
		h[++idx] = '<div class="panel-heading">';
			h[++idx] = addErrorMsg(question.errorMsg);
			h[++idx] = '<table class="table">';
				h[++idx] = '<td class="q_type_col">';
					h[++idx] = addQType(question.type);
				h[++idx] = '</td>';
				h[++idx] = '<td class="q_name_col"><input class="qname form-control" value="';
					h[++idx] = question.name;
				h[++idx] = '" type="text"></td>';
				h[++idx] = addFeaturedProperty(question, formIndex, qIndex, undefined, undefined);
				h[++idx] = '<td class="q_icons_col">';
					h[++idx] = '<div class="btn-group">';
					h[++idx] = '<a button tabindex="-1" class="btn btn-default" data-toggle="collapse"  href="#collapse';
					h[++idx] = globals.gElementIndex;
					h[++idx]='"><span class="glyphicon glyphicon-collapse-down edit_icon"></span></a>';
					
					h[++idx] = '<button tabindex="-1" class="btn btn-default">';
					h[++idx]='<span class="glyphicon glyphicon-trash edit_icon delete_question" data-id="question';
					h[++idx] = globals.gQuestionIndex;
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
			h[++idx] = addSubForm(question, globals.model.survey.forms[formIndex].id);
		} else if(question.type.indexOf("select") === 0) {
			h[++idx] = addOptions(question, formIndex);
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
	
	function addNewQuestionButton(after, topLevelForm, questionId) {
		var h = [],
			idx = -1,
			addButtonClass,
			locn;
		
		addButtonClass = after ? 'add_after_button add_button' : 'add_before_button add_button';
		locn = after ? 'after' : 'before';
		
		if(topLevelForm && locn === "after") {
			addButtonClass += ' add_final_button';
		}
		
		h[++idx] = '<li>';
		h[++idx] = '<button button tabindex="-1" id="addnew_';
		h[++idx] = globals.gNewQuestionButtonIndex++;
		h[++idx] = '" type="button" class="add_question btn dropon ';
		h[++idx] = addButtonClass;
		if(globals.gNewQuestionButtonIndex == 1) { // First button in the form
			h[++idx] = ' first_element ';
		}
		h[++idx] = '" data-locn="';
		h[++idx] = locn;
		h[++idx] = '" data-qid="';
		h[++idx] = questionId;
		h[++idx] = '">Add New Question</button>';
		h[++idx] = '</li>';
		
		return h.join('');
	}
	
	function addPanelStyle(type, formIndex, qIndex, error) {
		
		var h = [],
			idx = -1;
		
		h[++idx] = '<li class="panel editor_element draggable';

		if(type === "begin repeat" || type === "begin group") {
			h[++idx] = ' panel-warning" id="question';
		} else {
			if(error) {
				h[++idx] = ' panel-danger" id="question';
			} else {
				h[++idx] = ' panel-success" id="question';
			}
		}
		h[++idx] = ++globals.gQuestionIndex;
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
				h[++idx] = '<span class="question_type has_tt" title="';
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
			selLabel = $('#selProperty :selected').text(),
			naMedia = '<div class="naMedia text-center">Media cannot be used with this question</div>';
		
		 if(selProperty === "media") {
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
			h[++idx] = '<textarea class="labelProp" placeholder="';
			h[++idx] = selLabel;
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
			i;
		
		addOptionSequence(optionList);		// Add an array holding the option sequence if it does not already exist
		oSeq = optionList.oSeq;
		if(oSeq) {
			for(i = 0; i < oSeq.length; i++) {
				h[++idx] = addOneOption(optionList.options[oSeq[i]], formIndex, oSeq[i], question.list_name, question.name);
			}
			h[++idx] = addNewOptionButton(true); 
		}
		return h.join("");
	}

	/*
	 * Add a single option
	 */
	function addOneOption(option, fId, id, list_name, qname) {
		var h = [],
			idx = -1;
		
		if(option.error) {
			h[++idx] = '<div class="editor_element option_error">';
		} else {
			h[++idx] = '<div class="editor_element">';
		}	
		h[++idx] = addNewOptionButton();
		h[++idx] = addErrorMsg(option.errorMsg);
		h[++idx] = '<table class="table" id="option';
		h[++idx] = ++globals.gOptionIndex;
		globals.gElementIndex++;
		h[++idx] = '">';
		h[++idx] = '<td class="q_name_col"><input class="oname form-control" value="';
		h[++idx] = option.value;
		h[++idx] = '" type="text"></td>';
		h[++idx] = addFeaturedProperty(option, fId, id, list_name, qname);
		
		// Add button bar
		h[++idx] = '<td class="q_icons_col">';
		h[++idx] = '<div class="btn-group">';
		h[++idx] = '<button class="btn btn-default">';
		h[++idx]='<span class="glyphicon glyphicon-trash edit_icon delete_option" data-id="option';
		h[++idx] = globals.gOptionIndex;
		h[++idx]='"></span>';
		h[++idx]='</button>';		
		h[++idx] = '</td>';
		
		h[++idx] = '</table>';
		h[++idx] = '</div>';

		return h.join("");
	}
	
	function addNewOptionButton(after) {
		var h = [],
			idx = -1;
		h[++idx] = '<button type="button" class="add_option btn btn-primary ';
		h[++idx] = after ? 'add_button_now' : 'add_button';
		h[++idx] = '" data-locn="';
		h[++idx] = after ? 'after" ' : 'before" ';
		h[++idx] = '" data-index="';
		h[++idx] = globals.gOptionIndex;
		h[++idx] = '"><i class="glyphicon glyphicon-plus"></i></button>';
		
		
		return h.join('');
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
			if(forms[i].parentform === parentId && forms[i].parentQuestion === question.id) {			
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
	function addQuestions(form, fIndex) {
		var i,
			question,
			h = [],
			idx = -1,
			topLevelForm = false,
			lastRealQuestionId = -1;
		
		if(form) {
			addQuestionSequence(form);		// Add an array holding the question sequence if it does not already exist
			for(i = 0; i < form.qSeq.length; i++) {
				question = form.questions[form.qSeq[i]];
				// Ignore the following questions
				if(question.name === '_task_key' || 
						question.inMeta ||
						question.name === 'meta' || 
						question.name === 'meta_groupEnd') {
					continue;
				}
				if(question.type === "end group") {
					h[++idx] = addNewQuestionButton(true, false, lastRealQuestionId);
					h[++idx] = '</ul>';
					h[++idx] = '</div>';
					h[++idx] = '</li>';
					
					continue;
				}
				if(question.type === "end repeat") {
					continue;
				}
				lastRealQuestionId = "question" + (globals.gQuestionIndex + 1);
				h[++idx] = addOneQuestion(question, fIndex, form.qSeq[i], true);
			}
			if(form.parentform == 0) {
				topLevelForm = true;
			}
			h[++idx] = addNewQuestionButton(true, topLevelForm, lastRealQuestionId); 	// Adds a question at the end of the form
		}
		return h.join("");
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
		globals.gQuestionIndex = 0;
		globals.gNewQuestionButtonIndex = 0;
		globals.gOptionIndex = 0;
		if(survey) {
			if(survey.forms && survey.forms.length > 0) {
				for(i = 0; i < survey.forms.length; i++) {
					if(survey.forms[i].parentform == 0) {
						h[++idx] = addQuestions(survey.forms[i], i);
						break;
					}
				}
			}
		}
		
		// Get the current list of collapsed panels
		gCollapsedPanels = [];
		$('.in').each(function(){
			gCollapsedPanels.push($(this).attr("id"));
		});
		
		// Update the form view
		$('#formList').html(h.join(""));
		
		// Restore collapsed panels
		for(i = 0; i < gCollapsedPanels.length; i++) {
			$('#' + gCollapsedPanels[i]).addClass("in");
		}
		
		
		if(globals.gQuestionIndex == 0) {
			// If there were no questions then set focus to the add new question button
			$('.add_final_button').focus();
		} else {
			// Set focus to name of first question
			var $fe = $('input', '.container').first();
			$('input', '.container').first().focus();
		}
		return $('#formList');		// Return the context of the updated HTML so that events can be applied

		

	}

});