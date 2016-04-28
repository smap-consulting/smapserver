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
	$.fn.generateTaskGroupTable = function( options ) {
		
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
				i,
				$this = $(this),
				addressParams,
				addressObj,
				addressParamsObj,
				assignmentStatus,
				group;
			
			for(i = 0; i < settings.data.length; i++) {
				group = settings.data[i];
				tab[++idx] = '<tr>';
					tab[++idx] = addSelectCheckBox(true, group.tg_id, i == 0);
					if(i == 0) {
						globals.gCurrentTaskGroup = group.tg_id;
					}
					
					tab[++idx] = '<td>';
						tab[++idx] = group.name;
					tab[++idx] = '</td>';
					
					tab[++idx] = addPie(group.completeTasks, group.totalTasks);
					
					tab[++idx] = '<td>';
						tab[++idx] = group.totalTasks - group.completeTasks;
					tab[++idx] = '</td>';
				tab[++idx] = '</tr>';
			}
				
			
			// Populate table
			$this.append(tab.join(''));

		});
	};
		
})(jQuery);



function addSelectCheckBox(isRadio, val, checked) {
	
	var h  = [],
		idx = -1;
	
	h[++idx] = '<td><input type=';
	h[++idx] = isRadio ? '"radio"' : '"checkbox"';
	h[++idx] = 'name="taskgroup"';
	h[++idx] = ' class="taskgroup" value="';
	h[++idx] = val;
	h[++idx] = '"';
	if(checked) {
		h[++idx] = ' checked';
	}
	h[++idx] = '></td>';
	
	return h.join('');
	
}

function addPie(complete, total) {
	complete = complete || 0;
	total = total || 1;			// 1 is an arbitary total as 0 out of 0 does not render
	
	return '<td><span class="pie">' + complete + '/' + total + '</span></td>';
}
	




