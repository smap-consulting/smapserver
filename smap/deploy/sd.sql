-- 
-- Apply upgrade patches to survey definitions database
--

-- Clean up database
vacuum analyze;

-- Upgrade to:  13.08 from 13.07 =======

-- Upgrade to:  13.09 from 13.08 =======
-- None

-- Upgrade to:  13.10 from 13.09 =======
alter table upload_event add column form_status text;
alter table survey alter column blocked set default false;
update survey set blocked = 'false' where blocked is null;

-- Upgrade to:  13.11 from 13.10 =======
-- None

-- Upgrade to:  13.12 from 13.11 =======
alter table dashboard_settings add column ds_date_question_id INTEGER;
alter table dashboard_settings add column ds_time_group text;
alter table dashboard_settings add column ds_from_date date;
alter table dashboard_settings add column ds_to_date date;
alter table dashboard_settings add column ds_q_is_calc boolean default false;
alter table survey add column def_lang text;

CREATE SEQUENCE ssc_seq START 1;
ALTER SEQUENCE ssc_seq OWNER TO ws;

CREATE TABLE ssc (
	id INTEGER DEFAULT NEXTVAL('ssc_seq') CONSTRAINT pk_ssc PRIMARY KEY,
	s_id INTEGER REFERENCES survey ON DELETE CASCADE,
	f_id INTEGER,
	name text,
	type text,
	function text,
	parameters text
	);
ALTER TABLE ssc OWNER TO ws;
CREATE UNIQUE INDEX SscName ON ssc(s_id, name);

-- Upgrade to:  14.02 from 14.01 =======
insert into groups(id,name) values(4,'org admin');
alter table organisation add column changed_by text;
alter table organisation add column changed_ts TIMESTAMP WITH TIME ZONE;

-- Upgrade to:  14.03 from 14.02 =======
CREATE SEQUENCE forward_seq START 1;
ALTER SEQUENCE forward_seq OWNER TO ws;

CREATE TABLE forward (
	id INTEGER DEFAULT NEXTVAL('forward_seq') CONSTRAINT pk_forward PRIMARY KEY,
	s_id INTEGER REFERENCES survey ON DELETE CASCADE,
	enabled boolean,
	remote_s_id text,
	remote_s_name text,
	remote_user text,
	remote_password text,
	remote_host text
	);
ALTER TABLE forward OWNER TO ws;
CREATE UNIQUE INDEX ForwardDest ON forward(s_id, remote_s_id, remote_host);

ALTER TABLE subscriber_event alter column subscriber type text;
ALTER TABLE subscriber_event add column dest text;
ALTER TABLE upload_event add column orig_survey_ident text;
ALTER TABLE upload_event add column file_path text;

-- Upgrade to:  14.04 from 14.03 =======
ALTER TABLE forward alter column remote_s_id type text;

-- Upgrade to:  14.05 from 14.04 =======
ALTER TABLE upload_event add column update_id varchar(41);
ALTER TABLE ssc add column units varchar(20);
ALTER TABLE survey add column class varchar(10);
ALTER TABLE dashboard_settings add column ds_filter text;

CREATE SEQUENCE regions_seq START 10;
ALTER SEQUENCE regions_seq OWNER TO ws;

create TABLE regions (
	id INTEGER DEFAULT NEXTVAL('regions_seq') CONSTRAINT pk_regions PRIMARY KEY,
	o_id INTEGER REFERENCES organisation(id) ON DELETE CASCADE,
	table_name text,
	region_name text,
	geometry_column text
	);
ALTER TABLE regions OWNER TO ws;

-- INSERT into regions (o_id, table_name, region_name, geometry_column)
--	SELECT DISTINCT 1, f_table_name, f_table_name, f_geometry_column FROM geometry_columns 
--	WHERE type='MULTIPOLYGON' or type='POLYGON';
	
-- Upgrade to:  14.08 from 14.05 =======

-- Make deleting of surveys flow through to deleting of tasks
-- alter table tasks alter column form_id type integer using (form_id::integer); -- form_id replaced with survey_ident

-- Changes for survey editor:
-- alter table question add column list_name text;
-- update question set list_name = qname where list_name is null and qtype like 'select%';
alter table translation alter column t_id set DEFAULT NEXTVAL('t_seq');

-- Add survey editing and versioning

alter table survey add column version integer;
update survey set version = 1 where version is null;

CREATE SEQUENCE sc_seq START 1;
ALTER SEQUENCE sc_seq OWNER TO ws;

CREATE TABLE survey_change (
	c_id integer DEFAULT NEXTVAL('sc_seq') CONSTRAINT pk_survey_changes PRIMARY KEY,
	s_id integer REFERENCES survey ON DELETE CASCADE,				
		
	version integer,							
	changes text,								
												
	user_id integer,							
	updated_time TIMESTAMP WITH TIME ZONE		
	);
ALTER TABLE survey_change OWNER TO ws;

-- Add survey ident to identify surveys rather than using the survey id
alter table survey add column ident text;
CREATE UNIQUE INDEX SurveyKey ON survey(ident);
alter table upload_event add column ident text;
update survey set ident = s_id where ident is null;

alter table users add column current_survey_id integer;
alter table users add column language varchar(10);

-- Add administrator email
alter table organisation add column admin_email text;

-- Upgrade to:  14.09 from 14.08 =======

alter table survey add column model text;
alter table organisation add column can_edit boolean;

CREATE SEQUENCE form_downloads_id_seq START 1;
ALTER TABLE form_downloads_id_seq OWNER TO ws;

