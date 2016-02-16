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
    	jquery: '../../../../smapServer/WebContent/js/libs/jquery-1.8.3.min',
    	jquery_ui: '../../../../smapServer/WebContent/js/libs/jquery-ui-1.10.3.custom.min',
    	modernizr: '../../../../smapServer/WebContent/js/libs/modernizr',
    	common: '../../../../smapServer/WebContent/js/app/common',
    	globals: '../../../../smapServer/WebContent/js/app/globals',
    	tablesorter: '../../../../smapServer/WebContent/js/libs/tablesorter',
    	crf: '../../../../smapServer/WebContent/js/libs/commonReportFunctions',
    	openlayers: '../../../../smapServer/WebContent/js/libs/OpenLayers/OpenLayers',
    	lang_location: '../../../../smapServer/WebContent/js',
	bootstrap: '../../../../smapServer/WebContent/js/libs/bootstrap.min',
    	bootstrapfileinput: '../../../../smapServer/WebContent/js/libs/bootstrap.file-input',
	bootbox: '../../../../smapServer/WebContent/js/libs/bootbox.min',
	file_input: '../../../../smapServer/WebContent/js/libs/bootstrap.file-input',
	moment: '../../../../smapServer/WebContent/js/libs/moment-with-locales.min',
	datetimepicker: '../../../../smapServer/WebContent/js/libs/bootstrap-datetimepicker.min',
	rmm: '../../../../smapServer/WebContent/js/libs/responsivemobilemenu'
    },
    dir: '../fieldManager',
    modules: [
        {
            name: '../templatemanagement_main',
        },
        {
            name: '../usermanagement_main',
        },
        {
            name: '../assignments_main',
        },
        {
            name: '../monitor_main',
        },
        {
            name: '../notifications_main',
        }

    ]
})
