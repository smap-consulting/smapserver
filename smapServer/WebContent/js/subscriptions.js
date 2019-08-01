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

var gUserLocale = navigator.language;
if (Modernizr.localstorage) {
    gUserLocale = localStorage.getItem('user_locale') || navigator.language;
}

"use strict";
requirejs.config({
    baseUrl: 'js/libs',
    waitSeconds: 0,
    locale: gUserLocale,
    paths: {
        app: '../app',
        jquery: 'jquery-2.1.1',
        bootbox: 'bootbox.min',
        lang_location: '../'
    },
    shim: {
        'bootstrap.min': ['jquery'],
        'bootstrapValidator.min': ['bootstrap.min'],
        'app/common': ['jquery'],
        'bootbox': ['bootstrap.min']
    }
});

require([
    'jquery',
    'bootstrap.min',
    'bootstrapValidator.min',
    'app/localise',
    'app/common',
    'bootbox'
], function ($, bootstrap, bv, localise, common, bootbox) {

    var gToken;
    var gSubscribe;

    $(document).ready(function () {

        window.bootbox = bootbox;
        var i,
            params,
            pArray = [],
            param = [];


        setCustomSubscriptions();			// Apply custom javascript
        localise.setlang();

        // Get the authentication token if it has been passed in parameters
        params = location.search.substr(location.search.indexOf("?") + 1)
        pArray = params.split("&");
        for (i = 0; i < pArray.length; i++) {
            param = pArray[i].split("=");
            if ( param[0] === "token" ) {
                gToken = param[1];
            }
            if ( param[0] === "subscribe" ) {
                gSubscribe = param[1];
            }
        }

        if(gToken && !gSubscribe) {
            $('#heading').text(localise.set["c_unsubscribe"]);
            $('#unsubscribe').show();
            $('#subscribe').hide();
            $('#subscribe2').hide();
        } else if(gToken && gSubscribe) {
            $('#heading').text(localise.set["c_subscribe"]);
            $('#unsubscribe').hide();
            $('#subscribe').hide();
            $('#subscribe2').show();
        } else {
            $('#heading').text(localise.set["r_s"]);
            $('#unsubscribe').hide();
            $('#subscribe2').hide();
            $('#subscribe').show();
        }

        $('#subscribeForm').bootstrapValidator({
            feedbackIcons: {
                valid: 'glyphicon glyphicon-ok',
                invalid: 'glyphicon glyphicon-remove',
                validating: 'glyphicon glyphicon-refresh'
            }
        });

        if(gToken && !gSubscribe) {
            $('#heading').text(localise.set["c_unsubscribe"]);
            $('#unsubscribe').show();
            $('#subscribe').hide();
            $('#subscribe2').hide();
        } else if(gToken && gSubscribe) {
            $('#heading').text(localise.set["c_subscribe"]);
            $('#unsubscribe').hide();
            $('#subscribe').hide();
            $('#subscribe2').show();
        } else {
            $('#heading').text(localise.set["r_s"]);
            $('#unsubscribe').hide();
            $('#subscribe2').hide();
            $('#subscribe').show();
        }

        $('#unsubscribeSubmit').click(function (e) {
            e.preventDefault();

            addHourglass();
            $.ajax({
                cache: false,
                url: "/surveyKPI/subscriptions/unsubscribe/" + gToken,
                success: function (data, status) {
                    removeHourglass();
                    alert(localise.set["msg_uns"]);
                }, error: function (data, status) {
                    removeHourglass();
                    alert(data.responseText);
                }
            });
        });

        $('#subscribeSubmit').click(function (e) {
            e.preventDefault();

            var email = $('#email').val();

            addHourglass();
            $.ajax({
                cache: false,
                type: "POST",
                url: "/surveyKPI/subscriptions/subscribe",
                data: {email: email},
                success: function (data, status) {
                    removeHourglass();
                    alert(localise.set["msg_s1"]);
                }, error: function (data, status) {
                    removeHourglass();
                    alert(data.responseText);
                }
            });
        });

        $('#subscribe2Submit').click(function (e) {
            e.preventDefault();

            addHourglass();
            $.ajax({
                cache: false,
                url: "/surveyKPI/subscriptions/subscribe/" + gToken,
                success: function (data, status) {
                    removeHourglass();
                    alert(localise.set["msg_s2"]);
                }, error: function (data, status) {
                    removeHourglass();
                    alert(data.responseText);
                }
            });
        });


    });



});