CREATE TABLE public.form_downloads (
	id integer DEFAULT nextval('form_downloads_id_seq') NOT NULL PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	form_ident text REFERENCES survey(ident) ON DELETE CASCADE,
	form_version text,
	device_id text,
	updated_time TIMESTAMP WITH TIME ZONE
);
ALTER TABLE public.form_downloads OWNER TO ws;

CREATE UNIQUE INDEX idx_organisation ON organisation(name);

-- Upgrade to:  14.10.2 from 14.09 =======
alter table users add column one_time_password varchar(36);
alter table users add column one_time_password_expiry timestamp;

alter table upload_event add column incomplete boolean default false;
update upload_event set incomplete = 'false';

-- Upgrade to:  14.11.1 from 14.10.2 =======

CREATE SEQUENCE task_completion_id_seq START 1;
ALTER TABLE task_completion_id_seq OWNER TO ws;

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

CREATE SEQUENCE user_trail_id_seq START 1;
ALTER TABLE user_trail_id_seq OWNER TO ws;

CREATE TABLE public.user_trail (
	id integer DEFAULT nextval('user_trail_id_seq') NOT NULL PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	device_id text,
	event_time TIMESTAMP WITH TIME ZONE
);
SELECT AddGeometryColumn('user_trail', 'the_geom', 4326, 'POINT', 2);
ALTER TABLE public.user_trail OWNER TO ws;

update users set email = null where trim(email) = '';

alter table assignments drop constraint if exists assignee;
alter table assignments add constraint assignee FOREIGN KEY (assignee)
	REFERENCES users (id) MATCH SIMPLE
	ON UPDATE NO ACTION ON DELETE CASCADE;

-- Upgrade to:  14.12 from 14.11 =======
alter table upload_event add column notifications_applied boolean;
alter table upload_event add column instanceid varchar(41);
alter table forward add column target text;
alter table forward add column notify_details text;
update forward set target = 'forward' where target is null;
alter table organisation add column smtp_host text;

-- Create notification_log table
CREATE SEQUENCE notification_log_seq START 1;
ALTER SEQUENCE notification_log_seq OWNER TO ws;

CREATE TABLE public.notification_log (
	id integer default nextval('notification_log_seq') not null PRIMARY KEY,
	o_id integer,
	notify_details text,
	status text,
	status_details text,
	event_time TIMESTAMP WITH TIME ZONE
	);
ALTER TABLE notification_log OWNER TO ws;

-- Create organisation level table
create TABLE server (
	smtp_host text
	);
ALTER TABLE server OWNER TO ws;

-- Upgrade to:  15.01 from 14.12 =======

-- Changes required for updating surveys via loading a csv file
ALTER TABLE option add column externalfile boolean default false;
ALTER TABLE survey_change add column apply_results boolean default false;
ALTER TABLE survey add column manifest text;

-- Changes required to Tasks page
ALTER TABLE task_group add column p_id integer;

-- Upgrade to: 15.03 from 15.02
alter table organisation add column email_domain text;
alter table server add column email_domain text;

-- Create the dynamic users for webform submission
CREATE SEQUENCE dynamic_users_seq START 1;
ALTER SEQUENCE dynamic_users_seq OWNER TO ws;

CREATE TABLE dynamic_users (
	id INTEGER DEFAULT NEXTVAL('dynamic_users_seq') CONSTRAINT pk_dynamic_users PRIMARY KEY,
	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
	survey_ident text,
	access_key varchar(41)
	);
ALTER TABLE dynamic_users OWNER TO ws;

-- Class in survey too small
alter table survey alter column class set data type text;

-- User configuration for PDF reports
alter table users add column settings text;
alter table users add column signature text;
alter table users drop constraint if exists users_email_key;
alter table organisation add column company_name text;
alter table organisation add column default_email_content text;

-- Upgrade to: 15.04 from 15.03
alter table survey add column task_file boolean;
alter table upload_event add column assignment_id integer;
alter table notification_log add column p_id integer;
alter table notification_log add column s_id integer;
alter table server add column email_user text;
alter table server add column email_password text;
alter table server add column email_port integer;
alter table organisation add column email_user text;
alter table organisation add column email_password text;
alter table organisation add column email_port integer;

-- Upgrade to: 15.09 from 15.04
drop index if exists formid_sequence ;
alter table question alter column qname set not null;
alter table question alter column visible set default 'true';
alter table question alter column mandatory set default 'false';
alter table question alter column readonly set default 'false';
alter table question alter column enabled set default 'true';
update question set visible = 'true' where visible is null;
update question set mandatory = 'false' where mandatory is null;
update question set readonly = 'false' where readonly is null;
update question set enabled = 'true' where enabled is null;

-- Starting to add the column_name explicitely to question
alter table question add column column_name text;
update question set column_name = lower(qname) where column_name is null;

-- Zarkman Inspector
alter table dynamic_users add column expiry timestamp;
alter table organisation add column ft_sync_incomplete boolean;
alter table tasks add column update_id text;
alter table project add column description text;

-- Logging errors from user devices
CREATE SEQUENCE log_report_seq START 1;
ALTER TABLE log_report_seq OWNER TO ws;

CREATE TABLE public.log_report (
	id integer DEFAULT nextval('log_report_seq') NOT NULL PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	device_id text,
	report text,
	upload_time TIMESTAMP WITH TIME ZONE
);
ALTER TABLE public.log_report OWNER TO ws;

-- Repeating instances
alter table tasks add column repeat boolean;

-- Generating reports
alter table organisation add column company_address text;
alter table organisation add column company_phone text;
alter table organisation add column company_email text;

-- Upgrade to: 15.10 from 15.09
alter table survey add column instance_name text;

