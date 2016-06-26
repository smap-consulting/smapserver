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

define(['jquery', 'i18n!lang_location/nls/lang'], function($, lang) {
	
	var dtLangFiles = {
			en: "",
			es: "/js/libs/DataTables.i18n/es.json",
			ar: "/js/libs/DataTables.i18n/ar.json",
			fr: "/js/libs/DataTables.i18n/fr.json",
			pt: "/js/libs/DataTables.i18n/pt.json",
			hi: "/js/libs/DataTables.i18n/hi.json"
	}
	
	window.localise = {	// Set global while migrating app to AMD
		
		
		setlang: function () {
			
			$(".lang").each(function(index) {
				var $this = $(this);
				var code = $this.data("lang");
				if(code) {		
					$this.html(lang[code]);
				}
			});
			if(typeof responsiveMobileMenu === "function") {
				rmmResizeLabels();		// Resize menus
			}
			
			// Set RTL
			//if(gUserLocale && gUserLocale === "ar") {
			//	$('html').attr("dir", "rtl").attr("lang", "ar");
			//	}
		},
		set: lang,
		dt: function() {
			return dtLangFiles[gUserLocale];
		}
	}
	
	return localise;
});