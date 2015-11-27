-- 
-- Apply upgrade patches to survey definitions database
--

-- Upgrade to:  13.08 from 13.07 =======
-- None
-- Upgrade to:  13.09 from 13.08 =======
-- None

-- Upgrade to:  13.10 from 13.09 =======
CREATE SEQUENCE change_history_seq START 1;
ALTER SEQUENCE change_history_seq OWNER TO ws;

-- Upgrade to:  13.11 from 13.10 =======
CREATE SEQUENCE changeset_seq START 1;
ALTER SEQUENCE changeset_seq OWNER TO ws;

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
  	tablename text
	);
ALTER TABLE change_history OWNER TO ws;

-- Upgrade to:  15.11 from 15.10 =======
alter table change_history add column question_column_name text;
alter table change_history add column option_column_name text;
