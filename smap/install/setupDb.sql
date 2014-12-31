CREATE USER ws WITH PASSWORD 'ws1234';

DROP SEQUENCE IF EXISTS sc_seq;
CREATE SEQUENCE sc_seq START 1;
ALTER SEQUENCE sc_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS s_seq;
CREATE SEQUENCE s_seq START 1;
ALTER SEQUENCE s_seq OWNER TO ws;
 
DROP SEQUENCE IF EXISTS f_seq;
CREATE SEQUENCE f_seq START 1;
ALTER SEQUENCE f_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS o_seq;
CREATE SEQUENCE o_seq START 1;
ALTER SEQUENCE o_seq OWNER TO ws;		

DROP SEQUENCE IF EXISTS q_seq;
CREATE SEQUENCE q_seq START 1;
ALTER SEQUENCE q_seq OWNER TO ws;	

DROP SEQUENCE IF EXISTS ssc_seq;
CREATE SEQUENCE ssc_seq START 1;
ALTER SEQUENCE ssc_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS forward_seq;
CREATE SEQUENCE forward_seq START 1;
ALTER SEQUENCE forward_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS notification_log_seq;
CREATE SEQUENCE notification_log_seq START 1;
ALTER SEQUENCE notification_log_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS t_seq;
CREATE SEQUENCE t_seq START 1;
ALTER SEQUENCE t_seq OWNER TO ws;	

DROP SEQUENCE IF EXISTS g_seq;
CREATE SEQUENCE g_seq START 1;
ALTER SEQUENCE g_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS ue_seq;
CREATE SEQUENCE ue_seq START 1;
ALTER SEQUENCE ue_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS se_seq;
CREATE SEQUENCE se_seq START 1;
ALTER SEQUENCE se_seq OWNER TO ws;

-- User management
DROP SEQUENCE IF EXISTS project_seq CASCADE;
CREATE SEQUENCE project_seq START 10;
ALTER SEQUENCE project_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS regions_seq CASCADE;
CREATE SEQUENCE regions_seq START 10;
ALTER SEQUENCE regions_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS organisation_seq CASCADE;
CREATE SEQUENCE organisation_seq START 10;
ALTER SEQUENCE organisation_seq OWNER TO ws;

DROP TABLE IF EXISTS organisation CASCADE;
create TABLE organisation (
	id INTEGER DEFAULT NEXTVAL('organisation_seq') CONSTRAINT pk_organisation PRIMARY KEY,
	name text,
	allow_email boolean,
	allow_facebook boolean,
	allow_twitter boolean,
	can_edit boolean,
	ft_delete_submitted boolean,
	ft_send_trail boolean,
	changed_by text,
	admin_email text,
	smtp_host text,				-- Set if email is enabled
	changed_ts TIMESTAMP WITH TIME ZONE
	);
CREATE UNIQUE INDEX idx_organisation ON organisation(name);
ALTER TABLE organisation OWNER TO ws;

DROP TABLE IF EXISTS project CASCADE;
create TABLE project (
	id INTEGER DEFAULT NEXTVAL('project_seq') CONSTRAINT pk_project PRIMARY KEY,
	o_id INTEGER REFERENCES organisation(id) ON DELETE CASCADE,
	name text,
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

DROP SEQUENCE IF EXISTS users_seq CASCADE;
CREATE SEQUENCE users_seq START 10;
ALTER SEQUENCE users_seq OWNER TO ws;

DROP TABLE IF EXISTS users CASCADE;
CREATE TABLE users (
	id INTEGER DEFAULT NEXTVAL('users_seq') CONSTRAINT pk_users PRIMARY KEY,
	ident text,
	password text,
	realm text,
	name text,
	language varchar(10),
	location text,
	has_gps boolean,
	has_camera boolean,
	has_barcode boolean,
	has_data boolean,
	has_sms boolean,
	phone_number text,
	email text UNIQUE,
	device_id text,
	max_dist_km integer,
	user_role text,
	current_project_id integer,		-- Set to the last project the user selected
	current_survey_id integer,		-- Set to the last survey the user selected
	one_time_password varchar(36),	-- For password reset
	one_time_password_expiry timestamp,	-- Time and date one time password expires
	o_id integer REFERENCES organisation(id) ON DELETE CASCADE
	);
CREATE UNIQUE INDEX idx_users_ident ON users(ident);
ALTER TABLE users OWNER TO ws;

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

-- Create an administrator and set up defaul values
insert into organisation(id, name, allow_email, allow_facebook, allow_twitter) values(1, 'Smap', 'true', 'true', 'true');

insert into users (id, ident, realm, password, o_id, name, email) 
	values (1, 'admin', 'smap', '9f12895fe9898cc306c45c9d3fcbc3d6', 1, 'Administrator', '');

insert into groups(id,name) values(1,'admin');
insert into groups(id,name) values(2,'analyst');
insert into groups(id,name) values(3,'enum');
insert into groups(id,name) values(4,'org admin');

insert into user_group (u_id, g_id) values (1, 1);
insert into user_group (u_id, g_id) values (1, 2);
insert into user_group (u_id, g_id) values (1, 3);
insert into user_group (u_id, g_id) values (1, 4);

insert into project (id, o_id, name) values (1, 1, 'A project');

insert into user_project (u_id, p_id) values (1 , 1);

-- Monitoring tables
DROP TABLE IF EXISTS upload_event CASCADE;
CREATE TABLE upload_event (
	ue_id INTEGER DEFAULT NEXTVAL('ue_seq') CONSTRAINT pk_upload_event PRIMARY KEY,
	s_id INTEGER,
	ident text,	-- Identifier used by survey
	p_id INTEGER,
	upload_time TIMESTAMP WITH TIME ZONE,
	user_name text,
	file_name text,
	file_path text,
	survey_name text,
	imei text,
	orig_survey_ident text,
	update_id varchar(41),
	instanceid varchar(41),
	status varchar(10),
	reason text,
	location text,
	form_status text,
	notifications_applied boolean,		-- Set after notifications are sent
	incomplete boolean default false,	-- odk will set this if sending attachments in multiple posts
	server_name text  -- Stores the server used to upload the results.  The url's of all attachments will reference this address
	);

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
	class varchar(10),
	model text,										-- JSON model of the survey for thingsat
	last_updated_time DATE
	);
