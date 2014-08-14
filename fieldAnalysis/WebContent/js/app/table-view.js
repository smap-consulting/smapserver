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
 * Show an entire survey in a table
 */
function setTableSurvey(view) {

	var $selHead = $('#p' + view.pId).find('.phead'),
		$selMain = $('#table_panel' + view.pId),
		$selFoot = $('#p' + view.pId).find('.pfoot'),
		$tabSelect,
		sMeta = globals.gSelector.getSurvey(view.sId),
		topTable = undefined,
		currentTable = undefined,
		idx,
		i,
		tableIdx,
		data = view.results;

	$selHead.empty();
	$selMain.empty();

	/*
	 * Get the top table so it can be automatically selected
	 */
	if(view.table) {
		topTable = view.table;
	} else {
		$.each(sMeta.forms, function(j, item) {
			if(!item.p_id) {
				topTable = item.name;
			}
		});
	}
	
	// If an arbitrary table name has been set as the top table in the view then use that instead of the real top level table
	// The top table is just the table that gets displayed first
	/*
	if(view.currentTableName) {
		topTable = view.currentTableName;
		view.currentTableName = undefined;
	} 
	*/

	
	/*
	 * Add table selector buttons
	 */
	$selHead.append('<form><div id="table_select' + view.pId + '"></div></form>');
	$tabSelect = $('#table_select' + view.pId);
	i = -1;
	currentTable = undefined;
	var btns = [];
	for(idx = 0; idx < data.length; idx++) {
		btns[++i] = '<input type="radio" id="radio' + view.pId + '_' + idx + 
				'" name="radio" value="' + idx + '"'; 
		if(data[idx].tableName === topTable) {
			btns[++i] = ' checked="checked"';
			currentTable = idx;
		}
		btns[++i] = '/><label for="radio'+ view.pId + '_' + idx + '">' +
				data[idx].tableName + '</label>';
		
	}
	$tabSelect.append(btns.join(''));
	$tabSelect.buttonset();
	$tabSelect.find('input').change(function() {
		tableIdx = $tabSelect.find('input:checked').val();
		showTable(tableIdx, view, data[tableIdx], data[tableIdx].tableName);
	});

	if(typeof currentTable != "undefined") {
		// note: the current table may not have been loaded yet if the panels were re-loaded while data
		// was still being retrieved
		showTable(currentTable, view, data[currentTable], data[currentTable].tableName);
	}

	// Enable the export and delete button
	$selFoot.find('.tExport').button().click(function() {
		exportTable($(this), view);
	});
	$selFoot.find('.tDelete').button().click(function() {
		deleteAllTables(view.sId);
	});


}

function exportTable($this, view) {
	
	var tableName = "";
	if(view.qId == "-1") {
		tableName = view.table;
	} else {
		tableName = view.title;
		if(!tableName || tableName == "") {
			tableName = "table";
		}
	}

	$panel = $this.closest('.pContent');
	
	var csvValue = $panel.find('table').table2CSV({delivery:'value'});
	$this.closest('form').find('.csv_name').val(tableName);
	$this.closest('form').find('.csv_text').val(csvValue);
	
}

function deleteAllTables(sId) {
	var msg = "Are you sure you want to delete all the data in this survey?";
	var decision = confirm(msg);
	if (decision == true) {
		var msg2 = "Are you really sure?";
		var decision2 = confirm(msg2);		
		if(decision2 == true) {
			addHourglass();
			$.ajax({
				type : 'Delete',
				url : deleteSurveyDataURL(sId),
				dataType : 'json',
				success : function(response) {
					removeHourglass();
					refreshAnalysisData();
				}, 
				error: function(xhr, textStatus, err) {
						if(xhr.readyState == 0 || xhr.status == 0) {
				              return;  // Not an error
						} else {
							console.log(xhr);
							alert("Error: Failed to delete results: " + err);		
						}
					}
			});
		}
	}

}

function showTable(tableIdx, view, tableItems, tableName) {

	var elemMain = 'table_panel' + view.pId,
		$selMain = $('#'+ elemMain);
	
	$selMain.empty();
	view.table = tableName;		// Save current table name for the export function

	if(view.dirty) {
		view.dirty = false;
		view.currentTableName = tableName;
		refreshAnalysisData();
		return;
	}
	
	if(tableItems && tableItems.features && tableItems.features.length > 0) {
		generateTable(elemMain, tableItems);
		addRightClickToTable($selMain, view.sId, view);
		$selMain.find('table').tablesorter();
		addMoreLessButtons($selMain, view, tableName, tableItems);
	} else {
		$selMain.html("No data available");
	}
}

