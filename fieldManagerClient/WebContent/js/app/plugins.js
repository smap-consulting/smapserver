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

// usage: log('inside coolFunc', this, arguments);
// paulirish.com/2009/log-a-lightweight-wrapper-for-consolelog/
window.log = function(){
  log.history = log.history || [];   // store logs to an array for reference
  log.history.push(arguments);
  arguments.callee = arguments.callee.caller;  
  if(this.console) console.log( Array.prototype.slice.call(arguments) );
};
// make it safe to use console.log always
(function(b){function c(){}for(var d="assert,count,debug,dir,dirxml,error,exception,group,groupCollapsed,groupEnd,info,log,markTimeline,profile,profileEnd,time,timeEnd,trace,warn".split(","),a;a=d.pop();)b[a]=b[a]||c})(window.console=window.console||{});

/*
 * Generate a table of tasks
 * @author	Neil Penman
 */
(function($) {
	$.fn.generateTaskTable = function( options ) {
		
		// plugin's default options
		var settings = {
		    'rowClass': '',
		    'colClass': 'ui-widget-content',
		    'fields': [],
		    'nodataString': 'No records found.',
		    'data': {}
		}
		
		return this.each(function() {
			if ( options ) { 
				$.extend( settings, options );
			}
			
			var surveyName,
				tab = [],
				idx = -1,
				addressParams,
				addressObj,
				addressParamsObj,
				currentTg = "-1",
				i,j,
				$this = $(this),
				assignmentStatus,
				showCompleted,
				group,
				anItem,
				currentItemIndex = 0;
			
			$.each(settings.data.task_groups, function(j, group) {
				currentTg = group.tg_id;

			//$.each(settings.data.features, function(j, item) {
			//	surveyName = getProperty(item.properties, 'survey_name');
			//	assignmentStatus = getProperty(item.properties, 'assignment_status');
			//	thisTg = item.properties.task_group_id;
			//	if(currentTg !== thisTg) {
			//		currentTg = thisTg;
			//		if(recCounter > 1) {
			//			tab[++idx] = '</tbody></table>';
			//		}
					
					tab[++idx] = '<span class="task_group_name">';
					tab[++idx] = group.tg_name;
					tab[++idx] = '</span>';
					// Add a button to delete the task group
					tab[++idx] = '<span class="noPrint">';
					tab[++idx] = '<button class="add_new_task btn btn-success" value="';
					tab[++idx] = currentTg;
					tab[++idx] = '"><span class="glyphicon glyphicon-plus" aria-hidden="true"></span>' + localise.set["t_add_tasks2"] + '</button>';
					tab[++idx] = '<button class="save_new_task btn btn-warning" style="display:none;" value="';
					tab[++idx] = currentTg;
					tab[++idx] = '"><span class="glyphicon glyphicon-save" aria-hidden="true"></span>' + localise.set["t_save_tasks"] + '</button>';
					tab[++idx] = '<button class="delete_task_group btn btn-danger" value="';
					tab[++idx] = currentTg;
					tab[++idx] = '"><span class="glyphicon glyphicon-trash" aria-hidden="true"></span>' + localise.set["c_del"] + '</button>';
					tab[++idx] = '</span>';
					
					// Create the table
					tab[++idx] = '<table class="tasks table table-striped" id="tasktable';
					tab[++idx] = currentTg;
					tab[++idx] = '">';
					tab[++idx] = '<thead><tr>';
					tab[++idx] = '<th>' + localise.set["c_select"] + '</th>';
					tab[++idx] = '<th>' + localise.set["c_task"] + '</th>';
					tab[++idx] = '<th>' + localise.set["c_title"] + '</th>';
					tab[++idx] = '<th>' + localise.set["c_status"] + '</th>';
					tab[++idx] = '<th>' + localise.set["c_coords"] + '</th>';
					tab[++idx] = '<th>' + localise.set["c_repeat"] + '</th>';
					tab[++idx] = '<th>' + localise.set["t_assigned"] + '</th>';
					tab[++idx] = '<th>' + localise.set["c_scheduled"] + '</th>';
					
					// Add address params
					addressParams = group.tg_address_params;
					addressParamsObj = $.parseJSON(addressParams);
					if(addressParamsObj) {
						for(i = 0; i < addressParamsObj.length; i++) {
							if(addressParamsObj[i].selected) {
								tab[++idx] = '<th>' + addressParamsObj[i].name + '</th>';
							}
						}
					}

					tab[++idx] = '</tr></thead><tbody>';
					
					anItem = addItems(currentItemIndex, settings, currentTg);		// Add items
					currentItemIndex = anItem.currentItemIndex;
					tab[++idx] = anItem.resp;

					tab[++idx] = '</tbody></table>';
					
				});
				
			
			// Populate table
			$this.append(tab.join(''));

		});
	};
		
})(jQuery);

