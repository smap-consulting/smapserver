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
 * Write questions as a table
 * @author	Neil Penman
 */
(function($) {
	$.fn.questionListGenerator = function( sId, data ) {

		
		return this.each(function() {
					
			writebuffer = "";
			
			// Process JSON
			$.each(data, function(j, item){
				
				writebuffer += '<tr class="ui-state-highlight" id="q' + item.id + '" ' +
					's_name="' + sId + '" ' +
					's_id="' + item.id + '" ' +
					'q="' + item.q +'">';
				
				writebuffer += '<td class="ui-widget-content show_question">' + item.q + '</td>';	
				writebuffer += '<td class="ui-widget-content">' + item.type + '</td>';
				writebuffer += '</tr>';
				

			});
			
			// Populate table
			$(this).append(writebuffer);
			
		});
	};	
		
})(jQuery);

/*
 * Write regions as a select list
 * @author	Neil Penman
 */
(function($) {
	$.fn.regionListGenerator = function( sId, data ) {

		
		return this.each(function() {
					
			writebuffer = '<p>' +
				'<label for="' + sId + '_regionSelect">Region Type:  </label>' +
				'<select id=' + sId + '_regionSelect>' + 
				'<option value="none">none</option>';
				
				// Process JSON
				$.each(data, function(j, item){			
					writebuffer += '<option value="' + item.table + '">' + item.table + '</option>';
				});
				
				writebuffer += '</select>' +
					'</p>';
	
				// Populate table
				$(this).append(writebuffer);
				
		});
	};	
		
})(jQuery);

/*
 * Generate a table with all the properties for a selected item
 * @author	Neil Penman
 */
(function($) {
	$.fn.featureSelect = function( data, clusterData ) {
		
		writebuffer = "";
		var aDataItem,
			h = [],
			idx = -1,
			i;
		
		if(clusterData) {
			aDataItem = clusterData[0].attributes;
		} else {
			aDataItem = data;
		}
		
		// Close button
		h[++idx] = '<img id="fDel" src="/fieldAnalysis/img/delete.png"/><br/>';
		
		// show all the properties for the event
		h[++idx] = '<div id="feature_data">';
		h[++idx] = '<table border="1">';
		
		h[++idx] = '<tbody>';
		$.each(aDataItem, function(key, value) {
			h[++idx] = '<tr>';
			h[++idx] = '<td>' + key + '</td>';
			if(clusterData) {
				for(i = 0; i < clusterData.length; i++) {
					h[++idx] = '<td>' + clusterData[i].attributes[key] + '</td>';
				}
			} else {
				h[++idx] = '<td>' + value + '</td>';
			}
			h[++idx] = '</tr>';


		});
		h[++idx] = '</tbody></table>';
		h[++idx] = '</div>';

		
		// Populate table
		$(this).append(h.join(''));
		
	};
		
})(jQuery);

/*
 * Generate a table with all the records for the selected form
 * @author	Neil Penman
 */
(function($) {
	$.fn.tableGenerator3 = function( options ) {
		
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
			
			var recCounter = 1;
				
			var tab = Array();
			var idx = -1;
			
			tab[++idx] = '<table name="' + settings.tableName + '">';
			// Process JSON
			var pkey="-1";
			$.each(settings.data.features, function(j, item) {

				if(recCounter == 1) {
					tab[++idx] = '<tr>';
					tab[++idx] = '<th class="' + settings.colClass + '">location</th>';
					$.each(item.properties, function(key, value) {
						if(key != "_table") {
							tab[++idx] = '<th class="' + settings.colClass + '">' + key + '</th>';
						}
					});
					tab[++idx] = '</tr>';
					
				}
				tab[++idx] = '<tr>';
				/*
				 * Write the location if it exists
				 */
				if(item.geometry) {
					tab[++idx] = '<td class="' + settings.colClass + '">' + item.geometry.type + '[' + item.geometry.coordinates + ']' + '</td>';
				} else {
					tab[++idx] = '<td class="' + settings.colClass + '"></td>';
				}
				$.each(item.properties, function(key, value) {

					if(key == "prikey") {
						pkey = value;
					}
					if(key != "_table") {
						value = addAnchors(value).join(',');
						var params = 'class="' + settings.colClass;
						if(key == "_bad") {
							if(value == "f") {
								params += ' good_r" pkey="' + pkey + '"';
							} else {
								params += ' bad_r" pkey="' + pkey + '"';
							}
							
						} else {
							params += '"';
						}
						tab[++idx] = '<td ' + params + '>' + value + '</td>';
					}
				});
				tab[++idx] = '</tr>';
				recCounter++;
			});
			tab[++idx] = '</table>';
			
			// Populate table
			$(this).append(tab.join(''));

		});
	};
		
})(jQuery);

