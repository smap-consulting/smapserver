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
         'app/editorMarkup'], 
		function($, modernizr, lang, globals, markup) {

	return {	
		init: init,
		add: add,
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
	 * fIndex: The index of the form in the array of forms that are part of the survey model
	 * qIndex: The index of the question in the array of questions that make up a form 
	 */
	function add(qItem, locn) {		
		
		var $relatedElement = $("#question" + qItem),
			$relatedQuestionElement,
			fIndex,
			qIndexOther,
			splicePoint = 0,
			survey = globals.model.survey,
			newQuestion = {};
		
		if($relatedElement.size() > 0) {
			$relatedQuestionElement = $relatedElement.find('.question');
			fIndex = $relatedQuestionElement.data("fid");
			qIndexOther = $relatedQuestionElement.data("id");
		} else {
			// TODO First question in the form
			fIndex = 0;
		}
		
		console.log("Adding question: " + qItem + " : " + locn + " : " + fIndex + " : " + qIndexOther );
		console.log(survey.forms[fIndex].questions[qIndexOther])
		
		// 1. Choose type
		newQuestion.type = "dateTime";		// TODO
		
		// 3. Update internal modal so that refresh will not lose the changes
		splicePoint = qIndexOther;
		if(locn === "after") {
			++splicePoint;
		} 

		survey.forms[fIndex].questions.splice(splicePoint, 0, newQuestion);
		
		// 4. Add Markup
		if(locn === "after") {
			$relatedElement.after(markup.addOneQuestion(newQuestion, fIndex, splicePoint));
		} else {
			$relatedElement.before(markup.addOneQuestion(newQuestion, fIndex, splicePoint));
		}
		// TODO apply events
		
		// 5. Create changeset to be applied on save
		newQuestion.index = splicePoint;
		newQuestion.fIndex = fIndex;
		newQuestion.type = "question";
		
		var item = [];
		item.push({
			action: "new",
			element: element
		});
		
		globals.model.addElement(item);
	}
	
	function setType() {
		$('#typeModal').modal('show');
	}


});