function addMoreLessButtons($elem, tView, tName, tItems) {
	$elem.find('.get_less').button().click(function() {
		tView.tableCount = 1;
		var currentStart = tView.start_recs[tName].pop();
		var newStart = tView.start_recs[tName].pop();
		processSurveyData(tName, tView.sId, tView, tItems.survey, true, newStart);
	});
	$elem.find('.get_more').button().click(function() {
		tView.tableCount = 1;
		processSurveyData(tName, tView.sId, tView, tItems.survey, true, parseInt($(this).val()));
	});
	$elem.find('.get_less_dis, .get_more_dis').button({ disabled: true });
}

/*
 * Called to display a table with question data
 */
function setTableQuestion(view) {
	
	var $selHead = $('#p' + view.pId).find('.phead'),
		elemMain = 'table_panel' + view.pId,
		$selMain = $('#' + elemMain),
		$selFoot = $('#p' + view.pId).find('.pfoot'),
		disp_desc,
		data;

	$selHead.empty();
	$selMain.empty();

	if(view.results) {
		data = view.results[0];
		disp_desc = getDisplayDescription(data.fn, "graph", data.survey, data.question, data.group, undefined, 
				data.qtype, data.date_question,
				data.start, data.end, data.interval, data.units, data.filter);
		data.caption = disp_desc;
	}

	
	/*
	 * Get all the features for all the items in this form, 
	 */ 
	if(data) {
		generateTable(elemMain, data, disp_desc);
		addRightClickToTable($selMain, view.sId);
	} else {
		$selHead.html("No data available");
	}
	
	// Enable the export and hide the delete button
	$selFoot.find('.tExport').button().click(function() {
		exportTable($(this), view);
	});
	$selFoot.find('.tDelete').hide();
	
}

function getTablePanel() {
	for (var i = 0; i < globals.gSelector.getViews().length; i++) {
		if (globals.gSelector.getViews()[i].title == "Table"){
			return globals.gSelector.getViews()[i];
		}
	}
	
	return {};
}

/*
 * Toggle the setting of the _bad attribute for the passed in data record
 */
function toggleBad($elem, tableName, pKey, value, sId, theView) {
	
	var toValue,
		toBeBad;
	
	if(value == "f") {
		toBeBad = "true";
		toValue = "t";
	} else {
		toBeBad = "false";
		toValue = "f";
	}
	
	var reason = prompt("Please provide a reason","");
	
	$.ajax({
		  type: "POST",
		  contentType: "application/json",
		  dataType: "json",
		  data: { value: toBeBad, sId: sId, reason: reason},
		  url: toggleBadURL(tableName, pKey),
		  success: function(data, status) {
			  $elem.text(toValue);
			  $elem.toggleClass('bad_r').toggleClass('good_r');
			  if(theView) {
				  theView.dirty = true;
			  }
		  }, error: function(data, status) {
			  alert("Error: Failed to update record"); 
		  }
	});
	
}

/*
 * Add a right click function to all anchors beneath the passed in element that will add the media item to the report
 * Also add a function to toggle the "bad" status of a record
 */
function addRightClickToTable($elem, sId, view) {
	$elem.find('a').off().bind("contextmenu", function(e) {
		var $this = $(this),
			mediaObj,
			mediaType,
			media = $this.attr("href"),
			thumbnail = $this.find('img').attr("src");
		
		gReport = {
			smap: {
				sId: sId
			},
			url: media, 
			title: "media", 
			url: media,
			thumbnail_url: thumbnail,
			action: "new"
		};
		
		if(thumbnail.indexOf("audio-icon") > -1) {
			mediaType = "audio";
		} else {
			mediaObj = getMedia(media);
			mediaType = mediaObj.type;
			if(mediaType === "image") {
				mediaType = "photo";		// Photo used instead of image in reports as per oembed
			}
		}
		
		gReport.type = mediaType;
		gReport.smap.data_gen_type = mediaType;

		$('#report_title').val(gReport.title);
		$('#reportContainer').dialog("open");
		setReport(gReport);
		return false;
	});
	
	function toggleBadFn(survey, theView) {
		$elem.find('.bad_r, .good_r').off().bind("contextmenu", function(e) {
			var $this = $(this);
			var pkey = $this.attr("pkey");
			var tableName = $this.closest('table').attr("name");
			var value = $this.text();
			toggleBad($(this), tableName, pkey, value, survey, theView);
			return false;
		});
	}
	
	toggleBadFn(sId, view);
}
