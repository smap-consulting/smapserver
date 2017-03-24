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

/*
 * Purpose: Manage the panels that display graphs, maps etc of results data
 */

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
    gUserLocale = localStorage.getItem('user_locale') || navigator.language;
}

"use strict";
requirejs.config({
    baseUrl: 'js/libs',
    locale: gUserLocale,
    waitSeconds: 0,
    paths: {
        app: '../app',
        i18n: '../../../../js/libs/i18n',
        async: '../../../../js/libs/async',
        localise: '../../../../js/app/localise',
        jquery: '../../../../js/libs/jquery-2.1.1',
        modernizr: '../../../../js/libs/modernizr',
        common: '../../../../js/app/common',
        globals: '../../../../js/app/globals',
        bootstrap: '../../../../js/libs/bootstrap.min',
        crf: '../../../../js/libs/commonReportFunctions',
        toggle: 'bootstrap-toggle.min',
        lang_location: '../../../../js',
        file_input: '../../../../js/libs/bootstrap.file-input',
        mapbox: '../../../../js/libs/mapbox/js/mapbox',
        mapbox_app: '../../../../js/app/mapbox_app',
        datetimepicker: '../../../../js/libs/bootstrap-datetimepicker.min',
        datatables: '../../../../js/libs/DataTables/datatables',
        'datatables.net': '../../../../js/libs/DataTables/DataTables/js/datatables.net',
        'datatables.net-bs': '../../../../js/libs/DataTables/DataTables/js/datatables.bootstrap',
        'datatables.select': '../../../../js/libs/DataTables/Select/js/dataTables.select.min',
        icheck: '../../../../js/libs/wb/plugins/iCheck/icheck.min',
        inspinia: '../../../../js/libs/wb/inspinia',
        svgsave: '../../../../js/libs/saveSvgAsPng',
        metismenu: '../../../../js/libs/wb/plugins/metisMenu/jquery.metisMenu',
        slimscroll: '../../../../js/libs/wb/plugins/slimscroll/jquery.slimscroll.min',
        pace: '../../../../js/libs/wb/plugins/pace/pace.min',
        qrcode: '../../../../js/libs/jquery-qrcode-0.14.0.min'

    },
    shim: {

        'common': ['jquery'],
        'datetimepicker': ['moment'],
        'bootstrap': ['jquery'],
        'app/plugins': ['jquery'],
        'crf': ['jquery'],
        'file_input': ['jquery'],
        'inspinia': ['jquery'],
        'metismenu': ['jquery'],
        'icheck': ['jquery'],
        'slimscroll': ['jquery'],
        'datatables': ['jquery', 'bootstrap'],
        'app/common_mgmt': ['jquery'],
        'app/chart': ['jquery'],
        'qrcode': ['jquery'],
        'toggle': ['bootstrap.min'],

        'mapbox_app': ['jquery', 'mapbox'],
        'mapbox': {
            exports: 'L'
        },

    }
});

