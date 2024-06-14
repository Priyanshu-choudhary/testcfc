package com.example.WebSecurityExample.controller;

import com.example.WebSecurityExample.Pojo.Posts;
import com.example.WebSecurityExample.Pojo.User;
import com.example.WebSecurityExample.Service.PostService;
import com.example.WebSecurityExample.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@RestController
@RequestMapping("/Posts")
@CrossOrigin(origins = {"https://code-with-challenge.vercel.app", "http://localhost:5173"})
public class PostController {
    private static final Logger logger = LoggerFactory.getLogger(PostController.class);

    @Autowired
    private PostService postService;

    @Autowired
    private UserService userService;

    @GetMapping
    public ResponseEntity<?> getUserByUserName(@RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            logger.info("Fetching posts for user: {}", username);

            User users = userService.findByName(username);
            if (users == null) {
                logger.error("User not found: {}", username);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            List<Posts> all = users.getPosts();
            if (all == null || all.isEmpty()) {
                logger.info("No posts found for user: {}", username);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            Date lastModified = postService.getLastModifiedForUser(username);
            if (ifModifiedSince != null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
                Date ifModifiedSinceDate = dateFormat.parse(ifModifiedSince);
                if (!lastModified.after(ifModifiedSinceDate)) {
                    logger.info("Posts not modified since: {}", ifModifiedSince);
                    return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setLastModified(lastModified.getTime());
            logger.info("Returning posts for user: {}", username);
            return new ResponseEntity<>(all, headers, HttpStatus.OK);
        } catch (ParseException e) {
            logger.error("Error parsing If-Modified-Since header", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error fetching posts by username", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/filter")
    public ResponseEntity<?> getQuestionsByExactTags(@RequestParam List<String> tags, @RequestParam boolean exactMatch,
                                                     @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            logger.info("Filtering posts for user: {}", username);
            logger.info("Request tags: {}", tags);

            User user = userService.findByName(username);
            if (user == null) {
                logger.error("User not found: {}", username);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            Set<String> requestTags = new HashSet<>(tags);
            List<Posts> filteredPosts = user.getPosts().stream()
                    .filter(post -> {
                        Set<String> postTags = post.getTags() != null ? new HashSet<>(post.getTags()) : new HashSet<>();
                        boolean isMatch = postTags.containsAll(requestTags);
                        if (isMatch) {
                            logger.info("Match found: Post ID = {}", post.getId());
                        }
                        return isMatch;
                    })
                    .collect(Collectors.toList());

            logger.info("Filtered posts count: {}", filteredPosts.size());

            if (filteredPosts.isEmpty()) {
                logger.info("No posts found matching the tags: {}", tags);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            Date lastModified = postService.getLastModifiedForUser(username);
            if (ifModifiedSince != null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
                Date ifModifiedSinceDate = dateFormat.parse(ifModifiedSince);
                if (!lastModified.after(ifModifiedSinceDate)) {
                    logger.info("Posts not modified since: {}", ifModifiedSince);
                    return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setLastModified(lastModified.getTime());
            logger.info("Returning filtered posts for user: {}", username);
            return new ResponseEntity<>(filteredPosts, headers, HttpStatus.OK);
        } catch (ParseException e) {
            logger.error("Error parsing If-Modified-Since header", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            logger.error("Error filtering posts by tags", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/id/{myid}")
    public ResponseEntity<?> getUserById(@PathVariable String myid) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            logger.info("Fetching post by ID: {} for user: {}", myid, username);

            User users = userService.findByName(username);
            if (users == null) {
                logger.error("User not found: {}", username);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            List<Posts> collect = users.getPosts().stream().filter(x -> x.getId().equals(myid)).collect(Collectors.toList());
            if (collect.isEmpty()) {
                logger.info("Post not found with ID: {}", myid);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            Optional<Posts> userById = postService.getUserById(myid);
            if (userById.isPresent()) {
                logger.info("Returning post with ID: {}", myid);
                return new ResponseEntity<>(userById.get(), HttpStatus.OK);
            } else {
                logger.info("Post not found in database with ID: {}", myid);
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            logger.error("Error fetching post by ID", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping
    public ResponseEntity<Posts> createUser(@RequestBody Posts user) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            logger.info("Creating new post for user: {}", username);

            postService.createUser(user, username);
            logger.info("Post created successfully for user: {}", username);
            return new ResponseEntity<>(user, HttpStatus.CREATED);
        } catch (Exception e) {
            logger.error("Error creating post", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/id/{id}")
    public ResponseEntity<?> deleteUserById(@PathVariable String id) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth.getName();
            logger.info("Deleting post with ID: {} for user: {}", id, username);

            postService.deleteUserById(id, username);
            logger.info("Post deleted successfully with ID: {} for user: {}", id, username);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error deleting post by ID", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/id/{myId}")
    public ResponseEntity<?> updatePostById(@PathVariable String myId, @RequestBody Posts newPost) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication.getName();
            logger.info("Updating post with ID: {} for user: {}", myId, username);

            Posts updatedPost = postService.updatePost(myId, newPost, username);
            logger.info("Post updated successfully with ID: {} for user: {}", myId, username);
            return new ResponseEntity<>(updatedPost, HttpStatus.OK);
        } catch (RuntimeException e) {
            logger.error("Error updating post by ID", e);
            return new ResponseEntity<>(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }
}