-- Upgrade to: 15.11 from 15.10
update form set parentform = 0 where parentform is null;
alter table form alter column parentform set default 0;
alter table form alter column parentform set not null;
update form set parentquestion = 0 where parentquestion is null;
alter table form alter column parentquestion set default 0;
alter table form alter column parentquestion set not null;
alter table form add column form_index int default -1;

CREATE SEQUENCE l_seq START 1;
ALTER SEQUENCE l_seq OWNER TO ws;	

CREATE TABLE language (
	id INTEGER DEFAULT NEXTVAL('l_seq') CONSTRAINT pk_language PRIMARY KEY,
	s_id INTEGER REFERENCES survey ON DELETE CASCADE,
	seq integer,
	language text	
	);
ALTER TABLE language OWNER TO ws;

---------------------------------------------------------------------------------------
-- Add list name table
CREATE SEQUENCE l_seq START 1;
ALTER SEQUENCE l_seq OWNER TO ws;

CREATE TABLE listname (
	l_id integer default nextval('l_seq') constraint pk_listname primary key,
	s_id integer references survey on delete cascade, 
	name text
	);
ALTER TABLE listname OWNER TO ws;
CREATE UNIQUE INDEX listname_name ON listname(s_id, name);

drop index if exists q_id_sequence;
alter table option drop constraint if exists option_q_id_fkey;
alter table option add column l_id integer references listname on delete cascade;
alter table question add column l_id integer default 0;

insert into listname (s_id, name) select f.s_id, f.s_id || f.name || '_' || q.qname from question q, form f where q.qtype like 'select%' and q.f_id = f.f_id;
update option set l_id = sq.l_id from (select l.l_id, q.q_id from listname l, question q, form f where l.name = f.s_id || f.name || '_' || q.qname and f.f_id = q.f_id) as sq where sq.q_id = option.q_id and option.l_id is null;
update question set l_id = sq.l_id from (select l_id, q_id, o_id from option) as sq where sq.q_id = question.q_id and question.l_id = 0;
update question set l_id = 0 where l_id is null;

-------------
alter table survey_change add column success boolean default false;
alter table survey_change add column msg text;
update survey_change set success = true where success='false' and apply_results = true;
--------------
alter table question add column published boolean;
update question set published = true where published is null;
alter table question alter column published set default false;

alter table option add column published boolean;
update option set published = true where published is null;
alter table option alter column published set default false;

alter table question add column soft_deleted boolean default false;	 --set true if a question is deleted but not removed as there is a column in the results for this question
create unique index qname_index ON question(f_id,qname) where soft_deleted = 'false';
--------------
alter table option add column column_name text;
alter table question add column column_name_applied boolean default false;	-- Temporary column to ensure column name patches are only applied once
--------------- 
CREATE SEQUENCE map_seq START 1;
ALTER SEQUENCE map_seq OWNER TO ws;

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

-- Upgrade to: 15.12 from 15.11
alter table organisation add column website text;
alter table users add column password_reset boolean default false;

------ Performance Patches (For subscriber)
CREATE index o_l_id ON option(l_id);
CREATE index q_f_id ON question(f_id);

-- Upgrade to: 16.01 from 15.12
update form set repeats = subquery.calculate from (select f_id, calculate, path from question) as subquery
	where subquery.f_id = form.parentform and subquery.path = form.path || '_count';
delete from question q where q.calculate is not null and q.path in 
	(select f.path || '_count' from form f where  q.f_id = f.parentform);
	
-- Convert schedule_at to timestamp
alter table tasks add column schedule_atx timestamp with time zone;
update tasks set schedule_atx = schedule_at;
alter table tasks drop column schedule_at;
alter table tasks rename column schedule_atx to schedule_at;

CREATE SEQUENCE location_seq START 1;
ALTER SEQUENCE location_seq OWNER TO ws;	

-- uploading of locations
CREATE SEQUENCE location_seq START 1;
ALTER SEQUENCE location_seq OWNER TO ws;	

CREATE TABLE public.locations (
	id integer DEFAULT nextval('location_seq') NOT NULL PRIMARY KEY,
	o_id integer REFERENCES organisation ON DELETE CASCADE,
	locn_group text,
	locn_type text,
	name text,
	uid text
);
ALTER TABLE public.locations OWNER TO ws;

-- The following is deprecated but some code still refers to the column
alter table question add column repeatcount boolean default false;

-- Upgrade to: 16.02 from 16.01
alter table tasks add column location_trigger text;
alter table server add column version text;

-- Upgrade to: 16.03 from 16.02
alter table task_group add column rule text;
alter table task_group add column source_s_id integer;
alter table upload_event add column survey_notes text;
alter table upload_event add column location_trigger text;

-- Upgrade to 16.04 from 16.03
alter table project add column tasks_only boolean;
alter table server add column mapbox_default text;
alter table question add column required_msg text;
alter table question add column autoplay text;
alter table organisation add column locale text;

-- Add data processing table
alter table survey add column managed_id integer;

CREATE SEQUENCE dp_seq START 1;
ALTER SEQUENCE dp_seq OWNER TO ws;

create TABLE data_processing (
	id INTEGER DEFAULT NEXTVAL('dp_seq') CONSTRAINT pk_dp PRIMARY KEY,
	o_id INTEGER REFERENCES organisation(id) ON DELETE CASCADE,
	name text,
	type text,			-- lqas || manage
	description text,
	config text
	);
ALTER TABLE data_processing OWNER TO ws;

insert into groups(id,name) values(5,'manage');

