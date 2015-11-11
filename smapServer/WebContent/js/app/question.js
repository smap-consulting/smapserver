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
         'app/globals',
         'app/editorMarkup',
         'app/changeset'], 
		function($, modernizr, lang, globals, markup, changeset) {

	return {	
		init: init,
		add: add,
		deleteQuestion: deleteQuestion,
		addOption: addOption,
		deleteOption: deleteOption,
		moveBefore: moveBefore
	};
	
	var gEditor;

	function init(editor) {
		gEditor = editor;
	}

	/*
	 * Add a new question
	 * qItem: the html element id for the closest question to where we want to add the new question
	 * locn: "before" or "after".  Whether to add the new question "before" or "after" the reference question
	 * formIndex: The index of the form in the array of forms that are part of the survey model
	 * qIndex: The index of the question in the array of questions that make up a form 
	 */
	function add(qId, locn) {		
		
		var $relatedQuestion = $("#" + qId),
			seq = 0,
			formIndex,
			qIndex,
			survey = globals.model.survey,
			type,
			i,
			firstQuestion;
		
		// Choose type
		type = "string";		// Default to text question
		
		if($relatedQuestion.size() > 0) {
			// Appending or pre=pending to an existing question
			firstQuestion = false;
			formIndex = $relatedQuestion.data("fid");
			qIndex = $relatedQuestion.data("id");
			seq = getSequenceQuestion(qIndex, survey.forms[formIndex]);
			if(locn === "after") {
				++seq;
			} 
		} else {
			// First question in the form
			firstQuestion = true;
			formIndex = 0;
			seq = 1;
		}

		// Create changeset to be applied on save		
		change = {
				changeType: "question",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "add",
				question: {
					seq: seq,
					type: type,
					source: "user",	// For date type
					labels: [],
					visible: true,
					
					// Helper values 
					firstQuestion: firstQuestion,
					formIndex: formIndex,
					locn: locn,							// Whether the new question was added before or after the related question
					$relatedElement: $relatedQuestion	// Jquery element that is next to the new question
				}
		};
		
		// Add default empty languages
		for(i = 0; i < survey.languages.length; i++) {
			change.question.labels.push({text:""});
		}
		$context = changeset.add(change);
		return $context;				// Add events on to the altered html
		
	}
	
	/*
	 * Move a question
	 * The beforeId is the id of the dom element that precedes this element
	 */
	function moveBefore(sourceId, beforeId) {		
		
		var $beforeElement = $("#" + beforeId),					// The element that the new item with be "before"
			beforeFormIndex = $beforeElement.data("fid"),
			beforeItemIndex = $beforeElement.data("id"),
			
			seq,												// The new values
			formIndex,

			$sourceElement = $('#' + sourceId),					// The old values
			sourceFormIndex = $sourceElement.data("fid"),
			sourceItemIndex = $sourceElement.data("id"),
			sourceSeq,
			
			survey = globals.model.survey,
			name,
			change;
		
		// Get the new sequence of the question
		seq = getSequenceQuestion(beforeItemIndex, survey.forms[beforeFormIndex]);				// Take the sequence of the item it is moving in front of
		formIndex = beforeFormIndex;		// Moved to the same form as the element before it

		
		// Get the old sequence of the question
		sourceSeq = getSequenceQuestion(sourceItemIndex, survey.forms[sourceFormIndex]);
		name = survey.forms[sourceFormIndex].questions[sourceItemIndex].name;
		
		// Create changeset to be applied on save		
		change = {
				changeType: "question",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "move",
				question: {
					seq: seq,
					sourceSeq: sourceSeq,
					name: name,
					
					// Helper values 
					sourceFormIndex: sourceFormIndex,
					sourceItemIndex: sourceItemIndex,
					formIndex: beforeFormIndex
				}
		};
		
		$context = changeset.add(change);
		return $context;				// Add events on to the altered html
		
	}
	
	/*
	 * Delete a question
	 */
	function deleteQuestion(item) {
		var $deletedElement = $('#' + item),
			$deletedQuestion = $deletedElement.find('.question'),
			formIndex = $deletedQuestion.data("fid"),
			itemIndex = $deletedQuestion.data("id"),
			change,
			survey = globals.model.survey,
			seq;
		
		seq = getSequenceQuestion(itemIndex, survey.forms[formIndex]);
		
		change = {
				changeType: "question",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "delete",
				question: {
					
					// Helper values 
					seq: seq,
					formIndex: formIndex,
					itemIndex: itemIndex,
					$deletedElement: $deletedElement	// JQuery element that is next to the new question
				}
		};
		changeset.add(change);
	}
	
	/*
	 * Delete an option
	 */
	function deleteOption(item) {
		var $deletedElement = $('#' + item),
			$deletedOption = $deletedElement.find('.option'),
			formIndex = $deletedOption.data("fid"),
			itemIndex = $deletedOption.data("id"),
			optionList = $deletedOption.data("list_name"),
			change,
			survey = globals.model.survey,
			seq;
		
		seq = getSequenceOption(itemIndex, survey.optionLists[optionList]);
		
		change = {
				changeType: "option",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "delete",
				option: {
					
					// Helper values 
					seq: seq,
					optionList: optionList,
					formIndex: formIndex,
					itemIndex: itemIndex,
					$deletedElement: $deletedElement	// Jquery element that is next to the new question
				}
		};
		changeset.add(change);
	}
	
	/*
	 * Add a new option
	 * oItem: the html element id for the closest option to where we want to add the new option
	 */
	function addOption(oItem, locn) {		
		
		var $relatedElement = $("#option" + oItem).parent(),
			$relatedOption,
			formIndex,
			optionIndex,
			qName,
			optionList,
			oIndexOther,		
			seq = 0,
			survey = globals.model.survey;
		
		if($relatedElement.size() > 0) {
			$relatedOption = $relatedElement.find('.option');
			qName = $relatedOption.data("qname");
			formIndex = $relatedOption.data("fid");
			optionList = $relatedOption.data("list_name");
			oIndexOther = $relatedOption.data("id");
		} else {
			// TODO First option in the question
		}
		
		// Get the sequence of the option
		seq = getSequenceOption(oIndexOther, survey.optionLists[optionList]);
		if(locn === "after") {
			++seq;
		} 
		
		// Create changeset to be applied on save
		change = {
				changeType: "option",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "add",				// add | update | delete
				source: "editor",
				option: {
					seq: seq,
					optionList: optionList,
					sId: survey.id,
					labels: [],
					
					// Helper values 
					qName: qName,
					formIndex: formIndex,
					locn: locn,							// Whether the new question was added before or after the related question
					$relatedElement: $relatedElement	// Jquery element that is next to the new question
				}
		};
		
		// Add default empty languages
		for(i = 0; i < survey.languages.length; i++) {
			change.option.labels.push({text:""});
		}
		$context = changeset.add(change);
		return $context;				// Add events on to the altered html
		
	}
	
	/*
	 * Get the display sequence of the question
	 */
	function getSequenceQuestion(qIndex, form) {
		var i;
		
		for(i = 0; i < form.qSeq.length; i++) {
			if(form.qSeq[i] === qIndex) {
				return i;
			}
		}
		alert("Could not insert question");
		return 0;
	}
	
	/*
	 * Get the display sequence of the option
	 */
	function getSequenceOption(indexOther, optionList) {
		var i;
		
		for(i = 0; i < optionList.oSeq.length; i++) {
			if(optionList.oSeq[i] === indexOther) {
				return i;
			}
		}
		alert("Could not insert question");
		return 0;
	}
	

});
