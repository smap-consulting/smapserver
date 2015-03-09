/**
 * Default /webform
 */

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
	gUserLocale = localStorage.getItem('user_locale') || navigator.language;
} 

"use strict";
require.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    bootstrap: bootstrap.min,
    paths: {
    	app: '../app',
    	jquery: 'jquery-1.8.3.min',
    	bootbox: 'bootbox.min',
    	lang_location: '..'

    },
    shim: {
    	'app/common': ['jquery'],
        'bootstrap.min': ['jquery'],
        'jquery.autosize.min': ['jquery'],
        'jquery-drag-ui.min': ['jquery'],
        'bootstrap.file-input': ['bootstrap.min'],
    	'bootbox': ['bootstrap.min']
        
    }
});




//require( [ 'performance', 'gui', 'cache', 'store', 'file-manager', 'controller-webform', 'jquery' ],
//    function( monitor, gui, cache, recordStore, fileStore, controller, $ ) {

require( [ 'combined.min', 'jquery' ],
		function( enketo, $ ) {
        if ( !recordStore.isSupported() || !recordStore.isWritable() ) {
            window.location = settings[ 'modernBrowsersURL' ];
        } else if ( cache.requested() && !cache.activated() ) {
            gui.showCacheUnsupported();
        } else if ( cache.requested() ) {
            $( document ).trigger( 'browsersupport', 'offline-launch' );
        }

        $( document ).ready( function() {
            instanceStrToEdit = instanceStrToEdit || null; // Existing data
            controller.init( 'form.or:eq(0)', modelStr, instanceStrToEdit, {
                recordStore: recordStore,
                fileStore: fileStore,
                submitInterval: 300 * 1000
            } );
        } );
    } );

