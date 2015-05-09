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
		addPanelStyle: addPanelStyle,
		addQType: addQType,
		addFeaturedProperty: addFeaturedProperty,
		addQuestions: addQuestions,
		addMedia: addMedia,
		refresh: refresh
	};
	
	function addOneQuestion(question, formIndex, qIndex) {
		var h = [],
			idx = -1;
		
		h[++idx] = addPanelStyle(question.type, formIndex, qIndex);
		
		h[++idx] = '<div class="panel-heading">';
			h[++idx] = addNewQuestionButton(); 
			h[++idx] = '<table class="table">';
				h[++idx] = '<td class="q_type_col">';
					h[++idx] = addQType(question.type, question.calculation);
				h[++idx] = '</td>';
				h[++idx] = '<td class="q_name_col"><input class="qname form-control" value="';
					h[++idx] = question.name;
				h[++idx] = '" type="text"></td>';
				h[++idx] = addFeaturedProperty(question, formIndex, qIndex, undefined, undefined);
				h[++idx] = '<td class="q_icons_col">';
					h[++idx] = '<a data-toggle="collapse"  href="#collapse';
					h[++idx] = globals.gElementIndex;
					h[++idx]='"><span class="glyphicon glyphicon-collapse-down edit_collapse_icon"></span></a>';
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
			h[++idx] = '<ol>';
		} else { 
			h[++idx] = '</div>';
			h[++idx] = '</li>';
		}
		
		return h.join("");
	}
	
	function addNewQuestionButton() {
		var h = [],
			idx = -1;
		h[++idx] = '<button type="button" class="add_question btn btn-success add_button" data-locn="before" data-index="';
		h[++idx] = globals.gElementIndex;
		h[++idx] = '"><i class="glyphicon glyphicon-plus"></i></button>';
		
		return h.join('');
	}
	
	function addPanelStyle(type, formIndex, qIndex) {
		
		var h = [],
			idx = -1;
		
		h[++idx] = '<li class="panel editor_element';
		if(formIndex === 0 && qIndex === 0) { // First editor element in the form
			h[++idx] = ' first_element ';
		}
		if(type === "begin repeat" || type === "begin group") {
			h[++idx] = ' panel-warning" id="question';
		} else {
			h[++idx] = ' panel-success" id="question';	
		}
		h[++idx] = ++globals.gElementIndex;
		h[++idx] = '">';
		
		return h.join('');
	}
	
	function addQType(type, calculation) {
		
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
		h[++idx] = '" data-fid="';
		h[++idx] = fId;
		h[++idx] = '" data-id="';
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
			if((type === "question" && (question.source != "user" && question.type != "begin group" && question.type != "begin repeat") || question.calculation)) {
				h[++idx] = ' readonly tabindex="-1">';
				h[++idx] = selLabel;
				h[++idx] = ' not required';
			} else {
				h[++idx] = ' tabindex="';
				h[++idx] = globals.gElementIndex;
				h[++idx] = '">';
				if(selProperty === "label") { 
					h[++idx] = question.labels[globals.gLanguage].text;
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
		h[++idx] = '</table>';

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
	function addQuestions(form, fId) {
		var i,
			question,
			h = [],
			idx = -1;
		
		if(form) {
			addQuestionSequence(form);		// Add an array holding the question sequence if it does not already exist
			for(i = 0; i < form.qSeq.length; i++) {
				question = form.questions[form.qSeq[i]];
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
				h[++idx] = addOneQuestion(question, fId, form.qSeq[i]);
			}
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
		
		return $('#formList');		// Return the context of the updated HTML so that events can be applied

		//enableDragablePanels();
		

	}

});