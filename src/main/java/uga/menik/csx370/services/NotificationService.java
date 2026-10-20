package uga.menik.csx370.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uga.menik.csx370.models.Notification;
import uga.menik.csx370.models.User;
import uga.menik.csx370.utility.Utility;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class NotificationService {
     
     private final DataSource dataSource;
     private final UserService userService;

     @Autowired
     public NotificationService(DataSource dataSource, UserService userService) {
          this.dataSource = dataSource;
          this.userService = userService;
     } // NotificationService

     public void createNotification(long recipientId, long actorId, String type, long targetId) {
          if (recipientId == actorId) return;

          String query = "INSERT INTO notifications (recipient_id, actor_id, type, target_id) VALUES (?, ?, ?, ?)";
        
          try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
               pstmt.setLong(1, recipientId);
               pstmt.setLong(2, actorId);
               pstmt.setString(3, type);
               pstmt.setLong(4, targetId);
               pstmt.executeUpdate();
          } catch (SQLException e) {
               e.printStackTrace();
          } // Try-catch
     } // createNotification


     public List<Notification> getNotificationsForUser(long userId) {
          List<Notification> notifications = new ArrayList<>();
          String query = "SELECT * FROM notifications WHERE recipient_id = ? ORDER BY created_at DESC";
        
          try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
               pstmt.setLong(1, userId);
               try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                         long id = rs.getLong("id");
                         long actorId = rs.getLong("actor_id");
                         String type = rs.getString("type");
                         long targetId = rs.getLong("target_id");
                         boolean isRead = rs.getBoolean("is_read");
                         Timestamp timestamp = rs.getTimestamp("created_at");
                         String formattedDate = Utility.formatDateTime(timestamp.toLocalDateTime());

                         User actor = userService.getUserById(actorId);
                         notifications.add(new Notification(id, userId, actor, type, targetId, isRead, formattedDate));
                    }
               }
          } catch (SQLException e) {
               e.printStackTrace();
          }
               return notifications;
     }

     public long getUnreadCount(long userId) {
          String query = "SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND is_read = FALSE";
          try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
               pstmt.setLong(1, userId);
               try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                         return rs.getLong(1);
                    } //if
               }
          } catch (SQLException e) {
               e.printStackTrace();
          }
          
          return 0;
     }

     public void markAllAsRead(long userId) {
          String query = "UPDATE notifications SET is_read = TRUE WHERE recipient_id = ?";
          try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(query)) {
               pstmt.setLong(1, userId);
               pstmt.executeUpdate();
          } catch (SQLException e) {
               e.printStackTrace();
          }
     }

} // NotificationService
