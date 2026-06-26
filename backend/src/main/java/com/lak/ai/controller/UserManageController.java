package com.lak.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lak.ai.common.exception.AuthException;
import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.common.security.MenuPermissionChecker;
import com.lak.ai.mapper.SysRoleMapper;
import com.lak.ai.mapper.SysUserMapper;
import com.lak.ai.model.dto.ResetPasswordDTO;
import com.lak.ai.model.dto.UserCreateDTO;
import com.lak.ai.model.dto.UserUpdateDTO;
import com.lak.ai.model.entity.SysRole;
import com.lak.ai.model.entity.SysUser;
import com.lak.ai.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class UserManageController {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final MenuPermissionChecker menuPermissionChecker;

    private Long getCurrentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setRoleId(user.getRoleId());
        vo.setCreateTime(user.getCreateTime());
        if (user.getRoleId() != null) {
            SysRole role = sysRoleMapper.selectById(user.getRoleId());
            if (role != null) vo.setRoleName(role.getRoleName());
        }
        return vo;
    }

    @GetMapping("/users")
    public ApiResponse<List<UserVO>> listUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "role");
        List<SysUser> users = sysUserMapper.selectList(
                new LambdaQueryWrapper<SysUser>()
                        .like(username != null, SysUser::getUsername, username)
                        .eq(status != null, SysUser::getStatus, status)
                        .orderByAsc(SysUser::getId)
        );
        return ApiResponse.success(users.stream().map(this::toVO).toList());
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserVO> getUser(@PathVariable Long id, HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "role");
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) return ApiResponse.error(404, "用户不存在");
        return ApiResponse.success(toVO(user));
    }

    @PostMapping("/users")
    @Transactional
    public ApiResponse<UserVO> createUser(@RequestBody UserCreateDTO dto, HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "role");
        if (dto.getUsername() == null || dto.getUsername().isBlank()) {
            return ApiResponse.error(400, "用户名不能为空");
        }
        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            return ApiResponse.error(400, "密码至少6位");
        }
        // 检查用户名唯一性
        if (sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername())) > 0) {
            return ApiResponse.error(400, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName());
        user.setRoleId(dto.getRoleId());
        user.setStatus("ACTIVE");
        sysUserMapper.insert(user);
        log.info("用户已创建, username={}, id={}", user.getUsername(), user.getId());
        return ApiResponse.success(toVO(user));
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ApiResponse<UserVO> updateUser(@PathVariable Long id, @RequestBody UserUpdateDTO dto,
                                           HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "role");
        Long currentUserId = getCurrentUserId(request);
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) return ApiResponse.error(404, "用户不存在");

        // 不允许把自己降级为非 ADMIN
        if (id.equals(currentUserId) && dto.getRoleId() != null && dto.getRoleId() != 1) {
            return ApiResponse.error(400, "不能移除自己的管理员角色");
        }
        // 不允许禁用自己
        if (id.equals(currentUserId) && "DISABLED".equals(dto.getStatus())) {
            return ApiResponse.error(400, "不能禁用自己");
        }

        if (dto.getRealName() != null) user.setRealName(dto.getRealName());
        if (dto.getEmail() != null) user.setEmail(dto.getEmail());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        if (dto.getStatus() != null) user.setStatus(dto.getStatus());
        if (dto.getRoleId() != null) user.setRoleId(dto.getRoleId());
        sysUserMapper.updateById(user);
        log.info("用户已更新, userId={}", id);
        return ApiResponse.success(toVO(user));
    }

    @PutMapping("/users/{id}/password")
    @Transactional
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordDTO dto,
                                            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "role");
        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            return ApiResponse.error(400, "密码至少6位");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) return ApiResponse.error(404, "用户不存在");
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        sysUserMapper.updateById(user);
        log.info("密码已重置, userId={}", id);
        return ApiResponse.success();
    }
}
