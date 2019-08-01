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

    $(document).ready(function () {

        window.bootbox = bootbox;

        setCustomRegister();			// Apply custom javascript
        localise.setlang();

        $('#registerForm').bootstrapValidator({
            feedbackIcons: {
                valid: 'glyphicon glyphicon-ok',
                invalid: 'glyphicon glyphicon-remove',
                validating: 'glyphicon glyphicon-refresh'
            }
        });

        $('#registerForm input').keyup(function () {
            validateForm();
        });

        $('#accept_tc').change(function () {
            validateForm();
        });

        $('#registerSubmit').click(function (e) {
            e.preventDefault();


            var email = $('#admin_email').val(),
                reg = {
                    email: email,
                    org_name: $('#org_name').val(),
                    admin_name: $('#admin_name').val(),
                    website: $('#website').val()
                },
                regString;

            regString = JSON.stringify(reg);


            addHourglass();
            $.ajax({
                type: "POST",
                cache: false,
                url: "/surveyKPI/register",
                data: {registrationDetails: regString},
                success: function (data, status) {
                    removeHourglass();
                    var msg = localise.set["msg_reg"];
                    msg = msg.replace('%s1', email);
                    bootbox.alert(msg);
                    $('#registerForm')[0].reset();
                }, error: function (data, status) {
                    removeHourglass();
                    bootbox.alert("Error: " + data.responseText);
                }
            });
        });


    });

    /*
     * Enable or disable the submit button based on the validity of the form
     */
    function validateForm() {
        var status = true,
            hasAccepted = $('#accept_tc').is(":checked"),
            org_name = $('#org_name').val(),
            admin_name = $('#admin_name').val(),
            admin_email = $('#admin_email').val();

        if (!hasAccepted) {
            status = false;
        } else if (!org_name || org_name.trim().length === 0) {
            status = false;
        } else if (!admin_name || admin_name.trim().length === 0) {
            status = false;
        } else if (!admin_email || admin_email.trim().length === 0) {
            status = false;
        }

        if (status) {
            $('#registerSubmit').prop("disabled", false);
        } else {
            $('#registerSubmit').prop("disabled", true);
        }

    }


});



