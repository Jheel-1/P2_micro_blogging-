/**
Copyright (c) 2026 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.controllers;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import uga.menik.csx370.models.Post;
import uga.menik.csx370.utility.Utility;
import uga.menik.csx370.services.PostService;

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

    public BookmarksController(PostService postService) {
        this.postService = postService;
    }


    /**
     * /bookmarks URL itself is handled by this.
     */
    @GetMapping
    public ModelAndView webpage() {
        // posts_page is a mustache template from src/main/resources/templates.
        // ModelAndView class enables initializing one and populating placeholders
        // in the template using Java objects assigned to named properties.
        ModelAndView mv = new ModelAndView("posts_page");

        // Following line populates sample data.
        // You should replace it with actual data from the database.
        // Page selection remains a starter placeholder owned by the page feature.
        // Always reload selected posts so shared post features use real data.
        List<Post> posts = postService.refreshPosts(Utility.createSamplePostsListWithoutComments());
        mv.addObject("posts", posts);
        mv.addObject("isNoContent", posts.isEmpty());

        // If an error occured, you can set the following property with the
        // error message to show the error message to the user.
        // String errorMessage = "Some error occured!";
        // mv.addObject("errorMessage", errorMessage);

        // Enable the following line if you want to show no content message.
        // Do that if your content list is empty.
        // mv.addObject("isNoContent", true);

        return mv;
    }
    
}
