package exceptions;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class ServerException extends WebApplicationException {
	
	private static final long serialVersionUID = 1L;

	public ServerException() {
		super(Response.serverError().build());
	}
	
}
