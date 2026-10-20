/**
Copyright (c) 2026 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import uga.menik.csx370.models.FollowableUser;
import uga.menik.csx370.models.User;
import uga.menik.csx370.services.PeopleService;
import uga.menik.csx370.services.UserService;

/**
 * Handles /people URL and its sub URL paths.
 */
@Controller
@RequestMapping("/people")
public class PeopleController {

    private final PeopleService peopleService;
    private final UserService userService;

    @Autowired
    public PeopleController(PeopleService peopleService, UserService userService) {
        this.peopleService = peopleService;
        this.userService = userService;
    }

    /**
     * Serves the /people web page.
     * 
     * Note that this accepts a URL parameter called error.
     * The value to this parameter can be shown to the user as an error message.
     * See notes in HashtagSearchController.java regarding URL parameters.
     */
    @GetMapping
    public ModelAndView webpage(@RequestParam(name = "error", required = false) String error) {
        // See notes on ModelAndView in BookmarksController.java.
        ModelAndView mv = new ModelAndView("people_page");

        // Verify logged in user
        User currentUser = userService.getLoggedInUser();
        if (currentUser == null) { // if user is not logged in, redirect to login pg
            mv.setViewName("redirect:/login");
            return mv;
        }
        try { // SQL query to list other users, if our user follows them, and their latest post date
            List<FollowableUser> followableUsers = peopleService.getFollowableUsers(currentUser.getUserId());
            mv.addObject("users", followableUsers);

            if (followableUsers.isEmpty()) { // display empty message when no other users are found
                mv.addObject("isNoContent", true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            error = "Failed to retrieve followable users: " + e.getMessage();
        }

        // If an error occured, you can set the following property with the
        // error message to show the error message to the user.
        // An error message can be optionally specified with a url query parameter too.
        String errorMessage = error;
        mv.addObject("errorMessage", errorMessage);

        // Enable the following line if you want to show no content message.
        // Do that if your content list is empty.
        // mv.addObject("isNoContent", true);
        
        return mv;
    }

    /**
     * This function handles user follow and unfollow.
     * Note the URL has parameters defined as variables ie: {userId} and {isFollow}.
     * Follow and unfollow is handled by submitting a get type form to this URL 
     * by specifing the userId and the isFollow variables.
     * Learn more here: https://www.w3schools.com/tags/att_form_method.asp
     * An example URL that is handled by this function looks like below:
     * http://localhost:8081/people/1/follow/false
     * The above URL assigns 1 to userId and false to isFollow.
     */
    @GetMapping("{userId}/follow/{isFollow}")
    public String followUnfollowUser(@PathVariable("userId") String userId,
            @PathVariable("isFollow") Boolean isFollow) {
        System.out.println("User is attempting to follow/unfollow a user:");
        System.out.println("\tuserId: " + userId);
        System.out.println("\tisFollow: " + isFollow);

<<<<<<< HEAD
        // Redirect the user if the comment adding is a success.
        // return "redirect:/people";
        User currentUser = userService.getLoggedInUser();
        if (currentUser == null) {
            return "redirect:/login";
=======
        String currentUserId = userService.getLoggedInUser() != null
                ? userService.getLoggedInUser().getUserId()
                : null;
        if (currentUserId != null && isFollow != null) {
            try {
                if (isFollow) {
                    peopleService.followUser(currentUserId, userId);
                } else {
                    peopleService.unfollowUser(currentUserId, userId);
                }
                return "redirect:/people";
            } catch (SQLException e) {
                e.printStackTrace();
            }
>>>>>>> ab3fca360b562a2dc6699c28d7a8c2438cbe44b4
        }

        try { // SQL INSERT/ DELETE query to add or remove a follower relationship 
            peopleService.setFollowerStatus(currentUser.getUserId(), userId, isFollow);
            return "redirect:/people";
        } catch (SQLException e) {
            e.printStackTrace();
            // Redirect the user with an error message if there was an error.
            String message = URLEncoder.encode("Failed to (un)follow the user: " + e.getMessage(),
                    StandardCharsets.UTF_8);
            return "redirect:/people?error=" + message;
        }
    }

}
