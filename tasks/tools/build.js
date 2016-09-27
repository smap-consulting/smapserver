({
    appDir: '../WebContent',
//    optimize: 'none',
    wrapShim: false,
    waitSeconds: 0,
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
     	i18n: '../../../../smapServer/WebContent/js/libs/i18n',
     	async: '../../../../smapServer/WebContent/js/libs/async',
     	localise: '../../../../smapServer/WebContent/js/app/localise',
    	jquery: '../../../../smapServer/WebContent/js/libs/jquery-2.1.1',
    	modernizr: '../../../../smapServer/WebContent/js/libs/modernizr',
    	common: '../../../../smapServer/WebContent/js/app/common',
    	globals: '../../../../smapServer/WebContent/js/app/globals',
    	mapbox_app: '../../../../smapServer/WebContent/js/app/mapbox_app',
    	tablesorter: '../../../../smapServer/WebContent/js/libs/tablesorter',
    	crf: '../../../../smapServer/WebContent/js/libs/commonReportFunctions',
    	lang_location: '../../../../smapServer/WebContent/js',
	datatables: '../../../../smapServer/WebContent/js/libs/DataTables/datatables',
	'datatables.net': '../../../../smapServer/WebContent/js/libs/DataTables/DataTables/js/jquery.dataTables',
	'datatables.net-bs': '../../../../smapServer/WebContent/js/libs/DataTables/DataTables/js/dataTables.bootstrap',
	'datatables.select': '../../../../smapServer/WebContent/js/libs/DataTables/Select/js/dataTables.select.min',
	bootstrap: '../../../../smapServer/WebContent/js/libs/bootstrap.min',
	toggle: '../../../../smapServer/WebContent/js/libs/bootstrap-toggle.min',
	qrcode: '../../../../smapServer/WebContent/js/libs/jquery-qrcode-0.14.0.min',
    	bootstrapfileinput: '../../../../smapServer/WebContent/js/libs/bootstrap.file-input',
	bootbox: '../../../../smapServer/WebContent/js/libs/bootbox.min',
	file_input: '../../../../smapServer/WebContent/js/libs/bootstrap.file-input',
	moment: '../../../../smapServer/WebContent/js/libs/moment-with-locales.min',
	datetimepicker: '../../../../smapServer/WebContent/js/libs/bootstrap-datetimepicker.min',
	rmm: '../../../../smapServer/WebContent/js/libs/responsivemobilemenu',

	mapbox: '../../../../smapServer/WebContent/js/libs/mapbox/js/mapbox',

	inspinia: '../../../../smapServer/WebContent/js/libs/wb/inspinia',
	jquery_ui: '../../../../smapServer/WebContent/js/libs/wb/jquery-ui-1.10.4.min',
	metismenu: '../../../../smapServer/WebContent/js/libs/wb/plugins/metisMenu/jquery.metisMenu',
	slimscroll: '../../../../smapServer/WebContent/js/libs/wb/plugins/slimscroll/jquery.slimscroll.min',
	pace: '../../../../smapServer/WebContent/js/libs/wb/plugins/pace/pace.min',
	footable: '../../../../smapServer/WebContent/js/libs/wb/plugins/footable/footable.all.min',
	peity: '../../../../smapServer/WebContent/js/libs/wb/plugins/peity/jquery.peity.min',
	icheck: '../../../../smapServer/WebContent/js/libs/wb/plugins/iCheck/icheck.min',
	calendar: '../../../../smapServer/WebContent/js/libs/wb/plugins/fullcalendar/fullcalendar.min',
    },
    dir: '../tasks',
    modules: [
        {
            name: '../taskManagement'
        },
        {
            name: '../managed_forms'
        },
        {
            name: '../action_forms'
        },
        {
            name: '../manage'
        },
        {
            name: '../log'
        }

    ]
})
