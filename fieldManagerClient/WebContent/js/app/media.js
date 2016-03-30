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

$(document).ready(function() {
	
	var location,
		idx;

	location = String(document.location);

	// remove any messages from the original url
	idx = location.indexOf("&mesg");
	if(idx > 0) {
		location = location.substring(0, idx);
	}
	
	// Add button to add csv media
	$('#media_btn_add').button().click(function () {
		$('#media_keys').val($(this).val());
		$('#original_url').val(location);

		$('#add_media').dialog("open");
	});
	
	// Add buttons to add media
	$('.media_btn_add').click(function () {
		$('#media_keys').val($(this).val());
		$('#original_url').val(location);

		$('#add_media').dialog("open");
	});
	
	// Add buttons to remove media
	$('.media_btn_rem').click(function () {
		var media_keys = String($(this).val()),
			params = [],
			sId,
			qId,
			oId,
			text_id;
		
		addHourglass();

		params = media_keys.split(":");
		if(params.length > 1) {
			sId = params[0];
			qId = params[1];
			
			if(params.length > 2) {
				oId = params[2];
			} else {
				oId = -1;
			}
			
			if(params.length > 3) {
				text_id = params[3];
			}
		
			$.ajax({
				  type: "POST",
				  url: "/surveyKPI/survey/" + sId + "/remove_media",
				  data: { qId: qId, oId: oId, text_id: text_id },
				  success: function(data, status) {
					  removeHourglass();
					  window.location.reload(true);
				  }, error: function(data, status) {
					  removeHourglass();
					  alert("Failed to delete media");
				  }
			});
		} else {
			console.log("Error in removing media " + media_keys);
		}
	});
	
	
	$('#add_media').dialog(
		{
			autoOpen: false, closeOnEscape:true, draggable:true, modal:true,
			width:600,
			show:"drop",
			buttons: [
	          {
		        	text: "Cancel",
		        	click: function() {
		        		$(this).dialog("close");
		        	}
		        },
		        {
		        	text: "Submit",
		        	click: function() {
		        		addHourglass();
		        		document.add_media_form.submit();
		        		$(this).dialog("close");
		        	}
		        }
			]
		}
	);
	
});

