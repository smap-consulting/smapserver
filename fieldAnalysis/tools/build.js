({
    appDir: '../WebContent',
    mainConfigFile: '../WebContent/js/dashboard_main.js',
    optimize: "none",
    wrapShim: false,
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
     	i18n: '../../../../smapServer/WebContent/js/libs/i18n',
     	async: '../../../../smapServer/WebContent/js/libs/async',
     	localise: '../../../../smapServer/WebContent/js/app/localise',
    	jquery: '../../../../smapServer/WebContent/js/libs/jquery-1.8.3.min',
    	jquery_ui: '../../../../smapServer/WebContent/js/libs/jquery-ui-1.10.3.custom.min',
    	modernizr: '../../../../smapServer/WebContent/js/libs/modernizr',
    	rmm: '../../../../smapServer/WebContent/js/libs/responsivemobilemenu',
    	common: '../../../../smapServer/WebContent/js/app/common',
    	globals: '../../../../smapServer/WebContent/js/app/globals',
    	tablesorter: '../../../../smapServer/WebContent/js/libs/tablesorter',
    	crf: '../../../../smapServer/WebContent/js/libs/commonReportFunctions',
    	googlemaps: 'http://maps.google.com/maps/api/js?v=3.6&amp;sensor=false',
    	openlayers: '../../../../smapServer/WebContent/js/libs/OpenLayers/OpenLayers',
    	lang_location: '../../../../smapServer/WebContent/js'
    },
    dir: '../fieldAnalysis',
    modules: [
        {
            name: '../dashboard_main'
        },
        {
            name: '../reportlist_main'
        },
        {
            name: '../review_main'
        },
        {
            name: '../audit_main'
        }


    ]
})
