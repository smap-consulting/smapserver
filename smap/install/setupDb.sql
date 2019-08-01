CREATE USER ws WITH PASSWORD 'ws1234';

DROP SEQUENCE IF EXISTS sc_seq CASCADE;
CREATE SEQUENCE sc_seq START 1;
ALTER SEQUENCE sc_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS re_seq CASCADE;
CREATE SEQUENCE re_seq START 1;
ALTER SEQUENCE re_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS s_seq CASCADE;
CREATE SEQUENCE s_seq START 1;
ALTER SEQUENCE s_seq OWNER TO ws;
 
DROP SEQUENCE IF EXISTS f_seq CASCADE;
CREATE SEQUENCE f_seq START 1;
ALTER SEQUENCE f_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS o_seq CASCADE;
CREATE SEQUENCE o_seq START 1;
ALTER SEQUENCE o_seq OWNER TO ws;	

DROP SEQUENCE IF EXISTS l_seq CASCADE;
CREATE SEQUENCE l_seq START 1;
ALTER SEQUENCE l_seq OWNER TO ws;		

DROP SEQUENCE IF EXISTS q_seq CASCADE;
CREATE SEQUENCE q_seq START 1;
ALTER SEQUENCE q_seq OWNER TO ws;	

DROP SEQUENCE IF EXISTS ssc_seq CASCADE;
CREATE SEQUENCE ssc_seq START 1;
ALTER SEQUENCE ssc_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS forward_seq CASCADE;
CREATE SEQUENCE forward_seq START 1;
ALTER SEQUENCE forward_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS reminder_seq CASCADE;
CREATE SEQUENCE reminder_seq START 1;
ALTER SEQUENCE reminder_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS notification_log_seq CASCADE;
CREATE SEQUENCE notification_log_seq START 1;
ALTER SEQUENCE notification_log_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS t_seq CASCADE;
CREATE SEQUENCE t_seq START 1;
ALTER SEQUENCE t_seq OWNER TO ws;	

DROP SEQUENCE IF EXISTS location_seq CASCADE;
CREATE SEQUENCE location_seq START 1;
ALTER SEQUENCE location_seq OWNER TO ws;	

DROP SEQUENCE IF EXISTS l_seq CASCADE;
CREATE SEQUENCE l_seq START 1;
ALTER SEQUENCE l_seq OWNER TO ws;	

DROP SEQUENCE IF EXISTS g_seq CASCADE;
CREATE SEQUENCE g_seq START 1;
ALTER SEQUENCE g_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS ue_seq CASCADE;
CREATE SEQUENCE ue_seq START 1;
ALTER SEQUENCE ue_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS se_seq CASCADE;
CREATE SEQUENCE se_seq START 1;
ALTER SEQUENCE se_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS dp_seq CASCADE;
CREATE SEQUENCE dp_seq START 1;
ALTER SEQUENCE dp_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS sc_seq CASCADE;
CREATE SEQUENCE sc_seq START 1;
ALTER SEQUENCE sc_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS custom_report_seq CASCADE;
CREATE SEQUENCE custom_report_seq START 2;
ALTER SEQUENCE custom_report_seq OWNER TO ws;

-- User management
DROP SEQUENCE IF EXISTS project_seq CASCADE;
CREATE SEQUENCE project_seq START 10;
ALTER SEQUENCE project_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS regions_seq CASCADE;
CREATE SEQUENCE regions_seq START 10;
ALTER SEQUENCE regions_seq OWNER TO ws;

-- Server level defaults
DROP TABLE IF EXISTS server CASCADE;
create TABLE server (
	smtp_host text,
	email_domain text,
	email_user text,
	email_password text,
	email_port integer,
	version text,
	mapbox_default text,
	google_key text,
	sms_url text,
	document_sync boolean,
	doc_server text,
	doc_server_user text,
	doc_server_password text,
	keep_erased_days integer default 0,
	billing_enabled boolean default false
	);
ALTER TABLE server OWNER TO ws;

DROP SEQUENCE IF EXISTS enterprise_seq CASCADE;
CREATE SEQUENCE enterprise_seq START 1;
ALTER SEQUENCE enterprise_seq OWNER TO ws;

DROP TABLE IF EXISTS enterprise CASCADE;
create TABLE enterprise (
	id INTEGER DEFAULT NEXTVAL('enterprise_seq') CONSTRAINT pk_enterprise PRIMARY KEY,
	name text,
	changed_by text,
	billing_enabled boolean default false,
	changed_ts TIMESTAMP WITH TIME ZONE
	);
CREATE UNIQUE INDEX idx_enterprise ON enterprise(name);
ALTER TABLE enterprise OWNER TO ws;

DROP SEQUENCE IF EXISTS organisation_seq CASCADE;
CREATE SEQUENCE organisation_seq START 10;
ALTER SEQUENCE organisation_seq OWNER TO ws;

DROP TABLE IF EXISTS organisation CASCADE;
create TABLE organisation (
	id INTEGER DEFAULT NEXTVAL('organisation_seq') CONSTRAINT pk_organisation PRIMARY KEY,
	e_id integer references enterprise(id) on delete cascade,
	name text,
	company_name text,
	company_address text,
	company_phone text,
	company_email text,
	allow_email boolean,
	allow_facebook boolean,
	allow_twitter boolean,
	can_edit boolean,
	can_notify boolean default true,
	can_use_api boolean default true,
	can_submit boolean default true,
	can_sms boolean default false,
	set_as_theme boolean default false,
	email_task boolean,
	ft_delete text,
	ft_backward_navigation text,
	ft_navigation text,
	ft_image_size text,
	ft_send_location text,
	ft_sync_incomplete boolean,
	ft_odk_style_menus boolean default true,
	ft_specify_instancename boolean default false,
	ft_admin_menu boolean default false,
	ft_exit_track_menu boolean default false,
	ft_review_final boolean default true,
	ft_send text,
	ft_pw_policy integer default -1,
	ft_number_tasks integer default 20,
	changed_by text,
	admin_email text,
	smtp_host text,				-- Set if email is enabled
	email_domain text,
	email_user text,
	email_password text,
	email_port integer,
	default_email_content text,
	website text,
	locale text,					-- default locale for the organisation
	timezone text,				-- default timezone for the organisation
	billing_enabled boolean default false,
	server_description text,
	sensitive_data text,			-- Questions that should be stored more securely
	webform text,				-- Webform options
	navbar_color,
	changed_ts TIMESTAMP WITH TIME ZONE
	);
CREATE UNIQUE INDEX idx_organisation ON organisation(name);
ALTER TABLE organisation OWNER TO ws;

DROP SEQUENCE IF EXISTS log_seq CASCADE;
CREATE SEQUENCE log_seq START 1;
ALTER SEQUENCE log_seq OWNER TO ws;

