package vn.enghexteam.enghex.Model;

public class WritingExercise {
    private String id;
    private String title;
    private String script;

    public WritingExercise() {
        //
    }

    public WritingExercise(String id, String title, String script) {
        this.id = id;
        this.title = title;
        this.script = script;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }
}