-- Tasks
alter table tasks add column schedule_finish timestamp with time zone;
alter table tasks add column email text;
alter table tasks add column guidance text;
alter table users add column current_task_group_id integer;

-- Upgrade to 16.05 from 16.04
alter table survey add column loaded_from_xls boolean;
update survey set loaded_from_xls = 'true' where loaded_from_xls is null;
alter table survey alter column loaded_from_xls set default false;

CREATE SEQUENCE set_seq START 1;
ALTER SEQUENCE set_seq OWNER TO ws;

CREATE TABLE general_settings (
	id INTEGER DEFAULT NEXTVAL('set_seq') CONSTRAINT pk_settings PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	key text,
	settings text		-- JSON

	);
ALTER TABLE general_settings OWNER TO ws;

alter table tasks alter column schedule_finish type timestamp;
alter table tasks alter column schedule_at type timestamp;
alter table tasks add column repeat_count integer default 0;

-- Upgrade to 16.06 from 16.05
alter table survey add column hrk text;
alter table question add column list_name text;

-- Log table
CREATE SEQUENCE log_seq START 1;
ALTER SEQUENCE log_seq OWNER TO ws;

create TABLE log (
	id integer DEFAULT NEXTVAL('log_seq') CONSTRAINT pk_log PRIMARY KEY,
	log_time TIMESTAMP WITH TIME ZONE,
	s_id integer,
	o_id integer,
	user_ident text,
	event text,
	note text
	);
ALTER TABLE log OWNER TO ws;

-- Information on survey creation
alter table survey add column based_on text;
alter table survey add column created timestamp with time zone;

alter table server add column google_key text;

-- Upgrade to 16.07 from 16.06

insert into groups(id,name) values(6,'security');


CREATE SEQUENCE custom_report_seq START 2;
ALTER SEQUENCE custom_report_seq OWNER TO ws;

CREATE TABLE custom_report (
	id integer DEFAULT NEXTVAL('custom_report_seq') CONSTRAINT pk_custom_report PRIMARY KEY,
	o_id integer REFERENCES organisation(id) ON DELETE CASCADE,
	name text,
	type text,								-- oversight || lqas
	config text								-- custom report configuration as json object
	);
ALTER TABLE custom_report OWNER TO ws;
CREATE UNIQUE INDEX custom_report_name ON custom_report(o_id, name);

-- Linked forms
CREATE SEQUENCE linked_forms_seq START 1;
ALTER TABLE linked_forms_seq OWNER TO ws;

CREATE TABLE public.linked_forms (
	id integer DEFAULT nextval('linked_forms_seq') NOT NULL PRIMARY KEY,
	Linked_s_id integer,
	linked_table text,		-- deprecate
	number_records integer,	-- deprecate
	linker_s_id integer
);
ALTER TABLE public.linked_forms OWNER TO ws;

alter table question add column accuracy text;

-- Upgrade to 16.08 from 16.07
CREATE SEQUENCE role_seq START 1;
ALTER TABLE role_seq OWNER TO ws;

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

CREATE SEQUENCE user_role_seq START 1;
ALTER SEQUENCE user_role_seq OWNER TO ws;

create TABLE user_role (
	id INTEGER DEFAULT NEXTVAL('user_role_seq') CONSTRAINT pk_user_role PRIMARY KEY,
	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
	r_id INTEGER REFERENCES role(id) ON DELETE CASCADE
	);
ALTER TABLE user_role OWNER TO ws;

CREATE SEQUENCE survey_role_seq START 1;
ALTER SEQUENCE survey_role_seq OWNER TO ws;

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

alter table users add column temporary boolean default false;
update users set temporary = false where temporary is null;
alter table organisation add column timezone text;

-- Upgrade to 16.09 from 16.08

-- Create alert table
CREATE SEQUENCE alert_seq START 1;
ALTER SEQUENCE alert_seq OWNER TO ws;

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

-- Add action details for temporary user
alter table users add column action_details text;	-- Only used by temporary users
alter table users add column lastalert text;		-- Normal users
alter table users add column seen boolean;			-- Normal users

-- Delete entries from dashboard settings when the user is deleted
delete from dashboard_settings where ds_user_ident not in (select ident from users);
alter table dashboard_settings add constraint ds_user_ident FOREIGN KEY (ds_user_ident)
	REFERENCES users (ident) MATCH SIMPLE
	ON UPDATE NO ACTION ON DELETE CASCADE;
	
-- Upgrade to 16.12
-- The following may be required on some servers
-- alter table survey_change drop constraint survey_change_s_id_fkey;
-- alter table survey_change add constraint survey_change_survey FOREIGN KEY (s_id)
-- REFERENCES survey (s_id) MATCH SIMPLE
-- ON UPDATE NO ACTION ON DELETE CASCADE;

-- Add configuration options for fieldTask
alter table organisation add column ft_odk_style_menus boolean default true;
alter table organisation add column ft_review_final boolean default true;

-- Upgrade to 17.01
alter table survey add column pulldata text;
alter table linked_forms add column link_file text;
delete from linked_forms where linked_s_id not in (select s_id from survey);
alter table linked_forms add constraint lf_survey1 FOREIGN KEY (linked_s_id)
	REFERENCES survey (s_id) MATCH SIMPLE
	ON UPDATE NO ACTION ON DELETE CASCADE;
delete from linked_forms where linker_s_id not in (select s_id from survey);
alter table linked_forms add constraint lf_survey2 FOREIGN KEY (linker_s_id)
	REFERENCES survey (s_id) MATCH SIMPLE
	ON UPDATE NO ACTION ON DELETE CASCADE;
	
