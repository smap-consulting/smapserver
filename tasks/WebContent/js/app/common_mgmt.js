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


window.gTasks = {
		cache: {
			surveyConfig: {},
			managedData: {},
			surveyList: {}
		},
		gSelectedRecord: undefined,
		gSelectedSurveyIndex: undefined,
		gUpdate: [],
		gCurrentIndex: undefined,
		gPriKey: undefined,
		gSort: undefined,
		gDirn: undefined
	}

	 /*
	  * Function called when the current survey is changed
	  */
	 function surveyChanged() {
			
			if(globals.gCurrentSurvey > 0) {
				// getManagedData(globals.gCurrentSurvey);
				 saveCurrentProject(-1, globals.gCurrentSurvey);
				 if(isManagedForms) {
					 getSurveyConfig(globals.gCurrentSurvey, gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].managed_id);
				 } else {
					 getSurveyConfig(globals.gCurrentSurvey, 0);
				 }
			 } else {
				 // No managed surveys in this project
				 $('#trackingTable').empty();
			 }
	 }
	 
	 /*
	  * Function called when the current project is changed
	  */
	 function projectChanged() {

	 	globals.gCurrentProject = $('#project_name option:selected').val();
	 	globals.gCurrentSurvey = -1;
	 	saveCurrentProject(globals.gCurrentProject, globals.gCurrentSurvey);	// Save the current project id
	 	refreshData();
	 	
	 }
	 
	 /*
	  * Get the markup to edit the record
	  */
	 function getEditMarkup(configItem, itemIndex, first, record) {
		 
		 var h = [],
		 	idx = -1;
		 	
		// Add form group and label
		h[++idx] = '<div class="form-group"><label class="col-lg-4 control-label">';
		h[++idx] = configItem.humanName;
		h[++idx] = '</label>';
		
		// Add Data
		h[++idx] = ' <div class="col-lg-8">';
		if(configItem.readonly) {		// Read only text
			h[++idx] = '<input type="text" disabled="" class="form-control" value="';
			h[++idx] = record[configItem.humanName];
			h[++idx] = '">';
		} else {
			h[++idx] = addEditableColumnMarkup(configItem, record[configItem.humanName], itemIndex, first);
			first = false;
		}
		h[++idx] = '</div>';
		
		// Close form group
		h[++idx] = '</div>';
		
		return h.join('');
	 }
	 
	 /*
	  * Refresh the data used in this page
	  */
	 function refreshData() {
		 
		 // Clear gTasks.cache
		 gTasks.cache.surveyConfig = {};
		 gTasks.cache.managedData = {};
		 gTasks.cache.surveyList = {};
		 
		 // Get the list of available surveys
		 loadManagedSurveys(globals.gCurrentProject, surveyChanged);
		 
	 }
	 
	 /*
	  * User has changed a managed value
	  */
	 function dataChanged($this) {
		
		 var 
			itemIndex = $this.data("item"),
			value = $this.val(),
			record = gTasks.gSelectedRecord,
			columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns,
			currentValue,
			name = columns[itemIndex].name,
			i,
			foundExistingUpdate;
		
		currentValue = record[columns[itemIndex].humanName];
		if(typeof currentValue === "undefined") {
			currentValue = "";
		}
		
		if(currentValue !== value) {
			// Add new value to array, or update existing
			foundExistingUpdate = false;
			for(i = 0; i < gTasks.gUpdate.length; i++) {
				if(gTasks.gUpdate[i].name === name) {
					foundExistingUpdate = true;
					gTasks.gUpdate[i].value = value;
					break;
				}
			}
			
			if(!foundExistingUpdate) {
				// Add new value
				gTasks.gUpdate.push({
					name: name,
					value: value,
					currentValue: currentValue,
					prikey: gTasks.gPriKey
				});
			}
			
		} else {
			// Delete value from array of updates
			for(i = 0; i < gTasks.gUpdate.length; i++) {
				if(gTasks.gUpdate[i].name === name) {
					gTasks.gUpdate.splice(i, 1);
					break;
				}
			}
		}
		console.log("  changed: " + itemIndex + " " + value + " " + currentValue);
		
		if(gTasks.gUpdate.length > 0) {
			$('#saveRecord').prop("disabled", false);
		} else {
			$('#saveRecord').prop("disabled", true);
		}
	 }
	 
	 /*
	  * Show the survey data along with the management columns
	  * If prikey is specified then only show that record
	  */
	 function showManagedData(sId, tableElem, masterRecord) {
		 
		 var x = 1,
		 	columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns,
		 	shownColumns = [],
		 	hiddenColumns = [],
		 	visibleColumns = [],
		 	h = [],
		 	idx = -1,
		 	hfoot = [],
		 	foot_idx = -1,
		 	i,j,
		 	colIdx = 0,
		 	$table = $(tableElem),
		 	doneFirst = false,
		 	headItem,
		 	hColSort = [],
		 	hColSortIdx = -1;
		 
		 $('#survey_title').html($('#survey_name option:selected').text());
		 
		 
		 if(globals.gMainTable) {
			 globals.gMainTable.destroy();
		 }
		 
		 // Add head
		 h[++idx] = '<thead>';
		 h[++idx] = '<tr>';
		 //if(typeof masterRecord === "undefined") {
		//	 h[++idx] = '<th></th>';				// Select
		 //}
		 for(i = 0; i < columns.length; i++) {
			 headItem = columns[i];
			 
			 hColSort[hColSortIdx++] = addToColumnSort(headItem);
			 
			 shownColumns.push({
				 "data": headItem.humanName
			 });
			 h[++idx] = '<th>';
			 h[++idx] = '<span class="ch">';
			 h[++idx] = headItem.humanName;
			 h[++idx] = '</span>';
			 h[++idx] = '</th>';
			 hfoot[++foot_idx] = '<th></th>';
			 headItem.colIdx = colIdx;
			 colIdx++;
			 
			 if(headItem.hide) {
				 hiddenColumns.push(i);
			 } else {
				 visibleColumns.push(i);
			 }
		 }
		 h[++idx] = '</tr>';
		 h[++idx] = '</thead>';
		 h[++idx] = '<tfoot>';
		 h[++idx] = '<tr>';
		 h[++idx] = hfoot.join('');
		 h[++idx] = '</tr>';
		 h[++idx] = '</tfoot>';
	
		 $table.empty().html(h.join(''));

		 /*
		  * Apply data tables
		  */
		 var url = '/api/v1/data/' + sId;
		 if(isManagedForms) {
			 url += "?mgmt=true";
		 } else{
			 url += "?mgmt=false";
		 }
		 url += "&format=dt";
		 
		 // Add anchors
		 globals.gMainTable = $table.DataTable({
			 processing: true,
			 select: true,
		     ajax: url,
		     columns: shownColumns,
		     order: [[ 0, "desc" ]],
		     initComplete: function(settings, json) {
		    	 console.log("initComplete");
				 columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns;
				 globals.gMainTable.columns().flatten().each( function ( colIdx ) {
					 if(columns[colIdx].filter) {
						 var select = $('<select class="form-control"/>')
						 		.appendTo(
						 				globals.gMainTable.column(colIdx).footer()
			    		        )
			    		        .on( 'change', function () {
			    		        	var val =  $(this).val();
			    		        	if(val == '') {
			    		        		globals.gMainTable
			    		                	.column( colIdx )
			    		                	.search( val )
			    		                .draw();
			    		        	} else {
				    		        	globals.gMainTable
				    		                .column( colIdx )
				    		                .search( "^" + $(this).val() + "$", true, false, false )
				    		                .draw();
			    		        	}
			    		        	saveFilter(colIdx, val);
			    		        } );
			    		
			    		    select.append( $('<option value=""></option>') );
			    		    
			    		    globals.gMainTable
						        .column( colIdx )
						        .cache('search')
						        .sort()
						        .unique()
						        .each( function ( d ) {
						            select.append( $('<option value="'+d+'">'+d+'</option>') );
						        } );
			    		    
			    		    // Set current value
			    		    if(columns[colIdx].filterValue) {
			    		    	select.val(columns[colIdx].filterValue).trigger('change');
			    		    }
		    			}
			    		
		    		});
		    	  },
		     columnDefs: [ {
		    	 	targets: "_all",
		    	 	render: function ( data, type, full, meta ) {
		    	 		return addAnchors(data);
		    	 	}	 	
		     	},
		     	{
		     		visible: false,  
		     		"targets": hiddenColumns 
		     	},
		     	{
		     		visible: true,  
		     		"targets": visibleColumns 
		     	},
		     ],
		     language: {
		            url: localise.dt()
		        }
		 });
		 
		 // Respond to selection of a row
		 globals.gMainTable
	        .off('select').on( 'select', function ( e, dt, type, indexes ) {
	            var rowData = globals.gMainTable.rows( indexes ).data().toArray();
	            if(isManagedForms) {
	            	gTasks.gSelectedRecord = rowData[0];
	            	$('#editRecord').modal("show");
	            }
	            //alert(JSON.stringify( rowData ));
	        } );
		 
		 // Highlight data conditionally
		 globals.gMainTable.off('draw').on( 'draw', function () {
			 
			 columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns;
	 
			 for(i = 0; i < columns.length; i++) {
				 headItem = columns[i];
				 if(headItem.markup) {
					 $( globals.gMainTable.column( headItem.colIdx ).nodes() ).each(function(index) {
						 var $this = $(this),
						 	v = $this.text();
						 
					 	for(j = 0; j < headItem.markup.length; j++) {
						 	if(headItem.markup[j].value == v) {
						 		 $this.addClass( headItem.markup[j].classes );
						 	}
					 	}
						
					 });
				 }
			 }
		 });
		 
		 // Add filters
		 /*
		 globals.gMainTable.on( 'init.dt', function () {
			 console.log("init.dt");
			 columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns;
			 globals.gMainTable.columns().flatten().each( function ( colIdx ) {
				 if(columns[colIdx].filter) {
					 var select = $('<select class="form-control"/>')
					 		.appendTo(
					 				globals.gMainTable.column(colIdx).footer()
		    		        )
		    		        .on( 'change', function () {
		    		        	globals.gMainTable
		    		                .column( colIdx )
		    		                .search( $(this).val() )
		    		                .draw();
		    		        	saveFilter(colIdx, $(this).val());
		    		        } );
		    		
		    		    select.append( $('<option value=""></option>') );
		    		    
		    		    globals.gMainTable
					        .column( colIdx )
					        .cache('search')
					        .sort()
					        .unique()
					        .each( function ( d ) {
					            select.append( $('<option value="'+d+'">'+d+'</option>') );
					        } );
		    		    
		    		    // Set current value
		    		    if(columns[colIdx].filterValue) {
		    		    	select.val(columns[colIdx].filterValue).trigger('change');
		    		    }
	    			}
		    		
	    		});
		    	
		    } );
		    */
		 
		 /*
		  * Settings
		  */
		 $('#tab-settings-content').html(hColSort.join(''));
		 
		 // Set checkboxes in column sort section of settings
		 $('input', '#tab-settings-content').iCheck({
			 checkboxClass: 'icheckbox_square-green',
			 radioClass: 'iradio_square-green'
		 });
		 

	 }
	 
	/*
	 * Add the column to the settings
	 */
	function addToColumnSort(item) {
		var h = [],
			idx = -1;
		
		if(item.include) {
			h[++idx] = '<div class="setings-item">';
			h[++idx] = '<span>';
				h[++idx] = item.humanName;
			h[++idx] = '</span>';
			
			h[++idx] = '<div class="switch">';
			h[++idx] = '<input type="checkbox" name="columnSelect"';
			h[++idx] = ' class="columnSelect" value="';
			h[++idx] = item.name;
			h[++idx] = '"';
			if(!item.hide) {
				h[++idx] = ' checked';
			}
			h[++idx] = '>';
			h[++idx] = '</div>';
			h[++idx] = '</div>';
		}
		return h.join('');
	}
	 
	/*
	 * Add the markup for an editable column
	 */
	function addEditableColumnMarkup(column, value, itemIndex, first) {
		var h = [],
			idx = -1,
			i;
		
		if(column.type === "text") {
			h[++idx] = ' <input type="text" class="form-control editable" value="';
			h[++idx] = value;
			h[++idx] = '" data-item="';
			h[++idx] = itemIndex;
			if(first) {
				h[++idx] = '" autofocus/>';
			} else {
				h[++idx] = '"/>';
			}
		} else if(column.type === "date") {
			 h[++idx] = '<div class="input-group date" data-container="body">';
             h[++idx] = '<input type="text" class="form-control editable" data-date-format="YYYY-MM-DD" value="';
             h[++idx] = value;
             h[++idx] = '" data-item="';
             h[++idx] = itemIndex;
             if(first) {
 				h[++idx] = '" autofocus/>';
 			} else {
 				h[++idx] = '"/>';
 			}
             h[++idx] = '<span class="input-group-addon"><span class="glyphicon glyphicon-calendar"></span></span>';
             h[++idx] = '</div>';
		} else if(column.type === "select_one") {
			h[++idx] = ' <select class="form-control editable" ';
			h[++idx] = '" data-item="';
			h[++idx] = itemIndex;
			h[++idx] = '">';
			if(column.choices) {
				for(i = 0; i < column.choices.length; i++) {
					h[++idx] = '<option';
					if(column.choices[i] === value) {
						h[++idx] =' selected="selected"'
					}
					h[++idx] = ' value="';
					h[++idx] = column.choices[i];
					h[++idx] = '">'
					h[++idx] = column.choices[i];
					h[++idx] = '</option>';
				}
			}
			h[++idx] = '</select>';
			
		} else {
			h[++idx] = value;
		}
		
		return h.join('');
	} 
	
	/*
	 * Get surveys and update the survey lists on this page
	 *  This is a different function from the common loadSurveys function as processing differs depending on whether there is tracking
	 *   applied to the survey
	 */	
	 function loadManagedSurveys(projectId, callback) {
	 	
	 	var url="/surveyKPI/surveys?projectId=" + projectId + "&blocked=true",
	 		$elemSurveys = $('#survey_name');

	 	
	 	if(typeof projectId !== "undefined" && projectId != -1 && projectId != 0) {
	 		
	 		addHourglass();

	 		$.ajax({
	 			url: url,
	 			dataType: 'json',
	 			cache: false,
	 			success: function(data) {
	 				
	 				var i,
	 					item,
	 					h = [],
	 					idx = -1,
	 					firstSurvey = true,
	 					firstSurveyId = undefined,
	 					firstSurveyIndex = undefined;
	 				
	 				removeHourglass();
	 				
	 				gTasks.cache.surveyList[globals.gCurrentProject] = data;
	 				gTasks.gSelectedSurveyIndex = undefined;
	 				
	 				for(i = 0; i < data.length; i++) {
	 					item = data[i];
	 					if(item.managed_id > 0 || isBrowseResults) {
	 						h[++idx] = '<option value="';
	 						h[++idx] = i;
	 						h[++idx] = '">';
	 						h[++idx] = item.displayName;
	 						h[++idx] = '</option>';
	 					
	 						if(firstSurvey) {
	 							firstSurveyId = item.id;
	 							firstSurveyIndex = i;
	 							firstSurvey = false;
	 						}
	 						
	 						if(item.id === globals.gCurrentSurvey) {
		 						gTasks.gSelectedSurveyIndex = i;
		 					}
	 						
	 					}
	 					
	 				}
	 				
	 				$elemSurveys.empty().html(h.join(''));
	 				
	 				if(!gTasks.gSelectedSurveyIndex && firstSurveyId) {
 		 				globals.gCurrentSurvey = firstSurveyId;
 		 				gTasks.gSelectedSurveyIndex = firstSurveyIndex;
 		 			} else if(gTasks.gSelectedSurveyIndex && firstSurveyId) {
 		 				$elemSurveys.val(gTasks.gSelectedSurveyIndex);
 		 			}
	 				
	 				if(typeof callback == "function") {
	 					callback();
	 				}
	 			},
	 			error: function(xhr, textStatus, err) {
	 				
	 				removeHourglass();
	 				if(xhr.readyState == 0 || xhr.status == 0) {
	 		              return;  // Not an error
	 				} else {
	 					console.log("Error: Failed to get list of surveys: " + err);
	 				}
	 			}
	 		});	
	 	} 
	 }
	
	 /*
	  * Get the columns for a survey
	  */
	 function getSurveyConfig(sId, managed_id) {
		 
		 if(!gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex]) {
			 var url = '/surveyKPI/managed/config/' + sId + "/" + managed_id;
			 
			 addHourglass();
			 $.ajax({
				 url: url,
				 cache: false,
				 dataType: 'json',
				 success: function(data) {
					 removeHourglass();
					 gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex] = data;
					 showManagedData(sId, '#trackingTable', undefined);
				 },
				 error: function(xhr, textStatus, err) {
					 removeHourglass();
					 if(xhr.readyState == 0 || xhr.status == 0) {
						 return;  // Not an error
					 } else {
							alert("Error failed to get column names from survey: " + sId + " " + xhr.responseText);
					 }
				 }
			 });
		 } else {
			 showManagedData(sId, '#trackingTable', undefined);
		 }
	 }
	 
	 /*
	  * Get the currently selcted recoord
	  */
	 function getSelectedRecord() {
	 	
	 	var record,
	 		idx;
	 	
	 	$('input[type=radio]:checked', '#trackingTable').each(function() {
	 		idx = $(this).val();
	 	});
	 	
	 	return idx;
	 }
	 
	 /*
	  * Get data related to the currently selected record
	  */
	 function getRelatedList(sId, masterRecord) {
		 record = gTasks.gSelectedRecord;
		 
		 var url = '/surveyKPI/managed/connected/' + sId + '/0/' + record.prikey;
		 
		 $('#relatedData').empty();
		 addHourglass();
		 $.ajax({
			 url: url,
			 cache: false,
			 dataType: 'json',
			 success: function(data) {
				 removeHourglass();
				 
				 var i;
				 
				 for(i = 0; i < data.length; i++) {
					 showRelated(i, data[i]);
				 }
				 
				 
			 },
			 error: function(xhr, textStatus, err) {
				 removeHourglass();
				 if(xhr.readyState == 0 || xhr.status == 0) {
					 return;  // Not an error
				 } else {
						alert("Error failed to get related data:" + sId);
				 }
			 }
		 });
	
	 }
	 
	 /*
	  * Show a related data item
	  */
	 function showRelated(itemIndex, item) {
		 var h = [],
		 	idx = -1,
		 	tableId = "relTable" + itemIndex;
		 
		 h[++idx] = '<div class="row">'
			 h[++idx] = '<div class="col-lg-12">';
             	h[++idx] = '<div class="ibox float-e-margins">';
             		h[++idx] = '<div class="ibox-title">';
             			h[++idx] = '<h5>';
             			h[++idx] = '</h5>'; 
             		h[++idx] = '</div>';
             		h[++idx] = '<div class="ibox-content">';
                 		h[++idx] = '<div class="row">';
	                 		h[++idx] = '<div class="col-lg-12">';
	                 			h[++idx] = '<table id="';
	                 				h[++idx] = tableId;
	                 				h[++idx] = '" class="table table-striped table-responsive toggle-arrow-tiny" data-page-size="8">';
	                 			h[++idx] = '</table>';
	                 		h[++idx] = '</div>';
	                 	h[++idx] = '</div>';
	                 h[++idx] = '</div>';
	            h[++idx] = '</div>';
	        h[++idx] = '</div>';
	    h[++idx] = '</div>';
	    
	    $('#relatedData').append(h.join(""));
	    getRelatedTable(tableId, item)
	 }

	 function getRelatedTable(tableId, item) {
		 
		 var url,
		 	managed = isManagedForms ? "true" : "false";
		 
		 var url = "/api/v1/data/";
		 
		 if(item.type === "child") {
			 url += globals.gCurrentSurvey + "?mgmt=" + managed + "&form=" + item.fId + "&parkey=" + item.parkey;
		 } else if(item.type === "link") {
			 url += item.sId + "?mgmt=" + managed + "&form=" + item.fId + "&hrk=" + item.hrk;
		 }
		 
		 addHourglass();
		 $.ajax({
			 url: url,
			 cache: false,
			 dataType: 'json',
			 success: function(data) {
				 removeHourglass();
				 showManagedData(globals.gCurrentSurvey, '#' + tableId, undefined)
				
				 
				 
			 },
			 error: function(xhr, textStatus, err) {
				 removeHourglass();
				 if(xhr.readyState == 0 || xhr.status == 0) {
					 return;  // Not an error
				 } else {
						alert("Error failed to get table of related data:" + url);
				 }
			 }
		 });
	 }
	 
	 function updateVisibleColumns(cols) {
		 var i,
		 	hiddenColumns = [],
		 	visibleColumns = [];
		 
		 for(i = 0; i < cols.length; i++) {
			 if(cols[i].hide) {
				 hiddenColumns.push(i);	
			 } else {
				 visibleColumns.push(i);
			 }
		 }
		 
		 globals.gMainTable.columns( hiddenColumns ).visible(false, false);
		 globals.gMainTable.columns( visibleColumns ).visible(true, false);
		 $('#trackingTable').width('auto');
		 globals.gMainTable.columns.adjust().draw(); // adjust column sizing and redraw
	 }
	 
	 /*
	  * Save a filter setting
	  */
	 function saveFilter(column, value) {
		 
		var
			config = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex],
			i;
		
		if(value == '') {
			value = undefined;
		}
		
		for(i = 0; i < config.columns.length; i++) {
			if(config.columns[i].colIdx == column) {
				config.columns[i].filterValue = value;
				break;
			}
		}
		
		saveConfig(config);
	 }
	 /*
	  * Update the saved configuration
	  */
	 function saveConfig() {
		var config = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex];
		
		saveString = JSON.stringify(config);
		 
		 addHourglass();
		 $.ajax({
			 type: "POST",
				  dataType: 'text',
				  contentType: "application/json",
				  url: "/surveyKPI/managed/config/" + globals.gCurrentSurvey,
				  data: { settings: saveString },
				  success: function(data, status) {
					  removeHourglass();
					  $('#right-sidebar').removeClass("sidebar-open");
				  }, error: function(data, status) {
					  removeHourglass();
					  alert(data.responseText);
				  }
			});
	 }