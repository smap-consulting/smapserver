package org.smap.sdal.model;

public class Forward {
	private int id;
	private int s_id;		
	private String remote_s_ident;	
	private boolean enabled;
	private String remote_s_name;
	private String remote_user;
	private String remote_password;
	private String remote_host;
	private boolean update_password;
	
	// Getters
	public int getId() {return id;}; 
	public int getSId() {return s_id;}; 
	public String getRemoteIdent() {return remote_s_ident;}; 
	public String getRemoteSName() {return remote_s_name;}; 
	public boolean isEnabled() {return enabled;}; 
	public String getRemoteUser() { return remote_user;};
	public String getRemoteHost() { return remote_host;};
	public String getRemotePassword() { return remote_password;};
	public boolean isUpdatePassword() { return update_password;};
	
	// Setters
	public void setId(int v) { id = v;};
	public void setSId(int v) { s_id = v;};
	public void setRemoteIdent(String v) { remote_s_ident = v;};
	public void setRemoteSName(String v) { remote_s_name = v;};
	public void setEnabled(boolean v) { enabled = v;};
	public void setRemoteUser(String v) { remote_user = v;};
	public void setRemoteHost(String v) { remote_host = v;};
	public void setRemotePassword(String v) { remote_password = v;};
	public void setUpdatePassword(boolean v) { update_password = v;};
	

}