-- Upgrade to 17.02
alter table survey add column timing_data boolean;
alter table question add column display_name text;

-- Upgrade to 17.05
CREATE SEQUENCE message_seq START 1;
ALTER SEQUENCE message_seq OWNER TO ws;

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
ALTER TABLE message OWNER TO ws;

alter table users add column created timestamp with time zone;
alter table question add column linked_target text;

CREATE SEQUENCE custom_query_seq START 1;
ALTER SEQUENCE custom_query_seq OWNER TO ws;

create TABLE custom_query (
	id integer DEFAULT NEXTVAL('custom_query_seq') CONSTRAINT pk_custom_query PRIMARY KEY,
	u_id integer REFERENCES users(id) ON DELETE CASCADE,
	name text,
	query text
	
);
ALTER TABLE custom_query OWNER TO ws;

-- Create view tables
--CREATE SEQUENCE survey_view_seq START 1;
--ALTER SEQUENCE survey_view_seq OWNER TO ws;

--create TABLE survey_view (
--	id integer DEFAULT NEXTVAL('survey_view_seq') CONSTRAINT pk_survey_view PRIMARY KEY,
--	s_id integer,		-- optional survey id
--	m_id integer,		-- optional managed id requires s_id to be set
--	query_id integer,	-- optional query id
--	view text
--);
--ALTER TABLE survey_view OWNER TO ws;

--CREATE SEQUENCE user_view_seq START 1;
--ALTER SEQUENCE user_view_seq OWNER TO ws;

--create TABLE user_view (
--	id INTEGER DEFAULT NEXTVAL('user_view_seq') CONSTRAINT pk_user_view PRIMARY KEY,
--	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
--	v_id INTEGER REFERENCES survey_view(id) ON DELETE CASCADE,
--	access text		-- read || write || owner
--	);
--ALTER TABLE user_view OWNER TO ws;

--CREATE SEQUENCE default_user_view_seq START 1;
--ALTER SEQUENCE default_user_view_seq OWNER TO ws;

--create TABLE default_user_view (
--	id INTEGER DEFAULT NEXTVAL('default_user_view_seq') CONSTRAINT pk_default_user_view PRIMARY KEY,
--	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
--	s_id integer,		-- survey id
--	m_id integer,		-- managed id requires s_id to be set
--	query_id integer,	-- query id
--	v_id integer REFERENCES survey_view(id) ON DELETE CASCADE		-- view id
--	);
--ALTER TABLE default_user_view OWNER TO ws;

--alter TABLE survey_view add column map_view text;
--alter TABLE survey_view add column chart_view text;

CREATE SEQUENCE message_seq START 1;
ALTER SEQUENCE message_seq OWNER TO ws;

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
ALTER TABLE message OWNER TO ws;

alter table users add column created timestamp with time zone;
alter table survey add column key_policy text;
alter table linked_forms add column user_ident text;
alter table linked_forms add column download_time TIMESTAMP WITH TIME ZONE;

-- reports available for an organisation
CREATE SEQUENCE report_seq START 1;
ALTER SEQUENCE report_seq OWNER TO ws;

create TABLE report (
	id INTEGER DEFAULT NEXTVAL('report_seq') CONSTRAINT pk_report PRIMARY KEY,
	o_id integer REFERENCES organisation(id) ON DELETE CASCADE,
	name text,				-- Report Name
	s_id int				-- Replace with many to many relationship
	);
ALTER TABLE report OWNER TO ws;

-- Upgrade to 17.06
alter table server add column sms_url text;
delete from survey_change where changes like '%"action":"external option"%' ;

CREATE SEQUENCE form_dependencies_seq START 1;
ALTER TABLE form_dependencies_seq OWNER TO ws;

CREATE TABLE public.form_dependencies (
	id integer DEFAULT nextval('form_dependencies_seq') NOT NULL PRIMARY KEY,
	Linked_s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	linker_s_id integer REFERENCES survey(s_id) ON DELETE CASCADE
);
ALTER TABLE public.form_dependencies OWNER TO ws;

-- Upgrade to 17.08
ALTER TABLE upload_event add column audit_file_path text;
alter table question add column parameters text;
alter table question add column dataType text;

-- Upgrade to 17.09
alter table survey add column exclude_empty boolean;
alter table survey add column auto_updates text;

-- Upgrade to 17.10
alter table forward add column filter text;

-- Upgrade to 17.11
alter table organisation add column ft_send text;
alter table organisation add column ft_delete text;
update organisation set ft_delete = 'not set' where ft_delete is null;

-- Upgrade to 17.12
alter table server add column document_sync boolean;
alter table server add column doc_server text;
alter table server add column doc_server_user text;
alter table server add column doc_server_password text;

alter table survey add column meta text;
alter table report add column url text;

alter table survey add column group_survey_id integer default 0;
update survey set group_survey_id = 0 where group_survey_id is null;

alter table task_group add column target_s_id integer;
DROP INDEX IF EXISTS SurveyDisplayName;
alter table form add column reference boolean default false;
update form set reference = false where reference is null;

alter table organisation add column ft_number_tasks integer default 20;
alter table survey_change add column visible boolean default true;
update survey_change set visible = true where visible is null;

CREATE SEQUENCE replacement_seq START 1;
ALTER SEQUENCE replacement_seq OWNER TO ws;

create TABLE replacement (
	id INTEGER DEFAULT NEXTVAL('replacement_seq') CONSTRAINT pk_replacement PRIMARY KEY,
	old_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	old_ident text,				-- Survey ident of the replaced survey
	new_ident text				-- Survey ident of the new survey
	);
ALTER TABLE replacement OWNER TO ws;

