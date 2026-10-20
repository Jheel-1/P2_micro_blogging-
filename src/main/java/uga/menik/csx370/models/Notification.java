package uga.menik.csx370.models;

public class Notification {
    private final long id;
    private final long recipientId;
    private final User actor;
    private final String type;
    private final long targetId;
    private final boolean isRead;
    private final String createdAt;
    private final boolean postExists;

    public Notification(long id, long recipientId, User actor, String type, long targetId,
            boolean isRead, String createdAt, boolean postExists) {
        this.id = id;
        this.recipientId = recipientId;
        this.actor = actor;
        this.type = type;
        this.targetId = targetId;
        this.isRead = isRead;
        this.createdAt = createdAt;
        this.postExists = postExists;
    }

    public long getId() { return id; }
    public long getRecipientId() { return recipientId; }
    public User getActor() { return actor; }
    public String getType() { return type; }
    public long getTargetId() { return targetId; }
    public boolean isRead() { return isRead; }
    public String getCreatedAt() { return createdAt; }
    public boolean getIsLike() { return "LIKE".equals(type); }
    public boolean getIsComment() { return "COMMENT".equals(type); }
    public boolean getIsFollow() { return "FOLLOW".equals(type); }
    public boolean getIsMention() { return "MENTION".equals(type); }

    public String getTargetUrl() {
        if (getIsFollow()) return "/profile/" + actor.getUserId();
        return postExists ? "/post/" + targetId : "";
    }
}
