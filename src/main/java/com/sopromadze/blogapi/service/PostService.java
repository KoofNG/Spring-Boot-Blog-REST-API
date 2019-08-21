package com.sopromadze.blogapi.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import com.sopromadze.blogapi.exception.ResourceNotFoundException;
import com.sopromadze.blogapi.model.category.Category;
import com.sopromadze.blogapi.model.post.Post;
import com.sopromadze.blogapi.model.role.RoleName;
import com.sopromadze.blogapi.model.tag.Tag;
import com.sopromadze.blogapi.model.user.User;
import com.sopromadze.blogapi.payload.ApiResponse;
import com.sopromadze.blogapi.payload.PagedResponse;
import com.sopromadze.blogapi.payload.post.PostRequest;
import com.sopromadze.blogapi.payload.post.PostResponse;
import com.sopromadze.blogapi.repository.CategoryRepository;
import com.sopromadze.blogapi.repository.PostRepository;
import com.sopromadze.blogapi.repository.TagRepository;
import com.sopromadze.blogapi.repository.UserRepository;
import com.sopromadze.blogapi.security.UserPrincipal;
import com.sopromadze.blogapi.util.AppUtils;

@Service
public class PostService {
    private final PostRepository postRepository;

    private final UserRepository userRepository;
    
    private final CategoryRepository categoryRepository;
    
    private final TagRepository tagRepository;

    private static final Logger logger = LoggerFactory.getLogger(PostService.class);

