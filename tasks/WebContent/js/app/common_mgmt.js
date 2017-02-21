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
			surveyList: {},
			surveyRoles: {}
		},
		gSelectedRecord: undefined,
		gSelectedSurveyIndex: undefined,
		gUpdate: [],
		gCurrentIndex: undefined,
		gPriKey: undefined,
		gSort: undefined,
		gDirn: undefined
	}

	var gBlankChart = {
	    	groups: [],
			time_interval: false,
			humanName: "",
			name: "",
			chart_type: "bar",
			group: "none",
			fn: "count",
			tSeries: false,
			period: undefined,
			width: 12
		};

	var gReport = {
			date_q: "Upload Time",
			row: [
			      {
						datatable: false,
						name: "history",
						charts: 
							[
							 /*
						         {
						        	groups: [
						        	         {
						        	        	 q: "_start",
						        	        	 label: "Upload Time"
						        	         },
						        	         {
						        	        	 q: "Action Date",
						        	        	 label: "Date Closed"
						        	         }],
									time_interval: true,
						        	humanName: "",
									name: "periodic_count",
									chart_type: "groupedBar",
									group: undefined,
									fn: "count",
									tSeries: true,
									period: "day",
									width: 12
								},
								{
						        	groups: [
						        	         {
						        	        	 q: "_start",
						        	        	 label: "Start Time"
						        	         },
						        	         {
						        	        	 q: "_end",
						        	        	 label: "Finish Time"
						        	         }],
									time_interval: true,
									humanName: "Average survey completion time",
									name: "completion_time",
									chart_type: "bar",
									group: "_user",
									fn: "avgdurn",
									tSeries: false,
									period: undefined,
									width: 12
								}
								*/
						    ]
			      }
			      /*
			      ,
			      {
					datatable: true,
					name: "chartrow",
					def: {
						chart_type: "bar",
						groups: undefined,
						group: undefined,
						fn: "length",
						tSeries: false,
						period: undefined
					},
					charts: []
			      }
			      */
			    ]
	};
	var gReportLoaded = false,
		gDataLoaded = false,
		gConfigLoaded = false;
	 
	 /*
	  * Function called when the current project is changed
	  */
	 function projectChanged() {

	 	globals.gCurrentProject = $('#project_name option:selected').val();
	 	globals.gCurrentSurvey = -1;
		globals.gCurrentTaskGroup = undefined;
		
		saveCurrentProject(globals.gCurrentProject, 
				globals.gCurrentSurvey, 
				globals.gCurrentTaskGroup);
		
	 	refreshData();
	 	
	 }
	 
	 /*
	  * Function called when the current survey is changed
	  */
	 function surveyChanged() {
		
		gReportLoaded = false;
		gDataLoaded = false;
		gConfigLoaded = false;
		
		if(globals.gCurrentSurvey > 0) {
			
			 saveCurrentProject(-1, globals.gCurrentSurvey);
			 if(isManagedForms) {
				 getSurveyConfig(globals.gCurrentSurvey, gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].managed_id);
			 } else {
				 getSurveyConfig(globals.gCurrentSurvey, 0);
			 }
			 if(!isDuplicates) {
				 getReport(gReport);
			 }
			 
			 $('.main_survey').html($('#survey_name option:selected').text());
			 
		 } else {
			 // No managed surveys in this project
			 $('#trackingTable').empty();
		 }
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
			h[++idx] = record[configItem.name];
			h[++idx] = '">';
		} else {
			h[++idx] = addEditableColumnMarkup(configItem, record[configItem.name], itemIndex, first);
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
		 //gTasks.cache.surveyConfig = {};
		 gTasks.cache.managedData = {};
		 gTasks.cache.surveyList = {};
		 
		 // Get the list of available surveys
		 loadManagedSurveys(globals.gCurrentProject, surveyChanged);
		 getAlerts();
		 
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
		
		currentValue = record[columns[itemIndex].name];
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
	  * If masterRecord is specified then only show that record
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
		 	hDups = [],
		 	hColSortIdx = -1;
		 
		 
		 if(globals.gMainTable) {
			 globals.gMainTable.destroy();
		 }
		 
		 // Add head
		 h[++idx] = '<thead>';
		 h[++idx] = '<tr>';
		 
		 for(i = 0; i < columns.length; i++) {
			 headItem = columns[i];
			 
			 hColSort[hColSortIdx++] = addToColumnSort(headItem);
			 hDups[hColSortIdx++] = addToDuplicateReportSelect(headItem);
			 
			 shownColumns.push({
				 "data": headItem.name
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
		 var url = '/api/v1/data/';	 
		 url += sId;
		 
		 if(isManagedForms) {
			 url += "?mgmt=true";
		 } else{
			 url += "?mgmt=false";
		 }
		 
		 if(isDuplicates) {
			 url += "&group=true";
		 }
		 
		 
		 url += "&format=dt";
		 
		 $.fn.dataTable.ext.errMode = 'none';
		 
		 // Create data table
		 globals.gMainTable = $table.DataTable({
			 processing: true,
			 select: true,
		     ajax: url,
		     columns: shownColumns,
		     order: [[ 0, "desc" ]],
		     initComplete: function(settings, json) {
		    	 console.log("initComplete");
		    	 gDataLoaded = true;
		    	 console.log("Data loaded: " + gDataLoaded + " : " + gReportLoaded + " : " + gConfigLoaded)
		    	 if(gReportLoaded && gConfigLoaded) {
					 chart.setChartList();	// Enable charts based on this survey config
					 chart.refreshCharts();
				 }
				 columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns;
				 globals.gMainTable.columns().flatten().each( function ( colIdx ) {
					 if(columns[colIdx].filter || columns[colIdx].type === "select1") {
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
		 
		 // Respond to an error
		 globals.gMainTable.on( 'error.dt', function ( e, settings, techNote, message ) {
		        alert( 'An error has been reported by DataTables: ', message );
		    } );
		 
		 // Respond to selection of a row
		 globals.gMainTable
	        .off('select').on( 'select', function ( e, dt, type, indexes ) {
	            var rowData = globals.gMainTable.rows( indexes ).data().toArray();
	            if(isManagedForms) {
	            	gTasks.gSelectedRecord = rowData[0];
	            	$('#editRecord').modal("show");
	            } else if(isBrowseResults) {
	            	// TODO check if the user has maintain privilege
	            	alert(JSON.stringify( rowData ));
	            }
	            //alert(JSON.stringify( rowData ));
	        } );
		 
		 // Highlight data conditionally, set barcodes
		 globals.gMainTable.off('draw').on( 'draw', function () {
			 
			 if(isDuplicates) {
				 
				 var rows = globals.gMainTable.rows( {page:'current'} ).nodes();
		         var last=null;
		 
		         globals.gMainTable.column(0, {page:'current'} ).data().each( function ( group, i ) {
		                if ( last !== group ) {
		                    $(rows).eq( i ).before(
		                        '<tr class="group"><td colspan="5">' + group + '</td></tr>'
		                    );
		 
		                    last = group;
		                }
		            } );
			 } 
	            
			 columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns;
	 
			 for(i = 0; i < columns.length; i++) {
				 headItem = columns[i];
				 
				 // Highlighting
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
				 
				 // Barcode
				 if(headItem.barcode) {
					 $( globals.gMainTable.column( headItem.colIdx ).nodes() ).each(function(index) {
						 var $this = $(this),
						 	opt = {
							 render: 'div',
							 size: 100,
							 text: $this.text()
						 }

						$this.empty().qrcode(opt);
						
					 });
				 }
			 }
			 
		 });
		
		 // Respond to date filter changes
		 $('#filter_from, #filter_to').focusout( function() { globals.gMainTable.draw(); } );
		 	
		 // Respond to change of search
		 $('#trackingTable_filter input').focusout(function() { globals.gMainTable.draw(); } );
		
		 /*
		  * Settings
		  */
		 $('#tab-columns-content, #tab-barcode-content').html(hColSort.join(''));
		 
		 // Set checkboxes in column sort section of settings
		 
		 $('input', '#tab-columns-content,#tab-barcode-content').iCheck({
			 checkboxClass: 'icheckbox_square-green',
			 radioClass: 'iradio_square-green'
		 });
		 $('input', '#tab-columns-content').each(function(index){
			 if(!columns[index+1].hide) {
				 $(this).iCheck('check');
			 }
		 });
		 $('input', '#tab-barcode-content').each(function(index){
			 if(columns[index+1].barcode) {
				 $(this).iCheck('check');
			 }
		 });
		 
		/*
		 * Duplicates modal
		 */
		 $('#duplicateSelect').html(hDups.join(''));
		 $('input', '#duplicateSelect').iCheck({
			 checkboxClass: 'icheckbox_square-green',
			 radioClass: 'iradio_square-green'
		 });

	 }
	 
	 /*
	  * Show duplicates data
	  */
	 function showDuplicateData(sId, tableElem) {
		 
		 var url = '/api/v1/data/similar/' + sId + '/' + getSearchCriteria() + "?format=dt";
		 globals.gMainTable.ajax.url( url ).load();
	
	 }
	 
	/*
	 * Get the search criteria for a duplicate search
	 */
	function getSearchCriteria() {
		var criteria = "";
		
		$('input', '#duplicateSelect').each(function(index){
			var $this = $(this),
				fn;
			
			if($this.is(':checked')) {
				if(criteria.length > 0) {
					criteria += ',';
				}
				fn = $this.closest('.row').find('select').val();
				criteria += $this.val() + '::' + fn;
			}
			
			
		});
		
		return criteria;
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
			//if(!item.hide) {
			//	h[++idx] = ' checked';
			//}
			h[++idx] = '>';
			h[++idx] = '</div>';
			h[++idx] = '</div>';
		}
		return h.join('');
	}
	
	/*
	 * Add the column to the select list for duplicate searches
	 */
	function addToDuplicateReportSelect(item) {
		var h = [],
			idx = -1;
		
		if(item.include) {
			h[++idx] = '<div class="row">';
				h[++idx] = '<div class="setings-item">';
				
					h[++idx] = '<div class="col-sm-1">';
						h[++idx] = '<input type="checkbox" name="columnSelect"';
						h[++idx] = ' class="columnSelect" value="';
						h[++idx] = item.name;
						h[++idx] = '"';	
						h[++idx] = '>';
					h[++idx] = '</div>';
					
					h[++idx] = '<div class="col-sm-4">';
						h[++idx] = '<span>';
							h[++idx] = item.humanName;
						h[++idx] = '</span>';
					h[++idx] = '</div>';
				
					
					
					h[++idx] = '<div class= "col-sm-4">';
						h[++idx] = '<select>';
							h[++idx] = '<option value="exact">';
								h[++idx] = localise.set["br_exact"];
							h[++idx] = '</option>';
							h[++idx] = '<option value="lower">';
								h[++idx] = localise.set["br_ci"];
							h[++idx] = '</option>';
								h[++idx] = '<option value="soundex">';
							h[++idx] = localise.set["br_sdx"];
						h[++idx] = '</option>';
						h[++idx] = '</select>';
					h[++idx] = '</div>';
						
							
				h[++idx] = '</div>';	// Settings item
			h[++idx] = '</div>';		// Row
				
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
		
		if(column.type === "text" || column.type === "decimal" || column.type === "integer") {
			h[++idx] = ' <input type="';
			if(column.type === "text") {
				h[++idx] = "text";
			} else if(column.type === "decimal") {
				h[++idx] = "number";
			} else if(column.type === "integer") {
				h[++idx] = "number";
			}
			h[++idx] = '" class="form-control editable" value="';
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
					if(column.choices[i].k === value) {
						h[++idx] =' selected="selected"'
					}
					h[++idx] = ' value="';
					h[++idx] = column.choices[i].k;
					h[++idx] = '">'
					h[++idx] = column.choices[i].v;
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
	 *  This is a different function from the common loadSurveys function as processing differs depending on whether 
	 *    there is a managed form
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
	 					if(item.managed_id > 0 || isBrowseResults || isDuplicates) {
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
					 gConfigLoaded = true;
					 gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex] = data;
					 console.log("Config loaded: " + gDataLoaded + " : " + gReportLoaded + " : " + gConfigLoaded)
					 if(gReportLoaded && gDataLoaded) {
						 chart.setChartList();	// Enable charts based on this survey config
						 chart.refreshCharts();
					 }
					 
					 // Add a config item for the group value if this is a duplicates search
					 if(isDuplicates) {
						 data.columns.unshift({
							hide: true,
							include: true,
						 	name: "_group",
						 	humanName: "_group"
						 });
					 }
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
	  *  This includes information on specific charts that are added to the survey whereas the report save below
	  *  is for the base report.  
	  */
	 function saveConfig() {
		 var config = {
				 columns: []
		 },
		 columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns,
		 i;
		 
		for(i = 0; i < columns.length; i++) {
			config.columns.push({
				name: columns[i].name,
				hide: columns[i].hide,
				barcode: columns[i].barcode,
				filterValue: columns[i].filterValue,
				chart_type: columns[i].chart_type,
				width: columns[i].width ? columns[i].width : 6
			});
		}
		
		saveString = JSON.stringify(config);
		 
		 addHourglass();
		 $.ajax({
			 type: "POST",
			 dataType: 'text',
			 cache: false,
				  contentType: "application/json",
				  url: "/surveyKPI/managed/config/" + globals.gCurrentSurvey + "/mf",
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
	 
	 /*
	  * Save the report definition as opposed to saveConfig which saves the data table filter settings
	  * TODO These should be merged?????
	  */
	 function saveReport(report) {
		 
		
		saveString = JSON.stringify(report);
		 
		 addHourglass();
		 $.ajax({
			 type: "POST",
			 dataType: 'text',
			 cache: false,
				  contentType: "application/json",
				  url: "/surveyKPI/managed/config/" + globals.gCurrentSurvey + "/db",
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
	 
	 /*
	  * Get the report
	  */
	 function getReport(gReport) {
		 
			 addHourglass();
			 $.ajax({
				 url: "/surveyKPI/managed/getreportconfig/" + globals.gCurrentSurvey + "/db",
				 cache: false,
				 dataType: 'json',
				 success: function(data) {
					 removeHourglass();
					 gReportLoaded = true;
					 if(data) {
						 chart.setReport(data);
					 } else {
						 chart.setReport(gReport);
					 }
					 console.log("Report loaded: " + gDataLoaded + " : " + gReportLoaded + " : " + gConfigLoaded)
					 if(gDataLoaded && gConfigLoaded) {
						 chart.setChartList();
						 chart.refreshCharts();
					 }
				 },
				 error: function(xhr, textStatus, err) {
					 removeHourglass();
					 chart.setReport(gReport);
					 gReportLoaded = true;
					 console.log("Report loaded: " + gDataLoaded + " : " + gReportLoaded + " : " + gConfigLoaded)
					 if(gDataLoaded && gConfigLoaded) {
						 chart.setChartList();
						 chart.refreshCharts();
					 }
					 
				 }
			 });
			 
		 }
	 
	 
	 /*
	  * Add html to show a form to edit a record
	  */
	 function showEditRecordForm(record, columns, $editForm, $surveyForm) {
		var 
			h = [],
			idx = -1,
			m = [],
			cnt = -1,
			i,
			configItem,
			first = true;
		
		//gTasks.gCurrentIndex = index;
		gTasks.gPriKey = record["prikey"];
		
		// Clear the update array
		gTasks.gUpdate = [];
		$('#saveRecord').prop("disabled", true);
		
		for(i = 0; i < columns.length; i++) {
			configItem = columns[i];
			
			if(configItem.mgmt) {
				h[++idx] = getEditMarkup(configItem, i, first, record);
			} else {
				m[++cnt] = getEditMarkup(configItem, i, first, record);
			}
			if(!configItem.readonly) {
				first = false;
			}
		}
		
		$editForm.html(h.join(''));
		$surveyForm.html(m.join(''));
		
		// Set up date fields
		$editForm.find('.date').datetimepicker({
			locale: gUserLocale || 'en',
			useCurrent: false,
			showTodayButton: true
		});
		
		// Respond to changes in the data by creating an update object
		$editForm.find('.form-control').bind("click propertychange paste change keyup input", function() {
			dataChanged($(this));
		});
		$editForm.find('.date').on("dp.change", function() {
			dataChanged($(this).find('input'));
		});
		$editForm.find('select').change(function() {
			dataChanged($(this));
		});
		
		// Set focus to first editable data item
		$editForm.find('[autofocus]').focus();
	 }
