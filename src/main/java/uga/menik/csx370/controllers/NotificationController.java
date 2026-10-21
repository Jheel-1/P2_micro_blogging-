package uga.menik.csx370.controllers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uga.menik.csx370.services.NotificationService;
import uga.menik.csx370.services.UserService;

/**
 * Controller responsible for handling web requests related to user notifications,
 * including viewing filtered notification lists, marking individual notifications 
 * as read, and marking notifications as read.
 */
@Controller
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final UserService userService;

    /**
     * Constructs new NotificationController with required notification and user services
     *
     * @param notificationService  service used to retrieve and manage notifications
     * @param userService          service used to retrieve current user details
     */
    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    } //NotificationController

    /**
     * Handles GET requests to create notifications page, populating view model 
     * with filtered notification lists, type selection dropdown options, and error messages if any occur.
     *
     * @param model  model instance used to pass attributes to the view
     * @param unread flag indicating whether to filter for unread notifications only
     * @param type   optional notification type filter string
     * @param error  optional error message passed via query parameters
     * @return the name of the notifications view template ("notifications_page")
     */
    @GetMapping
    public String getNotificationsPage(Model model,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(required = false) String error) {
        model.addAttribute("notifications", List.of());
        model.addAttribute("unreadOnly", unread);
        model.addAttribute("notificationsLoaded", false);
        model.addAttribute("errorMessage", error);
        model.addAttribute("typeOptions", NotificationService.TYPES.stream()
                .map(value -> Map.of("value", value, "selected", value.equals(type))).toList());
        try {
            model.addAttribute("notifications",
                    notificationService.getNotificationsForUser(viewerId(), unread, type));
            model.addAttribute("notificationsLoaded", true);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
        } catch (IllegalStateException e) {
            model.addAttribute("errorMessage", "Unable to load notifications. Please try again.");
        } //try-catch
        return "notifications_page";
    } //getNotificationsPage

    /**
     * Marks pecific notification as read for logged-in user.
     *
     * @param id unique identifier of notification to be updated
     * @return a redirect string back to notifications page, or an error redirect if update fails
     */
    @PostMapping("/{id}/read")
    public String markRead(@PathVariable long id) {
        try {
            notificationService.markAsRead(viewerId(), id);
            return "redirect:/notifications";
        } catch (IllegalStateException e) {
            return failedUpdate();
        } //try-catch
    } //markRead

    /**
     * Marks all notifications as read for logged-in user.
     *
     * @return a redirect string back to notifications page, or an error redirect if update fails
     */
    @PostMapping("/read-all")
    public String markAllRead() {
        try {
            notificationService.markAllAsRead(viewerId());
            return "redirect:/notifications";
        } catch (IllegalStateException e) {
            return failedUpdate();
        } //try-catch
    } //markAllRead

    /**
     * Retrieves user ID of currently authenticated viewer
     *
     * @return the user ID parsed as a long value
     */
    private long viewerId() {
        return Long.parseLong(userService.getLoggedInUser().getUserId());
    } //viewerId

    /**
     * Constructs redirection URL containing an encoded error message when 
     * a notification update or modification operation fails.
     *
     * @return a redirect string pointing to the notifications page with an error
     */
    private String failedUpdate() {
        return "redirect:/notifications?error=" + URLEncoder.encode(
                "Unable to update notifications. Please try again.", StandardCharsets.UTF_8);
    } //failedUpdate
} //NotificationController
