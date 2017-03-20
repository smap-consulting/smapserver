/*
This file is part of SMAP.

SMAP is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
uSMAP is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

*/

/*
 * Functions for manipulating a question in the editor
 */

"use strict";

define([
         'jquery',
         'modernizr',
         'localise',
         'globals',
         'localise'], 
		function($, modernizr, lang, globals, localise) {

	
	return {	
		add: add,
		redraw: redraw
	};
	
	/*
	 * Add
	 */
	function add(chartId, chart, config, data, widthContainer, heightContainer) {
		initialiseMap(chartId + '_map', 1, true, undefined, undefined);
	}
	
	/*
	 * Update a map
	 */
	function redraw(chartId, chart, config, data, widthContainer, heightContainer) {
		
					
	}
	

});