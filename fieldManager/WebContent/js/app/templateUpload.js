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

define(['jquery', "i18n!../../../../js/nls/lang",'mustache','jquery_ui', 'localise'], function($, lang, Mustache, lang) {
	
	$(document).ready(function() {
		
		localise.setlang();		// Localise HTML
		
		$('#msg_locn').html(Mustache.to_html( $('#tpl').html(), msg));
		var sendto = administrator || '';
			$('#email_button').attr("href", "mailto:" + administrator + "?subject=Error loading template&body=" + msgToText(msg));
			$('.abutton').button();
	});
		
	function msgToText(msg) {
		var nl = escape('\r'),
			tab = escape('        '),
			msgtext = msg.mesg + nl;
		
		for(i = 0; msg.hints && i < msg.hints.length; i++) {
			msgtext += tab + msg.hints[i] + nl;
		}
		msgtext += nl;
		msgtext += 'Host: ' + msg.host + nl;
		msgtext += 'Project: ' + msg.project + nl;
		msgtext += 'Survey: ' + msg.survey + nl;
		msgtext += 'FileName: ' + msg.fileName + nl;
		
		msgtext += nl + 'Attach template to email';
		
		return msgtext;
	}
});

