

define(['jquery', "i18n!../../../../js/nls/lang"], function($, lang) {
	
	localise = {	// Set global while migrating app to AMD
		setlang: function () {
	     
			$(".lang").each(function(index) {
				var $this = $(this);
				var code = $this.data("lang");
				if(code) {		
					$(this).html(lang[code]);
				}
			});
			console.log("lang: " + lang.m_home);
		},
		set: lang
	}
	
	return localise;
});