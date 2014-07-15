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

var gMarkerOptions = [{style:'diamond'}, {style:'x'}, {style:'circle'}, {style:'filledSquare'}];

function setGraph(data, chart, optionSelElement, pId) {
	
	var isVisible = true,
		$chartdiv,
		$pc,
		groups,
		fn,
		cols,
		pareto = [],
		grandTotal = [],
		matrix = [],
		i, j,
		$btnSelect,
		btns = [],
		id_name,
	    series = [],
    	matrix2 = [],
    	ticks = [],
    	val,
    	disp_desc,
    	isTimeSeries = false,
    	seriesObj = {},
    	ticksObj = {},
    	cMatrix = [],
    	idx = 0,
    	grpIdx,
    	seriesIdx = 0,
    	label,
    	maxTimeIdx = 0,
    	markerOptionIdx;

	groups = data.features;
	fn = data.fn;
	cols = data.cols;
	if(typeof data.interval !== "undefined") {
		isTimeSeries = true;
	}
	
	$chartdiv = $('#' + chart);
	$chartdiv.empty();	// Clear out the old graph
	
	$.jqplot.config.enablePlugins = true;
	
	/*
	 * Sort the data by size and create a matrix of the data
	 */
	seriesIdx = 0;
	if(isTimeSeries) {
		var groupName = "none";
		for(i = 0; i < cols.length; i++) {
			pareto[i] = i;
			
			for(j = 0; j < groups.length; j++) {
				//matrix[i].push([groups[j].properties.period, groups[j].properties[cols[i]]]);
				
				if(typeof groups[j].properties.group_name !== "undefined") {
					groupName = groups[j].properties.group_name;
				}
				if(typeof seriesObj[groupName] === "undefined") {	// Add the matrix for this group
					seriesObj[groupName] = [];
				}
				cMatrix = seriesObj[groupName];
				if(typeof cMatrix[i] === "undefined") {
					cMatrix[i] = [];
				}
				cMatrix[i].push([groups[j].properties.timeIdx+1, groups[j].properties[cols[i]]]);
				if(typeof groups[j].properties.period !== "undefined") {
					ticksObj[groups[j].properties.timeIdx] = groups[j].properties.period;
				}
			}

		}
		
		// Generate the ticks array
		var maxTimeIdx = data.maxTimeIdx;
		for(i = 0; i <= maxTimeIdx; i++) {
			if(typeof ticksObj[i] !== "undefined") {
				ticks.push(ticksObj[i]);
			} else {
				ticks.push(" ");
			}
		}
		
	} else {
		for(i = 0; i < cols.length; i++) {
			pareto[i] = i;
			grandTotal[i] = 0.0;
			matrix[i] = [];
			for(j = 0; j < groups.length; j++) {
				grandTotal[i] += groups[j].properties[cols[i]];
				matrix[i][j] = groups[j].properties[cols[i]];
				if(typeof groups[j].properties.group_label === "undefined") {
					ticks[j] = "";
				} else {
					ticks[j] = groups[j].properties.group_label;
				}
			}
		}
		pareto.sort(function(a,b) {return grandTotal[b] - grandTotal[a];});
	}

	
	/*
	 * Add buttons to select the options to include in the graph
	 * Ignore if an HTML element was not provided to contain these buttons
	 */
	if(typeof optionSelElement != "undefined") {
		/*
		 * The optionIdx will be undefined for a new question
		 * Regenerate the option buttons 
		 */
		//$btnSelect = $('#mDataOptions' + pId);
		$btnSelect = $('#' + optionSelElement);
		if(typeof data.optionIdx === "undefined") {
			
			// Add any data series to the options pane
			j = -1;
			if(cols) {
				id_name = "btnsel" + pId +"_";
				for(i = 0; i < pareto.length; i++) {		// Add in descending order of series total value
	
					btns[++j] = '<input type="checkbox" id="'; 
					btns[++j] = id_name + i;
					btns[++j] = '" name="';
					btns[++j] = id_name;
					btns[++j] = '" value="';
					btns[++j] = pareto[i] + '"';

					if(!isTimeSeries) {
						if(grandTotal[pareto[i]] !== 0) {
							btns[++j] = ' checked="checked"';	// for the graph check non zero by default
						}
					} else {
						btns[++j] = ' checked="checked"';	
					}
					btns[++j] = '/><label for="';
					btns[++j] = id_name + i;
					
					btns[++j] = '">' + cols[pareto[i]];
					
					btns[++j] =	 '</label><br/>';
				}
				$btnSelect.empty().append(btns.join(''));
			
				$btnSelect.find('input').change(function() {
					data.optionIdx = $(this).val();
					setGraph(data, chart, optionSelElement, pId);		// Regenerate graph, this time the index will not be undefined
				});
			}
		}
	}
	
	// Remove rows from the matrix that have not been selected by the user
	if(isTimeSeries) {
		// Convert the matrix per group_by into a single matrix of data
		idx = 0;
		grpIdx = 0;
		var g;
		for(g in seriesObj) {
			cMatrix = seriesObj[g];

			if(typeof optionSelElement != "undefined") {
				$btnSelect.find(':checked').each(function(index,value) {
					val = $(this).val();
					matrix2[idx] = seriesObj[g][val];
					if(g === "none") {
						label = cols[val];
					} else {
						label = g + " : " + cols[val];
					}
	
					markerOptionIdx = grpIdx % gMarkerOptions.length;
					series[idx++] = {
							label: label,
							lineWidth:2,
							markerOptions: gMarkerOptions[markerOptionIdx]
					};
				});
			} else {
				for(i = 0; i < cMatrix.length; i++) {
					matrix2[i] = cMatrix[i];
					if(g === "none") {
						label = cols[i];
					} else {
						label = g + " : " + cols[i];
					}
	
					markerOptionIdx = grpIdx % gMarkerOptions.length;
					series[i] = {
							label: label,
							lineWidth:2,
							markerOptions: gMarkerOptions[markerOptionIdx]
					};
				}
			}
				
			grpIdx++;
		}

	} else {
		if(typeof optionSelElement != "undefined") {
			$btnSelect.find(':checked').each(function(index,value) {
				val = $(this).val();
				matrix2[index] = matrix[val];
				series[index] = {label: cols[val]};
			});
		} else {
			for(i = 0; i < matrix.length; i++) {
				matrix2[i] = matrix[pareto[i]];
				series[i] = {label: cols[pareto[i]]};
			}
		}
	}
	
	console.log("Graph description");
	console.log(data);
	disp_desc = getDisplayDescription(fn, "graph", data.survey, data.question, data.group, undefined, data.qtype, 
			data.date_question, data.start, data.end, data.interval, data.units, data.filter);
	$('#p' + pId).find('.r_description').html(disp_desc);
	data.caption = disp_desc;
	
	if(matrix2 && matrix2.length > 0) {
		if(isTimeSeries) {
			ts_graph(ticks, matrix2, series, chart, data, pId);
		} else {
			bar_graph(ticks, matrix2, series, chart, data, pId);
		}
	} else {
		$('#graph_panel' + pId + ' h3').html("No data available");
	}

}

