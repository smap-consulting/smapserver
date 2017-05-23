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
        'app/chart': ['jquery'],
        'qrcode': ['jquery'],
        'toggle': ['bootstrap.min']
    }
});

require([
    'jquery',
    'bootstrap',
    'common',
    'localise',
    'globals',
    'moment',
    'app/chart',
    'app/mapOL3',
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
    'qrcode',
    'toggle'

], function ($,
             bootstrap,
             common,
             localise,
             globals,
             moment,
             chart,
             map,
             datatables,
             svgsave) {

    /*
     * Report definition
     * Default Settings
     *    Create a chart for data table columns that are enabled and do not have column specific setting
     * Column specific settings
     *    Override settings where names match
     */

    var gDataLoaded = false,
        gConfigLoaded = false,
        gMapView = false;           // Set true when the map tab is shown
    var gChartView = false;         // Set true when the chart view is shown
    var gTimingView = false;        // Set true when the timing view is shown

    window.gTasks = {
        cache: {
            surveyConfig: {},
            managedData: {},
            surveyList: {},
            surveyRoles: {}
        },
        gSelectedRecord: undefined,
        gSelectedSurveyIndex: undefined,
        gUpdate: [],
        gCurrentIndex: undefined,
        gPriKey: undefined,
        gSort: undefined,
        gDirn: undefined
    }


    $(document).ready(function () {

        var i,
            params,
            pArray = [],
            param = [],
            openingNew = false,
            dont_get_current_survey = true,
            bs = isBusinessServer();

        window.chart = chart;
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
                columns = gTasks.cache.surveyConfig[globals.gViewId].columns,
                $editForm = $('#editRecordForm'),
                $surveyForm = $('#surveyForm');

            $('.shareRecordOnly, .role_select').hide();
            $('#srLink').val("");
            if (globals.gIsSecurityAdministrator) {
                getSurveyRoles(globals.gCurrentSurvey);
            }
            showEditRecordForm(record, columns, $editForm, $surveyForm);

        });

        /*
         * Save a record of data in managed forms
         */
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

        /*
         * Save changes to the table columns that are shown
         */
        $('#applyColumns').click(function () {

            var
                config = gTasks.cache.surveyConfig[globals.gViewId],
                $this;

            $('input', '#tab-columns-content').each(function (index) {
                $this = $(this);
                config.columns[index + 1].hide = !$this.is(':checked');		// Ignore prikey

            });

            updateVisibleColumns(config.columns);
            saveConfig(config);
            //chart.setChartList();
            //chart.refreshCharts();

        });

        // Save changes to the barcodes that are shown
        $('#applyBarcodes').click(function () {

            var
                config = gTasks.cache.surveyConfig[globals.gViewId],
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

        // Add a new chart
        $('#m_add_chart').click(function () {
            $('#chartInfo').hide();
            chart.addNewChart();
        });

        // Add a new map layer
        $('#m_add_layer').click(function () {
            $('#layerInfo').hide();
            $('#ml_title').val("");
            $('#layerEdit').modal("show");
        });

        // Respond to save on a layer edit dialog
        $('#addLayerSave').click(function () {
            map.saveLayer();
        });

        /*
         * Take action on tab change to initialise tab contents
         * Refer: http://stackoverflow.com/questions/20705905/bootstrap-3-jquery-event-for-active-tab-change
         */
        $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
            var target = $(e.target).attr("href") // activated tab
            $('#showlayers').hide();
            $('#m_add_chart').hide();
            gMapView = false;
            gChartView = false;
            gTimingView = false;
            if (target === '#map-view') {
                map.init();
                $('#showlayers').show();
                gMapView = true;
            } else if(target === '#chart-view') {
                chart.init(true, false);
                $('#m_add_chart').show();
                gChartView = true;
            } else if(target === '#timing-view') {
                chart.init(false, true);
                $('#m_add_chart').show();
                gTimingView = true;
            }
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
            gTasks.cache.surveyConfig[globals.gViewId].columns);

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
                chartData = chart.getXLSData();
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

    /*
     * Get the survey view (mini dashboard for a single survey)
     */
    function getSurveyView(viewId, sId, managedId, queryId) {

        var url;

        url = '/surveyKPI/surveyview/' + viewId;
        url += '?survey=' + sId;
        url += '&managed=' + managedId;
        url += '&query=' + queryId;		// ignore for moment, ie note caching is done only on survey index

        if (!globals.gViewId || !gTasks.cache.surveyConfig[globals.gViewId]) {

            addHourglass();
            $.ajax({
                url: url,
                cache: false,
                dataType: 'json',
                success: function (data) {
                    removeHourglass();
                    gConfigLoaded = true;
                    globals.gViewId = data.viewId;
                    gTasks.cache.surveyConfig[globals.gViewId] = data;

                    map.setLayers(data.layers);
                    chart.setCharts(data.charts);
                    if (gDataLoaded) {
                        map.refreshAllLayers(gMapView);
                        chart.refreshAllCharts(gChartView, gTimingView, true);
                        initialise();
                    }

                    // Add a config item for the group value if this is a duplicates search
                    if (isDuplicates) {
                        data.columns.unshift({
                            hide: true,
                            include: true,
                            name: "_group",
                            humanName: "_group"
                        });
                    }
                    showManagedData(sId, '#content', undefined);
                },
                error: function (xhr, textStatus, err) {
                    removeHourglass();
                    if (xhr.readyState == 0 || xhr.status == 0) {
                        return;  // Not an error
                    } else {
                        alert("Error failed to get column names from survey: " + sId + " " + xhr.responseText);
                    }
                }
            });
        } else {
            showManagedData(sId, '#content', undefined);
        }
    }

    /*
     * Function called when the current survey is changed
     */
    function surveyChanged() {

        gDataLoaded = false;
        gConfigLoaded = false;

        globals.gViewId = 0;        // TODO remember views set for each survey and restore

        if (globals.gCurrentSurvey > 0) {

            saveCurrentProject(-1, globals.gCurrentSurvey);
            if (isManagedForms) {
                getSurveyView(0, globals.gCurrentSurvey,
                    gTasks.cache.surveyList[globals.gCurrentProject][gTasks.gSelectedSurveyIndex].managed_id,
                    0);
            } else {
                getSurveyView(0, globals.gCurrentSurvey, 0, 0);
            }
            //if (!isDuplicates) {
            //    getReport(gReport);
            //}

            $('.main_survey').html($('#survey_name option:selected').text());

        } else {
            // No managed surveys in this project
            $('#content').empty();
        }
    }

    /*
     * Refresh the data used in this page
     */
    function refreshData() {

        // Clear gTasks.cache
        //gTasks.cache.surveyConfig = {};
        gTasks.cache.managedData = {};
        gTasks.cache.surveyList = {};

        // Get the list of available surveys
        loadManagedSurveys(globals.gCurrentProject, surveyChanged);

    }

    /*
     * Function called when the current project is changed
     */
    function projectChanged() {

        globals.gCurrentProject = $('#project_name option:selected').val();
        globals.gCurrentSurvey = -1;
        globals.gCurrentTaskGroup = undefined;

        saveCurrentProject(globals.gCurrentProject,
            globals.gCurrentSurvey,
            globals.gCurrentTaskGroup);

        refreshData();

    }


    /*
     * Get the markup to edit the record
     */
    function getEditMarkup(configItem, itemIndex, first, record) {

        var h = [],
            idx = -1;

        // Add form group and label
        h[++idx] = '<div class="form-group"><label class="col-md-4 control-label">';
        h[++idx] = configItem.humanName;
        h[++idx] = '</label>';

        // Add Data
        h[++idx] = ' <div class="col-md-8">';
        if (configItem.readonly) {		// Read only text
            h[++idx] = '<input type="text" disabled="" class="form-control" value="';
            h[++idx] = record[configItem.name];
            h[++idx] = '">';
        } else {
            h[++idx] = addEditableColumnMarkup(configItem, record[configItem.name], itemIndex, first);
            first = false;
        }
        h[++idx] = '</div>';

        // Close form group
        h[++idx] = '</div>';

        return h.join('');
    }

    /*
     * User has changed a managed value
     */
    function dataChanged($this) {

        var
            itemIndex = $this.data("item"),
            value = $this.val(),
            record = gTasks.gSelectedRecord,
            columns = gTasks.cache.surveyConfig[globals.gViewId].columns,
            currentValue,
            name = columns[itemIndex].name,
            i,
            foundExistingUpdate;

        currentValue = record[columns[itemIndex].name];
        if (typeof currentValue === "undefined") {
            currentValue = "";
        }

        if (currentValue !== value) {
            // Add new value to array, or update existing
            foundExistingUpdate = false;
            for (i = 0; i < gTasks.gUpdate.length; i++) {
                if (gTasks.gUpdate[i].name === name) {
                    foundExistingUpdate = true;
                    gTasks.gUpdate[i].value = value;
                    break;
                }
            }

            if (!foundExistingUpdate) {
                // Add new value
                gTasks.gUpdate.push({
                    name: name,
                    value: value,
                    currentValue: currentValue,
                    prikey: gTasks.gPriKey
                });
            }

        } else {
            // Delete value from array of updates
            for (i = 0; i < gTasks.gUpdate.length; i++) {
                if (gTasks.gUpdate[i].name === name) {
                    gTasks.gUpdate.splice(i, 1);
                    break;
                }
            }
        }
        console.log("  changed: " + itemIndex + " " + value + " " + currentValue);

        if (gTasks.gUpdate.length > 0) {
            $('#saveRecord').prop("disabled", false);
        } else {
            $('#saveRecord').prop("disabled", true);
        }
    }

    /*
     * Show the survey data along with the management columns
     * If masterRecord is specified then only show that record
     */
    function showManagedData(sId, content, masterRecord) {

        var x = 1,
            columns = gTasks.cache.surveyConfig[globals.gViewId].columns,
            shownColumns = [],
            hiddenColumns = [],
            visibleColumns = [],
            h = [],
            idx = -1,
            hfoot = [],
            foot_idx = -1,
            i, j,
            colIdx = 0,
            $table = $("#trackingTable"),
            doneFirst = false,
            headItem,
            hColSort = [],
            hDups = [],
            hColSortIdx = -1;


        if (globals.gMainTable) {
            globals.gMainTable.destroy();
        }

        // Add table
        h[++idx] = '<div class="table-responsive">';
        h[++idx] = '<table id="trackingTable" class="table table-striped">';

        // Add head
        h[++idx] = '<thead>';
        h[++idx] = '<tr>';

        for (i = 0; i < columns.length; i++) {
            headItem = columns[i];

            hColSort[hColSortIdx++] = addToColumnSort(headItem);
            hDups[hColSortIdx++] = addToDuplicateReportSelect(headItem);

            shownColumns.push({
                "data": headItem.name
            });
            h[++idx] = '<th>';
            h[++idx] = '<span class="ch">';
            h[++idx] = headItem.humanName;
            h[++idx] = '</span>';
            h[++idx] = '</th>';
            hfoot[++foot_idx] = '<th></th>';
            headItem.colIdx = colIdx;
            colIdx++;

            if (headItem.hide) {
                hiddenColumns.push(i);
            } else {
                visibleColumns.push(i);
            }
        }
        h[++idx] = '</tr>';
        h[++idx] = '</thead>';
        h[++idx] = '<tfoot>';
        h[++idx] = '<tr>';
        h[++idx] = hfoot.join('');
        h[++idx] = '</tr>';
        h[++idx] = '</tfoot>';

        // close table
        h[++idx] = '</table>';
        h[++idx] = '</div>';

        $table.empty().html(h.join(''));

        /*
         * Apply data tables
         */
        var url = '/api/v1/data/';
        url += sId;

        if (isManagedForms) {
            url += "?mgmt=true";
        } else {
            url += "?mgmt=false";
        }

        if (isDuplicates) {
            url += "&group=true";
        }


        url += "&format=dt";

        $.fn.dataTable.ext.errMode = 'none';

        // Create data table
        globals.gMainTable = $table.DataTable({
            processing: true,
            select: true,
            ajax: url,
            columns: shownColumns,
            order: [[0, "desc"]],
            initComplete: function (settings, json) {
                console.log("initComplete");
                gDataLoaded = true;
                if (gConfigLoaded) {
                    initialise();
                    map.refreshAllLayers(gMapView);
                    chart.refreshAllCharts(gChartView, gTimingView, true);
                }
                columns = gTasks.cache.surveyConfig[globals.gViewId].columns;
                globals.gMainTable.columns().flatten().each(function (colIdx) {
                    if (columns[colIdx].filter || columns[colIdx].type === "select1") {
                        var select = $('<select class="form-control"/>')
                            .appendTo(
                                globals.gMainTable.column(colIdx).footer()
                            )
                            .on('change', function () {
                                var val = $(this).val();
                                if (val == '') {
                                    globals.gMainTable
                                        .column(colIdx)
                                        .search(val)
                                        .draw();
                                } else {
                                    globals.gMainTable
                                        .column(colIdx)
                                        .search("^" + $(this).val() + "$", true, false, false)
                                        .draw();
                                }
                                saveFilter(colIdx, val);
                            });

                        select.append($('<option value=""></option>'));

                        globals.gMainTable
                            .column(colIdx)
                            .cache('search')
                            .sort()
                            .unique()
                            .each(function (d) {
                                select.append($('<option value="' + d + '">' + d + '</option>'));
                            });

                        // Set current value
                        if (columns[colIdx].filterValue) {
                            select.val(columns[colIdx].filterValue).trigger('change');
                        }
                    }

                });
            },

            columnDefs: [{
                targets: "_all",
                render: function (data, type, full, meta) {
                    return addAnchors(data);
                }
            },
                {
                    visible: false,
                    "targets": hiddenColumns
                },
                {
                    visible: true,
                    "targets": visibleColumns
                },
            ],
            language: {
                url: localise.dt()
            }
        });

        // Respond to an error
        globals.gMainTable.on('error.dt', function (e, settings, techNote, message) {
            alert('An error has been reported by DataTables: ', message);
        });

        // Respond to selection of a row
        globals.gMainTable
            .off('select').on('select', function (e, dt, type, indexes) {
            var rowData = globals.gMainTable.rows(indexes).data().toArray();
            if (isManagedForms) {
                gTasks.gSelectedRecord = rowData[0];
                $('#editRecord').modal("show");
            } else if (isBrowseResults) {
                // TODO check if the user has maintain privilege
                alert(JSON.stringify(rowData));
            }
            //alert(JSON.stringify( rowData ));
        });

        // Highlight data conditionally, set barcodes
        globals.gMainTable.off('draw').on('draw', function () {

            if (isDuplicates) {

                var rows = globals.gMainTable.rows({page: 'current'}).nodes();
                var last = null;

                globals.gMainTable.column(0, {page: 'current'}).data().each(function (group, i) {
                    if (last !== group) {
                        $(rows).eq(i).before(
                            '<tr class="group"><td colspan="5">' + group + '</td></tr>'
                        );

                        last = group;
                    }
                });
            } else {
                chart.refreshAllCharts(gChartView, gTimingView, false);
                map.refreshAllLayers(gMapView);
            }

            columns = gTasks.cache.surveyConfig[globals.gViewId].columns;

            for (i = 0; i < columns.length; i++) {
                headItem = columns[i];

                // Highlighting
                if (headItem.markup) {
                    $(globals.gMainTable.column(headItem.colIdx).nodes()).each(function (index) {
                        var $this = $(this),
                            v = $this.text();

                        for (j = 0; j < headItem.markup.length; j++) {
                            if (headItem.markup[j].value == v) {
                                $this.addClass(headItem.markup[j].classes);
                            }
                        }

                    });
                }

                // Barcode
                if (headItem.barcode) {
                    $(globals.gMainTable.column(headItem.colIdx).nodes()).each(function (index) {
                        var $this = $(this),
                            opt = {
                                render: 'div',
                                size: 100,
                                text: $this.text()
                            }

                        $this.empty().qrcode(opt);

                    });
                }
            }

        });

        // Respond to date filter changes
        $('#filter_from, #filter_to').focusout(function () {
            globals.gMainTable.draw();
        });
        $('#date_question').change(function () {
            globals.gMainTable.draw();
        });

        // Respond to change of search
        $('#trackingTable_filter input').focusout(function () {
            globals.gMainTable.draw();
        });

        /*
         * Settings
         */
        $('#tab-columns-content, #tab-barcode-content').html(hColSort.join(''));

        // Set checkboxes in column sort section of settings

        $('input', '#tab-columns-content,#tab-barcode-content').iCheck({
            checkboxClass: 'icheckbox_square-green',
            radioClass: 'iradio_square-green'
        });
        $('input', '#tab-columns-content').each(function (index) {
            if (!columns[index + 1].hide) {
                $(this).iCheck('check');
            }
        });
        $('input', '#tab-barcode-content').each(function (index) {
            if (columns[index + 1].barcode) {
                $(this).iCheck('check');
            }
        });

        /*
         * Duplicates modal
         */
        $('#duplicateSelect').html(hDups.join(''));
        $('input', '#duplicateSelect').iCheck({
            checkboxClass: 'icheckbox_square-green',
            radioClass: 'iradio_square-green'
        });

    }

    /*
     * Show duplicates data
     */
    function showDuplicateData(sId) {

        var url = '/api/v1/data/similar/' + sId + '/' + getSearchCriteria() + "?format=dt";
        globals.gMainTable.ajax.url(url).load();

    }

    /*
     * Get the search criteria for a duplicate search
     */
    function getSearchCriteria() {
        var criteria = "";

        $('input', '#duplicateSelect').each(function (index) {
            var $this = $(this),
                fn;

            if ($this.is(':checked')) {
                if (criteria.length > 0) {
                    criteria += ',';
                }
                fn = $this.closest('.row').find('select').val();
                criteria += $this.val() + '::' + fn;
            }


        });

        return criteria;
    }

    /*
     * Add the column to the settings
     */
    function addToColumnSort(item) {
        var h = [],
            idx = -1;

        if (item.include) {
            h[++idx] = '<div class="setings-item">';
            h[++idx] = '<span>';
            h[++idx] = item.humanName;
            h[++idx] = '</span>';

            h[++idx] = '<div class="switch">';
            h[++idx] = '<input type="checkbox" name="columnSelect"';
            h[++idx] = ' class="columnSelect" value="';
            h[++idx] = item.name;
            h[++idx] = '"';
            //if(!item.hide) {
            //	h[++idx] = ' checked';
            //}
            h[++idx] = '>';
            h[++idx] = '</div>';
            h[++idx] = '</div>';
        }
        return h.join('');
    }

    /*
     * Add the column to the select list for duplicate searches
     */
    function addToDuplicateReportSelect(item) {
        var h = [],
            idx = -1;

        if (item.include) {
            h[++idx] = '<div class="row">';
            h[++idx] = '<div class="setings-item">';

            h[++idx] = '<div class="col-sm-1">';
            h[++idx] = '<input type="checkbox" name="columnSelect"';
            h[++idx] = ' class="columnSelect" value="';
            h[++idx] = item.name;
            h[++idx] = '"';
            h[++idx] = '>';
            h[++idx] = '</div>';

            h[++idx] = '<div class="col-sm-4">';
            h[++idx] = '<span>';
            h[++idx] = item.humanName;
            h[++idx] = '</span>';
            h[++idx] = '</div>';


            h[++idx] = '<div class= "col-sm-4">';
            h[++idx] = '<select>';
            h[++idx] = '<option value="exact">';
            h[++idx] = localise.set["br_exact"];
            h[++idx] = '</option>';
            h[++idx] = '<option value="lower">';
            h[++idx] = localise.set["br_ci"];
            h[++idx] = '</option>';
            h[++idx] = '<option value="soundex">';
            h[++idx] = localise.set["br_sdx"];
            h[++idx] = '</option>';
            h[++idx] = '</select>';
            h[++idx] = '</div>';


            h[++idx] = '</div>';	// Settings item
            h[++idx] = '</div>';		// Row

        }
        return h.join('');
    }

    /*
     * Add the markup for an editable column
     */
    function addEditableColumnMarkup(column, value, itemIndex, first) {
        var h = [],
            idx = -1,
            i;

        if (column.type === "text" || column.type === "decimal" || column.type === "integer") {
            h[++idx] = ' <input type="';
            if (column.type === "text") {
                h[++idx] = "text";
            } else if (column.type === "decimal") {
                h[++idx] = "number";
            } else if (column.type === "integer") {
                h[++idx] = "number";
            }
            h[++idx] = '" class="form-control editable" value="';
            h[++idx] = value;
            h[++idx] = '" data-item="';
            h[++idx] = itemIndex;
            if (first) {
                h[++idx] = '" autofocus/>';
            } else {
                h[++idx] = '"/>';
            }
        } else if (column.type === "date") {
            h[++idx] = '<div class="input-group date" data-container="body">';
            h[++idx] = '<input type="text" class="form-control editable" data-date-format="YYYY-MM-DD" value="';
            h[++idx] = value;
            h[++idx] = '" data-item="';
            h[++idx] = itemIndex;
            if (first) {
                h[++idx] = '" autofocus/>';
            } else {
                h[++idx] = '"/>';
            }
            h[++idx] = '<span class="input-group-addon"><span class="glyphicon glyphicon-calendar"></span></span>';
            h[++idx] = '</div>';
        } else if (column.type === "select_one") {
            h[++idx] = ' <select class="form-control editable" ';
            h[++idx] = '" data-item="';
            h[++idx] = itemIndex;
            h[++idx] = '">';
            if (column.choices) {
                for (i = 0; i < column.choices.length; i++) {
                    h[++idx] = '<option';
                    if (column.choices[i].k === value) {
                        h[++idx] = ' selected="selected"'
                    }
                    h[++idx] = ' value="';
                    h[++idx] = column.choices[i].k;
                    h[++idx] = '">'
                    h[++idx] = column.choices[i].v;
                    h[++idx] = '</option>';
                }
            }
            h[++idx] = '</select>';

        } else {
            h[++idx] = value;
        }

        return h.join('');
    }

    /*
     * Get surveys and update the survey lists on this page
     *  This is a different function from the common loadSurveys function as processing differs depending on whether
     *    there is a managed form
     *   applied to the survey
     */
    function loadManagedSurveys(projectId, callback) {

        var url = "/surveyKPI/surveys?projectId=" + projectId + "&blocked=true",
            $elemSurveys = $('#survey_name');


        if (typeof projectId !== "undefined" && projectId != -1 && projectId != 0) {

            addHourglass();
            $.ajax({
                url: url,
                dataType: 'json',
                cache: false,
                success: function (data) {

                    var i,
                        item,
                        h = [],
                        idx = -1,
                        firstSurvey = true,
                        firstSurveyId = undefined,
                        firstSurveyIndex = undefined;

                    removeHourglass();

                    gTasks.cache.surveyList[globals.gCurrentProject] = data;
                    gTasks.gSelectedSurveyIndex = undefined;

                    for (i = 0; i < data.length; i++) {
                        item = data[i];
                        if (item.managed_id > 0 || isBrowseResults || isDuplicates) {
                            h[++idx] = '<option value="';
                            h[++idx] = i;
                            h[++idx] = '">';
                            h[++idx] = item.displayName;
                            h[++idx] = '</option>';

                            if (firstSurvey) {
                                firstSurveyId = item.id;
                                firstSurveyIndex = i;
                                firstSurvey = false;
                            }

                            if (item.id === globals.gCurrentSurvey) {
                                gTasks.gSelectedSurveyIndex = i;
                            }
                        }

                    }

                    $elemSurveys.empty().html(h.join(''));

                    if (!gTasks.gSelectedSurveyIndex && firstSurveyId) {
                        globals.gCurrentSurvey = firstSurveyId;
                        gTasks.gSelectedSurveyIndex = firstSurveyIndex;
                    } else if (gTasks.gSelectedSurveyIndex && firstSurveyId) {
                        $elemSurveys.val(gTasks.gSelectedSurveyIndex);
                    }

                    if (typeof callback == "function") {
                        callback();
                    }
                },
                error: function (xhr, textStatus, err) {

                    removeHourglass();
                    if (xhr.readyState == 0 || xhr.status == 0) {
                        return;  // Not an error
                    } else {
                        console.log("Error: Failed to get list of surveys: " + err);
                    }
                }
            });
        }
    }

    /*
     * Get the currently selected recoord
     */
    function getSelectedRecord() {

        var record,
            idx;

        $('input[type=radio]:checked', '#content table').each(function () {
            idx = $(this).val();
        });

        return idx;
    }

    /*
     * Get data related to the currently selected record
     */
    function getRelatedList(sId, masterRecord) {
        var record = gTasks.gSelectedRecord;

        var url = '/surveyKPI/managed/connected/' + sId + '/0/' + record.prikey;

        $('#relatedData').empty();
        addHourglass();
        $.ajax({
            url: url,
            cache: false,
            dataType: 'json',
            success: function (data) {
                removeHourglass();

                var i;

                for (i = 0; i < data.length; i++) {
                    showRelated(i, data[i]);
                }


            },
            error: function (xhr, textStatus, err) {
                removeHourglass();
                if (xhr.readyState == 0 || xhr.status == 0) {
                    return;  // Not an error
                } else {
                    alert("Error failed to get related data:" + sId);
                }
            }
        });

    }

    /*
     * Show a related data item
     */
    function showRelated(itemIndex, item) {
        var h = [],
            idx = -1,
            tableId = "relTable" + itemIndex;

        h[++idx] = '<div class="row">'
        h[++idx] = '<div class="col-md-12">';
        h[++idx] = '<div class="ibox float-e-margins">';
        h[++idx] = '<div class="ibox-title">';
        h[++idx] = '<h5>';
        h[++idx] = '</h5>';
        h[++idx] = '</div>';
        h[++idx] = '<div class="ibox-content">';
        h[++idx] = '<div class="row">';
        h[++idx] = '<div class="col-md-12">';
        h[++idx] = '<table id="';
        h[++idx] = tableId;
        h[++idx] = '" class="table table-striped table-responsive toggle-arrow-tiny" data-page-size="8">';
        h[++idx] = '</table>';
        h[++idx] = '</div>';
        h[++idx] = '</div>';
        h[++idx] = '</div>';
        h[++idx] = '</div>';
        h[++idx] = '</div>';
        h[++idx] = '</div>';

        $('#relatedData').append(h.join(""));
        getRelatedTable(tableId, item)
    }

    function getRelatedTable(tableId, item) {

        var url,
            managed = isManagedForms ? "true" : "false";

        var url = "/api/v1/data/";

        if (item.type === "child") {
            url += globals.gCurrentSurvey + "?mgmt=" + managed + "&form=" + item.fId + "&parkey=" + item.parkey;
        } else if (item.type === "link") {
            url += item.sId + "?mgmt=" + managed + "&form=" + item.fId + "&hrk=" + item.hrk;
        }

        addHourglass();
        $.ajax({
            url: url,
            cache: false,
            dataType: 'json',
            success: function (data) {
                removeHourglass();
                showManagedData(globals.gCurrentSurvey, '#' + tableId, undefined)


            },
            error: function (xhr, textStatus, err) {
                removeHourglass();
                if (xhr.readyState == 0 || xhr.status == 0) {
                    return;  // Not an error
                } else {
                    alert("Error failed to get table of related data:" + url);
                }
            }
        });
    }

    function updateVisibleColumns(cols) {
        var i,
            hiddenColumns = [],
            visibleColumns = [];

        for (i = 0; i < cols.length; i++) {
            if (cols[i].hide) {
                hiddenColumns.push(i);
            } else {
                visibleColumns.push(i);
            }
        }

        globals.gMainTable.columns(hiddenColumns).visible(false, false);
        globals.gMainTable.columns(visibleColumns).visible(true, false);
        $('#trackingTable').width('auto');
        globals.gMainTable.columns.adjust().draw(); // adjust column sizing and redraw
    }

    /*
     * Save a filter setting
     */
    function saveFilter(column, value) {

        var
            config = gTasks.cache.surveyConfig[globals.gViewId],
            i;

        if (value == '') {
            value = undefined;
        }

        for (i = 0; i < config.columns.length; i++) {
            if (config.columns[i].colIdx == column) {
                config.columns[i].filterValue = value;
                break;
            }
        }

        saveConfig(config);
    }

    /*
     * Update the saved configuration
     *  This includes information on specific charts that are added to the survey whereas the report save below
     *  is for the base report.
     */
    function saveConfig() {
        var configColumns = [],
            columns = gTasks.cache.surveyConfig[globals.gViewId].columns,
            i;

        for (i = 0; i < columns.length; i++) {
            configColumns.push({
                name: columns[i].name,
                hide: columns[i].hide,
                barcode: columns[i].barcode,
                filterValue: columns[i].filterValue,
                chart_type: columns[i].chart_type,
                width: columns[i].width ? columns[i].width : 6
            });
        }

        var saveView = JSON.stringify(configColumns);
        var viewId = globals.gViewId || 0;
        var url = "/surveyKPI/surveyview/" + viewId;
        url += '?survey=' + globals.gCurrentSurvey;
        url += '&managed=' + 0;						// TODO
        url += '&query=' + 0;							// TODO

        addHourglass();
        $.ajax({
            type: "POST",
            dataType: 'json',
            cache: false,
            contentType: "application/json",
            url: url,
            data: {view: saveView},
            success: function (data, status) {
                removeHourglass();
                globals.gViewId = data.viewId;
                $('#right-sidebar').removeClass("sidebar-open");
            }, error: function (data, status) {
                removeHourglass();
                alert(data.responseText);
            }
        });
    }

    /*
     * Save the report definition as opposed to saveConfig which saves the data table filter settings
     * TODO These should be merged?????
     */
    function saveReport(report) {


        var saveString = JSON.stringify(report);

        addHourglass();
        $.ajax({
            type: "POST",
            dataType: 'text',
            cache: false,
            contentType: "application/json",
            url: "/surveyKPI/managed/config/" + globals.gCurrentSurvey + "/db",
            data: {settings: saveString},
            success: function (data, status) {
                removeHourglass();
                $('#right-sidebar').removeClass("sidebar-open");
            }, error: function (data, status) {
                removeHourglass();
                alert(data.responseText);
            }
        });
    }


    /*
     * Add HTML to show a form to edit a record
     */
    function showEditRecordForm(record, columns, $editForm, $surveyForm) {
        var
            h = [],
            idx = -1,
            m = [],
            cnt = -1,
            i,
            configItem,
            first = true;

        //gTasks.gCurrentIndex = index;
        gTasks.gPriKey = record["prikey"];

        // Clear the update array
        gTasks.gUpdate = [];
        $('#saveRecord').prop("disabled", true);

        for (i = 0; i < columns.length; i++) {
            configItem = columns[i];

            if (configItem.mgmt) {
                h[++idx] = getEditMarkup(configItem, i, first, record);
            } else {
                m[++cnt] = getEditMarkup(configItem, i, first, record);
            }
            if (!configItem.readonly) {
                first = false;
            }
        }

        $editForm.html(h.join(''));
        $surveyForm.html(m.join(''));

        // Set up date fields
        $editForm.find('.date').datetimepicker({
            locale: gUserLocale || 'en',
            useCurrent: false,
            showTodayButton: true
        });

        // Respond to changes in the data by creating an update object
        $editForm.find('.form-control').bind("click propertychange paste change keyup input", function () {
            dataChanged($(this));
        });
        $editForm.find('.date').on("dp.change", function () {
            dataChanged($(this).find('input'));
        });
        $editForm.find('select').change(function () {
            dataChanged($(this));
        });

        // Set focus to first editable data item
        $editForm.find('[autofocus]').focus();
    }

    /*
     * Perform initialisation after the data and the survey view configuration have been loaded
     */
    function initialise() {


        var columns = gTasks.cache.surveyConfig[globals.gViewId].columns,
            i,
            h = [],
            idx = -1,
            select_questions = {};

        /*
         * Add an indicator to coulumns if they can be used as a chart question
         * Merge choices in select multiples
         */

        for(i = 0; i < columns.length; i++) {
            var d = columns[i];

            if(
                d.name !== "prikey" &&
                d.name !== "_upload_time" &&
                d.name !== "_start" &&
                d.name !== "_end" &&
                d.type !== "geopoint" &&
                d.type !== "dateTime" &&
                d.type !== "time" &&
                d.type !== "date" &&
                d.type !== "image" && d.type !== "video" && d.type !== "audio") {
                d.chartQuestion = true;
            } else {
                d.chartQuestion = false;
            }

            if(d.type === "select") {
                var n = d.humanName.split(" - ");
                if (n.length > 1) {

                    if (!select_questions[n[0]]) {		// New choice

                        d.select_name = n[0];
                        d.choices = [];
                        d.choices.push(d.humanName);

                        select_questions[n[0]] = d[i];
                        d.chartQuestion = true;
                    } else {
                        var f = select_questions[n[0]];
                        f.choices.push(d.humanName);
                        d.chartQuestion = false;
                    }
                }
            }

        }



    }

});


