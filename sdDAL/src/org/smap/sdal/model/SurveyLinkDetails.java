package org.smap.sdal.model;

/*
 * Details of a link between two surveys
 */
public class SurveyLinkDetails {
	
	public int fromSurveyId = 0;
	public int fromFormId = 0;
	public int fromQuestionId = 0;
	
	public int toSurveyId = 0;	
	public int toQuestionId = 0;	
	
	public String getId() {
		return fromSurveyId + "_" + fromFormId + "_" + fromQuestionId + "_" + toSurveyId;
	}
}
