package com.example.langhexx.Model;

public class WritingTopics {
    private String id; // Firebase key for the topic
    private String topicName; // Display name of the topic (previously 'title' when it was the key)

    public WritingTopics() {
        // Default constructor required for calls to DataSnapshot.getValue(Topics.class)
        // Though we'll primarily be constructing this manually after fetching
    }

    // Constructor to use when creating Topic objects from Firebase data
    public WritingTopics(String id, String topicName) {
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

    // You might keep getTitle/setTitle if other parts of your app still expect it,
    // but ensure it maps to topicName for consistency in this context.
    // For clarity with the new structure, I'm using getTopicName/setTopicName.
    // If TopicAdapter uses getTitle(), you'll need to ensure this method returns topicName.
    public String getTitle() { // Compatibility if TopicAdapter uses getTitle()
        return topicName;
    }
}
