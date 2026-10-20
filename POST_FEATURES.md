# Post features and hashtag search

Implemented scope: likes, bookmarks, comments, counts, post dates, and hashtag search.
No new dependencies were added. No post creation, follow system, or new custom feature was implemented.

- `PostService`: JDBC queries for current viewer states, counts, ordered comments, comment inserts, likes/bookmarks, and hashtag searches.
- `Hashtags`: validates whitespace-separated hashtags (letters, numbers, underscores), deduplicates case-insensitively, and builds whole-tag patterns.
- `PostController`: uses the authenticated session user for writes; redirects to the updated post after success.
- `HashtagSearchController`: returns posts containing every requested hashtag, newest first; malformed input shows a message and blank input returns no posts.
- Shared templates: like/bookmark forms use POST; comments reject empty input and limit length to 400.
- `dml.sql`: documents SQL operations for this contribution.

## Integration with teammates

Home, profile, and bookmarks still use starter placeholder selection logic. Their chosen IDs are passed through `refreshPosts`, which reloads real posts and current metadata without implementing those separate page requirements. Missing IDs are omitted rather than displaying fictitious actionable posts. Replace the placeholder selection with your teammate's real selection and keep this refresh call, or use the same shared query when implementing their retrieval. Home feed rules, profile selection, and bookmark-list selection are not completed by this contribution.

Search works independently of these placeholders and searches the full post table. It reads hashtags from existing post content, so no hashtag indexing change to post creation is necessary. Requires MySQL 8's REGEXP_LIKE. Hashtags are case-insensitive whole tokens: #fire does not match #fireworks or #fire_alarm. Punctuation can surround a hashtag in post content. Search input must contain only whitespace-separated hashtags.

## Verification

Start the project's MySQL container and apply `database_setup.sql` if the tables are absent. Use existing database users and posts supplied by your teammates; this contribution does not overwrite or seed their data. Start Spring Boot on port 8081, log in, and search for a known hashtag. Test multiple tags including a post missing one tag and equal timestamps. On results and post details, check counts, timestamps, filled icons, repeated adds/removes, and independence between two logged-in users. Add comments and confirm oldest-first ordering after refresh. Try blank and overlong comments and malformed searches.

Regression tests under `src/test` use the starter JUnit dependency and JDBC test doubles; they do not claim to exercise MySQL. Docker must be running for live verification.