alter table question add column compressed boolean default false;
alter table organisation add column ft_specify_instancename boolean default false;
alter table organisation add column ft_admin_menu boolean default false;
update organisation set ft_specify_instancename = false where ft_specify_instancename is null;
update organisation set ft_admin_menu = false where ft_admin_menu is null;

-- Upgrade to 18.01
alter table organisation add column ft_send_location text;
alter table translation add column external boolean default false;
update translation set external = false where external is null;
insert into groups(id,name) values(7,'view data');
alter table notification_log add column message_id integer;

-- Upgrade to 18.02
alter table question add column external_choices text;
alter table question add column external_table text;
alter table survey add column public_link text;

alter table form_downloads drop constraint IF EXISTS form_downloads_form_ident_fkey;
alter table task_completion drop constraint IF EXISTS task_completion_form_ident_fkey;
alter table form add column merge boolean default false;

-- Upgrade to 18.03
alter table survey add column hidden boolean default false;
alter table survey add column original_ident text;
update survey set hidden = 'true'  where deleted and ident like 's%\_%\_%' and not hidden;

-- Upgrade to 18.04
-- Create table to manage csv files
CREATE SEQUENCE csv_seq START 1;
ALTER SEQUENCE csv_seq OWNER TO ws;

create TABLE csvtable (
	id integer default nextval('csv_seq') constraint pk_csvtable primary key,
	o_id integer references organisation(id) on delete cascade,
	s_id integer,				-- Survey id may be 0 for organisation level csv hence do not reference
	filename text,				-- Name of the CSV file
	headers text,				-- Mapping between file headers and table headers
	ts_initialised TIMESTAMP WITH TIME ZONE
	);
ALTER TABLE csvtable OWNER TO ws;
CREATE SCHEMA csv AUTHORIZATION ws;

ALTER TABLE dashboard_settings add column ds_advanced_filter text;
alter table forward drop constraint if exists forward_s_id_fkey;	-- Notifications may now be transferred to a new survey if survey replaced

CREATE SEQUENCE du_seq START 1;
ALTER SEQUENCE du_seq OWNER TO ws;

create TABLE disk_usage (
	id integer default nextval('du_seq') constraint pk_diskusage primary key,
	o_id integer,
	total bigint,					-- Total disk usage
	upload bigint,					-- Disk used in upload directory
	media bigint,					-- Disk used in media directory
	template bigint,					-- Disk used in template directory
	attachments bigint,				-- Disk used in attachments directory
	when_measured TIMESTAMP WITH TIME ZONE
	);
ALTER TABLE disk_usage OWNER TO ws;

-- Upgrade to 18.05
alter table organisation add column billing_enabled boolean default false;

alter table csvtable add column survey boolean default false;
alter table csvtable add column user_ident text;
alter table csvtable add column chart boolean default false;
alter table csvtable add column non_unique_key boolean default false;
alter table csvtable add column sqldef text;

alter table assignments add column completed_date timestamp with time zone;

-- Upgrade to 18.06

-- update tasks table
alter table tasks drop column if exists type;
alter table tasks drop column if exists geo_type;
alter table tasks drop column if exists assigned_by;
alter table tasks drop column if exists from_date;
alter table tasks drop column if exists geo_linestring;
alter table tasks drop column if exists geo_polygon;

alter table tasks add column deleted boolean default false;
alter table tasks add column created_at timestamp with time zone;
alter table tasks add column deleted_at timestamp with time zone;

alter table tasks alter column schedule_at type timestamp with time zone;
alter table tasks alter column schedule_finish type timestamp with time zone;

SELECT AddGeometryColumn('tasks', 'geo_point_actual', 4326, 'POINT', 2);

alter table tasks add column p_name text;
update tasks t set p_name = (select name from project p where p.id = t.p_id ) where t.p_name is null;

alter table tasks add column survey_name text; 

alter table tasks add column tg_name text;
update tasks t set tg_name = (select name from task_group tg where tg.tg_id = t.tg_id ) where t.tg_name is null;

alter table tasks drop constraint if exists tasks_form_id_fkey;
alter table tasks drop constraint if exists tasks_tg_id_fkey;
alter table tasks drop constraint if exists tasks_p_id_fkey;

-- update assignments table
alter table assignments drop column if exists assigned_by;			
alter table assignments drop column if exists last_status_changed_date;		

alter table assignments alter column assigned_date type timestamp with time zone;

alter table assignments add column assignee_name text;
update assignments a set assignee_name = (select name from users u where u.id = a.assignee ) where a.assignee_name is null;

alter table assignments add column cancelled_date timestamp with time zone;
alter table assignments add column deleted_date timestamp with time zone;
alter table assignments add column completed_date timestamp with time zone;

alter table assignments drop constraint if exists assignee;
alter table assignments drop constraint if exists assigner;

-- Unsubscribing
CREATE SEQUENCE people_seq START 1;
ALTER SEQUENCE people_seq OWNER TO ws;

create TABLE people (
	id integer default nextval('people_seq') constraint pk_people primary key,
	o_id integer,
	email text,								
	unsubscribed boolean default false,
	uuid text,								-- Uniquely identify this person
	when_unsubscribed TIMESTAMP WITH TIME ZONE,
	when_subscribed TIMESTAMP WITH TIME ZONE
	);
ALTER TABLE people OWNER TO ws;

-- Add email to assignments
alter table assignments add column email text;
alter table assignments add column action_link text;
alter table tasks add column instance_id text;			-- ID of the record that prompted this task

-- Improve performance of user_trail and delete opeations on users table
create index idx_user_trail_u_id on user_trail(u_id);

