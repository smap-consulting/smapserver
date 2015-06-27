package taskModel;

import java.util.List;

import org.smap.sdal.model.Project;

import com.google.gson.annotations.SerializedName;

public class TaskResponse {

	public String message;
	public String status;
	public String deviceId;
	@SerializedName("data")
	public List<TaskAssignment> taskAssignments;
	public List<FormLocator> forms;
	public FieldTaskSettings settings;
	public List<TaskCompletionInfo> taskCompletionInfo;
	public List<PointEntry> userTrail;
	public List<Project> projects;
}
