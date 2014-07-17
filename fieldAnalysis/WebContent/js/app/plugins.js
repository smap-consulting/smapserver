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
 * Generate media data
 * @author	Neil Penman
 */
(function($) {
	$.fn.mediaGeneratorSurvey = function( data ) {
		
		return this.each(function() {
			
			var html = [],
				th = -1,
				setSize = 4,
				count = setSize,
				i;
			
			// Process JSON
			for(i = 0; i < data.length; i++) {	// Get the set of tables

				$.each(data[i].features, function(j, features) {	// Repeat for each record
					
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
			}
			
			if(setSize != count) {
				html[++th] = '</div>';
			}
			
			// Populate table
			$(this).html(html.join(''));


		});
	};
		
})(jQuery);


/*
 * Plugin to export html tables to csv
 * From: http://www.kunalbabre.com/projects/table2CSV.js
 */
jQuery.fn.table2CSV = function(options) {
    var options = jQuery.extend({
        separator: ',',
        header: [],
        delivery: 'popup' // popup, value
    },
    options);

    var csvData = [];
    var headerArr = [];
    var el = this;

    //header
    var numCols = options.header.length;
    var tmpRow = []; // construct header avalible array

    if (numCols > 0) {
        for (var i = 0; i < numCols; i++) {
            tmpRow[tmpRow.length] = formatData(options.header[i]);
        }
    } else {
        $(el).filter(':visible').find('th').each(function() {
            if ($(this).css('display') != 'none') tmpRow[tmpRow.length] = formatData($(this).html());
        });
    }

    row2CSV(tmpRow);

    // actual data
    $(el).find('tr').each(function() {
        var tmpRow = [];
        $(this).filter(':visible').find('td').each(function() {
            if ($(this).css('display') != 'none') tmpRow[tmpRow.length] = formatData($(this).html());
        });
        row2CSV(tmpRow);
    });
    if (options.delivery == 'popup') {
        var mydata = csvData.join('\n');
        return popup(mydata);
    } else {
        var mydata = csvData.join('\n');
        return mydata;
    }

    function row2CSV(tmpRow) {
        var tmp = tmpRow.join('') // to remove any blank rows
        // alert(tmp);
        if (tmpRow.length > 0 && tmp != '') {
            var mystr = tmpRow.join(options.separator);
            csvData[csvData.length] = mystr;
        }
    }
    function formatData(input) {
        // replace " with ""
        var regexp = new RegExp(/["]/g);
        var output = input.replace(regexp, '""');
        //HTML
        //var regexp = new RegExp(/\<[^\<]+\>/g);
        //var output = output.replace(regexp, "");
        if (output == "") return '';
        return '"' + output + '"';
    }
    function popup(data) {
        var generator = window.open('', 'csv', 'height=400,width=600');
        generator.document.write('<html><head><title>CSV</title>');
        generator.document.write('</head><body >');
        generator.document.write('<textArea cols=70 rows=15 wrap="off" >');
        generator.document.write(data);
        generator.document.write('</textArea>');
        generator.document.write('</body></html>');
        generator.document.close();
        return true;
    }
};


