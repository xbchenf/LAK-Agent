package com.lak.ai.model.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CaptchaVO {

    private String captchaKey;

    /** 开发环境返回明文，生产环境应为 base64 图片 */
    private String captchaText;
}
