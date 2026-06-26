package com.lak.ai.common.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lak.ai.common.exception.AuthException;
import com.lak.ai.mapper.SysMenuMapper;
import com.lak.ai.mapper.SysRoleMenuMapper;
import com.lak.ai.mapper.SysUserMapper;
import com.lak.ai.model.entity.SysMenu;
import com.lak.ai.model.entity.SysRoleMenu;
import com.lak.ai.model.entity.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 菜单权限校验 — 根据用户角色检查是否拥有指定菜单的访问权限。
 */
@Component
@RequiredArgsConstructor
public class MenuPermissionChecker {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;

    /**
     * 检查当前用户是否有指定菜单的访问权限。
     * 管理员（ADMIN 角色）直接放行。
     */
    public void requireMenu(HttpServletRequest request, String menuCode) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) request.getAttribute("roles");

        // ADMIN 角色直接放行
        if (roles != null && roles.contains("ADMIN")) {
            return;
        }

        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new AuthException(1_001, "未认证，请先登录");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getRoleId() == null) {
            throw new AuthException(1_003, "权限不足");
        }

        // 查找菜单 ID
        SysMenu menu = sysMenuMapper.selectOne(
                new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getMenuCode, menuCode));
        if (menu == null) {
            throw new AuthException(1_003, "权限不足");
        }

        // 检查角色是否拥有该菜单
        if (sysRoleMenuMapper.selectCount(
                new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, user.getRoleId())
                        .eq(SysRoleMenu::getMenuId, menu.getId())
        ) == 0) {
            throw new AuthException(1_003, "权限不足，需要 " + menuCode + " 菜单权限");
        }
    }
}
