/*
 This file is part of SMAP.

 SMAP is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.
 uSMAP is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with SMAP.  If not, see <http://www.gnu.org/licenses/>.

 */

/*
 * Chart functions
 */

"use strict";

define([
        'jquery',
        'modernizr',
        'localise',
        'globals',
        'app/charts/bar_h',
        'app/charts/bar_v',
        'app/charts/pie',
        'app/charts/line',
        'app/charts/wordcloud',
        'svgsave'],
    function ($, modernizr, lang, globals, bar_h, bar_v, pie, line, wordcloud, svgsave) {

        /*
         * Available charts
         * The system knows how to show these
         */
        var avCharts = {
            bar_h: {
                draw: bar_h,
                tseries: true,
                non_tseries: true
            },
            bar_v: {
                draw: bar_v,
                tseries: true,
                non_tseries: true
            },
            pie: {
                draw: pie,
                tseries: false,
                non_tseries: true
            },
            line: {
                draw: line,
                tseries: true,
                non_tseries: false
            },
            wordcloud: {
                draw: wordcloud,
                tseries: false,
                non_tseries: true
            }
        };

        /*
         * Graph functions
         */
        var avFunctions = [
            {
                fn: "count",
                numeric: false,
                nonNumeric: true
            },
            {
                fn: "average",
                numeric: true,
                nonNumeric: false
            },
            {
                fn: "max",
                numeric: true,
                nonNumeric: false
            },
            {
                fn: "min",
                numeric: true,
                nonNumeric: false
            },
            {
                fn: "sum",
                numeric: true,
                nonNumeric: false
            }
        ]

        var gBlankChart = {
            groups: [],
            time_interval: false,
            title: "",
            chart_type: "bar_h",
            group: "none",
            fn: "count",
            tSeries: false,
            period: undefined,
            width: 12
        };

        //var gCurrentReport = undefined;
        var gEdConfig,			// Temporary objects used when editing a chart
            gEdChart,
            gIsNewChart,
            gChartId,
            gCharts = [],
            gChartsConfig = {};
        var gChartUpdatePending = true,
            gTimingUpdatePending = true,
            gInitDone = false;

        return {
            init: init,
            setCharts: setCharts,           // Set the list of charts to display
            refreshAllCharts: refreshAllCharts,
            addNewChart: addNewChart,
            addFunctions: addFunctions

        };

        function init(chartView, timingView) {

            if(!gInitDone) {
                localise.setlang();

                $('#editChartSave').off().click(function () {
                    saveChart();
                });

                $('#ew_tseries').off().click(function () {
                    var $this = $(this);

                    setChartDialogVisibility({
                        tSeries: $this.prop("checked")
                    });
                    addChartTypeSelect($this.prop("checked"));
                });

                $('#ew_question').off().change(function () {
                    var index = $(this).val();
                    var columns = gTasks.cache.surveyConfig[globals.gViewId].columns;

                    addFunctions(columns[index].type);
                });

                gInitDone = true;
            }

            if ((gChartUpdatePending && chartView) || (gTimingUpdatePending && timingView)) {
                refreshAllCharts(chartView, timingView, true);
            }
        }

        function setCharts(charts) {
            gCharts = charts || [];
        }

        /*
         * Get XLS Data from charts
         */
        function getXLSData() {

            var results = globals.gMainTable.rows({
                order: 'current',  // 'current', 'applied', 'index',  'original'
                page: 'all',      // 'all',     'current'
                search: 'applied',     // 'none',    'applied', 'removed'
            }).data();

            var i,
                data,
                chart,
                dataLength = results.count(),
                xlsResponse = [];

            for (i = 0; i < gCharts.length; i++) {

                chart = gCharts[i];
                data = processData(results, chart, dataLength);
                getXlsResponseObject(xlsResponse, chart, data);

            }

            return xlsResponse;

        }

        /*
         * Refresh all charts
         */
        function refreshAllCharts(chartView, timingView, clearExisting) {

            var i;
            if (chartView || timingView) {
                if (globals.gMainTable) {
                    var results = globals.gMainTable.rows({
                        order: 'current',  // 'current', 'applied', 'index',  'original'
                        page: 'all',      // 'all',     'current'
                        search: 'applied',     // 'none',    'applied', 'removed'
                    }).data();

                    gTasks.cache.surveyConfig[globals.gViewId].processedData = undefined;

                    if (clearExisting) {
                        $('.aChart').remove();
                    }

                    // Add charts
                    for (i = 0; i < gCharts.length; i++) {
                        updateSingleChart(results, gCharts[i], false);
                    }
                }
                if(chartView) {
                    gChartUpdatePending = false;
                } else if(timingView) {
                    gTimingUpdatePending = false;
                }
            } else {
                if(chartView) {
                    gChartUpdatePending = true;
                } else if(timingView) {
                    gTimingUpdatePending = true;
                }
            }

        }

        /*
         * Refresh a single chart
         */
        function refreshChart(chart, clean) {

            var results = globals.gMainTable.rows({
                order: 'current',  // 'current', 'applied', 'index',  'original'
                page: 'all',      // 'all',     'current'
                search: 'applied',     // 'none',    'applied', 'removed'
            }).data();

            updateSingleChart(results, chart, clean);

        }

        /*
         * Update a single chart
         * Note this is a separate function to refreshChart() as refreshChart() and refreshAllCharts()
         *  both need to do similar initialisation
         */
        function updateSingleChart(results, chart, clean) {

            if (chart.groups) {
                chart.groupLabels = chart.groups.map(function (e) {
                    return e.dataLabel;
                });
            }

            var dataLength = results.count();
            var data = processData(results, chart, dataLength);

            var existingChart = false;
            var $chart;
            if (chart.id) {
                $chart = $('#c_' + chart.id);
                existingChart = $chart.length;
            }

            if (existingChart && clean) {
                $chart.remove();
                existingChart = false;
            }

            if (!existingChart) {
                chart.id = appendNewChart(chart);
                $chart = $('#c_' + chart.id);
            }

            fillChart($chart, data, chart, true);
        }

        /*
         * Add a charts data to the xlsResponse object if the data is to be sent to an XLS export
         */
        function getXlsResponseObject(xlsResponse, chart, data) {
            var newData,
                i,
                add = false,
                name = chart.title;

            if (chart.tSeries) {
                newData = data;
                add = true;
            } else if (chart.chart_type === "bar_h" || chart.chart_type === "bar_v") {
                newData = [];
                add = true;
                for (i = 0; i < data.length; i++) {
                    newData.push({
                        key: data[i].key,
                        pr: [{
                            key: name,
                            value: data[i].value
                        }]
                    });
                }
            } else if (chart.chart_type === "wordcloud") {
                newData = [];
                add = true;
                for (var p in data) {
                    if (data.hasOwnProperty(p)) {
                        newData.push({
                            key: p,
                            pr: [{
                                key: name,
                                value: data[p]
                            }]
                        });
                    }
                }

            } else {
                console.log("Unknown chart type: " + chart.chart_type);
            }

            if (add) {
                xlsResponse.push({
                    chart_type: chart.chart_type,
                    name: chart.title,
                    data: newData
                });
            }
        }

        /*
         * Generate data suitable for charting from the results
         */
        function processData(results, chart, dataLength) {
            var i;

            if(chart.tSeries) {
                return processTimeSeriesData(results, chart, dataLength);

            } else {
                if (!gTasks.cache.surveyConfig[globals.gViewId].processedData) {

                    gTasks.cache.surveyConfig[globals.gViewId].processedData = [];
                    for (i = 0; i < results.length; i++) {
                        var di = {};
                        di.count = 1;
                        for (var p in results[i]) {
                            if (results[i].hasOwnProperty(p)) {
                                di[p] = results[i][p];
                            }
                        }
                        if(!di._duration) {         // Make sure durations have a number
                            di._duration = 0;
                        }
                        di._duration = +di._duration;
                        gTasks.cache.surveyConfig[globals.gViewId].processedData.push(di);
                    }
                }
                return gTasks.cache.surveyConfig[globals.gViewId].processedData;
            }
        }

        /*
         * Process the data according to chart type
         */
        function processTimeSeriesData(results, chart, dataLength) {

            var allData = [],
                processedData,
                parseTimeSec = d3.timeParse("%Y-%m-%d %H:%M:%S%Z"),
                parseTimeDay = d3.timeParse("%Y-%m-%d"),
                formatTimeDay = d3.timeFormat("%Y-%m-%d"),
                parseTimeMonth = d3.timeParse("%Y-%m"),
                formatTimeMonth = d3.timeFormat("%Y-%m"),
                parseTimeYear = d3.timeParse("%Y"),
                formatTimeYear = d3.timeFormat("%Y"),
                i,
                dateValueMap = [],
                dateExtent,
                parseTime,
                formatTime,
                dateRange,
                d3Fn,
                groupIdx;

            if (chart.fn === "count" || chart.fn === "percent") {
                d3Fn = "length";
            } else {
                d3Fn = chart.fn;
            }

            if (chart.fn !== "percent") {
                dataLength = 1;			// Don't divide by total number of records unless percent
            }

            if (chart.tSeries) {
                parseTime = chart.period === "day" ? parseTimeDay :
                    chart.period === "month" ? parseTimeMonth :
                        parseTimeYear;
                formatTime = chart.period === "day" ? formatTimeDay :
                    chart.period === "month" ? formatTimeMonth :
                        formatTimeYear;
                dateRange = chart.period === "day" ? d3.timeDay :
                    chart.period === "month" ? d3.timeMonth :
                        d3.timeYear;
            } else {
                parseTime = parseTimeSec;
            }

            if (chart.tSeries && chart.groups) {
                for (i = 0; i < chart.groups.length; i++) {

                    allData.push(d3.nest()
                        .key(function (d) {
                            var dateArray,
                                adjKey,
                                adjKeyArray;

                            if (d[chart.groups[i].name]) {
                                dateArray = d[chart.groups[i].name].trim().split(" ");
                                if (dateArray.length > 0) {
                                    adjKey = dateArray[0];
                                    adjKeyArray = adjKey.split("-");

                                    // Default is day
                                    if (chart.period === "month") {
                                        adjKey = adjKeyArray[0] + "-" + adjKeyArray[1];
                                    } else if (chart.period === "year") {
                                        adjKey = adjKeyArray[0];
                                    }
                                }
                            }

                            return adjKey;
                        })
                        .rollup(function (v) {
                            return v[d3Fn];
                        })
                        .entries(results));
                }

                /*
                 * Remove undefined dates
                 */
                for (i = 0; i < allData.length; i++) {
                    allData[i] = allData[i].filter(function (v) {
                        return v.key !== "undefined";
                    })
                }

                // Add missing dates
                // Based on http://stackoverflow.com/questions/18835053/d3-js-calculate-width-of-bars-in-time-scale-with-changing-range


                for (i = 0; i < allData.length; i++) {
                    allData[i].forEach(function (d) {
                        d.key = parseTime(d.key);
                    });

                    dateValueMap[i] = allData[i].reduce(function (r, v) {
                        if (v.key) {
                            r[v.key.toISOString()] = v.value;
                            return r;
                        }
                    }, {});


                }

                // Get the extent from all the dates in the time series
                var allDates = [];
                for (i = 0; i < allData.length; i++) {
                    allDates = allDates.concat(allData[i])
                }

                dateExtent = d3.extent(allDates.map(function (v) {
                    return v.key;
                }));


                var range = dateRange.range(
                    dateExtent[0],
                    dateRange.offset(dateExtent[1], 1)
                );

                /*
                 * Format the dates and rename the key column to date
                 */
                var newData = [];
                range.forEach(function (date) {
                    var dx = date.toISOString();

                    for (i = 0; i < allData.length; i++) {
                        var value = 0;
                        var newDataItem = {
                            'date': formatTime(date)
                        };
                        if ((dx in dateValueMap[i])) {
                            value = dateValueMap[i][dx];
                        } else if(chart.chart_type === "line") {
                            continue;   // Don't create new empty values for line charts
                        }
                        newDataItem["count"] = value;
                        newDataItem["group"] = chart.groups[i].dataLabel;
                        newData.push(newDataItem);
                    }

                });

                processedData = newData;


            } else if (chart.chart_type === "wordcloud") {
                /*
                 * Generate data for a word cloud
                 */
                //var text_string = "Of course that’s your contention. You’re a first year grad student. You just got finished readin’ some Marxian historian, Pete Garrison probably. You’re gonna be convinced of that ’til next month when you get to James Lemon and then you’re gonna be talkin’ about how the economies of Virginia and Pennsylvania were entrepreneurial and capitalist way back in 1740. That’s gonna last until next year. You’re gonna be in here regurgitating Gordon Wood, talkin’ about, you know, the Pre-Revolutionary utopia and the capital-forming effects of military mobilization… ‘Wood drastically underestimates the impact of social distinctions predicated upon wealth, especially inherited wealth.’ You got that from Vickers, Work in Essex County, page 98, right? Yeah, I read that, too. Were you gonna plagiarize the whole thing for us? Do you have any thoughts of your own on this matter? Or do you, is that your thing? You come into a bar. You read some obscure passage and then pretend, you pawn it off as your own, as your own idea just to impress some girls and embarrass my friend? See, the sad thing about a guy like you is in 50 years, you’re gonna start doin’ some thinkin’ on your own and you’re gonna come up with the fact that there are two certainties in life. One: don’t do that. And two: you dropped a hundred and fifty grand on a fuckin’ education you coulda got for a dollar fifty in late charges at the public library.";
                var common = "poop,i,me,my,myself,we,us,our,ours,ourselves,you,your,yours,yourself,yourselves,he,him,his,himself,she,her,hers,herself,it,its,itself,they,them,their,theirs,themselves,what,which,who,whom,whose,this,that,these,those,am,is,are,was,were,be,been,being,have,has,had,having,do,does,did,doing,will,would,should,can,could,ought,i'm,you're,he's,she's,it's,we're,they're,i've,you've,we've,they've,i'd,you'd,he'd,she'd,we'd,they'd,i'll,you'll,he'll,she'll,we'll,they'll,isn't,aren't,wasn't,weren't,hasn't,haven't,hadn't,doesn't,don't,didn't,won't,wouldn't,shan't,shouldn't,can't,cannot,couldn't,mustn't,let's,that's,who's,what's,here's,there's,when's,where's,why's,how's,a,an,the,and,but,if,or,because,as,until,while,of,at,by,for,with,about,against,between,into,through,during,before,after,above,below,to,from,up,upon,down,in,out,on,off,over,under,again,further,then,once,here,there,when,where,why,how,all,any,both,each,few,more,most,other,some,such,no,nor,not,only,own,same,so,than,too,very,say,says,said,shall";

                var textArray = results.map(function (d) {
                    return d[chart.name];
                });

                var data = {};
                for (i = 0; i < textArray.length; i++) {
                    if (textArray[i]) {
                        var words = textArray[i].split(/[ '\-\(\)\*":;\[\]|{},.!?]+/);
                        if (words.length == 1) {
                            if (data[words[0]]) {
                                data[words[0]]++;
                            } else {
                                data[words[0]] = 1;
                            }
                        } else {
                            words.forEach(function (word) {
                                var word = word.toLowerCase();
                                if (word != "" && common.indexOf(word) == -1 && word.length > 1) {
                                    if (data[word]) {
                                        data[word]++;
                                    } else {
                                        data[word] = 1;
                                    }
                                }
                            })
                        }
                    }
                }
            } else if (chart.fn === "avgdurn") {
                var i, j,
                    data = [],
                    dc,
                    maxValue;

                data = d3.nest()
                    .key(function (d) {
                        return d[chart.group];
                    })
                    .rollup(function (v) {
                        return d3.mean(v, function (d) {
                            if (d[chart.groups[0].q] && d[chart.groups[1].q]) {
                                return d3.timeSecond.count(parseTime(d[chart.groups[0].q]), parseTime(d[chart.groups[1].q]));
                            } else {
                                return 0;
                            }
                        });
                    })
                    .entries(results);

                maxValue = d3.max(data, function (d) {
                    return +d.value;
                });
                if (maxValue < 120) {
                    chart.scale = "seconds";
                } else if (maxValue >= 120 && maxValue < 7200) {	// Between 2 minutes and 2 hours
                    data.forEach(function (d) {
                        d.value = d.value / 60;
                    });
                    chart.scale = "minutes";
                } else {
                    data.forEach(function (d) {
                        d.value = d.value / 3600;
                    });
                    chart.scale = "hours";
                }

            } else {

                for (groupIdx = 0; groupIdx < chart.groups.length; groupIdx++) {

                    var item = chart.groups[groupIdx];
                    if (item.type === "select") {
                        var i, j,
                            data = [],
                            dc;
                        for (i = 0; i < item.choices.length; i++) {
                            dc = d3.nest()
                                .key(function (d) {
                                    return d[item.choices[i]];
                                })
                                .rollup(function (v) {
                                    return v[d3Fn];
                                })
                                .entries(results);

                            for (j = 0; j < dc.length; j++) {
                                var choiceName = item.choices[i].split(" - ");
                                if (dc[j].key == "1") {
                                    data.push({
                                        key: choiceName[1],
                                        value: dc[j].value
                                    });
                                    break;
                                }
                            }
                        }

                    } else {

                        var newData = [];
                        for(i = 0; i < results.length; i++) {
                            var di = {};
                            di.count = 1;
                            for (var p in results[i]) {
                                if (results[i].hasOwnProperty(p)) {
                                    di[p] = results[i][p];
                                }
                            }
                            newData.push(di);
                        }
                        processedData = newData;
                        /*
                        processedData = d3.nest()
                            .key(function (d) {
                                return d[item.name];
                            })
                            .rollup(function (v) {
                                return v[d3Fn] / dataLength;
                            })
                            .entries(results);
                            */
                    }
                }

            }

            return processedData;
        }

        /*
         * Fill a chart container with the chart details
         */
        function fillChart($chart, data, chart) {

            // Get dynamic widths of container
            var widthContainer = $('.ibox-content', $chart).width();
            var heightContainer = $('.ibox-content', $chart).height();

            if (widthContainer > 0 && heightContainer > 0) {
                var config = gChartsConfig[chart.id],
                    init = false;

                if (typeof config === "undefined") {
                    gChartsConfig[chart.id] = {};
                    config = gChartsConfig[chart.id];
                    init = true;
                }

                if (avCharts[chart.chart_type]) {


                    if (true || init || chart.chart_type === "pie" || chart.chart_type === "wordcloud") {	// Pie charts tricky to update, wordcloud not implemented update yet

                        config.domElement = '#svg_' + chart.id;

                        // Remove the existing plot if we cannot update gracefully
                        if ($(config.domElement).length > 0) {
                            $(config.domElement).empty();
                        }

                        var $elem = $(config.domElement);
                        var width = $elem.width();
                        var height = $elem.height();
                        var svg = dimple.newSvg(config.domElement, width, height);
                        config.graph = new dimple.chart(svg, data);
                        avCharts[chart.chart_type].draw.add(chart, config);
                        config.graph.addLegend(width - 150, 10, 100, height - 20, "right");
                        config.graph.draw();
                    }
                    //if (chart.chart_type !== "pie" && chart.chart_type !== "wordcloud") {
                    //    avCharts[chart.chart_type].redraw(chart, config, data, widthContainer, heightContainer);
                    //}
                } else {
                    console.log("unknown chart type: " + chart.chart_type);
                }
            } else {
                alert("Could not find html for " + chart.title);
            }

        }

        /*
         * Append a new chart to the chart content area
         */
        function appendNewChart(chart) {
            // Create a unique element id for the chart
            var uniqueString = new Date().valueOf() + "_" + performance.now(),
                id;

            id = uniqueString.replace('\.', '_');

            var h = [],
                idx = -1;

            h[++idx] = '<div class="aChart col-sm-';            // Chart id
            h[++idx] = (chart.width ? chart.width : '6');
            h[++idx] = '" id="c_';
            h[++idx] = id;
            h[++idx] = '">';

            h[++idx] = '<div class="ibox float-e-margins">';    // ibox title
            h[++idx] = '<div class="ibox-title">';
            h[++idx] = '<div><h5>';
            h[++idx] = chart.title;
            h[++idx] = '</h5></div>';
            h[++idx] = '<div class="ibox-tools"><a class="widget-edit" href="#"><i class="fa fa-edit fa-widget-edit"></i></a><a class="close-link widget-close"><i class="fa fa-close fa-widget-close"></i></a></div>';
            h[++idx] = '</div>';
            h[++idx] = '</div>';                                // ibox title end

            h[++idx] = '<div class="ibox-content">';            // ibox content
            h[++idx] = '<div class="svg-container" id="svg_';
            h[++idx] = id;
            h[++idx] = '">';
            h[++idx] = '</div>';
            h[++idx] = '</div>';                                // ibox content end


            h[++idx] = '</div>';                                // Chart id end

            $('#chartrow').append(h.join(''));
            setupIbox("#c_" + id);		// Add event listeners
            return id;
        }

        /*
         * Add the controls to a chart ibox
         */
        function addChartTools(title) {

            var tools = title.append("div").attr("class", "ibox-tools"),
                i;

            tools.append("a").attr("class", "widget-delete fa-widget-edit")
                .attr("href", "#")
                .append("i").attr("class", "fa fa-trash-o");

            tools.append("a").attr("class", "widget-edit")
                .attr("href", "#")
                .append("i").attr("class", "fa fa-edit fa-widget-edit");

            tools.append("a").attr("class", "close-link widget-close")
                .append("i").attr("class", "fa fa-close fa-widget-close");

        }

        function setupIbox(element) {

            var h = [],
                idx = -1,
                key,
                keylangid;

            // Close ibox function
            $('.close-link', element).click(function () {
                var content = $(this).closest('.aChart');
                content.remove();
            });

            $('.widget-close').off().click(function () {
                var $this = $(this);
                var i;
                var containerId;
                var id;

                containerId = $this.closest('.aChart').attr("id");
                id = containerId.substr(2);   // jump over "c_"
                $('#' + containerId).remove();

                for (i = 0; i < gCharts.length; i++) {
                    if (gCharts[i].id === id) {
                        gCharts.splice(i, 1);
                        saveToServer(gCharts);
                        break;
                    }
                }

                console.log("delete");

            });

            $('.widget-edit').off().click(function () {

                var $this = $(this),
                    chart = d3.select($this.closest('.aChart')),
                    filtered = gTasks.cache.surveyConfig[globals.gViewId].filtered,
                    chart_type = $this.data("ctype"),
                    i,
                    containerId,
                    id;

                containerId = $this.closest('.aChart').attr("id");
                id = containerId.substr(2);   // jump over "c_"

                gEdConfig = gChartsConfig[id];
                gIsNewChart = false;

                for (i = 0; i < gCharts.length; i++) {
                    if (gCharts[i].id === id) {
                        gEdChart = gCharts[i];
                        break;
                    }
                }

                /*
                 * Fix up saved configurations with changes
                 */
                if (gEdChart.name === "completion_time" || gEdChart.name === "periodic_count") {
                    gEdChart.time_interval = true;	// 2016-12-06
                }

                initialiseWidgetDialog();

                $('#editChart').modal("show");
            });

        }

        /*
         * Set the initial values for the widget dialog
         */
        function initialiseWidgetDialog() {

            var columns = gTasks.cache.surveyConfig[globals.gViewId].columns;

            addChartTypeSelect(gEdChart.tSeries);
            if(gEdChart.groups.length > 0) {
                addFunctions(columns[gEdChart.groups[0].qIdx].type);
            }

            $('#ew_tseries').prop("checked", gEdChart.tSeries);
            $('#ew_chart_type').val(gEdChart.chart_type);
            $("#ew_title").val(gEdChart.title);
            $('#ew_width').val(gEdChart.width);
            $('#ew_fn').val(gEdChart.fn);
            if (!gEdChart.tSeries && gEdChart.groups.length > 0) {
                $('#ew_question').val(gEdChart.groups[0].qIdx);
            }

            $('#ew_group').val(gEdChart.group);

            if (gEdChart.tSeries) {
                if (gEdChart.groups && gEdChart.groups.length > 0) {
                    $("#ew_date1").val(gEdChart.groups[0].qIdx);
                    if (gEdChart.groups.length > 1) {
                        $("#ew_date2").val(gEdChart.groups[1].qIdx);
                    }
                }
                $('#ew_period').val(gEdChart.period);
            }

            setChartDialogVisibility({
                chart_type: gEdChart.chart_type,
                group: gEdChart.group,
                tSeries: gEdChart.tSeries
            });

        }

        function setChartDialogVisibility(s) {

            if (typeof s.tSeries !== "undefined") {
                if (s.tSeries) {
                    $(".date_range_only, .period_only").show();
                    $(".question_only, .group_only").hide();
                } else {
                    $(".date_range_only, .period_only").hide();
                    $(".question_only, .group_only").show();
                }
            }

            if (typeof s.gEdChart !== "undefined") {
                if (gEdChart.chart_type === "bar" || gEdChart.chart_type === "pie") {
                    $(".numeric_only").show();
                } else {
                    $(".numeric_only").hide();
                }

                if (typeof s.group !== "undefined") {
                    if (s.group && s.chart_type !== "wordcloud") {
                        $(".group_only").show();
                    } else {
                        $(".group_only").hide();
                    }
                }
            }
        }

        /*
         * Get the config item that this filtered item refers to
         */
        function getFullIndex(name) {
            var i = 0,
                columns = gTasks.cache.surveyConfig[globals.gViewId].columns,
                index = -1;

            for (i = 0; i < columns.length; i++) {
                if (columns[i].name === name) {
                    index = i;
                    break;
                }
            }
            return index;
        }

        /*
         * Get the human name  column name
         */
        function getCol(qname, columns) {
            var col = -1,
                i;

            for (i = 0; i < columns.length; i++) {
                if (qname === columns[i].name) {
                    col = i;
                    break;
                }
            }
            return col;
        }

        function saveChart() {
            var width = $('#ew_width').val(),
                validated = true,
                reset = false,
                errMsg,
                i,
                questionIndex;

            var columns = gTasks.cache.surveyConfig[globals.gViewId].columns;

            var title = $('#ew_title').val();
            if (!title || title.trim().length === 0) {
                validated = false;
                errMsg = localise.set["mf_tr"];
            }

            if (validated) {
                questionIndex = $('#ew_question').val();
                gEdChart.groups = [];
                gEdChart.fn = $('#ew_fn').val();
                gEdChart.title = title;
                gEdChart.tSeries = $('#ew_tseries').prop("checked");
                gEdChart.chart_type = $('#ew_chart_type').val();
                gEdChart.width = $('#ew_width').val();
                gEdChart.period = $('#ew_period').val();

                gEdChart.group = $('#ew_group').val();

                if(!gEdChart.tSeries) {
                    if (typeof questionIndex !== "undefined" && columns && columns[questionIndex]) {		// Question specific

                        gEdChart.groups.push({
                            qIdx: questionIndex,
                            type: columns[questionIndex].type,
                            name: columns[questionIndex].name,
                            dataLabel: columns[questionIndex].humanName
                        });
                    }
                }

                if (gEdChart.tSeries) {
                    questionIndex = $("#ew_date1").val();
                    gEdChart.groups.push({
                        qIdx: questionIndex,
                        type: columns[questionIndex].type,
                        name: columns[questionIndex].name,
                        dataLabel: columns[questionIndex].humanName
                    });

                    questionIndex = $("#ew_date2").val();
                    gEdChart.groups.push({
                        qIdx: questionIndex,
                        type: columns[questionIndex].type,
                        name: columns[questionIndex].name,
                        dataLabel: columns[questionIndex].humanName
                    });

                    //gEdChart.groups[0].label = $("#ew_date1 option[value='" + gEdChart.groups[0].q + "']").text();

                    //gEdChart.groups[1].q = $("#ew_date2").val();
                    //gEdChart.groups[1].label = $("#ew_date2 option[value='" + gEdChart.groups[1].q + "']").text();
                }
                if (gEdChart.tSeries) {
                    var period = $('#ew_period').val();
                    if (period != gEdChart.period) {
                        reset = true;
                    }
                    gEdChart.period = $('#ew_period').val();
                }

                // Todo if chart is being edited, ie index id set then replace
                var replaced = false;
                for (i = 0; i < gCharts.length; i++) {
                    if (gCharts[i].id === gEdChart.id) {
                        gCharts.splice(i, 1, gEdChart);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    gCharts.push(gEdChart);
                }
                saveToServer(gCharts);

                $('#editChart').modal("hide");	// All good close the modal
                refreshChart(gEdChart, true);

            } else {
                $('#chartInfo').show().removeClass('alert-success').addClass('alert-danger').html(errMsg);
            }

        }

        /*
         * Open a dialog to add a new chart
         */
        function addNewChart() {
            gEdChart = $.extend(true, {}, gBlankChart);
            gIsNewChart = true;
            initialiseWidgetDialog();
            $('#editChart').modal("show");
        }

        /*
         * Add the list of selectable chart types
         */
        function addChartTypeSelect(tSeries) {

            var h = [];
            var idx = -1;
            var key;

            for (key in avCharts) {

                if (avCharts.hasOwnProperty(key)) {
                    if (tSeries) {
                        if (!avCharts[key].tseries) {
                            continue;
                        }
                    } else {
                        if (!avCharts[key].non_tseries) {
                            continue;
                        }
                    }

                    h[++idx] = '<option value="';
                    h[++idx] = key;
                    h[++idx] = '">';
                    h[++idx] = localise.set[key];
                    h[++idx] = '</option>';
                }
            }
            $('.chart_type').empty().append(h.join(''));
        }

        /*
         * Add the list of available functions for a chart
         */
        function addFunctions(type) {

            var h = [];
            var idx = -1;
            var key;
            var addNumeric = false;
            var addNonNumeric = false;
            var i;

            if(type === "duration") {
                addNumeric = true;
            } else {
                addNonNumeric = true;
            }

            for (i = 0; i < avFunctions.length; i++) {

                if(addNumeric && avFunctions[i].numeric
                    || addNonNumeric && avFunctions[i].nonNumeric) {


                    h[++idx] = '<option value="';
                    h[++idx] = avFunctions[i].fn;
                    h[++idx] = '">';
                    h[++idx] = localise.set[avFunctions[i].fn];
                    h[++idx] = '</option>';
                }

            }
            $('#ew_fn').empty().append(h.join(''));
        }


        /*
         * Save the layers to the server
         */
        function saveToServer(charts) {

            var saveString = JSON.stringify(charts);
            var viewId = globals.gViewId || 0;
            var url = "/surveyKPI/surveyview/" + viewId;
            url += '?survey=' + globals.gCurrentSurvey;
            url += '&managed=' + 0;
            url += '&query=' + 0;

            addHourglass();
            $.ajax({
                type: "POST",
                dataType: 'json',
                contentType: "application/json",
                cache: false,
                url: url,
                data: {chartView: saveString},
                success: function (data, status) {
                    removeHourglass();
                    globals.gViewId = data.viewId;
                }, error: function (data, status) {
                    removeHourglass();
                    alert(localise.set["msg_err_save"] + " " + data.responseText);
                }
            });
        }

    });
