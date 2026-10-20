/**
Copyright (c) 2026 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.controllers;

import java.util.List;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.bind.annotation.RequestParam;

import uga.menik.csx370.models.Post;
import uga.menik.csx370.models.User;
import uga.menik.csx370.utility.Utility;
import uga.menik.csx370.services.PostService;
import uga.menik.csx370.services.UserService; 

/**
 * Handles /bookmarks and its sub URLs.
 * No other URLs at this point.
 * 
 * Learn more about @Controller here: 
 * https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html
 */
@Controller
@RequestMapping("/bookmarks")
public class BookmarksController {
    private final PostService postService;
    private final UserService userService;

    public BookmarksController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }


    /**
     * /bookmarks URL itself is handled by this.
     */
    @GetMapping
    // /bookmark page for user
    public ModelAndView webpage(@RequestParam (name = "error", required=false) String error) {
        // posts_page is a mustache template from src/main/resources/templates.
        // ModelAndView class enables initializing one and populating placeholders
        // in the template using Java objects assigned to named properties.
        ModelAndView mv = new ModelAndView("posts_page");
        User currentUser = userService.getLoggedInUser();
        if (currentUser == null) { // if user is not logged in, redirect to login pg
            mv.setViewName("redirect:/login");
            return mv;
        }

        try {
            // get all posts bookmarked by the user
            List<Post> posts = postService.getBookMarkedPosts(currentUser.getUserId());
            mv.addObject("posts", posts);
            mv.addObject("isNoContent", posts.isEmpty()); // check if there are no bookmarked posts
        } catch (Exception e) {
            mv.addObject("errorMessage", "Failed to load bookmarked posts.");
        }
        
        // Following line populates sample data.
        // You should replace it with actual data from the database.
        // Page selection remains a starter placeholder owned by the page feature.
        // Always reload selected posts so shared post features use real data.

        // If an error occured, you can set the following property with the
        // error message to show the error message to the user.
        // String errorMessage = "Some error occured!";
        // mv.addObject("errorMessage", errorMessage);

        // Enable the following line if you want to show no content message.
        // Do that if your content list is empty.
        // mv.addObject("isNoContent", true);

        return mv;
    }

    // bookmarking and unbookmarking
    @GetMapping("{postId}/bookmark/{isBookmark}")
    public String bookmarkPost(@PathVariable("postId") String postId, @PathVariable("isBookmark") boolean isBookmark) {
        User currentUser = userService.getLoggedInUser();
        if (currentUser == null) {
            return "redirect:/login";
        }
        // SQL INSERT/ DELETE for bookmarking/unbookmarking
        boolean success = postService.setBookmarked(postId, currentUser.getUserId(), isBookmark);

        if (!success) {
            String errorMessage = "Failed to " + (isBookmark ? "bookmark" : "unbookmark") + " the post.";
            String encodedErrorMessage = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            return "redirect:/bookmarks?error=" + encodedErrorMessage;
        }
        return "redirect:/bookmarks";
        
    }
}