require([
    'jquery',
    'bootstrap',
    'common',
    'mapbox',
    'mapbox_app',
    'localise',
    'globals',
    'moment',
    'app/chart',
    'app/map',
    'datatables.net-bs',
    'svgsave',
    'datatables.select',
    'inspinia',
    'metismenu',
    'slimscroll',
    'pace',
    'datetimepicker',
    'icheck',
    'crf',
    'app/common_mgmt',
    'qrcode',
    'd3',
    'toggle'

], function ($,
             bootstrap,
             common,
             mapbox,
             mapbox_app,
             localise,
             globals,
             moment,
             chart,
             map,
             datatables,
             svgsave) {

    var gMapLayersShown = false;

    /*
     * Report definition
     * Default Settings
     *    Create a chart for data table columns that are enabled and do not have column specific setting
     * Column specific settings
     *    Override settings where names match
     */

    $(document).ready(function () {

        var i,
            params,
            pArray = [],
            param = [],
            openingNew = false,
            dont_get_current_survey = true,
            bs = isBusinessServer();

        window.chart = chart;
        chart.init();
        window.moment = moment;
        localise.setlang();		// Localise HTML

        // Get the parameters and show a management survey if required
        params = location.search.substr(location.search.indexOf("?") + 1)
        pArray = params.split("&");
        dont_get_current_survey = false;
        for (i = 0; i < pArray.length; i++) {
            param = pArray[i].split("=");
            if (param[0] === "id") {
                dont_get_current_survey = true;		// Use the passed in survey id
                globals.gCurrentSurvey = param[1];
                saveCurrentProject(-1, globals.gCurrentSurvey);	// Save the current survey id
            } else if (param[0] === "new") {
                dont_get_current_survey = true;		// Don't set the current survey from the users defaults
                globals.gCurrentSurvey = -1;
                // TODO display list of
            }
        }

        // Get the user details
        globals.gIsAdministrator = false;
        getLoggedInUser(refreshData, false, true, undefined, false, dont_get_current_survey);

        enableUserProfileBS();										// Enable user profile button

        // Get the report definition

        // Set change function on projects
        $('#project_name').change(function () {
            projectChanged();
        });

        // Set change function on survey
        $('#survey_name').change(function () {
            gTasks.gSelectedSurveyIndex = $(this).val();
            globals.gCurrentSurvey = gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].id;
            surveyChanged();
        });

        /*
         * Setup dialog to change the current survey
         */
        $("#changeSurveys").click(function () {
            $("#surveySelect").modal("show");
        });

        /*
         * Set up dialog to edit a record
         */
        $('#editRecord').on('show.bs.modal', function (event) {
            var
                record = gTasks.gSelectedRecord,
                columns = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns,
                $editForm = $('#editRecordForm'),
                $surveyForm = $('#surveyForm');

            $('.shareRecordOnly, .role_select').hide();
            $('#srLink').val("");
            if (globals.gIsSecurityAdministrator) {
                getSurveyRoles(globals.gCurrentSurvey);
            }
            showEditRecordForm(record, columns, $editForm, $surveyForm);

        });

        $('#saveRecord').click(function () {
            var saveString = JSON.stringify(gTasks.gUpdate);
            addHourglass();
            $.ajax({
                type: "POST",
                dataType: 'text',
                contentType: "application/json",
                cache: false,
                url: "/surveyKPI/managed/update/" + globals.gCurrentSurvey + "/" + gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].managed_id,
                data: {settings: saveString},
                success: function (data, status) {
                    removeHourglass();
                    globals.gMainTable.ajax.reload();
                }, error: function (data, status) {
                    removeHourglass();
                    alert(data.responseText);
                }
            });
        });

        $('#shareRecord').click(function () {
            $('.shareRecordOnly').toggle();
            // Automatically get the link if there are no roles to select
            if ($('.role_select_roles').text().length === 0) {
                $("#getSharedRecord").trigger("click");
            }
        });

        $('#getSharedRecord').click(function () {

            var url = "/surveyKPI/managed/actionlink/" +
                globals.gCurrentSurvey + "/" +
                gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].managed_id + "/" +
                gTasks.gPriKey;

            if (globals.gIsSecurityAdministrator) {
                var roleIds = [],
                    id;
                $('input[type=checkbox]:checked', '.role_select_roles').each(function () {
                    id = $(this).val();
                    roleIds.push(id);
                });
                if (roleIds.length > 0) {
                    url += "?roles=" + roleIds.join();
                }
            }

            addHourglass();
            $.ajax({
                url: url,
                dataType: 'json',
                cache: false,
                success: function (data) {

                    removeHourglass();
                    $('#srLink').val(data.link);
                },
                error: function (xhr, textStatus, err) {
                    removeHourglass();
                    if (xhr.readyState == 0 || xhr.status == 0) {
                        return;  // Not an error
                    } else {
                        console.log("Error: Failed to get sharing link: " + err);
                    }
                }
            });
        });

        // Save changes to the table columns that are shown
        $('#applyColumns').click(function () {

            var
                config = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex],
                $this;

            $('input', '#tab-columns-content').each(function (index) {
                $this = $(this);
                config.columns[index + 1].hide = !$this.is(':checked');		// Ignore prikey

            });

            updateVisibleColumns(config.columns);
            saveConfig(config);
            chart.setChartList();
            chart.refreshCharts();

        });

        // Save changes to the barcodes that are shown
        $('#applyBarcodes').click(function () {

            var
                config = gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex],
                $this;

            $('input', '#tab-barcode-content').each(function (index) {
                $this = $(this);
                config.columns[index + 1].barcode = $this.is(':checked');		// Ignore prikey

            });
            globals.gMainTable.ajax.reload(); // redraw
            saveConfig(config);

        });

        // Refresh menu
        $('#m_refresh').click(function () {
            refreshData();
        });

        /*
         * Set up the action menu functions
         */
        $('#getRelated').click(function () {

            var masterRecord = getSelectedRecord();

            if (typeof masterRecord != "undefined") {
                // 1. Hide results other than this primary result
                showManagedData(globals.gCurrentSurvey, '#content', masterRecord);

                // 2. Get related surveys and show it
                getRelatedList(globals.gCurrentSurvey, masterRecord);
            }
        });

        /*
         * Dashboard menus
         */
        $('#m_map_view').click(function () {
            map.init();
            if (gMapLayersShown) {
                $('.main_content').removeClass("col-md-12").addClass("col-md-8");
                $('.map_layers').show();
            } else {
                $('.main_content').removeClass("col-md-8").addClass("col-md-12");
                $('.map_layers').hide();
            }
            $('#showlayers').show();
            $('#table_content').hide();
            $('#map_content').show();
        });
        $('#m_table_view').click(function () {
            $('#showlayers').hide();
            $('.main_content').removeClass("col-md-8").addClass("col-md-12");
            $('.map_layers').hide();
            $('#table_content').show();
            $('#map_content').hide();
        });

        // Add a new chart
        $('#m_add_chart').click(function () {
            chart.addNewChart();
        });


    });

    /*
     * Get the roles for a survey
     */
    function getSurveyRoles(sId) {

        if (!gTasks.cache.surveyRoles[sId]) {
            addHourglass();
            $.ajax({
                url: "/surveyKPI/role/survey/" + sId + "?enabled=true",
                dataType: 'json',
                cache: false,
                success: function (data) {

                    gTasks.cache.surveyRoles[sId] = data;
                    showRoles(gTasks.cache.surveyRoles[sId]);
                },
                error: function (xhr, textStatus, err) {

                    removeHourglass();
                    if (xhr.readyState == 0 || xhr.status == 0) {
                        return;  // Not an error
                    } else {
                        console.log("Error: Failed to get roles for a survey: " + err);
                    }
                }
            });
        } else {
            showRoles(gTasks.cache.surveyRoles[sId]);
        }
    }

    /*
     * Show the roles
     */
    function showRoles(data) {

        var h = [],
            idx = -1,
            i;

        if (data.length > 0) {
            for (i = 0; i < data.length; i++) {
                h[++idx] = '<div class="checkbox">';
                h[++idx] = '<label><input type="checkbox" value="';
                h[++idx] = data[i].id;
                h[++idx] = '">';
                h[++idx] = data[i].name;
                h[++idx] = '</label>';
                h[++idx] = '</div>';
            }
            $('.role_select').show();
            $('.role_select_roles').empty().append(h.join(''));
        }
    }

    /*
     * Respond to a request to generate a file
     */
    $('.genfile').click(function () {
        var url = "/surveyKPI/tables/generate",
            $this = $(this),
            filename,
            mime,
            data,
            settings = [],
            managedId,
            title = $('#survey_name option:selected').text(),
            project = $('#project_name option:selected').text(),
            charts = [],
            chartData,
            settingsObj,
            fromVal,
            toVal,
            colCount = 0,
            colName,
            colValue,
            i,
            format;

        /*
         * Get the settings
         */
        settingsObj = globals.gMainTable.settings();
        settings.push({
            k: "search",
            v: settingsObj.search()
        });
        settings.push({
            k: "Filter Date",
            v: $('#date_question :selected').text()
        });
        settings.push({
            k: "from",
            v: $('#filter_from').val()
        });
        settings.push({
            k: "to",
            v: $('#filter_to').val()
        });
        colCount = globals.gMainTable.columns()[0].length;
        for (i = 0; i < colCount; i++) {
            colValue = globals.gMainTable.column(i).search();
            if (colValue && colValue.trim().length > 2) {

                settings.push({
                    k: $(globals.gMainTable.column(i).header()).find('span').text(),
                    v: colValue.substring(1, colValue.length - 1)	// Remove regexp
                });
            }

        }


        data = getTableData(globals.gMainTable,
            gTasks.cache.surveyConfig[gTasks.gSelectedSurveyIndex].columns);

        if ($this.hasClass("xls")) {
            filename = title + ".xlsx"
            mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            format = "xlsx";
        } else if ($this.hasClass("pdf")) {
            filename = title + ".pdf"
            mime = "application/pdf";
            format = "pdf";
        } else {
            filename = title + ".zip"
            mime = "application/zip";
            format = "image";
        }

        if (format !== "image") {
            if (isBrowseResults || isDuplicates) {
                managedId = 0;
            } else {
                managedId = gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].managed_id;
            }

            if (format === "xlsx") {
                chartData = chart.refreshCharts(true);
            }

            generateFile(url, filename, format, mime, data, globals.gCurrentSurvey, managedId, title, project, charts, chartData, settings);
        } else {
            var countImages = $('.svg-container svg').length;
            $('.svg-container svg').each(function (index) {
                var $this = $(this),
                    elem = $this[0],
                    title = $this.closest('.ibox').find('.ibox-title h5').text();

                if (!title) {
                    title = "A Chart";
                }
                //svgsave.saveSvgAsPng(elem, "x.png");
                svgsave.svgAsPngUri(elem, undefined, function (uri) {
                    var chart = {
                        image: uri,
                        title: title
                    }
                    charts.push(chart);
                    console.log("Got image: " + countImages);
                    countImages--;
                    if (countImages <= 0) {
                        generateFile(url, filename, format, mime, undefined, globals.gCurrentSurvey, managedId, title, project, charts, chartData, settings);
                    }
                });

            });
        }

    });

    /*
     * Alerts
     */
    $('#show_alerts').click(function () {
        if (!globals.gAlertSeen) {
            globals.gAlertSeen = true;
            $('.alert_icon').removeClass("text-danger");
            saveLastAlert(globals.gLastAlertTime, true);
        }
    });

    // Respond to duplicate gReports menu
    if (isDuplicates) {
        $('#duplicateSearch').click(function () {
            showDuplicateData(globals.gCurrentSurvey);
        });
    }


    /*
     * Add date filtering to datatable
     */
    if (!isDuplicates) {
        $.fn.dataTableExt.afnFiltering.push(
            function (oSettings, aData, iDataIndex) {
                var fromDate = document.getElementById('filter_from').value,
                    toDate = document.getElementById('filter_to').value,
                    dateCol = $('#date_question').val();

                var dateParts = [],
                    dataDate,
                    dataDateVal;

                fromDate = fromDate.replace(/\-/g, "");
                toDate = toDate.replace(/\-/g, "");

                dataDateVal = aData[dateCol];

                if (dataDateVal) {
                    dataDate = dataDateVal.replace(/\-/g, "");
                    dateParts = dataDate.split(" ");
                    if (dateParts.length > 0) {
                        dataDate = dateParts[0];
                    }

                    if (fromDate === "" && toDate === "") {
                        return true;
                    }
                    if (fromDate === "" && toDate >= dataDate) {
                        return true;
                    } else if (toDate === "" && fromDate <= dataDate) {
                        return true;
                    } else if (fromDate <= dataDate && toDate >= dataDate) {
                        return true;
                    }

                    return false;
                } else {
                    return true;
                }

            }
        );
    }

});


