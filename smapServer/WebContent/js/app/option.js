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
 * Functions for manipulating a choice in the editor
 * 
 *  Filter Model
 *    globals.gFilterArray:				Filters in display order
 *    survey.filters	Filters with state of shown or not shown
 *  
 *    Create choice view
 *    	initialise model
 *    	createChoiceView()
 *    	Set select controls
 *      getOptionTable()
 *      setupChoiceView()
 *      	Hide and show dom elements dependent on view
 *      Add response to events
 *      
 *    if filter type changes:
 *    	getOptionTable()
 *  	setupChoiceView()
 *      Add response to events
		
 *    if model changes:  (filter added or removed)
 *    	update model
 *    	update filter select dom elements
 *    	getOptionTable()
 *  	setupChoiceView()
 *      Add response to events
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
		refreshOptionListControls: refreshOptionListControls,
		createChoiceView: createChoiceView,
		setupChoiceView: setupChoiceView,						// On create of option view
		addOneOption: addOneOption,
		resetFilterColumns: resetFilterColumns,
		setPreviousChoices: setPreviousChoices,
		getOptionTable: getOptionTable,
		addFilter: addFilter,
		addFilterSelectList: addFilterSelectList,
		addOptionSequence: addOptionSequence
	};
	
	globals.gFilterArray = [];			// Filters in repeatable order
	
	/*
	 * Refresh the select controls that show the available option lists
	 */
	function refreshOptionListControls() {
		var $selector = $(".option-lists");
		$selector.html(getOptionLists());
	}
	
	/*
	 * Create a choice view
	 */
	function createChoiceView() {
		
		var $cv = $('#choiceView').find('.choice-content'),
			survey = globals.model.survey,
			question,
			filter,
			filterType = "none",
			i;
		
		if(globals.gListName) {
			$cv.empty().append(addOptionContainer(undefined, undefined, undefined, globals.gListName));
		} else {
			// Choice list opened from a question
			question = survey.forms[globals.gFormIndex].questions[globals.gItemIndex];
			$cv.empty().append(addOptionContainer(question, globals.gFormIndex, globals.gItemIndex, undefined));
		}
		addFilterSelectList(survey.filters);
		
		if(globals.gFilterArray.length > 0) {
			filterType = "custom";
		}
		for(i = 0; i < globals.gFilterArray.length; i++) {
			if (survey.filters[globals.gFilterArray[i]] === "_smap_cascade") {
				filterType = "cascade";
				break;
			}
		}

		// Set the custom filter view by default
		$('#filterType').val(filterType);
		
		return $cv;
	}
	
	function setupChoiceView(filterType) {
		var survey = globals.model.survey;
		/*
		 * show filter columns that should be visible
		 */
		for (i = 0; i < globals.gFilterArray.length; i++) {
			if(survey.filters[globals.gFilterArray[i]] === true) {
				$('table', '#choiceView').removeClass("hide" + i);
			}
		}
		
		if(filterType === "custom") {
			$('.custom_filter_only').show();
			$('.cascade_filter_only').hide();
			$('#choiceView table').addClass("notcascade").removeClass("notcustom");	
		} else if(filterType === "cascade") {
			$('.custom_filter_only').hide();
			$('.cascade_filter_only').show();
			$('#choiceView table').removeClass("notcascade").addClass("notcustom");
		} else {
			$('.custom_filter_only').hide();
			$('.cascade_filter_only').hide();
			$('#choiceView table').addClass("notcascade").addClass("notcustom");	
		}
		
		$('[type="checkbox"]', '#choiceView').iCheck({
		    checkboxClass: 'icheckbox_square-green',
		    radioClass: 'iradio_square-green'
		});
		
	}

	/*
	 * Add a single option
	 */
	function addOneOption(optionList, option, formIndex, index, list_name, qname) {
		
		var h = [],
			idx = -1,
			prevChoice = $("#previousSelectChoice").val(),
			filterType = $('#filterType').val();
		
		optionList.maxOption++;
		
		if(filterType !== "cascade" || showCascadeOption(option.cascade_filters, prevChoice,  option.value) ) {
			h[++idx] = '<tr class="editor_element option draggable dropon ';
			if(option.error) {
				h[++idx] = ' error';
			}
			
			// Add the option index 
			h[++idx] = '" data-id="';
			h[++idx] = index;
			h[++idx] = '" data-fid="';					
			h[++idx] = formIndex;
			h[++idx] = '" data-qname="';
			h[++idx] = qname;
			h[++idx] = '" data-list_name="';
			h[++idx] = list_name;
			h[++idx] = '" data-filters=';
			h[++idx] = JSON.stringify(option.cascade_filters);
			h[++idx] = '>';
			
				// Add select for cascade selects
				h[++idx] = '<td class="cascade">';
					h[++idx] = '<input type="checkbox" class="cascadeFilter" name="isSelected" value="';
					h[++idx] = "";
					h[++idx] = '" ';
					if(idx !== false) {
						h[++idx] = addCascadeChecked(option.cascade_filters, prevChoice);
					} 
				h[++idx] = '</td>';
				
				// Add option name and value cell
				h[++idx] = '<td>';
						h[++idx] = '<div class="btn-group">';
						h[++idx] = '<button type="button" class="btn btn-default dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false"><span class="edit_icon glyphicon glyphicon-option-vertical"></span> <span class="caret"></span>';
						h[++idx] = '</button>';
							h[++idx] = '<ul class="dropdown-menu">';
								h[++idx] = '<li><a href="#" class="delete_option">';
								h[++idx] = localise.set["c_del"];
								h[++idx] = '</a></li>';
								h[++idx] = '<li><a href="#" class="add_option_before">';
								h[++idx] = localise.set["ed_ab"];
								h[++idx] = '</a></li>';
								h[++idx] = '<li><a href="#" class="add_option_after">';
								h[++idx] = localise.set["ed_aa"];
								h[++idx] = '</a></li>';
							h[++idx] = '</ul>';
						h[++idx] = '</div>';
				h[++idx] = '</td>';	// End of menu button
				
				// Add option name cell
				h[++idx] = '<td>';
					
					h[++idx] = '<input class="oname form-control has_tt" value="';
					h[++idx] = option.value;
					h[++idx] = '" ';
							
					if(option.published) {				// Mark disabled if the option has been published
						h[++idx] = 'readonly="true"';
					}
					h[++idx] = ' type="text" title="';
					h[++idx] = localise.set["ed_cval"];
					h[++idx] = '">';
							
				h[++idx] = '</td>';	// End of option name and label cell
			
				h[++idx] = addFilterColumnBody(option.cascade_filters);
				h[++idx] = addOptionLabel(option);
		
			
			h[++idx] = '</tr>';
		}

	
		return h.join("");
	}
	
	function resetFilterColumns() {
		var i,
			survey = globals.model.survey;
		
		for (i = 0; i < globals.gFilterArray.length; i++) {
			if(survey.filters[globals.gFilterArray[i]] === true) {
				$('table', '#choiceView').removeClass("hide" + i);
			} else {
				$('table', '#choiceView').addClass("hide" + i);
			}
			
		}
	}
	
	/*
	 * Add an option container
	 */
	function addOptionContainer(question, formIndex, qIndex, listName) {
		var h = [],
			idx = -1,
			filter;
		
		h[++idx] = '<div class="question_head" data-fId="';
		h[++idx] = formIndex;
		h[++idx] = '" data-id="';
		h[++idx] = qIndex;
		h[++idx] = '">';
		h[++idx] = '<div class="question-controls">';
			h[++idx] = '<div class="row">';
				h[++idx] = '<div class="col-md-6">';
					h[++idx] = '<form role="form" class="form-horizontal">';
						
						// A control to set option list name
						if(!listName) {
							h[++idx] = '<div class="form-group">';
								h[++idx] = '<label class="col-sm-2">';
									h[++idx] = localise.set["ed_cl"];
								h[++idx] = '</label>';
								h[++idx] = '<div class="col-sm-10">';
									h[++idx] = '<select class="form-control option-lists">';
									h[++idx] = getOptionLists();
									h[++idx] = '</select>';
								h[++idx] = '</div>';
							h[++idx] = '</div>';
						}
						
						// A control to select the filter type
						h[++idx] = '<div class="form-group">';
							h[++idx] = '<label class="col-sm-2">';
								h[++idx] = localise.set["c_filter"];
							h[++idx] = '</label>';
							h[++idx] = '<div class="col-sm-10">';
								h[++idx] = '<select class="form-control" id="filterType">';
								
								h[++idx] = '<option value="none">';
								h[++idx] = localise.set["c_none"];
								h[++idx] = '</option>';
								
								h[++idx] = '<option value="cascade">';
								h[++idx] = localise.set["ed_cs"];
								h[++idx] = '</option>';
								
								h[++idx] = '<option value="custom">';
								h[++idx] = localise.set["ed_cf"];
								h[++idx] = '</option>';
								
								h[++idx] = '</select>';
							h[++idx] = '</div>';
						h[++idx] = '</div>';
						
					h[++idx] = '</form>';
				h[++idx] = '</div>';
				h[++idx] = '<div class="col-md-6">';
					h[++idx] = '<button class="btn btn-primary pull-right exitOptions">';
						h[++idx] = localise.set["c_back"];
					h[++idx] = '</button>';
					h[++idx] = '<div class="custom_filter_only" style="display:none;">';
						h[++idx] = '<form role="form" class="form-horizontal">';
						
							if(!listName) {
								h[++idx] = '<div class="form-group">';
									h[++idx] = '<label class="col-sm-2">';
									h[++idx] = localise.set["ed_choice_f"];
									h[++idx] = '</label>';
									h[++idx] = '<div class="col-sm-8">';
										h[++idx] = '<textarea id="choiceFilter" class="input-block" form-control has_tt" type="text">';
										h[++idx] = question.choice_filter;
										h[++idx] = '</textarea>';
										h[++idx] = addChoiceFilterError(question.errorMsg);
									h[++idx] = '</div>';
								h[++idx] = '</div>';
							}
							
							h[++idx] = '<div id="custom_filters">';
							h[++idx] = '</div>';
						h[++idx] = '</form>';
						h[++idx] = '<button id="addFilter" class="btn btn-default">';
						h[++idx] = localise.set["ed_afc"];
						h[++idx] = '</button>';
					h[++idx] = '</div>';  // Custom filter only
					
					h[++idx] = '<div class="cascade_filter_only" style="display:none;">';
						h[++idx] = '<form role="form" class="form-horizontal">';

							h[++idx] = '<div class="form-group">';
								h[++idx] = '<label class="col-sm-2">';
									h[++idx] = localise.set["ed_cspq"];
								h[++idx] = '</label>';
								h[++idx] = '<div class="col-sm-10">';
									h[++idx] = '<select class="form-control" id="previousSelect">';
										h[++idx] = addSelectQuestions(question);
									h[++idx] = '</select>';
								h[++idx] = '</div>';
							h[++idx] = '</div>';
			
							h[++idx] = '<div class="form-group">';
								h[++idx] = '<label class="col-sm-2">';
									h[++idx] = localise.set["ed_csp"];
								h[++idx] = '</label>';
								h[++idx] = '<div class="col-sm-10">';
									h[++idx] = '<select class="form-control" id="previousSelectChoice">';	
									h[++idx] = '</select>';
								h[++idx] = '</div>';
							h[++idx] = '</div>';
						h[++idx] = '</form>';
					h[++idx] = '</div>';	// Cascade filter only
				h[++idx] = '</div>';
			h[++idx] = '</div>';
		h[++idx] = '</div>';
	
		h[++idx] = '<div id="optionTable">';	
		h[++idx] = '</div>';	// Option Table
		h[++idx] = '</div>';		// Question Head
		
		return h.join("");
	}

	/*
	 * Refresh the table of options
	 */
	function getOptionTable(question, formIndex, listName) {
		var h = [],
			idx = -1;
		
		if(listName) {
			h[++idx] = addOptions(undefined, undefined, listName);
			$('#choiceViewQuestion').html(localise.set["ed_cl"] + ": " + listName);
		} else {
			// Opened from a specific question
			h[++idx] = addOptions(question, formIndex, undefined);
			$('#choiceViewQuestion').html(localise.set["c_question"] + ": " + question.name);
		}
		
		return h.join("");
	}
	
	/*
	 * Add filter to filters if not already there
	 */
	function addFilter(filter) {
		var i,
			hasFilter = false,
			survey = globals.model.survey;
		
		for(i = 0; i < globals.gFilterArray.length; i++) {
			if(globals.gFilterArray[i] === filter) {
				hasFilter = true;
			}
		}
		if(!hasFilter) {
			globals.gFilterArray.push(filter);
		}
		
		if(!survey.filters) {
			survey.filters = {};
		}
		survey.filters[filter] = true;
	}
	
	/*
	 * Add the filters as  a select list
	 */
	function addFilterSelectList(filters) {
		var h = [],
			idx = -1,
			survey = globals.model.survey;
		
		globals.gFilterArray = [];
		for(filter in filters) {
			if (filters.hasOwnProperty(filter)) {
				globals.gFilterArray.push(filter);	// Save filters as ordered array
				h[++idx] = '<div class="checkbox">';
			    h[++idx] = '<label>';
			      h[++idx] = '<input type="checkbox" ';
			      if(survey.filters[filter]) {
			    	  h[++idx] = 'checked=true ';
			      }
			      h[++idx] = 'value="';
			      h[++idx] = filter;
			      h[++idx] = '"> ';
			      h[++idx] = filter
			    h[++idx] = '</label>';
			    h[++idx] = '</div>';
			}
		}
		
		$('#custom_filters').html(h.join(""));
		
		// Style checkboxes
		$('[type="checkbox"]', '#custom_filters').iCheck({
		    checkboxClass: 'icheckbox_square-green',
		    radioClass: 'iradio_square-green'
		});
	}
	
	/*
	 * Show the options
	 */
	function addOptions(question, formIndex, list_name) {
		var survey = globals.model.survey,
			optionList,
			questionName,
			oSeq,
			maxIndex,
			h = [],
			idx = -1,
			i,
			initialiseFilters = false;
		
		if(!list_name) {		// Options attached to a question
			list_name = question.list_name;
			questionName = question.name;
		} 
		optionList = survey.optionLists[list_name];
		
		if(typeof optionList !== "undefined") {
			optionList.maxOption = 0;
			
			addOptionSequence(optionList);		// Add an array holding the option sequence if it does not already exist
			oSeq = optionList.oSeq;
			
			h[++idx] = '<div class="table-responsive">';
			h[++idx] = '<table class="table notcustom hide0 hide1 hide2 hide3 hide4">';
			
			h[++idx] = '<thead class="thead-default"><tr>';
			
				h[++idx] = '<th class="cascade">';
				h[++idx] = '</th>';
			
				h[++idx] = '<th>';
				h[++idx] = '</th>';
				
				h[++idx] = '<th>';
				h[++idx] = localise.set["ed_cval"];
				h[++idx] = '</th>';
				
				h[++idx] = addFilterColumnHeadings();
				
				h[++idx] = '<th>';
				h[++idx] = localise.set["ed_clab"];
				h[++idx] = '</th>';
				
			h[++idx] = '</tr></thead>';
			h[++idx] = '<tbody>';
			if(oSeq) {
				maxIndex = 0;
				for(i = 0; i < oSeq.length; i++) {
					h[++idx] = addOneOption(optionList,
							optionList.options[oSeq[i]], 
							formIndex, 
							oSeq[i], 
							list_name, 
							questionName);
					if(oSeq[i] >= maxIndex) {
						maxIndex = oSeq[i] + 1;
					}
				}
			}
			h[++idx] = '</tbody>';
			h[++idx] = '</table>';
			h[++idx]= '</div>';
			h[++idx] = addNewOptionButton(true, list_name, formIndex, questionName, -1); 
		}
		return h.join("");
	}
	
	/*
	 * Add the array containing the option sequence
	 */
	function addOptionSequence(optionList) {
		var i;
		
		if(!optionList.oSeq) {
			optionList.oSeq = [];
			for(i = 0; i < optionList.options.length; i++) {
				optionList.oSeq[i] = i;
			}
		}
	}
	
	
	/*
	 * One of the questions properties will be featured so that it can be edited in the header without expanding the question
	 */
	function addOptionLabel(option) {
		
		var h = [],
			idx = -1,
			selProperty = globals.gSelProperty;
		
		h[++idx] = '<td class="option">';
			if(selProperty === "media") {
				h[++idx] = markup.addMedia("Image", 
						option.labels[globals.gLanguage].image, 
						option.labels[globals.gLanguage].imageUrl, 
						option.labels[globals.gLanguage].imageThumb);
		        
				h[++idx] = markup.addMedia("Video", 
						option.labels[globals.gLanguage].video, 
						option.labels[globals.gLanguage].videoUrl, 
						option.labels[globals.gLanguage].videoThumb);
				
				h[++idx] = markup.addMedia("Audio", 
						option.labels[globals.gLanguage].audio, 
						option.labels[globals.gLanguage].audioUrl, 
						option.labels[globals.gLanguage].audioThumb);	
			} else {
				h[++idx] = '<textarea class="labelProp has_tt" title="';
				h[++idx] = localise.set["ed_clab"];
				h[++idx] = '">';
				h[++idx] = option.labels[globals.gLanguage].text;
				h[++idx] = '</textarea>';
				h[++idx] = addOptionErrorMsg(option.errorMsg);
			}
		
		h[++idx] = '</td>';
		return h.join("");
	}
	
	function addNewOptionButton(after, list_name, formIndex, qname, index) {
		var h = [],
			idx = -1,
			addButtonClass,
			locn;
		
		addButtonClass = after ? 'add_after_button add_button' : 'add_before_button add_button';
		locn = after ? 'after' : 'before';
		
		h[++idx] = '<button type="button" class="add_option btn btn-primary dropon option editor_element add_after_button"';
		if(typeof index !== "undefined") {
			h[++idx] = ' data-id="';
			h[++idx] = index;
			h[++idx] = '"';
		}
		if(typeof list_name !== "undefined") {
			h[++idx] = ' data-list_name="';
			h[++idx] = list_name;
			h[++idx] = '"';
		}
		if(typeof qname !== "undefined") {
			h[++idx] = ' data-qname="';
			h[++idx] = qname;
			h[++idx] = '"';
		}
		if(typeof formIndex !== "undefined") {
			h[++idx] = ' data-fid="';
			h[++idx] = formIndex;
			h[++idx] = '"';
		}
		h[++idx] = '>';
		h[++idx] = localise.set["ed_anc"];
		h[++idx] = ' </button>';

		
		return h.join('');
	}
	
	/*
	 * Get an array of option list names sorted alphabetically
	 */
	function getOptionLists() {
		
		var lists = survey = globals.model.survey.optionLists,
			name,
			nameArray = [],
			h = [],
			idx = -1,
			i;

		// get the names into an array so they can be sorted
		for (name in lists) {
			if (lists.hasOwnProperty(name)) {
			    nameArray.push(name);
			}
		}
		// Sort array of list names
		nameArray.sort();
		
		// Create html
		for(i = 0; i < nameArray.length; i++) {
			h[++idx] = '<option value ="';
			h[++idx] = nameArray[i];
			h[++idx] = '">';
			h[++idx] = nameArray[i];
			h[++idx] = '</option>';
		}
		return h.join("");
	}
	
	/*
	 * Add the table headings for filter columns
	 */
	function addFilterColumnHeadings() {
		
		var i,
			h = [],
			idx = -1;
		
		for(i = 0; i < globals.gFilterArray.length; i++) {
			h[++idx] = '<th class="f';		// Class to hide / show filter
			h[++idx] = i;
			h[++idx] = '">';
			h[++idx] = globals.gFilterArray[i];
			h[++idx] = '</th>';
		}
		
		return h.join("");
	}
	
	/*
	 * Add the table body for filter columns
	 */
	function addFilterColumnBody(filters) {
		
		var i,
			h = [],
			idx = -1;
		
		for(i = 0; i < globals.gFilterArray.length; i++) {
			h[++idx] = '<td class="f';		// Class to hide / show filter
			h[++idx] = i;
			h[++idx] = '" data-f_name="';
			h[++idx] = globals.gFilterArray[i];
			h[++idx] = '">';
			h[++idx] = '<input class="filter form-control has_tt" value="';
			h[++idx] = filters[globals.gFilterArray[i]];
			h[++idx] = '" ';	
			h[++idx] = ' type="text" title="';
			h[++idx] = localise.set["ed_fv"];
			h[++idx] = '">';
			
			h[++idx] = '</td>';
			
		}
		
		return h.join("");
	}
	
	/*
	 * Add a media type
	 */
	function addOptionMedia(label, mediaIdent, url, thumbUrl) {
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
	
	function addOptionErrorMsg(msg) {
		var h = [],
			idx = -1;
		
		h[++idx] = '<div class="error-msg pull-right">';
		h[++idx] = msg;
		h[++idx] = '</div>';
		return h.join("");
	}
	
	function addChoiceFilterError(msg) {
		var h = [],
			idx = -1;
		
		h[++idx] = '<div class="error-msg choice_filter_error pull-right">';
		h[++idx] = msg;
		h[++idx] = '</div>';
		return h.join("");
	}
	
	/*
	 * Add a list of select questions that can be selected as the preceeding question for a cascade select
	 */
	function addSelectQuestions(choiceQuestion) {
		var survey = globals.model.survey,
			h = [],
			idx = -1,
			i, j,
			form,
			question,
			first = true;
			
		for(i = 0; i < survey.forms.length; i++) {
			form = survey.forms[i];
			for(j = 0; j < form.qSeq.length; j++) {
				question = form.questions[form.qSeq[j]];
				if(!question.soft_deleted && question.type === "select1") {
					if(choiceQuestion && choiceQuestion.name === question.name) {
						continue;		// Skip question being edited
					}
					h[++idx] = '<option ';
					if(first) {
						h[++idx] = 'selected ';
					}
					h[++idx] = 'value="';
					h[++idx] = question.list_name;
					h[++idx] = '">';
					h[++idx] = question.name;
					h[++idx] = '</option>';
				}
			}
		}
	
		return h.join("");
	}
	
	/*
	 * Set the choices that can be selected as the preceeding choice for a cascade select
	 */
	function setPreviousChoices(listname) {
		
		var survey = globals.model.survey,
			h = [],
			idx = -1,
			i,
			option,
			optionList = survey.optionLists[listname],
			oSeq;
			
		addOptionSequence(optionList);		// Add an array holding the option sequence if it does not already exist
		oSeq = optionList.oSeq;
		
		for(i = 0; i < oSeq.length; i++) {

			option = optionList.options[oSeq[i]];
				
			h[++idx] = '<option ';
				if(i == 0) {
					h[++idx] = 'selected ';
				}
				h[++idx] = 'value="';
				h[++idx] = option.value;
				h[++idx] = '">';
				h[++idx] = option.value;
			h[++idx] = '</option>';
		}
		$("#previousSelectChoice").html(h.join(""));
	}
	
	/*
	 * Set the checkbox checked if the cascade filter matches the previously selected choice
	 */
	function addCascadeChecked (filters, prevChoice) {
		
		var match = false;
		
		if(filters) {
			if(filters["_smap_cascade"] && filters["_smap_cascade"] == prevChoice) {
				match = true;
			}
		}
		
		if(match) {
			return 'checked="checked"';
		} else {
			return '';
		}
	}
	
	/*
	 * As this is the cascade view options should only be shown if their filter value is empty or 
	 *  matches the prevChoice
	 */
	function showCascadeOption (filters, prevChoice, optionValue) {
		
		var show = false,
			i,
			inOptionList = false,
			listname = $('#previousSelect').val(),
			survey = globals.model.survey,
			optionList = survey.optionLists[listname],
			oSeq,
			option;
		
		if(listname) {
			addOptionSequence(optionList);		// Add an array holding the option sequence if it does not already exist
			oSeq = optionList.oSeq
			for(i = 0; i < oSeq.length; i++) {
				option = optionList.options[oSeq[i]];
				if(filters["_smap_cascade"] == option.value) {
					inOptionList = true;
					break;
				}
			}
						
			if(!filters["_smap_cascade"] || filters["_smap_cascade"] == prevChoice || !inOptionList) {
				show = true;
			}
		} else {
			show = true;		// No previous list set
		}
		
		return show;
	}
	
});
