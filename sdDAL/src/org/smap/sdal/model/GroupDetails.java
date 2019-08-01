package org.smap.sdal.model;

public class GroupDetails {
	public int sId;
	public String surveyName;
	public String surveyIdent;
	
	public GroupDetails(int id, String name, String ident) {
		sId = id;
		surveyName = name;
		surveyIdent = ident;
	}
}
