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
"use strict";

define([
         'jquery',
         'foundation.min', 
         'modernizr',
         'app/localise',
         'app/globals'], 
		function($, foundation, modernizr, lang, globals) {

	return {
	
		init: init,
		setHtml: setHtml

	};
	



	function init() {
		
		var i;
		$('#upload_media_done').off().click(function(){
			
			var filepath = $('#file').val(),
				filename,
				idx = filepath.lastIndexOf('\\');
			
			if(idx > 0) {
				filename = filepath.substring(idx + 1);
			} else {
				filename = filepath;
			}
			// Add the new csv to the list
			var newCSV = {
					filename: filename,
					type: 'csv',
					qId: -1,
					oId: -1
			}
			
			globals.model.survey.surveyManifest.push(newCSV);
			setHtml('#csvList', globals.model.survey.surveyManifest);
			globals.model.settingsChange();
			$('#upload_media').foundation('reveal', 'close');
		
		});
		
		$('#add_csv').off().click(function() {	
			// Set the url to return to after submit a csv file to the server
			$('#original_url').val(window.location.href);
			$('#sId').val(globals.gCurrentSurvey);
			$('#upload_media').foundation('reveal', 'open');
		});


	}


	function setHtml(selector, csvList) {
		var h =[],
			idx = -1,
			i,
			filename,
			idx,
			value;
		
		for(i = 0; i < csvList.length; i++) {	

			value = csvList[i].value;
			filename = csvList[i].filename;
			
			
		
			h[++idx] = '<tr>';
				h[++idx] = '<td>';
				h[++idx] = '<a href=\"/media/"';
				h[++idx] = value;
				h[++idx] = '">';
				h[++idx] = filename;
				h[++idx] = '</a></td>';	
				h[++idx] = '<td><button value="';
				h[++idx] = globals.gCurrentSurvey;
				h[++idx] = ':-1:-1:';
				h[++idx] = csvList[i].text_id;
				h[++idx] = '</td>';	
				h[++idx] = '<td><button value="';
				h[++idx] = i;
				h[++idx] = '" class="csv_btn_rem">-</button></td>';
				
			h[++idx] = '</tr>';
		}

		$(selector).html(h.join(''));
		
		$('.csv_btn_rem').click(function () {
			var id = $(this).val();
			globals.model.survey.surveyManifest.splice(id,1);
			globals.model.settingsChange();
			setHtml('#csvList', globals.model.survey.surveyManifest);
		});
		
	}




});