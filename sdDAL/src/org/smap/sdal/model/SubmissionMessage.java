package org.smap.sdal.model;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

import org.smap.sdal.Utilities.GeneralUtilityMethods;

public class SubmissionMessage {
	public int sId;					// Legacy reference to survey - now use survey_ident
	public int taskId;
	public String survey_ident;
	public int pId;
	public String instanceId;
	public String from;
	public String subject;
	public String content;
	public String attach;
	public boolean include_references;	// Follow links to referenced surveys
	public boolean launchedOnly;
	private int emailQuestion;			// Legacy question identifier
	private String emailQuestionName;	// New question identifier
	public String emailMeta;
	public ArrayList<String> emails;
	public String target;
	public String user;
	public String scheme;
	public String server;
	public String basePath;
	public String serverRoot;
	
	public SubmissionMessage(
			int taskId,
			String survey_ident,
			int pId, 
			String instanceId, 
			String from, 
			String subject, 
			String content, 
			String attach, 
			boolean include_references,
			boolean launchedOnly,
			int emailQuestion,
			String emailQuestionName,
			String emailMeta,
			ArrayList<String> emails,
			String target,
			String user,
			String scheme,
			String server,
			String basePath) {
		
		this.taskId = taskId;
		this.survey_ident = survey_ident;
		this.pId = pId;
		this.instanceId = instanceId;
		this.from = from;
		this.subject = subject;
		this.content = content;
		this.attach = attach;
		this.include_references = include_references;
		this.launchedOnly = launchedOnly;
		this.emailQuestion = emailQuestion;
		this.emailQuestionName = emailQuestionName;
		this.emailMeta = emailMeta;
		this.emails = emails;
		this.target = target;
		this.user = user;
		this.scheme = scheme;
		this.server = server;
		this.basePath = basePath;
	}
	
	public boolean emailQuestionSet() {
		boolean set = false;
		if(emailQuestionName != null) {
			if(!emailQuestionName.equals("-1")) {
				set = true;
			}
		} else if(emailQuestion > 0) {
			set = true;
		}
		return set;
	}
	
	public String getEmailQuestionName(Connection sd) throws SQLException {
		String name = null;;
		
		if(emailQuestionName != null) {
			name = emailQuestionName;
		} else if(emailQuestion > 0) {
			name = GeneralUtilityMethods.getNameForQuestion(sd, emailQuestion);
		}
		
		return name;
	}
}
