package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.Blog;
import com.junzhecai.mapper.BlogMapper;
import com.junzhecai.service.IBlogService;
import org.springframework.stereotype.Service;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {



    @Override
    public Result queryHotBlog(Integer current) {
       return null;
    }

    @Override
    public Result queryBlogById(Long id) {
        return null;
    }

    private void isBlogLiked(Blog blog) {
        
    }

    @Override
    public Result likeBlog(Long id) {
        return null;
    }

    @Override
    public Result queryBlogLikes(Long id) {
        return null;
    }

    @Override
    public Result saveBlog(Blog blog) {
        return null;
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        return null;
    }
}
