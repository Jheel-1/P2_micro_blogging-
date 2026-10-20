# Post features and hashtag search

This file describes the post features in the completed Plan B version.
See Readme.txt for the full project, group responsibilities and run instructions.

PostService loads like/comment counts and the logged-in viewer's saved states.
PostController saves likes, bookmarks and comments using the logged-in user.
Comments are ordered oldest first. Post lists are ordered newest first.
Post and comment text must be nonempty and at most 400 characters.

HashtagSearchController and Hashtags validate space-separated hashtags.
Search requires every requested tag, ignores letter case, and matches whole
tokens. For example, #fire does not match #fireworks. The active search checks
post content, while post creation also stores normalized hashtag rows.

The home feed, profiles, People page and bookmarks page now use real database
selection. They are connected to the shared post features. Activity notifications
are created for new likes, comments, follows and mentions. Bookmarks are private.

database_setup.sql contains a final demo snapshot. dml.sql explains the queries
and their page URLs. The unit and MySQL integration tests check the relevant
behavior, and the completed app was also checked in Chrome.