-- Log table
DROP TABLE IF EXISTS log CASCADE;
create TABLE log (
	id integer DEFAULT NEXTVAL('log_seq') CONSTRAINT pk_log PRIMARY KEY,
	log_time TIMESTAMP WITH TIME ZONE,
	s_id integer,
	o_id integer REFERENCES organisation(id) ON DELETE CASCADE,
	e_id integer,
	user_ident text,
	event text,	
	note text
	);
ALTER TABLE log OWNER TO ws;

DROP TABLE IF EXISTS project CASCADE;
create TABLE project (
	id INTEGER DEFAULT NEXTVAL('project_seq') CONSTRAINT pk_project PRIMARY KEY,
	o_id INTEGER REFERENCES organisation(id) ON DELETE CASCADE,
	name text,
	description text,
	tasks_only boolean default false,	-- Deprecated - Set per form instead as (hide_on_device). When true only tasks will be downloaded to fieldTask
	changed_by text,
	changed_ts TIMESTAMP WITH TIME ZONE
	);
CREATE UNIQUE INDEX idx_project ON project(o_id,name);
ALTER TABLE project OWNER TO ws;


DROP TABLE IF EXISTS regions CASCADE;
create TABLE regions (
	id INTEGER DEFAULT NEXTVAL('regions_seq') CONSTRAINT pk_regions PRIMARY KEY,
	o_id INTEGER REFERENCES organisation(id) ON DELETE CASCADE,
	table_name text,
	region_name text,
	geometry_column text
	);
ALTER TABLE regions OWNER TO ws;

DROP SEQUENCE IF EXISTS map_seq CASCADE;
CREATE SEQUENCE map_seq START 1;
ALTER SEQUENCE map_seq OWNER TO ws;

DROP TABLE IF EXISTS map CASCADE;
create TABLE map (
	id INTEGER DEFAULT NEXTVAL('map_seq') CONSTRAINT pk_maps PRIMARY KEY,
	o_id INTEGER REFERENCES organisation(id) ON DELETE CASCADE,
	name text,
	map_type text,			-- mapbox || geojson
	description text,
	config text,
	version integer
	);
ALTER TABLE map OWNER TO ws;

DROP TABLE IF EXISTS data_processing CASCADE;
create TABLE data_processing (
	id INTEGER DEFAULT NEXTVAL('dp_seq') CONSTRAINT pk_dp PRIMARY KEY,
	o_id INTEGER REFERENCES organisation(id) ON DELETE CASCADE,
	name text,
	type text,			-- lqas || manage
	description text,
	config text
	);
ALTER TABLE data_processing OWNER TO ws;

DROP SEQUENCE IF EXISTS users_seq CASCADE;
CREATE SEQUENCE users_seq START 2;
ALTER SEQUENCE users_seq OWNER TO ws;

DROP TABLE IF EXISTS users CASCADE;
CREATE TABLE users (
	id INTEGER DEFAULT NEXTVAL('users_seq') CONSTRAINT pk_users PRIMARY KEY,
	ident text,
	temporary boolean default false,			-- If true will not show in user management page
	password text,
	realm text,
	name text,
	settings text,				-- User configurable settings
	signature text,
	language varchar(10),
	location text,
	has_gps boolean,
	has_camera boolean,
	has_barcode boolean,
	has_data boolean,
	has_sms boolean,
	phone_number text,
	email text,
	device_id text,
	max_dist_km integer,
	timezone text,
	user_role text,
	current_project_id integer,		-- Set to the last project the user selected
	current_survey_id integer,		-- Set to the last survey the user selected
	current_task_group_id integer,	-- Set to the last task group the user selected
	one_time_password varchar(36),	-- For password reset
	one_time_password_expiry timestamp with time zone,		-- Time and date one time password expires
	password_reset boolean default false,	-- Set true if the user has reset their password
	o_id integer REFERENCES organisation(id) ON DELETE CASCADE,
	action_details text,			-- Details of a specific action the user can undertake
	lastalert text,					-- Time last alert sent to the user
	seen boolean,					-- True if the user has aknowledged the alert
	single_submission boolean default false,		-- Only one submission can be accepted by this user
	created timestamp with time zone
	);
CREATE UNIQUE INDEX idx_users_ident ON users(ident);
ALTER TABLE users OWNER TO ws;

DROP SEQUENCE IF EXISTS dynamic_users_seq CASCADE;
CREATE SEQUENCE dynamic_users_seq START 1;
ALTER SEQUENCE dynamic_users_seq OWNER TO ws;

DROP TABLE IF EXISTS dynamic_users CASCADE;
CREATE TABLE dynamic_users (
	id INTEGER DEFAULT NEXTVAL('dynamic_users_seq') CONSTRAINT pk_dynamic_users PRIMARY KEY,
	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
	survey_ident text,
	access_key varchar(41),
	expiry timestamp with time zone
	);
ALTER TABLE dynamic_users OWNER TO ws;

DROP TABLE IF EXISTS groups CASCADE;
create TABLE groups (
	id INTEGER CONSTRAINT pk_groups PRIMARY KEY,
	name text
	);
CREATE UNIQUE INDEX idx_groups_name ON groups(name);
ALTER TABLE groups OWNER TO ws;
	
DROP SEQUENCE IF EXISTS user_group_seq CASCADE;
CREATE SEQUENCE user_group_seq START 1;
ALTER SEQUENCE user_group_seq OWNER TO ws;

DROP TABLE IF EXISTS user_group CASCADE;
create TABLE user_group (
	id INTEGER DEFAULT NEXTVAL('user_group_seq') CONSTRAINT pk_user_group PRIMARY KEY,
	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
	g_id INTEGER REFERENCES groups(id) ON DELETE CASCADE
	);
CREATE UNIQUE INDEX idx_user_group ON user_group(u_id,g_id);
ALTER TABLE user_group OWNER TO ws;
	
DROP SEQUENCE IF EXISTS user_project_seq CASCADE;
CREATE SEQUENCE user_project_seq START 1;
ALTER SEQUENCE user_project_seq OWNER TO ws;

DROP TABLE IF EXISTS user_project CASCADE;
create TABLE user_project (
	id INTEGER DEFAULT NEXTVAL('user_project_seq') CONSTRAINT pk_user_project PRIMARY KEY,
	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
	p_id INTEGER REFERENCES project(id) ON DELETE CASCADE
	);
ALTER TABLE user_project OWNER TO ws;

DROP SEQUENCE IF EXISTS user_organisation_seq CASCADE;
CREATE SEQUENCE user_organisation_seq START 1;
ALTER SEQUENCE user_organisation_seq OWNER TO ws;