/*
function addAnchors (property) {
	var output = property;
	
	if(property && (typeof property == "string") && (
			property.indexOf("//") === 0 ||
			property.indexOf("http") === 0)) { // Property is a URL
		var media = getMedia(property);
		
		if(media) {  // Add links to media files
			output = '<a href="' + media.url
				+ '" target="_blank"><img src="'
				+ media.thumbNail + '" alt="Picture"></a>';
	
		} else { 
			output = '<a href="' + property +  '" target="_blank">'+ property +'</a>';
		}
	}
	
	return output;
}
*/

/*
 * 
 *
function getMedia(property) {
	
	var media;
	
	if(property && (property.indexOf("//") === 0 || property.indexOf("http") === 0)) {  // Add links to media files
		var len = property.length;
		var idx;
		
		// Get name, type, urlBase and thumbName from the input url
		var name, ext, urlBase, thumbNail, baseName, flv, type;
		idx = property.lastIndexOf("/");
		name = property.substring(idx+1);
		urlBase = property.substring(0, idx+1);
		idx = name.lastIndexOf(".");
		ext = name.substring(idx+1);
		//baseName = name.substring(0, idx);
		//thumbNail = urlBase + "t." + baseName + ".jpg";
		thumbNail = urlBase + "thumbs/" + name + ".jpg";
		flv = urlBase + "flv/" + name + ".flv";
		
		// If this is a recognised media type then create a media object
		if(ext == "jpg") {
			type = "image";
		} else if(ext == "mp4" || ext == "3gp") {
			type = "video";
		} else {
			type = "unknown";
		}
		
		if(type != "unknown") { 
			media = new Object();
			media.name = name;
			media.thumbNail = thumbNail;
			media.type = type;	
			type == "image" ? media.url = property : media.url = flv; 
		} 
	}
	
	return media;
}
*/

/*
 * Generate media data
 * @author	Neil Penman
 */
