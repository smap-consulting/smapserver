package surveyMobileAPI.managers;

import java.util.ArrayList;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "forms")
public class XFormListProxy {
	
	
	@XmlElement(name="form")
	public ArrayList<XFormProxy> forms = new ArrayList<XFormProxy>();

	public XFormListProxy() {
		super();
	}
}
