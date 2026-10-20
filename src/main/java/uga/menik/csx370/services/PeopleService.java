/**
Copyright (c) 2026 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uga.menik.csx370.models.FollowableUser;
import uga.menik.csx370.utility.DisplayTime;

/**
 * This service contains people related functions.
 */
@Service
public class PeopleService {

    private final DataSource dataSource;

    @Autowired
    public PeopleService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * This function should query and return all users that 
     * are followable. The list should not contain the user 
     * with id userIdToExclude.
     */
    public List<FollowableUser> getFollowableUsers(String userIdToExclude) {
        List<FollowableUser> followableUsers = new ArrayList<>();

        final String sql = "SELECT u.userId, u.username, u.firstName, u.lastName, "
                + "(SELECT MAX(p.timestamp) FROM post p WHERE p.userId = u.userId) AS lastPost, "
                + "EXISTS(SELECT 1 FROM user_follow f WHERE f.followerId = ? "
                + "AND f.followeeId = u.userId) AS isFollowed "
                + "FROM user u WHERE u.userId <> ? ORDER BY u.firstName, u.lastName, u.userId";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userIdToExclude);
            pstmt.setString(2, userIdToExclude);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String userId = rs.getString("userId");
                    String firstName = rs.getString("firstName");
                    String lastName = rs.getString("lastName");

                    String lastActive = DisplayTime.format(rs.getObject("lastPost", LocalDateTime.class));
                    FollowableUser user = new FollowableUser(userId, firstName, lastName,
                            rs.getBoolean("isFollowed"), lastActive);
                    user.setUsername(rs.getString("username"));
                    followableUsers.add(user);
                }
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load people", e);
        }

        return followableUsers;
    }

    public boolean setFollowed(String followerId, String followeeId, boolean follow) {
        try {
            long follower = Long.parseLong(followerId);
            long followee = Long.parseLong(followeeId);
            if (follower <= 0 || followee <= 0 || follower == followee) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        String sql = follow
                ? "INSERT INTO user_follow (followerId, followeeId) VALUES (?, ?)"
                : "DELETE FROM user_follow WHERE followerId = ? AND followeeId = ?";
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean inserted = false;
                try (PreparedStatement statement = conn.prepareStatement(sql)) {
                    statement.setString(1, followerId);
                    statement.setString(2, followeeId);
                    statement.executeUpdate();
                    inserted = follow;
                } catch (SQLException e) {
                    // A retried follow is successful but must not send another notification.
                    if (!follow || e.getErrorCode() != 1062) throw e;
                }
                if (inserted) {
                    NotificationService.insert(conn, Long.parseLong(followeeId),
                            Long.parseLong(followerId), "FOLLOW", Long.parseLong(followerId));
                }
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                return false;
            }
        } catch (SQLException e) {
            return false;
        }
    }

}
