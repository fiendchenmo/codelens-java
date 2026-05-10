package com.ruoyi.system.service.impl;

import java.util.List;
import com.ruoyi.system.domain.SysUser;
import com.ruoyi.system.domain.SysUserRole;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.mapper.SysUserRoleMapper;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl implements ISysUserService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysUserRoleMapper userRoleMapper;

    @Override
    public List<SysUser> selectUserList(SysUser user) {
        return userMapper.selectUserList(user);
    }

    @Override
    public SysUser selectUserByUserName(String userName) {
        SysUser user = userMapper.selectUserByUserName(userName);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        return user;
    }

    @Override
    public int insertUser(SysUser user) {
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        int rows = userMapper.insertUser(user);
        insertUserRole(user);
        return rows;
    }

    @Override
    public int updateUser(SysUser user) {
        userRoleMapper.deleteUserRoleByUserId(user.getUserId());
        insertUserRole(user);
        return userMapper.updateUser(user);
    }

    @Override
    public int deleteUserByIds(Long[] userIds) {
        userRoleMapper.deleteUserRoleByUserIds(userIds);
        return userMapper.deleteUserByIds(userIds);
    }

    private void insertUserRole(SysUser user) {
        Long[] roles = user.getRoleIds();
        if (roles != null && roles.length > 0) {
            for (Long roleId : roles) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(user.getUserId());
                ur.setRoleId(roleId);
                userRoleMapper.insertUserRole(ur);
            }
        }
    }

    @Override
    public void resetPwd(SysUser user) {
        if (user.getUserId() == null) {
            throw new ServiceException("用户ID不能为空");
        }
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        userMapper.updateUser(user);
    }
}