/*
 * Show the bar graph
 */
function bar_graph(ticks, matrix, series, chart, data, pId) {
	var i,
		plot,
		yaxislabel;
	
	// Truncate tick labels for readability
	for(i = 0; i < ticks.length; i++) {
		if(ticks[i].length > 32) {
			ticks[i] = ticks[i].substring(0,32);
		}
	}
	

	// If there are no tick values ensure nothing gets displayed
	if(ticks.length === 1 && ticks[0] === "") {
		ticks[0] = " ";		// JQPlot converts a zero length string into "1" so create a string containing a space
	}
	
	if(data.units) {
		yaxislabel = data.fn + " (" + data.units + ")";
	} else {
		yaxislabel = data.fn;
	}
	
    plot = $.jqplot(chart, matrix, {
        seriesDefaults: {
        	 renderer:$.jqplot.BarRenderer,
             rendererOptions: {fillToZero: true}
        },
    	series: series,
		legend: {
            show: true,
            placement: 'insideGrid',
            rendererOptions: {
            	   fontSize: "12pt"
            }
		}, 
        axes: {
          xaxis: {
            renderer: $.jqplot.CategoryAxisRenderer,
            labelRenderer: $.jqplot.CanvasAxisLabelRenderer,
            tickRenderer: $.jqplot.CanvasAxisTickRenderer,
            ticks: ticks,
            label: data.group,
            tickOptions: {
                angle: -30,
                fontSize: '12pt'
            }          
          },
          yaxis: {
            label: yaxislabel,
            labelRenderer: $.jqplot.CanvasAxisLabelRenderer,
            pad: 1.05,
            tickOptions: {
                fontSize: '12pt'
            }  
          }

        },
        highlighter: {
      	  show: true,
      	  tooltipContentEditor: tooltipContentEditor,
      	  sizeAdjust: 5
        }
    });
    
    function tooltipContentEditor(str, seriesIndex, pointIndex) {
    	return plot.series[seriesIndex]["label"] + ", " + plot.data[seriesIndex][pointIndex];
    }
 
	// Replot on resize events
	function replot() {
		plot.replot( { resetAxes: true, barMargin: 8 } );
	}
	
	$('#graph_panel' + pId).bind('resized', function() {
		replot();
	});
	
	$('.graphLabel').off().click(function() {	// Show / hide graph labels
		graphLabel($(this));
	});
	
}

