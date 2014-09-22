package surveyMobileAPI.managers;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;

@XmlRootElement(name="form")
public class XFormProxy {

	@XmlAttribute(name="url")
	public String url;
	
	@XmlAttribute(name="id")
	public String id;
	
	@XmlAttribute(name="name")
	public String name;
	
	@XmlValue
	public String display_name;
	
	public XFormProxy() {
		super();
	}
}
