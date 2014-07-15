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

function setGraphSurvey(view) {
	
	var isVisible = true,
		$pc,
		$chartdiv;
	
	$chartdiv = $('#chartdiv' + view.pId);
	
	$panel = $('#p' + view.pId);
	
	$.jqplot.config.enablePlugins = true;

	// Make the container visible temporarily in case it has been hidden
	$pc = $('#panel-container');
	if(!$pc.is(':visible')) {
		isVisible = false;
		$pc.show();
	}
	
	$chartdiv.empty();	// Clear out the old graph
	$('#graph_panel' + view.pId + ' h3').html("Select a question to view graph");
	
	// Hide the container if it wasn't originally visible
	if(!isVisible) {
		$pc.hide();
	}

}

function newSetGraphQuestion(view) {
	
	var isVisible = true,
		$chartdiv,
		chartelem,
		btnSelElem,
		$pc;

	
	// Make the container visible temporarily in case it has been hidden
	$pc = $('#panel-container');
	if(!$pc.is(':visible')) {
		isVisible = false;
		$pc.show();
	}
	
	chartelem = 'chartdiv' + view.pId;
	btnSelElem = 'mDataOptions' + view.pId;
	$chartdiv = $('#' + chartelem);
	
	setGraph(view.results[0], chartelem, btnSelElem, view.pId);
	
	// Hide the container if it wasn't originally visible
	if(!isVisible) {
		$pc.hide();
	}

}


