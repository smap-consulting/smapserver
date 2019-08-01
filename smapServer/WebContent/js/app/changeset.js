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
        'app/editorMarkup',
        'app/option'],
    function($, modernizr, lang, globals, markup, option) {

        var modelGeneratedChanges = [];

        return {
            add: add,
            undo: undo,
            save: save,
            setHasChanges: setHasChanges,
            addValidationError: addValidationError,
            removeValidationError: removeValidationError,
            validateItem: validateItem,
            validateName: validateName,
            updateModelWithErrorStatus: updateModelWithErrorStatus,
            validateAll: validateAll,
            numberIssues: numberIssues,
            addUpdateMessage: addUpdateMessage,
            updateViewControls: updateViewControls
        };

		/*
		 * Add a new change item
		 */
        function add(change) {
            var refresh,
                $context,
                container,
                itemIndex,
                itemType;

            // Apply to model
            modelGeneratedChanges = [];
            refresh = updateModel(change);

			/*
			 * Validate updated model
			 */
            if(change.changeType === "property" || change.changeType === "label") {

                if (change.property.type === "option") {
                    container = change.property.optionList;
                    itemType = "option";
                } else {
                    container = change.property.formIndex;
                    itemType = "question";
                }
                itemIndex = change.property.itemIndex;

            } else if(change.changeType === "option") {

                container = change.option.optionList;
                itemIndex = change.option.itemIndex;
                itemType = "option";

            } else if(change.changeType === "question") {

                container = change.question.formIndex;
                itemIndex = change.question.itemIndex;
                itemType = "question";

            }
            if(typeof itemIndex !== "undefined") {		// Note some changes do not have an itemIndex and don't need to be validated

                validateItem(container, itemIndex, itemType, true);
            }

			/*
			 * Apply any HTML changes either directly to the changed element, or by refreshing the whole form using
			 * the updated model
			 */
            if(change.action === "delete" && typeof change.question !== "undefined") {
                $context = undefined;
                change.question.$deletedElement.prev().remove();		// remove the add new button
                change.question.$deletedElement.remove();
            } else {
                if(refresh) {
                    if(change.question || change.changeType === "optionlist" || (change.property && change.property.type === "question")) {
                        $context = markup.refresh();
	                    validateAll();
                    } else {
                        $context = option.createChoiceView();
                        var survey = globals.model.survey;
                        var question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
                        option.addOptionTable(question, globals.gFormIndex, globals.gListName);
                        option.setupChoiceView($('#filterType').val());
                    }
                } else {
                    $context = updateHtmlElement(change);
                }
            }

            // Add to changeset array ready for writing to the database
            addToChangesetArray(change);

            // Add any additional changes generated in the model
            for(i = 0; i < modelGeneratedChanges.length; i++) {
                addToChangesetArray(modelGeneratedChanges[i]);
            }

            return $context;

        }

        function undo() {
        }

        // Save the survey
        function save(callback) {

            var url="/surveyKPI/surveys/save/" + globals.gCurrentSurvey,
                changesString,
                changesInSeq = resequenceChangeSet(globals.changes);


            changesString = JSON.stringify(changesInSeq);
            setHasChanges(0);
            globals.gSaveInProgress = true;
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
                    globals.gSaveInProgress = false;

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
                        h[++idx] = localise.set["msg_upd"];
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
                    globals.gSaveInProgress = false;

                    if(typeof responseFn === "function") {
                        responseFn();
                    }

                    if(xhr.readyState == 0 || xhr.status == 0) {
                        // Not an error
                    } else {
                        bootbox.alert(localise.set["msg_err_save"] + ' ' + err);
                    }
                }
            });

        };

        /*
         * Resequence a change array to allign with the sequences in the model
         */
        function resequenceChangeSet(changes) {

            var fo = [],
                i,
                newSeq = [];

            for(i = 0; i < globals.model.survey.forms.length; i++) {
                fo.push({});
            }

            // Set the sequence nuber as per the model
            for(i = 0; i < changes.length; i++) {
                if(globals.changes[i].changeType === "question") {
                    var question = globals.changes[i].items[0].question;
                    if(question && globals.changes[i].items[0].action === "add") {
                        var form = globals.model.survey.forms[question.formIndex];
                        for (var j = 0; j < form.qSeq.length; j++) {
                            if(form.qSeq[j] == question.itemIndex) {
                                question.seq = j;
                                fo[question.formIndex]["q"+j] = globals.changes[i];
                                break;
                            }
                        }
                    }
                }
            }

            // Put the question changes in the order of their sequence numbers
            for(i = 0; i < globals.model.survey.forms.length; i++) {
                var form = globals.model.survey.forms[i];
                for (var j = 0; j < form.qSeq.length; j++) {
                    if(fo[i]["q"+j]) {
                        newSeq.push(fo[i]["q"+j]);
                    }
                }
            }

            // Add any other changes
            for(i = 0; i < changes.length; i++) {
                if (globals.changes[i].changeType !== "question"
                    || typeof globals.changes[i].items[0].question === "undefined"
                    || globals.changes[i].items[0].action !== "add"
                ) {
                    newSeq.push(changes[i]);
                }
            }

            return newSeq;

        }

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
                forms_orig,
                optionListOrig,
                form,
                applyChange = true,
                changes = globals.changes;

            // Delete any items containing jquery elements
            if(change.question) {
                delete change.question.$relatedElement;
                delete change.question.$deletedElement;
            }

			/*
			 * Add additional parameters to change object
			 */
            if(change.changeType === "label" || change.changeType === "property") {
                if(change.property.type === "question") {
                    item = survey.forms[change.property.formIndex].questions[change.property.itemIndex];
                    if(survey.forms_orig) {
                        forms_orig = survey.forms_orig[change.property.formIndex];
                        if (forms_orig) {
                            item_orig = forms_orig.questions[change.property.itemIndex];
                        }
                    }
                    change.property.name = item.name;
                    change.property.qId = item.id;
                    change.property.fId = item.fId;
                    change.property.childFormIndex = survey.forms.length - 1;
                    if(change.changeType === "property") {
                        setTypeSpecificChanges(change.property.prop, change, survey);
                    }
                } else if(change.property.type === "option") {
                    item = survey.optionLists[change.property.optionList].options[change.property.itemIndex];
                    optionListOrig = survey.optionLists_orig[change.property.optionList];
                    if(optionListOrig) {
                        item_orig = optionListOrig.options[change.property.itemIndex];
                    }
                    change.property.name = item.value;
                    change.property.o_id = item.id;
                    //if(change.changeType === "property") {
                    //	setOptionTypeSpecificChanges(change.property.prop, change, survey);
                    //}
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
                            change.property.key = getFormPath(form) + "/" + item.name +
                                (change.property.prop === "hint" ? ":hint" : ":label");
                        } else if(change.property.type === "option") {
                            change.property.key = getFormPath(form) + "/" + change.property.qname + "/" + item.value + ":label";
                            change.property.o_id = item.id;
                        }
                    }
                } else {
                    if(item_orig) {
                        change.property.oldVal = item_orig[change.property.prop];
                    }
                }

                change.property.languageName = survey.languages[change.property.language].name;			// For logging the event

            } else if(change.changeType === "question") {
                form = survey.forms[change.question.formIndex];
                change.question.fId = form.id;
                if(change.action === "delete") {
                    item = survey.forms[change.question.formIndex].questions[change.question.itemIndex];
                    change.question.id = item.id;
                    change.question.name = item.name;
                    change.question.path = item.path;
                }
                //else {
                //	change.question.path = getFormPath(form) + "/" + change.question.name;
                //}
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

			/*
			 * If this is a property update and the property is cascade_filters then convert to a string
			 * Filter values should be saved as a json string
			 */
            if(change.property) {
                if(change.property.type === "option" && change.property.prop === "cascade_filters") {
                    change.property.newVal = JSON.stringify(change.property.newVal);
                    change.property.oldVal = JSON.stringify(change.property.oldVal);
                }
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
            }
        }

		/*
		 * Remove duplicate updates.  This simplifies the analysis of changes to a survey
		 * For example if the user sets the label to "x", then changes it to "y" the change recorded should only be "y"
		 */
        function removeDuplicateChange(changes, change) {

            var j,
                item,
                element,
                newItem,
                newElement,
                newElementType,
                elementType;

            newItem = change.items[0];
            if(newItem.question) {
                newElement = newItem.question;
                newElementType = "question";
            } else if(newItem.option) {
                newElement = newItem.option;
                newElementType = "option";
            } else if(newItem.property) {
                newElement = newItem.property;
                newElementType = newElement.type;
            } else if(newItem.changeType === "optionlist") {
                newElement = {
                    itemIndex: newItem.name
                };
                newElementType = newItem.changeType;
            }

            for(j = 0; j < changes.length; j++) {

                item = changes[j].items[0];
                if(item.question) {
                    element = item.question;
                    elementType = "question";
                } else if(item.option) {
                    element = item.option;
                    elementType = "option";
                } else if(item.property) {
                    element = item.property;
                    elementType = element.type;
                } else if(item.changeType === "optionlist") {
                    element = {
                        itemIndex: item.name
                    };
                    elementType = newItem.changeType;
                }

				/*
				 * Only remove duplicates if
				 * 		- The new and existing element types are the same, ie both question or both option
				 * 		- They refer to the same item
				 */
                if(elementType === newElementType && newElement.itemIndex === element.itemIndex) {
                    if((newElementType === "question" && newElement.formIndex === element.formIndex) ||
                        (newElementType === "option" && newElement.optionList === element.optionList) ||
                        (newElementType === "optionlist")) {

                        console.log("Update to an item that is already in the update queue");


						/*
						 * 1. If this is a property update and there is already another update to
						 * 		- the same property
						 *    then remove the first update
						 */
                        if(newItem.action === "update" 	&& newItem.changeType === "property"
                            && item.changeType === "property") {

                            if(newElement.prop === element.prop) {
                                changes.splice(j,1);	// Remove the old item and apply the new one
                                return true;
                            }

							/*
							 * 2. If this is a label update and there is already another update to
							 * 		- the same language
							 * 		- the same label type (in the propType attribute)
							 *    then remove the first update
							 */
                        } else if(newItem.action === "update" 	&& newItem.changeType === "label"
                            && item.changeType === "label") {

                            if(newElement.languageName === element.languageName &&
                                newElement.propType === element.propType) {
                                changes.splice(j,1);	// Remove the old item and apply the new one
                                return true;
                            }

							/*
							 * 3. If this is an update to a property or label and
							 *      - the existing item is newly added then
							 * 		- merge the update into the added item
							 */
                        } else if(newItem.action === "update"
                            && ((elementType === "question" && typeof element.qId === "undefined") ||
                            (elementType === "option" && item.action === "add"))
                            && (newItem.changeType === "label" || newItem.changeType === "property")) {

                            if(newItem.changeType === "label") {

                                if(!element.labels) {
                                    element.labels = [];
                                }
                                if(!element.labels[newElement.language]) {
                                    element.labels[newElement.language] = {};
                                }
                                element.labels[newElement.language][newElement.propType] = newElement.newVal;

                            } else {

                                element[newElement.prop] = newElement.newVal;

                            }

                            if(elementType === "question") {
                                if(newElement.prop === "name") {
                                    element["path"] = newElement.path;
                                }
                            } else {
                                if(newElement.prop === "value") {
                                    element["path"] = newElement.path;
                                }
                            }

                            return false;		// Don't apply the change it has been merged

							/*
							 * 4. If this is a move of an item
							 */
                        } else if(newItem.action === "move") {
							/*
							 * Remove any modifications to this deleted element
							 */
                            element.seq = newElement.seq;
                            return false;


							/*
							 * 5. If this is a delete of an item
							 */
                        } else if(newItem.action === "delete") {
							/*
							 * Remove any modifications to this deleted element
							 * If the item is a group question then also remove its group end
							 */
                            changes.splice(j,1);	// Remove this item
                            return true;

                        }
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
                globals.changes = [];
                $('.m_save_survey').addClass("disabled").prop("disabled", true).find('.badge').html(numberChanges);
                $('.m_languages,#m_required,#m_not_required').closest('li').removeClass("disabled").prop("disabled", false);
            } else {
                $('.m_save_survey').find('.badge').html(numberChanges);
                $('.m_languages,#m_required,#m_not_required').closest('li').addClass("disabled").prop("disabled", true);
                $('.m_validate').removeClass("disabled").prop("disabled", false);
                $('.m_save_survey').removeClass("disabled").prop("disabled", false);
                //if(numberIssues("error") === 0) {
                //	$('.m_save_survey').removeClass("disabled").prop("disabled", false);
                //}
            }

        }


		/*
		 * Update the in memory model
		 */
        function updateModel(change) {
            var refresh = false,		// Set to true if the page needs to be refreshed with this change
                survey = globals.model.survey,
                question,
                theOption,
                property,
                length,
                i, j;

            if(change.property) {
				/*
				 * Update the property values
				 */
                property = change.property;

                if(property.type === "question") {			// Change to a question
                    question = survey.forms[property.formIndex].questions[property.itemIndex];
                    if(property.propType === "text" || property.propType === "hint") {		// Not a media change

                        if(property.prop === "label" || property.prop === "hint") {	   // Changing the label or hint

                            question.labels[property.language][property.propType] = property.newVal;

                        } else {						// Other properties, such as constraints, relevance question name

                            var oldVal = question[property.prop];
                            question[property.prop] = property.newVal;


                            if(property.setVisible) {
                                question["visible"] = property.visibleValue;
                                question["source"] = property.sourceValue;
                            }

							/*
							 * Set type dependent properties
							 */
                            if(property.prop === "type" || property.prop === "name") {

                                if(property.newVal.indexOf("select") == 0 || question.type.indexOf("select") == 0 ||
                                    property.newVal === "rank" || question.type === "rank") {	// Select question

                                    // Ensure there is a list name for this question
                                    if(!question.list_name && question.name) {
                                        question.list_name = question.name;
                                    }

                                    // Ensure there is a list of choices
                                    if(question.list_name) {
                                        var optionList = survey.optionLists[question.list_name];
                                        if(!optionList) {
                                            survey.optionLists[question.list_name] = {
                                                oSeq: [],
                                                options: []
                                            };
                                            option.refreshOptionListControls();
                                        }
                                    }

                                }
                            }


                            if(property.prop === "type") {

                                if(property.newVal == "begin repeat") {
                                    // New sub form
                                    survey.forms.push({
                                        id: undefined,
                                        name: survey.forms[property.formIndex].questions[property.itemIndex].name,  // name of question
                                        parentQuestionIndex: property.itemIndex,
                                        parentFormIndex: property.formIndex,
                                        questions: [],
                                        qSeq: []
                                    });
                                    question.childFormIndex = survey.forms.length - 1;

                                } else if(property.newVal == "geopoint" || property.newVal == "geoshape" || property.newVal == "geotrace") {

                                    // Set the question name automatically
                                    modelGeneratedChanges.push({
                                        changeType: "property",
                                        action: "update",
                                        source: "editor",
                                        property: {
                                            type: "question",
                                            prop: "name",
                                            newVal: "the_geom",
                                            oldVal: question.name,
                                            language: property.language,
                                            formIndex: property.formIndex,
                                            itemIndex: property.itemIndex

                                        }
                                    });
                                    question.name = "the_geom";

                                }

                                // Fix ups depending on oldVal
                                var newFormIdx;
                                var oldFormIdx;
                                var endQuestionIdx;
                                var groupMembers = [];
                                var mvStartIdx;
                                var itemIndex;
                                if(oldVal === "note") {
                                    // Remove the readonly status
                                    modelGeneratedChanges.push({
                                        changeType: "property",
                                        action: "update",
                                        source: "editor",
                                        property: {
                                            type: "question",
                                            prop: "readonly",
                                            newVal: false,
                                            language: property.language,
                                            formIndex: property.formIndex,
                                            itemIndex: property.itemIndex
                                        }
                                    });
                                    question.readonly = false;
                                } else if(oldVal === "begin group") {
                                    console.log("xxxxxxx fix up begin group");
                                    if(property.newVal === "begin repeat") {
                                        console.log("xxxxxxx move questions to repeat");
                                        // Move the questions in the group to the new form
                                        newFormIdx = question.childFormIndex;
                                        oldFormIdx = property.formIndex;
                                        oldLocation = property.itemIndex;
                                        newLocation = 0;
                                        endQuestionIdx = 0;

                                        name = question.name;
                                        endName = name + "_groupEnd";

										/*
										 * Get the questions to move
										 * Start from one past the begin group
										 */
                                        oldLocation++;
                                        for(i = oldLocation; i < survey.forms[oldFormIdx].qSeq.length; i++) {

                                            // Don't include the group end or any questions after it
                                            if(survey.forms[oldFormIdx].questions[survey.forms[oldFormIdx].qSeq[i]].name.toLowerCase() === endName.toLowerCase()) {
                                                endQuestionIdx = i;
                                                break;
                                            }
                                            groupMembers.push(survey.forms[oldFormIdx].qSeq[i]);
                                        }

										/*
										 * Move the group members
										 */
                                        mvStartIdx = oldLocation;
                                        for(i = 0; i < groupMembers.length; i++) {

                                            itemIndex = moveQuestion(survey, survey.forms[oldFormIdx].questions[groupMembers[i]],
                                                newFormIdx,
                                                newLocation++,
                                                oldFormIdx,
                                                mvStartIdx++);

                                            // Add the move to the changeset
                                            modelGeneratedChanges.push({
                                                changeType: "question",
                                                action: "move",
                                                source: "editor",
                                                question: {

                                                    type: survey.forms[oldFormIdx].questions[groupMembers[i]].type,
                                                    formIndex: newFormIdx,
                                                    itemIndex: itemIndex,
                                                    sourceFormIndex: oldFormIdx,
                                                    sourceItemIndex: survey.forms[oldFormIdx].questions[groupMembers[i]].itemIndex,
                                                    sourceFormId: survey.forms[oldFormIdx].id,
                                                    name: survey.forms[oldFormIdx].questions[groupMembers[i]].name
                                                }
                                            });

                                            console.log("xxxxx moved");
                                        }

                                    }
                                    console.log("xxxxxxx remove end group");
                                    applyToEndGroup(survey.forms[oldFormIdx], question.name, oldLocation, "delete", undefined, undefined);
                                    refresh = true;
                                } else if(oldVal === "begin repeat") {
                                    console.log("xxxxxxx fix up begin repeat");
                                    if(property.newVal === "begin group") {
                                        console.log("xxxxxxx move questions to group");

                                        // Move the questions in the repeat to the group
                                        newFormIdx = property.formIndex;
                                        newLocation = property.itemIndex + 1; // After the begin group
                                        oldLocation = 0;
                                        endQuestionIdx = 0;
                                        oldFormIdx = getSubFormIndex(newFormIdx, property.itemIndex);

                                        name = question.name;
                                        endName = name + "_groupEnd";

										/*
										 * Get the questions to move
										 */
                                        groupMembers = [];
                                        for(i = oldLocation; i < survey.forms[oldFormIdx].qSeq.length; i++) {
                                            groupMembers.push(survey.forms[oldFormIdx].qSeq[i]);
                                        }

										/*
										 * Move the group members
										 */
                                        mvStartIdx = oldLocation;
                                        for(i = 0; i < groupMembers.length; i++) {

                                            itemIndex = moveQuestion(survey, survey.forms[oldFormIdx].questions[groupMembers[i]],
                                                newFormIdx,
                                                newLocation++,
                                                oldFormIdx,
                                                mvStartIdx++);

                                            // Record the group that this question has been added to
                                            survey.forms[newFormIdx].questions[survey.forms[newFormIdx].qSeq[newLocation - 1]].memberGroup = name;

                                            // Add the move to the changeset
                                            modelGeneratedChanges.push({
                                                changeType: "question",
                                                action: "move",
                                                source: "editor",
                                                question: {

                                                    type: survey.forms[oldFormIdx].questions[groupMembers[i]].type,
                                                    formIndex: newFormIdx,
                                                    itemIndex: itemIndex,
                                                    sourceFormIndex: oldFormIdx,
                                                    sourceItemIndex: survey.forms[oldFormIdx].questions[groupMembers[i]].itemIndex,
                                                    sourceFormId: survey.forms[oldFormIdx].id,
                                                    name: survey.forms[oldFormIdx].questions[groupMembers[i]].name
                                                }
                                            });

                                            console.log("yyyy moved");
                                        }

                                    }
                                    refresh = true;
                                }


                            } else if(property.prop === "name") {
                                // update the end group name
                                if(survey.forms[property.formIndex].questions[property.itemIndex].type === "begin group") {
                                    applyToEndGroup(survey.forms[property.formIndex],
                                        oldVal, 0, "rename", property.newVal, undefined);

                                }
                            }

							/*
							 * If this is a change to a begin group then refresh the entire form
							 */
                            if(survey.forms[property.formIndex].questions[property.itemIndex].type === "begin group") {
                                refresh = true;
                            }


                        }

                    } else {
                        // For non text changes update all languages
                        for(i = 0; i < survey.forms[property.formIndex].questions[property.itemIndex].labels.length; i++) {
                            survey.forms[property.formIndex].questions[property.itemIndex].labels[i][property.propType] = property.newVal;
                            survey.forms[property.formIndex].questions[property.itemIndex].labels[i][property.propType + "Url"] =
                                _getUrl(property.newVal, false, property.propType, property.isSurveyLevel, survey.id);
                        }
                    }
                } else if(property.type === "option") {	// Change to an option

                    theOption = survey.optionLists[property.optionList].options[property.itemIndex];
                    theOption[property.prop] = property.newVal;

                    if(property.propType === "text") {
                        if(property.prop === "label") {
                            survey.optionLists[property.optionList].options[property.itemIndex].labels[property.language][property.propType] = property.newVal;
                        }
                    } else {
                        // For non text changes update all languages
                        for(i = 0; i < survey.optionLists[property.optionList].options[property.itemIndex].labels.length; i++) {
                            survey.optionLists[property.optionList].options[property.itemIndex].labels[i][property.propType] = property.newVal;
                            survey.optionLists[property.optionList].options[property.itemIndex].labels[i][property.propType + "Url"] =
                                _getUrl(property.newVal, false, property.propType, property.isSurveyLevel, survey.id);
                        }
                    }
                } else if(property.type === "optionlist") {	// Change to an optionlist
                    // 1. Rename the option list
                    survey.optionLists[property.newVal] = survey.optionLists[property.oldVal];
                    delete survey.optionLists[property.oldVal];

                    // 2. Update all questions that refer to this option list so they reference the new one
                    for(i = 0; i < survey.forms.length; i++) {
                        for(j = 0; j < survey.forms[i].questions.length; j++) {
                            if(survey.forms[i].questions[j].list_name === property.oldVal) {
                                survey.forms[i].questions[j].list_name = property.newVal
                            }
                        }
                    }

                    //3.  Update the optionList property for all options in the changed optionList
                    for(i = 0; i < survey.optionLists[property.newVal].options.length; i++) {
                        survey.optionLists[property.newVal].options[i].optionList = property.newVal;
                    }
                } else {
                    console.log("Error: unknown property type: " + property.type);
                }

            } else if(change.changeType === "question") {		// Not a change to a property
                if(change.action === "move") {

                    var sourceForm = change.question.sourceFormIndex;
                    var sourceItem = change.question.sourceItemIndex;
                    var targetForm = change.question.formIndex;
                    var oldLocation = change.question.sourceSeq;
                    var newLocation = change.question.seq;
                    var question = survey.forms[sourceForm].questions[sourceItem];
                    var name = question.name;
                    var endName;
                    var form;

                    if(question.type === "begin group") {	// Move all the group members
                        name = question.name;
                        endName = name + "_groupEnd";
                        form = survey.forms[sourceForm];

						/*
						 * Get the questions to move
						 */
                        var groupMembers = [];
                        for(i = oldLocation; i < form.qSeq.length; i++) {

                            groupMembers.push(form.qSeq[i]);
                            if(form.questions[form.qSeq[i]].name.toLowerCase() === endName.toLowerCase()) {
                                break;
                            }
                        }

						/*
						 * Move the group members
						 */
                        for(i = 0; i < groupMembers.length; i++) {

                            moveQuestion(survey, form.questions[groupMembers[i]],
                                targetForm,
                                newLocation++,
                                sourceForm,
                                oldLocation++);

                            console.log("moved");
                        }

                    } else {

                        // Move the single question
                        change.question.itemIndex = moveQuestion(survey, question,
                            targetForm,
                            newLocation,
                            sourceForm,
                            oldLocation);
                        
                        // Fix up the form being moved to point to its new parent
                        var movedForm;
                        if(question.type === "begin repeat") {
                            // Get the form being moved
                            for(i = 0; i < survey.forms.length; i++) {
                                movedForm = survey.forms[i];
                                if(movedForm.parentFormIndex === sourceForm && movedForm.parentQuestionIndex === sourceItem) {
                                    movedForm.parentFormIndex = targetForm;
                                    movedForm.parentform = survey.forms[targetForm].id;
                                    movedForm.parentQuestionIndex = change.question.itemIndex;
                                    break;
                                }
                            }
                        }

                    }
                    refresh = true;		// Do a complete refresh after moving questions

                } else if(change.action === "add") {
                    length = survey.forms[change.question.formIndex].questions.push(change.question);			// Add the new question to the end of the array of questions
                    change.question.itemIndex = length -1;
                    survey.forms[change.question.formIndex].qSeq.splice(change.question.seq, 0, length - 1);	// Update the question sequence array

                    if(change.question.firstQuestion) {
                        refresh = true;		// Refresh all the questions when adding the first question to a form
                    } else if(change.question.type === "end group") {
                        refresh = true;
                    }

                    // Change the name if this is a location question
                    if(change.question.type === "geopoint" ||
                        change.question.type === "geotrace" ||
                        change.question.type === "geoshape") {
                        change.question.name = "the_geom";
                    }

                    // Add a subform if required
                    if(change.question.type === "begin repeat") {
                        // New sub form
                        survey.forms.push({
                            id: undefined,
                            name: change.question.name,
                            parentQuestionIndex: change.question.itemIndex,
                            parentFormIndex: change.question.formIndex,
                            questions: [],
                            qSeq: []
                        });
                        change.question.childFormIndex = survey.forms.length - 1;
                    }

                    refresh = true;			// DEBUG - remove after fixing update of html

                } else if(change.action === "delete") {
                    var form = survey.forms[change.question.formIndex];
                    var question = form.questions[change.question.itemIndex];
                    form.qSeq.splice(change.question.seq, 1);	// Remove item from the sequence array
                    question.deleted = true;	// Mark deleted
                    if(question.type === "begin group") {
                        applyToEndGroup(form, question.name, change.question.seq, "delete", undefined, change.question.formIndex);
                        refresh = true;
                    }
                } else {
                    console.log("Unknown action: " + change.action);
                }


            } else if(change.changeType === "option") {				// Change to an option
                if(change.action === "move") {

                    var sourceOptionList = survey.optionLists[change.option.sourceOptionList];
                    var targetOptionList = survey.optionLists[change.option.optionList];

                    var theOption = sourceOptionList.options[change.option.sourceItemIndex];
                    var newOption = jQuery.extend(true, {}, theOption);
                    var oldLocation = change.option.sourceSeq;
                    var newLocation = change.option.seq;

                    // 1. Add the option in the new location
                    length = targetOptionList.options.push(newOption);
                    change.option.itemIndex = length -1;
                    change.option.value = theOption.value;
                    targetOptionList.oSeq.splice(change.option.seq, 0, length - 1);

                    // 2. Remove the option from the old location
                    // The old location may have changed if the new location was inserted before it
                    if(newLocation < oldLocation && change.option.sourceOptionList == change.option.optionList) {
                        oldLocation++;
                    }
                    sourceOptionList.oSeq.splice(oldLocation, 1);
                    theOption.deleted = true;

                    // 3. Update any items in the change list to the new location
                    if(globals.changes) {
                        for(i = 0; i < globals.changes.length; i++) {
                            var existingChange = globals.changes[i];
                            if(existingChange.changeType === "option") {
                                for(j = 0; j < existingChange.items.length; j++) {
                                    var existingItem = existingChange.items[j];
                                    if(existingItem.option.optionList === theOption.optionList &&
                                        existingItem.option.itemIndex === theOption.itemIndex) {
                                        // We moved an option thats in the change queue
                                        existingItem.option.optionList = change.option.optionList;
                                        existingItem.option.itemIndex = change.option.itemIndex;
                                    }
                                }
                            }
                        }
                    }
                    refresh = true;

                } else if(change.action === "add") {
                    length = survey.optionLists[change.option.optionList].options.push(change.option);			// Add the new option to the end of the array of options
                    change.option.itemIndex = length -1;
                    survey.optionLists[change.option.optionList].oSeq.splice(change.option.seq, 0, length - 1);	// Update the option sequence array

                } else if(change.action === "delete") {
                    survey.optionLists[change.option.optionList].oSeq.splice(change.option.seq, 1);	// Remove item from the sequence array
                    survey.optionLists[change.option.optionList].options[change.option.itemIndex].deleted = true;
                } else {
                    console.log("Unknown action: " + change.action);
                }

            } else if(change.changeType === "optionlist") {				// Change to an option list
                if(change.action === "add") {
                    survey.optionLists[change.name] = {
                        oSeq: [],
                        options: []
                    };
                    refresh = true;
                } else if(change.action === "delete") {
                    delete survey.optionLists[change.name];
                    refresh = true;
                } else {
                    console.log("Unknown action: " + change.action);
                }
            }
            return refresh;
        }

		/*
		 * Move a question
		 */
        function moveQuestion(survey, question, targetForm, newLocation, sourceForm, oldLocation) {

            var newQuestion = jQuery.extend(true, {}, question),
                itemIndex,
                i,j,
                change,
                item,
                length;

            // 1. Add the question in the new location
            length = survey.forms[targetForm].questions.push(newQuestion);			// Add the new question to the end of the array of questions
            itemIndex = length - 1;
            survey.forms[targetForm].qSeq.splice(newLocation, 0, length - 1);	// Update the question sequence array

            // 1.5 Update the sequences stored in the question objects
            //for(i = 0; i < survey.forms[targetForm].qSeq.length; i++) {
            //	var idx = survey.forms[targetForm].qSeq[i];
            //    survey.forms[targetForm].questions[idx].seq = i;
            //}

            // 2. Remove the question from the old location
            // The old location may have changed if the new location was inserted before it
            if(newLocation < oldLocation && sourceForm === targetForm) {
                oldLocation++;
            }
            survey.forms[sourceForm].qSeq.splice(oldLocation, 1);
            question.deleted = true;

            // 3. Update any items in the change list to put to the new location
            if(globals.changes) {
                for(i = 0; i < globals.changes.length; i++) {
                    change = globals.changes[i];
                    if(change.changeType === "question") {
                        for(j = 0; j < change.items.length; j++) {
                            item = change.items[j];
                            if(item.question.formIndex === question.formIndex &&
                                item.question.itemIndex === question.itemIndex) {
                                // We moved a question thats in the change queue
                                item.question.formIndex = targetForm;
                                item.question.itemIndex = itemIndex;
                                item.question.fId = survey.forms[targetForm].id;
                            }
                        }
                    }
                }
            }

            // Update the model
            question.itemIndex = itemIndex;
            question.formIndex = targetForm;

            return itemIndex;
        }
		/*
		 * Apply a change to the "end group" of a group
		 */
        function applyToEndGroup(form, name, start_seq, action, new_name, form_index) {
            var i,
                end_name = name + "_groupEnd";

            // Remove the group from the model
            for(i = start_seq; i < form.qSeq.length; i++) {
                if(form.questions[form.qSeq[i]].name === end_name) {
                    if(action === "delete") {
                        form.questions[form.qSeq[i]].deleted = true;
                        form.qSeq.splice(i, 1);
                    } else if(action === "rename") {
                        form.questions[form.qSeq[i]].name = new_name + "_groupEnd";
                    }
                    break;
                } else {

                    // Delete the member
                    if(action === "delete"  && typeof form_index !== "undefined") {
                        modelGeneratedChanges.push({
                            changeType: "question",
                            action: "delete",
                            source: "editor",
                            question: {
                                seq: i,
                                formIndex: form_index,
                                itemIndex: i
                            }
                        });
                    }
                }
            }

            // Remove group end from the pending changes list
            for(i = 0; i < globals.changes.length; i++) {
                if(globals.changes[i].items[0].question) {
                    if(globals.changes[i].items[0].question.name === end_name) {
                        if(action === "delete") {
                            globals.changes.splice(i, 1);
                        } else if(action === "rename") {
                            globals.changes[i].items[0].question.name = new_name + "_groupEnd";
                        }
                    }
                }
            }

        }

		/*
		 * Update the modified HTML element
		 */
        function updateHtmlElement(change) {
            var newMarkup,
                survey = globals.model.survey,
                $changedRow,
                i,
                collapsedPanels = [];

            if(change.changeType === "label") {
                if(change.property.propType === "image") {

                    newMarkup = markup.addMedia("Image",
                        change.property.newVal,
                        _getUrl(change.property.newVal, false, 'image', change.property.isSurveyLevel, survey.id),
                        _getUrl(change.property.newVal, true, 'image', change.property.isSurveyLevel, survey.id)
                    );

                } else if(change.property.propType === "video") {

                    newMarkup = markup.addMedia("Video",
                        change.property.newVal,
                        _getUrl(change.property.newVal, false, 'video', change.property.isSurveyLevel, survey.id),
                        _getUrl(change.property.newVal, true, 'video', change.property.isSurveyLevel, survey.id)
                    );

                } else if(change.property.propType === "audio") {

                    newMarkup = markup.addMedia("Audio",
                        change.property.newVal,
                        _getUrl(change.property.newVal, false, 'audio', change.property.isSurveyLevel, survey.id),
                        undefined
                    );

                }

                if(newMarkup) {
                    if(change.property.type === "question") {
                        $changedRow = $('#question' + change.property.formIndex + '_' + change.property.itemIndex);
						/*
						 $changedRow = $('#formList').find('li.question').filter(function(index){
						 var $this = $(this);
						 return $this.data("fid") == change.property.formIndex && $this.data("id") == change.property.itemIndex;
						 });
						 */
                    } else {

                        $changedRow = $('#choiceView').find('tr').filter(function(index){
                            var $this = $(this);
                            return $this.data("id") == change.property.itemIndex;
                        });
                    }

                    if($changedRow) {
                        $changedRow.find('.' + change.property.propType + 'Element').replaceWith(newMarkup);
                    }
                }
            } else if(change.changeType === "question") {

                var form = survey.forms[change.question.formIndex];

                if(change.action === "add") {
                    var preceedingQuestion = form.questions[form.qSeq[change.question.seq-1]];
                    if(change.question.locn === "after") {
                        change.question.$relatedElement.after(markup.addOneQuestion(form, change.question, change.question.formIndex, change.question.itemIndex, true, undefined, false));
                    } else {
                        change.question.$relatedElement.prev().before(markup.addOneQuestion(form, change.question, change.question.formIndex, change.question.itemIndex, true, undefined, false));
                    }
                    $changedRow = $("#question" + change.question.formIndex + "_" + change.question.itemIndex);

                } else if(change.action === "delete") {
                    change.question.$deletedElement.prev().remove();	// Remove the add before button
                    change.question.$deletedElement.remove();
                }
            } else if(change.changeType === "option") {
                if(change.action === "add") {
                    var optionList,
                        $ref;


                    optionList = survey.optionLists[change.option.optionList];

                    // get the new markup
                    newMarkup = option.addOneOption(optionList,
                        change.option,
                        change.option.formIndex,
                        change.option.itemIndex,
                        change.option.optionList,
                        change.option.qName);


                    $ref = $('#choiceView').find('.editor_element').
                    filter(function(index) {
                        var $this = $(this);
                        return $this.data("id") == change.option.optionIndex;
                    });

                    if(change.option.locn == "after") {
                        $ref.after(newMarkup);
                        $changedRow = $ref.next();
                    } else if(change.option.locn == "before") {
                        $ref.before(newMarkup);
                        $changedRow = $ref.prev();
                    } else {
                        // put at end of table
                        $('#choiceView tbody').append(newMarkup);
                        $changedRow = $('#choiceView tbody tr:last');
                    }

                } else if(change.action === "delete") {
                    change.option.$deletedElement.remove();
                }
            } else if(change.changeType === "property") {
                // Apply any markup changes that result from a property change


                // 1. Update the question / option
                if(change.property.type === "option") {
					/*
					 * No option property changes result in markup changes
					 */
                } else if(change.property.type === "question") {

                    // Get the changed question row
                    $changedRow = $('#formList').find('li.question').filter(function(index){
                        var $this = $(this);
                        return $this.data("fid") == change.property.formIndex && $this.data("id") == change.property.itemIndex;
                    });

                    newMarkup = markup.addOneQuestion(
                        survey.forms[change.property.formIndex],
                        survey.forms[change.property.formIndex].questions[change.property.itemIndex],
                        change.property.formIndex,
                        change.property.itemIndex,
                        false, undefined, true);

                }

				/*
				 * Apply the update
				 */
                if($changedRow) {

                    // Get the current list of collapsed panels
                    $('.collapse.in', $changedRow).each(function(){
                        collapsedPanels.push($(this).closest('li').attr("id"));
                    });

                    // Update the content view
                    $changedRow.replaceWith(newMarkup);

                    // Restore collapsed panels
                    for(i = 0; i < collapsedPanels.length; i++) {
                        $('#' + collapsedPanels[i]).find('.collapse').addClass("in");
                    }

                    // Since we replaced the row we had better get the replaced row so that actions can be reapplied
                    if(change.property.type === "option") {
                        $changedRow = $('#formList').find('li.option').filter(function(index){
                            var $this = $(this);
                            return $this.data("fid") == change.property.formIndex && $this.data("id") == change.property.itemIndex;
                        });
                    } else {
                        $changedRow = $('#formList').find('li.question').filter(function(index){
                            var $this = $(this);
                            return $this.data("fid") == change.property.formIndex && $this.data("id") == change.property.itemIndex;
                        });
                    }
                }

            }


            return $changedRow;
        }

        function getQuestionsUsingOptionList(list) {
            var i, j,
                survey = globals.model.survey,
                forms = survey.forms,
                question,
                nameArray = [];

            for(i = 0; i < forms.length; i++) {
                for(j = 0; j < forms[i].questions.length; j++) {
                    question = forms[i].questions[j];
                    if(!question.deleted &&  !question.soft_deleted &&
                        (question.type.indexOf("select") === 0 || question.type === "rank") &&
                        question.list_name === list) {

                        nameArray.push(question.name);
                    }
                }
            }
            return nameArray;
        }

		/*
		 * Media functions
		 */
        function _getUrl(newVal, thumbs, type, isSurveyLevel, sId) {
            var url = "/surveyKPI/file/",
                filebase,
                ext,
                index;

            if(newVal) {

                // Videos and other derived thumbnails will have type jpg
                if(thumbs && type !== "image") {
                    index = newVal.lastIndexOf('.');
                    filebase = url.substr(0, index);
                    newVal = filebase + ".jpg";
                }

                url += newVal;

                if(isSurveyLevel) {
                    url += "/survey/"
                    url += sId;
                } else {
                    url += "/organisation";
                }

                if(thumbs) {
                    url += "?thumbs=true";
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
                j,
                action,
                result,
                name;

            if(data.updateFailed && forError) {
                h[++idx] = '<li>';
                h[++idx] = data.errorMsg;
                h[++idx] = '</li>';
            } else if(!data.updateFailed && !forError) {
                for(j = 0; j < data.items.length; j++) {
                    h[++idx] = '<li>';
                    h[++idx] = getChangeDescription(data.items[j]);
                    h[++idx] = '</li>';
                }
            }

            return h.join("");
        }

		/*
		 * ***************************************************************
		 * Validate each change to a question or option
		 */
        function validateItem(container, itemIndex, itemType, removeExisting) {

            var i, j,
                form,
                survey = globals.model.survey,
                item,
                name,
                isDeleted,
                isValid = true,
                hasDuplicate = false,
                changes = globals.changes,
                numberErrors,
                numberWarnings;

            if(itemType === "question") {
                item = survey.forms[container].questions[itemIndex];
                name = item.name;
            } else if(itemType === "option") {
                item = survey.optionLists[container].options[itemIndex];
                name = item.value;
            } else if(itemType === "optionlist") {
                item = survey.optionLists[container];
                name = container;
            }

			/*
			 * Remove the existing error
			 * If this validation has been called by validateAll() then this removal would already have been done
			 */
            if(removeExisting) {
                removeValidationError(container, itemIndex,	"item", itemType);
            }

            if(!item.deleted &&
                ((itemType === "question" && markup.includeQuestion(item)) ||
                (itemType === "optionlist") ||
                (itemType === "option"))) {

                // Validate the name
                isValid = validateName(container, itemIndex, name, itemType, removeExisting);

				/*
				 * Question specific validations
				 */
                if(itemType === "question") {

                    // Check references to other questions
                    isValid = checkReferences(container, itemIndex, itemType, item);

                    // Check for multiple geom types in a single form
                    if(isValid) {
                        if(item.type === "geopoint" || item.type === "geoshape" || item.type === "geotrace") {
                            form = survey.forms[container];
                            for(j = 0; j < form.questions.length; j++) {
                                var otherQuestion = form.questions[j];
                                if(j != itemIndex) {
                                    if(!otherQuestion.soft_deleted && !otherQuestion.deleted &&
                                        (otherQuestion.type === "geopoint" || otherQuestion.type === "geotrace" ||
                                        otherQuestion.type === "geoshape")) {
                                        addValidationError(
                                            container,
                                            itemIndex,
                                            "item",
                                            localise.set["ed_o_o_g"],	// Only one geometry question can be added to a form
                                            itemType,
                                            "error");
                                        isValid = false;
                                        break;
                                    }
                                }
                            }
                        }
                    }


                    if(isValid) {	// Check parenthesis on relevant
                        isValid = checkParentheisis(container, itemIndex, itemType, item.relevant);
                    }

                    if(isValid) {	// Check parenthesis on constraint
                        isValid = checkParentheisis(container, itemIndex, itemType, item.constraint);
                    }

                    if(isValid) {	// Check parenthesis on calculation
                        isValid = checkParentheisis(container, itemIndex, itemType, item.calculation);
                    }

                    if(isValid) {	// Check parenthesis on choiceFilter
                        isValid = checkParentheisis(container, itemIndex, itemType, item.choice_filter);
                    }

	                if(isValid && (item.type === "child_form" || item.type === "parent_form")) {	// Check that form launch types have a reference form

	                    if(item.parameters && item.parameters.indexOf("form_identifier") >= 0) {
	                        isValid = true;
	                    } else {
	                        isValid = false;
		                    addValidationError(
			                    container,
			                    itemIndex,
			                    "item",
			                    localise.set["ed_f_l"],	// Only one geometry question can be added to a form
			                    itemType,
			                    "error");
	                    }

	                }

	                if(isValid && item.type === "begin repeat") {	// Check that a repeat group has children

	                    var itemx = item;
		                var childFormIndex = getSubFormIndex(item.formIndex, itemIndex);
		                var questions = survey.forms[childFormIndex].questions;
                        var isEmpty = true;

                        if(questions.length > 0) {
                            for(j = 0; j < questions.length; j++) {
                                if(questions[j].type !== 'calculate') {
                                    isEmpty = false;
                                    break;
                                }
                            }
                        }

                        if(isEmpty) {
	                        isValid = false;
	                        addValidationError(
		                        container,
		                        itemIndex,
		                        "item",
		                        localise.set["ed_emp_rep"],
		                        itemType,
		                        "warning");
                        }
	                }

	                if(isValid && item.type === "begin group") {	// Check that a group has children

		                var endName = item.name + "_groupEnd";
		                var questions = survey.forms[item.formIndex].questions;
		                var qSeq = survey.forms[item.formIndex].qSeq;
		                var isEmpty = true;
		                var inGroup = false;

		                /*
						 * Get the questions in the group
						 */
		                for(j = 0; j < qSeq.length; j++) {

		                    if(inGroup === true) {
			                    if (questions[qSeq[j]].name.toLowerCase() === endName.toLowerCase()) {
				                    break;
			                    } else if (questions[qSeq[j]].type !== 'calculate') {
				                    isEmpty = false;
				                    break;
			                    }
		                    }

			                if(qSeq[j] === itemIndex) {
				                inGroup = true;
			                }

		                }

		                if(isEmpty) {
			                isValid = false;
			                addValidationError(
				                container,
				                itemIndex,
				                "item",
				                localise.set["ed_emp_rep"],
				                itemType,
				                "warning");
		                }
	                }


                } else if(itemType === "option") {
                    // Check references to other questions
                    isValid = checkReferences(container, itemIndex, itemType, item);
                }

                if(!isValid) {
                    updateModelWithErrorStatus(container, itemIndex, itemType);	// Update model and DOM
                }


				/*
				 * If there were no errors check for warnings
				 * Only do this on validateAll as otherwise it is just annoying to get the warnings
				 */
                if(!removeExisting) {
                    if(isValid) {
                        if(item.visible || itemType === "option") {
                            isValid = checkBlankLabels(container, itemIndex, itemType, item, "warning");
                        }
                    }

                    if(isValid) {
                        if(item.visible && itemType === "question") {
                            isValid = checkMissingChoices(container, itemIndex, itemType, item, "warning");
                        }
                    }
                }
            }

            // Set the control buttons
            numberErrors = numberIssues("error");
            numberWarnings = numberIssues("warning");

            $('.error-count').html(numberErrors);
            $('.warning-count').html(numberWarnings);

            //if(numberErrors > 0) {
            //	$('.m_save_survey').addClass("disabled").prop("disabled", true);
            //} else
            if(changes.length > 0) {
                $('.m_save_survey').removeClass("disabled").prop("disabled", false);
            }

            if(numberErrors > 0 || numberWarnings > 0) {

                $('#error-nav-btns').show();
                if(numberErrors > 0) {
                    $('#next-error').removeClass("disabled");
                } else {
                    $('#next-error').addClass("disabled");
                }
                if(numberWarnings > 0) {
                    $('#next-warning').removeClass("disabled");
                } else {
                    $('#next-warning').addClass("disabled");
                }
            }  else {
                $('#error-nav-btns').hide();
            }

        }

		/*
		 * Check for blank labels
		 */
        function checkBlankLabels(container, itemIndex, itemType, item) {
            var i,
                valid = true;

            for(i = 0; i < item.labels.length; i++) {
                if(typeof item.labels[i].text === "undefined" ||
                    (typeof item.labels[i].text === "undefined" && item.labels[i].text.trim().length === 0)) {
                    if(itemType === "question" && (item.type === "begin repeat" || item.type === "begin group")) {
                        continue;		// Don't report warnings on blank labels for these question types
                    }
                    addValidationError(
                        container,
                        itemIndex,
                        "item",
                        "Blank Label for language: " + globals.model.survey.languages[i].name,
                        itemType,
                        "warning");
                    valid = false;
                    break;
                }
            }

            return valid;
        }

		/*
		 * Check for Missing choices in a select questions
		 */
        function checkMissingChoices(container, itemIndex, itemType, item) {
            var i,
                valid = true,
                survey;

            if(item.type.indexOf('select') === 0 || item.type === "rank") {
                if(typeof globals.model.survey.optionLists[item.list_name] === "undefined") {
                    valid = false;
                }
                if(valid) {
                    var list = globals.model.survey.optionLists[item.list_name].options;
                    valid = false;
                    for(i = 0; i < list.length; i++) {
                        if(!list[i].deleted) {
                            valid = true;
                            break;
                        }
                    }
                }

                if(!valid) {
                    addValidationError(
                        container,
                        itemIndex,
                        "item",
                        "No choices in the choice list ",
                        itemType,
                        "warning");
                    valid = false;
                }

            }


            return valid;
        }

		/*
		 * Check for mismatched parenthesis
		 */
        function checkParentheisis(container, itemIndex, itemType, elem) {
            var i,
                c,
                depth = 0,
                lastOpen,
                errorText,
                isValid = true,
                locn;

            if(elem) {
                for(i = 0; i < elem.length; i++) {
                    c = elem.charAt(i);
                    if( c === '(') {
                        depth++;
                        locn = i;
                    } else if( c === ')') {
                        depth--;
                        locn = i;
                    }
                    if(depth < 0) {
                        break;
                    }
                }

                if(depth != 0) {
                    errorText = "Mis-matched parenthesis: " + elem.slice(0, locn) + '<b><span style="color:red;">' +
                        elem.slice(locn, locn + 1) +
                        '</span></b>' +
                        elem.slice(locn + 1);

                    addValidationError(
                        container,
                        itemIndex,
                        "item",
                        "Mis-matched parenthesis: " + errorText,
                        itemType,
                        "error");
                    isValid = false;
                }
            }

            return isValid;
        }

		/*
		 * Return false if the calculation is not valid
		 */
        function checkReferences(container, itemIndex, itemType, item) {

            var refQuestions = {},
                survey = globals.model.survey,
                form,
                i, j,
                name;

            // Get a list of references to other questions
            if(itemType  === "question") {
                getReferenceNames(item.relevant, refQuestions);
                getReferenceNames(item.constraint, refQuestions);
                getReferenceNames(item.calculation, refQuestions);
                getReferenceNames(item.choice_filter, refQuestions);
            }
            for(i = 0; i < item.labels.length; i++) {
                var text = item.labels[i].text;
                if(typeof text === "string") {
                    getReferenceNames(item.labels[i].text, refQuestions);
                }
            }

            var refCount = 0;
            for (name in refQuestions) {
                if (refQuestions.hasOwnProperty(name)) {
                    refCount++;
                }
            }

            if(refCount > 0) {

                for (i = 0; i < survey.forms.length; i++) {
                    form = survey.forms[i];
                    for (j = 0; j < form.questions.length; j++) {
                        var otherItem = form.questions[j];
                        var questionType = otherItem.type;
                        if (!otherItem.deleted && !otherItem.soft_deleted && questionType !== "end group") {
                            if (!(i === container && j === itemIndex)) {	// Don't test the question against itself!
                                otherItem = form.questions[j];

                                for (name in refQuestions) {
                                    if (refQuestions.hasOwnProperty(name)) {
                                        if (name === otherItem.name) {
                                            refQuestions[name].exists = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Check against preloads
                console.log("check against preloads");
                if(survey.meta) {
                    for (i = 0; i < survey.meta.length; i++) {
                        for (name in refQuestions) {
                            if (name === survey.meta[i].name) {
                                refQuestions[name].exists = true;
                            }
                        }
                    }
                }

                for (name in refQuestions) {
                    if (refQuestions.hasOwnProperty(name)) {
                        if (!refQuestions[name].exists) {
                            addValidationError(
                                container,
                                itemIndex,
                                "item",
                                localise.set["c_question"] + " ${" + name + "} " + localise.set["msg_not_f"],
                                itemType,
                                "error");
                            return false;
                        }
                    }
                }
            }

            return true;

        }

		/*
		 * Get the names of referenced questions in the passed in string
		 */
        function getReferenceNames(elem, refQuestions) {
            var names = [],
                reg = /\$\{[A-Za-z_][A-Za-z0-9_\-\.]*\}/g,
                i,
                name;

            if (elem) {
                names = elem.match(reg);
                if(names) {
                    for(i = 0; i < names.length; i++) {
                        if(names[i].length > 3) {
                            name = names[i].substring(2, names[i].length - 1);		// Remove the curly brackets
                            refQuestions[name] = {
                                name: name,
                                exists: false
                            };
                        }
                    }
                }
            }
        }

		/*
		 * Return true if the passed in value is accepted by xlsFormConverter
		 */
        function isValidODKQuestionName(val) {

            var sqlCheck = /^[A-Za-z_][A-Za-z0-9_\-\.]*$/;
            return sqlCheck.test(val);
        }

        function isValidODKOptionName(val) {

            var sqlCheck = /^[A-Za-z0-9_@\-\.\+\(\),%:\/]*$/;
            return sqlCheck.test(val);
        }

		/*
		 * Error types:  name || dupname || noname || dupgeom
		 */
        function addValidationError(container, itemIndex, errorType, msg, itemType, severity) {

            // Push error into validation array
            globals.errors.push({
                container: container,
                itemIndex: itemIndex,
                errorType: errorType,
                itemType: itemType,
                msg: msg,
                severity: severity
            });

            updateModelWithErrorStatus(container, itemIndex, itemType);

        }

		/*
		 * Update the model and the DOM to report any erors on the question
		 */
        function updateModelWithErrorStatus(container, itemIndex, itemType) {

            var $changedRow,
                survey = globals.model.survey,
                hasError = false,
                hasWarning = false,
                msg = "",
                i,
                errors = globals.errors,
                item;

            if(itemType === "question") {
                item = survey.forms[container].questions[itemIndex];
                $changedRow = $('#question' + container + '_' + itemIndex);
            } else if(itemType === "option") {
                item = survey.optionLists[container];
                $changedRow = $('#ol_' + container);
            }

            for(i = errors.length - 1; i >= 0; i--) {
                if(errors[i].itemType === itemType && errors[i].container === container &&
                    errors[i].itemIndex === itemIndex) {

                    if(errors[i].severity === "error") {
                        hasError = true;
                        msg = errors[i].msg;
                    } else {
                        hasWarning = true;
                        if(!hasError) {
                            msg = errors[i].msg;
                        }
                    }

                    if(errors[i].errorType === "name") {	// Break on name errors as this is the highest level error to show
                        break;
                    }

                }
            }

            // Update Model
            if(typeof item !== "undefined") {
                item.error = hasError;
                item.warning = hasWarning;
                item.errorMsg = msg;
            }

            // Update DOM
            $changedRow.find('.error-msg').html(msg);	// Add message
            if(globals.gShowingChoices) {
                $(".choice_filter_error").html(msg);
            }

            $changedRow.removeClass("error warning");
            if(hasError) {
                $changedRow.addClass("error");
                if(itemType === "question") {
                    $changedRow.find('.question_type').addClass("disabled");
                }
            } else {

                if(itemType === "question" && !item.published) {
                    $changedRow.find('.question_type').removeClass("disabled");
                }

                if(hasWarning) {
                    $changedRow.addClass("warning");
                }

            }
        }

		/*
		 * Remove the specified error type (see addValidationError for error types)
		 */
        function removeValidationError(container, itemIndex, errorType, itemType) {

            var i,
                $changedRow,
                survey = globals.model.survey;

            // Remove error
            for(i = globals.errors.length - 1; i >= 0; i--) {
                if(globals.errors[i].itemType === itemType &&
                    globals.errors[i].container === container &&
                    globals.errors[i].itemIndex === itemIndex &&
                    globals.errors[i].errorType === errorType) {
                    globals.errors.splice(i, 1);
                }
            }

            updateModelWithErrorStatus(container, itemIndex, itemType);  // Update model

        }

		/*
		 * Validate a question or option name
		 */
        function validateName(container, itemIndex, val, itemType, removeExisting) {

            var i, j,
                form,
                optionList,
                otherItem,
                survey = globals.model.survey,
                isValid = true,
                hasDuplicate = false,
                itemDesc,
                questionType,
                question,
                valLower;

            if(itemType === "question") {
                question = survey.forms[container].questions[itemIndex];
            }

            // Clear the existing name validation errors
            if(removeExisting) {
                removeValidationError(
                    container,
                    itemIndex,
                    "name",
                    itemType);
            }

            if(itemType === "question" ) {
                itemDesc = "question";
            } else if(itemType === "option" ) {
                itemDesc = "choice";
            } else if(itemType === "optionlist" ) {
                itemDesc = "choice list";
            } else {
                itemDesc = "unknown";
            }

            // Check for empty name
            if(typeof val === "undefined" || val === "") {
                addValidationError(
                    container,
                    itemIndex,
                    "name",
                    "This " + itemDesc + " does not have a name.  Specify a unique name.",
                    itemType,
                    "error");
                isValid = false;
            }

            // Check for invalid characters
            if(isValid) {
                if(itemType === "question") {
                    isValid = isValidODKQuestionName(val);

                    if(!isValid) {
                        addValidationError(
                            container,
                            itemIndex,
                            "name",
                            localise.set["ed_vq"],
                            itemType,
                            "error");

                    }

                    if(isValid) {
                        if(question.type == "geopoint"
                            || question.type == "geoshape"
                            || question.type == "geotrace"
                        ) {
                            if(val !== 'the_geom') {
                                addValidationError(
                                    container,
                                    itemIndex,
                                    "name",
                                    localise.set["ed_gl"],
                                    itemType,
                                    "error");
                            }
                        } else if(val === 'the_geom') {
                            addValidationError(
                                container,
                                itemIndex,
                                "name",
                                localise.set["ed_ogl"],
                                itemType,
                                "error");
                        }
                    }

                } else {
                    isValid = isValidODKOptionName(val);

                    if(!isValid) {
                        addValidationError(
                            container,
                            itemIndex,
                            "name",
                            localise.set["ed_vc"] + " : "  + val,
                            itemType,
                            "error");

                    }
                }
            }

			/*
			 * Question name change require the questions in all the forms to be validated for duplicates
			 * Note this is a stronger test than applied by xlsForm
			 */
            if(isValid && val !== 'the_geom') {

                valLower = val.toLowerCase();

                if(itemType === "question") {
                    for(i = 0; i < survey.forms.length; i++) {
                        form = survey.forms[i];
                        for(j = 0; j < form.questions.length; j++) {
                            questionType = form.questions[j].type;
                            if(questionType !== "end group") {
                                if(!(i === container && j === itemIndex)) {	// Don't test the question against itself!
                                    otherItem = form.questions[j];
                                    if(otherItem.name.toLowerCase() === valLower) {
                                        if((!otherItem.deleted && !otherItem.soft_deleted)
                                            || (otherItem.soft_deleted && otherItem.type !== questionType)) {
                                            hasDuplicate = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                } else if(itemType === "option") {
                    optionList = survey.optionLists[container];
                    for(j = 0; j < optionList.options.length; j++) {
                        if(j !== itemIndex) {
                            otherItem = optionList.options[j];
                            if(!otherItem.deleted && otherItem.value.toLowerCase() === valLower) {
                                hasDuplicate = true;
                                break;
                            }
                        }
                    }
                } else if(itemType === "optionlist") {
                    if(container === val) {
                        // no change its valid
                    } else {
                        optionList = survey.optionLists[val];
                        if(typeof optionList !== "undefined") {
                            hasDuplicate = true;
                        }
                    }
                }
                if(hasDuplicate) {
                    addValidationError(
                        container,
                        itemIndex,
                        "name",
                        "The " + itemDesc + " name is the same as the name of another " + itemDesc +".  Specify a unique name.",
                        itemType,
                        "error");
                    isValid = false;
                }

            }

            return isValid;

        }

		/*
		 * Get the path for the form from the top level form down
		 */
        function getFormPath(form) {
            var path = form.name,
                forms = globals.model.survey.forms,
                i;
            while(form.parentFormIndex !== -1) {
                for(i = 0; i < forms.length; i++) {
                    if(i === form.parentFormIndex) {
                        form = forms[i];
                        path = form.name + "/" + path;
                        break;
                    }
                }
            }
            path = "/" + path;
            return path;
        }

		/*
		 * Validate the entire survey
		 */
        function validateAll() {
            var i,
                j,
                forms = globals.model.survey.forms,
                optionLists = globals.model.survey.optionLists,
                list,
                numberErrors = 0,
                numberWarnings = 0;

            globals.errors = [];		// Clear the existing errors
            $('tr.editor_element.error', '#choiceView').removeClass("error");
            $('tr.editor_element.warning', '#choiceView').removeClass("warning");
            $('li.panel.error', '#formList').removeClass("error");
            $('li.panel.warning', '#formList').removeClass("warning");

            for(i = 0; i < forms.length; i++) {
                var deleted = false;
                var parentForm = forms[i].parentFormIndex;
                var parentQuestion = forms[i].parentQuestionIndex;
                if(parentForm > -1 && parentQuestion > -1) {
                    deleted = forms[parentForm].questions[parentQuestion].deleted;
                }
                if(!deleted) {
                    var validate = true;
                    var groupName;
                    for (j = 0; j < forms[i].questions.length; j++) {
                        // Skip deleted groups
                        if(validate) {
                            if (!forms[i].questions[j].deleted && !forms[i].questions[j].soft_deleted) {
                                validateItem(i, j, "question", false);		// Validate the question
                            } else {
                                if(forms[i].questions[j].type === "begin group") {
                                    groupName = forms[i].questions[j].name;
                                    validate = false;
                                }
                            }
                        } else {
                            if(forms[i].questions[j].type === "end group") {
                                if(forms[i].questions[j].name.indexOf(groupName) > 0) {
                                    validate = true;
                                }
                            }
                        }
                    }
                }
            }
            for(list in optionLists) {
                if(optionLists.hasOwnProperty(list)) {
                    for(j = 0; j < optionLists[list].options.length; j++) {
                        validateItem(list, j, "option", false);		// Validate the option
                    }
                }

                // Validate the option list itself
                validateItem(list, "ol_" + list, "optionlist", false);
            }

        }

        function numberIssues(severity) {
            var i,
                count = 0;

            for(i = 0; i < globals.errors.length; i++) {
                if(globals.errors[i].severity === severity) {
                    count++;
                }
            }
            return count;
        }

        function getSubFormIndex(parentFormIndex, parentQuestionIndex) {

            var survey = globals.model.survey,
                forms = survey.forms,
                i,
                form;

            for(i = 0; i < forms.length; i++) {
                form = forms[i];
                if(forms[i].parentFormIndex === parentFormIndex && forms[i].parentQuestionIndex === parentQuestionIndex) {
                    return i;
                }
            }

            return 0;
        }

        /*
         * Modify controls that are dependent on the view being either for questions or choices
         */
        function updateViewControls() {

            //if(globals.gSelProperty !== "media") {		// media is the only common attribute between question and option view
            //	globals.gSelProperty = "label";
            //}
            if(globals.gIsQuestionView && !globals.gShowingChoices) {
                $('.q_only').show();
                $('.o_only').hide();
                globals.gSelLabel = $('#selProperty > li.q_only.default').text();
            } else {
                $('.q_only').hide();
                $('.o_only').show();
                globals.gSelLabel = $('#selProperty > li.o_only.default').text();
            }
            globals.gSelProperty = "label";
            $('#propSelected').text(globals.gSelLabel);
        }

    });