/*
 * Show the time series
 */
function ts_graph(ticks, matrix, series, chart, data, pId) {
	var i,
		plot;
	
	// Truncate tick labels for readability
	for(i = 0; i < ticks.length; i++) {
		if(ticks[i].length > 32) {
			ticks[i] = ticks[i].substring(0,32);
		}
	}
	
	// If there are no tick values ensure nothing gets displayed
	if(ticks.length === 1 && ticks[0] === "") {
		ticks[0] = " ";		// JQPlot converts a zero length string into "1" so create a string containing a space
	}
	
    plot = $.jqplot(chart, matrix, {
    	series: series,
		legend: {
            show: true,
            placement: 'insideGrid',
            rendererOptions: {
            	   fontSize: "12pt"
            }
		}, 
        axes: {
          xaxis: {
            renderer: $.jqplot.CategoryAxisRenderer,
            labelRenderer: $.jqplot.CanvasAxisLabelRenderer,
            tickRenderer: $.jqplot.CanvasAxisTickRenderer,
            ticks: ticks,
            label: data.interval,
            tickOptions: {
                angle: -30,
                fontSize: '12pt'
            }          
          },
          yaxis: {
            label: data.fn,
            labelRenderer: $.jqplot.CanvasAxisLabelRenderer,
            pad: 1.05,
            tickOptions: {
                fontSize: '12pt'
            }  
          }

        },
        highlighter: {
      	  show: true,
      	  tooltipContentEditor: tooltipContentEditor,
      	  sizeAdjust: 5
        }
    });
    
    function tooltipContentEditor(str, seriesIndex, pointIndex) {
    	return plot.series[seriesIndex]["label"] + ", " + plot.data[seriesIndex][pointIndex];
    }
 
	// Replot on resize events
	function replot() {
		plot.replot( { resetAxes: true, barMargin: 8 } );
	}
	
	$('#graph_panel' + pId).bind('resized', function() {
		replot();
	});
	
	$('.graphLabel').off().click(function() {	// Show / hide graph labels
		graphLabel($(this));
	});
	
}	

function graphLabel($elem) {
	var $legend = $elem.closest('.pContent,.rep_content').find('.jqplot-table-legend');
	$legend.toggle();
}
