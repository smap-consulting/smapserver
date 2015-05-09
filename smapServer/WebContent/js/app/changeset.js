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

	var changes = [];
	
	
	return {	
		add: add,
		undo: undo,
		save: save
	};

	/*
	 * Add a new change item
	 */
	function add(change) {
		var refresh,
			$context;
		
		console.log("Add change");
		console.log(change);
		
		// 1. Add to changeset array
		addToChangesetArray(change);
		
		// 2. Apply to model
		refresh = updateModel(change);
		
		/*
		 * Apply any HTML changes either directly to the changed element or by refreshing using
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
				setHasChanges(0);
				
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
	
	/*
	 * Add a single change item to the array of changes (changeset)
	 */
	function addToChangesetArray(change) {
		
		var ci = {
				changeType: change.changeType,
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
		if(change.changeType === "label" || change.changeType === "media" || change.changeType === "property") {
			if(change.property.type === "question") {
				item = survey.forms[change.property.formIndex].questions[change.property.itemIndex];
				item_orig = survey.forms_orig[change.property.formIndex].questions[change.property.itemIndex];
				change.property.name = item.name;
				change.property.qId = item.qId;
			} else {
				item = survey.optionLists[change.property.optionList][label.itemIndex];
				item_orig = survey.optionLists_orig[change.property.optionList][label.itemIndex];	
				change.property.name = change.property.optionList;
			}
			
			if(change.changeType === "label" || change.changeType === "media") {
				change.property.oldVal = item_orig.labels[change.property.language][change.property.propType]; 
				
				// Add a reference for the label
				form = survey.forms[change.property.formIndex];
				if(item.text_id) {
					change.property.key = item.text_id;
				} else {
					// Create reference for this new Label		
					if(change.property.type === "question") {
						change.property.key = "/" + form.name + "/" + item.name + ":label";	
					} else {
						change.property.key = "/" + form.name + "/" + change.property.qname + "/" + item.name + ":label";
					}
				}
			} else {
				change.property.oldVal = item_orig[change.property.prop];
			}
				
			change.property.languageName = survey.languages[change.property.language];			// For logging the event
		
		} else if(change.changeType === "question") {
			form = survey.forms[change.question.formIndex];
			change.question.fId = form.id;
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
			} else if(change.action === "add") {
				changes.push(ci);
			}
		}
		setHasChanges(changes.length);
		
	}
	
	/*
	 * If this is the second time an element has been modified then remove the original modification
	 * If this is a property change on a new element then update the element here
	 */
	function removeDuplicateChange(changes, change) {
		
		var j, 
			item,
			newItem;
		
		for(j = 0; j < changes.length; j++) {
			
			item = changes[j].items[0];
			newItem = change.items[0];
			
			if(newItem.action === "update" && item.property) {

				if(item.language === newItem.language 
						&& item.type === newItem.type) {		// Question or option
					if(
							(newItem.property.type === "question" && 
									newItem.property.itemIndex === item.property.itemIndex &&
									newItem.property.formIndex === item.property.formIndex) ||
							(newItem.property.type === "option" && 
									newItem.property.itemIndex === item.property.itemIndex &&
									newItem.property.optionList === item.property.optionList) ) {
						
						changes.splice(j,1);	// Remove this item
						return;					
						
					}
				}
			}
		}
	}
	
	/*
	 * Update settings when the number of changes to apply changes 
	 */ 
	function setHasChanges(numberChanges) {
		if(numberChanges === 0) {
			changes = [];
			$('.m_save_survey').addClass("disabled").attr("disabled", true).find('.badge').html(numberChanges);
			
		} else {
			$('.m_save_survey').removeClass("disabled").attr("disabled", false).find('.badge').html(numberChanges);
		}
	}
	
	/*
	 * Update the in memory model
	 */
	function updateModel(change) {
		var refresh = false,		// Set to true if the page needs to be refreshed with this change
			survey = globals.model.survey,
			property;
		
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
						survey.forms[property.formIndex].questions[property.itemIndex][property.prop] = property.newVal;		//XXX
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
					survey.optionLists[property.optionList][property.itemIndex].labels[property.language][property.propType] = property.newVal;
				} else {
					// For non text changes update all languages
					for(i = 0; i < survey.optionLists[property.optionList][property.itemIndex].labels.length; i++) {
						survey.optionLists[property.optionList][property.itemIndex].labels[i][property.propType] = property.newVal;
						survey.optionLists[property.optionList][property.itemIndex].labels[i][property.propType + "Url"] = 
							_getUrl(survey.o_id, survey.ident, property.newVal, false, property.propType);
					}
				}
			}
		} else if(change.ChangeType === "question") {
			if(change.action === "add") {
				survey.forms[change.question.fIndex].questions.splice(change.question.seq, 0, change.question);
			} else if(change.action === "delete") {
				// TODO
			} else {
				console.log("Unknown action: " + change.action);
			}
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
			if(change.propType === "image") {	
				
				newMarkup = markup.addMedia("Image", 
						change.newVal, 
						_getUrl(survey.o_id, survey.ident, change.newVal, false, 'image', change.isSurveyLevel), 
						_getUrl(survey.o_id, survey.ident, change.newVal, true, 'image', change.isSurveyLevel)
						);
				
			} else if(change.propType === "video") {
				
				newMarkup = markup.addMedia("Video", 
						change.newVal, 
						_getUrl(survey.o_id, survey.ident, change.newVal, false, 'video', change.isSurveyLevel), 
						_getUrl(survey.o_id, survey.ident, change.newVal, true, 'video', change.isSurveyLevel)
						);
				
			} else if(change.propType === "audio") {
				
				newMarkup = markup.addMedia("Audio", 
						change.newVal, 
						_getUrl(survey.o_id, survey.ident, change.newVal, false, 'audio', change.isSurveyLevel), 
						undefined
						);	
	
			}
			
			if(newMarkup) {
				if(change.type === "question") {
					$changedRow = $('#formList').find('td.question').filter(function(index){
						var $this = $(this);
						return $this.data("fid") == change.formIndex && $this.data("id") == change.itemIndex;
					});
				} else {
					// changed row for choices
				}
				if($changedRow) {
					$changedRow.find('.' + change.propType + 'Element').replaceWith(newMarkup);
					$('.mediaProp', $changedRow).off().click(function(){
						var $this = $(this);
						mediaPropSelected($this);
					});
				}
			}
		} else if(change.changeType === "question") {
			if(change.action === "add") {
				if(change.question.locn === "after") {
					change.question.$relatedElement.after(markup.addOneQuestion(change.question, change.question.fIndex, change.question.seq));
					$changedRow = change.question.$relatedElement.after();
				} else {
					change.question.$relatedElement.before(markup.addOneQuestion(change.question, change.question.fIndex, change.question.seq));
					$changedRow = change.question.$relatedElement.after();
				}
				delete change.question.$relatedElement;		// Delete this, it is no longer needed and contains circular references which cannot be stringified
			} else if(change.action === "delete") {
				// TODO
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
			if(type !== "image") {			
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
});