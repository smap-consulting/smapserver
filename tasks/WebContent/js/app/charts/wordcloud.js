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
 * Drawing a wordcloud
 */

"use strict";

requirejs.config({
    baseUrl: 'js/libs',
    locale: gUserLocale,
    waitSeconds: 0,
    paths: {
        app: '../app'
    },
    shim: {

        'd3cloud2': ['d3v3'],
    }
});

define([
        'jquery',
        'modernizr',
        'localise',
        'globals',
        'd3v3',
        'd3cloud2'],
    function ($, modernizr, lang, globals, d3) {


        return {
            add: add,
            redraw: redraw
        };


        /*
         * Add
         */
        function add(chartID, chart, config, data, widthContainer, heightContainer) {

            var width,
                height,
                margin;

            // Allow space for labels if needed
            var bottom_margin = chart.chart_type === "wordcloud" ? 0 : 60;
            var left_margin = chart.chart_type === "wordcloud" ? 0 : 60;
            var top_margin = chart.chart_type === "wordcloud" ? 40 : 40;
            var right_margin = chart.chart_type === "wordcloud" ? 0 : 20;

            margin = {top: top_margin, right: right_margin, bottom: bottom_margin, left: left_margin};
            width = +widthContainer - margin.left - margin.right;
            height = +heightContainer - margin.top - margin.bottom;

            var fill = d3.scale.category20();
            var word_entries = d3.entries(data);

            var xScale = d3.scale.linear()
                .domain([0, d3.max(word_entries, function (d) {
                    return d.value;
                })
                ])
                .range([6, 40]);

            d3.layout.cloud().size([width, height])
                .timeInterval(20)
                .words(word_entries)
                .fontSize(function (d) {
                    return xScale(+d.value);
                })
                .text(function (d) {
                    return d.key;
                })
                .rotate(function () {
                    return ~~(Math.random() * 2) * 30;
                })
                .font("Impact")
                .on("end", draw)
                .start();

            function draw(words) {
                config.svg
                    .append("g")
                    .attr("transform", "translate(" + [width >> 1, height >> 1] + ")")
                    .selectAll("text")
                    .data(words)
                    .enter().append("text")
                    .style("font-size", function (d) {
                        return xScale(d.value) + "px";
                    })
                    .style("font-family", "Impact")
                    .style("fill", function (d, i) {
                        return fill(i);
                    })
                    .attr("text-anchor", "middle")
                    .attr("transform", function (d) {
                        return "translate(" + [d.x, d.y] + ")rotate(" + d.rotate + ")";
                    })
                    .text(function (d) {
                        return d.key;
                    });
            }
        }

        /*
         * Redraw
         */
        function redraw(chartId, chart, config, data, widthContainer, heightContainer) {

            var fill = d3.scale.category20();
            var word_entries = d3.entries(data);

            var xScale = d3.scale.linear()
                .domain([0, d3.max(word_entries, function (d) {
                    return d.value;
                })
                ])
                .range([6, 40]);

            svg = config.svg;

            d3.layout.cloud().stop().words(word_entries).start();

        }


    });