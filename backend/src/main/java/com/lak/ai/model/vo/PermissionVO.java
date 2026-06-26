package com.lak.ai.model.vo;

import lombok.Data;
import java.io.Serializable;

@Data
public class PermissionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String permCode;
    private String permName;
    private String resourcePath;
    private String method;
}
