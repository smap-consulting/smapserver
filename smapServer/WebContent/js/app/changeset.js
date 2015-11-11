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
 * Module to manage the changeset of changes for editor
 */

"use strict";

define([
         'jquery',
         'modernizr',
         'app/localise',
         'app/globals',
         'app/editorMarkup'], 
		function($, modernizr, lang, globals, markup) {

	var changes = [],
		errors = [];
	
	
	return {	
		add: add,
		undo: undo,
		save: save,
		changes: changes,
		setHasChanges: setHasChanges,
		addValidationError: addValidationError,
		removeValidationError: removeValidationError,
		addOptionValidationError: addOptionValidationError,
		removeOptionValidationError: removeOptionValidationError
	};

	/*
	 * Add a new change item
	 */
	function add(change) {
		var refresh,
			$context;
		
		// Add to changeset array
		addToChangesetArray(change);
		
		// Apply to model
		refresh = updateModel(change);
		
		// Validate the change
		if(change.changeType === "question") {
			validateQuestion(change);
		} else if(change.changeType === "option") {
			validateOption(change);
		}
		
		/*
		 * Apply any HTML changes either directly to the changed element, or by refreshing the whole form using
		 * the updated model
		 */
		if(refresh) {
			$context = markup.refresh();
		} else {
			$context = updateHtmlElement(change);
		}
		
		return $context;
		
	}
	
	function undo() {
	}
	
	// Save the survey
	function save(callback) {
		
		var url="/surveyKPI/surveys/save/" + globals.gCurrentSurvey,
			changesString = JSON.stringify(changes);		
		
		console.log("Saving survey");
		console.log(changes);
		
		setHasChanges(0);
		
		addHourglass();
		$.ajax({
			url: url,
			type: 'PUT',
			dataType: 'json',
			cache: false,
			data: { changes: changesString },
			success: function(data) {
				var responseFn = callback,
					h = [],
					idx = -1,
					i;
				
				removeHourglass();			
				
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
						
				if(typeof responseFn === "function") { 
					responseFn();
				}
			}
		});	
		
	};
	
	/*
	 * Add a single change item to the array of changes (changeset)
	 */
	function addToChangesetArray(change) {
		
		var type = change.property ? change.property.type : undefined;
		
		var ci = {
				changeType: change.changeType,
				type: type,
				action: change.action,
				items: []
			},
			survey = globals.model.survey,
			item,
			item_orig,
			form,
			applyChange = true;

		/*
		 * Add additional parameters to change object
		 */	
		if(change.changeType === "label" || change.changeType === "property") {
			if(change.property.type === "question") {
				item = survey.forms[change.property.formIndex].questions[change.property.itemIndex];
				item_orig = survey.forms_orig[change.property.formIndex].questions[change.property.itemIndex];
				change.property.name = item.name;
				change.property.qId = item.id;
				if(change.changeType === "property") {
					setTypeSpecificChanges(change.property.prop, change, survey);
				}
			} else {
				item = survey.optionLists[change.property.optionList].options[change.property.itemIndex];
				item_orig = survey.optionLists_orig[change.property.optionList].options[change.property.itemIndex];	
				change.property.name = change.property.optionList;
				if(change.changeType === "property") {
					setOptionTypeSpecificChanges(change.property.prop, change, survey);
				}
			}
			
			if(change.changeType === "label") {
				if(item_orig) {
					change.property.oldVal = item_orig.labels[change.property.language][change.property.propType]; 
				}
				
				// Add a reference for the label
				form = survey.forms[change.property.formIndex];
				if(change.property.prop === "label" || change.property.prop === "media" && item.text_id) {
					change.property.key = item.text_id;
				} else if(change.property.prop === "hint" && item.hint_id) {
					change.property.key = item.hint_id;
				} else {
					// Create reference for this new Label		
					if(change.property.type === "question") {
						change.property.key = getFormPath(form) + "/" + item.name + ":label";	
					} else {
						change.property.key = getFormPath(form) + "/" + change.property.qname + "/" + item.value + ":label";
					}
				}
			} else {
				if(item_orig) {
					change.property.oldVal = item_orig[change.property.prop];
				}
			}
				
			change.property.languageName = survey.languages[change.property.language];			// For logging the event
		
		} else if(change.changeType === "question") {
			form = survey.forms[change.question.formIndex];
			change.question.fId = form.id;
			if(change.action === "delete") {
				item = survey.forms[change.question.formIndex].questions[change.question.itemIndex];
				change.question.id = item.id;
				change.question.name = item.name;
			}
		} else if(change.changeType === "option") {
			if(change.action === "delete") {
				item = survey.optionLists[change.option.optionList].options[change.option.itemIndex];
				change.option.value = item.value;
			}
		}

		
		/*
		 * Add the change item to the array
		 */
		ci.items.push(change);
		if(change.action !== "add") {
			applyChange = removeDuplicateChange(changes, ci);
		}
		if(applyChange) {
			if(change.property && (change.property.newVal !== change.property.oldVal)) {		// Add if the value has changed
				changes.push(ci);
			} else if(change.action === "add" || change.action === "delete" || change.action === "move") {
				changes.push(ci);
			}
		}
		setHasChanges(changes.length);
		
	}
	
	/*
	 * Annotate a change item with changes that are dependent on the type of the property
	 */
	function setTypeSpecificChanges(type, change, survey) {
		var i;
		if(type === "type") {
			var typeList = globals.model.qTypes;
			
			for(i = 0; i < typeList.length; i++) {
				if(change.property.newVal === typeList[i].type) {
					change.property.setVisible = true;
					change.property.visibleValue = typeList[i].visible;
					change.property.sourceValue = typeList[i].source;
					break;
				}
			}
		} else if(type === "name") {
			var form = survey.forms[change.property.formIndex];
			change.property.path = getFormPath(form) + "/" + change.property.newVal;				
		}
	}
	
	/*
	 * Annotate an option change item with changes that are dependent on the type of the property
	 */
	function setOptionTypeSpecificChanges(type, change, survey) {
		var i;
		if(type === "value") {
			var form = survey.forms[change.property.formIndex];
			change.property.path = getFormPath(form) + "/" + change.property.qname + "/" + change.property.newVal;			
		}
	}
	
	/*
	 * If this is the second time an element has been modified then remove the original modification
	 * If this is a property change on a new element then update the element here
	 */
	function removeDuplicateChange(changes, change) {
		
		var j, 
			item,
			element,
			newItem,
			newElement;
		
		newItem = change.items[0];
		if(newItem.question) {
			newElement = newItem.question;
		} else if(newItem.option) {
			newElement = newItem.option;
		} else if(newItem.property) {
			newElement = newItem.property;
		}
			
		for(j = 0; j < changes.length; j++) {
			
			item = changes[j].items[0];
			if(item.question) {
				element = item.question;
			} else if(item.option) {
				element = item.option;
			} else if(item.property) {
				element = item.property;
			}
			
			
			if(newItem.action === "update" && item.property) {

				if(item.language === newItem.language 
						&& item.type === newItem.type) {		// Question or option
					if(
							(newElement.type === "question" && 
									newElement.itemIndex === element.itemIndex &&
									newElement.formIndex === element.formIndex) ||
							(newElement.type === "option" && 
									newElement.itemIndex === element.itemIndex &&
									newElement.optionList === element.optionList) ) {
						
						// This property change already exists - remove the old one
						changes.splice(j,1);	// Remove this item and apply the new one
						return true;							
						
					}
				}
			} else if(newItem.action === "update" && item.question) {
				if(newElement.type === "question" && 
						newElement.itemIndex === element.itemIndex &&
						newElement.formIndex === element.formIndex) {
					
					item.question[newElement.prop] = newElement.newVal;
					if(newElement.prop === "name") {
						item.question["path"] = newElement.path;
					}
					return false;
					
				}
			} else if(newItem.action === "update" && item.option) {
				if(newElement.type === "option" && 
						newElement.itemIndex === element.itemIndex &&
						newElement.optionList === element.optionList) {
					
					item.option[newElement.prop] = newElement.newVal;
					if(newElement.prop === "value") {
						item.option["path"] = newElement.path;
					}
					return false;
					
				}
			} else if(newItem.action === "delete") {
				/*
				 * Remove any modifications to this deleted element
				 */
				if((newElement.type === "question" || newElement.type === "option") &&
						newElement.itemIndex === element.itemIndex &&
						newElement.formIndex === element.formIndex) {
					changes.splice(j,1);	// Remove this item
					// Continue to remove all matching changes
				}
			}
		}
		return true;
	}
	
	/*
	 * Update settings when the number of changes to apply changes 
	 */ 
	function setHasChanges(numberChanges) {
		if(numberChanges === 0) {
			changes = [];
			$('.m_save_survey').addClass("disabled").attr("disabled", true).find('.badge').html(numberChanges);
			
		} else {
			$('.m_save_survey').find('.badge').html(numberChanges);
			if(errors.length === 0) {
				$('.m_save_survey').removeClass("disabled").attr("disabled", false);
			}
		}
		console.log("setHasChanges: " + JSON.stringify(errors));
	}
	
	/*
	 * Update the in memory model
	 */
	function updateModel(change) {
		var refresh = false,		// Set to true if the page needs to be refreshed with this change
			survey = globals.model.survey,
			property,
			length;
		
		if(change.property) {
			/*
			 * Update the property values
			 */
			property = change.property;
			
			if(property.type === "question") {
				if(property.propType === "text") {
					if(property.prop === "label") {
						survey.forms[property.formIndex].questions[property.itemIndex].labels[property.language][property.propType] = property.newVal;
					} else {
						survey.forms[property.formIndex].questions[property.itemIndex][property.prop] = property.newVal;		//Other properties
						
						// Set type dependent properties
						if(property.setVisible) {
							survey.forms[property.formIndex].questions[property.itemIndex]["visible"] = property.visibleValue;
							survey.forms[property.formIndex].questions[property.itemIndex]["source"] = property.sourceValue;
						}
						
					}
				} else {
					// For non text changes update all languages
					for(i = 0; i < survey.forms[property.formIndex].questions[property.itemIndex].labels.length; i++) {
						survey.forms[property.formIndex].questions[property.itemIndex].labels[i][property.propType] = property.newVal;
						survey.forms[property.formIndex].questions[property.itemIndex].labels[i][property.propType + "Url"] = 
							_getUrl(survey.o_id, survey.ident, property.newVal, false, property.propType, property.isSurveyLevel);
					}
				}
			} else {
				if(property.propType === "text") {
					survey.optionLists[property.optionList].options[property.itemIndex].labels[property.language][property.propType] = property.newVal;
				} else {
					// For non text changes update all languages
					for(i = 0; i < survey.optionLists[property.optionList].options[property.itemIndex].labels.length; i++) {
						survey.optionLists[property.optionList].options[property.itemIndex].labels[i][property.propType] = property.newVal;
						survey.optionLists[property.optionList].options[property.itemIndex].labels[i][property.propType + "Url"] = 
							_getUrl(survey.o_id, survey.ident, property.newVal, false, property.propType);
					}
				}
			}
			
			refresh = false;	// Update markup solely for this Property
			
		} else if(change.changeType === "question") {
			if(change.action === "move") {
				
				var question = survey.forms[change.question.sourceFormIndex].questions[change.question.sourceItemIndex];	// existing question
				var oldLocation = change.question.sourceSeq;
				var newLocation = change.question.seq;
				// Add the question in the new location
				length = survey.forms[change.question.formIndex].questions.push(question);			// Add the new question to the end of the array of questions
				change.question.itemIndex = length - 1;
				survey.forms[change.question.formIndex].qSeq.splice(newLocation, 0, length - 1);	// Update the question sequence array
			
				// Remove the question from the old location	
				// The old location may have changed if the new location was inserted before it
				if(newLocation < oldLocation) {
					oldLocation++;
				}
				survey.forms[change.question.sourceFormIndex].qSeq.splice(oldLocation, 1);
				
				refresh = true;
				
			} else if(change.action === "add") {			
				length = survey.forms[change.question.formIndex].questions.push(change.question);			// Add the new question to the end of the array of questions
				change.question.itemIndex = length -1;
				survey.forms[change.question.formIndex].qSeq.splice(change.question.seq, 0, length - 1);	// Update the question sequence array
			
				if(change.question.firstQuestion) {
					refresh = true;		// Refresh all the questions, actually there is only one so why not
				} else {
					refresh = false;	// Update markup solely for this question
				}
				
			} else if(change.action === "delete") {
				survey.forms[change.question.formIndex].qSeq.splice(change.question.seq, 1);	// Remove item from the sequence array
				refresh = false;
			} else {
				console.log("Unknown action: " + change.action);
			}
			
			
		} else if(change.changeType === "option") {
			if(change.action === "add") {			
				length = survey.optionLists[change.option.optionList].options.push(change.option);			// Add the new option to the end of the array of options
				change.option.itemIndex = length -1;
				survey.optionLists[change.option.optionList].oSeq.splice(change.option.seq, 0, length - 1);	// Update the option sequence array
			} else if(change.action === "delete") {
				survey.optionLists[change.option.optionList].oSeq.splice(change.option.seq, 1);	// Remove item from the sequence array		
			} else {
				console.log("Unknown action: " + change.action);
			}
			refresh = false;	// Update markup solely for this option
		}
		
		return refresh;	
	}
	
	/*
	 * Update the modified HTML element
	 */
	function updateHtmlElement(change) {
		var newMarkup,
			survey = globals.model.survey,
			$changedRow;
		
		if(change.changeType === "label") {
			if(change.property.propType === "image") {	
				
				newMarkup = markup.addMedia("Image", 
						change.property.newVal, 
						_getUrl(survey.o_id, survey.ident, change.property.newVal, false, 'image', change.property.isSurveyLevel), 
						_getUrl(survey.o_id, survey.ident, change.property.newVal, true, 'image', change.property.isSurveyLevel)
						);
				
			} else if(change.property.propType === "video") {
				
				newMarkup = markup.addMedia("Video", 
						change.property.newVal, 
						_getUrl(survey.o_id, survey.ident, change.property.newVal, false, 'video', change.property.isSurveyLevel), 
						_getUrl(survey.o_id, survey.ident, change.property.newVal, true, 'video', change.property.isSurveyLevel)
						);
				
			} else if(change.property.propType === "audio") {
				
				newMarkup = markup.addMedia("Audio", 
						change.property.newVal, 
						_getUrl(survey.o_id, survey.ident, change.property.newVal, false, 'audio', change.property.isSurveyLevel), 
						undefined
						);	
	
			}
			
			if(newMarkup) {
				if(change.property.type === "question") {
					$changedRow = $('#formList').find('td.question').filter(function(index){
						var $this = $(this);
						return $this.data("fid") == change.property.formIndex && $this.data("id") == change.property.itemIndex;
					});
				} else {
					// changed row for choices
					$changedRow = $('#formList').find('td.option').filter(function(index){
						var $this = $(this);
						return $this.data("fid") == change.property.formIndex && $this.data("id") == change.property.itemIndex;
					});
				}
				if($changedRow) {
					$changedRow.find('.' + change.property.propType + 'Element').replaceWith(newMarkup);
					$('.mediaProp', $changedRow).off().click(function(){
						var $this = $(this);
						mediaPropSelected($this);
					});
				}
			}
		} else if(change.changeType === "question") {
			if(change.action === "add") {
				if(change.question.locn === "after") {
					change.question.$relatedElement.after(markup.addOneQuestion(change.question, change.question.formIndex, change.question.itemIndex, true));			
				} else {
					change.question.$relatedElement.prev().before(markup.addOneQuestion(change.question, change.question.formIndex, change.question.itemIndex, true));
				}
				$changedRow = $("#question" + globals.gQuestionIndex);
				delete change.question.$relatedElement;		// Delete the "related element", it is no longer needed and contains circular references which cannot be stringified
			} else if(change.action === "delete") {
				change.question.$deletedElement.remove();
				delete change.question.$deletedElement;
			}
		} else if(change.changeType === "option") {
			if(change.action === "add") {
				newMarkup = markup.addOneOption(change.option, change.option.formIndex, change.option.itemIndex, change.option.optionList, change.option.qName);
				if(change.option.locn === "after") {
					change.option.$relatedElement.after(newMarkup);
					$changedRow = change.option.$relatedElement.next();
				} else {
					change.option.$relatedElement.before(newMarkup);
					$changedRow = change.option.$relatedElement.prev();
				}
				delete change.option.$relatedElement;		// Delete this, it is no longer needed and contains circular references which cannot be stringified
			} else if(change.action === "delete") {
				change.option.$deletedElement.remove();
				delete change.option.$deletedElement;
			}
		} else if(change.changeType === "property") {
			// Apply any markup changes that result from a property change
			if(change.property.prop === "type") {
				
				// 1. Get the changed question row
				$changedRow = $('#formList').find('td.question').filter(function(index){
					var $this = $(this);
					return $this.data("fid") == change.property.formIndex && $this.data("id") == change.property.itemIndex;
				});
				
				
				// 1. Update the question
				//newMarkup = markup.addQType(change.property.newVal);
				newMarkup = markup.addOneQuestion(
						survey.forms[change.property.formIndex].questions[change.property.itemIndex], 
						change.property.formIndex, 
						change.property.itemIndex,
						false);
				$changedRow = $changedRow.closest('li');
				if($changedRow) {
					$changedRow.replaceWith(newMarkup);
					
					// Since we replaced the row we had better get the replaced row so that actions can be reapplied
					$changedRow = $('#formList').find('td.question').filter(function(index){
						var $this = $(this);
						return $this.data("fid") == change.property.formIndex && $this.data("id") == change.property.itemIndex;
					});
					$changedRow = $changedRow.closest('li');
				}
	
			}
		}
		

		return $changedRow;
	}
	
	/*
	 * Media functions
	 */
	function _getUrl(o_id, s_ident, newVal, thumbs, type, isSurveyLevel) {
		var url = "/media/",
			filebase,
			ext;
		
		if(newVal) {
			if(isSurveyLevel) {
				url += s_ident;
				url += "/";
				if(thumbs) {
					url += "thumbs/"; 
				}
			} else {
				url += "organisation/";
				url += o_id;
				url += "/";
				if(thumbs) {
					url += "thumbs/"; 
				}
			}
			
			url += newVal;

			// Videos and other derived thumbnails will have type jpg
			if(thumbs && type !== "image") {			
				index = url.lastIndexOf('.');
				filebase = url.substr(0, index);
				url = filebase + ".jpg";		
			}
		} else {
			url = undefined;
		}
		
		return url;
	}

	/*
	 * Create a message from response to a save request
	 */
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
	
	/*
	 * ***************************************************************
	 * Validate each change to a question
	 */
	function validateQuestion(change) {
		
		var i, j,
			form, question,
			survey = globals.model.survey,
			isValid = true,
			hasDuplicate = false,
			formIndex,
			itemIndex;
		
		
		if(change.action === "add" || change.action === "delete" || change.action === "move") {
			formIndex = change.question.formIndex;
			itemIndex = change.question.itemIndex;
		} else {
			formIndex = change.property.formIndex;
			itemIndex = change.property.itemIndex;
		}
		
		if(change.action === "add") {
			// New questions always have a blank name
			addValidationError(
					formIndex,
					itemIndex,
					"This question does not have a name. Specify a unique name.");
			isValid = false;
		
		} else if(change.action === "update") {
			
			/*
			 * Name change require the entire set of questions to be validated for:
			 *   references to the previous question name
			 */
			if(isValid && change.property.prop === "name") {
				
				console.log("Need to check for duplicates");
				for(i = 0; i < survey.forms.length; i++) {
					form = survey.forms[i];
					for(j = 0; j < form.questions.length; j++) {		
						if(!(i === formIndex && j === itemIndex)) {
							question = form.questions[j];
							
							// TODO Check for reference errors
						}
					}
				}
				
			}
		}
		
		
		// If the question is valid then the error message can be removed
		if(isValid) {
			removeValidationError(
					formIndex,
					itemIndex);
		}
		
		// Set the control buttons
		if(errors.length > 0 ) {
			$('.m_save_survey').addClass("disabled").attr("disabled", true);			
		} else if(changes.length > 0) {
			$('.m_save_survey').removeClass("disabled").attr("disabled", false);
		}
		console.log("validateQuestion: " + JSON.stringify(errors));
	}
	
	function addValidationError(formIndex, itemIndex, msg) {
		
		var $changedRow,
			survey = globals.model.survey;
		
		// Push error into validation array
		errors.push({
			isQuestion: true,
			formIndex: formIndex,
			itemIndex: itemIndex
		});
		
		// Update model
		survey.forms[formIndex].questions[itemIndex].error = true;
		survey.forms[formIndex].questions[itemIndex].errorMsg = msg;
		
		// Add error class to item row
		$changedRow = $('#formList').find('td.question').filter(function(index){
			var $this = $(this);
			return $this.data("fid") == formIndex && $this.data("id") == itemIndex;
		});
		$changedRow = $changedRow.closest('li');
		$changedRow.removeClass("panel-success").addClass("panel-danger");
		
		// Add message
		$changedRow.find('.error-msg').html(msg);
	}
	
	function removeValidationError(formIndex, itemIndex) {
		
		var i,
			$changedRow,
			survey = globals.model.survey;
		
		// Remove errors from validation array
		for(i = errors.length - 1; i >= 0; i--) {
			if(errors[i].isQuestion && errors[i].formIndex === formIndex && errors[i].itemIndex === itemIndex) {
				errors.splice(i, 1);
			}
		}
		
		// Update model
		survey.forms[formIndex].questions[itemIndex].error = false;
		survey.forms[formIndex].questions[itemIndex].errorMsg = undefined;
		
		// Remove error class from item row
		$changedRow = $('#formList').find('td.question').filter(function(index){
			var $this = $(this);
			return $this.data("fid") == formIndex && $this.data("id") == itemIndex;
		});
		$changedRow.closest('li').removeClass("panel-danger").addClass("panel-success");
	}
	
	/*
	 * Validate each change to an option
	 */
	function validateOption(change) {
		
		var i, j,
			form, question,
			survey = globals.model.survey,
			isValid = true,
			hasDuplicate = false,
			optionList,
			itemIndex;
		
		
		if(change.action === "add" || change.action === "delete") {
			optionList = change.option.optionList;
			itemIndex = change.option.itemIndex;
		} else {
			formIndex = change.property.formIndex;
			itemIndex = change.property.itemIndex;
		}
		
		if(change.action === "add") {
			// New options always have a blank value
			addOptionValidationError(
					optionList,
					itemIndex,
					"This choice does not have a name. Specify a unique name from within the list");
			isValid = false;
		
		} else if(change.action === "update") {
			
			/*
			 * Name change require the entire set of questions to be validated for:
			 *   references to the previous question name
			 */
			if(isValid && change.property.prop === "name") {
				
				console.log("Need to check for duplicates");
				for(i = 0; i < survey.forms.length; i++) {
					form = survey.forms[i];
					for(j = 0; j < form.questions.length; j++) {		
						if(!(i === formIndex && j === itemIndex)) {
							question = form.questions[j];
							
							// TODO Check for reference errors
						}
					}
				}
				
			}
		}
		
		
		// If the question is valid then the error message can be removed
		if(isValid) {
			removeOptionValidationError(
					optionList,
					itemIndex);
		}
		
		// Set the control buttons
		if(errors.length > 0 ) {
			$('.m_save_survey').addClass("disabled").attr("disabled", true);			
		} else if(changes.length > 0) {
			$('.m_save_survey').removeClass("disabled").attr("disabled", false);
		}
		console.log("validateOption: " + JSON.stringify(errors));
	}
	
	function addOptionValidationError(listName, itemIndex, msg) {
		
		var $changedRow,
			survey = globals.model.survey;
		
		// Push error into validation array
		errors.push({
			isQuestion: false,
			listName: listName,
			itemIndex: itemIndex
		});
		
		// Update model
		survey.optionLists[listName].options[itemIndex].error = true;
		survey.optionLists[listName].options[itemIndex].errorMsg = msg;
		
		// Add error class to item row
		$changedRow = $('#formList').find('td.option').filter(function(index){
			var $this = $(this);
			return $this.data("list_name") == listName && $this.data("id") == itemIndex;
		});
		$changedRow = $changedRow.closest('.editor_element');
		$changedRow.addClass("option_error");
		
		// Add message
		$changedRow.find('.error-msg').html(msg);
	}
	
	function removeOptionValidationError(listName, itemIndex) {
		
		var i,
			$changedRow,
			survey = globals.model.survey;
		
		// Remove error from validation array
		for(i = 0; i < errors.length; i++) {
			if(!errors[i].isQuestion && errors[i].listName === listName && errors[i].itemIndex === itemIndex) {
				errors.splice(i, 1);
			}
		}
		
		// Update model
		survey.optionLists[listName].options[itemIndex].error = false;
		survey.optionLists[listName].options[itemIndex].errorMsg = undefined;

		
		// Remove error class from item row
		$changedRow = $('#formList').find('td.option').filter(function(index){
			var $this = $(this);
			return $this.data("list_name") == listName && $this.data("id") == itemIndex;
		});
		$changedRow.closest('.editor_element').removeClass("option_error");
	}
	
	/*
	 * Get the path for the form from the top level form down
	 */
	function getFormPath(form) {
		var path = form.name,
			forms = globals.model.survey.forms,
			i;
		while(form.parentform !== 0) {
			for(i = 0; i < forms.length; i++) {
				if(forms[i].id === form.parentform) {
					form = forms[i];
					path = form.name + "/" + path;
					break;
				}
			}
		}
		path = "/" + path;
		return path;
	}
	 
});