    @Autowired
    public PostService(PostRepository postRepository, UserRepository userRepository, CategoryRepository categoryRepository, TagRepository tagRepository) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
    }

    public PagedResponse<PostResponse> getAllPosts(int page, int size){
        AppUtils.validatePageNumberAndSize(page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");

        Page<Post> posts = postRepository.findAll(pageable);
        
        List<PostResponse> postResponses = new ArrayList<PostResponse>();
        
        for(Post post: posts.getContent()) {
        	PostResponse postResponse = AppUtils.mapPostToPosteResponse(post);
        	
        	postResponses.add(postResponse);
        }

        return new PagedResponse<>(postResponses, posts.getNumber(), postResponses.size(), posts.getTotalElements(), posts.getTotalPages(), posts.isLast());
    }

    public PagedResponse<Post> getPostsByCreatedBy(String username, int page, int size){
        AppUtils.validatePageNumberAndSize(page, size);
        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
        Page<Post> posts = postRepository.findByCreatedBy(user.getId(), pageable);

        if(posts.getNumberOfElements() == 0){
            return new PagedResponse<>(Collections.emptyList(), posts.getNumber(), posts.getSize(), posts.getTotalElements(), posts.getTotalPages(), posts.isLast());
        }
        return new PagedResponse<>(posts.getContent(), posts.getNumber(), posts.getSize(), posts.getTotalElements(), posts.getTotalPages(), posts.isLast());
    }
    
    public PagedResponse<PostResponse> getPostsByCategory(Long id, int page, int size){
    	AppUtils.validatePageNumberAndSize(page, size);
    	Category category = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    	
    	Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC, "createdAt");
    	Page<Post> posts = postRepository.findByCategory(category, pageable);
    	
    	List<PostResponse> postResponses = new ArrayList<PostResponse>();
    	
    	for(Post post : posts.getContent()) {
    		PostResponse postResponse = AppUtils.mapPostToPosteResponse(post);
    		
    		postResponses.add(postResponse);
    	}
    	
    	return new PagedResponse<>(postResponses, posts.getNumber(), postResponses.size(), posts.getTotalElements(), posts.getTotalPages(), posts.isLast());
    }
    
    public PagedResponse<Post> getPostsByTag(Long id, int page, int size){
    	AppUtils.validatePageNumberAndSize(page, size);
    	
    	Tag tag = tagRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Tag", "id", id));
    	
    	Pageable pageable = PageRequest.of(page, size, Sort.Direction.DESC,"createdAt");
    	
    	Page<Post> posts = postRepository.findByTags(Arrays.asList(tag), pageable);
    	
    	List<Post> content = posts.getNumberOfElements() == 0 ? Collections.emptyList() : posts.getContent();
    	
    	return new PagedResponse<>(content, posts.getNumber(), posts.getSize(), posts.getTotalElements(), posts.getTotalPages(), posts.isLast());
    }

    public ResponseEntity<?> updatePost(Long id, PostRequest newPostRequest, UserPrincipal currentUser){
        Post post = postRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Post", "id", id));
        Category category = categoryRepository.findById(newPostRequest.getCategoryId()).orElseThrow(() -> new ResourceNotFoundException("Category", "id", newPostRequest.getCategoryId()));
        
        if (post.getUser().getId().equals(currentUser.getId()) || currentUser.getAuthorities().contains(new SimpleGrantedAuthority(RoleName.ROLE_ADMIN.toString()))){
        	List<Tag> tags = new ArrayList<Tag>();
            
            for(String name : newPostRequest.getTags()) {
            	Tag tag = tagRepository.findByName(name);
            	tag = tag == null ? tagRepository.save(new Tag(name)) : tag;
            	
            	tags.add(tag);
            }
        	
        	post.setTitle(newPostRequest.getTitle());
            post.setBody(newPostRequest.getBody());
            post.setCategory(category);
            post.setImgUrl(newPostRequest.getImgUrl());
            post.setTags(tags);
            Post updatedPost = postRepository.save(post);
            
            PostResponse postResponse = AppUtils.mapPostToPosteResponse(updatedPost);
            return new ResponseEntity<>(postResponse, HttpStatus.OK);
        }
        return new ResponseEntity<>(new ApiResponse(false, "You don't have permission to edit this post"), HttpStatus.UNAUTHORIZED);
    }

    public ResponseEntity<?> addPost(PostRequest postRequest, UserPrincipal currentUser){
        User user = userRepository.findById(currentUser.getId()).orElseThrow(() -> new ResourceNotFoundException("User", "id", 1L));
        Category category = categoryRepository.findById(postRequest.getCategoryId()).orElseThrow(() -> new ResourceNotFoundException("Category", "id", postRequest.getCategoryId()));
        
        List<Tag> tags = new ArrayList<Tag>();
        
        for(String name : postRequest.getTags()) {
        	Tag tag = tagRepository.findByName(name);
        	tag = tag == null ? tagRepository.save(new Tag(name)) : tag;
        	
        	tags.add(tag);
        }
        
        Post post = new Post();
        post.setBody(postRequest.getBody());
        post.setTitle(postRequest.getTitle());
        post.setCategory(category);
        post.setUser(user);
        post.setTags(tags);
        post.setImgUrl(postRequest.getImgUrl());
        
        Post newPost =  postRepository.save(post);
        
        PostResponse postResponse = AppUtils.mapPostToPosteResponse(newPost);
        
        return new ResponseEntity<>(postResponse, HttpStatus.CREATED);
    }

    public ResponseEntity<?> getPost(Long id){
        Post post = postRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Post", "id", id));
        
        PostResponse postResponse = AppUtils.mapPostToPosteResponse(post);
        
        return new ResponseEntity<>(postResponse, HttpStatus.OK);
    }

    public ResponseEntity<?> deletePost(Long id, UserPrincipal currentUser){
        Post post = postRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Post", "id", id));
        if (post.getUser().getId().equals(currentUser.getId()) || currentUser.getAuthorities().contains(new SimpleGrantedAuthority(RoleName.ROLE_ADMIN.toString()))){
            postRepository.deleteById(id);
            return new ResponseEntity<>(new ApiResponse(true, "You successfully deleted post"), HttpStatus.OK);
        }
        return new ResponseEntity<>(new ApiResponse(true, "You don't have permission to delete this post"), HttpStatus.UNAUTHORIZED);
    }
}
