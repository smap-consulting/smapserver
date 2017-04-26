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
 * Drawing bar charts
 */

"use strict";

define([
        'jquery',
        'modernizr',
        'localise',
        'globals',
        'localise'],
    function ($, modernizr, lang, globals, localise) {


        return {
            add: add,
            redraw: redraw
        };


        /*
         * Add
         */
        function add(chart, config, data, widthContainer, heightContainer) {

            var barWidth,
                width,
                height,
                format,
                margin;
            var $elem = $(config.domElement);

            if (chart.fn === "percent") {
                format = "%";
            }

            width = $elem.width();
            height = $elem.height();
            var svg = dimple.newSvg(config.domElement, width, height);
            var myChart = new dimple.chart(svg, data);
            myChart.setMargins(80, 50, 20, 80);
            var x = myChart.addMeasureAxis("x", "value");
            var y = myChart.addCategoryAxis("y", "key");
            x.title = localise.set[chart.fn];
            y.title = chart.dataLabel;
            myChart.addSeries(null, dimple.plot.bar);
            myChart.draw();
        }

        /*
         * Update a bar chart
         */
        function redraw(chart, config, data, widthContainer, heightContainer) {

            var barWidth,
                width,
                height,
                margin,
                labelId,
                format = "";

            if (chart.fn === "percent") {
                format = "%";
            }



        }


    });