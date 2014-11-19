package taskModel;

public class PointEntry {
    public double lat;    // form or task
    public double lon;
    public long time;
    @Override
    public String toString() {
        return "Point(" + lat + "," + lon + ")";
    }
}
