/**
Copyright (c) 2026 Sami Menik, PhD. All rights reserved.

This is a project developed by Dr. Menik to give the students an opportunity to apply database concepts learned in the class in a real world project. Permission is granted to host a running version of this software and to use images or videos of this work solely for the purpose of demonstrating the work to potential employers. Any form of reproduction, distribution, or transmission of the software's source code, in part or whole, without the prior written consent of the copyright owner, is strictly prohibited.
*/
package uga.menik.csx370.controllers;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import uga.menik.csx370.models.Post;
import uga.menik.csx370.services.PostService;

/**
 * Handles /hashtagsearch URL and possibly others.
 * At this point no other URLs.
 */
@Controller
@RequestMapping("/hashtagsearch")
public class HashtagSearchController {
    private final PostService postService;

    public HashtagSearchController(PostService postService) {
        this.postService = postService;
    }

    /**
     * This function handles the /hashtagsearch URL itself.
     * This URL can process a request parameter with name hashtags.
     * In the browser the URL will look something like below:
     * http://localhost:8081/hashtagsearch?hashtags=%23amazing+%23fireworks
     * Note: the value of the hashtags is URL encoded.
     */
    @GetMapping
    public ModelAndView webpage(@RequestParam(name = "hashtags", defaultValue = "") String hashtags) {
        System.out.println("User is searching: " + hashtags);

        // See notes on ModelAndView in BookmarksController.java.
        ModelAndView mv = new ModelAndView("posts_page");

        // Search real posts, requiring every supplied hashtag.
        List<Post> posts = List.of();
        mv.addObject("isNoContent", false);
        try {
            posts = postService.searchHashtags(hashtags);
            mv.addObject("isNoContent", posts.isEmpty());
        } catch (IllegalArgumentException e) {
            mv.addObject("errorMessage", e.getMessage());
        } catch (IllegalStateException e) {
            mv.addObject("errorMessage", "Unable to search posts. Please try again.");
        }
        mv.addObject("posts", posts);
        
        return mv;
    }
    
}
