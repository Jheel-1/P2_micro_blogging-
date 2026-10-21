package uga.menik.csx370.models;

/**
 * Notification system containing details about user events, such as likes, comments,
 * follows, or mentions, including read status and associated targets.
 */
public class Notification {
    private final long id;
    private final long recipientId;
    private final User actor;
    private final String type;
    private final long targetId;
    private final boolean isRead;
    private final String createdAt;
    private final boolean postExists;

    /**
     * Constructs a new Notification instance with the specified attributes.
     *
     * @param id           unique identifier of the notification
     * @param recipientId  user ID of the notification recipient
     * @param actor        user who performed the action triggering the notification
     * @param type         type of notification (e.g., LIKE, COMMENT, FOLLOW, MENTION)
     * @param targetId     identifier of the target entity (e.g., post ID)
     * @param isRead       flag indicating whether the notification has been read
     * @param createdAt    timestamp when the notification was created
     * @param postExists   flag indicating whether the target post still exists
     */
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

    /**
     * Returns unique identifier of the notification.
     *
     * @return the notification ID
     */
    public long getId() { return id; }

    /**
     * Returns user ID of the notification recipient.
     *
     * @return the recipient user ID
     */
    public long getRecipientId() { return recipientId; }

    /**
     * Returns user actor who triggered the notification.
     *
     * @return the actor User object
     */
    public User getActor() { return actor; }

    /**
     * Returns type string of the notification.
     *
     * @return the notification type
     */
    public String getType() { return type; }

    /**
     * Returns identifier of the target entity associated with the notification.
     *
     * @return the target ID
     */
    public long getTargetId() { return targetId; }

    /**
     * Returns whether notification has been read by recipient.
     *
     * @return true if read, false otherwise
     */
    public boolean isRead() { return isRead; }

    /**
     * Returns creationn timestamp string of the notification.
     *
     * @return the creation timestamp
     */
    public String getCreatedAt() { return createdAt; }

    /**
     * Checks if notification type is a "LIKE".
     *
     * @return true if the notification type is LIKE, false otherwise
     */
    public boolean getIsLike() { return "LIKE".equals(type); }

    /**
     * Checks if notification type is a "COMMENT".
     *
     * @return true if the notification type is COMMENT, false otherwise
     */
    public boolean getIsComment() { return "COMMENT".equals(type); }

    /**
     * Checks if notification type is a "FOLLOW".
     *
     * @return true if the notification type is FOLLOW, false otherwise
     */
    public boolean getIsFollow() { return "FOLLOW".equals(type); }

    /**
     * Checks if notification type is a "MENTION".
     *
     * @return true if the notification type is MENTION, false otherwise
     */
    public boolean getIsMention() { return "MENTION".equals(type); }

    /**
     * Determines and returns appropriate target URL for redirection 
     * when a user clicks on the notification, based on its type and target existence.
     *
     * @return the target URL string, or an empty string if target is unavailable
     */
    public String getTargetUrl() {
        if (getIsFollow()) return "/profile/" + actor.getUserId();
        return postExists ? "/post/" + targetId : "";
    }
}