function addItems(start_rec, settings, current_tg) {
	
	var i,
		item,
		addressParams,
		addressObj,
		addressParamsObj,
		assignmentStatus,
		surveyName,
		tab = [],
		idx = -1,
		ret = {};
	
	for(i = start_rec; i < settings.data.features.length; i++) {
		
		item = settings.data.features[i];
		if(current_tg != item.properties.task_group_id) {
			break;	// end of task group			
		}
		
		assignmentStatus = getProperty(item.properties, 'assignment_status');
		surveyName = getProperty(item.properties, 'survey_name');
		
		// Check if we should show this record
		showCompleted = item.properties.task_id > 0 && (settings.showCompleted || assignmentStatus !== "submitted");
		
		if(showCompleted) {
			tab[++idx] = '<tr data-idx="';
			tab[++idx] = i;
			tab[++idx] = '">';
		
			// Add select checkbox
			tab[++idx] = '<td class="control_td"><input class="select_row" type="checkbox" name="controls" data-taskid="';
			tab[++idx] = item.properties.task_id;
			tab[++idx] = '" data-assid="';
			tab[++idx] = item.properties.assignment_id;
			tab[++idx] = '" data-status="';
			tab[++idx] = item.properties.assignment_status;
			tab[++idx] = '"></td>';
		
			tab[++idx] = '<td><button type="button" class="task_edit">' + surveyName + '</button></td>';
		
			// Task title
			tab[++idx] = '<td>';
			tab[++idx] = item.properties.title;
			tab[++idx] = '</td>';
		
			tab[++idx] = '<td class="' + item.properties.assignment_status + '">' + item.properties.assignment_status + '</td>';
		
			if(item.geometry) {
				tab[++idx] = '<td>' + item.geometry.type + '[' + formatCoords(item.geometry.coordinates) + ']' + '</td>';
			} else {
				tab[++idx] = '<td></td>';
			}
		
			tab[++idx] = '<td>';
			tab[++idx] = item.properties.repeat ? 'yes' : 'no';
			tab[++idx] = '</td>';
		
			// Assigned user
			tab[++idx] = '<td>';
			tab[++idx] = item.properties.user_name;
			tab[++idx] = '</td>';
		
			// Scheduled At
			tab[++idx] = '<td>';
			//tab[++idx] = formatTimestamp(item.properties.scheduleAt);
			tab[++idx] = localTime(item.properties.scheduleAt);
			tab[++idx] = '</td>';
		
			if(addressParamsObj) {
				addressObj = $.parseJSON(item.properties.address);
			
				if(addressObj) {
					j = 0;
					for(i = 0; i < addressParamsObj.length; i++) {
						if(addressParamsObj[i].selected) {
							value = addAnchors(addressObj[j].value).join(',');
							
							if(addressParamsObj[i].isBarcode) {
								tab[++idx] = '<td class="barcode">' + value + '</td>';
							} else {							
								tab[++idx] = '<td>' + value + '</td>';
							}
							j++;
						} 
					}
				}
					
			}
			tab[++idx] = '</tr>';
		}
	}
	ret.currentItemIndex = i;
	ret.resp = tab.join("");
	
	return ret;		// Return the counter to avoid going over same items for every task group
}

function formatTimestamp(value) {
	var aValue = [];
	
	aValue = value.split(":");
	if(aValue.length === 3) {
		value = aValue[0] + ":" + aValue[1];
	}
	return value;
	
}
function formatCoords(coords) {
	
	var cList = [],
		i,
		pointIdx,
		idx = -1,
		cListOut = [];
	
	if(coords) {
		coords = String(coords);
		cList= coords.split(",");
		for(i = 0; i < cList.length; i++) {
			pointIdx = cList[i].indexOf(".");
			if(pointIdx >= 0) {
				cListOut[++idx] = cList[i].substring(0, pointIdx + 2); 
			} else {
				cListOut[++idx] = cList[i];
			}
		}
	}
	
	return cListOut.join(',');
}

function includeKey(key) {
	if(key === "_table" ||
			key === 'assignment_id' ||
			key === 'survey_name' ||
			key === 'user_id' ||
			key === 'user_ident' || 
			key === 'geo_type' ||
			key === 'task_group_id' ||
			key === 'task_group_name' ||
			key === 'assignment_status' ||
			key === 'type') {
		return false;
	}
	return true;
}

function getProperty(properties, property) {
	var key;
	
	for(key in properties) {
		if( key === property) {
			return properties[key];
		}
	}
	
	return "";
}

