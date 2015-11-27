

DROP TABLE IF EXISTS days;
CREATE TABLE days (
	day DATE CONSTRAINT pk_days PRIMARY KEY
	);

-- Populate days table with initial values from 2001 to 31/12/2099
INSERT INTO days select to_date('20000101', 'YYYYMMDD') +
 s.a from generate_series(0,36524) as s(a);
 
 ALTER TABLE days OWNER TO ws;
 
-- Reports

DROP SEQUENCE IF EXISTS reports_seq;
CREATE SEQUENCE reports_seq START 1;
ALTER SEQUENCE reports_seq OWNER TO ws;

DROP TABLE IF EXISTS reports CASCADE;
CREATE TABLE reports (
	r_id INTEGER DEFAULT NEXTVAL('reports_seq') CONSTRAINT prikey_reports PRIMARY KEY,	/* RSS (guid) */
	p_id INTEGER,
	project_name text,
	ident char(36),							/* Report identifier used to access the report */
	item_type text,							/* Video || Photo || Rich */				
	url text,								/* RSS, Article (URL) */
	html text,								/* HTML for embedded data */
	thumb_url text,
	data_url text,							/* url of geoJSON file containing data || parameters to retrieve live version of that data */
	live boolean,							/* Set true if the data is live */
	title text,								/* RSS, Article */
	category text,							/* Article (section) */
	description text,						/* RSS, Article */		
	the_geom geometry,						/* Polygon covered by report */
	start_time TIMESTAMP WITH TIME ZONE,	/* Date of last survey that collected data for this report */
	end_time TIMESTAMP WITH TIME ZONE,		/* Date of last survey that collected data for this report */
	pub_date TIMESTAMP WITH TIME ZONE,		/* Article (Modified Time) */
	author text,							/* Article */
	country text,
	region text,
	district text,
	community text,
	tags text,								/* Article TODO convert to hstore */
	width integer,							/* Width of Item */
	height integer,							/* Height of Item */
	t_width integer,						/* Thumbnail width */
	t_height integer,						/* Thumbnail height */ 
	_lock TIMESTAMP							/* used for optimistic locking */
	);
ALTER TABLE reports OWNER TO ws;

-- Change history

DROP SEQUENCE IF EXISTS change_history_seq;
CREATE SEQUENCE change_history_seq START 1;
ALTER SEQUENCE change_history_seq OWNER TO ws;

DROP SEQUENCE IF EXISTS changeset_seq;
CREATE SEQUENCE changeset_seq START 1;
ALTER SEQUENCE changeset_seq OWNER TO ws;

DROP TABLE IF EXISTS changeset CASCADE;
CREATE TABLE changeset (
	id INTEGER DEFAULT NEXTVAL('changeset_seq') CONSTRAINT pk_changeset PRIMARY KEY,
	s_id INTEGER,
  	user_name text,		/* User making change */
  	change_reason text,
  	description text,
  	reversed boolean default false,
  	reversed_by_user text,
	changed_ts TIMESTAMP WITH TIME ZONE
	);
ALTER TABLE changeset OWNER TO ws;

DROP TABLE IF EXISTS change_history;
CREATE TABLE change_history (
	id INTEGER DEFAULT NEXTVAL('change_history_seq') CONSTRAINT pk_change_history PRIMARY KEY,
	c_id INTEGER REFERENCES changeset(id) ON DELETE CASCADE,		/* Changeset Id */
  	q_id INTEGER,
  	r_id INTEGER,
  	oname text,
  	old_value text,
  	new_value text,
  	qname text,
  	qtype text,
  	tablename text,
  	question_column_name text,
  	option_column_name text
	);
ALTER TABLE change_history OWNER TO ws;