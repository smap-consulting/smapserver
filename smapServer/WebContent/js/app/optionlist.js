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
 * Functions for manipulating an option list in the editor
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
		add: add
	};
	
	var gEditor;


	/*
	 * Add a new option list
	 */
	function add() {		
		
		var name = getDefaultListName();
		
		// Create changeset to be applied on save		
		change = {
				changeType: "optionlist",		// survey | form | language | question | option | optionlist (property | label) last two are types of property change
				action: "add",
				name: name
		};
		globals.gLatestOptionList = name;		// Remember name so focus can be set to new list
		
		$context = changeset.add(change);
		return $context;				// Add events on to the altered html
		
	}
	
	function getDefaultListName() {
		
		var name = "choices_" + globals.gMaxOptionList;
		
		
		// Keep incrementing the maxOptionList until we get a unique name for this list
		while(!listNameIsUnique(name)) {
			name = "choices_" + ++globals.gMaxOptionList;
		}
		
		return name;
	}
	
	function listNameIsUnique(name) {
		var unique = true,
			lists = globals.model.survey.optionLists;
		
		if(typeof name !== "undefined") {
			if(lists[name]) {
				unique = false;
			}
		}
		return unique;
	}
	

	

});
