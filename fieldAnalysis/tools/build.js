({
    appDir: '../WebContent',
    optimize: 'none',
    wrapShim: false,
    waitSeconds: 0,
    baseUrl: 'js/libs',
    removeCombined: true,
    paths: {
    	app: '../app',
    	main: '..',
     	i18n: '../../../../smapServer/WebContent/js/libs/i18n',
     	async: '../../../../smapServer/WebContent/js/libs/async',
     	localise: '../../../../smapServer/WebContent/js/app/localise',
    	jquery: '../../../../smapServer/WebContent/js/libs/jquery-2.1.1',
    	jquery_ui: '../../../../smapServer/WebContent/js/libs/jquery-ui-1.10.3.custom.min',
    	modernizr: '../../../../smapServer/WebContent/js/libs/modernizr',
    	rmm: '../../../../smapServer/WebContent/js/libs/responsivemobilemenu',
    	common: '../../../../smapServer/WebContent/js/app/common',
        moment: '../../../../smapServer/WebContent/js/libs/moment-with-locales.min',
    	version: '../../../../smapServer/WebContent/js/app/version',
    	pacesettings: '../../../../smapServer/WebContent/js/libs/paceSettings',
	pace: '../../../../smapServer/WebContent/js/libs/wb/plugins/pace/pace.min',
    	globals: '../../../../smapServer/WebContent/js/app/globals',
    	tablesorter: '../../../../smapServer/WebContent/js/libs/tablesorter',
    	crf: '../../../../smapServer/WebContent/js/libs/commonReportFunctions',
    	lang_location: '../../../../smapServer/WebContent/js'
    },
    dir: '../fieldAnalysis',
    modules: [
        {
            name: '../jqplot_main',
	    include: [
 		'jqplot/jquery.jqplot.min',
         	'jqplot/plugins/jqplot.highlighter',
         	'jqplot/plugins/jqplot.cursor',
         	'jqplot/plugins/jqplot.dateAxisRenderer',
         	'jqplot/plugins/jqplot.barRenderer',
         	'jqplot/plugins/jqplot.categoryAxisRenderer',
         	'jqplot/plugins/jqplot.canvasAxisLabelRenderer',
         	'jqplot/plugins/jqplot.canvasAxisTickRenderer',
         	'jqplot/plugins/jqplot.canvasTextRenderer',
         	'jqplot/plugins/jqplot.enhancedLegendRenderer'
		],
	    exclude: ['jquery', 'jquery_ui']
        },
        {
            name: '../dashboard_main',
	    exclude: ['../jqplot_main']
        },
        {
            name: '../reportlist_main'
        },
        {
            name: '../review_main'
        },
        {
            name: '../audit_main'
        },
        {
            name: '../table_reports_main'
        },
        {
            name: '../graph_reports_main'
//	    exclude: ['../jqplot_main']
        },
        {
            name: '../map_reports_main'
        }


    ]
})