-- Improve performance of queries that access user ident in upload_event
create index idx_ue_ident on upload_event(user_name);

alter table users add column single_submission boolean default false;
alter table tasks add column complete_all boolean default false;
alter table task_group add column email_details text;

alter table organisation add column email_task boolean;

-- Speed up loading of data into results db
alter table upload_event add column results_db_applied boolean default false;
create index idx_ue_applied on upload_event(results_db_applied);
update upload_event set results_db_applied = 'true' where not results_db_applied and ue_id in (select ue_id from subscriber_event where subscriber = 'results_db');

-- Prevent spamming
alter table people add column when_requested_subscribe TIMESTAMP WITH TIME ZONE;

alter table form add column replace boolean default false;
alter table organisation add column server_description text;

alter table organisation add column ft_image_size text;
update organisation set ft_image_size = 'not set' where ft_image_size is null;

-- Foreign keys
CREATE SEQUENCE apply_foreign_keys_seq START 1;
ALTER SEQUENCE apply_foreign_keys_seq OWNER TO ws;

create TABLE apply_foreign_keys (
	id integer default nextval('apply_foreign_keys_seq') constraint pk_apply_foreign_keys primary key,
	update_id text,
	s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	qname text,
	instanceid text,
	prikey integer,
	table_name text,
	applied boolean default false,
	comment text,
	ts_created TIMESTAMP WITH TIME ZONE,
	ts_applied TIMESTAMP WITH TIME ZONE
	);
ALTER TABLE apply_foreign_keys OWNER TO ws;

alter table server add column keep_erased_days integer default 0;
alter table organisation add column sensitive_data text;

-- Organisation select
CREATE SEQUENCE user_organisation_seq START 1;
ALTER SEQUENCE user_organisation_seq OWNER TO ws;

create TABLE user_organisation (
	id INTEGER DEFAULT NEXTVAL('user_organisation_seq') CONSTRAINT pk_user_organisation PRIMARY KEY,
	u_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
	o_id INTEGER REFERENCES organisation(id) ON DELETE CASCADE,
	settings text
	);
CREATE UNIQUE INDEX idx_user_organisation ON user_organisation(u_id,o_id);
ALTER TABLE user_organisation OWNER TO ws;

-- Version 18.12

-- Billing upgrade
drop table if exists billing;
drop sequence if exists bill_seq;

CREATE SEQUENCE bill_rates_seq START 1;
ALTER SEQUENCE bill_rates_seq OWNER TO ws;

create table bill_rates (
	id integer default nextval('bill_rates_seq') constraint pk_bill_rates primary key,
	o_id integer,	-- If 0 then all organisations (In enterprise or server)
	e_id integer,	-- If 0 then all enterprises (ie server level)
	rates text,		-- json object
	currency text,
	created_by text,
	ts_created TIMESTAMP WITH TIME ZONE,
	ts_applies_from TIMESTAMP WITH TIME ZONE
	);
alter table bill_rates OWNER TO ws;

alter table disk_usage add column e_id integer default 0;
update disk_usage set e_id = 0 where e_id is null;

insert into groups(id,name) values(8,'enterprise admin');
insert into groups(id,name) values(9,'server owner');

-- Save org id and enterprise id on upload
alter table upload_event add column o_id integer default 0;
alter table upload_event add column e_id integer default 0;
update upload_event ue set o_id = (select p.o_id from project p where p.id = ue.p_id) where o_id is null or o_id = 0; 

-- Enterprise
CREATE SEQUENCE enterprise_seq START 1;
ALTER SEQUENCE enterprise_seq OWNER TO ws;

create TABLE enterprise (
	id INTEGER DEFAULT NEXTVAL('enterprise_seq') CONSTRAINT pk_enterprise PRIMARY KEY,
	name text,
	changed_by text,
	changed_ts TIMESTAMP WITH TIME ZONE
	);
CREATE UNIQUE INDEX idx_enterprise ON enterprise(name);
ALTER TABLE enterprise OWNER TO ws;

alter table organisation add column e_id integer references enterprise(id) on delete cascade;
insert into enterprise(id, name, changed_by, changed_ts) values(1, 'Default', '', now());
update organisation set e_id = 1 where e_id is null or e_id = 0;
update upload_event set e_id = 1 where e_id is null or e_id = 0;
-- Clear all the externalfile options
delete from option where externalfile = 'true';

alter table forward add column name text;

-- Performance patches for message
CREATE index msg_outbound ON message(outbound);
CREATE index msg_processing_time ON message(processed_time);

-- Performance patches for upload event when checking for duplicate error messages
CREATE index ue_survey_ident ON upload_event(ident);

alter table users add column timezone text;

-- Make sure we can't create duplicate billing rate entries
create unique index idx_bill_rates on bill_rates(o_id, e_id, ts_applies_from);

alter table enterprise add column billing_enabled boolean default false;
alter table server add column billing_enabled boolean default false;
alter table log add column e_id integer;
update log set e_id = 1 where e_id is null or e_id = 0;
alter table dashboard_settings add column ds_subject_type text;
alter table dashboard_settings add column ds_u_id integer;

-- Organisation permissions
alter table organisation add column can_notify boolean default true;
alter table organisation add column can_use_api boolean default true;
alter table organisation add column can_submit boolean default true;
update organisation set can_notify = true, can_use_api = true, can_submit = true where can_notify is null;

-- Add display name to choices
alter table option add column display_name text;

-- Add additional information to upload event
alter table upload_event add column start_time timestamp with time zone;
alter table upload_event add column end_time timestamp with time zone;
alter table upload_event add column instance_name text;