DROP TABLE IF EXISTS user_organisation CASCADE;
create TABLE user_organisation (
	id INTEGER DEFAULT NEXTVAL('user_organisation_seq') CONSTRAINT pk_user_organisation PRIMARY KEY,
	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
	o_id INTEGER REFERENCES organisation(id) ON DELETE CASCADE,
	settings text
	);
CREATE UNIQUE INDEX idx_user_organisation ON user_organisation(u_id,o_id);
ALTER TABLE user_organisation OWNER TO ws;

DROP SEQUENCE IF EXISTS role_seq CASCADE;
CREATE SEQUENCE role_seq START 1;
ALTER TABLE role_seq OWNER TO ws;

DROP TABLE IF EXISTS public.role CASCADE;
CREATE TABLE public.role (
	id integer DEFAULT nextval('role_seq') NOT NULL PRIMARY KEY,
	o_id integer REFERENCES organisation(id) ON DELETE CASCADE,
	name text,
	description text,
	changed_by text,
	changed_ts TIMESTAMP WITH TIME ZONE
);
ALTER TABLE public.role OWNER TO ws;
CREATE UNIQUE INDEX role_name_index ON public.role(o_id, name);

DROP SEQUENCE IF EXISTS user_role_seq CASCADE;
CREATE SEQUENCE user_role_seq START 1;
ALTER SEQUENCE user_role_seq OWNER TO ws;

DROP TABLE IF EXISTS user_role CASCADE;
create TABLE user_role (
	id INTEGER DEFAULT NEXTVAL('user_role_seq') CONSTRAINT pk_user_role PRIMARY KEY,
	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
	r_id INTEGER REFERENCES role(id) ON DELETE CASCADE
	);
ALTER TABLE user_role OWNER TO ws;

-- Create an administrator and set up defaul values
insert into enterprise(id, name, changed_by, changed_ts) values(1, 'Default', '', now());
insert into organisation(id, name, e_id) values(1, 'Smap', 1);

insert into users (id, ident, realm, password, o_id, name, email) 
	values (1, 'admin', 'smap', '9f12895fe9898cc306c45c9d3fcbc3d6', 1, 'Administrator', '');

insert into groups(id,name) values(1,'admin');
insert into groups(id,name) values(2,'analyst');
insert into groups(id,name) values(3,'enum');
insert into groups(id,name) values(4,'org admin');
insert into groups(id,name) values(5,'manage');
insert into groups(id,name) values(6,'security');
insert into groups(id,name) values(7,'view data');
insert into groups(id,name) values(8,'enterprise admin');
insert into groups(id,name) values(9,'server owner');

insert into user_group (u_id, g_id) values (1, 1);
insert into user_group (u_id, g_id) values (1, 2);
insert into user_group (u_id, g_id) values (1, 3);
insert into user_group (u_id, g_id) values (1, 4);
insert into user_group (u_id, g_id) values (1, 5);
insert into user_group (u_id, g_id) values (1, 6);
insert into user_group (u_id, g_id) values (1, 7);
insert into user_group (u_id, g_id) values (1, 8);
insert into user_group (u_id, g_id) values (1, 9);

insert into project (id, o_id, name) values (1, 1, 'A project');

insert into user_project (u_id, p_id) values (1 , 1);

-- Monitoring tables
DROP TABLE IF EXISTS upload_event CASCADE;
CREATE TABLE upload_event (
	ue_id INTEGER DEFAULT NEXTVAL('ue_seq') CONSTRAINT pk_upload_event PRIMARY KEY,
	results_db_applied boolean default false,	-- Speed up for most common subscriber
	s_id INTEGER,
	ident text,	-- Identifier used by survey
	p_id integer,
	o_id integer default 0,	-- Record organisation at time of upload for billing purposes
	e_id integer default 0,	-- Record enterprise for billing
	upload_time TIMESTAMP WITH TIME ZONE,
	user_name text,
	file_name text,
	file_path text,
	audit_file_path text,
	survey_name text,
	imei text,
	orig_survey_ident text,
	update_id varchar(41),
	assignment_id INTEGER,
	instanceid varchar(41),
	status varchar(10),
	reason text,
	location text,
	form_status text,
	notifications_applied boolean,		-- Set after notifications are sent
	incomplete boolean default false,	-- odk will set this if sending attachments in multiple posts
	server_name text,  		-- Stores the server used to upload the results.  The url's of all attachments will reference this address
	survey_notes text,		-- Notes added during completion of the task
	location_trigger text,	-- The trigger for the completion of the task
	start_time timestamp with time zone,
	end_time timestamp with time zone,
	scheduled_start timestamp with time zone,
	instance_name text
	);
create index idx_ue_ident on upload_event(user_name);
CREATE INDEX idx_ue_results_db ON upload_event(results_db_applied);
CREATE index ue_survey_ident ON upload_event(ident);
ALTER TABLE upload_event OWNER TO ws;

DROP TABLE IF EXISTS subscriber_event CASCADE;
CREATE TABLE subscriber_event (
	se_id INTEGER DEFAULT NEXTVAL('se_seq') CONSTRAINT pk_subscriber_event PRIMARY KEY,
	ue_id INTEGER REFERENCES upload_event ON DELETE CASCADE,
	subscriber text,
	dest text,
	status varchar(10),
	reason text
	);
CREATE INDEX se_ue_id_sequence ON subscriber_event(ue_id);
ALTER TABLE subscriber_event OWNER TO ws;

DROP TABLE IF EXISTS option CASCADE;
DROP TABLE IF EXISTS question CASCADE;
DROP TABLE IF EXISTS ssc CASCADE;
DROP TABLE IF EXISTS form CASCADE;
DROP TABLE IF EXISTS survey CASCADE;
DROP TABLE IF EXISTS survey_change CASCADE;

DROP TABLE IF EXISTS survey CASCADE;
CREATE TABLE survey (
	s_id INTEGER DEFAULT NEXTVAL('s_seq') CONSTRAINT pk_survey PRIMARY KEY,
	name text,
	ident text,										-- identifier used by survey clients
	version integer,								-- Version of the survey
	p_id INTEGER REFERENCES project(id),			-- Project id
	blocked boolean default false,					-- Blocked indicator, no uploads accepted if true
	deleted boolean default false,					-- Soft delete indicator
	display_name text not null,
	def_lang text,
	task_file boolean,								-- allow loading of tasks from a file
	timing_data boolean,								-- collect timing data on the phone
	audit_location_data boolean,						-- collect location data on the phone
	track_changes boolean,							-- collect location data on the phone
	class text,
	model text,										-- JSON model of the survey for thingsat
	manifest text,									-- JSON set of manifest information for the survey
	instance_name text,								-- The rule for naming a survey instance form its data
	last_updated_time DATE,
	managed_id integer,								-- Identifier of configuration for managing records
	auto_updates text,								-- Contains the auto updates that need to be applied for this survey
	loaded_from_xls boolean default false,			-- Set true if the survey was initially loaded from an XLS Form
	hrk text,										-- human readable key
	key_policy text,								-- Whether to discard, add or merge duplicates of the HRK
	based_on text,									-- Survey and form this survey was based on
	group_survey_id integer default 0,
	pulldata text,									-- Settings to customise pulling data from another survey into a csv file
	exclude_empty boolean default false,				-- True if reports should not include empty data
	created timestamp with time zone,				-- Date / Time the survey was created
	meta text,										-- Meta items to collect with this survey
	public_link text,
	hidden boolean default false,					-- Updated when a form is replaced
	original_ident text,								-- Updated when a form is replaced
	hide_on_device boolean							-- Used when forms are launched from other forms or as tasks to hide the ad-hoc form
	);
