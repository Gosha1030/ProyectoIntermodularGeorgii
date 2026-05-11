package georgii.sytnik.thothtasks.ui.schedule;

public class DayBlock {
    public final boolean isTravel;
    public final String taskIdHex; // null for travel
    public final String title;
    public final int startMin;
    public final int endMin;
    public final boolean muted;
    public String placeText;

    // TASK
    public DayBlock(String taskIdHex, String title, int startMin, int endMin, boolean muted) {
        this.isTravel = false;
        this.taskIdHex = taskIdHex;
        this.title = title;
        this.startMin = startMin;
        this.endMin = endMin;
        this.muted = muted;
        this.placeText = null;
    }

    // TRAVEL
    public DayBlock(int startMin, int endMin, String line3) {
        this.isTravel = true;
        this.taskIdHex = null;
        this.title = "Travel";
        this.startMin = startMin;
        this.endMin = endMin;
        this.muted = false;
        this.placeText = line3;
    }
}