-- Webform parameters
alter table organisation add column webform text;

alter table upload_event add column scheduled_start timestamp with time zone;

alter table survey add column hide_on_device boolean;

alter table apply_foreign_keys add column instanceIdLaunchingForm text;

-- Backward navigation
alter table organisation add column ft_backward_navigation text;
update organisation set ft_backward_navigation = 'not set' where ft_backward_navigation is null;
update organisation set ft_send = 'not set' where ft_send is null;

-- Upgrade to 19.02
alter table survey add column audit_location_data boolean;
update organisation set ft_send_location = 'not set' where ft_send_location is null;

-- Upgrade to 19.03
alter table question add column intent text;
alter table organisation add column ft_pw_policy integer default -1;
alter table translation alter column type type text;

-- Upgrade to 19.04
alter table tasks add column initial_data_source text;
update tasks set initial_data_source = 'none' where initial_data_source is null and update_id is null;
update tasks set initial_data_source = 'survey' where initial_data_source is null and update_id is not null;
update tasks set initial_data = null where initial_data_source is null or initial_data_source != 'task';

alter table organisation add column ft_navigation text;
update organisation set ft_navigation = 'not set' where ft_navigation is null;

CREATE SEQUENCE last_refresh_seq START 1;
ALTER SEQUENCE last_refresh_seq OWNER TO ws;

create TABLE last_refresh (
	id integer default nextval('last_refresh_seq') constraint pk_last_refresh primary key,
	o_id integer,
	user_ident text,
	refresh_time TIMESTAMP WITH TIME ZONE
	);
SELECT AddGeometryColumn('last_refresh', 'geo_point', 4326, 'POINT', 2);
ALTER TABLE last_refresh OWNER TO ws;

-- Change dl_dist to show_dist in tasks
alter table tasks add column show_dist integer;
update tasks set show_dist = dl_dist where show_dist is null;
--update tasks set show_dist = 0 where show_dist is null;
--alter table tasks drop column dl_dist;  -- Keep tempoarily in case a deployment needs to be reversed

alter table task_group add column dl_dist integer;

-- 19.05
alter table organisation add column ft_exit_track_menu boolean default false;
update organisation set ft_exit_track_menu = false where ft_exit_track_menu is null;
SELECT AddGeometryColumn('locations', 'the_geom', 4326, 'POINT', 2);
CREATE UNIQUE INDEX location_index ON locations(locn_group, name);

alter table tasks add column location_group text;
alter table tasks add column location_name text;

-- 19.06
alter table organisation add column set_as_theme boolean default false;
alter table survey add column track_changes boolean;

alter table forward add column trigger text;
update forward set trigger = 'submission' where trigger is null;

alter table forward add column tg_id integer default 0;
alter table forward add column period text;

CREATE SEQUENCE reminder_seq START 1;
ALTER SEQUENCE reminder_seq OWNER TO ws;

CREATE TABLE reminder (
	id integer DEFAULT NEXTVAL('reminder_seq') CONSTRAINT pk_reminder PRIMARY KEY,
	n_id integer references forward(id) ON DELETE CASCADE,
	a_id integer references assignments(id) ON DELETE CASCADE,
	reminder_date timestamp with time zone
	);
ALTER TABLE reminder OWNER TO ws;

alter table tasks add column survey_ident text;
update tasks t set survey_ident = (select ident from survey s where s.s_id = t.form_id ) where t.survey_ident is null;
update tasks t set survey_name = (select display_name from survey s where s.ident = t.survey_ident ) where t.survey_name is null;

alter table notification_log add column type text;

-- Upgrade 19.07
alter table organisation add column navbar_color text;
update organisation set navbar_color = '#2c3c28' where navbar_color is null;
alter table organisation add column can_sms boolean default false;

-- Default key policy is now 'merge', policy of 'add' is to be replaced with 'merge' as it is no longer supported
update survey set key_policy = 'merge' where key_policy = 'none';
update survey set key_policy = 'merge' where key_policy = 'add';

CREATE SEQUENCE group_survey_seq START 1;
ALTER SEQUENCE group_survey_seq OWNER TO ws;

create TABLE group_survey (
	id integer default nextval('group_survey_seq') constraint pk_group_survey primary key,
	u_ident text REFERENCES users(ident) ON DELETE CASCADE,
	s_id integer REFERENCES survey(s_id) ON DELETE CASCADE,
	group_ident text REFERENCES survey(ident) ON DELETE CASCADE
	);
ALTER TABLE group_survey OWNER TO ws;

CREATE SEQUENCE survey_settings_seq START 1;
ALTER SEQUENCE survey_settings_seq OWNER TO ws;

 create TABLE survey_settings (
	id integer DEFAULT NEXTVAL('survey_settings_seq') CONSTRAINT pk_survey_settings PRIMARY KEY,
	s_ident text,		-- Survey ident
	u_id integer,		-- User
	view text,			-- Overall view (json)
	map_view text,		-- Map view data
	chart_view text		-- Chart view data	
);
ALTER TABLE survey_settings OWNER TO ws;

alter table assignments add column comment text;

CREATE SEQUENCE re_seq START 1;
ALTER SEQUENCE re_seq OWNER TO ws;

CREATE TABLE record_event (
	id integer DEFAULT NEXTVAL('re_seq') CONSTRAINT pk_record_changes PRIMARY KEY,
	table_name text,								-- Main table containing unique key	
	key text,									-- HRK of change or notification
	instanceid text,								-- instance of change or notification	
	status text,									-- Status of event - determines how it is displayed
	event text,									-- created || change || task || reminder || deleted
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
