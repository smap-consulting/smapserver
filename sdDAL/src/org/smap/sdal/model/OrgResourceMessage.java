package org.smap.sdal.model;

public class OrgResourceMessage {
	public int orgId;				
	public String resourceName;
	
	public OrgResourceMessage(int id, String name) {
		this.orgId = id;
		this.resourceName = name;
	}
}
