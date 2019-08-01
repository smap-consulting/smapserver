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
         'app/changeset',
         'app/option'], 
		function($, modernizr, lang, globals, markup, changeset, option) {

	return {	
		init: init,
		add: add,
		deleteQuestion: deleteQuestion,
		addOption: addOption,
		deleteOption: deleteOption,
		moveQuestion: moveQuestion,
		moveBeforeOption: moveBeforeOption,
		setGroupEnd: setGroupEnd,
		getSequenceQuestion: getSequenceQuestion
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
	function add(formIndex, qId, locn, type, name) {		
		
		var $relatedQuestion = $("#" + qId),
			seq = 0,
			relatedFormIndex,
			qIndex,
			survey = globals.model.survey,
			type,
			name,
			i,
			firstQuestion;
		
		// Choose type
		
		type = type || "string";		// Default to text question
		
		firstQuestion = true;
		qIndex = 0;
		seq = 0;
		if($relatedQuestion.size() > 0) {
			relatedFormIndex = $relatedQuestion.data("fid");
			if(relatedFormIndex == formIndex) {
				// Appending or pre=pending to an existing question
				firstQuestion = false;
	
				qIndex = $relatedQuestion.data("id");
				seq = getSequenceQuestion(qIndex, survey.forms[formIndex], false, undefined);
				if(locn === "after") {
					++seq;
				} 
			}
		} 
		
		if(typeof name === "undefined") {
			name = getDefaultQuestionName(formIndex, qIndex);
		}

		// Create changeset to be applied on save		
		var change = {
				changeType: "question",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "add",
				question: {
					seq: seq,
					type: type,
					name: name,
					list_name: name,
					source: "user",	// For date type
					labels: [],
					visible: type === "end group" ? false : true,
					
					// Helper values 
					firstQuestion: firstQuestion,
					formIndex: formIndex,
					locn: locn,							// Whether the new question was added before or after the related question
					$relatedElement: $relatedQuestion	// Jquery element that is next to the new question
				}
		};
		
		// Add list of choices if this is a select question
		if(type.indexOf("select") == 0 || type === "rank") {
			var optionList = survey.optionLists[name];
			if(!optionList) {
				survey.optionLists[name] = {
					oSeq: [],
					options: []
				};
				option.refreshOptionListControls();
			}
		}
		
		// Add default labels
		if(type !== "end group") {
			for(i = 0; i < survey.languages.length; i++) {
				change.question.labels.push({text: name + ") "});
			}
		}
		
		var $context = changeset.add(change);
		return $context;				// Add events on to the altered html
		
	}
	
	function getDefaultQuestionName(formIndex, qIndex) {
		
		var forms = globals.model.survey.forms,
			form = forms[formIndex],
			name = form.maxQuestion;
		
		if(form.parentQuestionIndex >= 0) {
			name = forms[form.parentFormIndex].questions[form.parentQuestionIndex].name + "." + name;
		} else {	
			name = "q" + name;
		}
		
		// Keep incrementing the maxQuestion until we get a unique name for this question
		while(!nameIsUniqueInForm(form, name)) {
			name = ++form.maxQuestion;
			console.log("Get default qname maxQuestion: " + form.maxQuestion);
			if(form.parentQuestionIndex >= 0) {
				name = forms[form.parentFormIndex].questions[form.parentQuestionIndex].name + "." + name;
			} else {	
				name = "q" + name;
			}
		}
		
		return name;
	}
	
	function nameIsUniqueInForm(form, name) {
		var unique = false,
			i;
		if(typeof name !== "undefined") {
			unique = true;
			for(i = 0; i < form.questions.length; i++) {
				if(!form.questions[i].deleted && form.questions[i].name === name) {
					unique = false;
					break;
				}
			}
		}
		return unique;
	}
	
	function nameIsUniqueInOptionList(list, name) {
		var unique = false,
			i;
		if(typeof name !== "undefined") {
			unique = true;
			for(i = 0; i < list.options.length; i++) {
				if(!list.options[i].deleted && list.options[i].name === name) {
					unique = false;
					break;
				}
			}
		}
		return unique;
	}
	
	function setGroupEnd(formIndex, qId, locn, type, name, availableGroups) {		
		
		var $beforeElement = $("#" + qId),
			seq = 0,
			formIndex,
			qIndex,
			survey = globals.model.survey,
			group,
			beforeFormIndex = $beforeElement.data("fid"),
			beforeItemIndex = $beforeElement.data("id"),
			seq,
			sourceSeq,
			sourceFormId,
			groupEndName,
			endGroupData,
			question;
		
		if(availableGroups.length > 1) {
			alert("Select group: " + availableGroups.join("-"));
			group = availableGroups[0];
		} else {
			group = availableGroups[0];
			alert("Moving group: " + group);
		}
		groupEndName = group + "_groupEnd";
		
		
		endGroupData = getEndGroup(groupEndName, survey.forms[beforeFormIndex]);
		sourceFormId = survey.forms[beforeFormIndex].id;	
		
		// Get the sequence of the question just after the new location of the end group
		seq = getSequenceQuestion(beforeItemIndex, survey.forms[beforeFormIndex], true, endGroupData.index);
		if(locn === "after") {
			++seq;
		} 
		
		// Get information about the current question
		question = survey.forms[beforeFormIndex].questions[endGroupData.index];
		
		// Create changeset to be applied on save		
		var change = {
				changeType: "question",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "move",
				question: {
						seq: seq,
						sourceSeq: endGroupData.seq,
						sourceFormId: sourceFormId,
						name: groupEndName,
						type: question.type,
						path: question.path,
						
						// Helper values 
						sourceFormIndex: beforeFormIndex,
						sourceItemIndex: endGroupData.index,
						formIndex: beforeFormIndex
					}
			};

		var $context = changeset.add(change);
		return $context;	
		
	}
	
	/*
	 * Move a question
	 * The beforeId is the id of the dom element that precedes this element
	 */
	function moveQuestion(formIndex, sourceId, beforeId, locn) {		
		
		var $beforeElement,									// The element that the new item with be "before"
			beforeFormIndex,
			beforeItemIndex,
			
			seq,												// The new values
			formIndex,

			$sourceElement = $('#' + sourceId),					// The old values
			sourceFormIndex = $sourceElement.data("fid"),
			sourceItemIndex = $sourceElement.data("id"),
			sourceSeq,
			sourceFormId,
			
			survey = globals.model.survey,
			question,
			change;
		
		$beforeElement = $("#" + beforeId);
		seq = 0;
		beforeItemIndex = 0;
		if($beforeElement.size() > 0) {
			beforeFormIndex = $beforeElement.data("fid");
			if(beforeFormIndex == formIndex) {
				beforeItemIndex = $beforeElement.data("id");
				seq = getSequenceQuestion(beforeItemIndex, survey.forms[beforeFormIndex], false/*beforeFormIndex === sourceFormIndex*/, sourceItemIndex);	
				if(locn === "after") {
					seq++;
				}
			}
		}

		// Get the old sequence of the question
		sourceSeq = getSequenceQuestion(sourceItemIndex, survey.forms[sourceFormIndex], false, undefined);
		question = survey.forms[sourceFormIndex].questions[sourceItemIndex];
		sourceFormId = survey.forms[sourceFormIndex].id;										// Used to verify that a question has not been moved by another user

		// Create changeset to be applied on save	
		
		change = {
			changeType: "question",		// survey | form | language | question | option | (property | label) last two are types of property change
			action: "move",
			question: {
					seq: seq,
					sourceSeq: sourceSeq,
					sourceFormId: sourceFormId,
					name: question.name,
					type: question.type,
					path: question.path,
					
					// Helper values 
					sourceFormIndex: sourceFormIndex,
					sourceItemIndex: sourceItemIndex,
					formIndex: formIndex
				}
		};
		

		
		
		var $context = changeset.add(change);
		return $context;				// Add events on to the altered html
		
	}
	
	/*
	 * Move an option
	 */
	function moveBeforeOption(sourceListName, sourceItemIndex, 
				targetListName, targetItemIndex, locn) {		
		
		var 	
			seq,	
			sourceSeq,
			survey = globals.model.survey,
			change;

		// Get the new sequence of the option
		option.addOptionSequence(survey.optionLists[targetListName]);
		seq = getSequenceOption(targetItemIndex, survey.optionLists[targetListName]);
		if(locn === "after") {
			seq++;
		}
		
		// Get the old sequence of the option
		sourceSeq = getSequenceOption(sourceItemIndex, survey.optionLists[sourceListName]);
		
		// Create changeset to be applied on save		
		change = {
			changeType: "option",		// survey | form | language | question | option | (property | label) last two are types of property change
			action: "move",
			option: {
					seq: seq,
					sourceSeq: sourceSeq,
					optionList: targetListName,
					sourceOptionList: sourceListName,
					
					// Helper values 
					sourceItemIndex: sourceItemIndex,
					targetItemIndex: targetItemIndex
				}
				
		};
		
		var $context = changeset.add(change);
		return $context;				// Add events on to the altered html
		
	}
	
	/*
	 * Delete a question
	 */
	function deleteQuestion(item) {
		var $deletedElement = $('#' + item),
			formIndex = $deletedElement.data("fid"),
			itemIndex = $deletedElement.data("id"),
			change,
			survey = globals.model.survey,
			seq;
		
		seq = getSequenceQuestion(itemIndex, survey.forms[formIndex], false, undefined);
		
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
	function deleteOption(index, list_name) {
		var $deletedElement, 
			change,
			survey = globals.model.survey,
			seq;
		
		option.addOptionSequence(survey.optionLists[list_name]);
		seq = getSequenceOption(index, survey.optionLists[list_name]);
		$deletedElement = $('#choiceView').find('tr').filter(function() {
			var $this = $(this);
			return $this.data("id") == index;
		});
		
		change = {
				changeType: "option",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "delete",
				option: {
					
					// Helper values 
					seq: seq,
					optionList: list_name,
					itemIndex: index,
					$deletedElement: $deletedElement	// Jquery element that is next to the new question
				}
		};
		changeset.add(change);
	}
	
	/*
	 * Add a new option
	 */
	function addOption(oId, locn, list_name, formIndex, qname) {		
		
		var seq = 0,
			survey = globals.model.survey,
			value,
			i;

		option.addOptionSequence(survey.optionLists[list_name]);
		seq = getSequenceOption(oId, survey.optionLists[list_name]); 
		
		value = getDefaultOptionValue(list_name, seq);

		// Create changeset to be applied on save
		var change = {
				changeType: "option",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "add",				// add | update | delete
				source: "editor",
				option: {
					seq: seq,
					optionList: list_name,
					sId: survey.id,
					labels: [],
					value: value,
					cascade_filters: {},
					
					// Helper values 
					formIndex: formIndex,
					optionIndex: oId,
					qname: qname,
					locn: locn,							// Whether the new option was added before or after the related option
				}
		};
		
		// Add default empty languages
		for(i = 0; i < survey.languages.length; i++) {
			change.option.labels.push({text:""});
		}
		var $context = changeset.add(change);
		return $context;				// Add events on to the altered HTML
		
	}
	
	/*
	 * Get default option value
	 */
	function getDefaultOptionValue(list_name, optionIndex) {
		
		var optionLists = globals.model.survey.optionLists,
			list = optionLists[list_name],
			name = list.maxOption;
		
		// Keep incrementing the maxOptionValue until we get a unque name for this optionList
		while(!nameIsUniqueInOptionList(list, name)) {
			name = ++list.maxOption;
		}
		
		return String(name);

	}
	/*
	 * Get the display sequence of the question
	 * If exclude is set to true then don't count the question with index "excludeIndex"
	 *  This should be used when you are getting the new seq of a question that is being moved
	 *  The old location of that question will be removed and should not be counted
	 */
	function getSequenceQuestion(qIndex, form, exclude, excludeIndex) {
		var i,
			seq = 0;
		
		for(i = 0; i < form.qSeq.length; i++) {
			if(form.qSeq[i] === qIndex) {
				return seq;
			}
			if(!exclude || (form.qSeq[i] !== excludeIndex)) {
				seq++;
			}
		}
		return 0;
	}
	
	/*
	 * Get the display sequence of an end group
	 */
	function getEndGroup(endName, form) {
		var i,
			endGroupData = {};
		
		for(i = 0; i < form.qSeq.length; i++) {
			if(form.questions[form.qSeq[i]].name === endName) {
				endGroupData.seq = i;
				endGroupData.index = form.qSeq[i];
				return endGroupData;
			}
		}
		alert("Could not locate the current end group with name: " + endName);
		return 0;
	}
	
	/*
	 * Get the display sequence of the option
	 */
	function getSequenceOption(indexOther, optionList) {
		var i;
		
		for(i = 0; i < optionList.oSeq.length; i++) {
			if(optionList.oSeq[i] == indexOther) {
				return i;
			}
		}
		return optionList.oSeq.length;		// Add to end
	}
	

});
