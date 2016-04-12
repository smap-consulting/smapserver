({
    appDir: '../WebContent',
    optimize: 'none',
    wrapShim: false,
    waitSeconds: 0,
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
     	i18n: '../../../../smapServer/WebContent/js/libs/i18n',
     	async: '../../../../smapServer/WebContent/js/libs/async',
     	localise: '../../../../smapServer/WebContent/js/app/localise',
    	jquery: '../../../../smapServer/WebContent/js/libs/jquery-2.1.1',
    	jquery_ui: '../../../../smapServer/WebContent/js/libs/jquery-ui-1.10.3.custom.min',
    	modernizr: '../../../../smapServer/WebContent/js/libs/modernizr',
    	common: '../../../../smapServer/WebContent/js/app/common',
    	globals: '../../../../smapServer/WebContent/js/app/globals',
    	mapbox_app: '../../../../smapServer/WebContent/js/app/mapbox_app',
    	tablesorter: '../../../../smapServer/WebContent/js/libs/tablesorter',
    	crf: '../../../../smapServer/WebContent/js/libs/commonReportFunctions',
    	lang_location: '../../../../smapServer/WebContent/js',
	bootstrap: '../../../../smapServer/WebContent/js/libs/bootstrap.min',
    	bootstrapfileinput: '../../../../smapServer/WebContent/js/libs/bootstrap.file-input',
	bootbox: '../../../../smapServer/WebContent/js/libs/bootbox.min',
	file_input: '../../../../smapServer/WebContent/js/libs/bootstrap.file-input',
	moment: '../../../../smapServer/WebContent/js/libs/moment-with-locales.min',
	datetimepicker: '../../../../smapServer/WebContent/js/libs/bootstrap-datetimepicker.min',
	rmm: '../../../../smapServer/WebContent/js/libs/responsivemobilemenu',

	mapbox: '../../../../smapServer/WebContent/js/libs/mapbox/js/mapbox',

	inspinia: '../../../../smapServer/WebContent/js/libs/wb/inspinia',
	metismenu: '../../../../smapServer/WebContent/js/libs/wb/plugins/metisMenu/jquery.metisMenu',
	slimscroll: '../../../../smapServer/WebContent/js/libs/wb/plugins/slimscroll/jquery.slimscroll.min',
	pace: '../../../../smapServer/WebContent/js/libs/wb/plugins/pace/pace.min',
	peity: '../../../../smapServer/WebContent/js/libs/wb/plugins/peity/jquery.peity.min',
	icheck: '../../../../smapServer/WebContent/js/libs/wb/plugins/iCheck/icheck.min',
	calendar: '../../../../smapServer/WebContent/js/libs/wb/plugins/fullcalendar/fullcalendar.min',
    },
    dir: '../tasks',
    modules: [
        {
            name: '../manage'
        }

    ]
})
