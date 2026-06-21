package com.lak.ai.model.vo;

import lombok.Data;
import java.util.List;

@Data
public class MenuVO {
    private Long id;
    private String menuCode;
    private String menuName;
    private List<MenuVO> children;
}
