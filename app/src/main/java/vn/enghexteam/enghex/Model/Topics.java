package vn.enghexteam.enghex.Model;

public class Topics {
    private String id;
    private String topicName;

    public Topics() {
        //
    }

    public Topics(String id, String topicName) {
        this.id = id;
        this.topicName = topicName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getTitle() {
        return topicName;
    }
}