ALTER TABLE survey OWNER TO ws;
DROP INDEX IF EXISTS SurveyDisplayName;
DROP INDEX IF EXISTS SurveyKey;
CREATE UNIQUE INDEX SurveyKey ON survey(ident);

DROP TABLE IF EXISTS survey_change CASCADE;
CREATE TABLE survey_change (
	c_id integer DEFAULT NEXTVAL('sc_seq') CONSTRAINT pk_survey_changes PRIMARY KEY,
	s_id integer REFERENCES survey ON DELETE CASCADE,		-- Survey containing this version
	version integer,								-- Version of survey with these changes
	changes text,								-- Changes as json object
	apply_results boolean default false,			-- Set to true if the results tables need to be updated	
	success boolean default false,				-- Set true if the update was a success
	msg text,									-- Error messages
	user_id integer,								-- Person who made the changes
	visible boolean default true,				-- set false if the change should not be displayed 				
	updated_time TIMESTAMP WITH TIME ZONE		-- Time and date of change
	);
ALTER TABLE survey_change OWNER TO ws;

-- record events on data records by HRK or instanceid if HRK not set
-- Events include
--     submission (reference data in data table keyed on instanceid)
--     managed form update
--     Data cleaning (reference data in .......
--     Task status change  (reference data in tasks)
--     Task completed (reference data in tasks,   submission)
--     Message sent (reference data in messages and notifications)
--     Record assignment status changes
DROP TABLE IF EXISTS record_event CASCADE;
CREATE TABLE record_event (
	id integer DEFAULT NEXTVAL('re_seq') CONSTRAINT pk_record_changes PRIMARY KEY,
	table_name text,								-- Main table containing unique key	
	key text,									-- HRK of change or notification
	instanceid text,								-- instance of change or notification	
	event text,									-- created || change || task || reminder || deleted
	status text,									-- Status of event - determines how it is displayed
	changes text,								-- Details of the change as json object	
	task text,									-- Details of task changes as json object
	notification text,							-- Details of notification as json object
	description text,
	success boolean default false,				-- Set true of the event was a success
	msg text,									-- Error messages
	changed_by integer,							-- Person who made a change	
	change_survey text,							-- Survey ident that applied the change
	change_survey_version integer,				-- Survey version that made the change	
	assignment_id integer,						-- Record if this is an task event	
	task_id integer,								-- Record if this is an task event	
	event_time TIMESTAMP WITH TIME ZONE			-- Time and date of event
	);
ALTER TABLE record_event OWNER TO ws;


DROP TABLE IF EXISTS custom_report CASCADE;
CREATE TABLE custom_report (
	id integer DEFAULT NEXTVAL('custom_report_seq') CONSTRAINT pk_custom_report PRIMARY KEY,
	o_id integer REFERENCES organisation(id) ON DELETE CASCADE,
	name text,
	type text,								-- oversight || lqas
	config text								-- Custom report columns as json object
	);
ALTER TABLE custom_report OWNER TO ws;
CREATE UNIQUE INDEX custom_report_name ON custom_report(o_id, name);

-- table name is used by "results databases" to store result data for this form
DROP TABLE IF EXISTS form CASCADE;
CREATE TABLE form (
	f_id INTEGER DEFAULT NEXTVAL('f_seq') CONSTRAINT pk_form PRIMARY KEY,
	s_id INTEGER REFERENCES survey ON DELETE CASCADE,
	name text,
	label text,
	table_name text,
	parentForm integer not null default 0,
	parentQuestion integer not null default 0,
	repeats text,
	path text,
	form_index int default -1,					-- Temporary data used by the online editor
	reference boolean default false,
	merge boolean default false,
	replace boolean default false
	);
ALTER TABLE form OWNER TO ws;

DROP TABLE IF EXISTS listname CASCADE;
CREATE TABLE listname (
	l_id INTEGER DEFAULT NEXTVAL('l_seq') CONSTRAINT pk_listname PRIMARY KEY,
	s_id integer references survey on delete cascade, 
	name text
	);
ALTER TABLE listname OWNER TO ws;
CREATE UNIQUE INDEX listname_name ON listname(s_id, name);

-- q_itext references the text string in the translations table
DROP TABLE IF EXISTS question CASCADE;
CREATE TABLE question (
	q_id INTEGER DEFAULT NEXTVAL('q_seq') CONSTRAINT pk_question PRIMARY KEY,
	f_id INTEGER REFERENCES form ON DELETE CASCADE,
	l_id integer default 0,
	seq INTEGER,
	qName text NOT NULL,						-- Name that conforms to ODK restrictions
	column_name text,							-- Name of column in results table, qname with postgres constraints
	display_name text,							-- Name displayed to user
	column_name_applied boolean default false,	-- If set true column name has been added to results
	qType text,									-- Question type, select, begin repeat (also int, decimal for legacy reasons)
	dataType text,								-- Decimal, int etc
	question text,
	qtext_id text,
	defaultAnswer text,
	info text,
	infotext_id text,
	visible BOOLEAN default true,
	source text,
	source_param text,
	readonly BOOLEAN default false,
	mandatory BOOLEAN default false,
	relevant text,
	calculate text,
	qConstraint text,
	constraint_msg text,
	required_msg text,
	appearance text,
	parameters text,
	enabled BOOLEAN default true,
	path text,
	nodeset text,						-- the xpath to an itemset containing choices, includes filter defn
	nodeset_value text,					-- name of value column for choice list when stored as an itemset
	nodeset_label text,					-- name of label column for choice list when stored as an itemset
	cascade_instance text,				-- Identical to list name (deprecate)
	list_name text,						-- Name of a set of options common across multiple questions
	published boolean default false,		-- Set true when a survey has been published for data collection
										--  Once a survey has been published there are constraints on the
										--  changes that can be applied to question definitions
	soft_deleted boolean default false,	-- Set true if a question has been deleted and has also been published
										-- If the question hasn't been published then it can be removed from the survey
	autoplay text,
	accuracy text,						-- gps accuracy at which a reading is automatically accepted
	linked_target text,					-- Id of a survey whose hrk is populated here
	compressed boolean default false,	-- Will put all answers to select multiples into a single column
	external_choices text,				-- Set to yes if choices are external
	external_table text,					-- The table containing the external choices
	intent text							-- ODK intent attribute
	);
ALTER TABLE question OWNER TO ws;
CREATE INDEX qtext_id_sequence ON question(qtext_id);
CREATE INDEX infotext_id_sequence ON question(infotext_id);
CREATE UNIQUE INDEX qname_index ON question(f_id,qname) where soft_deleted = 'false';
CREATE INDEX q_f_id ON question(f_id);
	
DROP TABLE IF EXISTS option CASCADE;
CREATE TABLE option (
	o_id INTEGER DEFAULT NEXTVAL('o_seq') CONSTRAINT pk_option PRIMARY KEY,
	q_id integer,
	l_id integer references listname on delete cascade,
	seq INTEGER,
	label text,
	label_id text,
	oValue text,
	column_name text,
	display_name text,
	selected BOOLEAN,
	cascade_filters text,
	published boolean default false,
	externalfile boolean default false
	);
ALTER TABLE option OWNER TO ws;
CREATE INDEX label_id_sequence ON option(label_id);
CREATE index o_l_id ON option(l_id);

-- Server side calculates
DROP TABLE IF EXISTS ssc;
CREATE TABLE ssc (
	id INTEGER DEFAULT NEXTVAL('ssc_seq') CONSTRAINT pk_ssc PRIMARY KEY,
	s_id INTEGER REFERENCES survey ON DELETE CASCADE,
	f_id INTEGER,
	name text,
	type text,
	units varchar(20),
	function text,
	parameters text
	);
ALTER TABLE ssc OWNER TO ws;
CREATE UNIQUE INDEX SscName ON ssc(s_id, name);

-- Survey Forwarding (All notifications are stored in here, forward is a legacy name)
DROP TABLE IF EXISTS forward;
CREATE TABLE forward (
	id INTEGER DEFAULT NEXTVAL('forward_seq') CONSTRAINT pk_forward PRIMARY KEY,
	s_id INTEGER REFERENCES survey ON DELETE CASCADE,
	name text,
	enabled boolean,
	filter text,
	trigger text,
	target text,
	remote_s_id text,
	remote_s_name text,
	remote_user text,
	remote_password text,
	remote_host text,
	notify_details	text	,			-- JSON string
	tg_id integer default 0,			-- Reminder notifications
	period text						-- Reminder notifications
	);
ALTER TABLE forward OWNER TO ws;
CREATE UNIQUE INDEX ForwardDest ON forward(s_id, remote_s_id, remote_host);

-- Record sending of notification reminders
DROP TABLE IF EXISTS reminder;
CREATE TABLE reminder (
	id integer DEFAULT NEXTVAL('reminder_seq') CONSTRAINT pk_reminder PRIMARY KEY,
	n_id integer references forward(id) ON DELETE CASCADE,
	a_id integer references assignments(id) ON DELETE CASCADE,
	reminder_date timestamp with time zone
	);
ALTER TABLE reminder OWNER TO ws;

-- Log of all sent notifications (except for forwards which are recorded by the forward subscriber)
DROP TABLE IF EXISTS notification_log;
CREATE TABLE public.notification_log (
	id integer default nextval('notification_log_seq') not null PRIMARY KEY,
	o_id integer,
	p_id integer,
	s_id integer,
	notify_details text,
	status text,	
	status_details text,
	event_time TIMESTAMP WITH TIME ZONE,
	message_id integer,			-- Identifier from the message queue that triggered this notification
	type text					-- Notification type, submission || reminder || task
	);
ALTER TABLE notification_log OWNER TO ws;


-- form can be long, short, image, audio, video
DROP TABLE IF EXISTS translation;
CREATE TABLE translation (
	t_id INTEGER DEFAULT NEXTVAL('t_seq') CONSTRAINT pk_translation PRIMARY KEY,
	s_id INTEGER REFERENCES survey ON DELETE CASCADE,
	language text,
	text_id text,
	type text,
	value text,
	external boolean default false
	);
ALTER TABLE translation OWNER TO ws;
CREATE UNIQUE INDEX translation_index ON translation(s_id, language, text_id, type);
CREATE INDEX text_id_sequence ON translation(text_id);
CREATE INDEX language_sequence ON translation(language);
CREATE INDEX t_s_id_sequence ON translation(s_id);


DROP TABLE IF EXISTS language;
CREATE TABLE language (
	id INTEGER DEFAULT NEXTVAL('l_seq') CONSTRAINT pk_language PRIMARY KEY,
	s_id INTEGER REFERENCES survey ON DELETE CASCADE,
	seq int,
	language text	
	);
ALTER TABLE language OWNER TO ws;

-- Tables to manage settings

DROP SEQUENCE IF EXISTS ds_seq CASCADE;
CREATE SEQUENCE ds_seq START 1;
ALTER SEQUENCE ds_seq OWNER TO ws;

DROP TABLE IF EXISTS dashboard_settings CASCADE;
CREATE TABLE dashboard_settings (
	ds_id INTEGER DEFAULT NEXTVAL('ds_seq') CONSTRAINT pk_dashboard_settings PRIMARY KEY,
	ds_user_ident text,
	ds_seq INTEGER,
	ds_state text,
	ds_title text,
	ds_s_name text,
	ds_s_id integer,
	ds_type text,
	ds_region text,
	ds_lang text,
	ds_q_id INTEGER,
	ds_q_is_calc boolean default false,
	ds_date_question_id INTEGER,
	ds_question text,
	ds_fn text,
	ds_table text,
	ds_key_words text,
	ds_q1_function text,
	ds_group_question_id INTEGER,
	ds_group_question_text text,
	ds_group_type text,
	ds_layer_id integer,
	ds_time_group text,
	ds_from_date date,
	ds_to_date date,
	ds_filter text,
	ds_advanced_filter text,
	ds_subject_type text,
	ds_u_id integer
	);
alter table dashboard_settings add constraint ds_user_ident FOREIGN KEY (ds_user_ident)
	REFERENCES users (ident) MATCH SIMPLE
	ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE dashboard_settings OWNER TO ws;


DROP SEQUENCE IF EXISTS set_seq CASCADE;
CREATE SEQUENCE set_seq START 1;
ALTER SEQUENCE set_seq OWNER TO ws;

DROP TABLE IF EXISTS general_settings CASCADE;
CREATE TABLE general_settings (
	id INTEGER DEFAULT NEXTVAL('set_seq') CONSTRAINT pk_settings PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	key text,			-- Identifies type of setting such as "mf" managed forms
	settings text		-- JSON

	);
ALTER TABLE general_settings OWNER TO ws;


--- Task Management -----------------------------------
-- Cleanup old tables  
DROP TABLE IF EXISTS public.tasks CASCADE;
DROP TABLE IF EXISTS public.assignments CASCADE;
DROP TABLE IF EXISTS public.task_group CASCADE;

DROP SEQUENCE IF EXISTS assignment_id_seq CASCADE;
CREATE SEQUENCE assignment_id_seq START 1;
ALTER TABLE assignment_id_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS task_id_seq CASCADE;
CREATE SEQUENCE task_id_seq START 1;
ALTER TABLE task_id_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS task_history_seq CASCADE;
CREATE SEQUENCE task_histoy_seq START 1;
ALTER TABLE task_history_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS task_group_id_seq CASCADE;
CREATE SEQUENCE task_group_id_seq START 1;
ALTER TABLE task_group_id_seq OWNER TO ws;

CREATE TABLE public.task_group (
	tg_id integer NOT NULL DEFAULT nextval('task_group_id_seq') PRIMARY KEY,
	name text,
	p_id integer,
    address_params text,
    rule text,					-- The criteria for adding a new task to this group (JSON)
    source_s_id integer,			-- The source survey id for quick lookup from notifications engine
    target_s_id integer,
    email_details text,
    dl_dist integer				-- Download distance, same value is in the rule, needed here for selects
);
ALTER TABLE public.task_group OWNER TO ws;

CREATE TABLE public.tasks (
	id integer DEFAULT nextval('task_id_seq') NOT NULL PRIMARY KEY,
	tg_id integer,
	tg_name text,
	p_id integer,
	p_name text,
	title text,
	url text,
	survey_ident text,	
	survey_name text,
	created_at timestamp with time zone,
	schedule_at timestamp with time zone,
	schedule_finish timestamp with time zone,
	deleted_at timestamp with time zone,
	address text,
	update_id text,				-- Record to update
	initial_data_source text,	-- none || survey || task
	initial_data text,			-- Contains InstanceJson of data if data source is task
	repeat boolean,
	repeat_count integer default 0,
	guidance text,
	location_trigger text,
	location_group text,
	location_name text,
	deleted boolean,
	complete_all boolean default false,	-- Set true if all assignments associated to this task need to be completed
	show_dist integer						-- Distance in meters at which task will be downloaded
);
SELECT AddGeometryColumn('tasks', 'geo_point', 4326, 'POINT', 2);
SELECT AddGeometryColumn('tasks', 'geo_point_actual', 4326, 'POINT', 2);
ALTER TABLE public.tasks OWNER TO ws;

CREATE TABLE public.locations (
	id integer DEFAULT nextval('location_seq') NOT NULL PRIMARY KEY,
	o_id integer REFERENCES organisation ON DELETE CASCADE,
	locn_group text,
	locn_type text,
	name text,
	uid text
);
SELECT AddGeometryColumn('locations', 'the_geom', 4326, 'POINT', 2);
CREATE UNIQUE INDEX location_index ON locations(locn_group, name);
ALTER TABLE public.locations OWNER TO ws;

CREATE TABLE public.assignments (
	id integer NOT NULL DEFAULT nextval('assignment_id_seq') PRIMARY KEY,
	assignee integer,
	assignee_name text,			-- Name of assigned person
	email text,					-- Email to send the task to
	status text NOT NULL,		-- Current status: accepted || rejected || submitted || deleted || unsent || unsubscribed
	comment text,
	task_id integer REFERENCES tasks(id) ON DELETE CASCADE,
	action_link text,			-- Used with single shot web form tasks
	assigned_date timestamp with time zone,
	completed_date timestamp with time zone,		-- Date of submitted || rejected
	cancelled_date timestamp with time zone,
	deleted_date timestamp with time zone
);
ALTER TABLE public.assignments OWNER TO ws;

-- Table to manage state of user downloads of forms
DROP SEQUENCE IF EXISTS form_downloads_id_seq CASCADE;
CREATE SEQUENCE form_downloads_id_seq START 1;
ALTER TABLE form_downloads_id_seq OWNER TO ws;

DROP TABLE IF EXISTS public.form_downloads CASCADE;
CREATE TABLE public.form_downloads (
	id integer DEFAULT nextval('form_downloads_id_seq') NOT NULL PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	form_ident text,
	form_version text,
	device_id text,
	updated_time TIMESTAMP WITH TIME ZONE
);
ALTER TABLE public.form_downloads OWNER TO ws;

-- Tables to manage task completion and user location
-- Deprecate - use tasks
DROP SEQUENCE IF EXISTS task_completion_id_seq CASCADE;
CREATE SEQUENCE task_completion_id_seq START 1;
ALTER TABLE task_completion_id_seq OWNER TO ws;

-- deprecate use tasks
DROP TABLE IF EXISTS public.task_completion CASCADE;
CREATE TABLE public.task_completion (
	id integer DEFAULT nextval('task_completion_id_seq') NOT NULL PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	form_ident text,
	form_version int,
	device_id text,
	uuid text,		-- Unique identifier for the results
	completion_time TIMESTAMP WITH TIME ZONE
);
SELECT AddGeometryColumn('task_completion', 'the_geom', 4326, 'POINT', 2);
ALTER TABLE public.task_completion OWNER TO ws;

DROP SEQUENCE IF EXISTS user_trail_id_seq CASCADE;
CREATE SEQUENCE user_trail_id_seq START 1;
ALTER TABLE user_trail_id_seq OWNER TO ws;

DROP TABLE IF EXISTS public.user_trail CASCADE;
CREATE TABLE public.user_trail (
	id integer DEFAULT nextval('user_trail_id_seq') NOT NULL PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	device_id text,
	event_time TIMESTAMP WITH TIME ZONE
);
SELECT AddGeometryColumn('user_trail', 'the_geom', 4326, 'POINT', 2);
create index idx_user_trail_u_id on user_trail(u_id);
ALTER TABLE public.user_trail OWNER TO ws;

DROP SEQUENCE IF EXISTS log_report_seq CASCADE;
CREATE SEQUENCE log_report_seq START 1;
ALTER TABLE log_report_seq OWNER TO ws;

DROP TABLE IF EXISTS public.log_report CASCADE;
CREATE TABLE public.log_report (
	id integer DEFAULT nextval('log_report_seq') NOT NULL PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	device_id text,
	report text,
	upload_time TIMESTAMP WITH TIME ZONE
);
ALTER TABLE public.log_report OWNER TO ws;

DROP SEQUENCE IF EXISTS linked_forms_seq CASCADE;
CREATE SEQUENCE linked_forms_seq START 1;
ALTER TABLE linked_forms_seq OWNER TO ws;

DROP TABLE IF EXISTS public.linked_forms CASCADE;
CREATE TABLE public.linked_forms (
	id integer DEFAULT nextval('linked_forms_seq') NOT NULL PRIMARY KEY,
	Linked_s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	linked_table text,			-- deprecate
	number_records integer,		-- deprecate
	linker_s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	link_file text,
	user_ident text,
	download_time TIMESTAMP WITH TIME ZONE
);
ALTER TABLE public.linked_forms OWNER TO ws;

DROP SEQUENCE IF EXISTS form_dependencies_seq CASCADE;
CREATE SEQUENCE form_dependencies_seq START 1;
ALTER TABLE form_dependencies_seq OWNER TO ws;

DROP TABLE IF EXISTS public.form_dependencies CASCADE;
CREATE TABLE public.form_dependencies (
	id integer DEFAULT nextval('form_dependencies_seq') NOT NULL PRIMARY KEY,
	Linked_s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	linker_s_id integer REFERENCES survey(s_id) ON DELETE CASCADE
);
ALTER TABLE public.form_dependencies OWNER TO ws;

DROP SEQUENCE IF EXISTS survey_role_seq CASCADE;
CREATE SEQUENCE survey_role_seq START 1;
ALTER SEQUENCE survey_role_seq OWNER TO ws;

DROP TABLE IF EXISTS survey_role CASCADE;
create TABLE survey_role (
	id integer DEFAULT NEXTVAL('survey_role_seq') CONSTRAINT pk_survey_role PRIMARY KEY,
	s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	r_id integer REFERENCES role(id) ON DELETE CASCADE,
	enabled boolean,
	column_filter text,
	row_filter text
	);
ALTER TABLE survey_role OWNER TO ws;
CREATE UNIQUE INDEX survey_role_index ON public.survey_role(s_id, r_id);

DROP SEQUENCE IF EXISTS alert_seq CASCADE;
CREATE SEQUENCE alert_seq START 1;
ALTER SEQUENCE alert_seq OWNER TO ws;

DROP TABLE IF EXISTS alert CASCADE;
create TABLE alert (
	id integer DEFAULT NEXTVAL('alert_seq') CONSTRAINT pk_alert PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	status varchar(10),
	priority integer,
	updated_time TIMESTAMP WITH TIME ZONE,
	created_time TIMESTAMP WITH TIME ZONE,
	link text,
	message text,
	s_id integer,	-- Survey Id that the alert applies to
	m_id integer,	-- Managed form id that the alert applies to
	prikey integer	-- Primary key of survey for which the alert applies
);
ALTER TABLE alert OWNER TO ws;

DROP SEQUENCE IF EXISTS message_seq CASCADE;
CREATE SEQUENCE message_seq START 1;
ALTER SEQUENCE message_seq OWNER TO ws;

-- Very draft definition of Smap messaging
-- topic is
--    and email for direct (hack) notifications
--    form for a change to a form

DROP TABLE IF EXISTS message CASCADE;
create TABLE message (
	id integer DEFAULT NEXTVAL('message_seq') CONSTRAINT pk_message PRIMARY KEY,
	o_id integer REFERENCES organisation(id) ON DELETE CASCADE,
	topic text,
	description text,
	data text,
	outbound boolean,
	created_time TIMESTAMP WITH TIME ZONE,
	processed_time TIMESTAMP WITH TIME ZONE,
	status text
);
CREATE index msg_outbound ON message(outbound);
CREATE index msg_processing_time ON message(processed_time);
ALTER TABLE message OWNER TO ws;

DROP SEQUENCE IF EXISTS custom_query_seq CASCADE;
CREATE SEQUENCE custom_query_seq START 1;
ALTER SEQUENCE custom_query_seq OWNER TO ws;

DROP TABLE IF EXISTS custom_query CASCADE;
create TABLE custom_query (
	id integer DEFAULT NEXTVAL('custom_query_seq') CONSTRAINT pk_custom_query PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	name text,
	query text
	
);
ALTER TABLE custom_query OWNER TO ws;

DROP SEQUENCE IF EXISTS survey_settings_seq CASCADE;
CREATE SEQUENCE survey_settings_seq START 1;
ALTER SEQUENCE survey_settings_seq OWNER TO ws;

DROP TABLE IF EXISTS survey_settings CASCADE;
 create TABLE survey_settings (
	id integer DEFAULT NEXTVAL('survey_settings_seq') CONSTRAINT pk_survey_settings PRIMARY KEY,
	s_ident text,		-- Survey ident
	u_id integer,		-- User
	view text,			-- Overall view (json)
	map_view text,		-- Map view data
	chart_view text		-- Chart view data	
);
ALTER TABLE survey_settings OWNER TO ws;

--DROP SEQUENCE IF EXISTS survey_view_seq CASCADE;
--CREATE SEQUENCE survey_view_seq START 1;
--ALTER SEQUENCE survey_view_seq OWNER TO ws;

-- DROP TABLE IF EXISTS survey_view CASCADE;
-- create TABLE survey_view (
--	id integer DEFAULT NEXTVAL('survey_view_seq') CONSTRAINT pk_survey_view PRIMARY KEY,
--	s_id integer,		-- optional survey id
--	m_id integer,		-- optional managed id requires s_id to be set
--	query_id integer,	-- optional query id
--	view text,			-- Table view data
--	map_view text,		-- Map view data
--	chart_view text		-- Chart view data
	
--);
--ALTER TABLE survey_view OWNER TO ws;

--DROP SEQUENCE IF EXISTS user_view_seq CASCADE;
--CREATE SEQUENCE user_view_seq START 1;
--ALTER SEQUENCE user_view_seq OWNER TO ws;

--DROP TABLE IF EXISTS user_view CASCADE;
--create TABLE user_view (
--	id INTEGER DEFAULT NEXTVAL('user_view_seq') CONSTRAINT pk_user_view PRIMARY KEY,
--	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
--	v_id INTEGER REFERENCES survey_view(id) ON DELETE CASCADE,
--	access TEXT		-- read || write || write
--	);
--ALTER TABLE user_view OWNER TO ws;

--DROP SEQUENCE IF EXISTS default_user_view_seq CASCADE;
--CREATE SEQUENCE default_user_view_seq START 1;
--ALTER SEQUENCE default_user_view_seq OWNER TO ws;

--DROP TABLE IF EXISTS default_user_view CASCADE;
--create TABLE default_user_view (
--	id INTEGER DEFAULT NEXTVAL('default_user_view_seq') CONSTRAINT pk_default_user_view PRIMARY KEY,
--	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
--	s_id integer,		-- survey id
--	m_id integer,		-- managed id requires s_id to be set
--	query_id integer,	-- query id
--	v_id integer REFERENCES survey_view(id) ON DELETE CASCADE		-- view id
--	);
--ALTER TABLE default_user_view OWNER TO ws;

DROP SEQUENCE IF EXISTS report_seq CASCADE;
CREATE SEQUENCE report_seq START 1;
ALTER SEQUENCE report_seq OWNER TO ws;

DROP TABLE IF EXISTS report CASCADE;
create TABLE report (
	id INTEGER DEFAULT NEXTVAL('report_seq') CONSTRAINT pk_report PRIMARY KEY,
	o_id integer REFERENCES organisation(id) ON DELETE CASCADE,
	name text,				-- Report Name
	s_id int	,				-- Replace with many to many relationship
	url text
	);
ALTER TABLE report OWNER TO ws;

DROP SEQUENCE IF EXISTS replacement_seq CASCADE;
CREATE SEQUENCE replacement_seq START 1;
ALTER SEQUENCE replacement_seq OWNER TO ws;

DROP TABLE IF EXISTS replacement CASCADE;
create TABLE replacement (
	id INTEGER DEFAULT NEXTVAL('replacement_seq') CONSTRAINT pk_replacement PRIMARY KEY,
	old_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	old_ident text,				-- Survey ident of the replaced survey
	new_ident text				-- Survey ident of the new survey
	);
ALTER TABLE replacement OWNER TO ws;

DROP SEQUENCE IF EXISTS csv_seq CASCADE;
CREATE SEQUENCE csv_seq START 1;
ALTER SEQUENCE csv_seq OWNER TO ws;

DROP TABLE IF EXISTS csvtable CASCADE;
create TABLE csvtable (
	id integer default nextval('csv_seq') constraint pk_csvtable primary key,
	o_id integer references organisation(id) on delete cascade,
	s_id integer,					-- Survey id may be 0 for organisation level csv hence do not reference
	filename text,					-- Name of the CSV file
	headers text,
	survey boolean default false,	-- Set true if the data actually comes from a survey
	user_ident text,						-- Survey data from a survey needs tohave access authenticated so RBAC can be applied
	chart boolean default false,		-- Set true if the data is for a chart
	non_unique_key boolean default false,	-- Set true if the data does not have a unique key
	sqldef text,						-- The sql definition			
	ts_initialised TIMESTAMP WITH TIME ZONE
	);
ALTER TABLE csvtable OWNER TO ws;

CREATE SCHEMA csv AUTHORIZATION ws;

DROP SEQUENCE IF EXISTS du_seq CASCADE;
CREATE SEQUENCE du_seq START 1;
ALTER SEQUENCE du_seq OWNER TO ws;

DROP TABLE IF EXISTS disk_usage CASCADE;
create TABLE disk_usage (
	id integer default nextval('du_seq') constraint  pk_diskusage primary key,
	e_id integer,
	o_id integer,
	total bigint,					-- Total disk usage
	upload bigint,					-- Disk used in upload directory
	media bigint,					-- Disk used in media directory
	template bigint,					-- Disk used in template directory
	attachments bigint,				-- Disk used in attachments directory
	when_measured TIMESTAMP WITH TIME ZONE
	);
ALTER TABLE disk_usage OWNER TO ws;

DROP SEQUENCE IF EXISTS people_seq CASCADE;
CREATE SEQUENCE people_seq START 1;
ALTER SEQUENCE people_seq OWNER TO ws;

DROP TABLE IF EXISTS people;
create TABLE people (
	id integer default nextval('people_seq') constraint pk_people primary key,
	o_id integer,
	email text,								
	unsubscribed boolean default false,
	uuid text,								-- Uniquely identify this person
	when_unsubscribed TIMESTAMP WITH TIME ZONE,
	when_subscribed TIMESTAMP WITH TIME ZONE,
	when_requested_subscribe TIMESTAMP WITH TIME ZONE		-- prevent spamming
	);
ALTER TABLE people OWNER TO ws;

DROP SEQUENCE IF EXISTS apply_foreign_keys_seq CASCADE;
CREATE SEQUENCE apply_foreign_keys_seq START 1;
ALTER SEQUENCE apply_foreign_keys_seq OWNER TO ws;

DROP TABLE IF EXISTS apply_foreign_keys;
create TABLE apply_foreign_keys (
	id integer default nextval('apply_foreign_keys_seq') constraint pk_apply_foreign_keys primary key,
	update_id text,
	s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	qname text,
	instanceid text,
	prikey integer,
	table_name text,
	instanceIdLaunchingForm text,
	applied boolean default false,
	comment text,
	ts_created TIMESTAMP WITH TIME ZONE,
	ts_applied TIMESTAMP WITH TIME ZONE
	);
ALTER TABLE apply_foreign_keys OWNER TO ws;

-- billing
DROP SEQUENCE IF EXISTS bill_rates_seq CASCADE;
CREATE SEQUENCE bill_rates_seq START 1;
ALTER SEQUENCE bill_rates_seq OWNER TO ws;

DROP TABLE IF EXISTS bill_rates;
create TABLE bill_rates (
	id integer default nextval('bill_rates_seq') constraint pk_bill_rates primary key,
	o_id integer default 0,	-- If 0 then all organisations (In enterprise or server)
	e_id integer default 0,	-- If 0 then all enterprises (ie server level)
	rates text,				-- json object
	currency text,
	created_by text,
	ts_created TIMESTAMP WITH TIME ZONE,
	ts_applies_from TIMESTAMP WITH TIME ZONE
	);
create unique index idx_bill_rates on bill_rates(o_id, e_id, ts_applies_from);
ALTER TABLE bill_rates OWNER TO ws;

-- Audit
DROP SEQUENCE IF EXISTS last_refresh_seq CASCADE;
CREATE SEQUENCE last_refresh_seq START 1;
ALTER SEQUENCE last_refresh_seq OWNER TO ws;

DROP TABLE IF EXISTS last_refresh;
create TABLE last_refresh (
	id integer default nextval('last_refresh_seq') constraint pk_last_refresh primary key,
	o_id integer,
	user_ident text,
	refresh_time TIMESTAMP WITH TIME ZONE
	);
SELECT AddGeometryColumn('last_refresh', 'geo_point', 4326, 'POINT', 2);
ALTER TABLE last_refresh OWNER TO ws;

-- Group Surveys
DROP SEQUENCE IF EXISTS group_survey_seq CASCADE;
CREATE SEQUENCE group_survey_seq START 1;
ALTER SEQUENCE group_survey_seq OWNER TO ws;

DROP TABLE IF EXISTS group_survey;
create TABLE group_survey (
	id integer default nextval('group_survey_seq') constraint pk_group_survey primary key,
	u_ident text REFERENCES users(ident) ON DELETE CASCADE,
	s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	group_ident text REFERENCES survey(ident) ON DELETE CASCADE
	);
ALTER TABLE group_survey OWNER TO ws;