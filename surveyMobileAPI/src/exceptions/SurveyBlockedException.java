package exceptions;

public class SurveyBlockedException extends Exception {
	
	private static final long serialVersionUID = 1L;

	public SurveyBlockedException() {
		super("Survey Blocked - Contact Administrator");
	}
	
}
