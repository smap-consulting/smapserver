package utilities;

import java.net.URI;
import java.net.URISyntaxException;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class AuthenticationException extends WebApplicationException {
	
	private static final long serialVersionUID = 1L;
	
	public AuthenticationException() throws URISyntaxException {
	        super(Response.temporaryRedirect(new URI("../login.html")).build());
	}
}
