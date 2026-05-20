package com.ruoyi.system.controller;

import java.util.List;
import java.util.ArrayList;
import com.ruoyi.system.domain.SysMenu;
import com.ruoyi.system.domain.vo.MetaVo;
import com.ruoyi.system.domain.vo.RouterVo;
import com.ruoyi.system.service.ISysMenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/menu")
public class SysMenuController {

    @Autowired
    private ISysMenuService menuService;

    @GetMapping("/list")
    public List<SysMenu> list(SysMenu menu) {
        return menuService.selectMenuList(menu);
    }

    @GetMapping("/{menuId}")
    public SysMenu getInfo(@PathVariable Long menuId) {
        return menuService.selectMenuById(menuId);
    }

    @PostMapping
    public int add(@RequestBody SysMenu menu) {
        if (menuService.checkMenuNameUnique(menu)) {
            throw new RuntimeException("菜单名称已存在");
        }
        return menuService.insertMenu(menu);
    }

    @PutMapping
    public int edit(@RequestBody SysMenu menu) {
        if (menuService.checkMenuNameUnique(menu)) {
            throw new RuntimeException("菜单名称已存在");
        }
        return menuService.updateMenu(menu);
    }

    @DeleteMapping("/{menuId}")
    public int remove(@PathVariable Long menuId) {
        if (menuService.hasChildByMenuId(menuId)) {
            throw new RuntimeException("存在子菜单，不允许删除");
        }
        if (menuService.checkMenuExistRole(menuId)) {
            throw new RuntimeException("菜单已分配角色，不允许删除");
        }
        return menuService.deleteMenuById(menuId);
    }

    @GetMapping("/router")
    public List<RouterVo> getRouters() {
        List<SysMenu> menus = menuService.selectMenuTreeByUserId();
        return buildMenus(menus);
    }

    private List<RouterVo> buildMenus(List<SysMenu> menus) {
        List<RouterVo> routers = new ArrayList<>();
        for (SysMenu menu : menus) {
            RouterVo router = new RouterVo();
            router.setName(menu.getMenuName());
            router.setPath(menu.getPath());
            MetaVo meta = new MetaVo();
            meta.setTitle(menu.getMenuName());
            meta.setIcon(menu.getIcon());
            router.setMeta(meta);
            routers.add(router);
        }
        return routers;
    }
}
