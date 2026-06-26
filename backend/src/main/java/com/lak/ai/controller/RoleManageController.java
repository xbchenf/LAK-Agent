package com.lak.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.common.security.MenuPermissionChecker;
import com.lak.ai.mapper.SysMenuMapper;
import com.lak.ai.mapper.SysMenuPermissionMapper;
import com.lak.ai.mapper.SysRoleMapper;
import com.lak.ai.mapper.SysRoleMenuMapper;
import com.lak.ai.mapper.SysUserMapper;
import com.lak.ai.model.dto.RolePermissionsDTO;
import com.lak.ai.model.entity.SysMenu;
import com.lak.ai.model.entity.SysMenuPermission;
import com.lak.ai.model.entity.SysRole;
import com.lak.ai.model.entity.SysRoleMenu;
import com.lak.ai.model.vo.MenuVO;
import com.lak.ai.model.vo.RoleVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class RoleManageController {

    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuPermissionMapper sysMenuPermissionMapper;
    private final MenuPermissionChecker menuPermissionChecker;

    // ===== 当前用户菜单 =====

    @GetMapping("/menus/me")
    public ApiResponse<List<String>> myMenus(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) return ApiResponse.success(List.of());

        var user = sysUserMapper.selectById(userId);
        if (user == null || user.getRoleId() == null) return ApiResponse.success(List.of());

        // 从角色→菜单关联查询该用户有权限的菜单 code
        List<SysRoleMenu> rms = sysRoleMenuMapper.selectList(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, user.getRoleId()));
        if (rms.isEmpty()) return ApiResponse.success(List.of());

        List<Long> menuIds = rms.stream().map(SysRoleMenu::getMenuId).toList();
        List<SysMenu> menus = sysMenuMapper.selectBatchIds(menuIds);

        return ApiResponse.success(menus.stream().map(SysMenu::getMenuCode).toList());
    }

    // ===== 菜单树（管理后台用） =====

    @GetMapping("/menus")
    public ApiResponse<List<MenuVO>> listMenus(HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "role");
        List<SysMenu> menus = sysMenuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSortOrder));
        List<MenuVO> vos = menus.stream().map(m -> {
            MenuVO vo = new MenuVO();
            vo.setId(m.getId());
            vo.setMenuCode(m.getMenuCode());
            vo.setMenuName(m.getMenuName());
            return vo;
        }).toList();
        return ApiResponse.success(buildTree(vos, menus));
    }

    private List<MenuVO> buildTree(List<MenuVO> vos, List<SysMenu> all) {
        Map<Long, MenuVO> map = new LinkedHashMap<>();
        for (MenuVO vo : vos) map.put(vo.getId(), vo);
        for (SysMenu m : all) {
            if (m.getParentId() != null) {
                MenuVO child = map.get(m.getId());
                MenuVO parent = map.get(m.getParentId());
                if (child != null && parent != null) {
                    if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                    parent.getChildren().add(child);
                }
            }
        }
        return vos.stream().filter(v -> {
            SysMenu m = all.stream().filter(a -> a.getId().equals(v.getId())).findFirst().orElse(null);
            return m != null && m.getParentId() == null;
        }).toList();
    }

    // ===== 角色列表（含已分配菜单 ID） =====

    @GetMapping("/roles")
    public ApiResponse<List<RoleVO>> listRoles(HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "role");
        List<SysRole> roles = sysRoleMapper.selectList(null);
        List<RoleVO> vos = roles.stream().map(role -> {
            RoleVO vo = new RoleVO();
            vo.setId(role.getId());
            vo.setRoleCode(role.getRoleCode());
            vo.setRoleName(role.getRoleName());
            vo.setDescription(role.getDescription());
            // 查询该角色拥有的菜单 ID
            List<SysRoleMenu> rms = sysRoleMenuMapper.selectList(
                    new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, role.getId()));
            vo.setMenuIds(rms.stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList()));
            return vo;
        }).toList();
        return ApiResponse.success(vos);
    }

    // ===== 更新角色菜单 =====

    @Transactional
    @PutMapping("/roles/{roleId}/menus")
    public ApiResponse<Void> updateRoleMenus(
            @PathVariable Long roleId,
            @RequestBody RolePermissionsDTO dto,
            HttpServletRequest request) {
        menuPermissionChecker.requireMenu(request, "role");
        // 删除旧关联
        sysRoleMenuMapper.delete(
                new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        // 插入新关联
        if (dto.getMenuIds() != null && !dto.getMenuIds().isEmpty()) {
            for (Long menuId : dto.getMenuIds()) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(roleId);
                rm.setMenuId(menuId);
                sysRoleMenuMapper.insert(rm);
            }
        }
        log.info("角色菜单已更新, roleId={}, menuIds={}", roleId, dto.getMenuIds());
        return ApiResponse.success();
    }
}
