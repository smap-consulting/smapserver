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
		addOption: addOption,
		setType: setType
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
	function add(qItem, locn) {		
		
		var $relatedElement = $("#question" + qItem),
			$relatedQuestion,
			formIndex,
			qIndexOther,
			seq = 0,
			survey = globals.model.survey,
			type,
			i;
		
		if($relatedElement.size() > 0) {
			$relatedQuestion = $relatedElement.find('.question');
			formIndex = $relatedQuestion.data("fid");
			qIndexOther = $relatedQuestion.data("id");
		} else {
			// TODO First question in the form
			formIndex = 0;
		}
		
		// Choose type
		type = "dateTime";		// TODO
		
		// Get the sequence of the question
		seq = getSequenceQuestion(qIndexOther, survey.forms[formIndex]);
		if(locn === "after") {
			++seq;
		} 

		
		// Create changeset to be applied on save		
		change = {
				changeType: "question",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "add",
				question: {
					seq: seq,
					type: type,
					labels: [],
					
					// Helper values 
					formIndex: formIndex,
					locn: locn,							// Whether the new question was added before or after the related question
					$relatedElement: $relatedElement	// Jquery element that is next to the new question
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
	
	function setType() {
		$('#typeModal').modal('show');
	}
	
	/*
	 * Get the display sequence of the question
	 */
	function getSequenceQuestion(indexOther, form) {
		var i;
		
		for(i = 0; i < form.qSeq.length; i++) {
			if(form.qSeq[i] === indexOther) {
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
