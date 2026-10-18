/**
Copyright (c) 2026 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.controllers;

import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.server.ResponseStatusException;

import uga.menik.csx370.models.Post;
import uga.menik.csx370.services.ProfileService;
import uga.menik.csx370.services.UserService;

/**
 * Handles /profile URL and its sub URLs.
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileController.class);

    // UserService has user login and registration related functions.
    private final UserService userService;
    private final ProfileService profileService;

    /**
     * See notes in AuthInterceptor.java regarding how this works 
     * through dependency injection and inversion of control.
     */
    @Autowired
    public ProfileController(UserService userService, ProfileService profileService) {
        this.userService = userService;
        this.profileService = profileService;
    }

    /**
     * This function handles /profile URL itself.
     * This serves the webpage that shows posts of the logged in user.
     */
    @GetMapping
    public ModelAndView profileOfLoggedInUser() {
        return profileOfSpecificUser(Integer.parseInt(userService.getLoggedInUser().getUserId()));
    }

    /**
     * This function handles /profile/{userId} URL.
     * This serves the webpage that shows posts of a speific user given by userId.
     * See comments in PeopleController.java in followUnfollowUser function regarding 
     * how path variables work.
     */
    @GetMapping("/{userId}")
    public ModelAndView profileOfSpecificUser(@PathVariable("userId") int userId) {
        if (userId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User ID must be positive.");
        }

        // See notes on ModelAndView in BookmarksController.java.
        ModelAndView mv = new ModelAndView("posts_page");
        mv.addObject("posts", List.of());
        mv.addObject("isNoContent", false);

        try {
            int viewerId = Integer.parseInt(userService.getLoggedInUser().getUserId());
            List<Post> posts = profileService.getPostsForUser(userId, viewerId);
            mv.addObject("posts", posts);
            mv.addObject("isNoContent", posts.isEmpty());
        } catch (SQLException e) {
            logger.error("Unable to load posts for profile {}", userId, e);
            mv.addObject("errorMessage", "Unable to load this profile's posts. Please try again.");
            mv.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return mv;
    }
    
}