(function($) {
	$.fn.mediaGeneratorSurvey = function( data ) {
		
		return this.each(function() {
			
			var html = [];
			var th = -1;
			var setSize = 4;
			var count = setSize;
				
			// Process JSON
			$.each(data, function(j, item) {	// Get the set of tables

				$.each(item, function(j, table) {	// Repeat for each table

					$.each(table.data.features, function(j, features) {	// Repeat for each record
						
						$.each(features.properties, function(key, value) {
							var media = getMedia(value);
							if(media) {
								var inSet = count % setSize;
								if(!inSet) {
									if(setSize != count) {
										html[++th] = '</div>';
									}
									html[++th] = '<div>';
								}
								html[++th] = '<img src="' + media.thumbNail + '" full="' + media.url + '" type="' + 
										media.type + '" alt="' + media.type + '" />';
								++count;
							}

						});
						

					});
				});
			});
			
			if(setSize != count) {
				html[++th] = '</div>';
			}
			
			// Populate table
			$(this).html(html.join(''));


		});
	};
		
})(jQuery);

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
			
			var recCounter = 1,
				surveyName,
				tab = [],
				idx = -1,
				addressParams,
				addressObj,
				addressParamsObj,
				currentTg = "-1",
				i,j,
				$this = $(this),
				assignmentStatus,
				showCompleted;
			
			$.each(settings.data.features, function(j, item) {
				surveyName = getProperty(item.properties, 'survey_name');
				assignmentStatus = getProperty(item.properties, 'assignment_status');
				thisTg = item.properties.task_group_id;
				if(currentTg !== thisTg) {
					currentTg = thisTg;
					if(recCounter > 1) {
						tab[++idx] = '</tbody></table>';
					}
					
					tab[++idx] = '<span class="task_group_name">';
					tab[++idx] = item.properties.task_group_name;
					tab[++idx] = '</span>';
					// Add a button to delete the task group
					tab[++idx] = '<span class="noPrint">';
					tab[++idx] = '<button class="add_new_task btn btn-success" value="';
					tab[++idx] = thisTg;
					tab[++idx] = '"><span class="glyphicon glyphicon-plus" aria-hidden="true"></span> Add Tasks</button>';
					tab[++idx] = '<button class="save_new_task btn btn-warning" style="display:none;" value="';
					tab[++idx] = thisTg;
					tab[++idx] = '"><span class="glyphicon glyphicon-save" aria-hidden="true"></span> Save Tasks</button>';
					tab[++idx] = '<button class="delete_task_group btn btn-danger" value="';
					tab[++idx] = thisTg;
					tab[++idx] = '"><span class="glyphicon glyphicon-trash" aria-hidden="true"></span> Delete Group</button>';
					tab[++idx] = '</span>';
					
					// Create the table
					tab[++idx] = '<table class="tasks table table-striped" id="tasktable';
					tab[++idx] = thisTg;
					tab[++idx] = '">';
					tab[++idx] = '<thead><tr>';
					tab[++idx] = '<th>Select</th><th>Task</th><th>location</th>';
					
					addressParams = settings.data.task_groups[currentTg].tg_address_params;
					$.each(item.properties, function(key, value) {
						if(includeKey(key)) {
							if(key === 'address') {
								addressParamsObj = $.parseJSON(addressParams);
								for(i = 0; i < addressParamsObj.length; i++) {
									if(addressParamsObj[i].selected) {
										tab[++idx] = '<th>' + addressParamsObj[i].name + '</th>';
									}
								}
							} else if(key === 'task_group_id' || key === 'task_group_name') {
								// ignore
							} else {
								tab[++idx] = '<th>' + key + '</th>';
							}
						}
					});
					tab[++idx] = '</tr></thead><tbody>';
					
				}
				
				// Check if we should show this record
				showCompleted = item.properties.task_id > 0 && (settings.showCompleted || assignmentStatus !== "submitted");
				if(showCompleted) {
					tab[++idx] = '<tr>';
					
					// Add select checkbox
					tab[++idx] = '<td class="control_td"><input class="select_row" type="checkbox" name="controls" data-taskid="';
					tab[++idx] = item.properties.task_id;
					tab[++idx] = '" data-assid="';
					tab[++idx] = item.properties.assignment_id;
					tab[++idx] = '" data-status="';
					tab[++idx] = item.properties.assignment_status;
					tab[++idx] = '"></td>';
					
					tab[++idx] = '<td>' + surveyName + '</td>';
					if(item.geometry) {
						tab[++idx] = '<td>' + item.geometry.type + '[' + item.geometry.coordinates + ']' + '</td>';
					} else {
						tab[++idx] = '<td></td>';
					}
					$.each(item.properties, function(key, value) {
	
						if(includeKey(key)) {
							
							if(key === 'address' && addressParamsObj) {
								addressObj = $.parseJSON(value);
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
							} else if(key === 'task_group_id' || key === 'task_group_name') {
								// ignore
							} else {
								value = addAnchors(value).join(',');							
								tab[++idx] = '<td>' + value + '</td>';
							}
						}
					});
					tab[++idx] = '</tr>';
				}
				recCounter++;
			});
			tab[++idx] = '</tbody></table>';
			
			// Populate table
			$this.append(tab.join(''));

		});
	};
		
})(jQuery);

function includeKey(key) {
	if(key === "_table" ||
			key === 'assignment_id' ||
			key === 'survey_name' ||
			key === 'user_id' ||
			key === 'user_ident' || 
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
