/**
Copyright (c) 2026 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uga.menik.csx370.models.FollowableUser;

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
    public List<FollowableUser> getFollowableUsers(String userIdToExclude) throws SQLException{
        List<FollowableUser> followableUsers = new ArrayList<>();

        // SQL Query to be executed under /people webpage
        final String sql = "SELECT u.userId, u.firstName, u.lastName, " +
            "       DATE_FORMAT(MAX(p.timestamp), '%b %d, %Y, %h:%i %p') AS lastActiveDate, " + // user's most recent post
            "       EXISTS(" + // already following the specified follower?
            "           SELECT 1 FROM user_follow uf " +
            "           WHERE uf.followerId = ? AND uf.followeeId = u.userId" + // if they follow each other
            "       ) AS isFollowed " + 
            "FROM user u " +
            "LEFT JOIN post p ON u.userId = p.userId " + // include users w/ no posts
            "WHERE u.userId != ? " + // exclude the logged in user in follower list
            "GROUP BY u.userId, u.firstName, u.lastName ";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userIdToExclude); // replace 1st ?
            pstmt.setString(2, userIdToExclude);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String userId = rs.getString("userId");
                    String firstName = rs.getString("firstName");
                    String lastName = rs.getString("lastName");
                    boolean isFollowed = rs.getBoolean("isFollowed");
                    String lastActiveDate = rs.getString("lastActiveDate");

                    if (lastActiveDate == null || lastActiveDate.trim().isEmpty()) { //.trim() to include whitespace
                        lastActiveDate = "Unknown"; // handle case where user has no posts
                    }

                    followableUsers.add(new FollowableUser(userId, firstName, lastName, isFollowed, lastActiveDate));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return followableUsers;
    }

    // inserts/ deletes a follower relationship
    public void setFollowerStatus(String followerId, String targetUserId, boolean follow) throws SQLException {
        String sql;
        if (follow) {
            sql = "INSERT INTO user_follow (followerId, followeeId) VALUES (?, ?)"; // when a user follows another user
        } else {
            sql = "DELETE FROM user_follow WHERE followerId = ? AND followeeId = ?";
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, followerId);
            pstmt.setString(2, targetUserId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
