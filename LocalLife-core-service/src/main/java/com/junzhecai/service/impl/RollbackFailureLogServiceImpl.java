package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.entity.RollbackFailureLog;
import com.junzhecai.mapper.RollbackFailureLogMapper;
import com.junzhecai.service.IRollbackFailureLogService;
import org.springframework.stereotype.Service;

@Service
public class RollbackFailureLogServiceImpl extends ServiceImpl<RollbackFailureLogMapper, RollbackFailureLog>
        implements IRollbackFailureLogService {
}