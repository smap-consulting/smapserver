package taskModel;

public class FormLocator {
	public String ident;
	public String name;
	public int version;
	public String project;
	public int pid;				// project Id
	public boolean tasks_only;	// Set true if this form should not be available for ad-hoc tasks
	public String url;
	public String manifestUrl;
	public boolean hasManifest;
	public boolean dirty;	// Set true if the manifest has been updated and the client should refresh
}
