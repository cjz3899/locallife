package com.junzhecai.service.impl;

import com.junzhecai.entity.BlogComments;
import com.junzhecai.mapper.BlogCommentsMapper;
import com.junzhecai.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
