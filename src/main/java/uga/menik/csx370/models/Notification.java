package uga.menik.csx370.models;

public class Notification {
     private long id;
     private long recipientId;
     private User actor;
     private String type;
     private long targetId;
     private boolean isRead;
     private String createdAt;

     public Notification(long id, long recipientId, User actor, String type, long targetId, boolean isRead, String createdAt) {
          this.id = id;
          this.recipientId = recepientId;
          this.actor = actor;
          this.type = type;
          this.targetId = targetId;
          this.isRead = isRead;
          this.createdAt = createdAT;
     } // Notification

     public long getId() { return id; }
     public long getRecipientId() { return recipientId; }
     public User getActor() { return actor; }
     public String getType() { return type; }
     public long getTargetId() { return TargetId; }
     public boolean isRead() { return isRead; }
     public String getCreatedAt() { return createdAt; }

     public boolean getIsLike() { return "LIKE".equals(type); }
     public boolean getIsComment() { return "COMMENT".equals(type); }
     public boolean getIsFollow() { return "FOLLOW".equals(type); }
     public boolean getIsMnetion() { return "MENTION".equals(type); }
) // Notification