ALTER TABLE survey OWNER TO ws;
DROP INDEX IF EXISTS SurveyDisplayName;
CREATE UNIQUE INDEX SurveyDisplayName ON survey(p_id, display_name);
DROP INDEX IF EXISTS SurveyKey;
CREATE UNIQUE INDEX SurveyKey ON survey(ident);

DROP TABLE IF EXISTS survey_change CASCADE;
CREATE TABLE survey_change (
	c_id integer DEFAULT NEXTVAL('sc_seq') CONSTRAINT pk_survey_changes PRIMARY KEY,
	s_id integer REFERENCES survey ON DELETE CASCADE,	-- Survey containing this version
		
	version integer,							-- Version of survey with these changes
	changes text,								-- Changes as json object
	
	user_id integer,							-- Person who made the changes
	updated_time TIMESTAMP WITH TIME ZONE		-- Time and date of change
	);
ALTER TABLE survey_change OWNER TO ws;

-- table name is used by "results databases" to store result data for this form
DROP TABLE IF EXISTS form CASCADE;
CREATE TABLE form (
	f_id INTEGER DEFAULT NEXTVAL('f_seq') CONSTRAINT pk_form PRIMARY KEY,
	s_id INTEGER REFERENCES survey ON DELETE CASCADE,
	name text,
	label text,
	table_name text,
	parentForm INTEGER,
	parentQuestion INTEGER,
	repeats text,
	path text
	);
ALTER TABLE form OWNER TO ws;

-- q_itext references the text string in the translations table
DROP TABLE IF EXISTS question CASCADE;
CREATE TABLE question (
	q_id INTEGER DEFAULT NEXTVAL('q_seq') CONSTRAINT pk_question PRIMARY KEY,
	f_id INTEGER REFERENCES form ON DELETE CASCADE,
	seq INTEGER,
	qName text,
	qType text,
	question text,
	qtext_id text,
	defaultAnswer text,
	info text,
	infotext_id text,
	visible BOOLEAN,
	source text,
	source_param text,
	readOnly BOOLEAN,
	mandatory BOOLEAN,
	relevant text,
	calculate text,
	qConstraint text,
	constraint_msg text,
	appearance text,
	enabled BOOLEAN,
	path text,
	nodeset text,
	nodeset_value text,
	nodeset_label text,
	cascade_instance text,
	list_name text				-- Name of a set of options common across multiple questions
	);
ALTER TABLE question OWNER TO ws;
DROP INDEX IF EXISTS formId_sequence;
CREATE UNIQUE INDEX formId_sequence ON question(f_id, seq);
CREATE INDEX qtext_id_sequence ON question(qtext_id);
CREATE INDEX infotext_id_sequence ON question(infotext_id);
	
DROP TABLE IF EXISTS option CASCADE;
CREATE TABLE option (
	o_id INTEGER DEFAULT NEXTVAL('o_seq') CONSTRAINT pk_option PRIMARY KEY,
	q_id INTEGER REFERENCES question ON DELETE CASCADE,
	seq INTEGER,
	label text,
	label_id text,
	oValue text,
	selected BOOLEAN,
	cascade_filters text
	);
ALTER TABLE option OWNER TO ws;
CREATE INDEX label_id_sequence ON option(label_id);
CREATE INDEX q_id_sequence ON option(q_id);

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
	enabled boolean,
	target text,
	remote_s_id text,
	remote_s_name text,
	remote_user text,
	remote_password text,
	remote_host text,
	notify_details	text				-- JSON string
	);
