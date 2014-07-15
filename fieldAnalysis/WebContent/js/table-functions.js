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

var gTab = [];
	gIdx = -1;


function generateTable(elementId, data, disp_desc) {
	
	console.log("generate table");
	var i,j,k,
		fn = data.fn,
		groups = data.features,
		cols = data.cols,
		groupLabels = data.groups,
		isGrouped = false,
		isPeriod = false,
		numberCols,
		numberGroups,
		repeatCols = 1,
		title,
		val, key,
		params,
		record = [],
		curPeriod,
		curGroup,
		recordObject = {};
	
	if(typeof fn === "undefined") {
		fn = "none";
	}
	if(data.table) {
		title = data.tableName;
	} else {
		title = data.question;
	}
	
	// Write information about the table
	gTab = [];
	gTab[++gIdx] = '<div><div id="scroll_controls"><p>';
	if(typeof disp_desc !== "undefined") {
		gTab[++gIdx] = disp_desc;
	} else if(fn != "none") {
		gTab[++gIdx] = fn + " of: ";
		gTab[++gIdx] = title;
	} else {
		gTab[++gIdx] = title;
	}
	if(typeof data.totals !== "undefined") {
		gTab[++gIdx] = '(';
		gTab[++gIdx] = data.totals.total_count;
		gTab[++gIdx] = ' records, ';
		gTab[++gIdx] = data.totals.bad_count;
		gTab[++gIdx] = ' marked bad)';
	}
	
	gTab[++gIdx] = '</p>';
	
	// Add next / previous buttons
	if(typeof data.totals !== "undefined" && data.totals.returned_count < data.totals.total_count) {
		gTab[++gIdx] = '<div class="get_less_more">';
		if(data.totals.start_key > 0) {
			// Add less
			gTab[++gIdx] = '<button class="get_less" value="';
			gTab[++gIdx] = 0;
			gTab[++gIdx] = '">&lt;&lt;&lt;</button>';
		} else {
			// Add disabled less
			gTab[++gIdx] = '<button class="get_less_dis">&lt;&lt;&lt;</button>';
		}
		if(data.totals.more_recs > 0) {
			// Add more
			gTab[++gIdx] = '<button class="get_more" value="';
			gTab[++gIdx] = data.totals.max_rec;
			gTab[++gIdx] = '">&gt;&gt;&gt;</button>';
		} else {
			// Add disabled more
			gTab[++gIdx] = '<button class="get_more_dis">&gt;&gt;&gt;</button>';
		}
		gTab[++gIdx] = '</div>';
	}
	gTab[++gIdx] = '</div>';	// scroll controls
	
	if(typeof data.group !== "undefined") {
		isGrouped = true;
	}
	if(typeof data.interval !== "undefined" && data.period !== "none") {
		isPeriod = true;
	}
	gTab[++gIdx] = '<div id="scroll_zone">';
	gTab[++gIdx] = '<table name="' + data.tableName + '" border="1" class="tablesorter">';

	// Write headers
	gTab[++gIdx] = '<thead>';

	numberCols = cols.length;
	
	// Add grouping columns across the top of the table if there are both time series and groups
	if(isPeriod && isGrouped) {
		numberGroups = groupLabels.length;
		repeatCols = numberGroups;
		gTab[++gIdx] = '<tr>';
		gTab[++gIdx] = '<td></td>';
		for(i = 0; i < numberGroups; i++) {
			gTab[++gIdx] = '<th colspan="';
			gTab[++gIdx] = numberCols;
			gTab[++gIdx] = '">';
			gTab[++gIdx] = groupLabels[i];
			gTab[++gIdx] = '</th>';
		}
		gTab[++gIdx] = '</tr>';
	}
	
	// Add the column headings
	gTab[++gIdx] = '<tr>';
	if(isGrouped && !isPeriod) {
		gTab[++gIdx] = '<th>' + data.group + '</th>';
	} else if(isPeriod ){
		gTab[++gIdx] = '<th>' + data.interval + '</th>';
	} else {
		gTab[++gIdx] = '<th>Record</th>';
	}
	
	for(j = 0; j < repeatCols; j++) {
		for(i = 0; i < numberCols; i++) {	
			if(cols[i] !== "_instanceid" && cols[i] !== "instanceid" && cols[i] !== "_task_key" && 
					cols[i] !== "_task_replace"&& cols[i] !== "prikey") {
				gTab[++gIdx] = '<th>';
				gTab[++gIdx] = cols[i];
				gTab[++gIdx] = '</th>';
			}
		}
	}
	
	gTab[++gIdx] = '</tr></thead><tbody>';

	/*
	 * If the data is both grouped by a question and grouped by time then there a single
	 *  record of data will be spread across multiple feature objects
	 */
	if(isGrouped && isPeriod) {

		for(i = 0; i < groups.length; i++) {
			if(curPeriod != groups[i].properties.period) {				
				if(typeof curPeriod !== "undefined") {
					writeRecord(record, recordObject, groupLabels, numberCols);
				}
				curPeriod = groups[i].properties.period;
				record = [];
				recordObject = {};
				record.push(groups[i].properties.period);
			}
			
			curGroup = groups[i].properties.group_label;
			if(typeof recordObject[curGroup] === "undefined") {
				recordObject[curGroup] = [];
			}
			for(k = 0; k < cols.length; k++) {
				key = cols[k];
				val = addAnchors(groups[i].properties[key]);
				recordObject[curGroup].push(val);
			}
		}
		if(typeof curPeriod !== "undefined") {
			writeRecord(record, recordObject, groupLabels, numberCols);
		}
	} else {
	
		for(i = 0; i < groups.length; i++) {
			
			gTab[++gIdx] = '<tr>';
			
			gTab[++gIdx] = '<td>';
			if(isGrouped && !isPeriod) {
				gTab[++gIdx] = groups[i].properties.group_label;
			} else if(isPeriod) {
				gTab[++gIdx] = groups[i].properties.period;
			} else {
				gTab[++gIdx] = groups[i].properties.prikeys[0];
			} 
			gTab[++gIdx] = '</td>';
	
			for(k = 0; k < cols.length; k++) {
				
				params = "";
				key = cols[k];
	
				if(key !== "_instanceid" && key !== "instanceid" && key !== "_task_key" && key !== "_task_replace" && key !== "prikey") {
					if (isGrouped && fn === "none") {
						// Grouped but no aggregating function. Show as an array
						for(j = 0; j < groups[i].properties[key].length; j++) {
							val = groups[i].properties[key][j];
							if(key === "the_geom") {
								// Get the value from the geometry
								if(typeof groups[i].geometry !== "undefined" && 
										typeof groups[i].geometry.coordinates != "undefined") {
									val = groups[i].geometry.coordinates.join(',');
								}
							}
							gTab[++gIdx] = '<td ' + params + '>';
							if(val === "0") {
								gTab[++gIdx] = "";
							} else {
								gTab[++gIdx] = addAnchors(val).join(',');
							}
							gTab[++gIdx] = '</td>';
						}
					} else {
						val = groups[i].properties[key];
						if(key === "the_geom" && typeof groups[i].geometry !== "undefined") {
							// Get the value from the geometry
							val = groups[i].geometry.type + '[' + groups[i].geometry.coordinates + ']';
						} else if(key === "_bad") {
							if(val === "f") {
								params = 'class="good_r" pkey="' + groups[i].properties.prikeys[0] + '"';
							} else {
								params = 'class="bad_r" pkey="' + groups[i].properties.prikeys[0] + '"';
							}
						} 
						gTab[++gIdx] = '<td ' + params + '>';
						if(val === "0") {
							gTab[++gIdx] = "";
						} else {
							gTab[++gIdx] = addAnchors(val).join(',');
						}
						gTab[++gIdx] = '</td>';
					}
				}
				
			}
		
			gTab[++gIdx] = '</tr>';
	
		}
	}

	gTab[++gIdx] = '</tbody></table></div></div></div>';
	document.getElementById(elementId).innerHTML=gTab.join('');
	gTab = [];	// Free memory

}

function writeRecord(record, recordObject, groupLabels, numberCols) {
	var i, j,
		subRecord = [];

	gTab[++gIdx] = '<tr>';
	for(i = 0; i < groupLabels.length; i++) {
		subRecord = recordObject[groupLabels[i]];
		if(typeof subRecord === "undefined") {
			for(j = 0; j < numberCols; j++) {
				record.push("");
			}
		} else {
			record = record.concat(subRecord);
		}
	}
	for(i = 0; i < record.length; i++) {
		gTab[++gIdx] = '<td>';
		gTab[++gIdx] = record[i];
		gTab[++gIdx] = '</td>';
	}
	gTab[++gIdx] = '</tr>';
}

