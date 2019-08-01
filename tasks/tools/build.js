({
    appDir: '../WebContent',
    locale: "en",
    optimize: 'none',
    wrapShim: false,
    waitSeconds: 60,
    baseUrl: 'js/libs',
    paths: {
    	app: '../app',
     	jquery: '../../../../smapServer/WebContent/js/libs/jquery-2.1.1',
     	i18n: '../../../../smapServer/WebContent/js/libs/i18n',
     	async: '../../../../smapServer/WebContent/js/libs/async',
     	localise: '../../../../smapServer/WebContent/js/app/localise',
    	modernizr: '../../../../smapServer/WebContent/js/libs/modernizr',
    	common: '../../../../smapServer/WebContent/js/app/common',
    	globals: '../../../../smapServer/WebContent/js/app/globals',
    	mapbox_app: '../../../../smapServer/WebContent/js/app/mapbox_app',
    	tablesorter: '../../../../smapServer/WebContent/js/libs/tablesorter',
    	crf: '../../../../smapServer/WebContent/js/libs/commonReportFunctions',
    	lang_location: '../../../../smapServer/WebContent/js',
	datatables: '../../../../smapServer/WebContent/js/libs/DataTables/datatables.min',
	datatables_b4: '../../../../smapServer/WebContent/js/libs/datatables.b4.min',
	'datatables.net': '../../../../smapServer/WebContent/js/libs/DataTables/DataTables/js/jquery.dataTables',
	'datatables.net-bs': '../../../../smapServer/WebContent/js/libs/DataTables/DataTables/js/dataTables.bootstrap',
	'datatables.select': '../../../../smapServer/WebContent/js/libs/DataTables/Select/js/dataTables.select.min',
	'datatables.select.v1.3.0': '../../../../smapServer/WebContent/js/libs/DataTables/Select/js/dataTables.select.v1.3.0.min',
	toggle: '../../../../smapServer/WebContent/js/libs/bootstrap-toggle.min',
	qrcode: '../../../../smapServer/WebContent/js/libs/jquery-qrcode-0.14.0.min',
	svgsave: '../../../../smapServer/WebContent/js/libs/saveSvgAsPng',
	popper: '../../../../smapServer/WebContent/js/libs/popper.v1.12.9.min',
	bootstrap_v4: '../../../../smapServer/WebContent/js/libs/bootstrap.bundle.v4.min',
	bootstrap: '../../../../smapServer/WebContent/js/libs/bootstrap.min',
    	bootstrapfileinput: '../../../../smapServer/WebContent/js/libs/bootstrap.file-input',
	bootbox: '../../../../smapServer/WebContent/js/libs/bootbox.min',
	file_input: '../../../../smapServer/WebContent/js/libs/bootstrap.file-input',
	moment: '../../../../smapServer/WebContent/js/libs/moment-with-locales.2.24.0',
	datetimepicker: '../../../../smapServer/WebContent/js/libs/bootstrap-datetimepicker-4.17.47',
	rmm: '../../../../smapServer/WebContent/js/libs/responsivemobilemenu',
	d3: '../../../../smapServer/WebContent/js/libs/d3.v4',
        d3v3: '../../../../smapServer/WebContent/js/libs/d3.v3.min',
        //d3cloud: '../../../../smapServer/WebContent/js/libs/d3.layout.cloud',
        d3cloud2: '../../../../smapServer/WebContent/js/libs/d3.layout.cloud2',
        multiselect: '../../../../smapServer/WebContent/js/libs/bootstrap-multiselect',
        knockout: '../../../../smapServer/WebContent/js/libs/knockout',

	mapbox: '../../../../smapServer/WebContent/js/libs/mapbox/js/mapbox',

	inspinia: '../../../../smapServer/WebContent/js/libs/wb/inspinia.v2.9.2',
	'wb-setup': '../../../../smapServer/WebContent/js/libs/wb/wb-setup',
	jquery_ui: '../../../../smapServer/WebContent/js/libs/wb/jquery-ui-1.10.4.min',
	metismenu: '../../../../smapServer/WebContent/js/libs/wb/plugins/metisMenu/jquery.metisMenu',
	metismenu_v4: '../../../../smapServer/WebContent/js/libs/wb/plugins/metisMenu/jquery.metisMenu.2.7.7',
	slimscroll: '../../../../smapServer/WebContent/js/libs/wb/plugins/slimscroll/jquery.slimscroll.min',
	sweetalert: '../../../../smapServer/WebContent/js/libs/wb/plugins/sweetalert/sweetalert.min',
	pace: '../../../../smapServer/WebContent/js/libs/wb/plugins/pace/pace.min',
	footable: '../../../../smapServer/WebContent/js/libs/wb/plugins/footable/footable.all.min',
	peity: '../../../../smapServer/WebContent/js/libs/wb/plugins/peity/jquery.peity.min',
	icheck: '../../../../smapServer/WebContent/js/libs/wb/plugins/iCheck/icheck.min',
	calendar: '../../../../smapServer/WebContent/js/libs/wb/plugins/fullcalendar/fullcalendar.min',
	es: '../../../../smapServer/WebContent/js/libs/wb/plugins/fullcalendar/es'
    },
    dir: '../tasks',
    modules: [
        {
            name: '../taskManagement',
	    exclude: ['jquery', 'bootstrap']
        },
        {
            name: '../managed_forms',
	    exclude: ['jquery', 'bootstrap', 'datatables_b4']
        },
        {
            name: '../action_forms'
        },
        {
            name: '../manage',
	    exclude: ['jquery', 'bootstrap']
        },
        {
            name: '../log',
	    exclude: ['jquery', 'bootstrap', 'datatables_b4']
        }

    ]
})
