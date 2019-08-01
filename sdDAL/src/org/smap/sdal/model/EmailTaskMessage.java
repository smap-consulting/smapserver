package org.smap.sdal.model;

public class EmailTaskMessage {
	public int sId;
	public int pId;
	public int aId;
	public String instanceId;
	public String from;
	public String subject;
	public String content;
	public String attach;
	public String email;
	public String target;
	public String user;
	public String actionLink;

	
	public EmailTaskMessage(
			int sId, 
			int pId,
			int aId,
			String instanceId, 
			String from, 
			String subject, 
			String content, 
			String attach, 
			String email,
			String target,
			String user,
			String actionLink) {
		
		this.sId = sId;
		this.pId = pId;
		this.aId = aId;
		this.instanceId = instanceId;
		this.from = from;
		this.subject = subject;
		this.content = content;
		this.attach = attach;	
		this.email = email;
		this.target = target;
		this.user = user;
		this.actionLink = actionLink;
	}
}
