/**
 * @preserve Copyright 2013 Martijn van de Rijdt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Deals with communication to the server
 */

define( [ 'wfapp/gui', 'wfapp/settings', 'wfapp/store', 'jquery' ], function( gui, settings, store, $ ) {
    "use strict";
    var oRosaHelper, progress, maxSubmissionSize,
        gStore,
        that = this,
        SUBMISSION_URL,
        currentOnlineStatus = true,
        uploadOngoingID = null,
        uploadOngoingBatchIndex = null,
        uploadResult = {
            win: [],
            fail: []
        },
        uploadBatchesResult = {},
        uploadQueue = []

    //setSubmissionUrl(surveyData.key);	// d1404 key is specific to a survey and should be obtained from the data record

    /**
     * Initialize the connection object
     * @param  { boolean=} submissions whether or not to prepare the connection object to deal with submissions
     */
    function init( submissions, store ) {
        if ( submissions ) {
            _setMaxSubmissionSize();
        }
        gStore = store;
    }

    
    /*
     * Get the submission url for this record
     */
    function getSubmissionUrl(record) {

    	var dynamic = "",
    		url;
    	
        if ( record.accessKey ) {
            dynamic = "/key/" + record.accessKey;
        }

        if ( !record.instanceStrToEditId ) {
            url = "/submission" + dynamic; // New record
        } else {
            url = "/submission" + dynamic + "/" + record.instanceStrToEditId; // Update existing record
        }
        //if(record.assignmentId) {
        //	url += "?assignment_id=" + record.assignmentId;
        //}
        
        return url;
    }
    
    function _setOnlineStatus( newStatus ) {
      
        if ( newStatus !== currentOnlineStatus ) {
            console.log( 'online status changed to: ' + newStatus + ', triggering window.onlinestatuschange' );
            $( window ).trigger( 'onlinestatuschange', newStatus );
        }
        currentOnlineStatus = newStatus;
    }

    function _cancelSubmissionProcess() {
        uploadOngoingID = null;
        uploadOngoingBatchIndex = null;
        _resetUploadResult();
        uploadQueue = [];
    }

    /*
     * Get a new key and update the record
     */
    function getNewKey(record) {
		$.ajax({
			url: '/surveyKPI/login/key?form=user',		// Get a generic user key
			dataType: 'json',
			cache: false,
			success: function(data) {
				// TODO update the record
				if(record.name == 'iframe_record') {
					// Update the web page access key
					surveyData.key = data.key;
				} else {
					// Update the access key in the stored record
					var dbRecord = store.getRecord( record.name );
					dbRecord.accessKey = data.key;
					gStore.setRecord( record.name, dbRecord, true, true, dbRecord.key );
				}
				// TODO update the surveyData in the form if this is an immediate submit
				// setSubmissionUrl(data.key);
			},
			error: function(xhr, textStatus, err) {
				removeHourglass();
				if(xhr.readyState == 0 || xhr.status == 0) {
		              return;  // Not an error
				} else {
					alert("Error: Failed to get access key: " + err);
				}
			}
		});	
    }
    /**
     * [uploadRecords description]
     * @param  {{name: string, instanceID: string, formData: FormData, batches: number, batchIndex: number}}    record   [description]
     * @param  {boolean=}                                                   force     [description]
     * @param  {Object.<string, Function>=}                             callbacks only used for testing
     * @param {boolean} 												close after sending
     * @return {boolean}           [description]
     */
    function uploadRecords( record, force, callbacks, autoClose ) {
        var sameItemInQueue, sameItemSubmitted, sameItemOngoing;
        force = force || false;
        callbacks = callbacks || null;

        if ( !record.name || !record.instanceID || !record.formData || !record.batches || typeof record.batchIndex == 'undefined' ) {
            console.log( 'record name, instanceID, formData, batches and/or batchIndex was not defined!' );
            // Slow the reporting of this error down so that the user can see we tried
            setTimeout( function() {
                progress._updateClass( progress._getLi( record ), 'error' );
            }, 200 );

            return false;
        }

        sameItemInQueue = $.grep( uploadQueue, function( item ) {
            return ( record.instanceID === item.instanceID && record.batchIndex === item.batchIndex );
        } );
        sameItemSubmitted = $.grep( uploadResult.win, function( item ) {
            return ( record.instanceID === item.instanceID && record.batchIndex === item.batchIndex );
        } );
        sameItemOngoing = ( uploadOngoingID === record.instanceID && uploadOngoingBatchIndex === record.batchIndex );
        if ( sameItemInQueue.length === 0 && sameItemSubmitted.length === 0 && !sameItemOngoing ) {
            record.forced = force;
            //TODO ADD CALLBACKS TO EACH RECORD??
            uploadQueue.push( record );
            if ( !uploadOngoingID ) {
                _resetUploadResult();
                uploadBatchesResult = {};
                _uploadOne( callbacks, autoClose );
            }
        }
        //override force property
        //this caters to a situation where the record is already in a queue through automatic uploads, 
        //but the user orders a forced upload
        else {
            sameItemInQueue.forced = force;
        }
        return true;
    }

    /**
     * Uploads a record from the queue
     * @param  {Object.<string, Function>=} callbacks [description]
     */
    function _uploadOne( callbacks, autoClose ) { 
        var record, content, last, props;

        callbacks = ( typeof callbacks === 'undefined' || !callbacks ) ? {
            complete: function( jqXHR, response ) {
                // this event doesn't appear to be use anywhere
                $( document ).trigger( 'submissioncomplete' );
                _processOpenRosaResponse( jqXHR.status,
                    props = {
                        name: record.name,
                        instanceID: record.instanceID,
                        batches: record.batches,
                        batchIndex: record.batchIndex,
                        forced: record.forced
                    } );
                /**
                 * ODK Aggregrate gets very confused if two POSTs are sent in quick succession,
                 * as it duplicates 1 entry and omits the other but returns 201 for both...
                 * so we wait for the previous POST to finish before sending the next
                 */
                var autoCloseVal = autoClose && (jqXHR.status == 201);
                _uploadOne(undefined, autoCloseVal);
            },
            error: function( jqXHR, textStatus ) {
                
            },
            success: function() {}
        } : callbacks;
        
        // smap make sure the complete function is set (override complete past from calling program)
        callbacks.complete = function( jqXHR, response ) {
        	$( document ).trigger( 'submissioncomplete' );
        	_processOpenRosaResponse( jqXHR.status,
        			props = {
	                name: record.name,
	                instanceID: record.instanceID,
	                batches: record.batches,
	                batchIndex: record.batchIndex,
	                forced: record.forced
	            } );
        	 var autoCloseVal = autoClose && (jqXHR.status == 201);
             _uploadOne(undefined, autoCloseVal);
        }

        if ( uploadQueue.length > 0 ) {
            record = uploadQueue.shift();
            
            progress.update( record, 'ongoing', '' );
            
            uploadOngoingID = record.instanceID;
            uploadOngoingBatchIndex = record.batchIndex;
            content = record.formData;
            if(typeof content.append === "function") {
            	content.append( 'Date', new Date().toUTCString() );
            	console.debug( 'prepared to send: ', content );
            }

            _setOnlineStatus( null );
            $( document ).trigger( 'submissionstart' );

            var url = getSubmissionUrl(record);		// Get the url for this record
            $.ajax( url, {
                type: 'POST',
                data: content,
                cache: false,
                contentType: false,
                processData: false,
                timeout: 800 * 1000,
                complete: function( jqXHR, response ) {
                    uploadOngoingID = null;
                    uploadOngoingBatchIndex = null;
                    callbacks.complete( jqXHR, response );
                },
                error: function(jqXHR, response) {
                	var recordInError = record;
                	if(jqXHR.status == 401) {
                    	getNewKey(recordInError);		// Get a new access key
                    }
                	callbacks.error();
                },
                success: callbacks.success
            } );
 
        } else {
        	if ( autoClose ) {
        		 gui.alert( 'This form will now be closed!', 'Submission Successful!', 'success' );
                 setTimeout( function() {
                     window.open( '', '_self' ).close();
                 }, 3500 );
        	}
        }
    }
    
    progress = {

        _getLi: function( record ) {
            var $lis = $( '.record-list' ).find( '[name="' + record.name + '"]' );
            return $lis;
        },

        _reset: function( record ) {
            var $allLis = $( '.record-list' ).find( 'li' );
            //if the current record, is the first in the list, reset the list
            if ( $allLis.first().attr( 'name' ) === record.name ) {
                $allLis.removeClass( 'ongoing success error' ).filter( function() {
                    return !$( this ).hasClass( 'record' );
                } ).remove();
            }
        },

        _updateClass: function( $el, status ) {
            $el.removeClass( 'ongoing error' ).addClass( status );
        },

        _updateProgressBar: function( status ) {
            var $progress,
                max = uploadQueue.length + uploadResult.win.length + uploadResult.fail.length,
                value = uploadResult.win.length + uploadResult.fail.length;

            max += ( status == 'ongoing' ) ? 1 : 0;

            $progress = $( '.upload-progress' ).attr( {
                'max': max,
                'value': value
            } );

            if ( value === max || max === 1 ) {
                $progress.css( 'visibility', 'hidden' );
            } else {
                $progress.css( 'visibility', 'visible' );
            }
        },

        _getMsg: function( record, status, msg ) {
            if ( record.batches > 1 && msg ) {
                return 'part ' + ( record.batchIndex + 1 ) + ' of ' + record.batches + ': ' + msg;
            } else {
                return ( status === 'error' ) ? msg : '';
            }

            return displayMsg;
        },

        update: function( record, status, msg ) {
            var $result,
                $lis = this._getLi( record ),
                displayMsg = this._getMsg( record, status, msg );

            this._reset( record );

            //add display messages (always showing end status)
            if ( displayMsg ) {
                $result = $( '<li name="' + record.name + '" class="' + status + '">' + displayMsg + '</li>' ).insertAfter( $lis.last() );
                window.setTimeout( function() {
                    $result.hide( 500 );
                }, 3000 );
            }

            this._updateClass( $lis.first(), status );
            this._updateProgressBar( status );

            if ( uploadQueue.length === 0 && status !== 'ongoing' ) {
                $( 'button.upload-records' ).removeAttr( 'disabled' );
            } else {
                $( 'button.upload-records' ).attr( 'disabled', 'disabled' );
            }
        }
    };

    //TODO: move this outside this class?
    /**
     * processes the OpenRosa response
     * @param  {number} status [description]
     * @param  {{name:string, instanceID:string, batches:number, batchIndex:number, forced:boolean}} props  record properties
     */
    function _processOpenRosaResponse( status, props ) {
        var i, waswere, name, namesStr, batchText,
            partial = false,
            msg = '',
            names = [],
            level = 'error',
            contactSupport = 'Contact ' + settings[ 'supportEmail' ] + ' please.',
            contactAdmin = 'Contact the survey administrator please.',
            serverDown = 'Sorry, the server is not available. Please try again later or contact your administrator.',
            statusMap = {
                0: {
                    success: false,
                    msg: "Failed (offline?). The browser will retry to send when back online. Please do not close this page."
                },
                200: {
                    success: false,
                    msg: "Data server did not accept data. " + contactSupport
                },
                201: {
                    success: true,
                    msg: "Done!"
                },
                202: {
                    success: true,
                    msg: "Done! (duplicate)"
                },
                '2xx': {
                    success: false,
                    msg: "Unknown error occurred when submitting data. " + contactSupport
                },
                400: {
                    success: false,
                    msg: "Data server did not accept data. " + contactAdmin
                },
                401: {
                    success: false,
                    msg: "Authorisation expired. Refresh your browser. " 
                },
                403: {
                    success: false,
                    msg: "Not allowed to post data to this data server. " + contactAdmin
                },
                404: {
                    success: false,
                    msg: "Submission service on data server not found."
                },
                '4xx': {
                    success: false,
                    msg: "Unknown submission problem on data server."
                },
                413: {
                    success: false,
                    msg: "Data is too large. Please contact " + settings[ 'supportEmail' ] + "."
                },
                500: {
                    success: false,
                    msg: serverDown
                },
                503: {
                    success: false,
                    msg: serverDown
                },
                '5xx': {
                    success: false,
                    msg: serverDown
                }
            };

        //console.debug( 'submission results with status: ' + status + ' for ', props );

        batchText = ( props.batches > 1 ) ? ' (batch #' + ( props.batchIndex + 1 ) + ' out of ' + props.batches + ')' : '';
        props.batchText = batchText;

        // Smap Set online status to false if the error failed due to an inability to connect
        if ( status === 0 ) {	
            _setOnlineStatus( false );
        }
        
        if ( typeof statusMap[ status ] !== 'undefined' ) {
            props.msg = statusMap[ status ].msg;
            if ( statusMap[ status ].success === true ) {
                level = 'success';
                if ( props.batches > 1 ) {
                    if ( typeof uploadBatchesResult[ props.instanceID ] == 'undefined' ) {
                        uploadBatchesResult[ props.instanceID ] = [];
                    }
                    uploadBatchesResult[ props.instanceID ].push( props.batchIndex );
                    for ( i = 0; i < props.batches; i++ ) {
                        if ( $.inArray( i, uploadBatchesResult[ props.instanceID ] ) === -1 ) {
                            partial = true;
                        }
                    }
                }
                uploadResult.win.push( props );
            } else if ( statusMap[ status ].success === false ) {
                uploadResult.fail.push( props );
            }
        }
        //unforeseen statuscodes
        else if ( status > 500 ) {
            console.error( 'Error during uploading, received unexpected statuscode: ' + status );
            props.msg = statusMap[ '5xx' ].msg;
            uploadResult.fail.push( props );
        } else if ( status > 400 ) {
            console.error( 'Error during uploading, received unexpected statuscode: ' + status );
            props.msg = statusMap[ '4xx' ].msg;
            uploadResult.fail.push( props );
        } else if ( status > 200 ) {
            console.error( 'Error during uploading, received unexpected statuscode: ' + status );
            props.msg = statusMap[ '2xx' ].msg;
            uploadResult.fail.push( props );
        }

        progress.update( props, level, props.msg );

        if ( !partial && level === 'success' ) {
            $( document ).trigger( 'submissionsuccess', [ props.name, props.instanceID ] );
        } else if ( level === 'success' ) {
            console.debug( 'not all batches for instanceID have been submitted, current queue:', uploadQueue );
        }

        if ( uploadQueue.length > 0 ) {
            return;
        }

        console.debug( 'online: ' + currentOnlineStatus, uploadResult );

        if ( uploadResult.win.length > 0 ) {
            for ( i = 0; i < uploadResult.win.length; i++ ) {
                name = uploadResult.win[ i ].name;
                if ( $.inArray( name, names ) === -1 ) {
                    names.push( name );
                    msg = ( typeof uploadResult.win[ i ].msg !== 'undefined' ) ? msg + ( uploadResult.win[ i ].msg ) + ' ' : '';
                }
            }
            waswere = ( names.length > 1 ) ? ' were' : ' was';
            namesStr = names.join( ', ' );
            gui.feedback( namesStr.substring( 0, namesStr.length ) + waswere + ' successfully uploaded!' );
            _setOnlineStatus( true );
        }

        if ( uploadResult.fail.length > 0 ) {
            msg = '';
            //console.debug('upload failed');
            if ( currentOnlineStatus !== false ) {
                for ( i = 0; i < uploadResult.fail.length; i++ ) {
                    //if the record upload was forced
                    if ( uploadResult.fail[ i ].forced ) {
                        msg += uploadResult.fail[ i ].name + uploadResult.fail[ i ].batchText + ': ' + uploadResult.fail[ i ].msg + '<br />';
                    }
                }
                if ( msg ) gui.alert( msg, 'Failed data submission' );
            } else {
                // not sure if there should be any notification if forms fail automatic submission when offline
            }

            //if ( status === 0 ) {				smap
            //    _setOnlineStatus( false );
            //}
        }
    }

    /**
     * returns the value of the X-OpenRosa-Content-Length header return by the OpenRosa server for this form
     * if request fails, returns a default value. Won't execute again if request was successful.
     *
     * @return {number} [description]
     */
    function _setMaxSubmissionSize() {
        var maxSize,
            storedMaxSize = store.getRecord( '__maxSize' ),
            defaultMaxSize = 10 * 1024 * 1024,
            absoluteMaxSize = 100 * 1024 * 1024;
        if ( typeof maxSubmissionSize == 'undefined' ) {
            maxSubmissionSize = storedMaxSize || defaultMaxSize;
            $( document ).data( {
                "maxSubmissionSize": maxSubmissionSize
            } );
        }
    }

    function getMaxSubmissionSize() {
        return maxSubmissionSize;
    }

    function isValidURL( url ) {
        return ( /^(https?:\/\/)(([\da-z\.\-]+)\.([a-z\.]{2,6})(:[0-9]{2,4})?|(([0-9]{1,3}\.){3}[0-9]{1,3})(:[0-9]{2,4})?)([\/\w \.\-]*)*\/?[\/\w \.\-\=\&\?]*$/ ).test( url );
    }

    function getFormlist( serverURL, callbacks ) {
        callbacks = _getCallbacks( callbacks );

        if ( !isValidURL( serverURL ) ) {
            callbacks.error( null, 'validationerror', 'not a valid URL' );
            return;
        }
        $.ajax( '/forms/get_list', {
            type: 'GET',
            data: {
                server_url: serverURL
            },
            cache: false,
            contentType: 'json',
            timeout: 60 * 1000,
            success: callbacks.success,
            error: callbacks.error,
            complete: callbacks.complete
        } );
    }

    /**
     * Collection of helper functions for openRosa connectivity
     * @param {*} conn [description]
     * @constructor
     */
    oRosaHelper = {
        /**
         * Magically generates a well-formed serverURL from a type and fragment
         * @param  {string} type    type of server or account (http, https, formhub_uni, formhub, appspot)
         * @param  {string} frag    a user input for the given type
         * @return {?string}        a full serverURL
         */
        fragToServerURL: function( type, frag ) {
            var protocol,
                serverURL = '';

            if ( !frag ) {
                console.log( 'nothing to do' );
                return null;
            }
            console.debug( 'frag: ' + frag );
            //always override if valid URL is entered
            //TODO: REMOVE reference to connection
            if ( isValidURL( frag ) ) {
                return frag;
            }

            switch ( type ) {
                case 'http':
                case 'https':
                    protocol = ( /^http(|s):\/\//.test( frag ) ) ? '' : type + '://';
                    serverURL = protocol + frag;
                    break;
                case 'formhub_uni':
                case 'formhub':
                    serverURL = 'https://formhub.org/' + frag;
                    break;
                case 'appspot':
                    serverURL = 'https://' + frag + '.appspot.com';
                    break;
            }

            if ( !isValidURL( serverURL ) ) {
                console.error( 'not a valid url: ' + serverURL );
                return null;
            }
            console.log( 'server_url: ' + serverURL );
            return serverURL;
        }
    };

    function _resetUploadResult() {
        uploadResult = {
            win: [],
            fail: []
        };
    }

    function _getUploadResult() {
        return uploadResult;
    }

    function getUploadQueue() {
        return uploadQueue;
    }

    function getUploadOngoingID() {
        return uploadOngoingID;
    }

    /**
     * Sets defaults for optional callbacks if not provided
     * @param  {Object.<string, Function>=} callbacks [description]
     * @return {Object.<string, Function>}           [description]
     */
    function _getCallbacks( callbacks ) {
        callbacks = callbacks || {};
        callbacks.error = callbacks.error || function( jqXHR, textStatus, errorThrown ) {
            console.error( textStatus + ' : ' + errorThrown );
        };
        callbacks.complete = callbacks.complete || function() {};
        callbacks.success = callbacks.success || function() {
            console.log( 'success!' );
        };
        return callbacks;
    }

    return {
        init: init,
        uploadRecords: uploadRecords,
        getUploadQueue: getUploadQueue,
        getUploadOngoingID: getUploadOngoingID,
        getFormlist: getFormlist,
        isValidURL: isValidURL,
        getMaxSubmissionSize: getMaxSubmissionSize,
        oRosaHelper: oRosaHelper,
        // "private" but used for tests:
        _processOpenRosaResponse: _processOpenRosaResponse,
        _getUploadResult: _getUploadResult,
        _resetUploadResult: _resetUploadResult,
        _setOnlineStatus: _setOnlineStatus
    };
} );
