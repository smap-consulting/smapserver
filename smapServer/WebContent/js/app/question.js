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
			splicePoint = 0,
			survey = globals.model.survey,
			type;
		
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
		splicePoint = getSplicePoint(qIndexOther, survey.forms[formIndex]);
		if(locn === "after") {
			++splicePoint;
		} 

		
		// Create changeset to be applied on save

		
		change = {
				changeType: "question",		// survey | form | language | question | option | (property | label) last two are types of property change
				action: "add",
				question: {
					seq: splicePoint,
					type: type,
					
					// Helper values 
					formIndex: formIndex,
					locn: locn,							// Whether the new question was added before or after the related question
					$relatedElement: $relatedElement	// Jquery element that is next to the new question
				}
		};
		
		$context = changeset.add(change);
		return $context;;				// Add events on to the altered html
		
	}
	
	function setType() {
		$('#typeModal').modal('show');
	}
	
	/*
	 * Get the index of the question into array that holds the question sequences
	 */
	function getSplicePoint(indexOther, form) {
		var i;
		
		for(i = 0; i < form.qSeq.length; i++) {
			if(form.qSeq[i] === indexOther) {
				return i;
			}
		}
		alert("Could not insert question");
		return 0;
	}
	

});
