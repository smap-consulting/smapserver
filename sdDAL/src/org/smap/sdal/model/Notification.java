package org.smap.sdal.model;

public class Notification {
	public int id;
	public String name;
	public String trigger;
	public String target;
	public int s_id;						// Deprecate in favour of sIdent
	public String sIdent;
	public String filter;
	public String s_name;				// submission only
	public String remote_s_ident;	
	public boolean enabled;
	public String remote_s_name;
	public String remote_user;
	public String remote_password;
	public String remote_host;
	public boolean update_password;
	public String instanceId;
	public NotifyDetails notifyDetails;
	
	public int tgId;					// reminder only
	public String period;			// reminder only
	public String tg_name;			// reminder only
	
	public String project;		// Reports only
	
}