ALTER TABLE forward OWNER TO ws;
CREATE UNIQUE INDEX ForwardDest ON forward(s_id, remote_s_id, remote_host);

-- Log of all sent notifications (except for forwards which are recorded by the forward subscriber)
DROP TABLE IF EXISTS notification_log;
CREATE TABLE public.notification_log (
	id integer default nextval('notification_log_seq') not null PRIMARY KEY,
	notify_details text,
	status text,	
	status_details text,
	event_time TIMESTAMP WITH TIME ZONE
	);
ALTER TABLE notification_log OWNER TO ws;

-- Question group Deprecated, (only used by SmapMobile) replaced by start and end of group columns on question table
DROP TABLE IF EXISTS question_group;
CREATE TABLE question_group (
	g_id INTEGER CONSTRAINT pk_group PRIMARY KEY,
	qFirst INTEGER,
	qLast INTEGER
	);
ALTER TABLE question_group OWNER TO ws;

-- form can be long, short, image, audio, video
DROP TABLE IF EXISTS translation;
CREATE TABLE translation (
	t_id INTEGER DEFAULT NEXTVAL('t_seq') CONSTRAINT pk_translation PRIMARY KEY,
	s_id INTEGER REFERENCES survey ON DELETE CASCADE,
	language text,
	text_id text,
	type char(5),
	value text
	);
ALTER TABLE translation OWNER TO ws;
CREATE UNIQUE INDEX translation_index ON translation(s_id, language, text_id, type);
CREATE INDEX text_id_sequence ON translation(text_id);
CREATE INDEX language_sequence ON translation(language);
CREATE INDEX t_s_id_sequence ON translation(s_id);

-- Tables to manage dashboard settings

DROP SEQUENCE IF EXISTS ds_seq;
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
	ds_filter text
	);
ALTER TABLE dashboard_settings OWNER TO ws;



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


DROP SEQUENCE IF EXISTS task_group_id_seq CASCADE;
CREATE SEQUENCE task_group_id_seq START 1;
ALTER TABLE task_group_id_seq OWNER TO ws;

CREATE TABLE public.task_group (
	tg_id integer NOT NULL DEFAULT nextval('task_group_id_seq') PRIMARY KEY,
	name text,
    address_params text
);

ALTER TABLE public.task_group OWNER TO ws;

CREATE TABLE public.tasks (
	id integer DEFAULT nextval('task_id_seq') NOT NULL PRIMARY KEY,
	tg_id integer REFERENCES task_group ON DELETE CASCADE,
	type text,
	title text,
	url text,
	form_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	existing_record integer,
	assignment_mode text,
	repeats integer,
	initial_data text,
	need_gps boolean,
	need_camera boolean,
	need_barcode boolean,
	need_rfid boolean,
	priority integer,
	schedule_at date,
	due_date date,
	created_date date,
	created_by integer,
    from_date date,
    status text,
    dynamic_open boolean default 'true',
    address text,
    "number" text,
	street text,
	locality text,
	postcode text,
	country text,
	geo_type text,
	p_id integer REFERENCES project(id),
	CONSTRAINT creator FOREIGN KEY (created_by)
      REFERENCES users (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION
);
SELECT AddGeometryColumn('tasks', 'geo_linestring', 4326, 'LINESTRING', 2);
SELECT AddGeometryColumn('tasks', 'geo_polygon', 4326, 'POLYGON', 2);
SELECT AddGeometryColumn('tasks', 'geo_point', 4326, 'POINT', 2);
ALTER TABLE public.tasks OWNER TO ws;

CREATE TABLE public.assignments (
	id integer NOT NULL DEFAULT nextval('assignment_id_seq'),
	assigned_by integer,
	assignee integer,
	status text NOT NULL,
	task_id integer,
	assigned_date date,
	last_status_changed_date date,
	PRIMARY KEY (id),
	CONSTRAINT assignee FOREIGN KEY (assignee)
      REFERENCES users (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE CASCADE,
  	CONSTRAINT assigner FOREIGN KEY (assigned_by)
      REFERENCES users (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT task_cons FOREIGN KEY (task_id)
      REFERENCES tasks (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE CASCADE
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
	form_ident text REFERENCES survey(ident) ON DELETE CASCADE,
	form_version text,
	device_id text,
	updated_time TIMESTAMP WITH TIME ZONE
);
ALTER TABLE public.form_downloads OWNER TO ws;

-- Tables to manage task completion and user location
DROP SEQUENCE IF EXISTS task_completion_id_seq CASCADE;
CREATE SEQUENCE task_completion_id_seq START 1;
ALTER TABLE task_completion_id_seq OWNER TO ws;

DROP TABLE IF EXISTS public.task_completion CASCADE;
CREATE TABLE public.task_completion (
	id integer DEFAULT nextval('task_completion_id_seq') NOT NULL PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	form_ident text REFERENCES survey(ident) ON DELETE CASCADE,
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
ALTER TABLE public.user_trail OWNER TO ws;
